"""Actual public import/source/plan/observe/explanation, not scientific execution."""
import asyncio
from copy import copy
import httpx,pytest

from klab_client import (Client,AsyncClient,Context,AsyncContext,Authoring,AsyncAuthoring,ResourceImporter,
    AsyncResourceImporter,ObservationRequest,to_handle)
from klab_client.execution_explanations import ExecutionExplanations,AsyncExecutionExplanations
from klab_client.input_provenance import InputProvenance,AsyncInputProvenance
from klab_client.errors import AuthorizationError,InvalidRequestError
from klab_simulator.authoring import MODEL,SOURCE
from klab_simulator.fixtures import authentic_fixture
from conftest import launch
from test_vertical import client_args,control,geometry,verify_data
from test_resource_imports import arguments,source
from test_input_workflow import reset,grid


@pytest.mark.parametrize('native_async',[False,True])
def test_actual_supplied_input_model_component_plan_recorded_and_semantic_effects(native_async,tmp_path):
    path=source(tmp_path)
    request=ObservationRequest('geography:Elevation in m',geometry=geometry(authentic_fixture()))
    with launch('execution-explanation') as ready:
        def update(context,observation):
            response=httpx.post(ready['endpoints']['runtime'].rsplit('/',1)[0]+'/__sim__/executionSemanticUpdate',
                json={'contextId':context.id,'observationId':observation.id,'expectedRevision':0,'afterObservable':'geography:Elevation in cm'},
                headers={'Authorization':'sim-admin-control'},trust_env=False,timeout=3)
            response.raise_for_status()
        def check(explanation,joined,selected):
            assert explanation.inputs[0]['binarySha256']==joined.binary_sha256
            assert explanation.inputs[0]['sourceSha256']==joined.manifest_sha256
            assert explanation.selections[0]['modelUrn']==joined.model_urn
            assert explanation.selections[0]['componentVersion']=='1.0.0'
            assert all(explanation.selections[0][key]==value for key,value in selected['referenceComponentSelection'].items())
            assert explanation.planned[0]['effect']=='CREATE' and explanation.recorded[0]['outcome']=='SUCCESS'
        async def run():
            async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=await api.create_session();context=await session.create_context()
                try:
                    submission=await AsyncResourceImporter(api,context).upload(path,**arguments())
                    await submission.job.result(timeout=2)
                    author=AsyncAuthoring(api);parsed=await author.parse(SOURCE)
                    workspace=await author.create_workspace('explainworkspace',allow_write=True)
                    project=await author.create_project(workspace,'explainproject',allow_write=True)
                    binding=await author.create_document(parsed,project,allow_write=True)
                    unplanned=await (await context.observe(request)).result(timeout=2)
                    with pytest.raises(InvalidRequestError):
                        await AsyncExecutionExplanations(context,allow_experimental=True).inspect(unplanned)
                    observable=await api.reasoner.resolve_observable('geography:Elevation in m')
                    plan=await api.resolver.resolve(request.to_wire(observable),context);selected=await plan.result(timeout=2)
                    observed=await (await context.observe(request)).result(timeout=2)
                    joined=await AsyncInputProvenance(context,allow_experimental=True).inspect(
                        submission,binding,SOURCE,plan,observed,model_urn=MODEL)
                    explanations=AsyncExecutionExplanations(context,allow_experimental=True)
                    before=await explanations.inspect(observed);check(before,joined,selected)
                    update(context,observed)
                    after=await explanations.inspect(observed)
                    assert before.revision==0 and not before.semantic_updates
                    assert after.revision==1 and after.semantic_updates[0]['afterObservable']=='geography:Elevation in cm'
                    assert after.recorded[-1]['effect']=='SEMANTIC_UPDATE' and after.digest!=before.digest
                    current=await api.restore_handle(to_handle(observed))
                    assert current.metadata['referenceSemanticUpdate']['afterObservable']=='geography:Elevation in cm'
                    assert current.observable.raw['urn']=='geography:Elevation in m'  # No numeric mediation inferred.
                    blocks=[await observed.fetch_data(range(256)),await observed.fetch_data(range(256,400))]
                    oracle=authentic_fixture();verify_data(observed,blocks,oracle['allCellsNorthToSouthWestToEast'],grid(oracle))
                    async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready)) as reader:
                        attached=await reader.attach_context(context.id);fresh=await reader.restore_handle(to_handle(observed))
                        assert await AsyncExecutionExplanations(attached,allow_experimental=True).inspect(fresh)==after
                    async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        foreign=AsyncContext(bob,context.configuration);borrowed=copy(observed);borrowed._context=foreign
                        with pytest.raises(AuthorizationError):await AsyncExecutionExplanations(foreign,allow_experimental=True).inspect(borrowed)
                    with pytest.raises(InvalidRequestError):AsyncExecutionExplanations(context.within(observed.id),allow_experimental=True)
                finally:await context.release();await session.release()
        def sync():
            with Client(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=api.create_session();context=session.create_context()
                try:
                    submission=ResourceImporter(api,context).upload(path,**arguments());submission.job.result(timeout=2)
                    author=Authoring(api);parsed=author.parse(SOURCE)
                    workspace=author.create_workspace('explainworkspace',allow_write=True)
                    project=author.create_project(workspace,'explainproject',allow_write=True)
                    binding=author.create_document(parsed,project,allow_write=True)
                    unplanned=context.observe(request).result(timeout=2)
                    with pytest.raises(InvalidRequestError):
                        ExecutionExplanations(context,allow_experimental=True).inspect(unplanned)
                    observable=api.reasoner.resolve_observable('geography:Elevation in m')
                    plan=api.resolver.resolve(request.to_wire(observable),context);selected=plan.result(timeout=2)
                    observed=context.observe(request).result(timeout=2)
                    joined=InputProvenance(context,allow_experimental=True).inspect(
                        submission,binding,SOURCE,plan,observed,model_urn=MODEL)
                    explanations=ExecutionExplanations(context,allow_experimental=True)
                    before=explanations.inspect(observed);check(before,joined,selected)
                    update(context,observed);after=explanations.inspect(observed)
                    assert before.revision==0 and not before.semantic_updates
                    assert after.revision==1 and after.semantic_updates[0]['afterObservable']=='geography:Elevation in cm'
                    assert after.recorded[-1]['effect']=='SEMANTIC_UPDATE' and after.digest!=before.digest
                    current=api.restore_handle(to_handle(observed))
                    assert current.metadata['referenceSemanticUpdate']['afterObservable']=='geography:Elevation in cm'
                    assert current.observable.raw['urn']=='geography:Elevation in m'
                    blocks=[observed.fetch_data(range(256)),observed.fetch_data(range(256,400))]
                    oracle=authentic_fixture();verify_data(observed,blocks,oracle['allCellsNorthToSouthWestToEast'],grid(oracle))
                    with Client(ready['endpoints']['runtime'],**client_args(ready)) as reader:
                        attached=reader.attach_context(context.id);fresh=reader.restore_handle(to_handle(observed))
                        assert ExecutionExplanations(attached,allow_experimental=True).inspect(fresh)==after
                    with Client(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        foreign=Context(bob,context.configuration);borrowed=copy(observed);borrowed._context=foreign
                        with pytest.raises(AuthorizationError):ExecutionExplanations(foreign,allow_experimental=True).inspect(borrowed)
                    with pytest.raises(InvalidRequestError):ExecutionExplanations(context.within(observed.id),allow_experimental=True)
                finally:context.release();session.release()
        try:
            asyncio.run(run()) if native_async else sync()
            assert not any(control(ready)['counts'].values())
        finally:reset(ready)
