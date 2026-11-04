"""Versioned local indexed-read snapshots. No transport, pickle or scientific extras."""
import json

from .api.runtime import GeometryImpl
from .client import ScientificData, ScientificDataOrigin
from .dto import decode_cell
from .errors import ConfigurationError, InvalidRequestError, UnsupportedOperationError
from .tables import _validated_metadata

_FIELDS = frozenset(("observation_id", "observable", "units", "geometry", "curve", "slice",
                     "source_observable", "source_units", "origin", "artifact_type", "contextualization"))
_KIND = "klab-local-indexed-read"


def _budget(max_bytes):
    if type(max_bytes) is not int or not 1 <= max_bytes <= 16 * 1024 * 1024:
        raise InvalidRequestError("Snapshot byte budget must be an integer in 1..16777216")


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate snapshot field")
        result[key] = value
    return result


def dumps(data, *, max_bytes):
    _budget(max_bytes)
    # The table metadata also includes explanatory text not stored in this format.
    # Its internal validation cap is distinct from the exact snapshot byte budget.
    metadata = _validated_metadata(data, max_metadata_bytes=16 * 1024 * 1024)
    # json.dumps normally coerces non-string object keys, potentially collapsing
    # distinct scientific metadata entries before the snapshot is even written.
    pending = [data.slice, data.geometry.to_wire() if data.geometry is not None else None]
    while pending:
        value = pending.pop()
        if isinstance(value, dict):
            if any(not isinstance(key, str) for key in value):
                raise InvalidRequestError("Snapshot metadata object keys must be strings")
            pending.extend(value.values())
        elif isinstance(value, (list, tuple)):
            pending.extend(value)
    payload = {"version": 1, "kind": _KIND,
               "metadata": {key: metadata[key] for key in sorted(_FIELDS)},
               "offsets": data.offsets, "text": data.text}
    # Encode scalar wire text, never floats or a second, potentially inconsistent
    # values column. The existing decoder is the authority on reloading.
    encoded = json.dumps(payload, allow_nan=False, sort_keys=True, separators=(",", ":"))
    if len(encoded) > max_bytes:  # ensure_ascii=True makes byte count exact.
        raise InvalidRequestError("Scientific read snapshot exceeded its byte budget")
    loads(encoded, max_bytes=max_bytes)  # Never emit a snapshot we cannot inspect.
    return encoded


def loads(text, *, max_bytes):
    _budget(max_bytes)
    try:
        if (not isinstance(text, str) or len(text) > max_bytes
                or len(text.encode("utf-8")) > max_bytes):
            raise ValueError("Snapshot input exceeds budget or is not text")
        payload = json.loads(text, object_pairs_hook=_pairs)
        if (not isinstance(payload, dict)
                or set(payload) != {"version", "kind", "metadata", "offsets", "text"}
                or type(payload["version"]) is not int or payload["version"] != 1
                or payload["kind"] != _KIND):
            raise ValueError("Unsupported snapshot schema")
        metadata = payload["metadata"]
        if not isinstance(metadata, dict) or set(metadata) != _FIELDS:
            raise ValueError("Invalid snapshot metadata")
        if metadata["artifact_type"] not in ("NUMBER", "BOOLEAN", "TEXT"):
            raise UnsupportedOperationError("Scientific snapshots require declared NUMBER, BOOLEAN or TEXT cells")
        if (not isinstance(payload["offsets"], list) or not isinstance(payload["text"], list)
                or not 1 <= len(payload["offsets"]) <= 256
                or len(payload["text"]) != len(payload["offsets"])
                or any(not isinstance(cell, str) for cell in payload["text"])):
            raise ValueError("Invalid snapshot columns")
        # No arbitrary class loading: the only accepted origin fields belong to
        # the existing immutable descriptive origin, never a Client/credential.
        origin = metadata["origin"]
        if origin is not None:
            origin = ScientificDataOrigin(**origin)
        geometry = metadata["geometry"]
        if geometry is not None:
            geometry = GeometryImpl.from_wire(geometry)
        values = tuple(decode_cell(cell, metadata["artifact_type"]) for cell in payload["text"])
        data = ScientificData(**{**metadata, "origin": origin, "geometry": geometry},
                              offsets=tuple(payload["offsets"]), text=tuple(payload["text"]), values=values)
        _validated_metadata(data, max_metadata_bytes=16 * 1024 * 1024)
    except (ValueError, TypeError, UnicodeError, RecursionError, AttributeError, ConfigurationError):
        raise InvalidRequestError("Invalid or unsupported scientific read snapshot") from None
    return data
