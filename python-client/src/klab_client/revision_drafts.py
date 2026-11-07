"""JAVA-GAP-0009: proposed private draft CAS, NOT accepted Java source or publication."""
from dataclasses import dataclass
import hashlib
import re

from .client import Client, Context, scope_token
from .errors import InvalidRequestError, ProtocolError, ServerError
from .handles import to_handle

PROTOCOL = "klab.experimental.drafts/1"
PREFIX = "/experimental/drafts/v1/"


class DraftConflict(ServerError):
    """Expected revision no longer matches; explicitly reread/reconcile, never retry blindly."""


@dataclass(frozen=True)
class DraftRevision:
    name: str
    revision: int
    source: str
    source_sha256: str
    validation: str = "UNVALIDATED_PRIVATE_DRAFT"


def _prepare(context, name, allow_experimental):
    if allow_experimental is not True:
        raise InvalidRequestError("Private draft reference requires explicit experimental opt-in")
    if not isinstance(context, Context) or scope_token(context) != context.id:
        raise InvalidRequestError("Private drafts require an explicitly bound root context")
    if not isinstance(name, str) or re.fullmatch(r"[A-Za-z0-9_.-]{1,128}", name) is None or name in (".", ".."):
        raise InvalidRequestError("Draft name must be a bounded opaque identity")
    context.client._validate_context_binding(context)
    reference = to_handle(context)
    return {"scope": context.id, "service_id": reference.runtime_id, "max_response_bytes": 131072}


def _source(value):
    try:
        if not isinstance(value, str) or not value.strip() or len(value.encode("utf-8")) > 16384:
            raise ValueError("Invalid draft source")
        return hashlib.sha256(value.encode("utf-8")).hexdigest()
    except (ValueError, UnicodeError):
        raise InvalidRequestError("Draft source must be nonblank bounded UTF-8; no validation inferred") from None


def _decode(value, name):
    if (not isinstance(value, dict) or set(value) != {"protocol", "name", "revision", "source", "source_sha256", "validation"}
            or value["protocol"] != PROTOCOL or value["name"] != name
            or type(value["revision"]) is not int or not 1 <= value["revision"] < 2**53
            or value["validation"] != "UNVALIDATED_PRIVATE_DRAFT"):
        raise ProtocolError("Draft reference did not confirm exact unvalidated revision")
    try:
        digest = _source(value["source"])
    except InvalidRequestError:
        raise ProtocolError("Draft reference returned invalid source") from None
    if value["source_sha256"] != digest:
        raise ProtocolError("Draft source digest mismatch")
    return DraftRevision(name, value["revision"], value["source"], digest)


def _body(source, expected_revision):
    if type(expected_revision) is not int or not 0 <= expected_revision < 2**53:
        raise InvalidRequestError("Explicit expected_revision is required; zero admits a new draft only")
    return {"protocol": PROTOCOL, "source": source, "source_sha256": _source(source), "expected_revision": expected_revision}


def read_draft(context, name, *, revision=None, allow_experimental=False):
    if not isinstance(getattr(context, "client", None), Client):
        raise InvalidRequestError("Use matching synchronous draft client")
    options = _prepare(context, name, allow_experimental)
    if revision is not None and (type(revision) is not int or not 1 <= revision < 2**53):
        raise InvalidRequestError("History requires an exact positive revision")
    params = {"protocol": PROTOCOL, **({"revision": revision} if revision is not None else {})}
    result = _decode(context.client.transport.request("runtime", "GET", PREFIX + name, params=params, **options), name)
    if revision is not None and result.revision != revision:
        raise ProtocolError("Draft history returned a different revision; no substitution permitted")
    return result


def write_draft(context, name, source, *, expected_revision, allow_experimental=False):
    if not isinstance(getattr(context, "client", None), Client):
        raise InvalidRequestError("Use matching synchronous draft client")
    options = _prepare(context, name, allow_experimental)
    body = _body(source, expected_revision)
    try:
        value = context.client.transport.request("runtime", "PUT", PREFIX + name, json=body, ambiguous=True, **options)
    except ServerError as error:
        if getattr(error, "status_code", None) == 409:
            raise DraftConflict("Draft revision changed; explicitly read and reconcile before another attempt") from None
        raise
    result = _decode(value, name)
    if result.revision != expected_revision + 1 or result.source != source:
        raise ProtocolError("Draft update outcome does not match exact requested source/revision; do not replay")
    return result


async def async_read_draft(context, name, *, revision=None, allow_experimental=False):
    from .async_client import AsyncClient
    if not isinstance(getattr(context, "client", None), AsyncClient):
        raise InvalidRequestError("Use matching native asynchronous draft client")
    options = _prepare(context, name, allow_experimental)
    if revision is not None and (type(revision) is not int or not 1 <= revision < 2**53):
        raise InvalidRequestError("History requires an exact positive revision")
    params = {"protocol": PROTOCOL, **({"revision": revision} if revision is not None else {})}
    result = _decode(await context.client.transport.request("runtime", "GET", PREFIX + name, params=params, **options), name)
    if revision is not None and result.revision != revision:
        raise ProtocolError("Draft history returned a different revision; no substitution permitted")
    return result


async def async_write_draft(context, name, source, *, expected_revision, allow_experimental=False):
    from .async_client import AsyncClient
    if not isinstance(getattr(context, "client", None), AsyncClient):
        raise InvalidRequestError("Use matching native asynchronous draft client")
    options = _prepare(context, name, allow_experimental)
    body = _body(source, expected_revision)
    try:
        value = await context.client.transport.request("runtime", "PUT", PREFIX + name, json=body, ambiguous=True, **options)
    except ServerError as error:
        if getattr(error, "status_code", None) == 409:
            raise DraftConflict("Draft revision changed; explicitly read and reconcile before another attempt") from None
        raise
    result = _decode(value, name)
    if result.revision != expected_revision + 1 or result.source != source:
        raise ProtocolError("Draft update outcome does not match exact requested source/revision; do not replay")
    return result
