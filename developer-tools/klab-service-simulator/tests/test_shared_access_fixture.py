"""Supplementary independent policy/invalid-intake controls; public acceptance is separate."""
import pytest

from klab_simulator.shared_access import PREFIX, PROTOCOL
from klab_simulator.state import ContractError, State


def fixture():
    state = State("shared-read")
    state.contexts["s.c"] = {"owner": "alice", "configuration": {"id": "s.c"}}
    state.contexts["s.other"] = {"owner": "alice", "configuration": {"id": "s.other"}}
    state.observations[("s.c", 1)] = {"id": 1, "value": "private"}
    state.jobs[("runtime", 7)] = {"scope": "s.c", "status": "FINISHED", "result": {"id": 1}}
    state.shared_reads.clock = lambda: 1000
    return state


def call(state, *, principal="alice", method="GET", route=PREFIX + "access", scope="s.c", service="runtime", headers=None, data=None, query=None, grantee=None):
    return state.shared_reads.respond(service, method, route, principal, scope,
        {"klab-service": state.service_ids["runtime"]} if headers is None else headers, data,
        "/runtime" + route + "?protocol=" + (PROTOCOL if query is None else query) + ("&grantee=" + grantee if grantee is not None else ""))


def grant(state):
    return call(state, method="POST", route=PREFIX + "grants", data={"protocol": PROTOCOL, "grantee": "bob", "ttlSeconds": 30})[0]


def test_owner_reader_grant_wire_copy_revocation_renewal_expiry_and_cleanup():
    state = fixture()
    assert call(state, route=PREFIX + "grants", grantee="bob")[0]["state"] == "ABSENT"
    assert call(state)[0]["rights"] == ["READ", "SUBMIT", "RELEASE", "SHARE"]
    value = grant(state); identity = value["grantId"]
    assert call(state, route=PREFIX + "grants", grantee="bob")[0]["state"] == "ACTIVE"
    value["rights"].append("SHARE")
    assert state.shared_reads.rows[identity]["rights"] == ["READ"]
    value = call(state, principal="bob")[0]
    assert value["owner"] == "alice" and value["principal"] == "bob" and value["rights"] == ["READ"]
    with pytest.raises(ContractError) as duplicate: grant(state)
    assert duplicate.value.status == 409
    route = PREFIX + "grants/" + identity + "/revoke"
    with pytest.raises(ContractError): call(state, principal="bob", method="POST", route=route, data={"protocol": PROTOCOL})
    assert call(state, method="POST", route=route, data={"protocol": PROTOCOL})[0]["revoked"]
    assert call(state, route=PREFIX + "grants", grantee="bob")[0]["state"] == "REVOKED"
    with pytest.raises(ContractError) as denied: call(state, principal="bob")
    assert denied.value.status == 403
    renewed = grant(state)
    assert renewed["grantId"] != identity
    state.shared_reads.clock = lambda: 1031
    assert call(state, route=PREFIX + "grants", grantee="bob")[0]["state"] == "EXPIRED"
    with pytest.raises(ContractError) as expired: call(state, principal="bob")
    assert expired.value.status == 410
    assert call(state)[0]["rights"] == ["READ", "SUBMIT", "RELEASE", "SHARE"]
    state.shared_reads.release("s.c"); assert state.shared_reads.rows == {}


@pytest.mark.parametrize("changes", [{"grantee": "alice"}, {"grantee": "unknown"}, {"ttlSeconds": True},
    {"ttlSeconds": 0}, {"ttlSeconds": 3601}, {"rights": ["READ", "WRITE"]}, {"grantee": None}])
def test_bad_or_escalating_grants_refuse(changes):
    state = fixture()
    with pytest.raises(ContractError) as error:
        call(state, method="POST", route=PREFIX + "grants", data={"protocol": PROTOCOL, "grantee": "bob", "ttlSeconds": 30, **changes})
    assert error.value.status == 422 and state.shared_reads.rows == {}


def test_unknown_protocol_boundaries_budget_and_revoke_identity_are_fail_closed():
    state = fixture()
    assert call(state, service="resources") is None
    state.store = object()
    with pytest.raises(ContractError, match="ephemeral"): call(state)
    state.store = None
    with pytest.raises(ContractError): call(state, route=PREFIX + "grants", grantee="unknown")
    for kwargs in ({"scope": "absent.root"}, {"scope": "s.c.1"}, {"headers": {}}, {"query": "wrong"},
        {"method": "POST", "data": None}, {"method": "PUT", "data": {"protocol": PROTOCOL}}):
        with pytest.raises(ContractError): call(state, **kwargs)
    assert call(state, route="/unscoped", scope="unknown.session") is None
    assert call(state, method="POST", route="/createContext", scope="s") is None
    with pytest.raises(ContractError): call(state, method="POST", route="/api/v1/connect", scope="", data={"configuration": None})
    with pytest.raises(ContractError): call(state, method="POST", route="/api/v1/connect", scope="", data={"configuration": {"id": []}})
    with pytest.raises(ContractError): call(state, principal="bob")
    value = grant(state); route = PREFIX + "grants/" + value["grantId"] + "/revoke"
    with pytest.raises(ContractError): call(state, method="POST", route=PREFIX + "grants/absent/revoke", data={"protocol": PROTOCOL})
    with pytest.raises(ContractError): call(state, method="POST", route=route, scope="s.other", data={"protocol": PROTOCOL})
    with pytest.raises(ContractError): call(state, method="POST", route=route, data={"protocol": PROTOCOL, "extra": True})
    with pytest.raises(ContractError): call(state, principal="bob", method="POST", route=PREFIX + "grants", data={"protocol": PROTOCOL})
    state.shared_reads.rows = {str(i): {**value, "contextId": "s.other", "revoked": False} for i in range(128)}
    with pytest.raises(ContractError) as budget: grant(state)
    assert budget.value.status == 429


def test_shared_runtime_read_routes_deny_mutations_foreign_ids_and_preserve_expired_jobs():
    state = fixture(); grant(state)
    assert call(state, method="GET", route="/releaseContext") is None  # Owner uses ordinary authoritative disposal.
    config = call(state, principal="bob", method="POST", route="/api/v1/connect", scope="", data={"configuration": {"id": "s.c"}})[0]
    assert config == {"id": "s.c"}; config["id"] = "changed"
    assert state.contexts["s.c"]["configuration"]["id"] == "s.c"
    for kwargs in ({"scope": "s.c.1"}, {"headers": {"klab-service": "foreign"}}, {"route": "/releaseContext"},
        {"method": "POST", "route": "/api/v1/submit"}, {"route": "/jobs/cancel/7"}, {"route": "/jobs/status/bad"},
        {"route": "/jobs/status/8"}):
        with pytest.raises(ContractError): call(state, principal="bob", **kwargs)
    for kwargs in ({"scope": "s.c.1"}, {"headers": {"klab-service": "foreign"}}):
        with pytest.raises(ContractError): call(state, principal="bob", route="/jobs/status/7", **kwargs)
    assert call(state, principal="bob", route="/jobs/status/7")[0] == {"status": "FINISHED"}
    assert call(state, principal="bob", route="/jobs/retrieve/7")[0] == {"id": 1}
    state.jobs[("runtime", 7)]["status"] = "WAITING"
    with pytest.raises(ContractError): call(state, principal="bob", route="/jobs/retrieve/7")
    state.jobs[("runtime", 7)]["expired"] = True
    assert call(state, principal="bob", route="/jobs/status/7")[0] == {"status": "EMPTY"}
    with pytest.raises(ContractError) as expired: call(state, principal="bob", route="/jobs/retrieve/7")
    assert expired.value.status == 409
    state.jobs[("runtime", 7)]["scope"] = "s.other"
    with pytest.raises(ContractError): call(state, principal="bob", route="/jobs/status/7")
    for data in (None, {}, {"resultType": "LINK", "id": 1}, {"resultType": "OBSERVATION", "id": True}):
        with pytest.raises(ContractError): call(state, principal="bob", method="POST", route="/api/v1/query", data=data)
    for identity, size in ((1, 1), (999, 0)):
        rows = call(state, principal="bob", method="POST", route="/api/v1/query", data={"resultType": "OBSERVATION", "id": identity})[0]
        assert len(rows) == size
        if rows: rows[0]["value"] = "changed"
    assert state.observations[("s.c", 1)]["value"] == "private"
    point = {"sourceId": 1, "offset": 0, "curve": "UNSPECIFIED", "semantics": None,
        "slice": {"type": "INITIALIZATION", "key": "0-0", "start": 0, "end": 0}}
    assert call(state, principal="bob", method="POST", route="/api/v1/observation/value", data=point)[0] == "100"
    state.values[0] = None
    assert call(state, principal="bob", method="POST", route="/api/v1/observation/value", data=point)[0] == "null"
    for data in (None, {**point, "sourceId": None}, {**point, "sourceId": 99}, {**point, "offset": True},
        {**point, "offset": -1}, {**point, "offset": 400}, {**point, "semantics": {}}, {**point, "curve": "D2_XY"}, {**point, "slice": {}}):
        with pytest.raises(ContractError): call(state, principal="bob", method="POST", route="/api/v1/observation/value", data=data)
