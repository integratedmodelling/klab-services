"""Public native-shaped dependency membership with honest incomplete graph limits."""
import asyncio

import httpx
import pytest

from klab_client import AsyncClient, Client, AsyncContext, Context
from klab_client.dependencies import AsyncDependencyInspector, DependencyInspector
from klab_client.errors import AuthorizationError
from conftest import launch
from test_vertical import client_args, control


@pytest.mark.parametrize('native_async',[False,True])
def test_public_group_membership_versions_current_credentials_and_no_provider_follow(native_async):
    with launch('dependency-inspection') as ready:
        def check(value):
            assert value.requested_urn=='synthetic.root'
            assert [m.urn for m in value.groups['namespaces']]==['synthetic.base','synthetic.root']
            assert value.groups['resources'][0].service_id=='external-provider'
            assert value.services['external-provider']=='https://untrusted-dependency.invalid/resources'
            assert 'UNKNOWN' in value.closure_completeness and 'UNAVAILABLE' in value.edge_completeness
            assert 'accessKey' not in repr(value) and 'not-public-authority' not in repr(value)
        def advance():
            url=ready['endpoints']['runtime'].rsplit('/',1)[0]+'/__sim__/advanceDependencies'
            httpx.post(url,headers={'Authorization':'sim-admin-control'},trust_env=False).raise_for_status()
        async def run():
            async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=await api.create_session();context=await session.create_context()
                try:
                    inspector=AsyncDependencyInspector(api,context)
                    before=await inspector.inspect('synthetic.root',knowledge_class='NAMESPACE');check(before)
                    advance()
                    after=await inspector.inspect('synthetic.root',knowledge_class='NAMESPACE');check(after)
                    assert before.results[0].version['build']==1 and after.results[0].version['build']==2
                    async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        with pytest.raises(AuthorizationError):
                            await AsyncDependencyInspector(bob,AsyncContext(bob,context.configuration)).inspect('synthetic.root',knowledge_class='NAMESPACE')
                finally:
                    await context.release();await session.release()
        def sync():
            with Client(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=api.create_session();context=session.create_context()
                try:
                    inspector=DependencyInspector(api,context)
                    before=inspector.inspect('synthetic.root',knowledge_class='NAMESPACE');check(before)
                    advance()
                    after=inspector.inspect('synthetic.root',knowledge_class='NAMESPACE');check(after)
                    assert before.results[0].version['build']==1 and after.results[0].version['build']==2
                    with Client(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        with pytest.raises(AuthorizationError):
                            DependencyInspector(bob,Context(bob,context.configuration)).inspect('synthetic.root',knowledge_class='NAMESPACE')
                finally:
                    context.release();session.release()
        asyncio.run(run()) if native_async else sync()
        assert not any(control(ready)['counts'].values())
