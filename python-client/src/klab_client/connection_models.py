"""Secret-free connection/profile/catalog objects; not permission authority."""
from dataclasses import dataclass, field
from datetime import datetime, timezone
import ipaddress
import json
import math
import os
from pathlib import Path
import re
import tempfile
from types import MappingProxyType
from urllib.parse import urlsplit

from .errors import AuthenticationError, ConfigurationError, InvalidRequestError, KlabError, ProtocolError
from .transport import Endpoint, _normalize_service_url

SERVICE_TYPES = ("runtime", "reasoner", "resources", "resolver")


class CredentialExpiredError(AuthenticationError):
    """Known expiry, distinct from a server's asset-permission denial."""


class ConnectionIdentityError(AuthenticationError):
    """Principal/issuer or pinned service identity changed; explicitly reconnect."""


class ServiceSelectionError(ConfigurationError):
    pass


class ReadinessError(KlabError, TimeoutError):
    pass


class ProfileIOError(KlabError):
    pass


def checked_url(value):
    try:
        Endpoint(value)
        return _normalize_service_url(value)
    except (TypeError, ValueError, AttributeError, ConfigurationError):
        raise ConfigurationError("Use an explicit HTTP(S) service base without credentials/query/fragment") from None


def loopback(value):
    host = urlsplit(value).hostname
    if host == "localhost":
        return True
    try:
        return ipaddress.ip_address(host).is_loopback
    except ValueError:
        return False


def parse_expiry(value):
    if value is None:
        return None
    try:
        if not isinstance(value, str) or not value.strip():
            raise ValueError()
        result = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return result.replace(tzinfo=timezone.utc) if result.tzinfo is None else result.astimezone(timezone.utc)
    except ValueError:
        raise ProtocolError("Authentication expiry is not a supported ISO date") from None


@dataclass(frozen=True)
class ConnectionIdentity:
    principal: str
    issuer: str | None = None
    email: str | None = None
    groups: tuple = field(default=(), repr=False)
    raw: dict = field(default_factory=dict, repr=False, compare=False)

    def __post_init__(self):
        if not isinstance(self.principal, str) or not self.principal.strip():
            raise ProtocolError("Authenticated identity requires a principal")
        if self.issuer is not None and (not isinstance(self.issuer, str) or not self.issuer.strip()):
            raise ProtocolError("Authentication issuer requires nonblank text when supplied")
        if self.email is not None and (not isinstance(self.email, str) or not self.email.strip()):
            raise ProtocolError("Identity email must be nonblank text when supplied")

    @property
    def binding(self):
        return self.principal, self.issuer


@dataclass(frozen=True)
class ServiceReference:
    service_type: str
    url: str
    reference_id: str | None = None
    expected_service_id: str | None = None
    primary: bool = False
    online: bool | None = None
    permissions: tuple[str, ...] = ()
    raw: dict = field(default_factory=dict, repr=False, compare=False)

    def __post_init__(self):
        if not isinstance(self.service_type, str) or not self.service_type:
            raise ProtocolError("Service reference requires its declared type")
        object.__setattr__(self, "service_type", self.service_type.lower())
        object.__setattr__(self, "url", checked_url(self.url))
        for value in (self.reference_id, self.expected_service_id):
            if value is not None and (not isinstance(value, str) or not value.strip()):
                raise ProtocolError("Service reference identity must be nonblank text")
        if type(self.primary) is not bool or self.online is not None and type(self.online) is not bool:
            raise ProtocolError("Unknown advertised primary/online state")
        if any(value not in {"QUERY", "PUBLISH"} for value in self.permissions):
            raise ProtocolError("Unknown advertised service permission state")
        object.__setattr__(self, "permissions", tuple(self.permissions))


@dataclass(frozen=True)
class CatalogEntry:
    reference: ServiceReference
    trusted: bool
    supported: bool
    reachable: bool | None = None
    available: bool | None = None
    operational: bool | None = None
    service_id: str | None = None
    worldview: str | None = None
    diagnostic: str | None = None
    error_code: str | None = None
    capabilities: dict = field(default_factory=dict, repr=False, compare=False)
    status: dict = field(default_factory=dict, repr=False, compare=False)

    @property
    def eligible(self):
        return self.trusted and self.supported and self.reachable is True and self.available is True and self.service_id is not None

    @property
    def ready(self):
        return self.eligible and self.operational is True


@dataclass(frozen=True)
class ServiceCatalog:
    entries: tuple[CatalogEntry, ...] = ()
    observed_at: datetime | None = None

    def summary(self):
        return tuple({"type": entry.reference.service_type, "url": entry.reference.url,
            "reference_id": entry.reference.reference_id, "service_id": entry.service_id,
            "trusted": entry.trusted, "supported": entry.supported, "reachable": entry.reachable,
            "available": entry.available, "operational": entry.operational,
            "worldview": entry.worldview, "diagnostic": entry.diagnostic, "error_code": entry.error_code} for entry in self.entries)


@dataclass(frozen=True)
class DeploymentProfile:
    name: str
    services: tuple[ServiceReference, ...] = ()
    trusted_urls: tuple[str, ...] = ()
    hub_url: str | None = None
    credential_refs: dict = field(default_factory=dict, repr=False)
    choices: dict = field(default_factory=dict)
    required_services: tuple[str, ...] = SERVICE_TYPES
    worldview: str | None = None
    timeout: float = 30
    poll_interval: float = .5
    max_services: int = 64
    test_loopback: bool = False
    local_federation: bool = False
    verify: bool | str = True

    def __post_init__(self):
        if not isinstance(self.name, str) or not self.name.strip():
            raise ConfigurationError("Profile requires a name")
        if self.worldview is not None and (not isinstance(self.worldview, str) or not self.worldview.strip()):
            raise ConfigurationError("Configured worldview must be nonblank text")
        services = tuple(self.services)
        if any(not isinstance(item, ServiceReference) for item in services):
            raise ConfigurationError("Profile services must be typed references")
        trusted = tuple(dict.fromkeys(checked_url(url) for url in (self.trusted_urls or tuple(item.url for item in services))))
        required = tuple(self.required_services)
        if not required or len(set(required)) != len(required) or any(item not in SERVICE_TYPES for item in required):
            raise ConfigurationError("Choose known required service types without duplicates")
        if type(self.max_services) is not int or not 1 <= self.max_services <= 256 or len(services) > self.max_services:
            raise ConfigurationError("Catalog size must have a bounded positive limit")
        if not all(isinstance(value, (float, int)) and not isinstance(value, bool) and math.isfinite(value) and value > 0
                   for value in (self.timeout, self.poll_interval)):
            raise ConfigurationError("Profile time budgets must be finite and positive")
        if type(self.test_loopback) is not bool or type(self.local_federation) is not bool:
            raise ConfigurationError("Profile test/local flags must be boolean")
        if not isinstance(self.verify, (bool, str)) or self.verify is False and not self.test_loopback:
            raise ConfigurationError("TLS verification can only be disabled in explicit loopback TEST profiles")
        refs = dict(self.credential_refs)
        if any(checked_url(url) not in trusted or not isinstance(ref, str) or not re.fullmatch(r"env:[A-Za-z_][A-Za-z0-9_]*", ref)
               for url, ref in refs.items()):
            raise ConfigurationError("Credential references must name explicit environment variables for approved endpoints")
        choices = dict(self.choices)
        if any(kind not in SERVICE_TYPES or not isinstance(value, str) or not value.strip() for kind, value in choices.items()):
            raise ConfigurationError("Selection choices require known types and nonblank IDs/URLs")
        object.__setattr__(self, "services", services)
        object.__setattr__(self, "trusted_urls", trusted)
        object.__setattr__(self, "required_services", required)
        object.__setattr__(self, "credential_refs", MappingProxyType({checked_url(k): v for k, v in refs.items()}))
        object.__setattr__(self, "choices", MappingProxyType(choices))
        if self.hub_url is not None:
            object.__setattr__(self, "hub_url", checked_url(self.hub_url))
        if self.test_loopback and any(not loopback(url) for url in (*trusted, *((self.hub_url,) if self.hub_url else ()))):
            raise ConfigurationError("TEST loopback profiles cannot include remote destinations")

    @classmethod
    def from_urls(cls, name, urls, *, credential_refs=None, **kwargs):
        refs = tuple(ServiceReference(kind, url) for kind, url in urls.items())
        return cls(name, refs, credential_refs={checked_url(urls[k]): value for k, value in (credential_refs or {}).items()}, **kwargs)

    def to_dict(self):
        return {"schema_version": 1, "name": self.name,
            "services": [{"type": item.service_type, "url": item.url, "reference_id": item.reference_id,
                          "expected_service_id": item.expected_service_id} for item in self.services],
            "trusted_urls": list(self.trusted_urls), "hub_url": self.hub_url,
            "credential_refs": dict(self.credential_refs), "choices": dict(self.choices),
            "required_services": list(self.required_services), "worldview": self.worldview,
            "timeout": self.timeout, "poll_interval": self.poll_interval, "max_services": self.max_services,
            "test_loopback": self.test_loopback, "local_federation": self.local_federation, "verify": self.verify}

    @classmethod
    def from_dict(cls, payload):
        allowed = set(cls.__dataclass_fields__) | {"schema_version"}
        if not isinstance(payload, dict) or type(payload.get("schema_version")) is not int or payload.get("schema_version") != 1 or set(payload) - allowed:
            raise ConfigurationError("Unsupported profile schema/fields; credentials do not belong in profiles")
        try:
            values = {key: value for key, value in payload.items() if key not in {"schema_version", "services"}}
            if any(not isinstance(item, dict) or set(item) - {"type", "url", "reference_id", "expected_service_id"}
                   for item in payload.get("services", [])):
                raise ConfigurationError("Unknown service profile fields; credentials do not belong in profiles")
            services = tuple(ServiceReference(item["type"], item["url"], item.get("reference_id"), item.get("expected_service_id"))
                             for item in payload.get("services", []))
            return cls(services=services, **values)
        except (TypeError, KeyError, AttributeError):
            raise ConfigurationError("Malformed deployment profile") from None

    @classmethod
    def load(cls, path):
        try:
            return cls.from_dict(json.loads(Path(path).read_text(encoding="utf-8")))
        except (OSError, ValueError, TypeError):
            raise ProfileIOError("Cannot load a valid deployment profile") from None

    def save(self, path, *, overwrite=False):
        if type(overwrite) is not bool:
            raise InvalidRequestError("Profile overwrite must be boolean")
        temporary = None
        failure = None
        try:
            destination = Path(path)
            if "\x00" in str(destination) or not destination.parent.is_dir() or not overwrite and os.path.lexists(destination):
                raise ProfileIOError("Profile parent must exist; default save cannot overwrite")
            descriptor, name = tempfile.mkstemp(prefix=".klab-profile-", dir=destination.parent)
            temporary = Path(name)
            with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
                json.dump(self.to_dict(), stream, indent=2)
                stream.flush()
                os.fsync(stream.fileno())
            if overwrite:
                os.replace(temporary, destination)
                temporary = None
            else:
                os.link(temporary, destination)
        except (OSError, ValueError, TypeError):
            failure = ProfileIOError("Profile save/publication failed")
            raise failure from None
        finally:
            if temporary is not None:
                try:
                    temporary.unlink(missing_ok=True)
                except OSError:
                    if failure is not None:
                        failure.add_note("Profile temporary cleanup failed")
                    else:
                        raise ProfileIOError("Profile temporary cleanup failed") from None
        return destination
