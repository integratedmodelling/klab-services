"""Native-shaped dependency membership, no simulator or server URL routing."""
import asyncio

import httpx
import pytest

from klab_client import AsyncClient, Client, Endpoint
from klab_client.dependencies import AsyncDependencyInspector, DependencyInspector, GROUPS
from klab_client.errors import InvalidRequestError, ProtocolError, UnsupportedOperationError


def member(urn='synthetic.root', kind='NAMESPACE', provider='resources-id'):
    return {'resourceUrn':urn,'knowledgeClass':kind,'serviceId':provider,
        'resourceVersion':{'major':1,'minor':2,'build':3},'timestamp':42,'optional':False,
        'projectUrn':'synthetic.project','metadata':{'label':'finite'},'accessKey':'never-export-this'}


def payload():
    return {'results':[member()],**{key:[] for key in GROUPS},
        'namespaces':[member('synthetic.dependency'),member()],
        'resources':[member('synthetic:resource','RESOURCE','remote-id')],
        'services':{'remote-id':'https://untrusted-provider.test/base'},'notifications':[]}


def invoke(native_async=False, value=None, *, budget=100):
    calls=[]
    def handler(request):
        calls.append(request)
        assert request.url.host=='resources.test'
        assert request.headers['Authorization']=='issued-secret'
        if request.url.path.endswith('/capabilities'): return httpx.Response(200,json={'serviceId':'resources-id'})
        assert request.method=='GET'
        return httpx.Response(200,json=payload() if value is None else value)
    peers={'resources':Endpoint('https://resources.test','issued-secret'),'http_transport':httpx.MockTransport(handler)}
    async def run():
        async with AsyncClient('https://runtime.test',**peers) as api:
            return await AsyncDependencyInspector(api,max_items=budget).inspect('synthetic.root',knowledge_class='NAMESPACE')
    if native_async: result=asyncio.run(run())
    else:
        with Client('https://runtime.test',**peers) as api:
            result=DependencyInspector(api,max_items=budget).inspect('synthetic.root',knowledge_class='NAMESPACE')
    return result,calls


@pytest.mark.parametrize('native_async',[False,True])
def test_grouped_native_membership_keeps_order_versions_provider_and_honest_limits(native_async):
    result,calls=invoke(native_async)
    assert len(calls)==3 and all(r.url.host=='resources.test' for r in calls)
    assert [m.urn for m in result.groups['namespaces']]==['synthetic.dependency','synthetic.root']
    assert result.groups['resources'][0].service_id=='remote-id'
    assert result.results[0].version['major']==1 and result.results[0].timestamp==42
    assert result.services['remote-id']=='https://untrusted-provider.test/base'
    assert 'UNKNOWN' in result.closure_completeness and 'UNAVAILABLE' in result.edge_completeness
    assert 'never-export-this' not in repr(result)
    with pytest.raises(TypeError): result.groups['namespaces']=()
    with pytest.raises(TypeError): result.results[0].version['major']=2


def test_missing_groups_are_unknown_not_empty_closure():
    result,_=invoke(value={'results':[member()]})
    assert all(result.groups[group] is None for group in GROUPS)


@pytest.mark.parametrize('change',[{'resourceUrn':None},{'serviceId':''},{'knowledgeClass':1},
    {'timestamp':True},{'timestamp':-1},{'optional':1},{'projectUrn':{}},{'metadata':[]}])
def test_malformed_members_refuse(change):
    value=payload();value['results']=[{**member(),**change}]
    with pytest.raises(ProtocolError): invoke(value=value)


@pytest.mark.parametrize('url',['https://user:secret@untrusted.test','https://untrusted.test?token=x',
    'file:///secret','https://untrusted.test/#secret'])
def test_provider_urls_are_not_credential_carriers_or_routing_authority(url):
    value=payload();value['services']={'other':url}
    with pytest.raises(ProtocolError): invoke(value=value)


def test_total_budget_and_duplicates_refuse_no_truncation():
    with pytest.raises(ProtocolError): invoke(budget=3)
    value=payload();value['namespaces']=[member(),member()]
    with pytest.raises(ProtocolError): invoke(value=value)


def test_foreign_root_or_conflicting_version_cannot_be_an_exact_dependency_snapshot():
    value=payload();value['results']=[member('other.root')]
    with pytest.raises(ProtocolError): invoke(value=value)
    value=payload();value['namespaces'][-1]['resourceVersion']={'major':2}
    with pytest.raises(ProtocolError): invoke(value=value)


def test_reflected_configured_credential_is_not_membership():
    value=payload();value['results'][0]['metadata']['label']='issued-secret'
    with pytest.raises(ProtocolError) as error: invoke(value=value)
    assert 'issued-secret' not in str(error.value)


def test_invalid_multi_root_or_class_refuses_before_network():
    def offline(request): raise AssertionError('No request authorized')
    with Client('https://runtime.test',http_transport=httpx.MockTransport(offline)) as api:
        inspector=DependencyInspector(api)
        with pytest.raises(InvalidRequestError): inspector.inspect('a,b')
        with pytest.raises(UnsupportedOperationError): inspector.inspect('a',knowledge_class='UNKNOWN')


@pytest.mark.parametrize('change',[{'results':None},{'results':[1]},{'services':[]},
    {'services':{'provider':1}},{'services':{'':'https://provider.test'}},
    {'namespaces':{}},{'namespaces':[1]},{'notifications':None}])
def test_malformed_group_envelopes_refuse(change):
    with pytest.raises(ProtocolError): invoke(value={**payload(),**change})


def test_scope_change_refuses_before_any_network():
    def offline(request): raise AssertionError('No HTTP after scope drift')
    with Client('https://runtime.test',http_transport=httpx.MockTransport(offline)) as api:
        inspector=DependencyInspector(api,scope='original')
        inspector.scope='different'
        with pytest.raises(ProtocolError): inspector.inspect('synthetic.root')


@pytest.mark.parametrize('native_async',[False,True])
def test_resources_identity_drift_discards_snapshot_without_retry(native_async):
    calls=[];capabilities=0
    def handler(request):
        nonlocal capabilities
        calls.append(request)
        if request.url.path.endswith('/capabilities'):
            capabilities+=1
            return httpx.Response(200,json={'serviceId':'resources-id' if capabilities==1 else 'restarted-id'})
        return httpx.Response(200,json=payload())
    async def run():
        async with AsyncClient('https://runtime.test',resources=Endpoint('https://resources.test'),http_transport=httpx.MockTransport(handler)) as api:
            with pytest.raises(ProtocolError):
                await AsyncDependencyInspector(api).inspect('synthetic.root',knowledge_class='NAMESPACE')
    if native_async: asyncio.run(run())
    else:
        with Client('https://runtime.test',resources=Endpoint('https://resources.test'),http_transport=httpx.MockTransport(handler)) as api:
            with pytest.raises(ProtocolError): DependencyInspector(api).inspect('synthetic.root',knowledge_class='NAMESPACE')
    assert len(calls)==3


def test_selected_resources_credentials_do_not_implicitly_rebind_between_inspections():
    calls=[]
    def handler(request):
        calls.append(request)
        return httpx.Response(200,json={'serviceId':'resources-id'} if request.url.path.endswith('/capabilities') else payload())
    with Client('https://runtime.test',resources=Endpoint('https://resources.test'),http_transport=httpx.MockTransport(handler)) as api:
        inspector=DependencyInspector(api)
        inspector.inspect('synthetic.root',knowledge_class='NAMESPACE')
        api.transport.endpoints['resources']=Endpoint('https://untrusted.test','different-secret')
        with pytest.raises(ProtocolError): inspector.inspect('synthetic.root',knowledge_class='NAMESPACE')
    assert len(calls)==3 and all(r.url.host=='resources.test' for r in calls)


def test_credential_named_metadata_is_not_exported_as_permission():
    value=payload()
    for group in ('results','namespaces'):
        for row in value[group]: row['metadata']['accessKey']='unknown-key-never-export'
    result,_=invoke(value=value)
    assert 'accessKey' not in result.results[0].metadata
    assert 'permission lease' in result.results[0].access


def test_nullable_native_descriptor_fields_preserve_unknown_not_default_authority():
    row=member();row.update(timestamp=None,optional=None,projectUrn=None,resourceVersion=None)
    result,_=invoke(value={'results':[row]})
    assert result.results[0].version is None and result.results[0].timestamp is None
    assert result.results[0].optional is None and result.results[0].project_urn is None


def test_native_root_group_descriptor_can_omit_timestamp_without_forging_version():
    value=payload();value['namespaces'][-1].update(timestamp=0,metadata={})
    result,_=invoke(value=value)
    assert result.results[0].timestamp==42 and result.groups['namespaces'][-1].timestamp==0
    assert result.results[0].version==result.groups['namespaces'][-1].version
