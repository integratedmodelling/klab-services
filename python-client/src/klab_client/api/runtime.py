from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any

from klab_client.api.knowledge import Observable
from klab_client.api.primitives import Notification


class Geometry(ABC):
    @abstractmethod
    def dimension(self) -> str: ...

    @abstractmethod
    def size(self) -> int: ...

    @abstractmethod
    def is_scalar(self) -> bool: ...

    @abstractmethod
    def encode(self, value: Any) -> str: ...


class Observation(ABC):
    @abstractmethod
    def get_urn(self) -> str: ...

    @abstractmethod
    def get_name(self) -> str | None: ...

    @abstractmethod
    def get_observable(self) -> Observable: ...

    @abstractmethod
    def get_value(self) -> Any: ...

    @abstractmethod
    def get_notifications(self) -> list[Notification]: ...


@dataclass(slots=True)
class GeometryImpl(Geometry):
    geometry_type: str = "scalar"
    shape: tuple[int, ...] = ()
    raw: dict[str, Any] = field(default_factory=dict, repr=False)

    def dimension(self) -> str:
        return self.geometry_type

    def size(self) -> int:
        if self.raw.get("empty"):
            return 0
        if self.geometry_type == "universal":
            from klab_client.errors import UnsupportedOperationError
            raise UnsupportedOperationError("Universal geometry has no finite cell count")
        if not self.shape:
            if not self.is_scalar():
                from klab_client.errors import UnsupportedOperationError
                raise UnsupportedOperationError("Geometry has no checked finite shape")
            return 1
        total = 1
        for n in self.shape:
            total *= n
        return total

    def is_scalar(self) -> bool:
        return self.geometry_type == "scalar" and len(self.shape) == 0

    def encode(self, value: Any = None) -> str:
        from klab_client.errors import UnsupportedOperationError
        if value is not None:
            raise UnsupportedOperationError("Geometry.encode encodes geometry, not arbitrary values")
        if self.geometry_type == "scalar" and not self.shape:
            return "1"
        if self.raw.get("key"):
            return self.raw["key"]
        raise UnsupportedOperationError("Use an explicit server geometry encoding for non-scalar geometry")

    def to_wire(self) -> dict[str, Any]:
        from klab_client.errors import UnsupportedOperationError
        if self.raw:
            return dict(self.raw)
        if self.geometry_type != "scalar" or self.shape:
            raise UnsupportedOperationError("Construct non-scalar geometry with GeometryImpl.from_wire")
        return {"@CLASS": "org.integratedmodelling.klab.api.geometry.impl.GeometryImpl",
                "dimensions": [], "granularity": "SINGLE", "scalar": True,
                "empty": False, "universal": False, "generic": False}

    @classmethod
    def from_wire(cls, payload: dict[str, Any]) -> "GeometryImpl":
        from klab_client.dto import object_payload
        raw = object_payload(payload, "geometry")
        dimensions = raw.get("dimensions")
        if not isinstance(dimensions, list):
            from klab_client.errors import ProtocolError
            raise ProtocolError("Geometry dimensions must be a list")
        if any(not isinstance(d, dict) for d in dimensions):
            from klab_client.errors import ProtocolError
            raise ProtocolError("Geometry dimensions must be objects")
        shape = tuple(n for d in dimensions for n in (d.get("shape") or []))
        if any(type(n) is not int or n < 0 for n in shape):
            from klab_client.errors import ProtocolError
            raise ProtocolError("Geometry shape must contain nonnegative integers")
        kind = "scalar" if raw.get("scalar") else "universal" if raw.get("universal") else "space"
        return cls(kind, shape, dict(raw))


@dataclass(slots=True)
class ObservationImpl(Observation):
    urn: str
    observable: Observable
    name: str | None = None
    value: Any = None
    notifications: list[Notification] = field(default_factory=list)
    id: int = -1
    geometry: GeometryImpl | None = None
    metadata: dict[str, Any] = field(default_factory=dict)
    raw: dict[str, Any] = field(default_factory=dict, repr=False)
    _context: Any = field(default=None, repr=False, compare=False)

    @property
    def units(self) -> str | None:
        unit = getattr(self.observable, "raw", {}).get("unit")
        return unit.get("definition") if isinstance(unit, dict) else None

    def fetch_data(self, offsets=(0,), *, curve="UNSPECIFIED", slice=None, semantics=None):
        """Read explicit cells from storage; metadata/value is not used as a substitute."""
        from klab_client.errors import UnsupportedOperationError
        if self._context is None:
            raise UnsupportedOperationError("Data retrieval requires a remotely bound observation")
        return self._context.fetch_data(self, offsets, curve=curve, slice=slice, semantics=semantics)

    def get_urn(self) -> str:
        return self.urn

    def get_name(self) -> str | None:
        return self.name

    def get_observable(self) -> Observable:
        return self.observable

    def get_value(self) -> Any:
        return self.value

    def get_notifications(self) -> list[Notification]:
        return self.notifications
