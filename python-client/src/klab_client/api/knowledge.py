from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from enum import Enum, auto
from typing import Any

from klab_client.api.primitives import DescriptionType, LogicalConnector, Notification, SemanticType


class Concept(ABC):
    @abstractmethod
    def get_type(self) -> set[SemanticType]: ...

    @abstractmethod
    def is_collective(self) -> bool: ...

    @abstractmethod
    def get_qualifier(self) -> LogicalConnector | None: ...

    @abstractmethod
    def singular(self) -> "Concept": ...

    @abstractmethod
    def collective(self) -> "Concept": ...

    @abstractmethod
    def get_notifications(self) -> list[Notification]: ...

    @abstractmethod
    def get_description_type(self) -> DescriptionType: ...


class ResolutionDirective(Enum):
    MISSING = auto()
    NODATA = auto()
    ERROR = auto()


class Observable(ABC):
    @abstractmethod
    def get_semantics(self) -> Concept: ...

    @abstractmethod
    def get_observer_semantics(self) -> Concept | None: ...

    @abstractmethod
    def get_description_type(self) -> DescriptionType: ...

    @abstractmethod
    def is_optional(self) -> bool: ...

    @abstractmethod
    def get_default_value(self) -> Any: ...

    @abstractmethod
    def get_resolution_directives(self) -> list[ResolutionDirective]: ...


@dataclass(slots=True)
class ConceptImpl(Concept):
    urn: str
    semantic_types: set[SemanticType] = field(default_factory=set)
    collective_flag: bool = False
    qualifier: LogicalConnector | None = None
    description_type: DescriptionType = DescriptionType.INSTANTIATION
    notifications: list[Notification] = field(default_factory=list)

    def get_type(self) -> set[SemanticType]:
        return self.semantic_types

    def is_collective(self) -> bool:
        return self.collective_flag

    def get_qualifier(self) -> LogicalConnector | None:
        return self.qualifier

    def singular(self) -> "ConceptImpl":
        return ConceptImpl(
            urn=self.urn,
            semantic_types=set(self.semantic_types),
            collective_flag=False,
            qualifier=self.qualifier,
            description_type=self.description_type,
            notifications=list(self.notifications),
        )

    def collective(self) -> "ConceptImpl":
        return ConceptImpl(
            urn=self.urn,
            semantic_types=set(self.semantic_types),
            collective_flag=True,
            qualifier=self.qualifier,
            description_type=self.description_type,
            notifications=list(self.notifications),
        )

    def get_notifications(self) -> list[Notification]:
        return self.notifications

    def get_description_type(self) -> DescriptionType:
        return self.description_type


@dataclass(slots=True)
class ObservableImpl(Observable):
    semantics: Concept
    observer_semantics: Concept | None = None
    optional: bool = False
    default_value: Any = None
    resolution_directives: list[ResolutionDirective] = field(default_factory=list)
    description_type: DescriptionType = DescriptionType.INSTANTIATION

    def get_semantics(self) -> Concept:
        return self.semantics

    def get_observer_semantics(self) -> Concept | None:
        return self.observer_semantics

    def get_description_type(self) -> DescriptionType:
        return self.description_type

    def is_optional(self) -> bool:
        return self.optional

    def get_default_value(self) -> Any:
        return self.default_value

    def get_resolution_directives(self) -> list[ResolutionDirective]:
        return self.resolution_directives
