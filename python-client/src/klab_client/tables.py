"""Optional lossless pandas views of checked indexed reads, not scientific mediation."""
from dataclasses import asdict
from decimal import Decimal
import json

from .api.runtime import GeometryImpl
from .client import ScientificData, ScientificDataOrigin
from .dto import decode_cell
from .errors import InvalidRequestError, ProtocolError, UnsupportedOperationError


def _validated_metadata(data, *, max_rows=256, max_metadata_bytes=1024 * 1024):
    """Shared indexed-read validation; deliberately independent of pandas."""
    if not isinstance(data, ScientificData):
        raise InvalidRequestError("A scientific table requires a ScientificData indexed read")
    if type(max_rows) is not int or not 1 <= max_rows <= 256:
        raise InvalidRequestError("Scientific table row budget must be an integer in 1..256")
    if type(max_metadata_bytes) is not int or not 1 <= max_metadata_bytes <= 16 * 1024 * 1024:
        raise InvalidRequestError("Scientific table metadata budget must be an integer in 1..16777216")
    if (not all(isinstance(v, tuple) for v in (data.offsets, data.values, data.text))
            or not 1 <= len(data.offsets) <= max_rows
            or len(data.values) != len(data.offsets) or len(data.text) != len(data.offsets)):
        raise InvalidRequestError("Scientific table columns require matching bounded, nonempty indexed tuples")
    if (type(data.observation_id) is not int or data.observation_id <= 0
            or any(type(offset) is not int or offset < 0 for offset in data.offsets)):
        raise InvalidRequestError("Scientific table requires a positive observation ID and nonnegative integer offsets")
    if data.artifact_type not in ("NUMBER", "BOOLEAN", "TEXT") or data.contextualization == "CATEGORIZATION":
        raise UnsupportedOperationError("Scientific tables support declared NUMBER, BOOLEAN and TEXT reads; categories need a dictionary/worldview contract")
    for value, text in zip(data.values, data.text):
        if not isinstance(text, str) or (value is None) != (text == "null"):
            raise ProtocolError("Scientific scalar/null flags and original wire text disagree")
        if value is None:
            continue
        allowed = ((type(value) is int or isinstance(value, Decimal)) if data.artifact_type == "NUMBER" else
                   type(value) is bool if data.artifact_type == "BOOLEAN" else isinstance(value, str))
        if not allowed:
            raise ProtocolError("Scientific table values disagree with their declared primitive type")
        expected = decode_cell(text, data.artifact_type)
        same = (isinstance(expected, Decimal) and value.as_tuple() == expected.as_tuple()
                if isinstance(value, Decimal) else type(value) is type(expected) and value == expected)
        if not same:
            raise ProtocolError("Scientific table values disagree with original checked wire text")
    if not isinstance(data.observable, str) or not data.observable.strip() or not isinstance(data.curve, str) or not data.curve:
        raise InvalidRequestError("Scientific table requires an observable and explicit traversal description")
    if (not isinstance(data.slice, dict)
            or any(v is not None and not isinstance(v, str) for v in (data.units, data.source_units, data.source_observable, data.contextualization))
            or data.origin is not None and not isinstance(data.origin, ScientificDataOrigin)
            or data.geometry is not None and not isinstance(data.geometry, GeometryImpl)):
        raise InvalidRequestError("Scientific table requires checked geometry/origin and explicit read metadata")
    metadata = {"kind": "indexed-scientific-read", "observation_id": data.observation_id,
        "observable": data.observable, "units": data.units, "source_observable": data.source_observable,
        "source_units": data.source_units, "artifact_type": data.artifact_type,
        "contextualization": data.contextualization, "curve": data.curve,
        "slice": data.slice, "geometry": data.geometry.to_wire() if data.geometry is not None else None,
        "origin": asdict(data.origin) if data.origin is not None else None,
        "coordinates": "Offsets only; no inferred world coordinates or time labels",
        "consistency": "Recorded read metadata; no committed-revision lease or cross-fetch snapshot guarantee",
        "missingness": "wire_missing marks explicit null cells only; nonfinite numeric values are preserved",
        "validation": "Initial values checked against wire text; subsequent pandas operations are not tracked",
        "local_view": True}
    try:
        # Snapshot metadata, reject non-JSON extensions and prevent aliasing mutable
        # source geometry/slice dictionaries through DataFrame.attrs.
        encoded = json.dumps(metadata, allow_nan=False)
        if len(encoded.encode("utf-8")) > max_metadata_bytes:
            raise InvalidRequestError("Scientific table metadata exceeded its byte budget")
        metadata = json.loads(encoded)
    except (ValueError, TypeError, RecursionError):
        raise InvalidRequestError("Scientific table metadata must be finite JSON data") from None
    return metadata


def to_dataframe(data, *, max_rows=256, max_metadata_bytes=1024 * 1024):
    """Preserve offsets and scalar objects, without inferring world coordinates.

    wire_missing marks explicit null cells, not pandas NaN or scientific validity.
    """
    metadata = _validated_metadata(data, max_rows=max_rows, max_metadata_bytes=max_metadata_bytes)
    try:
        import pandas as pd
    except ImportError:
        raise UnsupportedOperationError("Install klab-python-client[tabular] for pandas scientific views") from None
    frame = pd.DataFrame({"offset": pd.Series(data.offsets, dtype=object),
        "value": pd.Series(data.values, dtype=object),
        "wire_missing": pd.Series([text == "null" for text in data.text], dtype=bool),
        "wire_text": pd.Series(data.text, dtype=object)})
    frame.attrs["klab"] = metadata
    return frame
