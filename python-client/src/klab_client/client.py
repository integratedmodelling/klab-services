"""Scientist-facing synchronous scope and job workflow."""
from __future__ import annotations

import math
import os
import re
import time
import uuid
from dataclasses import dataclass
from itertools import islice
from typing import Any, Generic, TypeVar, cast

from .api.runtime import ObservationImpl
from .api.knowledge import ObservableImpl
from .api.scopes import ContextScopeImpl, SessionScopeImpl, UserScopeImpl
from .dto import (check_notifications, configuration_to_wire, decode_cell, object_payload,
                  observation_from_wire, observation_to_wire, required_text)
from .errors import (ConfigurationError, InvalidRequestError, JobCancelledError, JobFailedError,
                     JobUnavailableError, ProtocolError, ServerError, SubmissionOutcomeUnknown, TransportError,
                      UnsupportedOperationError, WaitTimeout, WaitCancelled)
from .transport import Endpoint, Transport, _normalize_service_url
from .requests import ContextOptions, ObservationOptions, ObservationRequest, _positive_id, _selection_id
from ._job_binding import JobBinding

_TOKEN = re.compile(r"^[^.\s#]+(?:\.[^.\s#]+)?(?:\.[1-9][0-9]*)*(?:#[1-9][0-9]*)?$")


def _scope_grammar(token):
    return (isinstance(token, str) and _TOKEN.fullmatch(token) is not None
            and not any(ord(char) < 32 or ord(char) == 127 for char in token))


def scope_token(scope) -> str | None:
    if scope is None:
        return None
    if hasattr(scope, "get_context_id"):
        token = scope.get_context_id()
    elif hasattr(scope, "get_session_id"):
        token = scope.get_session_id()
    elif isinstance(scope, str):
        token = scope
    elif isinstance(scope, UserScopeImpl):
        return None
    else:
        token = getattr(scope, "id", None)
    if not _scope_grammar(token):
        raise InvalidRequestError("Scope must follow session.context[.observationId...][#observerId] grammar")
    base, marker, observer = token.partition("#")
    for identity in base.split(".")[2:]:
        _selection_id(identity)
    if marker:
        _selection_id(observer)
    if isinstance(scope, Context):
        configuration = getattr(scope, "configuration", None)
        if not isinstance(configuration, dict) or configuration.get("id") != ".".join(base.split(".")[:2]):
            raise InvalidRequestError("Selected scope root differs from the current context configuration")
    return token


@dataclass(frozen=True)
class JobStatus:
    status: str
    stack_trace: str | None = None


@dataclass(frozen=True)
class ScientificDataOrigin:
    """Configured read origin, not credentials, authorization or a revision lease."""
    runtime_url: str
    context_id: str
    scope: str
    runtime_id: str | None = None

    def __post_init__(self):
        if not isinstance(self.runtime_url, str) or not isinstance(self.scope, str):
            raise InvalidRequestError("Scientific origin URL and scope must be explicit text")
        Endpoint(self.runtime_url)
        token = cast(str, scope_token(self.scope))  # Validated string input cannot produce a user-only token.
        if (not isinstance(self.context_id, str)
                or len(self.context_id.split(".")) != 2
                or ".".join(token.split("#")[0].split(".")[:2]) != self.context_id):
            raise InvalidRequestError("Scientific origin requires the exact root context and selected scope")
        if self.runtime_id is not None and (not isinstance(self.runtime_id, str) or not self.runtime_id.strip()):
            raise InvalidRequestError("Scientific origin Runtime identity must be nonblank text or unknown")
        object.__setattr__(self, "runtime_url", _normalize_service_url(self.runtime_url))


@dataclass(frozen=True)
class ScientificData:
    """Explicit indexed cells; no guessed array dimensions or coordinates."""
    observation_id: int
    observable: str
    units: str | None
    geometry: Any
    offsets: tuple[int, ...]
    curve: str
    slice: dict
    values: tuple[Any, ...]
    text: tuple[str, ...]
    source_observable: str | None = None
    source_units: str | None = None
    origin: ScientificDataOrigin | None = None
    artifact_type: str | None = None
    contextualization: str | None = None

    def to_dataframe(self, *, max_rows=256, max_metadata_bytes=1024 * 1024):
        """Optional local table view; no network, reshaping or scientific conversion."""
        from .tables import to_dataframe
        return to_dataframe(self, max_rows=max_rows, max_metadata_bytes=max_metadata_bytes)

    def dumps(self, *, max_bytes=1024 * 1024):
        """Encode a bounded local read snapshot, not a remote handle or revision."""
        from ._scientific_snapshot import dumps
        return dumps(self, max_bytes=max_bytes)

    @classmethod
    def loads(cls, text, *, max_bytes=1024 * 1024):
        """Inspect a saved read offline; never connect, authorize or replay work."""
        from ._scientific_snapshot import loads
        return loads(text, max_bytes=max_bytes)


_JobResult = TypeVar("_JobResult", covariant=True)


class Job(Generic[_JobResult]):
    def __init__(self, client, id: int, scope, *, service="runtime", decoder=None, result_kind=None):
        if type(id) is not int or id <= 0:
            raise ProtocolError("Submission must return a positive integer job ID")
        self.client, self.id, self.scope, self.service = client, id, scope, service
        self._decoder = decoder
        self.result_kind = result_kind if result_kind is not None else ("mapping" if decoder is None else None)
        self.cancellation_requested = False
        self._binding = JobBinding.capture(self, scope_token(scope))

    def _check_binding(self):
        self._binding.validate(self, scope_token)

    def __repr__(self):
        return f"Job(id={self.id}, service={self.service!r}, scope={scope_token(self.scope)!r})"

    def status(self, *, timeout: float | None = None, _deadline: float | None = None) -> JobStatus:
        """Poll the server state; EMPTY is unavailable, not successful cancellation."""
        self._check_binding()
        payload = self.client.transport.request(
            self.service, "GET", f"/jobs/status/{self.id}", scope=scope_token(self.scope),
            service_id=self.client.runtime_service_id, timeout=timeout, deadline=_deadline)
        self._check_binding()
        return self._decode_status(payload)

    def _decode_status(self, payload):
        payload = object_payload(payload, "job status")
        state = payload.get("status")
        if state not in {"WAITING", "STARTED", "CHANGED", "FINISHED", "ABORTED", "INTERRUPTED", "EMPTY"}:
            raise ProtocolError(f"Job {self.id} returned an unknown/null status")
        trace = payload.get("stackTrace")
        if trace is not None and not isinstance(trace, str):
            raise ProtocolError("Job stackTrace must be text or null")
        return JobStatus(state, self.client.transport.redact(trace) if trace else None)

    def _finished(self, status):
        if status.status == "ABORTED":
            raise JobFailedError(self.id, status.stack_trace or "Server computation failed")
        if status.status == "INTERRUPTED":
            raise JobCancelledError(self.id, "Server confirmed interruption")
        if status.status == "EMPTY":
            raise JobUnavailableError(self.id, "Job unavailable or expired; this is not confirmed cancellation")
        return status.status == "FINISHED"

    def _decode_result(self, payload):
        payload = object_payload(payload, "job result")
        try:
            check_notifications(payload, self.client.transport.redact)
        except ServerError as error:
            raise JobFailedError(self.id, str(error)) from None
        if "empty" in payload and type(payload["empty"]) is not bool:
            raise ProtocolError("Job result empty must be a boolean")
        if payload.get("empty"):
            raise JobFailedError(self.id, "Server returned an empty scientific result")
        return self._decoder(payload) if self._decoder else payload

    def cancel(self) -> bool:
        self._check_binding()
        accepted = self.client.transport.request(self.service, "GET", f"/jobs/cancel/{self.id}",
                                                 scope=scope_token(self.scope),
                                                 service_id=self.client.runtime_service_id, ambiguous=True)
        try:
            self._check_binding()
        except ProtocolError:
            raise SubmissionOutcomeUnknown("Cancellation response binding changed; outcome unknown, do not replay") from None
        if type(accepted) is not bool:
            raise ProtocolError("Cancellation must return a boolean")
        self.cancellation_requested = self.cancellation_requested or accepted
        return accepted

    def _retrieve_result(self, timeout, deadline):
        return self.client.transport.request(self.service, "GET", f"/jobs/retrieve/{self.id}",
            scope=scope_token(self.scope), service_id=self.client.runtime_service_id,
            timeout=timeout, deadline=deadline)

    def result(self, timeout: float | None = None, *, _cancelled=None) -> _JobResult:
        """Await and retrieve actual result; timeout preserves this resumable handle."""
        self._check_binding()
        if timeout is not None and (not math.isfinite(timeout) or timeout < 0):
            raise InvalidRequestError("Wait timeout must be finite and nonnegative")
        if _cancelled is not None and not callable(_cancelled):
            raise InvalidRequestError("Internal local cancellation control must be callable")
        deadline = time.monotonic() + timeout if timeout is not None else None

        def remaining():
            if _cancelled is not None and _cancelled():
                raise WaitCancelled(self)
            if deadline is None:
                return None
            value = deadline - time.monotonic()
            if value <= 0:
                raise WaitTimeout(self)
            return min(value, self.client.transport.timeout)

        while True:
            try:
                status = self.status(timeout=remaining(), _deadline=deadline)
                if self._finished(status):
                    self._check_binding()
                    payload = self._retrieve_result(remaining(), deadline)
                    self._check_binding()
                    result = self._decode_result(payload)
                    self._check_binding()
                    remaining()
                    return result
            except TransportError:
                if deadline is not None and time.monotonic() >= deadline:
                    raise WaitTimeout(self) from None
                raise
            delay = self.client.transport.poll_interval
            if deadline is not None:
                delay = min(delay, max(0, deadline - time.monotonic()))
            time.sleep(delay)

    wait = result

    def to_handle(self):
        """Create an offline reference; custom decoders require a supported helper."""
        from .handles import to_handle
        self._check_binding()
        return to_handle(self)


class Session(SessionScopeImpl):
    def __init__(self, client, id):
        super().__init__(user_id=client.agent_name or "", session_id=id)
        self.client = client

    @property
    def id(self):
        return self.session_id

    def create_context(self, *, configuration: dict[str, Any] | None = None, name: str = "Python context",
                       options: ContextOptions | None = None) -> Context:
        """Create a remote context; default ONE_OFF is disposable on explicit release."""
        request = self._context_request(configuration, name, options)
        payload = self.client.transport.request(
            "runtime", "POST", "/createContext", scope=self.id, json=request, ambiguous=True)
        return self.client._context(payload, owned=True)

    def _context_request(self, configuration, name, options):
        if options is not None:
            if not isinstance(options, ContextOptions) or configuration is not None or name != "Python context":
                raise InvalidRequestError("Use ContextOptions or raw configuration/name, not both")
            configuration = options.to_wire()
        return self.client._scope_request({"name": name, "persistence": "ONE_OFF", **(configuration or {})})

    def release(self) -> bool:
        """Explicitly close the remote session and its owned scopes."""
        return self.client.runtime.release_session(self)


class Context(ContextScopeImpl):
    def __init__(self, client, configuration, *, owned=False, token=None):
        id = required_text(configuration, "id")
        if len(id.split(".")) != 2 or "#" in id or not _scope_grammar(id):
            raise ProtocolError("Configuration ID must be session.context")
        selected = scope_token(id if token is None else token)
        if selected is None or ".".join(selected.split("#")[0].split(".")[:2]) != id:
            raise InvalidRequestError("Selected scope root differs from the context configuration")
        super().__init__(user_id=client.agent_name or "", session_id=id.split(".")[0], context_id=selected)
        self.client, self.configuration, self.owned = client, configuration, owned
        self._relationship_selection = None

    @property
    def id(self):
        return self.configuration["id"]

    def within(self, observation: ObservationImpl | int) -> Context:
        """Return a focused view; it does not create or release a remote context."""
        id = _positive_id(observation.id if isinstance(observation, ObservationImpl) else observation)
        if isinstance(observation, ObservationImpl) and observation._context is not None:
            bound = observation._context
            if bound.id != self.id:
                raise InvalidRequestError("Focused observation belongs to another runtime or context")
            self.client._validate_context_binding(bound, configuration=self.configuration)
        base, marker, observer = cast(str, scope_token(self)).partition("#")
        token = base + f".{id}" + (marker + observer if marker else "")
        return Context(self.client, self.configuration, owned=self.owned, token=token)

    def between(self, source: ObservationImpl | int, target: ObservationImpl | int) -> Context:
        """Select distinct persisted endpoints; no remote mutation or permission grant."""
        from .selections import between
        return between(self, source, target)

    @property
    def relationship_selection(self):
        from .selections import relationship_options
        relationship_options(self)
        return self._relationship_selection

    def observations(self, *, limit=64, offset=0, max_response_bytes=1024 * 1024):
        """Inspect one bounded page of direct children; explicitly request further pages."""
        from .selections import observations
        return observations(self, limit=limit, offset=offset, max_response_bytes=max_response_bytes)

    def submit(self, observation: ObservationImpl | dict[str, Any], *, resolution_constraints=()) -> Job:
        """Submit through Runtime; acceptance returns a job, not scientific success."""
        return self.client._submit(observation, self, resolution_constraints)

    def observe(self, request: ObservationRequest | ObservableImpl | str, *,
                options: ObservationOptions | None = None) -> Job:
        """Submit a typed request, a resolved observable, or Reasoner definition text."""
        from .requests import observe
        return observe(self, request, options)

    def resolve(self, request: ObservationRequest | ObservableImpl | str, *,
                options: ObservationOptions | None = None) -> Job:
        """Plan a typed observation with the same options as observe, without executing it."""
        from .requests import resolve
        return resolve(self, request, options)

    def job(self, id: int) -> Job:
        """Resume a saved job in its original context, including focus path."""
        return Job(self.client, id, self, decoder=lambda p: observation_from_wire(p, self), result_kind="observation")

    def to_handle(self):
        """Save exact focus/observer identity without ownership or credentials."""
        from .handles import to_handle
        return to_handle(self)

    def release(self) -> bool:
        """Explicit remote close; callers must intend disposal of this context."""
        return self.client.runtime.release_context(self)

    def fetch_data(self, observation, offsets, *, curve, slice, semantics):
        offsets, slice, semantics, origin = self._read_spec(observation, offsets, curve, slice, semantics)
        texts = tuple(self.client.transport.request(
            "runtime", "POST", "/api/v1/observation/value", scope=scope_token(self),
            json={"sourceId": observation.id, "slice": slice, "curve": curve, "geometry": None,
                  "semantics": semantics, "offset": offset, "rate": None}, response="text") for offset in offsets)
        return self._decode_read(observation, offsets, curve, slice, semantics, texts, origin)

    def _read_spec(self, observation, offsets, curve, slice, semantics):
        bound = observation._context
        if bound is None or bound.id != self.id:
            raise InvalidRequestError("Scientific reads require an observation bound to this runtime and context")
        self.client._validate_context_binding(bound, configuration=self.configuration)
        if observation.geometry is None:
            raise ProtocolError("Scientific reads require geometry to preserve location and temporal semantics")
        try:
            offsets = tuple(islice(offsets, 257))
        except TypeError:
            raise InvalidRequestError("Read 1–256 explicit cell offsets from an iterable") from None
        if not offsets or len(offsets) > 256 or any(type(i) is not int or not 0 <= i <= 2**63 - 1 for i in offsets):
            raise InvalidRequestError("Read 1–256 explicit nonnegative Java-long cell offsets per call")
        curves = {"UNSPECIFIED", "D1_LINEAR", "D2_XY", "D2_YX", "D2_XInvY", "D3_XYZ", "D3_ZYX", "D2_HILBERT", "D3_HILBERT"}
        if curve not in curves:
            raise InvalidRequestError("Unknown fill curve")
        source_id = observation.id
        if source_id == 0:
            raise UnsupportedOperationError("Query-result geometry/consumer bindings need an explicit StorageScan.Point; fetch_data supports committed native observations")
        if observation.metadata.get("im:storage-binding-source"):
            raise UnsupportedOperationError("Detached consumer bindings require an explicit StorageScan.Point")
        if type(source_id) is not int or not 1 <= source_id <= 2**63 - 1:
            raise ProtocolError("Storage reads require a positive Java-long source ID")
        if slice is None:
            if observation.geometry:
                for dimension in observation.geometry.raw.get("dimensions", []):
                    if dimension.get("type") == "TIME" and math.prod(dimension.get("shape") or [1]) > 1:
                        raise InvalidRequestError("Temporal observations require an explicit StorageScan.Slice")
            slice = {"type": "INITIALIZATION", "key": "0-0", "start": 0, "end": 0}
        from .scan_slices import _slice_to_wire
        slice = _slice_to_wire(slice)
        if semantics is not None and (not isinstance(semantics, dict) or any(
                not isinstance(semantics.get(k), str) for k in
                ("observable", "unit", "range", "currency", "contextualDimensions"))):
            raise InvalidRequestError("Semantics requires explicit observable/unit/range/currency/contextualDimensions strings")
        # A single indexed read must use the same explicit semantic definitions for
        # every point and for its returned metadata, even if the caller mutates its
        # dictionary while a request is in flight. Native fields are checked strings;
        # this is not a snapshot lease or deep-freezing of arbitrary extensions.
        semantics = dict(semantics) if semantics is not None else None
        return offsets, slice, semantics, self._read_origin()

    def _read_origin(self):
        origin = ScientificDataOrigin(self.client.transport.endpoint("runtime").url,
            self.id, cast(str, scope_token(self)), self.client._known_runtime_identity(self.configuration))
        if any(self.client.transport.redact(value) != value for value in
               (origin.runtime_url, origin.context_id, origin.scope, origin.runtime_id) if value is not None):
            raise ProtocolError("Scientific read origin must not contain configured credential material")
        return origin

    def _decode_read(self, observation, offsets, curve, slice, semantics, texts, origin):
        if self._read_origin() != origin:
            raise ProtocolError("Scientific read origin changed during indexed retrieval")
        artifact_type = observation.observable.raw.get("artifactType")
        values = tuple(decode_cell(text, artifact_type) for text in texts)
        return ScientificData(observation.id, semantics["observable"] if semantics else observation.observable.raw["urn"],
                               semantics.get("unit") if semantics else observation.units,
                              observation.geometry, offsets, curve, slice, values, texts,
                              observation.observable.raw["urn"], observation.units, origin, artifact_type,
                              observation.observable.raw.get("contextualization"))


class Client:
    _context_type = Context
    def __init__(self, runtime_url: str, credential: str | None = None, *,
                 reasoner: Endpoint | None = None, resources: Endpoint | None = None,
                 resolver: Endpoint | None = None, agent_name: str | None = None,
                 runtime_service_id: str | None = None, service_ids=(), timeout=30,
                 poll_interval=.5, verify=True, http_transport=None):
        from .api.services import ReasonerImpl, ResourcesServiceImpl, RuntimeServiceImpl, ResolverImpl
        endpoints = {"runtime": Endpoint(runtime_url, credential)}
        endpoints.update({k: v for k, v in {"reasoner": reasoner, "resources": resources, "resolver": resolver}.items() if v})
        self.transport = Transport(endpoints, timeout=timeout, poll_interval=poll_interval,
                                   verify=verify, http_transport=http_transport)
        self.agent_name, self.runtime_service_id = agent_name, runtime_service_id
        self.service_ids = tuple(service_ids)
        self._service_id_cache: dict[str, str] = {}
        self.runtime, self.reasoner = RuntimeServiceImpl(self), ReasonerImpl(self)
        self.resources, self.resolver = ResourcesServiceImpl(self), ResolverImpl(self)

    @classmethod
    def from_env(cls) -> Client:
        """Read only documented configuration; construction performs no networking."""
        runtime = os.environ.get("KLAB_RUNTIME_URL")
        if not runtime:
            raise ConfigurationError("Set KLAB_RUNTIME_URL to the configured deployment")
        peers = {service: Endpoint(os.environ[f"KLAB_{service.upper()}_URL"],
                                  os.environ.get(f"KLAB_{service.upper()}_TOKEN"))
                 for service in ("reasoner", "resources", "resolver")
                 if os.environ.get(f"KLAB_{service.upper()}_URL")}
        try:
            timeout = float(os.environ.get("KLAB_TIMEOUT", "30"))
            poll_interval = float(os.environ.get("KLAB_POLL_INTERVAL", ".5"))
        except ValueError:
            raise ConfigurationError("KLAB_TIMEOUT and KLAB_POLL_INTERVAL must be numbers") from None
        return cls(runtime, os.environ.get("KLAB_RUNTIME_TOKEN"), **peers,
                   agent_name=os.environ.get("KLAB_AGENT_NAME"),
                   runtime_service_id=os.environ.get("KLAB_RUNTIME_SERVICE_ID"),
                   service_ids=tuple(filter(None, os.environ.get("KLAB_SERVICE_IDS", "").split(","))),
                   timeout=timeout, poll_interval=poll_interval,
                   verify=os.environ.get("KLAB_CA_BUNDLE") or True)

    def _scope_request(self: Any, configuration):
        return {"configuration": configuration_to_wire(configuration), "serviceIds": list(self.service_ids)}

    def initialize_user_scope(self, *, email_address: str | None = None, local_federation: bool = False) -> tuple[dict[str, Any], ...]:
        """Explicitly advertise configured, authorized peers through the existing engine route.

        This grants no authority and discovers no credentials or extra origins.
        An existing Engine-advertised deployment may already have initialized it.
        """
        services = []
        for name in self.transport.endpoints:
            capability = getattr(self, name).capabilities()
            status = object_payload(self.transport.request(name, "GET", "/public/status"), "service status")
            services.append({"id": capability.service_id, "type": name.upper(),
                             "url": self.transport.endpoint(name).url, "status": status})
        runtime_id = next(item["id"] for item in services if item["type"] == "RUNTIME")
        if self.runtime_service_id is not None and self.runtime_service_id != runtime_id:
            raise ConfigurationError("Configured home runtime ID differs from its actual capabilities")
        for name in self.transport.endpoints:
            accepted = self.transport.request(name, "POST", "/notifyUserScope", ambiguous=True,
                json={"services": services, "emailAddress": email_address,
                      "localFederation": local_federation})
            if accepted is not True:
                raise ProtocolError(f"User scope advertisement not accepted by {name}")
        self.service_ids = tuple(item["id"] for item in services)
        self.runtime_service_id = runtime_id
        return tuple({"type": item["type"], "id": item["id"],
                      "operational": item["status"].get("operational")} for item in services)

    def create_session(self, *, name: str = "Python session") -> Session:
        """Create a unique server session without ambiguous automatic retry."""
        requested_id = uuid.uuid4().hex
        id = self.transport.request("runtime", "POST", "/createSession",
                                    json=self._scope_request({"id": requested_id, "name": name}), response="text", ambiguous=True)
        if not id or "." in id or not _TOKEN.fullmatch(id):
            raise ProtocolError("createSession returned an invalid session ID")
        return Session(self, id)

    def _context(self: Any, payload, *, owned=False):
        payload = object_payload(payload, "context configuration")
        check_notifications(payload, self.transport.redact)
        if payload.get("empty"):
            raise ProtocolError("Server returned an empty context configuration")
        return self._context_type(self, payload, owned=owned)

    def attach_context(self, id: str, *, configuration: dict[str, Any] | None = None) -> Context:
        """Attach to an authorized persisted/existing context without taking ownership."""
        if not isinstance(id, str) or len(id.split(".")) != 2 or not _TOKEN.fullmatch(id):
            raise InvalidRequestError("Attach requires a root session.context ID")
        return self._context(self.transport.request("runtime", "POST", "/api/v1/connect",
                             json=self._scope_request({**(configuration or {}), "id": id}), ambiguous=True))

    def _known_runtime_identity(self, configuration=None):
        identities = (self.runtime_service_id, getattr(self, "_service_id_cache", {}).get("runtime"),
                      (configuration or {}).get("serviceId"))
        known = [identity for identity in identities if identity is not None]
        if any(not isinstance(identity, str) or not identity.strip() for identity in known):
            raise InvalidRequestError("Runtime identity requires nonblank text")
        if known and any(identity != known[0] for identity in known):
            raise InvalidRequestError("Cached, configured and context Runtime identities differ")
        return known[0] if known else None

    def _validate_context_binding(self, context, *, configuration=None):
        if _normalize_service_url(context.client.transport.endpoint("runtime").url) != _normalize_service_url(
                self.transport.endpoint("runtime").url):
            raise InvalidRequestError("Source context is bound to a different runtime")
        current = self._known_runtime_identity(configuration)
        source = context.client._known_runtime_identity(context.configuration)
        if current is not None and source is not None and current != source:
            raise InvalidRequestError("Source context has a different known Runtime identity")

    def _submit(self, observation, scope, constraints=(), *, service="runtime", decoder=None, result_kind=None):
        from .selections import relationship_options
        context, envelope = self._submission(observation, scope, constraints, service)
        id = self.transport.request(service, "POST", "/api/v1/submit" if service == "runtime" else "/api/v1/resolve",
                                     json=envelope, scope=scope_token(scope), service_id=self.runtime_service_id,
                                      ambiguous=True, **relationship_options(context))
        return Job(self, id, context, service=service,
                    decoder=decoder or (lambda p: observation_from_wire(p, context)),
                    result_kind="observation" if decoder is None else result_kind)

    def _submission(self: Any, observation, scope, constraints, service):
        if service == "runtime" and not self.agent_name:
            raise ConfigurationError("Set agent_name/KLAB_AGENT_NAME to the authorized user's existing provenance agent name")
        token = scope_token(scope)
        if token is None or len(token.split("#")[0].split(".")) < 2:
            raise InvalidRequestError("Observation submission requires a context scope")
        if isinstance(scope, Context):
            self._validate_context_binding(scope)
            context = self._context_type(self, scope.configuration, owned=scope.owned, token=token)
            from .selections import relationship_options
            relationship_options(scope)
            context._relationship_selection = scope._relationship_selection
        else:
            context = self._context_type(self, {"id": ".".join(token.split("#")[0].split(".")[:2])}, token=token)
        envelope = {"observation": observation_to_wire(observation),
                    "agentName": self.agent_name, "resolutionConstraints": list(constraints)}
        return context, envelope

    def restore_handle(self, reference, *, allow_relocation=False):
        """Explicit capability verification/attach; never submit or create replacement work."""
        from .handles import restore_handle
        return restore_handle(self, reference, allow_relocation=allow_relocation)

    def export_formats(self, observation):
        """Inspect native observation exporters advertised by the configured Runtime."""
        from .exports import export_formats
        return export_formats(self, observation)

    def export_to_file(self, observation, path, *, media_type, overwrite=False, max_bytes=None, slice=None):
        """Stream a native observation representation into an atomically published file."""
        from .exports import export_to_file
        return export_to_file(self, observation, path, media_type=media_type, overwrite=overwrite, max_bytes=max_bytes, slice=slice)

    def close(self):
        """Close local connections only. Remote scopes require explicit release."""
        self.transport.close()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        try:
            self.close()
        except BaseException as cleanup:
            if exc_value is None:
                raise
            exc_value.add_note("Client cleanup failed: " + type(cleanup).__name__)
