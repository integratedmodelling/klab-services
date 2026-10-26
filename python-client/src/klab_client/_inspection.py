"""Small bounded, immutable, secret-redacted read-only DTO projections."""
from html import escape
import math
from types import MappingProxyType

from .errors import InvalidRequestError, ProtocolError


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


def freeze(value, redact=lambda value: value, depth=0):
    if depth > 32:
        raise ProtocolError("Inspection metadata nesting exceeds 32 levels")
    if isinstance(value, dict):
        if any(not isinstance(key, str) for key in value):
            raise ProtocolError("Inspection objects require text keys")
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
