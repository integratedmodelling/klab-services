"""Read-only, bounded plan/recorded-activity projections. Never replay or infer execution."""
from collections.abc import Mapping
from dataclasses import dataclass, field
from html import escape
import json
import math

from ._inspection import bounds, freeze, html_summary
from .client import scope_token
from .dto import notifications, object_payload, required_text
from .errors import InvalidRequestError, ProtocolError, UnsupportedOperationError
from .provenance import ProvenanceView, _node, fetch_provenance


def _plain(value, depth=0):
    if depth > 32:
        raise ProtocolError("Evidence nesting exceeds 32 levels")
    if isinstance(value, Mapping):
        return {key: _plain(item, depth + 1) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_plain(item, depth + 1) for item in value]
    return value


def _payload(value, budget, redact):
    bounds(budget, 16 * 1024 * 1024, "Evidence byte budget")
    plain = object_payload(_plain(value), "evidence")
    try:
        encoded = json.dumps(plain, allow_nan=False, ensure_ascii=True).encode("utf-8")
    except (TypeError, ValueError, OverflowError):
        raise ProtocolError("Evidence must be finite JSON data") from None
    if len(encoded) > budget:
        raise ProtocolError("Evidence exceeds the byte budget")
    return plain, freeze(plain, redact)


def _array(value, label):
    if not isinstance(value, list):
        raise ProtocolError(f"{label} must be an array")
    return value


@dataclass(frozen=True)
class ChartNode:
    id: str
    parent_id: str | None
    labels: tuple[str, ...]
    raw: Mapping = field(repr=False)


@dataclass(frozen=True)
class ChartPort:
    id: str
    node_id: str
    role: str
    raw: Mapping = field(repr=False)


@dataclass(frozen=True)
class ChartLink:
    id: str
    sources: tuple[str, ...]
    targets: tuple[str, ...]
    raw: Mapping = field(repr=False)


@dataclass(frozen=True)
class FlowChartEvidence:
    phase: str
    nodes: tuple[ChartNode, ...]
    ports: tuple[ChartPort, ...]
    links: tuple[ChartLink, ...]
    raw: Mapping = field(repr=False)

    def summary(self):
        return (f"{self.phase}: {len(self.nodes)} nodes, {len(self.ports)} ports, {len(self.links)} links\n"
                "Recorded/planned structure is not proof of execution or durable root commit\n"
                + "\n".join(f"{node.id}: {'; '.join(node.labels)}" for node in self.nodes))

    def _repr_html_(self):
        rows = "".join("<tr><td>" + escape(node.id) + "</td><td>" + escape("; ".join(node.labels)) + "</td></tr>"
                       for node in self.nodes)
        return html_summary(self.summary()) + "<table><tr><th>Node</th><th>Labels</th></tr>" + rows + "</table>"


def decode_flowchart(payload, *, max_nodes=256, max_links=512, max_response_bytes=1024 * 1024,
                     phase="unassociated plan", redact=lambda value: value):
    """Decode ELK containment and explicit connectivity; preserve parallel/hyper/reference edges."""
    bounds(max_nodes, 4096, "Chart node/port budget")
    bounds(max_links, 8192, "Chart link budget")
    if phase not in ("unassociated plan", "resolution plan", "contextualization plan"):
        raise InvalidRequestError("FlowChart evidence must remain a plan, not an execution claim")
    payload, raw = _payload(payload, max_response_bytes, redact)
    nodes, ports, links, ids = [], [], [], set()

    def identity(row):
        value = required_text(row, "id")
        if redact(value) != value:
            raise ProtocolError("FlowChart identity contains configured credential material")
        if value in ids:
            raise ProtocolError("FlowChart identities must be graph-wide unique")
        ids.add(value)
        return value

    def visit(row, parent, depth=0):
        row = object_payload(row, "chart node")
        if depth > 16 or len(nodes) + len(ports) >= max_nodes:
            raise ProtocolError("FlowChart exceeds the node/port or containment depth budget")
        id = identity(row)
        labels = tuple(required_text(object_payload(label, "chart label"), "text")
                       for label in _array(row.get("labels", []), "labels"))
        nodes.append(ChartNode(id, parent, tuple(redact(label) for label in labels), freeze(row, redact)))
        for port in _array(row.get("ports", []), "ports"):
            port = object_payload(port, "chart port")
            if len(nodes) + len(ports) >= max_nodes:
                raise ProtocolError("FlowChart exceeds the node/port budget")
            if port.get("role") not in ("INPUT", "OUTPUT"):
                raise ProtocolError("Unknown FlowChart port role")
            ports.append(ChartPort(identity(port), id, port["role"], freeze(port, redact)))
        for edge in _array(row.get("edges", []), "edges"):
            edge = object_payload(edge, "chart link")
            if len(links) >= max_links:
                raise ProtocolError("FlowChart exceeds the link budget")
            endpoints = []
            for name in ("sources", "targets"):
                values = _array(edge.get(name), name)
                if not values or any(not isinstance(value, str) or not value.strip() for value in values):
                    raise ProtocolError("FlowChart links require nonempty endpoint identities")
                endpoints.append(tuple(values))
            links.append(ChartLink(identity(edge), *endpoints, freeze(edge, redact)))
        for child in _array(row.get("children", []), "children"):
            visit(child, id, depth + 1)

    visit(payload.get("root"), None)
    endpoints = {node.id for node in nodes} | {port.id for port in ports}
    if any(id not in endpoints for link in links for id in (*link.sources, *link.targets)):
        raise ProtocolError("FlowChart contains a dangling node/port reference")
    return FlowChartEvidence(phase, tuple(nodes), tuple(ports), tuple(links), raw)


@dataclass(frozen=True)
class ActuatorEvidence:
    path: tuple[int, ...]
    id: int
    actuator_type: str
    strategy_urn: str | None
    calls: tuple[str, ...]
    raw: Mapping = field(repr=False)


@dataclass(frozen=True)
class DataflowEvidence:
    outcome: str
    resolved_coverage: float | None
    actuators: tuple[ActuatorEvidence, ...]
    diagnostics: tuple[str, ...]
    raw: Mapping = field(repr=False)
    source: str = "separately requested/unassociated plan; not recorded execution"

    def summary(self):
        return f"Resolution: {self.outcome}; {len(self.actuators)} actuator occurrences\n{self.source}\n" + "\n".join(self.diagnostics)

    def _repr_html_(self):
        return html_summary(self.summary())


def decode_dataflow(payload, *, max_actuators=256, max_response_bytes=1024 * 1024, redact=lambda value: value):
    """Project actual Dataflow fields without encoding, execution or portable replay."""
    bounds(max_actuators, 4096, "Actuator budget")
    payload, raw = _payload(payload, max_response_bytes, redact)
    outcome = payload.get("resolutionOutcome")
    if outcome not in ("RESOLVED", "NO_MODEL", "FAILED"):
        raise UnsupportedOperationError("Missing/unknown Java resolution outcome; do not infer it from Job completion")
    diagnostics = tuple(redact(item.message) for item in notifications(payload))
    computation = _array(payload.get("computation", []), "Dataflow computation")
    empty = payload.get("empty")
    if type(empty) is not bool or outcome != "FAILED" and empty or outcome == "NO_MODEL" and computation:
        raise ProtocolError("Dataflow outcome/empty/computation fields contradict the Java contract")
    coverage = payload.get("resolvedCoverage")
    if coverage is not None and (type(coverage) not in (int, float) or not 0 <= coverage <= 1 or not math.isfinite(coverage)):
        raise ProtocolError("Resolved coverage must be a finite fraction or unavailable")
    actuators = []

    def visit(row, path):
        row = object_payload(row, "actuator")
        if len(actuators) >= max_actuators:
            raise ProtocolError("Dataflow exceeds the actuator budget")
        if (row.get("actuatorType") not in ("OBSERVE", "RESOLVE", "REFERENCE", "UPDATE")
                or type(row.get("id")) is not int or not -(2**63) <= row["id"] < 2**63):
            raise ProtocolError("Unknown actuator type or invalid identity")
        strategy = row.get("strategyUrn")
        if strategy is not None and not isinstance(strategy, str):
            raise ProtocolError("Actuator strategy must be text or null")
        calls = tuple(required_text(object_payload(call, "service call"), "urn")
                      for call in _array(row.get("computation", []), "actuator computation"))
        actuators.append(ActuatorEvidence(path, row["id"], row["actuatorType"], redact(strategy) if strategy is not None else None,
                                         tuple(redact(call) for call in calls), freeze(row, redact)))
        for index, child in enumerate(_array(row.get("children", []), "actuator children")):
            visit(child, (*path, index))

    for index, actuator in enumerate(computation):
        visit(actuator, (index,))
    return DataflowEvidence(outcome, coverage, tuple(actuators), diagnostics, raw)


@dataclass(frozen=True)
class ActivityEvidence:
    id: int
    urn: str | None
    activity_type: str
    outcome: str | None
    charts: tuple[FlowChartEvidence, ...]
    missing: tuple[str, ...]
    raw: Mapping = field(repr=False)


@dataclass(frozen=True)
class ExecutionEvidence:
    provenance: ProvenanceView
    activities: tuple[ActivityEvidence, ...]
    diagnostics: tuple[str, ...]
    partial: bool = True
    durable_root_commit: str = "unknown: activity completion and resolution are not durable root commit proof"

    def summary(self):
        return (f"Recorded evidence for observation {self.provenance.observation.observation_id}: {len(self.activities)} activities\n"
                + "\n".join(f"Activity {activity.id}: {activity.activity_type} / {activity.outcome or 'outcome unavailable'}; {len(activity.charts)} plans"
                             + "\n" + "\n".join(activity.missing) for activity in self.activities) + "\nPartial evidence\n" + self.durable_root_commit
                + "\n" + "\n".join(self.diagnostics))

    def _repr_html_(self):
        return html_summary(self.summary()) + "".join(chart._repr_html_() for activity in self.activities for chart in activity.charts)


def fetch_execution_evidence(observation, *, limit=64, offset=0, activity_limit=16,
                             max_response_bytes=1024 * 1024, max_nodes=256, max_links=512):
    """Refresh one recorded neighborhood and bounded scoped activities, never issue a new resolve.

    Current observation membership and Runtime/context binding are checked by the
    existing provenance operation. Activity lookups remain scoped and authorized.
    Missing diagrams and unexamined activities are explicit partial evidence.
    """
    bounds(activity_limit, 64, "Activity fetch budget")
    bounds(max_nodes, 4096, "Chart node/port budget")
    bounds(max_links, 8192, "Chart link budget")
    view = fetch_provenance(observation, limit=limit, offset=offset, max_response_bytes=max_response_bytes)
    context = observation._context
    client = context.client
    activities, candidates, diagnostics = [], {}, []
    for link in view.links:
        for node in (link.source, link.target):
            if node.kind == "activity":
                if node.id in candidates and candidates[node.id].urn != node.urn:
                    raise ProtocolError("Conflicting activity identities in recorded neighborhood")
                candidates[node.id] = node
    if len(candidates) > activity_limit:
        diagnostics.append("Activity fetch budget reached; additional recorded activities remain unexamined")
    for node in list(candidates.values())[:activity_limit]:
        try:
            rows = client.transport.request("runtime", "POST", "/api/v1/query",
                json={"type": "QUERY", "resultType": "ACTIVITY", "id": node.id, "limit": 2},
                scope=scope_token(context), service_id=view.observation.runtime_id, max_response_bytes=max_response_bytes)
        except InvalidRequestError as failure:
            if getattr(failure, "status_code", None) == 422:
                raise UnsupportedOperationError("This Runtime does not support scoped activity retrieval") from None
            raise
        if not isinstance(rows, list) or len(rows) > 1:
            raise ProtocolError("Scoped activity lookup must return at most one activity")
        missing, charts = [], []
        if rows:
            current = _node(rows[0], client.transport.redact)
            if current.kind != "activity" or current.id != node.id or current.urn != node.urn:
                raise ProtocolError("Scoped activity lookup returned a different activity identity")
            node = current
        else:
            missing.append("Activity metadata unavailable from the current scoped query")
        declared_observation = node.raw.get("observationUrn")
        if declared_observation is not None and declared_observation != observation.urn:
            raise ProtocolError("Activity observation association differs from the requested observation")
        metadata = node.raw.get("metadata")
        if metadata is not None:
            if not isinstance(metadata, Mapping):
                raise ProtocolError("Activity metadata must be a map or unavailable")
            metadata = metadata.get("delegate", metadata)
            if not isinstance(metadata, Mapping):
                raise ProtocolError("Activity metadata delegate must be a map")
        for key, phase in (("im:resolution-graph", "resolution plan"), ("im:dataflow-graph", "contextualization plan")):
            chart = metadata.get(key) if metadata is not None else None
            if chart is None:
                missing.append(key + " unavailable; absence does not establish no dependencies")
            else:
                charts.append(decode_flowchart(chart, max_nodes=max_nodes, max_links=max_links,
                    max_response_bytes=max_response_bytes, phase=phase, redact=client.transport.redact))
        activities.append(ActivityEvidence(node.id, node.urn, node.activity_type, node.outcome,
            tuple(charts), tuple(missing), node.raw))
    if not candidates:
        diagnostics.append("No activity metadata in this partial neighborhood; not proof of no computation")
    return ExecutionEvidence(view, tuple(activities), tuple(diagnostics))
