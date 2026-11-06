"""Explicit native Resources principal-shared locks, not editor/revision leases."""
from dataclasses import dataclass
import re
from threading import RLock

from .errors import (InvalidRequestError, ProtocolError, ServerError,
                     SubmissionOutcomeUnknown, TransportError)


@dataclass(frozen=True)
class ProjectLockReceipt:
    project: str
    action: str
    confirmed: bool
    service_url: str
    service_id: str
    limit: str = "Native boolean acknowledgement only; principal-shared, not an editor lease, fencing token or current-lock proof"


class ProjectLock:
    """One explicit acquire/release journey; no automatic unlock, retry or overwrite.

    Other clients of the same authenticated principal may share/unlock this native
    lock. Keep principal continuity explicitly; credentials are never lock receipts.
    """
    def __init__(self, client, project, *, max_response_bytes=2 * 1024 * 1024):
        from .client import Client
        if not isinstance(client, Client):
            raise InvalidRequestError("ProjectLock requires a synchronous Client")
        self._configure(client, project, max_response_bytes, RLock())

    def _configure(self, client, project, max_response_bytes, lock):
        if (not isinstance(project, str) or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.-]{0,127}", project)
                or ".." in project):
            raise InvalidRequestError("Project lock requires a simple local project identifier")
        if type(max_response_bytes) is not int or not 1 <= max_response_bytes <= 16 * 1024 * 1024:
            raise InvalidRequestError("Project lock response budget requires 1..16777216 bytes")
        self._client, self._project = client, project
        self._max_bytes, self._lock = max_response_bytes, lock
        self._binding = self._receipt = None
        self._state = "NOT_REQUESTED"
        self._safe(project)

    def _safe(self, value):
        if self._client.transport.redact(value) != value:
            raise InvalidRequestError("Project lock identity cannot contain configured credential material")

    @property
    def project(self):
        return self._project

    @property
    def state(self):
        """Local acknowledgement state only; never a server lock-status query."""
        return self._state

    @property
    def receipt(self):
        return self._receipt

    def _prepare(self, action, allow_write):
        if allow_write is not True:
            raise InvalidRequestError("Explicit allow_write=True is required for native project lock changes")
        permitted = ("NOT_REQUESTED", "ACQUIRE_DENIED") if action == "ACQUIRE" else ("ACQUIRE_CONFIRMED",)
        if self._state not in permitted:
            raise InvalidRequestError("This lock journey cannot repeat that operation; reconcile unknown remote outcomes explicitly")
        self._safe(self._project)

    def _bind(self, capability):
        identity = capability.service_id
        if not isinstance(identity, str) or not identity.strip():
            raise ProtocolError("Resources capabilities require a bounded service identity")
        try:
            encoded = identity.encode("utf-8")
        except UnicodeError:
            raise ProtocolError("Resources service identity must be valid UTF-8") from None
        if len(encoded) > 4096 or any(ord(c) < 32 or ord(c) == 127 for c in identity):
            raise ProtocolError("Resources capabilities require a bounded control-free service identity")
        binding = (self._client.transport.endpoint("resources").url, identity)
        for value in binding:
            self._safe(value)
        if self._binding is not None and self._binding != binding:
            raise ProtocolError("Resources identity changed; no project lock operation sent")
        self._binding = binding
        return binding

    def _identity(self):
        return self._bind(self._client.resources.capabilities(max_response_bytes=self._max_bytes))

    def _finish(self, action, result, binding):
        if type(result) is not bool:
            raise ProtocolError("Native project lock operations must return an exact boolean, not a legacy URL or lease")
        if (self._client.transport.endpoint("resources").url != binding[0]
                or self._client._service_id_cache.get("resources") != binding[1]):
            raise ProtocolError("Resources binding changed during a project lock operation")
        receipt = ProjectLockReceipt(self._project, action, result, *binding)
        self._receipt = receipt
        self._state = ("ACQUIRE_CONFIRMED" if result else "ACQUIRE_DENIED") if action == "ACQUIRE" else (
            "RELEASE_CONFIRMED" if result else "RELEASE_UNCONFIRMED")
        return receipt

    def _operate(self, action, allow_write):
        with self._lock:
            self._prepare(action, allow_write)
            binding = self._identity()
            self._state = "OUTCOME_UNKNOWN"  # Admission consumes this operation even on interruption.
            route = "lock" if action == "ACQUIRE" else "unlock"
            try:
                result = self._client.transport.request("resources", "GET", f"/api/v1/project/{route}/{self._project}",
                    ambiguous=True, max_response_bytes=self._max_bytes)
                return self._finish(action, result, binding)
            except (ProtocolError, ServerError, TransportError):
                raise SubmissionOutcomeUnknown("Project lock change unconfirmed; inspect remote state, do not blindly retry or assume unlock") from None

    def acquire(self, *, allow_write=False):
        return self._operate("ACQUIRE", allow_write)

    def release(self, *, allow_write=False):
        return self._operate("RELEASE", allow_write)


class AsyncProjectLock(ProjectLock):
    """Native awaited project lock journey; no hidden loop/thread or cancellation unlock."""
    def __init__(self, client, project, *, max_response_bytes=2 * 1024 * 1024):
        import asyncio
        from .async_client import AsyncClient
        if not isinstance(client, AsyncClient):
            raise InvalidRequestError("AsyncProjectLock requires an AsyncClient")
        self._configure(client, project, max_response_bytes, asyncio.Lock())

    async def _identity(self):
        return self._bind(await self._client.resources.capabilities(max_response_bytes=self._max_bytes))

    async def _operate(self, action, allow_write):
        async with self._lock:
            self._prepare(action, allow_write)
            binding = await self._identity()
            self._state = "OUTCOME_UNKNOWN"
            route = "lock" if action == "ACQUIRE" else "unlock"
            try:
                result = await self._client.transport.request("resources", "GET", f"/api/v1/project/{route}/{self._project}",
                    ambiguous=True, max_response_bytes=self._max_bytes)
                return self._finish(action, result, binding)
            except (ProtocolError, ServerError, TransportError):
                raise SubmissionOutcomeUnknown("Project lock change unconfirmed; inspect remote state, do not blindly retry or assume unlock") from None

    async def acquire(self, *, allow_write=False):
        return await self._operate("ACQUIRE", allow_write)

    async def release(self, *, allow_write=False):
        return await self._operate("RELEASE", allow_write)
