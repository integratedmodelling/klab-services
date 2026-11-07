"""Explicit proposed shared-read grants; server responses never grant local authority.

Experimental reference only. No native grant endpoint is assumed: each operation
requires both caller opt-in and a matching advertised Runtime protocol.
"""
from dataclasses import dataclass
import math
import re

from .client import Client, Context, scope_token
from .errors import InvalidRequestError, ProtocolError, SubmissionOutcomeUnknown, UnsupportedOperationError
from .handles import to_handle

PROTOCOL = "klab.experimental.shared-access/1"
PREFIX = "/experimental/shared-access/v1/"
OWNER_RIGHTS = ("READ", "SUBMIT", "RELEASE", "SHARE")


@dataclass(frozen=True)
class ReadGrant:
    context_id: str
    grant_id: str
    grantee: str
    expires_at: float
    rights: tuple[str, ...] = ("READ",)
    evidence: str = "SERVER_REPORTED_EXPERIMENTAL_REFERENCE_NOT_NATIVE_GRANT"


@dataclass(frozen=True)
class AccessInspection:
    context_id: str
    principal: str
    owner: str
    rights: tuple[str, ...]
    grant: ReadGrant | None
    evidence: str = "SERVER_REPORTED_EXPERIMENTAL_REFERENCE_NOT_NATIVE_GRANT"


@dataclass(frozen=True)
class GrantInspection:
    context_id: str
    grantee: str
    state: str
    grant: ReadGrant | None
    evidence: str = "SERVER_REPORTED_EXPERIMENTAL_REFERENCE_NOT_NATIVE_GRANT"


def _principal(value):
    if not isinstance(value, str) or value in (".", "..") or re.fullmatch(r"[A-Za-z0-9_.@-]{1,128}", value) is None:
        raise InvalidRequestError("Grantee requires a bounded explicit principal identity")
    return value


def _grant_id(value):
    if not isinstance(value, str) or re.fullmatch(r"[0-9a-f]{32}", value) is None:
        raise InvalidRequestError("Grant requires an opaque32hex identity")
    return value


def _expiry(value):
    try:
        valid = type(value) in (int, float) and math.isfinite(value) and value > 0
    except OverflowError:
        valid = False
    if not valid: raise InvalidRequestError("Grant expiry requires a finite positive server timestamp")
    return float(value)


def _grant(value, context_id, grantee=None):
    if (not isinstance(value, dict) or set(value) != {"protocol", "contextId", "grantId", "grantee", "expiresAt", "rights"}
            or value["protocol"] != PROTOCOL or value["contextId"] != context_id
            or value["rights"] != ["READ"] or grantee is not None and value["grantee"] != grantee):
        raise ProtocolError("Shared read response did not confirm the exact root, grantee and read-only protocol")
    try:
        return ReadGrant(context_id, _grant_id(value["grantId"]), _principal(value["grantee"]), _expiry(value["expiresAt"]))
    except InvalidRequestError:
        raise ProtocolError("Shared read response returned invalid grant identity or expiry") from None


def _inspection(value, context_id):
    if (not isinstance(value, dict) or set(value) != {"protocol", "contextId", "principal", "owner", "rights", "grantId", "expiresAt"}
            or value["protocol"] != PROTOCOL or value["contextId"] != context_id):
        raise ProtocolError("Access inspection did not confirm the exact experimental root")
    try:
        principal, owner = _principal(value["principal"]), _principal(value["owner"])
    except InvalidRequestError:
        raise ProtocolError("Access inspection returned invalid principal identities") from None
    if principal == owner:
        if value["rights"] != list(OWNER_RIGHTS) or value["grantId"] is not None or value["expiresAt"] is not None:
            raise ProtocolError("Owner inspection did not confirm exact reference rights")
        return AccessInspection(context_id, principal, owner, OWNER_RIGHTS, None)
    grant = _grant({"protocol": PROTOCOL, "contextId": context_id, "grantId": value["grantId"],
        "grantee": principal, "expiresAt": value["expiresAt"], "rights": value["rights"]}, context_id)
    return AccessInspection(context_id, principal, owner, ("READ",), grant)


def _grant_inspection(value, context_id, grantee):
    if (not isinstance(value, dict) or set(value) != {"protocol", "contextId", "grantee", "state", "grant"}
            or value["protocol"] != PROTOCOL or value["contextId"] != context_id or value["grantee"] != grantee
            or value["state"] not in ("ACTIVE", "EXPIRED", "REVOKED", "ABSENT")):
        raise ProtocolError("Grant inspection did not confirm exact root, grantee and reference state")
    if value["state"] == "ABSENT":
        if value["grant"] is not None: raise ProtocolError("Absent grant inspection contains a grant")
        return GrantInspection(context_id, grantee, "ABSENT", None)
    return GrantInspection(context_id, grantee, value["state"], _grant(value["grant"], context_id, grantee))


class _SharedAccess:
    def _bind(self, context, allow_experimental, client_type):
        if allow_experimental is not True:
            raise InvalidRequestError("Shared-read reference requires explicit experimental opt-in")
        if (not isinstance(context, Context) or not isinstance(context.client, client_type)
                or scope_token(context) != context.id or context.relationship_selection is not None):
            raise InvalidRequestError("Shared-read management requires a matching explicit root context/client")
        self.context = context
        self._client_type = client_type
        self.reference = to_handle(context)

    def _options(self):
        if (not isinstance(self.context, Context) or not isinstance(self.context.client, self._client_type)
                or to_handle(self.context) != self.reference):
            raise InvalidRequestError("Shared-read root binding changed; explicitly construct another client")
        return {"scope": self.reference.context_id, "service_id": self.reference.runtime_id, "max_response_bytes": 16384}

    def _advertised(self, capabilities):
        if capabilities.service_id != self.reference.runtime_id:
            raise ProtocolError("Shared-read capability came from a different Runtime")
        self._options()
        if capabilities.raw.get("sharedAccessProtocol") != PROTOCOL:
            raise UnsupportedOperationError("Runtime does not advertise the explicit shared-read reference; no fallback")

    def _body(self, grantee, ttl_seconds):
        if type(ttl_seconds) is not int or not 1 <= ttl_seconds <= 3600:
            raise InvalidRequestError("Read grant TTL must be an explicit1..3600seconds")
        return {"protocol": PROTOCOL, "grantee": _principal(grantee), "ttlSeconds": ttl_seconds}

    def _public(self, value):
        if isinstance(value, str):
            if self.context.client.transport.redact(value) != value:
                raise ProtocolError("Shared-read response contains configured credential material")
        elif isinstance(value, dict):
            for key, item in value.items():
                self._public(key); self._public(item)
        elif isinstance(value, list):
            for item in value: self._public(item)
        return value

    def _revoke(self, value, identity):
        if (not isinstance(value, dict) or value.get("revoked") is not True
                or value != {"protocol": PROTOCOL, "contextId": self.reference.context_id,
                "grantId": identity, "revoked": True}):
            raise ProtocolError("Revocation response did not confirm the exact read grant")
        return True

    def _after_request(self, value, method):
        try:
            self._options()
        except InvalidRequestError:
            if method != "GET":
                raise SubmissionOutcomeUnknown("Shared-read binding changed after mutation; use owner grant inspection, never replay blindly") from None
            raise ProtocolError("Shared-read binding changed during inspection; response not accepted") from None
        return self._public(value)


class SharedAccess(_SharedAccess):
    """Synchronous capability-gated proposed grant management; no implicit permission changes."""
    def __init__(self, context, *, allow_experimental=False):
        self._bind(context, allow_experimental, Client)

    def _request(self, method, route, **kwargs):
        options = self._options()
        self._advertised(self.context.client.runtime.capabilities(max_response_bytes=16384))
        value = self.context.client.transport.request("runtime", method, PREFIX + route, **options, **kwargs)
        return self._after_request(value, method)

    def inspect(self):
        return _inspection(self._request("GET", "access", params={"protocol": PROTOCOL}), self.reference.context_id)

    def grant_read(self, grantee, *, ttl_seconds=300):
        body = self._body(grantee, ttl_seconds)
        return _grant(self._request("POST", "grants", json=body, ambiguous=True), self.reference.context_id, body["grantee"])

    def inspect_grant(self, grantee):
        principal = _principal(grantee)
        return _grant_inspection(self._request("GET", "grants", params={"protocol": PROTOCOL, "grantee": principal}),
            self.reference.context_id, principal)

    def revoke(self, grant_id):
        identity = _grant_id(grant_id)
        return self._revoke(self._request("POST", "grants/" + identity + "/revoke", json={"protocol": PROTOCOL}, ambiguous=True), identity)


class AsyncSharedAccess(_SharedAccess):
    """Native async counterpart; construction and validation perform no remote mutation."""
    def __init__(self, context, *, allow_experimental=False):
        from .async_client import AsyncClient
        self._bind(context, allow_experimental, AsyncClient)

    async def _request(self, method, route, **kwargs):
        options = self._options()
        self._advertised(await self.context.client.runtime.capabilities(max_response_bytes=16384))
        value = await self.context.client.transport.request("runtime", method, PREFIX + route, **options, **kwargs)
        return self._after_request(value, method)

    async def inspect(self):
        return _inspection(await self._request("GET", "access", params={"protocol": PROTOCOL}), self.reference.context_id)

    async def grant_read(self, grantee, *, ttl_seconds=300):
        body = self._body(grantee, ttl_seconds)
        return _grant(await self._request("POST", "grants", json=body, ambiguous=True), self.reference.context_id, body["grantee"])

    async def inspect_grant(self, grantee):
        principal = _principal(grantee)
        return _grant_inspection(await self._request("GET", "grants", params={"protocol": PROTOCOL, "grantee": principal}),
            self.reference.context_id, principal)

    async def revoke(self, grant_id):
        identity = _grant_id(grant_id)
        return self._revoke(await self._request("POST", "grants/" + identity + "/revoke", json={"protocol": PROTOCOL}, ambiguous=True), identity)
