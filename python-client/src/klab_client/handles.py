"""Secret-free versioned references. Local parsing never grants remote authority."""
from __future__ import annotations

from dataclasses import dataclass
import json
import os
from pathlib import Path
import tempfile
from urllib.parse import urlsplit

import httpx

from .api.runtime import ObservationImpl
from .client import Client, Context, Job, scope_token
from .dto import OBSERVATION_CLASS, object_payload, observation_from_wire
from .errors import (ConfigurationError, InvalidRequestError, JobUnavailableError, KlabError,
                     MissingAssetError, ProtocolError, UnsupportedOperationError)

MAX_HANDLE_BYTES = 65536
_COMMON = {"version", "kind", "runtime_id", "runtime_url", "context_id", "scope"}
_JOB_FIELDS = {"job_id", "service", "service_id", "service_url", "result_kind"}


class HandleFormatError(InvalidRequestError):
    """Local schema/JSON failure; diagnostics never include the supplied data."""


class HandleIdentityError(InvalidRequestError):
    """Configured/remote identity differs; no file-provided origin is followed."""


class HandleIOError(KlabError):
    """Local file operation failed; inspect the explicitly requested destination."""


def _text(value):
    if not isinstance(value, str) or not value.strip() or any(ord(c) < 32 for c in value):
        raise HandleFormatError("Handle identifiers require nonblank, control-free text")
    try:
        size = len(value.encode("utf-8"))
    except UnicodeError:
        raise HandleFormatError("Handle text must be valid UTF-8") from None
    if size > 4096:
        raise HandleFormatError("Handle identifier exceeds its size limit")
    return value


def _id(value):
    if type(value) is not int or not 0 < value <= 2**63 - 1:
        raise HandleFormatError("Handle IDs require positive signed-64-bit integers")
    return value


def _service_identity(value):
    _text(value)
    if any(c.isspace() for c in value) or value.startswith("eyJ") and value.count(".") == 2:
        raise HandleFormatError("Service identities cannot contain authentication material")
    return value


def _url(value):
    _text(value)
    try:
        parsed = urlsplit(value)
        if (parsed.scheme not in {"https", "http"} or not parsed.hostname or parsed.username is not None
                or parsed.password is not None or "?" in value or "#" in value or any(c.isspace() for c in value)):
            raise HandleFormatError("Handle origins must be explicit credential-free HTTP(S) service URLs")
        normalized = httpx.URL(value)
        if normalized.port == {"http": 80, "https": 443}[normalized.scheme]:
            normalized = normalized.copy_with(port=None)
        return _text(str(normalized).rstrip("/"))
    except (ValueError, httpx.InvalidURL):
        raise HandleFormatError("Invalid handle service URL") from None


def _selection(token, root):
    _text(token)
    _text(root)
    try:
        validated = scope_token(token)
    except InvalidRequestError:
        raise HandleFormatError("Invalid saved scope grammar") from None
    base, marker, observer = validated.partition("#")
    parts = base.split(".")
    if len(parts) < 2 or ".".join(parts[:2]) != root or len(root.split(".")) != 2:
        raise HandleFormatError("Saved root and focused context identities differ")
    focus = tuple(_id(int(part)) for part in parts[2:])
    observer_id = _id(int(observer)) if marker else None
    return focus, observer_id


@dataclass(frozen=True)
class Handle:
    kind: str
    runtime_id: str
    runtime_url: str
    context_id: str
    scope: str
    version: int = 1
    observation_id: int | None = None
    job_id: int | None = None
    service: str | None = None
    service_id: str | None = None
    service_url: str | None = None
    result_kind: str | None = None

    def __post_init__(self):
        if type(self.version) is not int:
            raise HandleFormatError("Handle schema version must be an integer")
        if self.version != 1:
            raise UnsupportedOperationError("Unsupported saved-handle schema version")
        if self.kind not in ("context", "observation", "job"):
            raise HandleFormatError("Unknown handle kind")
        _service_identity(self.runtime_id)
        _selection(self.scope, self.context_id)
        object.__setattr__(self, "runtime_url", _url(self.runtime_url))
        job_fields = (self.job_id, self.service, self.service_id, self.service_url, self.result_kind)
        if self.kind == "job":
            _id(self.job_id)
            if self.observation_id is not None or self.service not in ("runtime", "resolver"):
                raise HandleFormatError("Invalid job service or extra observation identity")
            _service_identity(self.service_id)
            object.__setattr__(self, "service_url", _url(self.service_url))
            allowed = {"observation", "mapping"} if self.service == "runtime" else {"dataflow", "mapping"}
            if not isinstance(self.result_kind, str) or self.result_kind not in allowed:
                raise HandleFormatError("Unknown or incompatible saved job decoder")
            if self.service == "runtime" and (self.service_id != self.runtime_id or self.service_url != self.runtime_url):
                raise HandleFormatError("Runtime job identity must equal its home Runtime")
        else:
            if any(value is not None for value in job_fields):
                raise HandleFormatError("Non-job handles cannot carry job/service/decoder fields")
            if self.kind == "observation":
                _id(self.observation_id)
            elif self.observation_id is not None:
                raise HandleFormatError("Context handles cannot carry an observation ID")

    def to_dict(self):
        values = {key: getattr(self, key) for key in _COMMON}
        if self.kind == "observation":
            values["observation_id"] = self.observation_id
        elif self.kind == "job":
            values.update({key: getattr(self, key) for key in _JOB_FIELDS})
        return values

    def dumps(self):
        return json.dumps(self.to_dict(), sort_keys=True, ensure_ascii=False, separators=(",", ":"))

    def inspect(self):
        return {**self.to_dict(), "remote_verified": False}

    def save(self, path, *, overwrite=False):
        return save_handle(self, path, overwrite=overwrite)

    def restore(self, client, *, allow_relocation=False):
        return restore_handle(client, self, allow_relocation=allow_relocation)

    @classmethod
    def loads(cls, contents):
        if not isinstance(contents, (str, bytes)):
            raise HandleFormatError("Saved handles require UTF-8 JSON text or bytes")
        try:
            raw = contents.encode("utf-8") if isinstance(contents, str) else contents
            if len(raw) > MAX_HANDLE_BYTES:
                raise HandleFormatError("Saved handle exceeds the input size limit")
            values = json.loads(raw.decode("utf-8"), object_pairs_hook=_unique, parse_constant=_invalid_constant)
        except (UnicodeError, ValueError, RecursionError):
            raise HandleFormatError("Invalid or truncated saved-handle JSON") from None
        if not isinstance(values, dict):
            raise HandleFormatError("Saved handle must be a JSON object")
        kind = values.get("kind")
        expected = _COMMON | ({"observation_id"} if kind == "observation" else _JOB_FIELDS if kind == "job" else set())
        if set(values) != expected:
            raise HandleFormatError("Saved handle fields are missing or unknown; snapshots/secrets are not supported")
        return cls(**values)

    @classmethod
    def load(cls, path):
        return load_handle(path)


def _unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise HandleFormatError("Duplicate saved-handle JSON keys")
        result[key] = value
    return result


def _invalid_constant(value):
    raise HandleFormatError("Nonfinite JSON values are not permitted in handles")


def _runtime_identity(client):
    known = client._service_id_cache.get("runtime")
    configured = client.runtime_service_id
    if known is not None and configured is not None and known != configured:
        raise HandleIdentityError("Cached and configured home Runtime identities differ")
    identity = configured or known
    if not identity:
        raise ConfigurationError("Initialize/configure the home Runtime identity before offline handle creation")
    return identity


def _no_credentials(reference, client):
    values = reference.to_dict().values()
    if any(endpoint.credential and any(endpoint.credential in value for value in values if isinstance(value, str))
           for endpoint in client.transport.endpoints.values()):
        raise HandleFormatError("A saved reference cannot contain a configured credential")


def to_handle(source):
    if isinstance(source, Handle):
        return source
    extra = {}
    if isinstance(source, Context):
        context, client, kind = source, source.client, "context"
    elif isinstance(source, ObservationImpl):
        if source._context is None:
            raise HandleFormatError("Unbound observations cannot produce durable handles")
        context, client, kind = source._context, source._context.client, "observation"
        extra["observation_id"] = _id(source.id)
    elif isinstance(source, Job):
        client, kind = source.client, "job"
        token = scope_token(source.scope)
        if token is None or len(token.split("#")[0].split(".")) < 2:
            raise HandleFormatError("Durable job handles require a context scope")
        root = ".".join(token.split("#")[0].split(".")[:2])
        context = source.scope if isinstance(source.scope, Context) else Context(client, {"id": root}, token=token)
        if source.result_kind is None:
            raise UnsupportedOperationError("Custom job decoders cannot be saved; use supported service job helpers")
        service = source.service
        if service not in ("runtime", "resolver"):
            raise UnsupportedOperationError("Unsupported saved executing-service kind")
        service_id = _runtime_identity(client) if service == "runtime" else client._service_id_cache.get(service)
        if not service_id:
            raise ConfigurationError("Read the executing peer capabilities before offline job-handle creation")
        extra.update(job_id=source.id, service=service, service_id=service_id,
            service_url=client.transport.endpoint(service).url, result_kind=source.result_kind)
    else:
        raise HandleFormatError("Only Context, bound ObservationImpl, Job or Handle objects can be saved")
    runtime_url = _url(client.transport.endpoint("runtime").url)
    if _url(context.client.transport.endpoint("runtime").url) != runtime_url:
        raise HandleIdentityError("Source scope belongs to another Runtime")
    runtime_id = _runtime_identity(client)
    if context.configuration.get("serviceId") not in (None, runtime_id):
        raise HandleIdentityError("Source context configuration has a different Runtime identity")
    reference = Handle(kind=kind, runtime_id=runtime_id, runtime_url=runtime_url,
        context_id=context.id, scope=scope_token(context), **extra)
    _no_credentials(reference, client)
    return reference


def save_handle(source, path, *, overwrite=False):
    if type(overwrite) is not bool:
        raise HandleFormatError("Overwrite policy must be an explicit boolean")
    reference = to_handle(source)
    data = reference.dumps().encode("utf-8")
    temporary = None
    try:
        destination = Path(path)
        if not destination.parent.is_dir():
            raise HandleIOError("Handle destination parent must already exist")
        descriptor, name = tempfile.mkstemp(prefix=".klab-handle-", dir=destination.parent)
        temporary = Path(name)
        try:
            view = memoryview(data)
            while view:
                written = os.write(descriptor, view)
                if written <= 0:
                    raise OSError("Short handle write")
                view = view[written:]
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
        if overwrite:
            os.replace(temporary, destination)
            temporary = None
        else:
            # Atomic no-clobber publication; unsupported filesystems fail explicitly.
            os.link(temporary, destination)
    except (OSError, TypeError, ValueError):
        raise HandleIOError("Handle write/publication failed; inspect the requested destination") from None
    finally:
        if temporary is not None:
            try:
                temporary.unlink(missing_ok=True)
            except OSError:
                raise HandleIOError("Handle temporary-file cleanup failed; inspect the requested destination") from None
    return reference


def load_handle(path):
    try:
        with Path(path).open("rb") as stream:
            data = stream.read(MAX_HANDLE_BYTES + 1)
    except (OSError, TypeError, ValueError):
        raise HandleIOError("Cannot read the requested handle file") from None
    return Handle.loads(data)


def _lookup(context, observation_id):
    rows = context.client.runtime.query_knowledge_graph(
        {"type": "QUERY", "resultType": "OBSERVATION", "id": observation_id, "limit": 2}, context)
    if not rows:
        raise MissingAssetError("Saved observation/selection is absent or inaccessible in this context")
    if len(rows) != 1 or rows[0].get("@CLASS") != OBSERVATION_CLASS:
        raise ProtocolError("Observation lookup returned an incompatible or nonunique asset")
    return _decode_observation(context, rows[0], observation_id)


def _decode_observation(context, payload, observation_id=None):
    payload = object_payload(payload, "observation")
    if payload.get("@CLASS") != OBSERVATION_CLASS:
        raise ProtocolError("Saved observation job returned an incompatible result")
    if (type(payload.get("id")) is not int or payload["id"] < 0
            or observation_id is not None and payload["id"] != observation_id):
        raise HandleIdentityError("Observation lookup returned a different identity")
    urn = payload.get("urn")
    if not isinstance(urn, str) or not urn.startswith((context.id + ".", context.id + ":")):
        raise HandleIdentityError("Observation lookup returned an asset outside the saved context")
    return observation_from_wire(payload, context)


def restore_handle(client, reference, *, allow_relocation=False):
    if not isinstance(client, Client) or not isinstance(reference, Handle) or type(allow_relocation) is not bool:
        raise HandleFormatError("Restore requires an explicit Client, validated Handle and boolean relocation policy")
    _no_credentials(reference, client)
    runtime_url = _url(client.transport.endpoint("runtime").url)
    if runtime_url != reference.runtime_url and not allow_relocation:
        raise HandleIdentityError("Configured Runtime origin/base path differs from the saved reference")
    if reference.kind == "job":
        service_url = _url(client.transport.endpoint(reference.service).url)
        if service_url != reference.service_url and not allow_relocation:
            raise HandleIdentityError("Configured job-service origin/base path differs from the saved reference")
    runtime_id = client.runtime.capabilities().service_id
    if runtime_id != reference.runtime_id or client.runtime_service_id not in (None, runtime_id):
        raise HandleIdentityError("Authenticated/configured home Runtime identity differs from the saved reference")
    if reference.kind == "job" and reference.service == "resolver":
        if client.resolver.capabilities().service_id != reference.service_id:
            raise HandleIdentityError("Executing peer identity differs from the saved reference")
    client.runtime_service_id = runtime_id
    root = client.attach_context(reference.context_id)
    if root.id != reference.context_id:
        raise ProtocolError("Attach returned a different root context")
    if root.configuration.get("serviceId") not in (None, runtime_id):
        raise HandleIdentityError("Attached context belongs to another Runtime")
    focus, observer = _selection(reference.scope, reference.context_id)
    for observation_id in focus:
        _lookup(root, observation_id)
    if observer is not None:
        selected = _lookup(root, observer)
        if "AGENT" not in selected.observable.semantics.raw.get("type", []):
            raise HandleIdentityError("Saved observer selection is not a persisted agent")
    context = Context(client, root.configuration, token=reference.scope, owned=False)
    if reference.kind == "context":
        return context
    if reference.kind == "observation":
        observation = _lookup(root, reference.observation_id)
        observation._context = context
        return observation
    decoder = None
    if reference.result_kind == "observation":
        decoder = lambda payload: _decode_observation(context, payload)
    elif reference.result_kind == "dataflow":
        decoder = lambda payload: object_payload(payload, "dataflow")
    job = Job(client, reference.job_id, context, service=reference.service, decoder=decoder, result_kind=reference.result_kind)
    if job.status().status == "EMPTY":
        raise JobUnavailableError(job.id, "Saved job is unavailable or expired; no work was recreated")
    return job
