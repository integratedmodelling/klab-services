"""Explicit provisional scientific envelope; not native Java Avro/StorageScan ABI."""
from dataclasses import dataclass
import json
import math

PROTOCOL = "klab.experimental.worker/1"
GAP_ID = "JAVA-GAP-0017"
MAX_BYTES = 256 * 1024


class WorkerError(Exception):
    """Secret-safe protocol/operation failure. Does not imply a retry is safe."""


def encoded(value):
    try:
        result = json.dumps(value, allow_nan=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    except (ValueError, TypeError, RecursionError):
        raise WorkerError("Worker message is not bounded finite JSON") from None
    if len(result) > MAX_BYTES:
        raise WorkerError("Worker message exceeds byte budget")
    return result


def finite(value):
    if type(value) not in (int, float):
        return False
    try:
        return math.isfinite(value)
    except OverflowError:
        return False


def decoded(value):
    def reject_constant(_):
        raise ValueError("Nonfinite JSON constant")
    def unique(pairs):
        result = {}
        for key, item in pairs:
            if key in result:
                raise ValueError("Duplicate key")
            result[key] = item
        return result
    try:
        if len(value) > MAX_BYTES:
            raise ValueError("Oversized")
        result = json.loads(value, object_pairs_hook=unique, parse_constant=reject_constant)
        encoded(result)
        return result
    except (ValueError, TypeError, RecursionError, UnicodeError):
        raise WorkerError("Invalid worker response") from None


@dataclass(frozen=True)
class Limits:
    seconds: float = 10
    output_bytes: int = MAX_BYTES
    cells: int = 4096
    memory_bytes: int | None = None

    def __post_init__(self):
        if (not finite(self.seconds) or not 0 < self.seconds <= 300
                or type(self.output_bytes) is not int or not 1 <= self.output_bytes <= MAX_BYTES
                or type(self.cells) is not int or not 1 <= self.cells <= 4096):
            raise WorkerError("Invalid worker limits")
        if self.memory_bytes is not None and (type(self.memory_bytes) is not int or not 64 * 1024**2 <= self.memory_bytes <= 16 * 1024**3):
            raise WorkerError("Explicit memory budget must be64MiB..16GiB")


@dataclass(frozen=True)
class Field:
    shape: tuple
    axes: tuple
    coordinates: tuple
    crs: str | None
    unit: str | None
    values: tuple
    valid: tuple

    def __post_init__(self):
        # Require immutable containers even for direct construction; no caller mutation.
        if any(type(x) is not tuple for x in (self.shape, self.axes, self.coordinates, self.values, self.valid)):
            raise WorkerError("Worker fields require immutable tuples")
        if not 1 <= len(self.shape) <= 4 or any(type(n) is not int or not 1 <= n <= 4096 for n in self.shape):
            raise WorkerError("Invalid scientific shape")
        count = math.prod(self.shape)
        if count > 4096 or len(self.values) != count or len(self.valid) != count:
            raise WorkerError("Scientific field size differs from declared shape")
        if len(self.axes) != len(self.shape) or any(not isinstance(a, str) or not a or len(a) > 64 for a in self.axes) or len(set(self.axes)) != len(self.axes):
            raise WorkerError("Explicit unique axes are required")
        if len(self.coordinates) != len(self.shape):
            raise WorkerError("Explicit coordinates are required")
        for size, coordinates in zip(self.shape, self.coordinates):
            if type(coordinates) is not tuple or len(coordinates) != size or any(not finite(n) for n in coordinates):
                raise WorkerError("Invalid scientific coordinates")
        for text in (self.crs, self.unit):
            if text is not None and (not isinstance(text, str) or not text or len(text) > 256):
                raise WorkerError("Scientific metadata must be explicit or unknown")
        for value, valid in zip(self.values, self.valid):
            if type(valid) is not bool or (not valid and value is not None) or (valid and not finite(value)):
                raise WorkerError("Invalid scientific value or missingness mask")
        encoded(self.to_dict())

    def to_dict(self):
        return {"shape": list(self.shape), "axes": list(self.axes), "coordinates": [list(c) for c in self.coordinates],
                "crs": self.crs, "unit": self.unit, "values": list(self.values), "valid": list(self.valid)}

    @classmethod
    def from_dict(cls, value):
        try:
            if not isinstance(value, dict) or set(value) != {"shape", "axes", "coordinates", "crs", "unit", "values", "valid"}:
                raise ValueError("Unknown/missing scientific envelope fields")
            return cls(tuple(value["shape"]), tuple(value["axes"]), tuple(tuple(c) for c in value["coordinates"]),
                       value["crs"], value["unit"], tuple(value["values"]), tuple(value["valid"]))
        except (ValueError, TypeError, KeyError):
            raise WorkerError("Invalid scientific envelope") from None

    def same_layout(self, other):
        return (self.shape, self.axes, self.coordinates, self.crs, self.unit, self.valid) == (
            other.shape, other.axes, other.coordinates, other.crs, other.unit, other.valid)
