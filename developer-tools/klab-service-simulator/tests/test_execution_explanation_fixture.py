"""Supplementary finite derived source/component/effect policy; public flows separate."""
import hashlib,json
from urllib.parse import urlencode

import pytest

from klab_simulator.authoring import MODEL,NAMESPACE,RESOURCE,SOURCE
from klab_simulator.resource_imports import SCHEMA
from klab_simulator.execution_explanations import PREFIX,PROTOCOL,seal
from klab_simulator.state import State,ContractError


def fixture():
    state=State('execution-explanation');state.contexts['s.c']={'owner':'alice'}
    binary=hashlib.sha256(b'finite supplied bytes').hexdigest()
    state.imported_resources[RESOURCE]={'owner':'alice','scope':'s.c',
        'asset':{'metadata':{'delegate':{'sourceSha256':binary,'sourceBytes':21}}}}
    state.authoring.assets[('NAMESPACE',NAMESPACE)]={'owner':'alice','payload':{'sourceCode':SOURCE}}
    manifest=json.dumps({'protocol':'klab.experimental.import-source/1','resourceUrn':RESOURCE,
        'schemaId':SCHEMA,'binarySha256':binary,'binaryBytes':21},sort_keys=True,separators=(',',':'))
    model='literal selected model '+MODEL+' binds namespace '+NAMESPACE+' and resource '+RESOURCE+'\n'
    state.jobs[('resolver',1)]={'scope':'s.c','status':'FINISHED','result':{
        'referenceComponentSelection':{'componentId':'simulator.recorded-crop','componentVersion':'1.0.0',
            'componentProviderId':state.service_ids['runtime'],'prototype':'simulator.recorded400'},
        'computation':[{'computation':[{'urn':MODEL}]}],
        'requirements':{'results':[{'resourceUrn':urn,'resourceVersion':'1.0.1',
            'serviceId':state.service_ids['resources'],'sourceSha256':hashlib.sha256(source.encode()).hexdigest()}
            for urn,source in ((MODEL,model),(RESOURCE,manifest))]}}}
    row={'id':1,'urn':'s.c.1','metadata':{'delegate':{}}}
    state.observations[('s.c',1)]=row
    return state,row


def call(state,method='GET',path=PREFIX+'1',params=None,headers=None,scope='s.c'):
    return state.execution_explanations.request(method,path+'?'+urlencode({'protocol':PROTOCOL} if params is None else params,doseq=True),
        'alice',scope,{'klab-service':state.service_ids['runtime']} if headers is None else headers)[0]


def test_derived_source_bindings_and_semantic_effects_remain_immutable_and_not_engine_outputs():
    state,row=fixture();state.execution_explanations.record('s.c',row)
    initial=call(state)
    assert initial['inputs'][0]['binarySha256']!=initial['inputs'][0]['sourceSha256']
    assert initial['selections'][0]['componentProviderId']==state.service_ids['runtime']
    body=json.dumps({'contextId':'s.c','observationId':1,'expectedRevision':0,'afterObservable':'geography:Elevation in cm'}).encode()
    state.execution_explanations.semantic_update(body)
    after=call(state)
    assert initial['revision']==0 and not initial['semanticUpdates']
    assert after['revision']==1 and row['metadata']['delegate']['referenceSemanticUpdate']['revision']==1
    after['inputs'].clear();assert call(state)['inputs']
    with pytest.raises(ContractError):state.execution_explanations.semantic_update(body)
    with pytest.raises(ContractError):state.execution_explanations.semantic_update(body.replace(b': 0',b': 1'))
    state.execution_explanations.release('other');assert state.execution_explanations.rows
    state.execution_explanations.release('s.c');assert not state.execution_explanations.rows


@pytest.mark.parametrize('defect',['no-plan','pending','wrong-model','wrong-component','missing-resource','stale-source','wrong-provider'])
def test_completion_without_selected_matching_plan_never_manufactures_explanation(defect):
    state,row=fixture();plan=state.jobs[('resolver',1)]
    if defect=='no-plan':state.jobs.clear()
    if defect=='pending':plan['status']='WAITING'
    if defect=='wrong-model':plan['result']['computation'][0]['computation']=[{'urn':'foreign'}]
    if defect=='wrong-component':plan['result']['referenceComponentSelection']['componentVersion']='2.0.0'
    if defect=='missing-resource':plan['result']['requirements']['results'].pop()
    if defect=='stale-source':plan['result']['requirements']['results'][0]['sourceSha256']='0'*64
    if defect=='wrong-provider':plan['result']['requirements']['results'][0]['serviceId']='other'
    state.execution_explanations.record('s.c',row)
    assert not state.execution_explanations.rows
    with pytest.raises(ContractError):call(state)


@pytest.mark.parametrize('change',[{'method':'POST'},{'path':PREFIX+'True'},{'path':PREFIX+'0'},
    {'path':PREFIX+str(2**63)},{'path':PREFIX+'2'},{'params':{'protocol':'wrong'}},
    {'params':{'protocol':[PROTOCOL,PROTOCOL]}},{'headers':{}},{'scope':'s.c.1'}])
def test_exact_read_selectors_root_and_principal_required(change):
    state,row=fixture();state.execution_explanations.record('s.c',row)
    with pytest.raises(ContractError):call(state,**change)


@pytest.mark.parametrize('body',[b'x'*4097,b'bad',b'[]',b'{}',
    b'{"contextId":"s.c","observationId":true,"expectedRevision":0,"afterObservable":"geography:Elevation in cm"}',
    b'{"contextId":"s.c","observationId":2,"expectedRevision":0,"afterObservable":"geography:Elevation in cm"}'])
def test_semantic_fixture_control_is_bounded_and_not_a_native_update_api(body):
    state,row=fixture();state.execution_explanations.record('s.c',row)
    with pytest.raises(ContractError):state.execution_explanations.semantic_update(body)


def test_ephemeral_and_membership_byte_capacity_refuse_without_truncation():
    state,row=fixture();state.store=object()
    with pytest.raises(ContractError):call(state)
    state.store=None;state.execution_explanations.rows={('s.c',i):{} for i in range(128)}
    with pytest.raises(ContractError):state.execution_explanations.record('s.c',row)
    with pytest.raises(ContractError):seal({'payload':'x'*(1024*1024)})
