"""Actual public source-bound finite graph, not native scientific dependency closure."""
import asyncio
import hashlib

import httpx
import pytest

from klab_client import AsyncClient, AsyncContext, Client, Context
from klab_client.dependency_graphs import AsyncDependencyGraphs, DependencyGraphs
from klab_client.errors import AuthorizationError, ProtocolError, ServerError
from conftest import launch
from test_vertical import client_args, control


def graph_control(ready,action):
    url=ready['endpoints']['runtime'].rsplit('/',1)[0]+'/__sim__/'+action
    httpx.post(url,headers={'Authorization':'sim-admin-control'},trust_env=False,timeout=3).raise_for_status()


def check(snapshot):
    assert len(snapshot.nodes)==5 and len(snapshot.edges)==4
    assert {node.urn for node in snapshot.nodes}=={'synthetic.model','synthetic.namespace','synthetic:input','synthetic.base','synthetic:base-input'}
    assert all(node.source_sha256==hashlib.sha256(node.source.encode()).hexdigest() for node in snapshot.nodes)
    assert all(node.access['contextId']==snapshot.context_id and node.access['right']=='READ' for node in snapshot.nodes)
    assert all(node.access['revision']==snapshot.revision for node in snapshot.nodes)
    assert 'REFERENCE_DECLARED_COMPLETE' in snapshot.evidence and 'not native' in snapshot.evidence
    assert snapshot.providers['external-provider']=='https://untrusted-graph-provider.invalid/resources'


@pytest.mark.parametrize('native_async',[False,True])
def test_public_multipage_source_version_access_graph_and_current_principal_denial(native_async):
    with launch('dependency-graphs') as ready:
        async def run():
            async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=await api.create_session();context=await session.create_context()
                try:
                    graphs=AsyncDependencyGraphs(context,allow_experimental=True)
                    first=await graphs.inspect('synthetic.model',page_size=1);check(first)
                    graph_control(ready,'advanceDependencyGraph')
                    second=await graphs.inspect('synthetic.model',page_size=2);check(second)
                    assert first.revision==1 and second.revision==2 and first.snapshot_id!=second.snapshot_id
                    assert first.nodes[2].source_sha256!=second.nodes[2].source_sha256
                    async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        denied=AsyncDependencyGraphs(AsyncContext(bob,context.configuration),allow_experimental=True)
                        with pytest.raises(AuthorizationError):await denied.inspect('synthetic.model')
                    with pytest.raises(ProtocolError):await AsyncDependencyGraphs(context,allow_experimental=True,max_nodes=2).inspect('synthetic.model',page_size=1)
                finally:
                    await context.release();await session.release()
        def sync():
            with Client(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=api.create_session();context=session.create_context()
                try:
                    graphs=DependencyGraphs(context,allow_experimental=True)
                    first=graphs.inspect('synthetic.model',page_size=1);check(first)
                    graph_control(ready,'advanceDependencyGraph')
                    second=graphs.inspect('synthetic.model',page_size=2);check(second)
                    assert first.revision==1 and second.revision==2 and first.snapshot_id!=second.snapshot_id
                    assert first.nodes[2].source_sha256!=second.nodes[2].source_sha256
                    with Client(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        denied=DependencyGraphs(Context(bob,context.configuration),allow_experimental=True)
                        with pytest.raises(AuthorizationError):denied.inspect('synthetic.model')
                    with pytest.raises(ProtocolError):DependencyGraphs(context,allow_experimental=True,max_nodes=2).inspect('synthetic.model',page_size=1)
                finally:
                    context.release();session.release()
        asyncio.run(run()) if native_async else sync()
        trace=control(ready)
        assert not any(trace['counts'].values())
        assert any('/experimental/dependencies/v1/graphs/' in entry['path'] for entry in trace['trace'])


@pytest.mark.parametrize('native_async',[False,True])
def test_public_source_revision_changes_during_pages_discard_entire_graph_no_retry(native_async):
    with launch('dependency-graphs') as ready:
        class Drift(httpx.BaseTransport):
            def __init__(self):self.delegate=httpx.HTTPTransport(trust_env=False);self.pages=0;self.changed=False
            def handle_request(self,request):
                result=self.delegate.handle_request(request)
                if '/experimental/dependencies/v1/graphs/' in request.url.path:
                    self.pages+=1
                    if not self.changed:
                        result.read();self.changed=True;graph_control(ready,'advanceDependencyGraph')
                return result
            def close(self):self.delegate.close()
        class AsyncDrift(httpx.AsyncBaseTransport):
            def __init__(self):self.delegate=httpx.AsyncHTTPTransport(trust_env=False);self.pages=0;self.changed=False
            async def handle_async_request(self,request):
                result=await self.delegate.handle_async_request(request)
                if '/experimental/dependencies/v1/graphs/' in request.url.path:
                    self.pages+=1
                    if not self.changed:
                        await result.aread();self.changed=True
                        url=ready['endpoints']['runtime'].rsplit('/',1)[0]+'/__sim__/advanceDependencyGraph'
                        async with httpx.AsyncClient(trust_env=False) as admin:
                            (await admin.post(url,headers={'Authorization':'sim-admin-control'})).raise_for_status()
                return result
            async def aclose(self):await self.delegate.aclose()
        async def run():
            transport=AsyncDrift()
            async with AsyncClient(ready['endpoints']['runtime'],http_transport=transport,**client_args(ready)) as api:
                session=await api.create_session();context=await session.create_context()
                try:
                    graphs=AsyncDependencyGraphs(context,allow_experimental=True)
                    with pytest.raises(ServerError) as conflict:await graphs.inspect('synthetic.model',page_size=1)
                    assert conflict.value.status_code==409 and transport.pages==2
                    refreshed=await graphs.inspect('synthetic.model',page_size=1);check(refreshed)
                    assert refreshed.revision==2 and transport.pages==7
                finally:
                    await context.release();await session.release()
        def sync():
            transport=Drift()
            with Client(ready['endpoints']['runtime'],http_transport=transport,**client_args(ready)) as api:
                session=api.create_session();context=session.create_context()
                try:
                    graphs=DependencyGraphs(context,allow_experimental=True)
                    with pytest.raises(ServerError) as conflict:graphs.inspect('synthetic.model',page_size=1)
                    assert conflict.value.status_code==409 and transport.pages==2
                    refreshed=graphs.inspect('synthetic.model',page_size=1);check(refreshed)
                    assert refreshed.revision==2 and transport.pages==7
                finally:
                    context.release();session.release()
        asyncio.run(run()) if native_async else sync()
        assert not any(control(ready)['counts'].values())


@pytest.mark.parametrize('native_async',[False,True])
def test_public_cycle_gap_and_page_budget_never_return_partial_graph(native_async):
    with launch('dependency-graphs') as ready:
        async def run():
            async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=await api.create_session();context=await session.create_context()
                try:
                    graphs=AsyncDependencyGraphs(context,allow_experimental=True)
                    for action in ('dependencyGraphCycle','dependencyGraphGap'):
                        graph_control(ready,action)
                        with pytest.raises(ProtocolError):await graphs.inspect('synthetic.model',page_size=1)
                    graph_control(ready,'dependencyGraphComplete')
                    with pytest.raises(ProtocolError):await AsyncDependencyGraphs(context,allow_experimental=True,max_pages=1).inspect('synthetic.model',page_size=1)
                    check(await graphs.inspect('synthetic.model',page_size=1))
                finally:
                    await context.release();await session.release()
        def sync():
            with Client(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=api.create_session();context=session.create_context()
                try:
                    graphs=DependencyGraphs(context,allow_experimental=True)
                    for action in ('dependencyGraphCycle','dependencyGraphGap'):
                        graph_control(ready,action)
                        with pytest.raises(ProtocolError):graphs.inspect('synthetic.model',page_size=1)
                    graph_control(ready,'dependencyGraphComplete')
                    with pytest.raises(ProtocolError):DependencyGraphs(context,allow_experimental=True,max_pages=1).inspect('synthetic.model',page_size=1)
                    check(graphs.inspect('synthetic.model',page_size=1))
                finally:
                    context.release();session.release()
        asyncio.run(run()) if native_async else sync()
        assert not any(control(ready)['counts'].values())


@pytest.mark.parametrize('native_async',[False,True])
def test_public_resource_root_transitive_source_graph_not_only_model_root(native_async):
    with launch('dependency-graphs') as ready:
        def verify(snapshot):
            assert len(snapshot.nodes)==2 and len(snapshot.edges)==1
            assert {node.urn for node in snapshot.nodes}=={'synthetic:input','synthetic:base-input'}
            selected=next(node for node in snapshot.nodes if node.node_id==snapshot.root_node_id)
            assert selected.urn=='synthetic:input' and selected.knowledge_class=='RESOURCE'
            assert all(hashlib.sha256(node.source.encode()).hexdigest()==node.source_sha256 for node in snapshot.nodes)
            assert all(node.provider_id=='external-provider' and node.access['right']=='READ' for node in snapshot.nodes)
        async def run():
            async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=await api.create_session();context=await session.create_context()
                try:verify(await AsyncDependencyGraphs(context,allow_experimental=True).inspect('synthetic:input',knowledge_class='RESOURCE',page_size=1))
                finally:
                    await context.release();await session.release()
        def sync():
            with Client(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=api.create_session();context=session.create_context()
                try:verify(DependencyGraphs(context,allow_experimental=True).inspect('synthetic:input',knowledge_class='RESOURCE',page_size=1))
                finally:
                    context.release();session.release()
        asyncio.run(run()) if native_async else sync()
        assert not any(control(ready)['counts'].values())
