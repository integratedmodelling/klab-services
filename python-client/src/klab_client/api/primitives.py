from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum, auto
from typing import Any


class SemanticType(Enum):
    SUBJECT = auto()
    EVENT = auto()
    RELATIONSHIP = auto()
    QUANTIFIABLE = auto()
    CATEGORY = auto()
    PRESENCE = auto()
    NOTHING = auto()


class DescriptionType(Enum):
    INSTANTIATION = auto()
    DETECTION = auto()
    QUANTIFICATION = auto()
    CATEGORIZATION = auto()


class LogicalConnector(Enum):
    UNION = auto()
    INTERSECTION = auto()
    EXCLUSION = auto()


class CRUDOperation(Enum):
    CREATE = auto()
    READ = auto()
    UPDATE = auto()
    DELETE = auto()


@dataclass(slots=True)
class Notification:
    message: str
    level: str = "info"


@dataclass(slots=True)
class Metadata:
    data: dict[str, Any] = field(default_factory=dict)


@dataclass(slots=True)
class ResourceSet:
    urns: list[str] = field(default_factory=list)


@dataclass(slots=True)
class Dataflow:
    urn: str = ""


@dataclass(slots=True)
class ContextInfo:
    session_id: str
    context_ids: list[str] = field(default_factory=list)


@dataclass(slots=True)
class Scope:
    id: str


@dataclass(slots=True)
class ServiceCapabilities:
    service_id: str


@dataclass(slots=True)
class KlabAsset:
    urn: str


@dataclass(slots=True)
class RuntimeAsset:
    id: str
