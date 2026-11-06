"""Explicit finite source admission and recorded computation with input receipts.

This is simulator contract evidence, not Java compilation or scientific execution.
"""
import asyncio

import httpx
import pytest

from klab_client import (AsyncAuthoring, AsyncClient, AsyncResourceImporter, Authoring,
                         Client, ObservationRequest, ResourceImporter, fetch_provenance)
from klab_client.errors import InvalidRequestError
from klab_simulator.authoring import MODEL, NAMESPACE, RESOURCE, SOURCE
from klab_simulator.fixtures import authentic_fixture, CROP_SHA
from klab_simulator.state import ContractError, State
from conftest import launch
from test_resource_imports import arguments, source
from test_vertical import client_args, control, geometry, verify_data
from test_state import call, setup


def grid(oracle):
    return {"shape": [20, 20], "bounds": oracle["bounds"], "crs": "EPSG:4269", "unit": "m",
        "row_orientation": "north_to_south", "axis_order": "x,y", "precision": "float32",
        "centre_origin": [oracle["affine"][2] + oracle["affine"][0] / 2,
                          oracle["affine"][5] + oracle["affine"][4] / 2],
        "cell_size": [oracle["affine"][0], oracle["affine"][4]]}


def reset(ready):
    response = httpx.post(ready["endpoints"]["runtime"].rsplit("/", 1)[0] + "/__sim__/reset",
        headers={"Authorization": "sim-admin-control"}, trust_env=False, timeout=3)
    response.raise_for_status()


def test_finite_authoring_is_not_a_parser_or_permission_bypass():
    state = State("import-workflow")
    scope = setup(state)
    headers = {"Authorization": "sim-token-alice", "Content-Type": "text/plain"}
    assert state.request("POST", "/resources/parseAsset/NAMESPACE", headers, b"namespace arbitrary;")[0] == 422
    assert state.request("POST", "/resources/parseAsset/NAMESPACE", headers, SOURCE.encode())[0] == 200
    payload = {"observation": {"observable": {"urn": "geography:Elevation in m"},
                               "geometry": geometry(authentic_fixture()).to_wire()}}
    assert call(state, "POST", "/api/v1/submit", payload, scope=scope)[0] == 422
    assert not state.authoring.assets and not state.observations
    # A namespace acknowledgement alone cannot be forged to bypass import ownership.
    state.authoring.assets[("NAMESPACE", NAMESPACE)] = {"owner": "bob", "payload": {}}
    with pytest.raises(ContractError) as failure:
        state.authoring.binding(scope, "alice")
    assert failure.value.status == 422


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_explicit_import_semantic_source_resolver_and400cell_input_receipt(mode, tmp_path):
    path = source(tmp_path)
    oracle = authentic_fixture()
    proposal = ObservationRequest("geography:Elevation in m", geometry=geometry(oracle))
    with launch("import-workflow") as ready:
        try:
            if mode == "async":
                async def run():
                    async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready)) as client:
                        session = await client.create_session()
                        context = await session.create_context()
                        try:
                            with pytest.raises(InvalidRequestError):
                                await context.observe(proposal)  # Import/binding cannot be silently invented.
                            receipt = await AsyncResourceImporter(client, context).upload(path, **arguments())
                            await receipt.job.result(timeout=2)
                            authoring = AsyncAuthoring(client)
                            parsed = await authoring.parse(SOURCE)
                            workspace = await authoring.create_workspace("inputworkspace", allow_write=True)
                            project = await authoring.create_project(workspace, "inputproject", allow_write=True)
                            await authoring.create_document(parsed, project, allow_write=True)
                            assert (await client.resources.retrieve(MODEL, "MODEL")).urn == MODEL
                            observable = await client.reasoner.resolve_observable("geography:Elevation in m")
                            plan = await (await client.resolver.resolve(proposal.to_wire(observable), context)).result(timeout=2)
                            assert {x["resourceUrn"] for x in plan["requirements"]["results"]} == {MODEL, RESOURCE}
                            observation = await (await context.observe(proposal)).result(timeout=2)
                            blocks = [await observation.fetch_data(range(256)), await observation.fetch_data(range(256, 400))]
                            verify_data(observation, blocks, oracle["allCellsNorthToSouthWestToEast"], grid(oracle))
                            assert observation.metadata["input"] == {"resourceUrn": RESOURCE, "modelUrn": MODEL,
                                "sourceSha256": receipt.source_sha256, "sourceBytes": receipt.source_bytes}
                        finally:
                            await context.release()
                            await session.release()
                asyncio.run(run())
            else:
                with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
                    session = client.create_session()
                    context = session.create_context()
                    try:
                        with pytest.raises(InvalidRequestError):
                            context.observe(proposal)
                        receipt = ResourceImporter(client, context).upload(path, **arguments())
                        receipt.job.result(timeout=2)
                        authoring = Authoring(client)
                        parsed = authoring.parse(SOURCE)
                        workspace = authoring.create_workspace("inputworkspace", allow_write=True)
                        project = authoring.create_project(workspace, "inputproject", allow_write=True)
                        admitted = authoring.create_document(parsed, project, allow_write=True)
                        assert admitted.urn == NAMESPACE
                        assert client.resources.retrieve(MODEL, "MODEL").urn == MODEL
                        observable = client.reasoner.resolve_observable("geography:Elevation in m")
                        plan = client.resolver.resolve(proposal.to_wire(observable), context).result(timeout=2)
                        assert {x["resourceUrn"] for x in plan["requirements"]["results"]} == {MODEL, RESOURCE}
                        observation = context.observe(proposal).result(timeout=2)
                        blocks = [observation.fetch_data(range(256)), observation.fetch_data(range(256, 400))]
                        verify_data(observation, blocks, oracle["allCellsNorthToSouthWestToEast"], grid(oracle))
                        assert observation.metadata["input"]["sourceSha256"] == receipt.source_sha256 == CROP_SHA
                        assert fetch_provenance(observation).partial  # Not complete native historical lineage.
                    finally:
                        context.release()
                        session.release()
            assert not any(control(ready)["counts"].values()) and control(ready)["resources"] == 0
            assert control(ready)["authoring_assets"] == 3  # ADD receipts are not disposable leases.
        finally:
            reset(ready)  # Only test-owned simulator state, never a native Java cleanup claim.
        assert control(ready)["authoring_assets"] == 0
