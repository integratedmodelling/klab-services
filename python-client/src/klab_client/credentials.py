"""Two concrete origin-bound credential providers using the native Engine contract."""
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime, timezone
import os
from pathlib import Path
import re
from types import MappingProxyType

from ._bootstrap import ENGINE_AUTH_ROUTE, decode_bootstrap, wire_request
from .connection_models import ConnectionIdentity, CredentialExpiredError, ServiceReference, checked_url, loopback, parse_expiry
from .errors import AuthenticationError, ProtocolError


class CertificateError(AuthenticationError):
    pass


def _unescape(value):
    result, index = [], 0
    while index < len(value):
        character = value[index]
        index += 1
        if character == "\\":
            character = value[index]
            index += 1
            if character == "u":
                encoded = value[index:index+4]
                if len(encoded) != 4 or not re.fullmatch(r"[0-9a-fA-F]{4}", encoded):
                    raise CertificateError("Malformed Java-properties Unicode escape")
                character = chr(int(encoded, 16))
                index += 4
            else:
                character = {"t": "\t", "r": "\r", "n": "\n", "f": "\f"}.get(character, character)
        result.append(character)
    try:
        return "".join(result).encode("utf-16", "surrogatepass").decode("utf-16")
    except UnicodeError:
        raise CertificateError("Malformed certificate Unicode text") from None


def parse_properties(contents):
    """Properties.load(InputStream): Latin-1, escapes, continued lines, last value wins."""
    text = contents.decode("iso-8859-1")
    lines, pending, continuing = [], "", False
    for physical in re.split(r"\r\n|\r|\n", text):
        if not continuing:
            physical = physical.lstrip(" \t\f")
            if not physical or physical.startswith(("#", "!")):
                continue
        else:
            physical = physical.lstrip(" \t\f")
        pending += physical
        slashes = len(pending) - len(pending.rstrip("\\"))
        if slashes % 2:
            pending = pending[:-1]
            continuing = True
            continue
        lines.append(pending)
        pending = ""
        continuing = False
    if pending:
        lines.append(pending)
    properties = {}
    for line in lines:
        escaped, end = False, len(line)
        for index, character in enumerate(line):
            if not escaped and character in "=: \t\f":
                end = index
                break
            escaped = not escaped if character == "\\" else False
        start = end
        while start < len(line) and line[start] in " \t\f":
            start += 1
        if start < len(line) and line[start] in "=:" :
            start += 1
        while start < len(line) and line[start] in " \t\f":
            start += 1
        properties[_unescape(line[:end])] = _unescape(line[start:])
    return properties


@dataclass(frozen=True)
class CredentialGrant:
    identity: ConnectionIdentity
    references: tuple
    credentials: dict = field(repr=False)
    expires_at: datetime | None = None
    sensitive_values: tuple[str, ...] = field(default=(), repr=False)

    def __post_init__(self):
        if not isinstance(self.identity, ConnectionIdentity):
            raise ProtocolError("Credential provider must supply a typed identity")
        values = {}
        for url, token in self.credentials.items():
            if not isinstance(token, str) or not token.strip() or "\r" in token or "\n" in token:
                raise AuthenticationError("Credential source requires a nonblank single-line token")
            values[checked_url(url)] = token
        object.__setattr__(self, "credentials", MappingProxyType(values))
        object.__setattr__(self, "references", tuple(self.references))
        if any(not isinstance(reference, ServiceReference) for reference in self.references):
            raise ProtocolError("Credential provider must supply typed service references")
        if any(not isinstance(value, str) for value in self.sensitive_values):
            raise ProtocolError("Sensitive credential values must be text")
        object.__setattr__(self, "sensitive_values", tuple(dict.fromkeys((*values.values(), *self.sensitive_values))))
        if self.expires_at is not None and (not isinstance(self.expires_at, datetime) or self.expires_at.tzinfo is None):
            raise ProtocolError("Credential expiry must be an aware datetime when supplied")

    def for_url(self, url):
        try:
            return self.credentials[checked_url(url)]
        except KeyError:
            raise AuthenticationError("Credential provider has no credential for this approved endpoint") from None


class CredentialProvider(ABC):
    @abstractmethod
    def acquire(self, profile, *, deadline=None):
        """Acquire a complete immutable grant; never submit scientific work."""


class IssuedTokenProvider(CredentialProvider):
    def __init__(self, identity, credentials):
        self.identity = identity
        self._source = credentials

    def __repr__(self):
        return "IssuedTokenProvider()"

    @classmethod
    def from_env(cls, profile, identity):
        def source():
            values = {}
            for url, reference in profile.credential_refs.items():
                token = os.environ.get(reference.removeprefix("env:"))
                if not token:
                    raise AuthenticationError("Requested credential environment reference is unavailable")
                values[url] = token
            return values
        return cls(identity, source)

    def acquire(self, profile, *, deadline=None):
        try:
            value = self._source() if callable(self._source) else self._source
        except Exception:
            raise AuthenticationError("Issued credential source failed") from None
        try:
            if isinstance(value, CredentialGrant):
                return value
            return CredentialGrant(self.identity, profile.services, dict(value))
        except (AuthenticationError, ProtocolError):
            raise
        except Exception:
            raise AuthenticationError("Issued credential source failed") from None


class HubCertificateProvider(CredentialProvider):
    def __init__(self, certificate_path, *, http_transport=None):
        self._path = certificate_path
        self._http_transport = http_transport

    def __repr__(self):
        return "HubCertificateProvider()"

    def acquire(self, profile, *, deadline=None):
        try:
            with Path(self._path).open("rb") as stream:
                contents = stream.read(1024 * 1024 + 1)
        except (OSError, TypeError, ValueError):
            raise CertificateError("Requested ENGINE certificate cannot be read") from None
        if len(contents) > 1024 * 1024:
            raise CertificateError("Certificate exceeds its bounded property-file budget")
        properties = parse_properties(contents)
        if properties.get("klab.certificate.type") != "ENGINE":
            raise CertificateError("Scientist authentication requires an ENGINE certificate")
        level = properties.get("klab.certificate.level", "USER")
        if level not in {"USER", "INSTITUTIONAL", "LEGACY", "TEST"}:
            raise CertificateError("Unsupported authenticated ENGINE certificate level")
        for key in ("klab.username", "klab.signature", "klab.certificate", "klab.partner.hub"):
            if not properties.get(key, "").strip():
                raise CertificateError("ENGINE certificate lacks a required authentication property")
        hub = checked_url(properties["klab.partner.hub"])
        if profile.hub_url is None or hub != profile.hub_url:
            raise CertificateError("Certificate Hub differs from the explicitly trusted profile Hub")
        if level == "TEST":
            if not profile.test_loopback or not loopback(hub):
                raise CertificateError("TEST certificate authentication requires explicit loopback configuration")
        elif not hub.startswith("https://"):
            raise CertificateError("Native Hub authentication requires a trusted HTTPS base")
        elif profile.verify is False:
            raise CertificateError("Non-TEST certificates require verified Hub TLS")
        expiry_hint = parse_expiry(properties.get("klab.validuntil"))
        if expiry_hint is not None and expiry_hint <= datetime.now(timezone.utc):
            raise CredentialExpiredError("Certificate's supplied expiry hint has expired")
        request = {"name": properties["klab.username"], "key": properties["klab.signature"],
            "userType": "ENGINE", "certificate": properties["klab.certificate"], "level": level,
            "idAgreement": properties.get("klab.agreement"), "email": properties.get("klab.user.email")}
        secrets = (request["key"], request["certificate"])
        payload = wire_request(hub + ENGINE_AUTH_ROUTE, "POST", payload=request,
            timeout=profile.timeout, verify=profile.verify, transport=self._http_transport, deadline=deadline)
        identity, expiry, token, references = decode_bootstrap(payload, profile, request["name"], secrets)
        credentials = {}
        for reference in references:
            if reference.url in profile.trusted_urls:
                if not reference.url.startswith("https://") and not (level == "TEST" and profile.test_loopback and loopback(reference.url)):
                    raise AuthenticationError("Hub credentials require HTTPS except for approved loopback TEST services")
                credentials[reference.url] = token
        return CredentialGrant(identity, references, credentials, expiry, secrets)
