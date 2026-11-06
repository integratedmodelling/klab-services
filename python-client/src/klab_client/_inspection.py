"""Small bounded, immutable, secret-redacted read-only DTO projections."""
from html import escape
import inspect
import math
from types import MappingProxyType

from .errors import InvalidRequestError, ProtocolError, UnsupportedOperationError


def require_sync(client):
    if inspect.iscoroutinefunction(getattr(getattr(client, "transport", None), "request", None)):
        raise UnsupportedOperationError("This inspection helper is synchronous; await AsyncClient service methods directly")


def bounds(limit, maximum, label):
    if type(limit) is not int or not 1 <= limit <= maximum:
        raise InvalidRequestError(f"{label} must be an integer in 1..{maximum}")
    return limit


def text(value, label, maximum=8192):
    if (not isinstance(value, str) or not value.strip()
            or len(value.encode("utf-16-le", errors="surrogatepass")) // 2 > maximum
            or any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in value)):
        raise InvalidRequestError(f"{label} requires nonblank text, at most {maximum} characters, without controls")
    return value


def _reject_credential_leaves(value, redact, message):
    """Check decoded leaves after bounded/cycle-free JSON validation by the caller.

    Serialized JSON can escape quotes, slashes or Unicode within a credential.
    Inspect the actual keys/strings without rewriting source or returned metadata.
    """
    pending = [value]
    while pending:
        item = pending.pop()
        if isinstance(item, str):
            if redact(item) != item:
                raise InvalidRequestError(message)
        elif isinstance(item, dict):
            pending.extend(item)
            pending.extend(item.values())
        elif isinstance(item, (list, tuple)):
            pending.extend(item)


def freeze(value, redact=lambda value: value, depth=0):
    if depth > 32:
        raise ProtocolError("Inspection metadata nesting exceeds 32 levels")
    if isinstance(value, dict):
        if any(not isinstance(key, str) for key in value):
            raise ProtocolError("Inspection objects require text keys")
        if any(redact(key) != key for key in value):
            # Rewriting keys can collapse distinct entries or alter graph metadata.
            # Reject without including the key or credential in the exception.
            raise ProtocolError("Metadata keys must not contain configured credential material")
        return MappingProxyType({key: freeze(item, redact, depth + 1) for key, item in value.items()
            if key.lower().replace("_", "-") not in {
                "authorization", "token", "access-token", "accesstoken", "access-key", "accesskey", "api-key", "apikey",
                "credential", "password", "server-key", "serverkey", "private-key", "privatekey", "secret", "certificate"}})
    if isinstance(value, list):
        return tuple(freeze(item, redact, depth + 1) for item in value)
    if isinstance(value, str):
        return redact(value)
    if value is None or type(value) in (bool, int) or type(value) is float and math.isfinite(value):
        return value
    raise ProtocolError("Inspection metadata must contain finite JSON values")


def html_summary(summary):
    return "<pre>" + escape(summary) + "</pre>"
