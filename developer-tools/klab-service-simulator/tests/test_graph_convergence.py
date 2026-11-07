"""Public finite reference convergence, never native scientific commit acceptance."""
import asyncio
from concurrent.futures import ThreadPoolExecutor

import httpx
import pytest

from klab_client import Client, AsyncClient, Context, AsyncContext, ObservationRequest
from klab_client.errors import AuthorizationError, InvalidRequestError, ServerError
from klab_client.reference_commits import (ReferenceCommits, AsyncReferenceCommits,
    ReferenceGraphProjection, AsyncReferenceGraphProjection, ReferenceResyncRequired)
from conftest import launch
from test_vertical import client_args, control, geometry


def admin(ready, action, context, observation=None, **extra):
    body={'contextId':context.id,**extra}
    if observation is not None:body['observationId']=observation.id
    response=httpx.post(ready['endpoints']['runtime'].rsplit('/',1)[0]+'/__sim__/graphReference'+action,
        json=body,headers={'Authorization':'sim-admin-control'},trust_env=False,timeout=3)
    response.raise_for_status()


@pytest.mark.parametrize('native_async',[False,True])
def test_public_two_client_snapshots_deltas_job_hints_gaps_and_explicit_resync(native_async):
    with launch('graph-convergence') as ready:
        request=ObservationRequest('geography:Elevation in m',geometry=geometry())
        async def run():
            async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready)) as owner, AsyncClient(
                    ready['endpoints']['runtime'],**client_args(ready)) as reader:
                session=await owner.create_session();context=await session.create_context()
                try:
                    attached=await reader.attach_context(context.id)
                    commits=AsyncReferenceCommits(context,allow_experimental=True)
                    remote=AsyncReferenceCommits(attached,allow_experimental=True)
                    projection=AsyncReferenceGraphProjection(remote)
                    with pytest.raises(InvalidRequestError):await projection.refresh()
                    initial=(await projection.resync()).snapshot
                    assert initial.revision==0 and len(initial.nodes)==1
                    job=await context.observe(request)
                    assert (await job.status()).status=='WAITING'
                    assert (await commits.snapshot()).digest==initial.digest
                    observed=await job.result(timeout=2)
                    assert (await commits.snapshot()).revision==0  # Finished is not a checkpoint.
                    # Hints are client-local and must not use another client's Job object.
                    from klab_client.async_client import AsyncJob
                    hint=AsyncJob(reader,job.id,attached)
                    projection.hint(hint);projection.hint(hint)
                    assert projection.view.invalidated and projection.view.snapshot==initial
                    first=await commits.checkpoint(expected_revision=0)
                    assert first.revision==1 and len(first.nodes)==3 and len(first.edges)==3
                    with pytest.raises(ServerError) as conflict:await remote.checkpoint(expected_revision=0)
                    assert conflict.value.status_code==409
                    assert (await projection.refresh()).snapshot==first
                    assert await remote.snapshot(revision=0)==initial
                    admin(ready,'Rename',context,observed,name='changed reference label')
                    second=await commits.checkpoint(expected_revision=1)
                    assert (await projection.refresh()).snapshot==second
                    assert any(n['properties'].get('name')=='changed reference label' for n in second.nodes)
                    admin(ready,'Drop',context,observed)
                    third=await commits.checkpoint(expected_revision=2)
                    assert len(third.nodes)==1 and (await projection.refresh()).snapshot==third
                    for revision in range(3,12):await commits.checkpoint(expected_revision=revision)
                    with pytest.raises(ReferenceResyncRequired):await projection.refresh()
                    assert projection.view.snapshot==third and projection.view.invalidated
                    assert (await projection.resync()).snapshot==await commits.snapshot()
                    admin(ready,'Epoch',context)
                    with pytest.raises(ReferenceResyncRequired):await projection.refresh()
                    assert (await projection.resync()).snapshot.epoch!=third.epoch
                    async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        with pytest.raises(AuthorizationError):
                            await AsyncReferenceCommits(AsyncContext(bob,context.configuration),allow_experimental=True).snapshot()
                    with pytest.raises(InvalidRequestError):AsyncReferenceCommits(context.within(observed.id),allow_experimental=True)
                    projection.clear();assert projection.view.snapshot is None
                finally:await context.release();await session.release()
        def sync():
            with Client(ready['endpoints']['runtime'],**client_args(ready)) as owner, Client(
                    ready['endpoints']['runtime'],**client_args(ready)) as reader:
                session=owner.create_session();context=session.create_context()
                try:
                    attached=reader.attach_context(context.id)
                    commits=ReferenceCommits(context,allow_experimental=True)
                    remote=ReferenceCommits(attached,allow_experimental=True)
                    projection=ReferenceGraphProjection(remote)
                    with pytest.raises(InvalidRequestError):projection.refresh()
                    initial=projection.resync().snapshot
                    assert initial.revision==0 and len(initial.nodes)==1
                    job=context.observe(request);assert job.status().status=='WAITING'
                    assert commits.snapshot().digest==initial.digest
                    observed=job.result(timeout=2)
                    assert commits.snapshot().revision==0
                    from klab_client.client import Job
                    hint=Job(reader,job.id,attached)
                    projection.hint(hint);projection.hint(hint)
                    assert projection.view.invalidated and projection.view.snapshot==initial
                    first=commits.checkpoint(expected_revision=0)
                    assert first.revision==1 and len(first.nodes)==3 and len(first.edges)==3
                    with pytest.raises(ServerError) as conflict:remote.checkpoint(expected_revision=0)
                    assert conflict.value.status_code==409
                    assert projection.refresh().snapshot==first
                    assert remote.snapshot(revision=0)==initial
                    admin(ready,'Rename',context,observed,name='changed reference label')
                    second=commits.checkpoint(expected_revision=1)
                    assert projection.refresh().snapshot==second
                    assert any(n['properties'].get('name')=='changed reference label' for n in second.nodes)
                    admin(ready,'Drop',context,observed)
                    third=commits.checkpoint(expected_revision=2)
                    assert len(third.nodes)==1 and projection.refresh().snapshot==third
                    for revision in range(3,12):commits.checkpoint(expected_revision=revision)
                    with pytest.raises(ReferenceResyncRequired):projection.refresh()
                    assert projection.view.snapshot==third and projection.view.invalidated
                    assert projection.resync().snapshot==commits.snapshot()
                    admin(ready,'Epoch',context)
                    with pytest.raises(ReferenceResyncRequired):projection.refresh()
                    assert projection.resync().snapshot.epoch!=third.epoch
                    with Client(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        with pytest.raises(AuthorizationError):
                            ReferenceCommits(Context(bob,context.configuration),allow_experimental=True).snapshot()
                    with pytest.raises(InvalidRequestError):ReferenceCommits(context.within(observed.id),allow_experimental=True)
                    projection.clear();assert projection.view.snapshot is None
                finally:context.release();session.release()
        asyncio.run(run()) if native_async else sync()
        final=control(ready)
        assert not any(final['counts'].values()) and final['counts']['graph_reference_roots']==0


def test_two_independent_public_clients_have_one_concurrent_cas_winner():
    with launch('graph-convergence') as ready:
        with Client(ready['endpoints']['runtime'],**client_args(ready)) as owner:
            session=owner.create_session();context=session.create_context()
            try:
                def checkpoint(_):
                    with Client(ready['endpoints']['runtime'],**client_args(ready)) as api:
                        commits=ReferenceCommits(api.attach_context(context.id),allow_experimental=True)
                        try:return commits.checkpoint(expected_revision=0)
                        except ServerError as error:
                            assert error.status_code==409
                            return 'CONFLICT'
                with ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(checkpoint,range(2)))
                assert results.count('CONFLICT')==1
                winner=next(result for result in results if result!='CONFLICT')
                assert winner==ReferenceCommits(context,allow_experimental=True).snapshot()
            finally:context.release();session.release()
        assert not any(control(ready)['counts'].values())
