"""Actual public finite state/window reads, not native provider time/window science."""
import asyncio
from copy import copy

import pytest

from klab_client import Client,AsyncClient,Context,AsyncContext,ObservationRequest
from klab_client.errors import AuthorizationError,InvalidRequestError,ServerError
from klab_client.reference_windows import ReadPlanSelections,AsyncReadPlanSelections,ReferenceWindow
from klab_client.read_plans import (open_read_plan,read_plan_shard,assemble_read_plan,release_read_plan,
    async_open_read_plan,async_read_plan_shard,async_release_read_plan)
from conftest import launch
from test_vertical import client_args,control,geometry


@pytest.mark.parametrize('native_async',[False,True])
def test_public_exact_windows_temporal_states_masks_layout_and_owned_release(native_async):
    with launch('selected-read-plans') as ready:
        window=ReferenceWindow(2,5,4,8)
        indices=[row*20+column for row in range(2,5) for column in range(4,8)]
        baseline=tuple(float(100+2*row+3*column) for row in range(2,5) for column in range(4,8))
        alternate=tuple(None if index%11==0 else float(index%9-4) for index in indices)
        request=ObservationRequest('geography:Elevation in m',geometry=geometry())
        def check(first,second):
            assert first.data.values==baseline and first.data.valid==(True,)*12
            assert second.data.values==alternate and second.data.valid==tuple(value is not None for value in alternate)
            assert first.data.plan.shape==second.data.plan.shape==(3,4)
            assert first.data.plan.transform==second.data.plan.transform==(100,0,400,0,-100,1800)
            assert first.data.plan.units==second.data.plan.units=='m'
            assert first.selection.temporal_state.instant=='2020-01-01T00:00:00Z'
            assert second.selection.temporal_state.instant=='2020-01-02T00:00:00Z'
            assert first.selection.temporal_state.source_revision!=second.selection.temporal_state.source_revision
            assert first.data.plan.revision!=second.data.plan.revision
        async def run():
            async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=await api.create_session();context=await session.create_context()
                try:
                    observed=await (await context.observe(request)).result(timeout=2)
                    facade=AsyncReadPlanSelections(context,allow_experimental=True)
                    states=await facade.states(observed)
                    assert [state.state_id for state in states]==['baseline','alternate']
                    first=await facade.open(observed,window,states[0],shard_cells=5)
                    second=await facade.open(observed,window,states[1],shard_cells=7)
                    try:
                        one=await facade.read(first);two=await facade.read(second);check(one,two)
                        assert (await facade.read(first)).data.values==one.data.values
                        whole=await async_open_read_plan(observed,allow_experimental=True)
                        try:
                            cells=assemble_read_plan(whole,tuple([await async_read_plan_shard(whole,spec.index,allow_experimental=True) for spec in whole.shards]))
                            assert len(cells.values)==400 and whole.revision==states[0].source_revision and cells.values[0]==100
                        finally:await async_release_read_plan(whole,allow_experimental=True)
                        async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                            denied=AsyncContext(bob,context.configuration);borrowed=copy(observed);borrowed._context=denied
                            with pytest.raises(AuthorizationError):await AsyncReadPlanSelections(denied,allow_experimental=True).states(borrowed)
                        with pytest.raises(InvalidRequestError):await facade.open(observed,ReferenceWindow(0,21,0,1),states[0])
                    finally:await facade.release(first);await facade.release(second)
                    with pytest.raises(ServerError) as expired:await facade.read(first)
                    assert expired.value.status_code==410
                finally:await context.release();await session.release()
        def sync():
            with Client(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=api.create_session();context=session.create_context()
                try:
                    observed=context.observe(request).result(timeout=2)
                    facade=ReadPlanSelections(context,allow_experimental=True)
                    states=facade.states(observed);assert [state.state_id for state in states]==['baseline','alternate']
                    first=facade.open(observed,window,states[0],shard_cells=5)
                    second=facade.open(observed,window,states[1],shard_cells=7)
                    try:
                        one=facade.read(first);two=facade.read(second);check(one,two)
                        assert facade.read(first).data.values==one.data.values
                        whole=open_read_plan(observed,allow_experimental=True)
                        try:
                            cells=assemble_read_plan(whole,tuple(read_plan_shard(whole,spec.index,allow_experimental=True) for spec in whole.shards))
                            assert len(cells.values)==400 and whole.revision==states[0].source_revision and cells.values[0]==100
                        finally:release_read_plan(whole,allow_experimental=True)
                        with Client(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                            denied=Context(bob,context.configuration);borrowed=copy(observed);borrowed._context=denied
                            with pytest.raises(AuthorizationError):ReadPlanSelections(denied,allow_experimental=True).states(borrowed)
                        with pytest.raises(InvalidRequestError):facade.open(observed,ReferenceWindow(0,21,0,1),states[0])
                    finally:facade.release(first);facade.release(second)
                    with pytest.raises(ServerError) as expired:facade.read(first)
                    assert expired.value.status_code==410
                finally:context.release();session.release()
        asyncio.run(run()) if native_async else sync()
        assert control(ready)['read_plans']==0 and not any(control(ready)['counts'].values())
