"""Native-shaped public commit-cache controls; no simulator imports."""
import asyncio
import httpx, pytest
from test_selections import client,root
from klab_client import Endpoint
from klab_client.errors import InvalidRequestError,ProtocolError,MissingAssetError
import klab_client.commits as draft


@pytest.mark.parametrize('native_async',[False,True])
def test_native_cache_descriptor_preserves_unknowns_and_old_endpoint_links(native_async):
    calls=[]
    def handler(request):
        calls.append(request)
        if request.url.path.endswith('/capabilities'):return httpx.Response(200,json={'serviceId':'runtime-id'})
        assert request.url.path=='/api/v1/commit' and request.url.params['id']=='17'
        return httpx.Response(200,json={'id':17,'timestamp':100,'owner':'alice','addedAssets':[3],
            'addedObservations':[3],'addedLinks':[{'first':2,'second':3,'third':'CONTAINS'}]})
    async def run():
        async with client(handler,True) as api:return await draft.AsyncCommitInspector(root(api)).inspect(17)
    if native_async:value=asyncio.run(run())
    else:
        with client(handler) as api:value=draft.CommitInspector(root(api)).inspect(17)
    assert len(calls)==3 and value.assets['modifiedAssets'] is None
    assert value.links['addedLinks'][0].source_id==2
    assert 'not full graph' in value.evidence


@pytest.mark.parametrize('value',[None,{'id':18},{'id':17,'timestamp':True},{'id':17,'owner':''},
    {'id':17,'addedAssets':[True]},{'id':17,'addedAssets':[1,1]},
    {'id':17,'addedLinks':[{'first':1,'second':True,'third':'REL'}]},
    {'id':17,'addedAssets':'unknown'}, {'id':17,'owner':'current-secret'}])
def test_unavailable_or_malformed_commit_is_not_empty_converged_graph(value):
    def handler(request):
        return httpx.Response(200,json={'serviceId':'runtime-id'}) if request.url.path.endswith('/capabilities') else httpx.Response(200,content=b'null' if value is None else __import__('json').dumps(value).encode(),headers={'Content-Type':'application/json'})
    with client(handler) as api:
        with pytest.raises((ProtocolError,MissingAssetError)):draft.CommitInspector(root(api)).inspect(17)


def invoke(value,*,native_async=False,max_items=1024,drift=None):
    calls=[];owner={}
    def handler(request):
        calls.append(request)
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json={'serviceId':'runtime-id' if drift!='identity' or len(calls)==1 else 'restarted-id'})
        if drift=='credential':owner['api'].transport.endpoints['runtime']=Endpoint('https://runtime.test','other-secret')
        return httpx.Response(200,content=__import__('json').dumps(value).encode(),headers={'Content-Type':'application/json'})
    async def run():
        async with client(handler,True) as api:
            owner['api']=api
            return await draft.AsyncCommitInspector(root(api),max_items=max_items).inspect(17)
    if native_async:return asyncio.run(run())
    with client(handler) as api:
        owner['api']=api
        return draft.CommitInspector(root(api),max_items=max_items).inspect(17)


def test_missing_groups_remain_unknown_and_reported_empty_groups_are_distinct():
    unknown=invoke({'id':17})
    empty=invoke({'id':17,**{k:[] for k in (*draft.ASSETS,*draft.LINKS)}})
    assert all(value is None for value in (*unknown.assets.values(),*unknown.links.values()))
    assert all(value==() for value in (*empty.assets.values(),*empty.links.values()))
    assert unknown.owner is None and unknown.timestamp is None
    with pytest.raises(TypeError):empty.assets['addedAssets']=(1,)
    with pytest.raises(TypeError):empty.raw['id']=18


@pytest.mark.parametrize('native_async',[False,True])
@pytest.mark.parametrize('identity',[0,-1,True,2**63,'17',None])
def test_explicit_numeric_id_required_without_any_network(native_async,identity):
    def offline(request):raise AssertionError('No network for invalid ID')
    async def run():
        async with client(offline,True) as api:
            with pytest.raises(InvalidRequestError):await draft.AsyncCommitInspector(root(api)).inspect(identity)
    if native_async:asyncio.run(run())
    else:
        with client(offline) as api:
            with pytest.raises(InvalidRequestError):draft.CommitInspector(root(api)).inspect(identity)


@pytest.mark.parametrize('value',[[],{'id':True},{'id':17,'timestamp':-1},{'id':17,'timestamp':2**63},
    {'id':17,'owner':1},{'id':17,'addedLinks':{}},{'id':17,'addedLinks':[{}]},
    {'id':17,'addedLinks':[{'first':1,'second':2,'third':''}]},
    {'id':17,'addedLinks':[{'first':1,'second':2,'third':'REL'},{'first':1,'second':2,'third':'REL'}]}])
def test_unsupported_descriptor_shapes_refuse(value):
    with pytest.raises(ProtocolError):invoke(value)


def test_total_budget_across_all_groups_refuses_without_truncation():
    with pytest.raises(ProtocolError):invoke({'id':17,'addedAssets':[1,2],'modifiedAssets':[3]},max_items=2)


@pytest.mark.parametrize('native_async',[False,True])
@pytest.mark.parametrize('drift',['identity','credential'])
def test_restart_or_current_credential_change_discards_report(native_async,drift):
    with pytest.raises(ProtocolError):invoke({'id':17},native_async=native_async,drift=drift)


def test_context_client_replacement_is_not_implicit_authorization_rebind():
    def offline(request):raise AssertionError('No request after context replacement')
    with client(offline) as first,client(offline) as second:
        context=root(first);inspector=draft.CommitInspector(context)
        context.client=second
        with pytest.raises(ProtocolError):inspector.inspect(17)


def test_root_replacement_with_focused_scope_refuses_before_network():
    def offline(request):raise AssertionError('No request after root replacement')
    with client(offline) as api:
        context=root(api);inspector=draft.CommitInspector(context)
        inspector.context=context.within(1)
        with pytest.raises(ProtocolError):inspector.inspect(17)


def test_malformed_or_mismatched_root_and_async_client_are_not_implicitly_attached():
    def offline(request):raise AssertionError('No implicit attach or read')
    with client(offline) as api:
        with pytest.raises(InvalidRequestError):draft.CommitInspector(None)
        with pytest.raises(InvalidRequestError):draft.CommitInspector(root(api).within(1))
        with pytest.raises(InvalidRequestError):draft.AsyncCommitInspector(root(api))
        context=root(api);inspector=draft.CommitInspector(context)
        context.configuration['serviceId']='other'
        with pytest.raises(ProtocolError):inspector.inspect(17)
