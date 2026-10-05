from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum, auto
from typing import Any


class SemanticType(Enum):
    OBSERVABLE = auto()
    PREDICATE = auto()
    QUALITY = auto()
    PROCESS = auto()
    EXTENSIVE = auto()
    INTENSIVE = auto()
    IDENTITY = auto()
    INDIVIDUAL = auto()
    ATTRIBUTE = auto()
    REALM = auto()
    SUBJECTIVE = auto()
    INTERNAL = auto()
    ROLE = auto()
    DENIABLE = auto()
    CONFIGURATION = auto()
    ABSTRACT = auto()
    ORDERING = auto()
    CLASS = auto()
    QUANTITY = auto()
    DOMAIN = auto()
    ENERGY = auto()
    ENTROPY = auto()
    LENGTH = auto()
    MASS = auto()
    VOLUME = auto()
    WEIGHT = auto()
    MONEY = auto()
    DURATION = auto()
    AREA = auto()
    ACCELERATION = auto()
    PRIORITY = auto()
    ELECTRIC_POTENTIAL = auto()
    CHARGE = auto()
    RESISTANCE = auto()
    RESISTIVITY = auto()
    PRESSURE = auto()
    ANGLE = auto()
    VELOCITY = auto()
    TEMPERATURE = auto()
    VISCOSITY = auto()
    AGENT = auto()
    FUNCTIONAL = auto()
    STRUCTURAL = auto()
    BIDIRECTIONAL = auto()
    UNIDIRECTIONAL = auto()
    DELIBERATIVE = auto()
    INTERACTIVE = auto()
    REACTIVE = auto()
    COUNTABLE = auto()
    UNCERTAINTY = auto()
    PROBABILITY = auto()
    PROPORTION = auto()
    PERCENTAGE = auto()
    NUMEROSITY = auto()
    DISTANCE = auto()
    RATIO = auto()
    VALUE = auto()
    OCCURRENCE = auto()
    EXTENT = auto()
    MACRO = auto()
    AMOUNT = auto()
    ANY = auto()
    ALL = auto()
    NONE = auto()
    MAGNITUDE = auto()
    UNION = auto()
    INTERSECTION = auto()
    MONETARY_VALUE = auto()
    RESCALING = auto()
    CHANGE = auto()
    RATE = auto()
    CHANGED = auto()
    SEALED = auto()
    AUTHORITY_IDENTITY = auto()
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
    raw: dict[str, Any] = field(default_factory=dict, repr=False)


@dataclass(slots=True)
class Dataflow:
    urn: str = ""
    raw: dict[str, Any] = field(default_factory=dict, repr=False)


@dataclass(slots=True)
class ContextInfo:
    session_id: str
    context_ids: list[str] = field(default_factory=list)
    raw: dict[str, Any] = field(default_factory=dict, repr=False)


@dataclass(slots=True)
class Scope:
    id: str


@dataclass(slots=True)
class ServiceCapabilities:
    service_id: str
    raw: dict[str, Any] = field(default_factory=dict, repr=False)


@dataclass(slots=True)
class KlabAsset:
    urn: str
    raw: dict[str, Any] = field(default_factory=dict, repr=False)


@dataclass(slots=True)
class RuntimeAsset:
    id: str
