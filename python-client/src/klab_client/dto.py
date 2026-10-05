"""Explicit conversion of checked Java transport DTOs, retaining optional fields."""
from __future__ import annotations

from copy import deepcopy
from decimal import Decimal, InvalidOperation
from typing import Any

from .errors import InvalidRequestError, ProtocolError, ServerError, UnsupportedOperationError
from .api.knowledge import ConceptImpl, ObservableImpl, ResolutionDirective
from .api.primitives import LogicalConnector, Notification, SemanticType

OBSERVATION_CLASS = "org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl"
CONFIGURATION_CLASS = "org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl"


def object_payload(payload, label):
    if not isinstance(payload, dict):
        raise ProtocolError(f"Expected a {label} JSON object; check service revision")
    return payload


def required_text(payload, key):
    value = payload.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ProtocolError(f"Missing/non-text required field {key}; check service revision")
    return value


def notifications(payload) -> list[Notification]:
    result = []
    values = payload.get("notifications", [])
    if not isinstance(values, list):
        raise ProtocolError("notifications must be a list")
    for value in values:
        value = object_payload(value, "notification")
        result.append(Notification(required_text(value, "message"), value.get("level", "INFO")))
    return result


def check_notifications(payload, redact=lambda text: text):
    errors = [n.message for n in notifications(payload) if str(n.level).upper() in {"ERROR", "SEVERE"}]
    if errors:
        raise ServerError(redact("; ".join(errors)))


def concept_from_wire(payload):
    payload = object_payload(payload, "concept")
    urn = required_text(payload, "urn")
    types = payload.get("type", [])
    if not isinstance(types, list) or any(not isinstance(t, str) for t in types):
        raise ProtocolError("Concept type must be enum names")
    qualifier = payload.get("qualifier")
    if qualifier is not None and qualifier not in LogicalConnector.__members__:
        raise ProtocolError("Unsupported concept qualifier")
    return ConceptImpl(urn, {SemanticType[t] for t in types if t in SemanticType.__members__},
                       payload.get("collective", False),
                       LogicalConnector[qualifier] if qualifier else None,
                       notifications=notifications(payload), raw=deepcopy(payload))


def observable_from_wire(payload):
    payload = object_payload(payload, "observable")
    required_text(payload, "urn")
    directives = payload.get("resolutionDirectives", [])
    if not isinstance(directives, list):
        raise ProtocolError("resolutionDirectives must be a list")
    return ObservableImpl(concept_from_wire(payload.get("semantics")),
                          concept_from_wire(payload["observerSemantics"]) if payload.get("observerSemantics") else None,
                          payload.get("optional", False), payload.get("defaultValue"),
                          [ResolutionDirective[d] for d in directives if d in ResolutionDirective.__members__],
                          raw=deepcopy(payload))


def observable_to_wire(observable):
    raw = getattr(observable, "raw", None)
    if not raw or "@CLASS" not in raw:
        raise UnsupportedOperationError("Resolve the observable remotely before submitting; local semantics are not server IDs")
    return deepcopy(raw)


def map_values(payload):
    payload = object_payload(payload, "metadata")
    return deepcopy(object_payload(payload.get("delegate", payload), "metadata values"))


def observation_to_wire(observation):
    if isinstance(observation, dict):
        payload = deepcopy(observation)
        if payload.get("@CLASS") != OBSERVATION_CLASS:
            raise InvalidRequestError("Observation mapping requires the checked ObservationImpl discriminator")
        object_payload(payload.get("observable"), "observable")
        return payload
    payload = {**deepcopy(observation.raw), "@CLASS": OBSERVATION_CLASS, "id": observation.id,
               "observable": observable_to_wire(observation.observable),
               "urn": observation.urn or None, "name": observation.name,
               "metadata": {"@CLASS": "org.integratedmodelling.klab.api.collections.impl.MetadataImpl",
                            "delegate": deepcopy(observation.metadata), "unnamedKeys": []}}
    if observation.geometry is not None:
        payload["geometry"] = observation.geometry.to_wire()
    # A locally supplied value is transported only when explicitly provided.
    if observation.value is not None:
        payload["value"] = observation.value
    return payload


def observation_from_wire(payload, context=None):
    from .api.runtime import GeometryImpl, ObservationImpl
    payload = object_payload(payload, "observation")
    if type(payload.get("id")) is not int or payload["id"] < 0:
        raise ProtocolError("Result is not a committed observation or a query result (id >= 0 required)")
    observable = observable_from_wire(payload.get("observable"))
    geometry = GeometryImpl.from_wire(payload["geometry"]) if payload.get("geometry") else None
    return ObservationImpl(urn=required_text(payload, "urn"), observable=observable,
                           name=payload.get("name"), value=payload.get("value"),
                           notifications=notifications(payload), id=payload["id"], geometry=geometry,
                           metadata=map_values(payload.get("metadata", {})),
                           raw=deepcopy(payload), _context=context)


def configuration_to_wire(configuration):
    return {"@CLASS": CONFIGURATION_CLASS, **deepcopy(configuration)}


def storage_semantics(observable):
    """Portable native/simple-unit snapshot, matching StorageScan.semantics.

    Contextual units, ranges and currencies need richer contracts and are rejected.
    """
    raw = observable_to_wire(observable)
    unit = raw.get("unit")
    if raw.get("range") or raw.get("currency") or (unit and (
            unit.get("contextual") or unit.get("aggregatedDimensions"))):
        raise UnsupportedOperationError("Build an explicit StorageScan.Semantics for contextual units/ranges/currencies")
    return {"observable": required_text(raw, "urn"),
            "unit": required_text(unit, "definition") if unit else "",
            "range": "", "currency": "", "contextualDimensions": "{}" if unit else "",
            "meaning": required_text(raw["semantics"], "urn") + "|"
                       + (raw.get("contextualization") or "null") + "|"
                       + (raw.get("observerSemantics") or {}).get("urn", "")}


def decode_cell(text: str, artifact_type: str):
    if text == "null":
        return None
    if artifact_type == "BOOLEAN":
        if text not in {"true", "false"}:
            raise ProtocolError("Invalid boolean cell text")
        return text == "true"
    if artifact_type == "NUMBER":
        # Decimal avoids truncating long values or rounding scientific precision.
        try:
            value = Decimal(text)
        except InvalidOperation:
            raise ProtocolError("Invalid numeric cell text") from None
        return int(value) if value.is_finite() and value == value.to_integral_value() else value
    if artifact_type in {"CONCEPT", "TEXT"}:
        return text
    raise UnsupportedOperationError(f"No checked text decoder for artifact type {artifact_type}")
