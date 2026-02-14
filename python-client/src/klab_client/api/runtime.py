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

    def dimension(self) -> str:
        return self.geometry_type

    def size(self) -> int:
        if not self.shape:
            return 1
        total = 1
        for n in self.shape:
            total *= n
        return total

    def is_scalar(self) -> bool:
        return len(self.shape) == 0

    def encode(self, value: Any) -> str:
        return str(value)


@dataclass(slots=True)
class ObservationImpl(Observation):
    urn: str
    observable: Observable
    name: str | None = None
    value: Any = None
    notifications: list[Notification] = field(default_factory=list)

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
