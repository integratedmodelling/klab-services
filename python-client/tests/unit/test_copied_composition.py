"""Public lifecycle controls with independent finite wire inputs, no simulator imports."""
import asyncio,hashlib,json
from dataclasses import replace
import httpx,pytest
from test_selections import client,root
from klab_client import Client,AsyncClient,Endpoint
from klab_client.errors import InvalidRequestError,ProtocolError,SubmissionOutcomeUnknown,UnsupportedOperationError
import klab_client.copied_composition as draft


def digest(values,valid):
    return hashlib.sha256(json.dumps({'values':values,'valid':valid},sort_keys=True,separators=(',',':'),allow_nan=False).encode()).hexdigest()


def source(runtime):
    values=[0.,-2.5,None,1.];valid=[True,True,False,True]
    return draft.CopiedSourceSnapshot(draft.CopiedSourceIdentity(
        {'runtime_url':'https://'+runtime+'.test/runtime','runtime_id':runtime,'context_id':'s.c','observation_id':1},
        'a'*32,'b'*32,digest(values,valid)),
        {'shape':[2,2],'crs':'EPSG:3857','transform':[1,0,0,0,-1,2],'units':'m','dtype':'float64','order':'ROW_MAJOR_NORTH_TO_SOUTH'},
        tuple(values),tuple(valid))


@pytest.mark.parametrize('native_async',[False,True])
def test_exact_public_copied_reference_compose_inspect_release(native_async):
    sources=(source('A'),source('B'));vector=tuple(item.identity for item in sources);calls=[]
    def handler(request):
        calls.append(request)
        if request.url.path.endswith('/capabilities'):return httpx.Response(200,json={'serviceId':'runtime-id','copiedCompositionProtocol':draft.PROTOCOL})
        assert request.url.host=='runtime.test'
        if request.method=='DELETE':return httpx.Response(200,json={'released':True,'source_mutations':0})
        values=[0.,-5.,None,2.];valid=[True,True,False,True]
        return httpx.Response(200,json={'protocol':draft.PROTOCOL,'result_id':'1','origin':{'runtime_id':'runtime-id','context_id':'s.c'},
            'source_vector':[item.to_wire() for item in vector],'descriptor':sources[0].descriptor,'values':values,'valid':valid,
            'revision':digest(values,valid),'revision_kind':draft.REVISION_KIND,'source_authority':draft.AUTHORITY,
            'operation':'PAIRWISE_SUM_REFERENCE','live_federation':False,'durable':False})
    async def run():
        async with client(handler,True) as api:
            compositions=draft.AsyncCopiedCompositions(root(api),allow_experimental=True)
            result=await compositions.compose(sources,expected_vector=vector)
            assert result.values==(0.,-5.,None,2.) and result.source_vector==vector
            assert await compositions.inspect(result)==result
            assert await compositions.release(result) is True
    if native_async:asyncio.run(run())
    else:
        with client(handler) as api:
            compositions=draft.CopiedCompositions(root(api),allow_experimental=True)
            result=compositions.compose(sources,expected_vector=vector)
            assert result.values==(0.,-5.,None,2.) and result.source_vector==vector
            assert compositions.inspect(result)==result and compositions.release(result) is True
    assert [r.method for r in calls if not r.url.path.endswith('/capabilities')]==['POST','GET','DELETE']


def test_duplicate_source_origins_and_stale_vector_refuse_without_mutation():
    def offline(request):raise AssertionError('No C write for unpinned or duplicate sources')
    with client(offline) as api:
        compositions=draft.CopiedCompositions(root(api),allow_experimental=True)
        a,b=source('A'),source('B')
        for sources,vector in (((a,a),(a.identity,a.identity)),((a,b),(b.identity,a.identity)),((a,b),None)):
            with pytest.raises(InvalidRequestError):compositions.compose(sources,expected_vector=vector)


def output(sources):
    values=[0.,-5.,None,2.];valid=[True,True,False,True]
    return {'protocol':draft.PROTOCOL,'result_id':'1','origin':{'runtime_id':'runtime-id','context_id':'s.c'},
        'source_vector':[item.identity.to_wire() for item in sources],'descriptor':sources[0].to_wire()['descriptor'],
        'values':values,'valid':valid,'revision':digest(values,valid),'revision_kind':draft.REVISION_KIND,
        'source_authority':draft.AUTHORITY,'operation':'PAIRWISE_SUM_REFERENCE','live_federation':False,'durable':False}


def invoke(*,native_async=False,operation='compose',change=lambda value:None,hook=lambda box,request:None,
        capability=None,release_value=None):
    sources=(source('A'),source('B'));value=output(sources);change(value);box={};calls=[]
    def handler(request):
        calls.append(request)
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json=capability or {'serviceId':'runtime-id','copiedCompositionProtocol':draft.PROTOCOL})
        hook(box,request)
        return httpx.Response(200,json=(release_value if release_value is not None else {'released':True,'source_mutations':0}) if request.method=='DELETE' else value)
    def local():
        return draft.CopiedCompositionResult('1','runtime-id','s.c',tuple(item.identity for item in sources),
            sources[0].descriptor,(0.,-5.,None,2.),(True,True,False,True),digest([0.,-5.,None,2.],[True,True,False,True]))
    async def run():
        async with client(handler,True) as api:
            facade=draft.AsyncCopiedCompositions(root(api),allow_experimental=True);box['api']=api
            if operation=='compose':return await facade.compose(sources,expected_vector=tuple(item.identity for item in sources))
            if operation=='inspect':return await facade.inspect(local())
            return await facade.release(local())
    if native_async:return asyncio.run(run()),calls
    with client(handler) as api:
        facade=draft.CopiedCompositions(root(api),allow_experimental=True);box['api']=api
        if operation=='compose':result=facade.compose(sources,expected_vector=tuple(item.identity for item in sources))
        elif operation=='inspect':result=facade.inspect(local())
        else:result=facade.release(local())
        return result,calls


@pytest.mark.parametrize('native_async',[False,True])
@pytest.mark.parametrize('field,bad',[('origin',{}),('result_id','other'),('source_vector',[]),('source_authority','NATIVE_GRANT'),
    ('live_federation',True),('durable',True),('revision','0'*64),('descriptor',{}),('values',[])])
def test_incomplete_mutation_ack_is_unknown_and_inspect_refuses_foreign_results(native_async,field,bad):
    def change(value):value[field]=bad
    with pytest.raises(SubmissionOutcomeUnknown):invoke(native_async=native_async,change=change)
    with pytest.raises(ProtocolError):invoke(native_async=native_async,operation='inspect',change=change)


@pytest.mark.parametrize('native_async',[False,True])
def test_unadvertised_reference_wrong_runtime_and_binding_drift_never_rebind(native_async):
    for capability in ({'serviceId':'runtime-id'},{'serviceId':'foreign','copiedCompositionProtocol':draft.PROTOCOL}):
        with pytest.raises((ProtocolError,UnsupportedOperationError)):invoke(native_async=native_async,capability=capability)
    def drift(box,request):box['api'].transport.endpoints['runtime']=Endpoint('https://runtime.test','changed-secret')
    with pytest.raises(SubmissionOutcomeUnknown):invoke(native_async=native_async,hook=drift)
    with pytest.raises(ProtocolError):invoke(native_async=native_async,operation='inspect',hook=drift)
    with pytest.raises(SubmissionOutcomeUnknown):invoke(native_async=native_async,operation='release',hook=drift)


@pytest.mark.parametrize('native_async',[False,True])
@pytest.mark.parametrize('value',[{},True,{'released':1,'source_mutations':0},{'released':True,'source_mutations':False},
    {'released':True,'source_mutations':1}])
def test_release_ack_never_claims_source_mutation_or_assumed_rollback(native_async,value):
    with pytest.raises(SubmissionOutcomeUnknown):invoke(native_async=native_async,operation='release',release_value=value)


@pytest.mark.parametrize('field,bad',[('revision_kind','native'),('epoch','bad'),('plan_id','bad'),('revision','0'*64),
    ('origin',{}),('descriptor',{}),('values',[True,-2.5,None,1.]),('valid',[1,True,False,True])])
def test_invalid_source_snapshots_refuse_without_a_c_write(field,bad):
    value=source('A').to_wire();value[field]=bad
    with pytest.raises(ProtocolError):draft._snapshot(value)


@pytest.mark.parametrize('url',['https://secret@source.test','https://source.test?token=secret','http://source.test:bad','not-a-url'])
def test_copied_origin_never_encodes_delegated_authority_or_invalid_url(url):
    value=source('A').to_wire();value['origin']['runtime_url']=url
    with pytest.raises(ProtocolError):draft._snapshot(value)


def test_input_vector_snapshot_receipt_budgets_and_source_credentials_are_not_grants():
    def offline(request):raise AssertionError('No implicit federation request')
    with client(offline) as api:
        context=root(api);facade=draft.CopiedCompositions(context,allow_experimental=True)
        a,b=source('A'),source('B');vector=(a.identity,b.identity)
        with pytest.raises(InvalidRequestError):draft.CopiedCompositions(context)
        for sources in (None,(),(a,object()),(replace(a,identity=None),b),(replace(a,values=[]),b)):
            with pytest.raises(InvalidRequestError):facade.compose(sources,expected_vector=vector)
        descriptor={**a.to_wire()['descriptor'],'units':'cm'}
        with pytest.raises(InvalidRequestError):facade.compose((a,replace(b,descriptor=descriptor)),expected_vector=vector)
        own=source('runtime-id')
        with pytest.raises(InvalidRequestError):facade.compose((a,own),expected_vector=(a.identity,own.identity))
        for value in (None,object(),draft.CopiedCompositionResult('1','foreign','s.c',vector,a.descriptor,(),(),'0'*64)):
            with pytest.raises(InvalidRequestError):facade.inspect(value)
        bad=replace(a,descriptor={**a.to_wire()['descriptor'],'units':'current-secret'})
        with pytest.raises(ProtocolError):facade.compose((bad,b),expected_vector=vector)
        context.configuration['serviceId']='other'
        with pytest.raises(ProtocolError):facade.inspect(None)


def test_aggregate_upload_budget_never_truncates_sources_or_sends_a_partial_composition():
    def offline(request):raise AssertionError('No C request over aggregate upload budget')
    values=tuple([1.2345678901234567e307]*4096);valid=tuple([True]*4096)
    descriptor={**source('A').descriptor,'shape':[64,64],'crs':'x'*4096}
    def large(label):
        identity=draft.CopiedSourceIdentity({'runtime_url':'https://source.test/'+'x'*4000,
            'runtime_id':label*4096,'context_id':'s.c','observation_id':1},'a'*32,'b'*32,digest(list(values),list(valid)))
        return draft.CopiedSourceSnapshot(identity,descriptor,values,valid)
    sources=(large('A'),large('B'))
    with client(offline) as api:
        with pytest.raises(InvalidRequestError):draft.CopiedCompositions(root(api),allow_experimental=True).compose(
            sources,expected_vector=tuple(item.identity for item in sources))


def test_wrong_but_valid_source_vector_in_server_result_is_not_a_grant():
    def change(value):value['source_vector'][0]['revision']='f'*64
    with pytest.raises(SubmissionOutcomeUnknown):invoke(change=change)
    with pytest.raises(ProtocolError):invoke(operation='inspect',change=change)


def test_custom_snapshot_wire_cannot_bypass_exact_copied_source_envelope():
    class InvalidSnapshot(draft.CopiedSourceSnapshot):
        def to_wire(self):return {}
    a,b=source('A'),source('B')
    bad=InvalidSnapshot(a.identity,a.descriptor,a.values,a.valid)
    def offline(request):raise AssertionError('No C write for invalid source envelope')
    with client(offline) as api:
        with pytest.raises(ProtocolError):draft.CopiedCompositions(root(api),allow_experimental=True).compose(
            (bad,b),expected_vector=(a.identity,b.identity))


@pytest.mark.parametrize('native_async',[False,True])
def test_mutable_caller_vector_after_post_does_not_replace_request_time_source_pins(native_async):
    sources=(source('A'),source('B'));wire=output(sources)
    def handler(request):
        if request.url.path.endswith('/capabilities'):return httpx.Response(200,json={'serviceId':'runtime-id','copiedCompositionProtocol':draft.PROTOCOL})
        assert json.loads(request.content)['expected_vector'][0]['origin']['runtime_id']=='A'
        sources[0].identity.origin['runtime_id']='changed-after-write'
        return httpx.Response(200,json=wire)
    async def run():
        async with client(handler,True) as api:
            return await draft.AsyncCopiedCompositions(root(api),allow_experimental=True).compose(
                sources,expected_vector=tuple(item.identity for item in sources))
    if native_async:result=asyncio.run(run())
    else:
        with client(handler) as api:
            result=draft.CopiedCompositions(root(api),allow_experimental=True).compose(sources,expected_vector=tuple(item.identity for item in sources))
    assert result.source_vector[0].origin['runtime_id']=='A'


@pytest.mark.parametrize('native_async',[False,True])
@pytest.mark.parametrize('defect',[None,'secret','missing-shard'])
def test_capture_uses_only_independent_source_reads_and_releases_its_read_grant(native_async,defect):
    from test_read_plans import manifest,bound,client_options
    value=manifest();values=[0.,-2.5,None,7.];valid=[True,True,False,True]
    value['revision']=digest(values,valid);value['shards']=[{'index':0,'start':0,'count':4,'sha256':digest(values,valid)}]
    if defect=='secret':value['units']='synthetic'
    calls=[]
    def handler(request):
        calls.append(request)
        if request.method=='POST':return httpx.Response(200,json=value)
        if request.method=='DELETE':return httpx.Response(200,json={'protocol':value['protocol'],'released':True,'plan_id':value['plan_id'],'epoch':value['epoch']})
        if defect=='missing-shard':return httpx.Response(200,json={})
        return httpx.Response(200,json={'protocol':value['protocol'],'plan_id':value['plan_id'],'epoch':value['epoch'],
            'revision':value['revision'],'index':0,'start':0,'values':values,'valid':valid,'sha256':digest(values,valid)})
    async def run():
        async with AsyncClient('https://example.test/runtime','synthetic',http_transport=httpx.MockTransport(handler),**client_options()) as api:
            _,observation=bound(api)
            return await draft.async_capture_copied_source(observation,allow_experimental=True)
    def sync():
        with Client('https://example.test/runtime','synthetic',http_transport=httpx.MockTransport(handler),**client_options()) as api:
            _,observation=bound(api)
            return draft.capture_copied_source(observation,allow_experimental=True)
    if defect:
        with pytest.raises(ProtocolError):asyncio.run(run()) if native_async else sync()
    else:
        result=asyncio.run(run()) if native_async else sync()
        assert result.values==tuple(values) and result.valid==tuple(valid)
    assert calls[-1].method=='DELETE' and all(request.url.host=='example.test' for request in calls)
