"""Supplementary server-owned conditions; public SDK HTTP workflows are separate."""
import hashlib

import pytest

from klab_simulator.accepted_documents import NAMES,PREFIX,PROTOCOL
from klab_simulator.state import ContractError,State


def fixture():
    state=State('accepted-documents');state.contexts['s.c']={'owner':'alice'}
    return state


def source(name=NAMES[0]): return f'namespace {name};\n'


def body(text=None,expected=0):
    text=source() if text is None else text
    return {'protocol':PROTOCOL,'expectedRevision':expected,'source':text,'sourceSha256':hashlib.sha256(text.encode()).hexdigest()}


def call(state,*,method='GET',route='knowledge',query=None,principal='alice',scope='s.c',headers=None,data=None):
    suffix=('?protocol='+PROTOCOL if method=='GET' else '') if query is None else query
    return state.accepted_sources.request(method,PREFIX+route+suffix,principal,scope,
        {'klab-service':state.service_ids['runtime']} if headers is None else headers,data)


def update(state,**kwargs): return call(state,method='PUT',route='documents/'+NAMES[0],data=body(**kwargs))[0]


def condition(revision=1,knowledge=1):
    return {'protocol':PROTOCOL,'expectedRevision':revision,'expectedKnowledgeRevision':knowledge}


def test_exact_workflow_source_and_knowledge_conditions_are_server_owned_and_immutable():
    state=fixture();name=NAMES[0]
    assert call(state)[0]['revision']==1
    row=update(state);row['source']='changed'
    assert call(state,route='documents/'+name)[0]['source']==source()
    for action in ('validate','publish'):
        with pytest.raises(ContractError): call(state,method='POST',route='documents/'+name+'/'+action,data=condition(revision=0))
    with pytest.raises(ContractError): call(state,method='POST',route='documents/'+name+'/publish',data=condition())
    validated=call(state,method='POST',route='documents/'+name+'/validate',data=condition())[0]
    assert validated['validation']['status']=='VALID'
    state.worldview_id='changed-worldview'
    with pytest.raises(ContractError): call(state,method='POST',route='documents/'+name+'/publish',data=condition())
    call(state,method='POST',route='documents/'+name+'/validate',data=condition())
    published=call(state,method='POST',route='documents/'+name+'/publish',data=condition())[0]
    published['source']='changed'
    assert call(state,route='publications/'+name)[0]['source']==source()
    with pytest.raises(ContractError): call(state,method='POST',route='documents/'+name+'/publish',data=condition())
    update(state,text='invalid',expected=1)
    invalid=call(state,method='POST',route='documents/'+name+'/validate',data=condition(revision=2))[0]
    assert invalid['validation']['status']=='INVALID'
    with pytest.raises(ContractError): call(state,method='POST',route='documents/'+name+'/publish',data=condition(revision=2))
    assert call(state,route='publications/'+name)[0]['documentRevision']==1
    state.accepted_sources.release('s.c')
    assert state.accepted_sources.rows==state.accepted_sources.publications=={}


@pytest.mark.parametrize('changes',[{'expectedRevision':True},{'expectedRevision':-1},{'expectedRevision':2**53},
    {'source':None},{'source':''},{'source':' '},{'source':'x'*16385},{'source':'\ud800'},
    {'sourceSha256':'bad'},{'extra':True}])
def test_hostile_update_refuses_without_allocating(changes):
    state=fixture()
    with pytest.raises(ContractError): call(state,method='PUT',route='documents/'+NAMES[0],data={**body(),**changes})
    assert state.accepted_sources.rows=={}


def test_ownership_protocol_routes_queries_and_durability_refuse():
    state=fixture();name=NAMES[0]
    for kwargs in ({'principal':'bob'},{'scope':'s.c.1'},{'headers':{}},{'route':'unknown'},
        {'query':'?protocol=unknown'},{'query':'?protocol='+PROTOCOL+'&protocol='+PROTOCOL},
        {'query':'?protocol='+PROTOCOL+'&unknown=x'}, {'route':'documents/not.declared'}):
        with pytest.raises(ContractError): call(state,**kwargs)
    state.store=object()
    with pytest.raises(ContractError): call(state)
    state.store=None
    for route in ('documents/'+name,'publications/'+name):
        with pytest.raises(ContractError): call(state,route=route)
    with pytest.raises(ContractError): call(state,method='POST',route='documents/'+name+'/publish',data=condition())
    with pytest.raises(ContractError): call(state,method='PUT',route='documents/'+name,query='?override=1',data=body())
    with pytest.raises(ContractError): call(state,method='PUT',route='documents/'+name,data=None)
    update(state)
    for kwargs in ({'route':'documents/'+name,'query':'?protocol='+PROTOCOL+'&unknown=x'},
        {'route':'documents/'+name,'query':'?protocol='+PROTOCOL+'&revision=false'},
        {'route':'documents/'+name,'query':'?protocol='+PROTOCOL+'&revision=0'},
        {'route':'publications/'+name,'method':'POST','data':{'protocol':PROTOCOL}},
        {'route':'documents/'+name,'method':'DELETE','data':{'protocol':PROTOCOL}},
        {'route':'documents/'+name+'/invalid','method':'POST','data':{'protocol':PROTOCOL}}):
        with pytest.raises(ContractError): call(state,**kwargs)
    for changes in ({'expectedRevision':True},{'expectedKnowledgeRevision':True},{'extra':True},{'expectedKnowledgeRevision':2}):
        with pytest.raises(ContractError): call(state,method='POST',route='documents/'+name+'/validate',data={**condition(),**changes})
    with pytest.raises(ContractError): update(state,expected=0)


def test_history_eviction_and_storage_budgets_are_explicit():
    state=fixture();name=NAMES[0]
    for revision in range(10): update(state,expected=revision)
    assert len(state.accepted_sources.rows[('s.c',name)])==8
    with pytest.raises(ContractError) as expired: call(state,route='documents/'+name,query='?protocol='+PROTOCOL+'&revision=1')
    assert expired.value.status==410
    assert call(state,route='documents/'+name,query='?protocol='+PROTOCOL+'&revision=3')[0]['revision']==3
    state.accepted_sources.rows[('s.c',name)][-1]['revision']=2**53-1
    with pytest.raises(ContractError) as exhausted: update(state,expected=2**53-1)
    assert exhausted.value.status==429
    state.accepted_sources.rows={(str(i),'other'):[] for i in range(256)}
    with pytest.raises(ContractError) as capacity: update(state)
    assert capacity.value.status==429
