"""Independent selected-window wire controls, no simulator import or scientific slicing."""
import asyncio
from copy import deepcopy
from dataclasses import replace

import httpx,pytest
from klab_client.dto import observation_from_wire
from klab_client.errors import InvalidRequestError,ProtocolError,SubmissionOutcomeUnknown
from test_selections import client,root,wire
from test_read_plans import manifest,block
import klab_client.reference_windows as draft


def state():
    return {'id':'baseline','label':'Declared baseline','instant':'2020-01-01T00:00:00Z','runtimeId':'runtime-id',
        'contextId':'s.c','observationId':1,'epoch':'b'*32,'sourceRevision':manifest()['revision'],
        'shape':[2,2],'crs':'EPSG:3857','transform':[100,0,0,0,-100,200],'units':'m'}


def opened():
    plan=manifest();plan['runtime_id']='runtime-id'
    return {'protocol':draft.PROTOCOL,'kind':draft.KIND,'state':state(),
        'window':{'rowStart':0,'rowStop':2,'columnStart':0,'columnStop':2},'plan':plan}


def invoke(*,native_async=False,change=lambda row:None,operation='lifecycle',bad_stage=None,drift_stage=None):
    payload=opened();change(payload);calls=[];box={}
    def handler(request):
        calls.append(request)
        if ((drift_stage=='catalog' and request.url.path.endswith('/states'))
                or drift_stage=='open' and request.method=='POST'):
            from klab_client import Endpoint
            box['api'].transport.endpoints['runtime']=Endpoint('https://runtime.test','changed-secret')
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json={'serviceId':'runtime-id','readPlanSelectionProtocol':draft.PROTOCOL})
        if request.url.path.endswith('/states'):
            if bad_stage=='catalog':return httpx.Response(200,content=b'invalid')
            return httpx.Response(200,json={'protocol':draft.PROTOCOL,'kind':draft.KIND,'states':[state()]})
        if request.method=='POST' and bad_stage=='open':return httpx.Response(200,content=b'invalid')
        if request.method=='POST':return httpx.Response(200,json=payload)
        if request.method=='DELETE':return httpx.Response(200,json={} if bad_stage=='release' else {'protocol':manifest()['protocol'],'released':True,'plan_id':'a'*32,'epoch':'b'*32})
        if bad_stage=='shard':return httpx.Response(200,json={})
        return httpx.Response(200,json=block(int(request.url.path.rsplit('/',1)[1])))
    async def run():
        async with client(handler,True) as api:
            box['api']=api
            context=root(api);facade=draft.AsyncReadPlanSelections(context,allow_experimental=True)
            observation=observation_from_wire(wire(),context);states=await facade.states(observation)
            if operation=='capture':return await facade.capture(observation,draft.ReferenceWindow(0,2,0,2),states[0],shard_cells=2)
            selected=await facade.open(observation,draft.ReferenceWindow(0,2,0,2),states[0],shard_cells=2)
            if operation=='open':return selected
            try:return await facade.read(selected)
            finally:assert await facade.release(selected)
    if native_async:return asyncio.run(run()),calls
    with client(handler) as api:
        box['api']=api
        context=root(api);facade=draft.ReadPlanSelections(context,allow_experimental=True)
        observation=observation_from_wire(wire(),context);states=facade.states(observation)
        if operation=='capture':return facade.capture(observation,draft.ReferenceWindow(0,2,0,2),states[0],shard_cells=2),calls
        selected=facade.open(observation,draft.ReferenceWindow(0,2,0,2),states[0],shard_cells=2)
        if operation=='open':return selected,calls
        try:return facade.read(selected),calls
        finally:assert facade.release(selected)


@pytest.mark.parametrize('native_async',[False,True])
def test_selected_grant_preserves_source_time_window_and_immutable_data(native_async):
    result,calls=invoke(native_async=native_async)
    assert result.data.values==(0.,-2.5,None,7.) and result.data.valid==(True,True,False,True)
    assert result.selection.temporal_state.instant=='2020-01-01T00:00:00Z'
    assert result.selection.window==draft.ReferenceWindow(0,2,0,2)
    assert result.evidence==draft.KIND
    assert len([request for request in calls if request.method=='POST'])==1


@pytest.mark.parametrize('field,bad',[('protocol','other'),('kind','NATIVE'),('state',{}),('window',{}),('plan',{})])
@pytest.mark.parametrize('native_async',[False,True])
def test_malformed_open_ack_is_unknown_not_replanned_or_assumed_rollback(field,bad,native_async):
    def change(row):row[field]=bad
    with pytest.raises(SubmissionOutcomeUnknown):invoke(native_async=native_async,change=change,operation='open')


@pytest.mark.parametrize('field,bad',[('shape',[1,4]),('crs','EPSG:4326'),('units','cm'),('transform',[100,0,100,0,-100,200])])
def test_valid_read_plan_descriptor_still_must_match_exact_selected_layout(field,bad):
    def change(row):row['plan'][field]=bad
    with pytest.raises(SubmissionOutcomeUnknown):invoke(change=change,operation='open')


@pytest.mark.parametrize('field,bad',[('runtimeId','foreign'),('contextId','s.foreign'),('observationId',True),('epoch','bad'),
    ('sourceRevision',None),('id','../route'),('shape',[True,2]),('crs',''),('units',[]),
    ('transform',[100,1,0,0,-100,200]),('instant','yesterday'),('instant','2020-1-01T00:00:00Z')])
def test_source_state_identity_and_time_layout_metadata_refuse(field,bad):
    def offline(request):raise AssertionError('No implicit state lookup')
    with client(offline) as api:
        facade=draft.ReadPlanSelections(root(api),allow_experimental=True)
        value=state();value[field]=bad
        with pytest.raises(ProtocolError):facade._state(value,1)


@pytest.mark.parametrize('window',[draft.ReferenceWindow(True,2,0,2),draft.ReferenceWindow(0,0,0,2),
    draft.ReferenceWindow(-1,2,0,2),draft.ReferenceWindow(0,4097,0,2),draft.ReferenceWindow(0,100,0,100)])
def test_noninteger_empty_or_oversize_windows_refuse_locally(window):
    with pytest.raises(InvalidRequestError):window.to_wire()


@pytest.mark.parametrize('native_async',[False,True])
def test_decode_and_release_uncertainty_never_replays(native_async):
    with pytest.raises(ProtocolError):invoke(native_async=native_async,bad_stage='catalog')
    with pytest.raises(SubmissionOutcomeUnknown):invoke(native_async=native_async,bad_stage='open')
    with pytest.raises(SubmissionOutcomeUnknown):invoke(native_async=native_async,bad_stage='release')
    result,calls=invoke(native_async=native_async,operation='capture')
    assert result.data.values==(0.,-2.5,None,7.)
    assert len([request for request in calls if request.method=='DELETE'])==1
    with pytest.raises(ProtocolError):invoke(native_async=native_async,operation='capture',bad_stage='shard')


@pytest.mark.parametrize('native_async',[False,True])
def test_request_time_credential_drift_is_not_success_or_replay(native_async):
    with pytest.raises(ProtocolError):invoke(native_async=native_async,drift_stage='catalog')
    with pytest.raises(SubmissionOutcomeUnknown):invoke(native_async=native_async,drift_stage='open')


@pytest.mark.parametrize('native_async',[False,True])
@pytest.mark.parametrize('defect',['state-observation','window-offset'])
def test_boolean_wire_ack_cannot_alias_integer_source_or_window(native_async,defect):
    def alter(value):
        if defect=='state-observation':value['state']['observationId']=True
        else:value['window']['rowStart']=False
    with pytest.raises(SubmissionOutcomeUnknown):invoke(native_async=native_async,change=alter,operation='open')


def test_local_fences_catalog_and_manual_dtos_fail_before_network():
    from types import SimpleNamespace
    from klab_client import Endpoint
    from klab_client.errors import UnsupportedOperationError
    def offline(request):raise AssertionError('Invalid local input must not reach transport')
    with client(offline) as api:
        context=root(api);facade=draft.ReadPlanSelections(context,allow_experimental=True)
        observation=observation_from_wire(wire(),context);source=facade._observation(observation)
        temporal=facade._state(state(),1)
        with pytest.raises(InvalidRequestError):draft.ReadPlanSelections(context)
        with pytest.raises(ProtocolError):facade._capability(SimpleNamespace(service_id='foreign',raw={}))
        with pytest.raises(UnsupportedOperationError):facade._capability(SimpleNamespace(service_id='runtime-id',raw={}))
        with pytest.raises(ProtocolError):facade._safe({'label':'current-secret'})
        for catalog in ({},{'protocol':draft.PROTOCOL,'kind':draft.KIND,'states':[]},
                {'protocol':draft.PROTOCOL,'kind':draft.KIND,'states':[state(),state()]},
                {'protocol':draft.PROTOCOL,'kind':draft.KIND,'states':[state(),{**state(),'id':'other','epoch':'c'*32}]}):
            with pytest.raises(ProtocolError):facade._catalog(catalog,source)
        for window,state_value,shard,lease,maximum in ((None,temporal,2,30,4),
                (draft.ReferenceWindow(0,2,0,2),None,2,30,4),
                (draft.ReferenceWindow(0,3,0,2),temporal,2,30,6),
                (draft.ReferenceWindow(0,2,0,2),temporal,True,30,4),
                (draft.ReferenceWindow(0,2,0,2),temporal,2,0,4),
                (draft.ReferenceWindow(0,2,0,2),temporal,2,30,3)):
            with pytest.raises(InvalidRequestError):facade._selection_body(observation,window,state_value,shard,lease,maximum)
        selected=facade._opened(opened(),source,temporal,{'window':opened()['window'],'shardCells':2,'maxCells':4})
        for value in (None,replace(selected,plan=None),replace(selected,window=None),
                replace(selected,plan=replace(selected.plan,shards=())),
                replace(selected,plan=replace(selected.plan,plan_id='invalid'))):
            with pytest.raises(InvalidRequestError):facade._selected(value)
        foreign=observation_from_wire({**wire(),'id':2},root(api))
        foreign._context.configuration['id']='s.other'
        with pytest.raises(InvalidRequestError):facade._observation(foreign)
        api.transport.endpoints['runtime']=Endpoint('https://runtime.test','new-secret')
        with pytest.raises(ProtocolError):facade._fence()
    with client(offline) as api:
        context=root(api);facade=draft.ReadPlanSelections(context,allow_experimental=True)
        context.configuration['serviceId']='foreign'
        with pytest.raises(ProtocolError):facade._fence()
    with client(offline) as api,client(offline) as other:
        facade=draft.ReadPlanSelections(root(api),allow_experimental=True)
        foreign=observation_from_wire(wire(),root(other))
        with pytest.raises(InvalidRequestError):facade._observation(foreign)


@pytest.mark.parametrize('cancel_stage',['open','shard'])
def test_cancelled_capture_never_replays_and_only_releases_established_grant(cancel_stage):
    async def run():
        entered=asyncio.Event();calls=[]
        async def handler(request):
            calls.append(request)
            if request.url.path.endswith('/capabilities'):
                return httpx.Response(200,json={'serviceId':'runtime-id','readPlanSelectionProtocol':draft.PROTOCOL})
            if request.method=='DELETE':return httpx.Response(200,json={'protocol':manifest()['protocol'],
                'released':True,'plan_id':'a'*32,'epoch':'b'*32})
            if request.method=='POST' and cancel_stage!='open':return httpx.Response(200,json=opened())
            entered.set();await asyncio.Event().wait()
        async with client(handler,True) as api:
            facade=draft.AsyncReadPlanSelections(root(api),allow_experimental=True)
            observation=observation_from_wire(wire(),facade.context)
            temporal=facade._state(state(),1)
            task=asyncio.create_task(facade.capture(observation,draft.ReferenceWindow(0,2,0,2),temporal,shard_cells=2))
            await entered.wait();task.cancel()
            with pytest.raises(asyncio.CancelledError):await task
            assert len([request for request in calls if request.method=='POST'])==1
            assert len([request for request in calls if request.method=='DELETE'])==(cancel_stage=='shard')
    asyncio.run(run())
