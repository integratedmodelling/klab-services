"""Public upload -> explicit source ADD -> plan -> recorded input -> graph join."""
import asyncio
from copy import copy
from dataclasses import replace
import hashlib

import pytest

from klab_client import AsyncAuthoring, AsyncClient, AsyncContext, AsyncResourceImporter, Authoring, Client, Context, ObservationRequest, ResourceImporter
from klab_client.async_client import AsyncJob
from klab_client.client import Job
from klab_client.input_provenance import AsyncInputProvenance, InputProvenance
from klab_client.errors import AuthorizationError, InvalidRequestError, ProtocolError
from klab_simulator.authoring import MODEL, NAMESPACE, RESOURCE, SOURCE
from klab_simulator.fixtures import authentic_fixture
from conftest import launch
from test_input_workflow import grid, reset
from test_resource_imports import arguments, source
from test_vertical import client_args, control, geometry, verify_data


@pytest.mark.parametrize('native_async', [False, True])
def test_actual_upload_source_plan_recorded_input_and_graph_claims_are_joined(native_async, tmp_path):
    path = source(tmp_path)
    oracle = authentic_fixture()
    proposal = ObservationRequest('geography:Elevation in m', geometry=geometry(oracle))
    def check(receipt, submission):
        assert receipt.resource_urn == RESOURCE and receipt.model_urn == MODEL and receipt.namespace_urn == NAMESPACE
        assert receipt.binary_sha256 == submission.source_sha256
        assert receipt.binary_bytes == path.stat().st_size
        assert receipt.annotation_sha256 == hashlib.sha256(SOURCE.encode()).hexdigest()
        assert receipt.manifest_sha256 != receipt.binary_sha256
        assert 'not native' in receipt.evidence and len(receipt.graph.nodes) == 3
    with launch('annotated-input') as ready:
        try:
            async def run():
                async with AsyncClient(ready['endpoints']['runtime'], **client_args(ready)) as api:
                    session = await api.create_session(); context = await session.create_context()
                    try:
                        submission = await AsyncResourceImporter(api, context).upload(path, **arguments())
                        await submission.job.result(timeout=2)
                        authoring = AsyncAuthoring(api)
                        parsed = await authoring.parse(SOURCE)
                        workspace = await authoring.create_workspace('inputworkspace', allow_write=True)
                        project = await authoring.create_project(workspace, 'inputproject', allow_write=True)
                        admitted = await authoring.create_document(parsed, project, allow_write=True)
                        observable = await api.reasoner.resolve_observable('geography:Elevation in m')
                        plan = await api.resolver.resolve(proposal.to_wire(observable), context)
                        await plan.result(timeout=2)
                        observation = await (await context.observe(proposal)).result(timeout=2)
                        join = AsyncInputProvenance(context, allow_experimental=True)
                        check(await join.inspect(submission, admitted, SOURCE, plan, observation, model_urn=MODEL), submission)
                        with pytest.raises(ProtocolError):
                            await join.inspect(replace(submission, source_sha256='0'*64), admitted, SOURCE, plan, observation, model_urn=MODEL)
                        with pytest.raises(ProtocolError):
                            await join.inspect(submission, admitted, SOURCE+'\n', plan, observation, model_urn=MODEL)
                        async with AsyncClient(ready['endpoints']['runtime'], **client_args(ready,'sim-token-bob')) as bob:
                            foreign=AsyncContext(bob,context.configuration)
                            stolen=copy(observation); stolen._context=foreign
                            borrowed=replace(submission,job=AsyncJob(bob,submission.job.id,foreign,service='resources'))
                            borrowed_plan=AsyncJob(bob,plan.id,foreign,service='resolver',decoder=plan._decoder,result_kind='dataflow')
                            with pytest.raises(AuthorizationError):
                                await AsyncInputProvenance(foreign,allow_experimental=True).inspect(
                                    borrowed,admitted,SOURCE,borrowed_plan,stolen,model_urn=MODEL)
                        other=await session.create_context()
                        try:
                            with pytest.raises(InvalidRequestError):
                                await AsyncInputProvenance(other,allow_experimental=True).inspect(
                                    submission,admitted,SOURCE,plan,observation,model_urn=MODEL)
                        finally:await other.release()
                        blocks = [await observation.fetch_data(range(256)), await observation.fetch_data(range(256, 400))]
                        verify_data(observation, blocks, oracle['allCellsNorthToSouthWestToEast'], grid(oracle))
                    finally:
                        await context.release(); await session.release()
            def sync():
                with Client(ready['endpoints']['runtime'], **client_args(ready)) as api:
                    session = api.create_session(); context = session.create_context()
                    try:
                        submission = ResourceImporter(api, context).upload(path, **arguments())
                        submission.job.result(timeout=2)
                        authoring = Authoring(api)
                        parsed = authoring.parse(SOURCE)
                        workspace = authoring.create_workspace('inputworkspace', allow_write=True)
                        project = authoring.create_project(workspace, 'inputproject', allow_write=True)
                        admitted = authoring.create_document(parsed, project, allow_write=True)
                        observable = api.reasoner.resolve_observable('geography:Elevation in m')
                        plan = api.resolver.resolve(proposal.to_wire(observable), context)
                        plan.result(timeout=2)
                        observation = context.observe(proposal).result(timeout=2)
                        join = InputProvenance(context, allow_experimental=True)
                        check(join.inspect(submission, admitted, SOURCE, plan, observation, model_urn=MODEL), submission)
                        with pytest.raises(ProtocolError):
                            join.inspect(replace(submission, source_sha256='0'*64), admitted, SOURCE, plan, observation, model_urn=MODEL)
                        with pytest.raises(ProtocolError):
                            join.inspect(submission, admitted, SOURCE+'\n', plan, observation, model_urn=MODEL)
                        with Client(ready['endpoints']['runtime'], **client_args(ready,'sim-token-bob')) as bob:
                            foreign=Context(bob,context.configuration)
                            stolen=copy(observation); stolen._context=foreign
                            borrowed=replace(submission,job=Job(bob,submission.job.id,foreign,service='resources'))
                            borrowed_plan=Job(bob,plan.id,foreign,service='resolver',decoder=plan._decoder,result_kind='dataflow')
                            with pytest.raises(AuthorizationError):
                                InputProvenance(foreign,allow_experimental=True).inspect(
                                    borrowed,admitted,SOURCE,borrowed_plan,stolen,model_urn=MODEL)
                        other=session.create_context()
                        try:
                            with pytest.raises(InvalidRequestError):
                                InputProvenance(other,allow_experimental=True).inspect(
                                    submission,admitted,SOURCE,plan,observation,model_urn=MODEL)
                        finally:other.release()
                        blocks = [observation.fetch_data(range(256)), observation.fetch_data(range(256, 400))]
                        verify_data(observation, blocks, oracle['allCellsNorthToSouthWestToEast'], grid(oracle))
                    finally:
                        context.release(); session.release()
            asyncio.run(run()) if native_async else sync()
            assert not any(control(ready)['counts'].values())
            assert control(ready)['resources'] == 0 and control(ready)['authoring_assets'] == 3
        finally:
            reset(ready)  # Fixture-only ADD cleanup, never a native SDK RESOURCE delete.
        assert control(ready)['authoring_assets'] == 0
