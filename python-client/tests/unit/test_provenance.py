"""Wire contracts traced to KnowledgeGraphQuery/LinkImpl, not actual server acceptance."""
from copy import deepcopy
from dataclasses import FrozenInstanceError
import json
from pathlib import Path

import httpx
import pytest

from klab_client import Client, Context, fetch_provenance
from klab_client.dto import observation_from_wire
from klab_client.errors import (AuthorizationError, AuthenticationError, InvalidRequestError,
    MissingAssetError, ProtocolError, ServerError, UnsupportedOperationError)
from klab_client.provenance import CLASSES, RELATIONSHIPS, _node

OBS = "org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl"
ACT = "org.integratedmodelling.klab.api.provenance.impl.ActivityImpl"


def payload():
    row = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    row["urn"] = "s.c.42"
    return row


def link(**changes):
    return {"@CLASS": "org.integratedmodelling.klab.api.data.impl.LinkImpl", "relationship": "MEASURED",
        "source": {"@CLASS": ACT, "id": 8, "urn": "s.c.activity.8", "type": "MEASURE", "outcome": "SUCCESS"},
        "target": {"@CLASS": OBS, "id": 42, "urn": "s.c.42"}, "sequence": 0,
        "properties": {"sequence": 0, "support": "S2(1,1)"}, **changes}


def setup(rows=None, *, membership=None, status=200):
    calls = []
    def handler(request):
        query = json.loads(request.content)
        calls.append((request, query))
        assert request.url.path == "/runtime/api/v1/query"
        assert request.headers["klab-scope"] == "s.c"
        assert request.headers["klab-service"] == "home"
        assert request.headers["authorization"] == "issued-secret"
        if query["resultType"] == "OBSERVATION":
            return httpx.Response(200, json=[payload()] if membership is None else membership)
        return httpx.Response(status, json=[link()] if rows is None else rows)
    client = Client("https://runtime.invalid/runtime", "issued-secret", runtime_service_id="home",
                    http_transport=httpx.MockTransport(handler))
    observation = observation_from_wire(payload(), Context(client, {"id": "s.c", "serviceId": "home"}))
    return client, observation, calls


def test_bounded_readonly_query_and_partial_view():
    client, observation, calls = setup()
    with client:
        view = fetch_provenance(observation)
    assert len(calls) == 2
    query = calls[1][1]
    assert query["type"] == "OR" and query["limit"] == 65 and query["offset"] == 0
    assert len(query["children"]) == 2 * len(RELATIONSHIPS)
    assert all(child["depth"] == child["minimumDepth"] == 1 for child in query["children"])
    assert all(child.get("source", child.get("target")) == {"type": "OBSERVATION", "id": 42} for child in query["children"])
    assert view.partial and view.consistency.startswith("unknown") and view.next_offset is None
    assert view.observation.runtime_id == "home" and view.fetched_at.tzinfo is not None
    edge = view.links[0]
    assert edge.source.kind == "activity" and edge.source.activity_type == "MEASURE"
    assert edge.source.outcome == "SUCCESS" and edge.target.id == 42
    assert edge.evidence == "recorded relationship"
    with pytest.raises(FrozenInstanceError):
        view.partial = False
    with pytest.raises(TypeError):
        edge.properties["support"] = "changed"


def test_empty_refresh_and_pagination_parallel_links_no_cache():
    row = link()
    client, observation, calls = setup([row, row, row])
    with client:
        view = fetch_provenance(observation, limit=2, offset=3)
        assert len(view.links) == 2 and view.next_offset == 5 and view.offset == 3
        fetch_provenance(observation, limit=2, offset=5)
    assert len(calls) == 4 and calls[-1][1]["offset"] == 5
    client, observation, _ = setup([])
    with client:
        view = fetch_provenance(observation)
        assert view.links == () and view.partial and view.next_offset is None


@pytest.mark.parametrize("relationship", ["RESOLVED", "CONTEXTUALIZED_BY"])
def test_plans_are_not_execution_evidence(relationship):
    client, observation, _ = setup([link(relationship=relationship)])
    with client:
        assert fetch_provenance(observation).links[0].evidence == "planned resolution"


def test_unknown_harmless_fields_deeply_frozen_and_secrets_removed():
    row = link(properties={"token": "secret", "future": [{"value": "issued-secret"}], "count": 3})
    client, observation, _ = setup([row])
    with client:
        props = fetch_provenance(observation).links[0].properties
    assert "token" not in props and props["future"][0]["value"] == "[REDACTED]" and props["count"] == 3
    with pytest.raises(TypeError):
        props["future"][0]["value"] = "other"


@pytest.mark.parametrize("kwargs", [{"limit": 0}, {"limit": 129}, {"limit": True}, {"offset": -1}, {"offset": True},
    {"max_response_bytes": 0}, {"max_response_bytes": True}, {"max_response_bytes": 16777217}])
def test_invalid_bounds_no_network(kwargs):
    client, observation, calls = setup()
    with client, pytest.raises(InvalidRequestError):
        fetch_provenance(observation, **kwargs)
    assert not calls


@pytest.mark.parametrize("rows", [{}, [None], [link(relationship="UNKNOWN")], [link(sequence=True)],
    [link(properties=[])], [link(source={})], [link(target={"@CLASS": OBS, "id": 43})],
    [link(target={"@CLASS": OBS, "id": 42, "urn": "s.c.else"})]])
def test_malformed_or_unrelated_links_reject(rows):
    client, observation, _ = setup(rows)
    with client, pytest.raises(ProtocolError):
        fetch_provenance(observation)


def test_over_budget_rows_and_bytes():
    client, observation, _ = setup([link()] * 3)
    with client, pytest.raises(ProtocolError, match="over-budget"):
        fetch_provenance(observation, limit=1)
    client, observation, _ = setup([link(properties={"large": "x" * 20000})])
    with client, pytest.raises(ProtocolError, match="byte budget"):
        fetch_provenance(observation, max_response_bytes=10000)


@pytest.mark.parametrize("status,error", [(401, AuthenticationError), (403, AuthorizationError),
    (404, MissingAssetError), (422, UnsupportedOperationError), (400, InvalidRequestError), (503, ServerError)])
def test_errors_not_empty_views(status, error):
    client, observation, _ = setup(status=status)
    with client, pytest.raises(error):
        fetch_provenance(observation)


@pytest.mark.parametrize("membership,error", [([], MissingAssetError), ({}, ProtocolError),
    ([payload(), payload()], ProtocolError), ([{**payload(), "urn": "s.c.other"}], ProtocolError)])
def test_current_membership_checked(membership, error):
    client, observation, calls = setup(membership=membership)
    with client, pytest.raises(error):
        fetch_provenance(observation)
    assert len(calls) == 1


@pytest.mark.parametrize("changes", [{"id": True}, {"id": -1}, {"id": 2**63}, {"@CLASS": "unknown"}, {"@CLASS": []},
    {"urn": ""}, {"urn": []}, {"type": 2}, {"type": "NEW"}, {"outcome": "UNKNOWN"}])
def test_invalid_endpoint_identity_and_state(changes):
    row = deepcopy(link()["source"])
    row.update(changes)
    with pytest.raises(ProtocolError):
        _node(row, lambda value: value)


@pytest.mark.parametrize("cls", list(CLASSES))
def test_supported_endpoint_classes_and_missing_optional_fields(cls):
    row = {"@CLASS": cls, "id": 2}
    if CLASSES[cls] == "activity":
        row["type"] = "SUBMISSION"
    assert _node(row, lambda value: value).kind == CLASSES[cls]


def test_response_budget_validation_and_success():
    client, _, _ = setup()
    with client:
        for value in (0, True, 1.2):
            with pytest.raises(InvalidRequestError):
                client.transport.request("runtime", "GET", "/x", max_response_bytes=value)


def test_self_cycles_and_shared_endpoints_are_not_recursively_expanded():
    node = {"@CLASS": OBS, "id": 42, "urn": "s.c.42"}
    rows = [link(source=node, target=node, relationship="AFFECTS"), link(), link(sequence=2)]
    client, observation, calls = setup(rows)
    with client:
        view = fetch_provenance(observation)
    assert len(calls) == 2 and len(view.links) == 3
    assert view.links[0].source.id == view.links[0].target.id == 42
    assert view.links[1].source.id == view.links[2].source.id == 8
    assert view.links[1].sequence != view.links[2].sequence


def test_invalid_runtime_binding_is_rejected_before_http():
    client, observation, calls = setup()
    client.runtime_service_id = "foreign-home"
    with client, pytest.raises(InvalidRequestError):
        fetch_provenance(observation)
    assert not calls


def test_compressed_response_cannot_bypass_decoded_byte_budget():
    import gzip
    def handler(request):
        assert request.headers["accept-encoding"] == "identity"
        return httpx.Response(200, headers={"content-encoding": "gzip"}, content=gzip.compress(b'[]'))
    with Client("https://runtime.invalid", "secret", http_transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(ProtocolError, match="identity content encoding"):
            client.transport.request("runtime", "POST", "/api/v1/query", json={}, max_response_bytes=1024)
