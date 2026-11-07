"""Public finite source/lifecycle/private-session disposal; never native actor execution."""
import asyncio,json
from dataclasses import replace

import pytest

from klab_client import Client,AsyncClient,Context,AsyncContext
from klab_client.managed_behaviors import ManagedBehaviors,AsyncManagedBehaviors
from klab_client.errors import AuthorizationError,InvalidRequestError,ServerError
from klab_simulator.managed_behaviors import SOURCES
from conftest import launch
from test_vertical import client_args,control


@pytest.mark.parametrize('native_async',[False,True])
@pytest.mark.parametrize('recipe,stop,terminal',[('finish',False,'FINISHED'),('fail',False,'FAILED'),('finish',True,'STOPPED')])
def test_public_source_admission_reconnect_outcome_and_independent_private_session_disposal(native_async,recipe,stop,terminal):
    with launch('managed-behavior') as ready:
        async def run():
            async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready)) as owner:
                session=await owner.create_session();context=await session.create_context()
                try:
                    facade=AsyncManagedBehaviors(context,allow_experimental=True)
                    with pytest.raises(InvalidRequestError):await facade.validate(SOURCES[recipe]+'// arbitrary source')
                    admission=await facade.validate(SOURCES[recipe])
                    assert control(ready)['counts']['sessions']==1
                    created=await facade.create(SOURCES[recipe],admission=admission)
                    assert created.state=='READY' and not created.private_session_disposed
                    assert control(ready)['counts']['sessions']==2
                    saved=json.loads(json.dumps(created.reference.to_wire()))
                    async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        foreign=AsyncContext(bob,context.configuration)
                        with pytest.raises(AuthorizationError):await AsyncManagedBehaviors(foreign,allow_experimental=True).reconnect(saved)
                    async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready)) as reader:
                        attached=await reader.attach_context(context.id)
                        peer=AsyncManagedBehaviors(attached,allow_experimental=True)
                        current=await peer.reconnect(saved)
                        assert current==created
                        command='stop' if stop else 'start'
                        running=await peer.command(current.reference,command,expected_revision=1)
                        with pytest.raises(ServerError):await peer.command(current.reference,command,expected_revision=1)
                        ended=await peer.read(current.reference)
                        assert ended.state==terminal and not ended.private_session_disposed
                        assert control(ready)['counts']['sessions']==2
                        with pytest.raises(ServerError):await peer.command(current.reference,'purge',expected_revision=ended.revision)
                        disposed=await peer.read(current.reference)
                        assert disposed.private_session_disposed and disposed.private_session_id==created.private_session_id
                        assert control(ready)['counts']['sessions']==1
                        assert disposed.outcome=={'FINISHED':'DECLARED_SUCCESS','FAILED':'DECLARED_FAILURE','STOPPED':'DECLARED_STOP'}[terminal]
                        assert await peer.command(current.reference,'purge',expected_revision=disposed.revision)
                        with pytest.raises(ServerError) as gone:await peer.reconnect(saved)
                        assert gone.value.status_code==410
                    assert (await owner.attach_context(context.id)).id==context.id
                finally:await context.release();await session.release()
        def sync():
            with Client(ready['endpoints']['runtime'],**client_args(ready)) as owner:
                session=owner.create_session();context=session.create_context()
                try:
                    facade=ManagedBehaviors(context,allow_experimental=True)
                    with pytest.raises(InvalidRequestError):facade.validate(SOURCES[recipe]+'// arbitrary source')
                    admission=facade.validate(SOURCES[recipe]);assert control(ready)['counts']['sessions']==1
                    created=facade.create(SOURCES[recipe],admission=admission)
                    assert created.state=='READY' and not created.private_session_disposed
                    assert control(ready)['counts']['sessions']==2
                    saved=json.loads(json.dumps(created.reference.to_wire()))
                    with Client(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        foreign=Context(bob,context.configuration)
                        with pytest.raises(AuthorizationError):ManagedBehaviors(foreign,allow_experimental=True).reconnect(saved)
                    with Client(ready['endpoints']['runtime'],**client_args(ready)) as reader:
                        attached=reader.attach_context(context.id);peer=ManagedBehaviors(attached,allow_experimental=True)
                        current=peer.reconnect(saved);assert current==created
                        command='stop' if stop else 'start'
                        running=peer.command(current.reference,command,expected_revision=1)
                        with pytest.raises(ServerError):peer.command(current.reference,command,expected_revision=1)
                        ended=peer.read(current.reference)
                        assert ended.state==terminal and not ended.private_session_disposed
                        assert control(ready)['counts']['sessions']==2
                        with pytest.raises(ServerError):peer.command(current.reference,'purge',expected_revision=ended.revision)
                        disposed=peer.read(current.reference)
                        assert disposed.private_session_disposed and disposed.private_session_id==created.private_session_id
                        assert control(ready)['counts']['sessions']==1
                        assert disposed.outcome=={'FINISHED':'DECLARED_SUCCESS','FAILED':'DECLARED_FAILURE','STOPPED':'DECLARED_STOP'}[terminal]
                        assert peer.command(current.reference,'purge',expected_revision=disposed.revision)
                        with pytest.raises(ServerError) as gone:peer.reconnect(saved)
                        assert gone.value.status_code==410
                    assert owner.attach_context(context.id).id==context.id
                finally:context.release();session.release()
        asyncio.run(run()) if native_async else sync()
        final=control(ready)
        assert not any(final['counts'].values()) and final['managed_behaviors']==0
