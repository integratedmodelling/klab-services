"""Native StorageScan.Slice metadata, not scheduling or revision discovery."""
from dataclasses import dataclass

from .errors import InvalidRequestError

_TYPES = ("INITIALIZATION", "TEMPORAL_TRANSITION", "INPUT_CHANGE", "EVENT")


def _validate(event_type, key, start, end):
    if not isinstance(event_type, str) or event_type not in _TYPES:
        raise InvalidRequestError("Slice type must be a maintained Scheduler.Event.Type")
    if not isinstance(key, str) or not key.strip():
        raise InvalidRequestError("Slice requires a nonblank event key")
    if any(type(v) is not int or not -(2**63) <= v <= 2**63 - 1 for v in (start, end)):
        raise InvalidRequestError("Slice start/end must be exact integers within Java long bounds")
    if event_type != "INITIALIZATION" and (end < start or end == start and event_type == "TEMPORAL_TRANSITION"):
        raise InvalidRequestError("Slice interval must be ordered; temporal transitions require positive duration")


@dataclass(frozen=True)
class ScanSlice:
    """An explicit native event slice: key and epoch-millisecond bounds are caller supplied.

    This does not establish that a slice exists or is committed, nor acquire a
    cross-read revision lease. No time labels, event key or model support is inferred.
    """
    type: str
    key: str
    start: int
    end: int

    def __post_init__(self):
        _validate(self.type, self.key, self.start, self.end)

    @classmethod
    def initialization(cls):
        """The maintained initialization event; not a temporal transition."""
        return cls("INITIALIZATION", "0-0", 0, 0)

    def to_wire(self):
        return {"type": self.type, "key": self.key, "start": self.start, "end": self.end}


def _slice_to_wire(value):
    if isinstance(value, ScanSlice):
        value = value.to_wire()
    if not isinstance(value, dict):
        raise InvalidRequestError("Slice requires a ScanSlice or explicit native slice dict")
    _validate(*(value.get(k) for k in ("type", "key", "start", "end")))
    # Preserve existing dictionary extension metadata without aliasing the caller's
    # top-level fields across async requests. No reconstruction of its opaque key.
    return dict(value)
