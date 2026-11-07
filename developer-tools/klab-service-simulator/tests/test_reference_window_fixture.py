"""Independent finite selection policies; SDK not imported."""
from copy import deepcopy
from urllib.parse import urlencode

import pytest
from klab_simulator.state import State,ContractError
from klab_simulator.reference_windows import PROTOCOL,PREFIX


def owned():
    state=State('selected-read-plans')
    state.contexts['s.c']={'owner':'alice'};state.observations[('s.c',1)]={'id':1}
    return state


def call(state,method='GET',route='observations/1/states',data=None,query=None,scope='s.c',headers=None):
    if query is None:query={'protocol':PROTOCOL} if method=='GET' else {}
    path=PREFIX+route+('?' + urlencode(query,doseq=True) if query else '')
    return state.read_plan_selections.request(method,path,'alice',scope,
        {'klab-service':state.service_ids['runtime']} if headers is None else headers,data)[0]


def body(state,identity='baseline'):
    row=next(row for row in call(state)['states'] if row['id']==identity)
    return {'protocol':PROTOCOL,'observationId':1,'window':{'rowStart':2,'rowStop':5,'columnStart':4,'columnStop':8},
        'stateId':identity,'sourceRevision':row['sourceRevision'],'epoch':row['epoch'],
        'shardCells':5,'leaseSeconds':30,'maxCells':12}


def test_exact_finite_slicing_and_frozen_grants_distinct_from_source_state():
    state=owned();states=call(state)['states'];baseline=call(state,'POST','open',body(state))
    alternate=call(state,'POST','open',body(state,'alternate'))
    first=state.read_plans.rows[baseline['plan']['plan_id']]['blocks']
    assert [value for block in first for value in block['values']]==[float(100+2*r+3*c) for r in range(2,5) for c in range(4,8)]
    assert baseline['plan']['transform']==[100,0,400,0,-100,1800]
    assert baseline['plan']['revision']!=alternate['plan']['revision']
    saved=deepcopy(first);state.values[44]=999
    assert state.read_plans.rows[baseline['plan']['plan_id']]['blocks']==saved
    assert call(state)['states'][0]['sourceRevision']!=states[0]['sourceRevision']


@pytest.mark.parametrize('field,bad',[('protocol','native'),('observationId',True),('shardCells',0),('leaseSeconds',61),
    ('maxCells',True),('stateId',None),('stateId','unknown'),('sourceRevision','0'*64),('epoch','0'*32),
    ('window',{}),('window',{'rowStart':True,'rowStop':5,'columnStart':4,'columnStop':8}),
    ('window',{'rowStart':0,'rowStop':21,'columnStart':4,'columnStop':8}),('maxCells',11)])
def test_bad_selection_never_allocates_grant(field,bad):
    state=owned();request=body(state);request[field]=bad
    with pytest.raises(ContractError):call(state,'POST','open',request)
    assert state.read_plans.rows=={}


def test_policy_routes_ownership_capacity_and_ephemeral_mode_refuse():
    state=owned();request=body(state)
    for options in ({'method':'DELETE'}, {'route':'observations/01/states'}, {'query':{}},
            {'headers':{}},{'scope':'s.c.1'},{'route':'observations/2/states'},
            {'method':'POST','route':'open','data':request,'query':{'extra':1}}):
        with pytest.raises(ContractError):call(state,**options)
    state.store=object()
    with pytest.raises(ContractError):call(state)
    state.store=None
    for index in range(16):call(state,'POST','open',request)
    with pytest.raises(ContractError):call(state,'POST','open',request)


def test_source_shapes_values_masks_and_manifest_budget_are_explicit():
    state=owned();state.values[0]=None;state.values[1]='NaN'
    frames=state.read_plan_selections.frames('s.c',1)
    assert frames['baseline'][1][:2]==[None,None] and frames['baseline'][2][:2]==[False,False]
    state.values[0]=True
    with pytest.raises(ContractError):call(state)
    state.values.pop()
    with pytest.raises(ContractError):call(state)
    state=owned();state.values=list(range(4096));state.grid['shape']=[64,64]
    request=body(state);request.update(window={'rowStart':0,'rowStop':64,'columnStart':0,'columnStop':64},maxCells=4096,shardCells=1)
    with pytest.raises(ContractError) as too_large:call(state,'POST','open',request)
    assert too_large.value.status==413 and not state.read_plans.rows


def test_identity_collision_never_overwrites_grant(monkeypatch):
    state=owned();request=body(state)
    monkeypatch.setattr('klab_simulator.reference_windows.secrets.token_hex',lambda count:'a'*32)
    call(state,'POST','open',request);saved=deepcopy(state.read_plans.rows)
    with pytest.raises(ContractError):call(state,'POST','open',request)
    assert state.read_plans.rows==saved


def test_expired_and_old_epoch_selected_grants_never_replan():
    state=owned();selected=call(state,'POST','open',body(state));plan=selected['plan']
    path='/experimental/read-plans/v1/'+plan['plan_id']+'/shards/0?'+urlencode({'protocol':plan['protocol'],'epoch':plan['epoch']})
    state.read_plans.rows[plan['plan_id']]['expires']=-1
    with pytest.raises(ContractError) as expired:state.read_plans.request('GET',path,'s.c','alice',{'klab-service':state.service_ids['runtime']},b'')
    assert expired.value.status==410 and not state.read_plans.rows
    request=body(state);state.read_plans.epoch='c'*32
    with pytest.raises(ContractError) as old:call(state,'POST','open',request)
    assert old.value.status==409 and not state.read_plans.rows
