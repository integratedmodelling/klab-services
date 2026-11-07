"""Typed public A/B authorized snapshots -> C-owned copied result, not live federation."""
import asyncio
from contextlib import ExitStack,AsyncExitStack

import pytest

from klab_client import Client,AsyncClient,Context,AsyncContext,ObservationRequest
from klab_client.copied_composition import (CopiedCompositions,AsyncCopiedCompositions,
    capture_copied_source,async_capture_copied_source)
from klab_client.errors import AuthorizationError,ServerError
from conftest import launch
from test_vertical import client_args,control,geometry


@pytest.mark.parametrize('native_async',[False,True])
def test_public_independent_a_b_capture_and_c_result_lifecycle_never_delegates_source_grants(native_async):
    with ExitStack() as stack:
        ready=[stack.enter_context(launch(scenario,seed=seed)) for scenario,seed in
            (('elevation',11),('zero-negative-nodata',22),('copied-composition',33))]
        tokens=('sim-token-admin-alice','sim-token-alice','sim-token-bob')
        proposal=ObservationRequest('geography:Elevation in m',geometry=geometry())
        def check(result,sources):
            expected=tuple(a+b if va and vb else None for a,b,va,vb in
                zip(sources[0].values,sources[1].values,sources[0].valid,sources[1].valid))
            assert result.values==expected and len(result.values)==400
            assert result.source_vector==tuple(source.identity for source in sources)
            assert result.runtime_id==ready[2]['service_ids']['runtime']
            assert result.live_federation is False and result.durable is False
            assert result.evidence=='CALLER_ASSERTED_COPIED_REFERENCE_NOT_REMOTE_GRANT'
            assert sources[0].identity.origin['observation_id']==sources[1].identity.origin['observation_id']
            assert sources[0].identity.origin['runtime_id']!=sources[1].identity.origin['runtime_id']
        async def run():
            async with AsyncExitStack() as resources:
                clients=[await resources.enter_async_context(AsyncClient(item['endpoints']['runtime'],**client_args(item,token)))
                    for item,token in zip(ready,tokens)]
                sessions=[await api.create_session() for api in clients]
                contexts=[await session.create_context() for session in sessions]
                try:
                    observed=[await (await context.observe(proposal)).result(timeout=2) for context in contexts[:2]]
                    sources=tuple([await async_capture_copied_source(observation,allow_experimental=True) for observation in observed])
                    async with AsyncClient(ready[0]['endpoints']['runtime'],**client_args(ready[0],'sim-token-bob')) as bob:
                        with pytest.raises(AuthorizationError):await bob.restore_handle(observed[0].to_handle())
                    before=[control(item)['counts'] for item in ready[:2]]
                    facade=AsyncCopiedCompositions(contexts[2],allow_experimental=True)
                    result=await facade.compose(sources,expected_vector=tuple(source.identity for source in sources));check(result,sources)
                    assert await facade.inspect(result)==result
                    async with AsyncClient(ready[2]['endpoints']['runtime'],**client_args(ready[2],'sim-token-alice')) as foreign:
                        attached=AsyncContext(foreign,contexts[2].configuration)
                        with pytest.raises(AuthorizationError):await AsyncCopiedCompositions(attached,allow_experimental=True).inspect(result)
                    assert await facade.release(result) is True
                    with pytest.raises(ServerError) as expired:await facade.inspect(result)
                    assert expired.value.status_code==410
                    assert [control(item)['counts'] for item in ready[:2]]==before
                    again=[await async_capture_copied_source(observation,allow_experimental=True) for observation in observed]
                    assert [(item.identity.origin,item.identity.revision,item.values,item.valid) for item in again]==[
                        (item.identity.origin,item.identity.revision,item.values,item.valid) for item in sources]
                finally:
                    for context in reversed(contexts):await context.release()
                    for session in reversed(sessions):await session.release()
        def sync():
            with ExitStack() as resources:
                clients=[resources.enter_context(Client(item['endpoints']['runtime'],**client_args(item,token)))
                    for item,token in zip(ready,tokens)]
                sessions=[api.create_session() for api in clients];contexts=[session.create_context() for session in sessions]
                try:
                    observed=[context.observe(proposal).result(timeout=2) for context in contexts[:2]]
                    sources=tuple(capture_copied_source(observation,allow_experimental=True) for observation in observed)
                    with Client(ready[0]['endpoints']['runtime'],**client_args(ready[0],'sim-token-bob')) as bob:
                        with pytest.raises(AuthorizationError):bob.restore_handle(observed[0].to_handle())
                    before=[control(item)['counts'] for item in ready[:2]]
                    facade=CopiedCompositions(contexts[2],allow_experimental=True)
                    result=facade.compose(sources,expected_vector=tuple(source.identity for source in sources));check(result,sources)
                    assert facade.inspect(result)==result
                    with Client(ready[2]['endpoints']['runtime'],**client_args(ready[2],'sim-token-alice')) as foreign:
                        attached=Context(foreign,contexts[2].configuration)
                        with pytest.raises(AuthorizationError):CopiedCompositions(attached,allow_experimental=True).inspect(result)
                    assert facade.release(result) is True
                    with pytest.raises(ServerError) as expired:facade.inspect(result)
                    assert expired.value.status_code==410
                    assert [control(item)['counts'] for item in ready[:2]]==before
                    again=[capture_copied_source(observation,allow_experimental=True) for observation in observed]
                    assert [(item.identity.origin,item.identity.revision,item.values,item.valid) for item in again]==[
                        (item.identity.origin,item.identity.revision,item.values,item.valid) for item in sources]
                finally:
                    for context in reversed(contexts):context.release()
                    for session in reversed(sessions):session.release()
        asyncio.run(run()) if native_async else sync()
        assert all(not any(control(item)['counts'].values()) for item in ready)
        assert control(ready[2])['compositions']==0
        assert not any(row['path'].endswith('/submit') for row in control(ready[2])['trace'])
