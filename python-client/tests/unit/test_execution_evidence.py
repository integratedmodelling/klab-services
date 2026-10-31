"""Java-shape FlowChart/Dataflow and scoped Activity contracts; not live computation proof."""
from copy import deepcopy
import json
from pathlib import Path

import httpx
import pytest

from klab_client import (Client, Context, decode_dataflow, decode_flowchart, fetch_execution_evidence)
from klab_client.dto import observation_from_wire
from klab_client.errors import (AuthorizationError, InvalidRequestError, ProtocolError, UnsupportedOperationError)
from klab_client.evidence import _payload, _plain

ACT = "org.integratedmodelling.klab.api.provenance.impl.ActivityImpl"
OBS = "org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl"
CHART = {"root": {"id": "root", "labels": [{"text": "<plan>"}],
    "ports": [{"id": "input", "role": "INPUT"}],
    "children": [{"id": "model", "labels": [{"text": "fixture.elevation"}], "metadata": {"kind": "model", "urn": "fixture.elevation"}},
                 {"id": "reference", "metadata": {"observationId": 42}}],
    "edges": [{"id": "first", "sources": ["input"], "targets": ["model"]},
              {"id": "parallel", "sources": ["input"], "targets": ["model"]},
              {"id": "reference-link", "sources": ["model"], "targets": ["reference"]}]},
    "metadata": {"activeNode": "model"}}
ACTUATOR = {"id": -2, "actuatorType": "OBSERVE", "strategyUrn": "fixture.strategy",
    "computation": [{"urn": "fixture.numeric", "requiredVersion": {"major": 1}}],
    "children": [{"id": 42, "actuatorType": "REFERENCE"}, {"id": 42, "actuatorType": "REFERENCE"}]}
DATAFLOW = {"resolutionOutcome": "RESOLVED", "empty": False, "resolvedCoverage": 1,
    "computation": [ACTUATOR], "requirements": {"results": []}, "coverage": {"definition": "S2(5,4)"}}


def test_chart_preserves_topology_parallel_reference_and_escaped_notebook_table():
    result = decode_flowchart(CHART)
    assert [link.id for link in result.links] == ["first", "parallel", "reference-link"]
    assert result.links[0].sources == result.links[1].sources == ("input",)
    assert result.links[0].targets == result.links[1].targets == ("model",)
    assert result.nodes[1].parent_id == "root" and result.ports[0].node_id == "root"
    assert result.raw["metadata"]["activeNode"] == "model"
    assert result.nodes[1].raw["metadata"]["urn"] == "fixture.elevation"
    assert "&lt;plan&gt;" in result._repr_html_() and "<table>" in result._repr_html_()
    assert "not proof of execution" in result.summary()
    with pytest.raises(TypeError):
        result.nodes[0].raw["id"] = "changed"


def test_hyperedges_cycles_and_disconnected_nodes_are_retained():
    chart = deepcopy(CHART)
    chart["root"]["children"].append({"id": "isolated"})
    chart["root"]["edges"].append({"id": "cycle", "sources": ["model", "reference"], "targets": ["model"]})
    result = decode_flowchart(chart, phase="resolution plan")
    assert len(result.nodes) == 4 and result.links[-1].sources == ("model", "reference")
    assert result.phase == "resolution plan"


@pytest.mark.parametrize("kind", ["node", "port", "link"])
def test_chart_identities_cannot_expose_credentials_or_be_silently_rewritten(kind):
    chart = deepcopy(CHART)
    if kind == "node":
        chart["root"]["id"] = "root-issued-secret"
    elif kind == "port":
        chart["root"]["ports"][0]["id"] = "input-issued-secret"
        for edge in chart["root"]["edges"][:2]:
            edge["sources"] = ["input-issued-secret"]
    else:
        chart["root"]["edges"][0]["id"] = "edge-issued-secret"
    with pytest.raises(ProtocolError, match="credential") as caught:
        decode_flowchart(chart, redact=lambda value: value.replace("issued-secret", "[REDACTED]"))
    assert "issued-secret" not in str(caught.value)


@pytest.mark.parametrize("mutate", [
    lambda c: c.update(root=None),
    lambda c: c["root"].update(id=""),
    lambda c: c["root"]["children"][0].update(id="root"),
    lambda c: c["root"]["ports"][0].update(id="model"),
    lambda c: c["root"]["ports"][0].update(role="FUTURE"),
    lambda c: c["root"].update(ports=None),
    lambda c: c["root"].update(children={}),
    lambda c: c["root"]["edges"][0].update(sources=[]),
    lambda c: c["root"]["edges"][0].update(targets=["missing"]),
    lambda c: c["root"]["edges"][0].update(sources=[1]),
    lambda c: c["root"]["edges"][0].update(id="root"),
    lambda c: c["root"].update(labels=[{"text": None}]),
])
def test_invalid_chart_topology_not_silently_dropped(mutate):
    chart = deepcopy(CHART)
    mutate(chart)
    with pytest.raises(ProtocolError):
        decode_flowchart(chart)


@pytest.mark.parametrize("kwargs", [{"max_nodes": 1}, {"max_nodes": 2}, {"max_links": 1}, {"max_response_bytes": 10}])
def test_chart_budgets(kwargs):
    with pytest.raises(ProtocolError):
        decode_flowchart(CHART, **kwargs)


def test_chart_invalid_parameters_and_json_reject():
    with pytest.raises(InvalidRequestError):
        decode_flowchart(CHART, phase="executed")
    with pytest.raises(InvalidRequestError):
        decode_flowchart(CHART, max_nodes=True)
    with pytest.raises(ProtocolError):
        _payload({"value": float("inf")}, 1000, lambda value: value)
    with pytest.raises(ProtocolError):
        _payload({"value": object()}, 1000, lambda value: value)
    nested = {}
    for _ in range(34):
        nested = {"inner": nested}
    with pytest.raises(ProtocolError, match="nesting"):
        _plain(nested)


def test_dataflow_is_plan_and_retains_repeated_reference_occurrences():
    result = decode_dataflow(DATAFLOW)
    assert result.outcome == "RESOLVED" and result.resolved_coverage == 1
    assert [actuator.id for actuator in result.actuators] == [-2, 42, 42]
    assert [actuator.path for actuator in result.actuators] == [(0,), (0, 0), (0, 1)]
    assert result.actuators[0].calls == ("fixture.numeric",)
    assert result.actuators[0].strategy_urn == "fixture.strategy"
    assert result.actuators[0].raw["computation"][0]["requiredVersion"]["major"] == 1
    assert "not recorded execution" in result._repr_html_()
    assert result.raw["coverage"]["definition"] == "S2(5,4)"


@pytest.mark.parametrize("outcome", ["RESOLVED", "NO_MODEL", "FAILED"])
def test_actual_outcomes_do_not_imply_computation(outcome):
    result = decode_dataflow({"resolutionOutcome": outcome, "empty": outcome == "FAILED", "computation": []})
    assert result.outcome == outcome and result.resolved_coverage is None and result.actuators == ()


@pytest.mark.parametrize("changes,error", [({"resolutionOutcome": "NEW"}, UnsupportedOperationError),
    ({"resolutionOutcome": None}, UnsupportedOperationError), ({"empty": None}, ProtocolError),
    ({"empty": True}, ProtocolError), ({"resolutionOutcome": "NO_MODEL"}, ProtocolError),
    ({"resolvedCoverage": -1}, ProtocolError), ({"resolvedCoverage": True}, ProtocolError),
    ({"resolvedCoverage": float("nan")}, ProtocolError), ({"computation": {}}, ProtocolError),
    ({"notifications": None}, ProtocolError)])
def test_unknown_or_contradictory_dataflow_fails(changes, error):
    with pytest.raises(error):
        decode_dataflow({**DATAFLOW, **changes})


@pytest.mark.parametrize("coverage", [10**400, -(10**400)], ids=["too-large", "too-small"])
def test_unrepresentable_coverage_raises_protocol_error(coverage):
    with pytest.raises(ProtocolError, match="coverage"):
        decode_dataflow({**DATAFLOW, "resolvedCoverage": coverage})


@pytest.mark.parametrize("changes", [{"actuatorType": "UNKNOWN"}, {"id": True}, {"id": 2**63},
    {"strategyUrn": 2}, {"children": {}}, {"computation": [None]}])
def test_invalid_actuator_does_not_acquire_execution_meaning(changes):
    with pytest.raises(ProtocolError):
        decode_dataflow({**DATAFLOW, "computation": [{**ACTUATOR, **changes}]})


def test_actuator_budget_and_redaction():
    with pytest.raises(ProtocolError, match="actuator budget"):
        decode_dataflow(DATAFLOW, max_actuators=1)
    result = decode_dataflow({**DATAFLOW, "token": "private", "future": "credential-secret"}, redact=lambda text: text.replace("credential-secret", "[REDACTED]"))
    assert "token" not in result.raw and result.raw["future"] == "[REDACTED]"
    failed = decode_dataflow({"resolutionOutcome": "FAILED", "empty": True,
        "notifications": [{"level": "ERROR", "message": "No applicable computation"}]})
    assert failed.diagnostics == ("No applicable computation",) and "No applicable computation" in failed.summary()


def observation_payload():
    value = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    value["urn"] = "s.c.42"
    return value


def activity(id=8, **changes):
    return {"@CLASS": ACT, "id": id, "urn": f"s.c.activity.{id}", "type": "MEASURE", "outcome": "FAILURE", **changes}


def link(id=8):
    return {"source": activity(id), "target": {"@CLASS": OBS, "id": 42, "urn": "s.c.42"},
        "relationship": "MEASURED", "sequence": 0, "properties": {}}


def setup(*, rows=None, links=None, status=200):
    calls = []
    def handler(request):
        calls.append(request)
        assert request.url.path == "/runtime/api/v1/query"
        assert request.headers["authorization"] == "issued-secret"
        assert request.headers["klab-scope"] == "s.c" and request.headers["klab-service"] == "home"
        query = json.loads(request.content)
        if query["resultType"] == "OBSERVATION":
            return httpx.Response(200, json=[observation_payload()])
        if query["resultType"] == "LINK":
            return httpx.Response(200, json=[link()] if links is None else links)
        assert query["resultType"] == "ACTIVITY" and query["limit"] == 2
        return httpx.Response(status, content=json.dumps([activity(metadata={"delegate": {"im:dataflow-graph": CHART}}, observationUrn="s.c.42")] if rows is None else rows))
    client = Client("https://runtime.invalid/runtime", "issued-secret", runtime_service_id="home", http_transport=httpx.MockTransport(handler))
    observation = observation_from_wire(observation_payload(), Context(client, {"id": "s.c", "serviceId": "home"}))
    return client, observation, calls


def test_recorded_activity_plan_retains_failure_and_original_observation_association():
    client, observation, calls = setup()
    with client:
        result = fetch_execution_evidence(observation)
    assert result.provenance.observation == observation.to_handle() and len(calls) == 3
    assert result.partial and result.activities[0].outcome == "FAILURE"
    assert result.activities[0].charts[0].phase == "contextualization plan"
    assert "im:resolution-graph unavailable" in result.activities[0].missing[0]
    assert "FAILURE" in result._repr_html_() and "not durable root commit" in result.summary()
    assert all("resolve" not in str(call.url) for call in calls)


def test_missing_metadata_empty_neighborhood_and_budget_are_explicit_partial():
    client, observation, _ = setup(rows=[])
    with client:
        result = fetch_execution_evidence(observation)
        assert result.activities[0].charts == () and len(result.activities[0].missing) == 3
    client, observation, calls = setup(links=[])
    with client:
        result = fetch_execution_evidence(observation)
        assert len(calls) == 2 and "not proof of no computation" in result.diagnostics[0]
    client, observation, calls = setup(links=[link(8), link(9)])
    with client:
        result = fetch_execution_evidence(observation, activity_limit=1)
        assert len(calls) == 3 and "budget reached" in result.diagnostics[0]


@pytest.mark.parametrize("rows", [{}, [activity(), activity()], [activity(9)],
    [activity(urn="other")], [{"@CLASS": OBS, "id": 8}],
    [activity(observationUrn="other")], [activity(metadata=[])], [activity(metadata={"delegate": []})]])
def test_scoped_activity_shape_identity_and_association_fail(rows):
    client, observation, _ = setup(rows=rows)
    with client, pytest.raises(ProtocolError):
        fetch_execution_evidence(observation)


@pytest.mark.parametrize("status,error", [(400, InvalidRequestError), (403, AuthorizationError), (422, UnsupportedOperationError)])
def test_extra_activity_fetch_preserves_error_distinctions(status, error):
    client, observation, _ = setup(status=status)
    with client, pytest.raises(error):
        fetch_execution_evidence(observation)


def test_direct_metadata_and_both_diagrams_are_preserved():
    client, observation, _ = setup(rows=[activity(metadata={"im:resolution-graph": CHART, "im:dataflow-graph": CHART})])
    with client:
        result = fetch_execution_evidence(observation)
    assert len(result.activities[0].charts) == 2 and not result.activities[0].missing


def test_conflicting_link_activity_identity_fails():
    other = deepcopy(link())
    other["source"]["urn"] = "another-activity"
    client, observation, _ = setup(links=[link(), other])
    with client, pytest.raises(ProtocolError, match="Conflicting activity"):
        fetch_execution_evidence(observation)
