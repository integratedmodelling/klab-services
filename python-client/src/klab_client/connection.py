"""Synchronous Engine-like connection orchestration over existing Client operations."""
from dataclasses import replace
from datetime import datetime, timezone
import math
import threading
import time
from types import MappingProxyType

from ._bootstrap import public_fields, wire_request
from .client import Client
from .connection_models import (CatalogEntry, ConnectionIdentityError, DeploymentProfile,
    ReadinessError, SERVICE_TYPES, ServiceCatalog, ServiceSelectionError, checked_url, loopback)
from .credentials import CredentialGrant
from .errors import (AuthenticationError, AuthorizationError, ConfigurationError, KlabError, ProtocolError,
    TransportError)
from .transport import Endpoint


class SelectionPolicy:
    """Explicit configured choice, otherwise exactly one available verified candidate."""
    def select(self, service_type, candidates, preference=None):
        if preference is not None:
            candidates = tuple(entry for entry in candidates if preference in {
                entry.reference.url, entry.reference.reference_id, entry.service_id})
        if len(candidates) != 1:
            raise ServiceSelectionError(service_type + " requires an explicit unique eligible service choice")
        return candidates[0]


class Connection:
    def __init__(self, profile, credentials, *, selection_policy=None, http_transport=None, initialize=True):
        if not isinstance(profile, DeploymentProfile):
            raise ConfigurationError("Connection requires a DeploymentProfile")
        self._profile = profile
        if type(initialize) is not bool:
            raise ConfigurationError("Peer initialization must be explicitly boolean")
        self.provider = credentials
        self.policy = selection_policy or SelectionPolicy()
        self._http_transport = http_transport
        self._lock = threading.RLock()
        self._closed = False
        self._stale = False
        self._client = None
        self._pending = {}
        self._epoch = 0
        self._fingerprint = None
        self._initialize = initialize
        self.catalog = ServiceCatalog()
        self._selected = {}
        self._sensitive = ()
        try:
            deadline = time.monotonic() + self.profile.timeout
            self._grant = self._acquire(deadline)
            self._sensitive = self._grant.sensitive_values
            self.refresh(_deadline=deadline)
        except BaseException:
            self.close()
            raise

    def __repr__(self):
        return "Connection(profile=" + repr(self.profile.name) + ", closed=" + repr(self._closed) + ")"

    @property
    def profile(self):
        return self._profile

    @property
    def identity(self):
        return self._grant.identity

    @property
    def selected(self):
        return MappingProxyType(self._selected)

    def _check(self):
        if self._closed:
            raise ConfigurationError("Connection is locally closed")
        if self._stale:
            raise ConnectionIdentityError("Connection identity changed; close and explicitly reconnect")

    def _acquire(self, deadline=None):
        grant = self.provider.acquire(self.profile, deadline=deadline)
        if not isinstance(grant, CredentialGrant):
            raise ProtocolError("Provider must return an immutable CredentialGrant")
        if len(grant.references) > self.profile.max_services:
            raise ProtocolError("Credential catalog exceeds its bounded service budget")
        public = (self.profile.name, grant.identity.principal, grant.identity.issuer, grant.identity.email,
                  *self.profile.choices.values(), *(value for reference in grant.references for value in
                  (reference.service_type, reference.url, reference.reference_id, reference.expected_service_id)))
        known = (*self._sensitive, *grant.sensitive_values)
        if any(secret and value and secret.casefold() in value.casefold() for secret in known for value in public):
            raise ProtocolError("Sensitive credential material cannot be used as public connection identity")
        if grant.expires_at is not None and grant.expires_at <= datetime.now(timezone.utc):
            from .connection_models import CredentialExpiredError
            raise CredentialExpiredError("Credential provider returned an expired package")
        return replace(grant, identity=replace(grant.identity,
            groups=tuple(public_fields(list(grant.identity.groups), known)), raw=public_fields(grant.identity.raw, known)),
            references=tuple(replace(reference, raw=public_fields(reference.raw, known)) for reference in grant.references))

    def _renew(self, deadline=None):
        grant = self._acquire(deadline)
        if grant.identity.binding != self.identity.binding:
            self._stale = True
            raise ConnectionIdentityError("Provider principal/issuer changed; explicitly reconnect")
        self._grant = grant
        self._sensitive = tuple(dict.fromkeys((*self._sensitive, *grant.sensitive_values)))
        if self._client is not None:
            self._client.transport.sensitive_values = self._sensitive

    def renew(self):
        """Explicit provider reacquisition; never retry or replay scientific work."""
        with self._lock:
            self._check()
            self._renew()
            self.refresh()
            return self.identity

    def _credential(self, service, url, deadline=None):
        with self._lock:
            self._check()
            entry = self.selected.get(service)
            if entry is None or checked_url(url) != entry.reference.url or entry.reference.url not in self.profile.trusted_urls:
                raise ConnectionIdentityError("Scientific endpoint differs from its approved selected binding")
            if self._grant.expires_at is not None and self._grant.expires_at <= datetime.now(timezone.utc):
                self._renew(deadline)
            return self._grant.for_url(url)

    def _observe(self, reference, deadline):
        trusted = reference.url in self.profile.trusted_urls
        supported = reference.service_type in SERVICE_TYPES
        entry = CatalogEntry(reference, trusted, supported,
            diagnostic=None if trusted and supported else "Untrusted destination" if not trusted else "Unsupported service type")
        if not trusted or not supported:
            return entry
        try:
            credential = self._grant.for_url(reference.url)
            capabilities = wire_request(reference.url + "/public/capabilities", "GET", credential=credential,
                timeout=self.profile.timeout, verify=self.profile.verify, transport=self._http_transport, deadline=deadline)
            status = wire_request(reference.url + "/public/status", "GET", credential=credential,
                timeout=self.profile.timeout, verify=self.profile.verify, transport=self._http_transport, deadline=deadline)
            identity = capabilities.get("serviceId")
            if not isinstance(identity, str) or not identity.strip():
                raise ProtocolError("Capabilities require an observed service ID")
            if capabilities.get("type") != reference.service_type.upper():
                raise ProtocolError("Capabilities require a verified declared service type")
            if any(secret and secret in identity for secret in self._sensitive):
                raise ProtocolError("Service identity contains sensitive material")
            previous = self.selected.get(reference.service_type)
            if previous is not None and previous.reference.url == reference.url and previous.service_id != identity:
                self._stale = True
                raise ConnectionIdentityError("Pinned service ID changed; explicitly reconnect")
            pins = tuple(item.expected_service_id for item in self.profile.services
                         if item.url == reference.url and item.service_type == reference.service_type)
            for supplied in (status.get("serviceId"), reference.expected_service_id, *pins):
                if supplied is not None and supplied != identity:
                    raise ProtocolError("Reported/configured service identity differs")
            for declared in (capabilities.get("type"), status.get("serviceType")):
                if declared is not None and declared != reference.service_type.upper():
                    raise ProtocolError("Reported service type differs from its reference")
            for field in ("available", "operational"):
                if field in status and type(status[field]) is not bool:
                    raise ProtocolError("Unknown service readiness state")
            worldview = capabilities.get("worldviewId")
            if worldview is not None and not isinstance(worldview, str):
                raise ProtocolError("Worldview compatibility requires a supplied text identity")
            if worldview is not None and any(secret and secret in worldview for secret in self._sensitive):
                raise ProtocolError("Worldview identity contains sensitive material")
            compatible = self.profile.worldview is None or worldview == self.profile.worldview
            return replace(entry, reachable=True, available=status.get("available"), operational=status.get("operational"),
                service_id=identity, worldview=worldview, supported=compatible,
                diagnostic=None if compatible else "Worldview incompatible or unknown",
                capabilities=public_fields(capabilities, self._sensitive), status=public_fields(status, self._sensitive))
        except ConnectionIdentityError:
            raise
        except KlabError as error:
            if deadline is not None and time.monotonic() >= deadline:
                raise
            return replace(entry, reachable=not isinstance(error, TransportError), supported=False,
                diagnostic=type(error).__name__ + ": service observation failed", error_code=type(error).__name__)

    def refresh(self, *, _deadline=None):
        """Observe only approved catalog URLs; keep selected endpoint/UUID bindings."""
        with self._lock:
            self._check()
            if self._grant.expires_at is not None and self._grant.expires_at <= datetime.now(timezone.utc):
                self._renew(_deadline)
            deadline = _deadline if _deadline is not None else time.monotonic() + self.profile.timeout
            # A failed/incomplete explicit probe must not leave old readiness labelled
            # current. Preserve identity pins while invalidating health observations.
            self._selected = {kind: replace(entry, reachable=None, available=None, operational=None,
                diagnostic="Refresh incomplete; readiness unknown") for kind, entry in self.selected.items()}
            self.catalog = ServiceCatalog(tuple(replace(entry, reachable=None, available=None, operational=None,
                diagnostic="Refresh incomplete; readiness unknown") for entry in self.catalog.entries), self.catalog.observed_at)
            by_key = {}
            for reference in self._grant.references:
                key = reference.service_type, reference.url
                if key in by_key and by_key[key] != reference:
                    raise ProtocolError("Conflicting service references for the same declared type and endpoint")
                by_key[key] = reference
            entries = tuple(self._observe(reference, deadline) for reference in by_key.values())
            self.catalog = ServiceCatalog(entries, datetime.now(timezone.utc))
            chosen = {}
            for kind in self.profile.required_services:
                candidates = tuple(entry for entry in entries if entry.reference.service_type == kind and entry.eligible)
                if kind in self.selected:
                    previous = self.selected[kind]
                    same = next((entry for entry in entries if entry.reference.url == previous.reference.url
                                 and entry.reference.service_type == kind), None)
                    chosen[kind] = same if same is not None else replace(previous, reachable=None,
                        available=None, operational=None, diagnostic="Selected service no longer advertised")
                else:
                    relevant = tuple(entry for entry in entries if entry.reference.service_type == kind)
                    if not candidates and len(relevant) == 1 and relevant[0].error_code in {"AuthenticationError", "AuthorizationError"}:
                        error = AuthenticationError if relevant[0].error_code == "AuthenticationError" else AuthorizationError
                        raise error(kind + " credential/probe authentication or authorization failed")
                    try:
                        choice = self.policy.select(kind, candidates, self.profile.choices.get(kind))
                    except ServiceSelectionError as error:
                        details = "; ".join(entry.reference.url + ": " + (entry.diagnostic or
                            "available=" + str(entry.available) + ", operational=" + str(entry.operational)) for entry in relevant)
                        raise ServiceSelectionError(public_fields(str(error), self._sensitive) + "; " +
                            (details[:2000] or "no advertised references for the required type")) from None
                    if choice not in candidates:
                        raise ServiceSelectionError("Selection policy cannot expand endpoint trust/eligibility")
                    chosen[kind] = choice
            self._selected = chosen
            if "runtime" in chosen and self._client is None:
                runtime = chosen["runtime"]
                peers = {kind: Endpoint(entry.reference.url, self._grant.for_url(entry.reference.url))
                         for kind, entry in chosen.items() if kind != "runtime"}
                self._client = Client(runtime.reference.url, self._grant.for_url(runtime.reference.url),
                    **peers, agent_name=self.identity.principal, runtime_service_id=runtime.service_id,
                    timeout=self.profile.timeout, poll_interval=self.profile.poll_interval,
                    verify=self.profile.verify, http_transport=self._http_transport)
                self._client.transport.credential_resolver = self._credential
                self._client.transport.sensitive_values = self._sensitive
            if self._initialize:
                self.initialize_peers(_deadline=deadline)
            return self.catalog

    def initialize_peers(self, *, _deadline=None):
        """Explicit latest-state delivery, not a membership/asset-permission grant."""
        with self._lock:
            self._check()
            if _deadline is None:
                _deadline = time.monotonic() + self.profile.timeout
            if self._client is None:
                return
            available = tuple(entry for entry in self.selected.values() if entry.eligible)
            if self.profile.local_federation and any(not loopback(entry.reference.url) for entry in available):
                raise ConfigurationError("Local identity-sharing setup cannot advertise remote peers as local")
            fingerprint = (self.identity.email, tuple((entry.reference.service_type, entry.reference.url, entry.service_id,
                                 entry.available, entry.operational) for entry in available))
            if fingerprint != self._fingerprint:
                self._fingerprint = fingerprint
                self._epoch += 1
                for target in available:
                    peers = [entry for entry in available if loopback(target.reference.url) or not loopback(entry.reference.url)]
                    services = [{"id": entry.service_id, "type": entry.reference.service_type.upper(),
                        "url": entry.reference.url, "status": entry.status} for entry in peers]
                    self._pending[target.reference.url] = (self._epoch, target.reference.service_type,
                        {"services": services, "emailAddress": self.identity.email,
                         "localFederation": self.profile.local_federation})
            for url, delivery in tuple(self._pending.items()):
                _, kind, payload = delivery
                if not any(entry.reference.url == url for entry in available):
                    continue
                try:
                    accepted = self._client.transport.request(kind, "POST", "/notifyUserScope", json=payload,
                        ambiguous=True, deadline=_deadline,
                        timeout=min(self.profile.timeout, max(.001, _deadline-time.monotonic())) if _deadline else None)
                    if type(accepted) is not bool:
                        raise ProtocolError("Scope notification requires a boolean acknowledgement")
                    if accepted is True and self._pending.get(url) == delivery:
                        self._pending.pop(url)
                except (AuthenticationError, AuthorizationError, ProtocolError):
                    raise
                except KlabError:
                    # Preserve the latest snapshot for a later explicit refresh/wait.
                    if _deadline is not None and time.monotonic() >= _deadline:
                        raise
            self._client.service_ids = tuple(entry.service_id for entry in available)
            return tuple(self._pending)

    @property
    def client(self):
        with self._lock:
            self._check()
            if self._client is None:
                raise ServiceSelectionError("A selected Runtime is required for scientific Client operations")
            if self._pending or not all(entry.ready for entry in self.selected.values()):
                raise ReadinessError(self.explain_readiness())
            return self._client

    def explain_readiness(self):
        pending = [kind for kind, entry in self.selected.items() if not entry.ready]
        if self._pending:
            pending.append("peer initialization acknowledgement")
        return "Connection dependencies not ready: " + ", ".join(pending)

    def wait_ready(self, timeout=30):
        if not isinstance(timeout, (int, float)) or isinstance(timeout, bool) or timeout < 0 or not math.isfinite(timeout):
            raise ConfigurationError("Readiness wait must have a finite nonnegative timeout")
        deadline = time.monotonic() + timeout
        while True:
            with self._lock:
                self._check()
                if not self._pending and all(entry.ready for entry in self.selected.values()):
                    return self.catalog
                if time.monotonic() >= deadline:
                    raise ReadinessError(self.explain_readiness())
            remaining = deadline - time.monotonic()
            time.sleep(min(self.profile.poll_interval, max(0, remaining)))
            try:
                self.refresh(_deadline=deadline)
            except TransportError:
                if time.monotonic() >= deadline:
                    raise ReadinessError(self.explain_readiness()) from None
                raise

    def close(self):
        with self._lock:
            self._closed = True
            if self._client is not None:
                self._client.close()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()


def connect(profile, *, credentials, selection_policy=None, http_transport=None, initialize=True):
    return Connection(profile, credentials, selection_policy=selection_policy,
                      http_transport=http_transport, initialize=initialize)
