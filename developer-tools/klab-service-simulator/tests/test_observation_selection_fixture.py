"""Supplementary independent fixture refusals; public SDK acceptance is separate."""
from copy import deepcopy

import pytest

from klab_simulator.observation_selections import observable, respond
from klab_simulator.state import ContractError, State


def fixture():
    state = State("observation-selections")
    state.contexts["s.c"] = {"owner": "alice"}
    for identity, parent, name in ((1, None, "synthetic:Entity"), (2, 1, "synthetic:Entity"), (3, None, "synthetic:Connection")):
        state.observations[("s.c", identity)] = {"id": identity, "observable": observable(name),
            "metadata": {"delegate": {"simulator:parent": parent}}}
    state.observations[("other.root", 1)] = deepcopy(state.observations[("s.c", 1)])
    return state


def query():
    return {"type": "QUERY", "resultType": "OBSERVATION", "source": {"type": "SCOPE"},
        "relationship": "HAS_CHILD", "depth": 1, "minimumDepth": 1, "limit": 2, "offset": 0}


def call(state, *, service="runtime", method="POST", route="/api/v1/query", principal="alice", scope="s.c", headers=None, data=None, body=b""):
    return respond(state, service, method, route, principal, scope,
        {"klab-service": state.service_ids["runtime"]} if headers is None else headers, data, body)


@pytest.mark.parametrize("changes", [{"type": "OTHER"}, {"resultType": "OTHER"}, {"source": {}},
    {"relationship": "HAS_MEMBER"}, {"depth": 2}, {"minimumDepth": 0}, {"limit": True},
    {"limit": 0}, {"limit": 130}, {"offset": True}, {"offset": -1}, {"offset": 2**31}])
def test_bounded_query_refusals(changes):
    with pytest.raises(ContractError, match="bounded direct-child"):
        call(fixture(), data={**query(), **changes})


@pytest.mark.parametrize("source,target", [("1", None), (None, "2"), ("0", "2"), ("1", "1"),
    (str(2**63), "1"), ("99", "2"), ("3", "2")])
def test_endpoint_headers_are_independently_checked(source, target):
    state = fixture()
    with pytest.raises(ContractError):
        call(state, headers={"klab-service": state.service_ids["runtime"],
            "klab-source-observation": source, "klab-target-observation": target}, data=query())


def test_collection_lookup_focused_filter_and_response_copy():
    state = fixture()
    rows, _ = call(state, data=query())
    assert [row["id"] for row in rows] == [1, 3]
    rows[0]["id"] = 100
    assert state.observations[("s.c", 1)]["id"] == 1
    rows, _ = call(state, scope="s.c.1", data={**query(), "source": {"type": "OBSERVATION", "id": 1}})
    assert [row["id"] for row in rows] == [2]
    for identity, expected in ((1, 1), (99, 0)):
        rows, _ = call(state, data={"resultType": "OBSERVATION", "id": identity})
        assert len(rows) == expected
    with pytest.raises(ContractError, match="Focused observation"):
        call(state, scope="s.c.99", data=query())
    with pytest.raises(ContractError, match="Runtime"):
        call(state, headers={}, data=query())
    with pytest.raises(ContractError, match="Structured"):
        call(state, data=None)
    with pytest.raises(ContractError, match="not authorized"):
        call(state, principal="bob", data=query())
    with pytest.raises(ContractError, match="numeric storage"):
        call(state, route="/api/v1/observation/value", data={})
    assert call(state, service="resources", data={}) is None
    assert call(state, method="GET", data={}) is None
    assert call(state, route="/other", data={}) is None


def test_finite_admission_refuses_unknown_forged_and_mismatched_selection():
    state = fixture()
    assert call(state, service="reasoner", route="/api/v1/resolve/observable", body=b"synthetic:Entity")[0] == observable("synthetic:Entity")
    with pytest.raises(ContractError): observable("native:Unknown")
    with pytest.raises(ContractError, match="envelope"):
        call(state, route="/api/v1/submit", data={})
    entity = {"observation": {"observable": observable("synthetic:Entity")}}
    with pytest.raises(ContractError, match="exact synthetic"):
        call(state, route="/api/v1/submit", data={**entity, "resolutionConstraints": [{}]})
    forged = deepcopy(entity); forged["observation"]["observable"]["urn"] = "synthetic:Connection"
    with pytest.raises(ContractError, match="exact synthetic"):
        call(state, route="/api/v1/submit", data=forged)
    with pytest.raises(ContractError, match="requires both"):
        call(state, route="/api/v1/submit", data={"observation": {"observable": observable("synthetic:Connection")}})
    headers = {"klab-service": state.service_ids["runtime"], "klab-source-observation": "1", "klab-target-observation": "2"}
    with pytest.raises(ContractError, match="requires both"):
        call(state, route="/api/v1/submit", data=entity, headers=headers)
    for service in ("runtime", "resolver"):
        for endpoints in (False, True):
            name = "synthetic:Connection" if endpoints else "synthetic:Entity"
            identity, _ = call(state, service=service, route="/api/v1/submit" if service == "runtime" else "/api/v1/resolve",
                headers=headers if endpoints else None, data={"observation": {"observable": observable(name)}})
            result = state.jobs[(service, identity)]["result"]
            receipt = result["selection"] if service == "resolver" else result["metadata"]["delegate"]["simulator:selection"]
            assert receipt["source"] == (1 if endpoints else None)
