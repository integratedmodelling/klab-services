"""Actual public native-shaped cache reads, not full/native graph convergence."""
import asyncio

import httpx
import pytest

from klab_client import AsyncClient, AsyncContext, Client, Context, ObservationRequest
from klab_client.commits import AsyncCommitInspector, CommitInspector
from klab_client.errors import AuthorizationError, MissingAssetError
from conftest import launch
from test_vertical import client_args, control, geometry


@pytest.mark.parametrize('native_async',[False,True])
def test_public_completed_observation_named_commit_cache_and_expiry_denial_cleanup(native_async):
    with launch('commit-inspection') as ready:
        def expire():
            response=httpx.post(ready['endpoints']['runtime'].rsplit('/',1)[0]+'/__sim__/expireCommits',
                headers={'Authorization':'sim-admin-control'},trust_env=False,timeout=3)
            response.raise_for_status()
        def check(value, observation):
            assert value.commit_id==observation.metadata['im:commit-id']
            assert value.assets['addedObservations']==(observation.id,)
            assert value.links['addedLinks'][0].target_id==observation.id
            assert value.links['addedLinks'][0].source_id==999  # May name previously seen assets.
            assert 'not full graph' in value.evidence
        async def run():
            async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=await api.create_session();context=await session.create_context()
                try:
                    inspector=AsyncCommitInspector(context)
                    with pytest.raises(MissingAssetError):await inspector.inspect(17)
                    job=await context.observe(ObservationRequest('geography:Elevation in m',geometry=geometry()))
                    assert (await job.status()).status=='WAITING'
                    with pytest.raises(MissingAssetError):await inspector.inspect(1001)
                    observation=await job.result(timeout=2)
                    selected=observation.metadata['im:commit-id']
                    value=await inspector.inspect(selected);check(value,observation)
                    async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        with pytest.raises(AuthorizationError):await AsyncCommitInspector(AsyncContext(bob,context.configuration)).inspect(selected)
                    expire()
                    with pytest.raises(MissingAssetError):await inspector.inspect(selected)
                    assert value.assets['addedObservations']==(observation.id,)  # Historical local receipt only.
                finally:
                    await context.release();await session.release()
        def sync():
            with Client(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=api.create_session();context=session.create_context()
                try:
                    inspector=CommitInspector(context)
                    with pytest.raises(MissingAssetError):inspector.inspect(17)
                    job=context.observe(ObservationRequest('geography:Elevation in m',geometry=geometry()))
                    assert job.status().status=='WAITING'
                    with pytest.raises(MissingAssetError):inspector.inspect(1001)
                    observation=job.result(timeout=2)
                    selected=observation.metadata['im:commit-id']
                    value=inspector.inspect(selected);check(value,observation)
                    with Client(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        with pytest.raises(AuthorizationError):CommitInspector(Context(bob,context.configuration)).inspect(selected)
                    expire()
                    with pytest.raises(MissingAssetError):inspector.inspect(selected)
                    assert value.assets['addedObservations']==(observation.id,)
                finally:
                    context.release();session.release()
        asyncio.run(run()) if native_async else sync()
        final=control(ready)
        assert not any(final['counts'].values()) and final['counts']['commit_reports']==0
