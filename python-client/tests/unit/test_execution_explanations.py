"""Independent public execution-explanation wire probes, no simulator imports."""
import asyncio,hashlib,json
from copy import deepcopy

import httpx,pytest

from klab_client.dto import observation_from_wire
from klab_client import Endpoint
from klab_client.errors import InvalidRequestError,ProtocolError,UnsupportedOperationError
from test_selections import client,root,wire
import klab_client.execution_explanations as draft


def receipt():
    value={'protocol':draft.PROTOCOL,'kind':draft.KIND,'contextId':'s.c','runtimeId':'runtime-id',
        'observationId':1,'epoch':'a'*32,'revision':0,'complete':True,
        'inputs':[{'urn':'example:input','providerId':'resources-id','version':'1.0.0',
            'sourceSha256':'1'*64,'binarySha256':'2'*64}],
        'selections':[{'id':'selection:1','modelUrn':'example.model','modelVersion':'1.0.0','modelSourceSha256':'3'*64,
            'providerId':'resources-id','componentProviderId':'runtime-id','componentId':'example.component','componentVersion':'1.2.3',
            'prototype':'example.recorded','inputUrns':['example:input']}],
        'planned':[{'id':'plan:1','selectionId':'selection:1','targetObservationId':1,'effect':'CREATE'}],
        'recorded':[{'id':'activity:1','plannedId':'plan:1','targetObservationId':1,'outcome':'SUCCESS','effect':'CREATE'}],
        'semanticUpdates':[]}
    value['digest']=hashlib.sha256(json.dumps(value,sort_keys=True,ensure_ascii=False,allow_nan=False,separators=(',',':')).encode()).hexdigest()
    return value


def invoke(value=None,*,native_async=False,advertised=True,runtime_id='runtime-id'):
    def handler(request):
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json={'serviceId':runtime_id,**({'executionExplanationProtocol':draft.PROTOCOL} if advertised else {})})
        assert request.method=='GET' and request.url.path==draft.PREFIX+'1'
        return httpx.Response(200,content=json.dumps(receipt() if value is None else value).encode(),headers={'Content-Type':'application/json'})
    async def run():
        async with client(handler,True) as api:
            context=root(api)
            return await draft.AsyncExecutionExplanations(context,allow_experimental=True).inspect(observation_from_wire(wire(),context))
    if native_async:return asyncio.run(run())
    with client(handler) as api:
        context=root(api)
        return draft.ExecutionExplanations(context,allow_experimental=True).inspect(observation_from_wire(wire(),context))


@pytest.mark.parametrize('native_async',[False,True])
def test_planned_and_recorded_source_component_pins_remain_distinct(native_async):
    value=invoke(native_async=native_async)
    assert value.inputs[0]['sourceSha256']!=value.inputs[0]['binarySha256']
    assert value.selections[0]['componentVersion']=='1.2.3'
    assert value.planned[0]['effect']=='CREATE' and value.recorded[0]['outcome']=='SUCCESS'
    assert value.evidence==draft.KIND
    with pytest.raises(TypeError):value.inputs[0]['version']='other'


@pytest.mark.parametrize('field,bad',[('contextId','foreign'),('complete',False),('digest','0'*64),
    ('inputs',[]),('selections',[]),('planned',[]),('recorded',[{}]),('semanticUpdates',[{}])])
def test_incomplete_or_unbound_explanation_never_becomes_scientific_lineage(field,bad):
    value=receipt();value[field]=bad
    with pytest.raises(ProtocolError):invoke(value)


def test_no_reference_capability_does_not_fallback_to_native_provenance():
    with pytest.raises(UnsupportedOperationError):invoke(advertised=False)


def seal(value):
    body={key:item for key,item in value.items() if key!='digest'}
    value['digest']=hashlib.sha256(json.dumps(body,sort_keys=True,ensure_ascii=False,allow_nan=False,separators=(',',':')).encode()).hexdigest()
    return value


def semantic_receipt():
    value=receipt();value['revision']=1
    value['planned'].append({'id':'plan:update','selectionId':'selection:1','targetObservationId':1,'effect':'SEMANTIC_UPDATE'})
    value['recorded'].append({'id':'effect:update','plannedId':'plan:update','targetObservationId':1,'outcome':'SUCCESS','effect':'SEMANTIC_UPDATE'})
    value['semanticUpdates']=[{'recordedId':'effect:update','previousRevision':0,'revision':1,
        'beforeObservable':'geography:Elevation in m','afterObservable':'geography:Elevation in cm'}]
    return seal(value)


@pytest.mark.parametrize('native_async',[False,True])
def test_explicit_recorded_semantic_changes_pin_before_after_revisions(native_async):
    value=invoke(semantic_receipt(),native_async=native_async)
    assert value.semantic_updates[0]['previousRevision']==0 and value.semantic_updates[0]['revision']==1
    assert len(value.planned)==2 and len(value.recorded)==2


@pytest.mark.parametrize('group,field,bad',[
    ('inputs','urn',''),('inputs','version',None),('inputs','providerId',None),('inputs','binarySha256','bad'),('inputs','sourceSha256','bad'),
    ('inputs','version','*'),('selections','modelVersion','latest'),('selections','componentVersion','1.*'),
    ('selections','modelVersion',''),('selections','modelSourceSha256','bad'),('selections','componentVersion',''),
    ('selections','inputUrns',['foreign']),('selections','inputUrns',['example:input','example:input']),
    ('planned','selectionId','missing'),('planned','targetObservationId',True),('planned','effect','NATIVE'),
    ('recorded','plannedId','missing'),('recorded','targetObservationId',2),('recorded','outcome','PENDING'),('recorded','effect','SEMANTIC_UPDATE'),
    ('semanticUpdates','recordedId','missing'),('semanticUpdates','previousRevision',True),('semanticUpdates','revision',3),
    ('semanticUpdates','afterObservable','')])
def test_hash_correct_but_unbound_source_selection_or_effect_is_refused(group,field,bad):
    value=semantic_receipt();value[group][0][field]=bad
    with pytest.raises(ProtocolError):invoke(seal(value))


@pytest.mark.parametrize('group',['inputs','selections','planned','recorded','semanticUpdates'])
@pytest.mark.parametrize('defect',['type','budget','duplicate'])
def test_group_shapes_budgets_and_duplicate_members_refuse(group,defect):
    value=semantic_receipt()
    if defect=='type':value[group]={}
    elif defect=='budget':value[group]*=129
    else:value[group].append(deepcopy(value[group][0]))
    with pytest.raises(ProtocolError):invoke(seal(value))


def test_semantic_record_completeness_and_planned_not_recorded_execution():
    value=semantic_receipt();value['semanticUpdates']=[]
    with pytest.raises(ProtocolError):invoke(seal(value))
    value=semantic_receipt();value['recorded'][-1]['outcome']='FAILURE'
    with pytest.raises(ProtocolError):invoke(seal(value))
    value=receipt();value['recorded'][0]['outcome']='FAILURE'
    result=invoke(seal(value));assert result.recorded[0]['outcome']=='FAILURE'
    value=receipt();value['revision']=1
    with pytest.raises(ProtocolError):invoke(seal(value))


def test_private_configured_credentials_are_rejected_not_echoed():
    value=receipt();value['selections'][0]['componentId']='current-secret'
    with pytest.raises(ProtocolError) as error:invoke(seal(value))
    assert 'current-secret' not in str(error.value)


def test_non_utf8_source_identity_and_wrong_runtime_capability_are_refused():
    value=receipt();value['selections'][0]['modelUrn']='\ud800'
    with pytest.raises(ProtocolError):invoke(value)
    with pytest.raises(ProtocolError):invoke(runtime_id='foreign-id')


def test_explicit_root_observation_and_matching_client_are_required_without_implicit_network():
    def offline(request):raise AssertionError('No implicit attach/query/replay')
    with client(offline) as first,client(offline) as second:
        context=root(first)
        with pytest.raises(InvalidRequestError):draft.ExecutionExplanations(context)
        with pytest.raises(InvalidRequestError):draft.AsyncExecutionExplanations(context,allow_experimental=True)
        explanations=draft.ExecutionExplanations(context,allow_experimental=True)
        with pytest.raises(InvalidRequestError):explanations.inspect(observation_from_wire(wire(),root(second)))
        context.configuration['serviceId']='drifted'
        with pytest.raises(ProtocolError):explanations.inspect(observation_from_wire(wire(),context))


@pytest.mark.parametrize('native_async',[False,True])
def test_postread_credential_drift_discards_explanation(native_async):
    box={}
    def handler(request):
        if request.url.path.endswith('/capabilities'):return httpx.Response(200,json={'serviceId':'runtime-id','executionExplanationProtocol':draft.PROTOCOL})
        box['api'].transport.endpoints['runtime']=Endpoint('https://runtime.test','changed-secret')
        return httpx.Response(200,json=receipt())
    async def run():
        async with client(handler,True) as api:
            box['api']=api;context=root(api)
            with pytest.raises(ProtocolError):await draft.AsyncExecutionExplanations(context,allow_experimental=True).inspect(observation_from_wire(wire(),context))
    if native_async:asyncio.run(run())
    else:
        with client(handler) as api:
            box['api']=api;context=root(api)
            with pytest.raises(ProtocolError):draft.ExecutionExplanations(context,allow_experimental=True).inspect(observation_from_wire(wire(),context))
