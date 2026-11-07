"""JAVA-GAP-0003/SIMULATED_PROVISIONAL source-shaped catalog, not selection/admission."""
import asyncio
import json

import pytest

from klab_client import Client, AsyncClient
from klab_client.errors import AuthenticationError, UnsupportedOperationError
from klab_client.model_candidates import ModelCandidateQuery, inspect_model_candidates, async_inspect_model_candidates
from klab_simulator.state import State
from conftest import launch
from test_vertical import client_args, control


@pytest.mark.parametrize("query,expected", [
    (ModelCandidateQuery(), {"synthetic.public"}),
    (ModelCandidateQuery(namespaces=("synthetic.private",)), {"synthetic.public", "synthetic.private"}),
    (ModelCandidateQuery(projects=("synthetic.alice",)), {"synthetic.public", "synthetic.project"}),
    (ModelCandidateQuery(scenarios=("synthetic.scenario",)), {"synthetic.public", "synthetic.scenario"})])
def test_candidate_sources_follow_declared_visibility_without_workflow_or_admission(query, expected):
    with launch("candidate-sources") as ready:
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            catalog = inspect_model_candidates(client, query)
            assert {c.namespace_urn for c in catalog.candidates} == expected
            assert catalog.provider_id == ready["service_ids"]["resources"]
            assert all(c.source_sha256 and c.model_urn.endswith(".model") for c in catalog.candidates)
            assert "NOT_SEMANTIC_ADMISSION" in catalog.evidence
        state = control(ready)
        assert not any(state["counts"].values()) and state["authoring_assets"] == 0
        assert not any(row["path"].endswith("/submit") for row in state["trace"])


def test_candidate_selector_cannot_grant_project_access_or_enable_unadvertised_server(simulator):
    with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
        with pytest.raises(UnsupportedOperationError): inspect_model_candidates(client, ModelCandidateQuery())
    assert not any(row["path"].endswith("/models/candidates") for row in control(simulator)["trace"])
    with launch("candidate-sources") as ready:
        query = ModelCandidateQuery(namespaces=("synthetic.private",), projects=("synthetic.alice",), scenarios=("synthetic.scenario",))
        with Client(ready["endpoints"]["runtime"], **client_args(ready, "sim-token-bob")) as client:
            assert {c.namespace_urn for c in inspect_model_candidates(client, query).candidates} == {"synthetic.public", "synthetic.scenario"}
        with Client(ready["endpoints"]["runtime"], **client_args(ready, "invalid-synthetic")) as client:
            with pytest.raises(AuthenticationError): inspect_model_candidates(client, query)
        assert not any(control(ready)["counts"].values())


def test_native_async_source_inspection_uses_same_explicit_supplier_contract():
    with launch("candidate-sources") as ready:
        async def run():
            async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready)) as client:
                catalog = await async_inspect_model_candidates(client, ModelCandidateQuery())
                assert len(catalog.candidates) == 1 and catalog.candidates[0].namespace_urn == "synthetic.public"
        asyncio.run(run())
        assert not any(control(ready)["counts"].values())


@pytest.mark.parametrize("body,status", [(b"x" * 32769, 413), (b"{", 422), (b'{"version":1,"version":1}', 422),
    (b'{"version":true,"namespaces":[],"projects":[],"scenarios":[]}', 422),
    (b'{"version":2,"namespaces":[],"projects":[],"scenarios":[]}', 422)])
def test_bad_candidate_request_is_explicit_not_empty_success(body, status):
    state = State("candidate-sources")
    assert state.request("POST", "/resources/api/v1/models/candidates", {"Authorization": "sim-token-alice"}, body)[0] == status
    assert not state.contexts and not state.jobs


def test_supplier_refuses_runtime_workflow_delegation_and_unadvertised_fixture():
    state = State("candidate-sources")
    body = json.dumps(ModelCandidateQuery().to_wire()).encode()
    assert state.request("POST", "/resources/api/v1/models/candidates", {"Authorization": "sim-token-alice", "klab-scope": "foreign"}, body)[0] == 422
    state = State()
    assert state.request("POST", "/resources/api/v1/models/candidates", {"Authorization": "sim-token-alice"}, body)[0] == 404
