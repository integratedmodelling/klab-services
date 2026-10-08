"""Typed inputs for implemented remote observation contracts, never local authority."""
from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass, field
import json
import math
from types import MappingProxyType
from typing import Any, Mapping

from .api.knowledge import ObservableImpl
from .api.runtime import GeometryImpl, ObservationImpl
from .dto import OBSERVATION_CLASS, check_notifications, observable_to_wire, observation_to_wire
from .errors import InvalidRequestError, UnsupportedOperationError

_CONSTRAINT_CLASS = "org.integratedmodelling.klab.api.services.resolver.objects.ResolutionConstraintImpl"
_ACTIVITIES = frozenset({"INSTANTIATION", "ACKNOWLEDGEMENT", "DETECTION", "SIMULATION",
    "MEASURE", "QUANTIFICATION", "VALUATION", "CATEGORIZATION", "VERIFICATION",
    "CLASSIFICATION", "CHARACTERIZATION", "TRANSFORMATION", "CONNECTION"})


def _text(value, label):
    if not isinstance(value, str) or not value.strip():
        raise InvalidRequestError(f"{label} requires nonblank text")
    return value


def _number(value, label):
    try:
        finite = type(value) in (int, float) and math.isfinite(value)
    except OverflowError:
        finite = False
    if not finite:
        raise InvalidRequestError(f"{label} requires a finite number, not a boolean")
    return value


def _positive_id(value):
    if type(value) is not int or value <= 0 or value > 2**63 - 1:
        raise InvalidRequestError("Selection requires a positive signed-64-bit observation ID")
    return value


def _json(value):
    try:
        return json.dumps(value, sort_keys=True, allow_nan=False, separators=(",", ":"))
    except (TypeError, ValueError, RecursionError):
        raise InvalidRequestError("Request values must be finite JSON data") from None


def _freeze(value):
    if isinstance(value, dict):
        return MappingProxyType({key: _freeze(item) for key, item in value.items()})
    if isinstance(value, list):
        return tuple(_freeze(item) for item in value)
    return value


def _mapping(value, reserved=()):
    if not isinstance(value, Mapping) or any(not isinstance(key, str) for key in value):
        raise InvalidRequestError("Metadata/extensions require a mapping with text keys")
    if set(value).intersection(reserved):
        raise InvalidRequestError("Extensions cannot overwrite reserved request fields")
    return json.loads(_json(dict(value)))


def _observable(value):
    if not isinstance(value, ObservableImpl):
        raise InvalidRequestError("Use a remotely resolved ObservableImpl or definition text")
    wire = observable_to_wire(value)
    activity = wire.get("contextualization")
    if not isinstance(activity, str) or activity not in _ACTIVITIES:
        raise UnsupportedOperationError("Unknown, missing or VOID semantic activity is not a typed observation contract")
    check_notifications(wire, lambda text: "Resolved observable contains error notifications")
    check_notifications(wire.get("semantics", {}), lambda text: "Resolved semantics contain error notifications")
    return wire


@dataclass(frozen=True)
class ResolutionConstraint:
    """Only constraint kinds with implemented consumers; other kinds fail explicitly."""
    kind: str
    values: tuple[Any, ...]

    def __post_init__(self):
        if not isinstance(self.kind, str):
            raise InvalidRequestError("Constraint kind must be text")
        if self.kind not in {"ResolutionNamespace", "ResolutionProject", "Scenarios", "Observer"}:
            raise UnsupportedOperationError("Constraint kind has no supported typed consumer")
        if not isinstance(self.values, (list, tuple)) or not self.values:
            raise InvalidRequestError("Constraint values require a nonempty sequence")
        if self.kind != "Scenarios" and len(self.values) != 1:
            raise InvalidRequestError("This constraint requires exactly one value")
        values = tuple(_positive_id(v) if self.kind == "Observer" else _text(v, "Constraint") for v in self.values)
        if len(set(values)) != len(values):
            raise InvalidRequestError("Duplicate constraint values are ambiguous")
        object.__setattr__(self, "values", values)

    def to_wire(self):
        return {"@CLASS": _CONSTRAINT_CLASS, "type": self.kind, "data": list(self.values)}


@dataclass(frozen=True)
class ObservationOptions:
    namespace: str | None = None
    project: str | None = None
    scenarios: tuple[str, ...] = ()
    observer: int | ObservationImpl | None = None
    constraints: tuple[ResolutionConstraint, ...] = ()
    selected_model: str | None = None
    _observer_binding: tuple[str, str] | None = field(default=None, init=False, repr=False)

    def __post_init__(self):
        if self.selected_model is not None:
            raise UnsupportedOperationError("Forced model selection (UsingModel) has no implemented server consumer")
        if not isinstance(self.scenarios, (list, tuple)) or not isinstance(self.constraints, (list, tuple)):
            raise InvalidRequestError("Scenarios and constraints require sequences")
        items = list(self.constraints)
        if any(not isinstance(item, ResolutionConstraint) for item in items):
            raise InvalidRequestError("Use typed ResolutionConstraint objects")
        for kind, value in (("ResolutionNamespace", self.namespace), ("ResolutionProject", self.project)):
            if value is not None:
                items.append(ResolutionConstraint(kind, (value,)))
        if self.scenarios:
            items.append(ResolutionConstraint("Scenarios", self.scenarios))
        observer = self.observer
        if isinstance(observer, ObservationImpl):
            if observer._context is None:
                raise InvalidRequestError("Observer objects must be bound to a Runtime/context")
            object.__setattr__(self, "_observer_binding", (
                observer._context.client.transport.endpoint("runtime").url.rstrip("/"), observer._context.id))
            observer = observer.id
        if observer is not None:
            items.append(ResolutionConstraint("Observer", (observer,)))
        if len({item.kind for item in items}) != len(items):
            raise InvalidRequestError("Conflicting or duplicate resolution constraints")
        object.__setattr__(self, "observer", observer)
        object.__setattr__(self, "scenarios", tuple(self.scenarios))
        object.__setattr__(self, "constraints", tuple(sorted(items, key=lambda item: item.kind)))


@dataclass(frozen=True)
class RectangularGeometry:
    """Square-cell EPSG:3857 rectangle; arbitrary geometry remains a checked DTO."""
    west: float
    east: float
    south: float
    north: float
    columns: int
    rows: int

    def __post_init__(self):
        for label in ("west", "east", "south", "north"):
            _number(getattr(self, label), label)
        for value in (self.columns, self.rows):
            _positive_id(value)
        if self.east <= self.west or self.north <= self.south:
            raise InvalidRequestError("Rectangle bounds must have positive extent")
        width = _number(self.east-self.west, "Rectangle width")
        height = _number(self.north-self.south, "Rectangle height")
        if self.columns * self.rows > 2**63 - 1 or width/self.columns <= 0 or height/self.rows <= 0:
            raise InvalidRequestError("Grid cell count/span exceeds the supported numeric range")
        if not math.isclose(width/self.columns, height/self.rows, rel_tol=1e-12):
            raise UnsupportedOperationError("This rectangle facade supports square cells only")

    def to_geometry(self):
        w, e, s, n = self.west, self.east, self.south, self.north
        return GeometryImpl.from_wire({
            "@CLASS": "org.integratedmodelling.klab.api.geometry.impl.GeometryImpl",
            "granularity": "SINGLE", "scalar": False, "empty": False, "universal": False,
            "dimensions": [{"@CLASS": "org.integratedmodelling.klab.api.geometry.impl.GeometryImpl$DimensionImpl",
                "type": "SPACE", "regular": True, "dimensionality": 2, "shape": [self.columns, self.rows],
                "generic": False, "coverage": 1.0, "parameters": {
                    "@CLASS": "org.integratedmodelling.klab.api.collections.impl.ParametersImpl",
                    "delegate": {"shape": f"EPSG:3857 POLYGON (({w} {s},{w} {n},{e} {n},{e} {s},{w} {s}))",
                        "sgrid": f"{(e-w)/self.columns} m", "proj": "EPSG:3857"}, "unnamedKeys": []}}]})


@dataclass(frozen=True)
class GridOptions:
    """Inline context lattice. Numeric span and anchor use the selected CRS units."""
    anchor_x: float
    anchor_y: float
    span: float
    projection: str = "EPSG:3857"
    strict: bool = True
    snap: bool = False

    def __post_init__(self):
        for label in ("anchor_x", "anchor_y", "span"):
            _number(getattr(self, label), label)
        if self.span <= 0:
            raise InvalidRequestError("Grid span must be positive")
        _text(self.projection, "Projection")
        if type(self.strict) is not bool or type(self.snap) is not bool:
            raise InvalidRequestError("Grid strict/snap must be booleans")

    def to_wire(self):
        return {"anchor": f"POINT ({self.anchor_x} {self.anchor_y})", "projection": self.projection,
                "span": self.span, "strict": self.strict, "snap": self.snap}


@dataclass(frozen=True)
class ContextOptions:
    name: str = "Python context"
    persistence: str = "ONE_OFF"
    grid: GridOptions | str | None = None
    observer: Any = None
    extensions: Mapping[str, Any] = field(default_factory=dict, repr=False)
    _wire: str = field(init=False, repr=False)

    def __post_init__(self):
        _text(self.name, "Context name")
        if not isinstance(self.persistence, str) or self.persistence not in {
                "ONE_OFF", "EXPLICIT_ACTION", "IDLE_TIMEOUT", "SERVICE_SHUTDOWN", "REINITIALIZED_ON_TIMEOUT"}:
            raise InvalidRequestError("Unknown persistence policy")
        if self.observer is not None:
            raise UnsupportedOperationError("Choose an existing observer at observation submission, not context creation")
        values = _mapping(self.extensions, {"@CLASS", "id", "owner", "serviceId", "serviceUrl", "url",
            "accessRights", "name", "persistence", "observer", "gridDefinition", "gridUrn", "gridAlignment",
            "timeout", "timeoutUnit", "createWhenAbsent"})
        values.update(name=self.name, persistence=self.persistence)
        if isinstance(self.grid, GridOptions):
            values["gridDefinition"] = self.grid.to_wire()
        elif self.grid is not None:
            values["gridUrn"] = _text(self.grid, "Grid URN")
        object.__setattr__(self, "extensions", _freeze(_mapping(self.extensions)))
        object.__setattr__(self, "_wire", _json(values))

    def to_wire(self):
        return json.loads(self._wire)


@dataclass(frozen=True)
class ObservationRequest:
    observable: ObservableImpl | str
    options: ObservationOptions = field(default_factory=ObservationOptions)
    urn: str = ""
    name: str | None = None
    geometry: GeometryImpl | RectangularGeometry | None = None
    metadata: Mapping[str, Any] = field(default_factory=dict, repr=False)
    extensions: Mapping[str, Any] = field(default_factory=dict, repr=False)
    _wire: str = field(init=False, repr=False)
    _observable_wire: str | None = field(init=False, repr=False)

    def __post_init__(self):
        if not isinstance(self.options, ObservationOptions):
            raise InvalidRequestError("Use ObservationOptions")
        if not isinstance(self.urn, str):
            raise InvalidRequestError("Observation URN must be text")
        if self.name is not None:
            _text(self.name, "Observation name")
        if isinstance(self.observable, str):
            _text(self.observable, "Observable definition")
            wire = None
        else:
            wire = _json(_observable(self.observable))
            object.__setattr__(self, "observable", deepcopy(self.observable))
        geometry = self.geometry
        if isinstance(geometry, RectangularGeometry):
            geometry = geometry.to_geometry()
        if geometry is not None and not isinstance(geometry, GeometryImpl):
            raise InvalidRequestError("Use RectangularGeometry or a checked GeometryImpl")
        metadata = _mapping(self.metadata)
        extensions = _mapping(self.extensions, {"@CLASS", "id", "urn", "name", "observable", "geometry",
            "metadata", "value", "empty", "notifications", "resolvedCoverage", "agentName", "resolutionConstraints"})
        values = {**extensions, "@CLASS": OBSERVATION_CLASS, "id": -1, "urn": self.urn or None,
            "name": self.name, "metadata": {"@CLASS": "org.integratedmodelling.klab.api.collections.impl.MetadataImpl",
                "delegate": metadata, "unnamedKeys": []}}
        if geometry is not None:
            encoded = geometry.to_wire()
            GeometryImpl.from_wire(encoded)
            values["geometry"] = encoded
        object.__setattr__(self, "geometry", deepcopy(geometry))
        object.__setattr__(self, "metadata", _freeze(metadata))
        object.__setattr__(self, "extensions", _freeze(extensions))
        object.__setattr__(self, "_observable_wire", wire)
        object.__setattr__(self, "_wire", _json(values))

    def to_wire(self, resolved: ObservableImpl | None = None):
        if self._observable_wire is None and resolved is None:
            raise InvalidRequestError("Resolve the definition through a configured Reasoner before encoding")
        values = json.loads(self._wire)
        values["observable"] = json.loads(self._observable_wire) if self._observable_wire is not None else _observable(resolved)
        return observation_to_wire(values)


def observe(context, request, options=None):
    """Resolve only when requested, then submit once through the existing Job path."""
    from .client import Context, scope_token
    if isinstance(request, ObservationRequest):
        if options is not None:
            raise InvalidRequestError("Request already carries its options")
    else:
        request = ObservationRequest(request, options=ObservationOptions() if options is None else options)
    token = scope_token(context)
    if ".".join(token.split("#")[0].split(".")[:2]) != context.id:
        raise InvalidRequestError("Focused scope token differs from the root context identity")
    binding = request.options._observer_binding
    if binding is not None and binding != (context.client.transport.endpoint("runtime").url.rstrip("/"), context.id):
        raise InvalidRequestError("Observer belongs to a different Runtime/context")
    observers = [item.values[0] for item in request.options.constraints if item.kind == "Observer"]
    if observers:
        base, marker, existing = token.partition("#")
        if marker and int(existing) != observers[0]:
            raise InvalidRequestError("Observer option conflicts with the existing scope selection")
        token = base + f"#{observers[0]}"
    resolved = context.client.reasoner.resolve_observable(request.observable) if request._observable_wire is None else None
    return Context(context.client, context.configuration, owned=context.owned, token=token).submit(
        request.to_wire(resolved), resolution_constraints=[item.to_wire() for item in request.options.constraints])
