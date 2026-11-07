"""Independent literal admission/private-session policy, no SDK imports."""
from copy import deepcopy
import hashlib
from urllib.parse import urlencode

import pytest
from klab_simulator.managed_behaviors import PROTOCOL,PREFIX,SOURCES
from klab_simulator.state import State,ContractError


def fixture():
    state=State('managed-behavior');state.sessions['s']={'owner':'alice'}
    state.contexts['s.c']={'owner':'alice'};state.contexts['s.other']={'owner':'alice'}
    return state


def body(recipe='finish'):
    source=SOURCES[recipe]
    return {'protocol':PROTOCOL,'source':source,'sourceSha256':hashlib.sha256(source.encode()).hexdigest()}


def call(state,route='validate',method='POST',data=None,params=None,scope='s.c',headers=None):
    return state.managed_behaviors.request(method,PREFIX+route+('?' + urlencode(params,doseq=True) if params else ''),
        'alice',scope,{'klab-service':state.service_ids['runtime']} if headers is None else headers,data)[0]


def create(state,recipe='finish'):
    return call(state,'create',data={**body(recipe),'recipe':recipe})


def command(state,row,operation,revision=None):
    return call(state,row['reference']['agentId']+'/'+operation,data={'protocol':PROTOCOL,
        'epoch':row['reference']['epoch'],'expectedRevision':row['revision'] if revision is None else revision})


def read(state,row):
    return call(state,row['reference']['agentId'],'GET',params={'protocol':PROTOCOL,'epoch':row['reference']['epoch']})


@pytest.mark.parametrize('recipe,stop,terminal',[('finish',False,'FINISHED'),('fail',False,'FAILED'),('finish',True,'STOPPED')])
def test_private_session_owned_disposal_separate_from_terminal_then_purge(recipe,stop,terminal):
    state=fixture();admitted=call(state,data=body(recipe));assert admitted['recipe']==recipe and len(state.sessions)==1
    created=create(state,recipe);private=created['privateSessionId']
    assert private in state.sessions and len(state.sessions)==2
    active=command(state,created,'stop' if stop else 'start');assert active['revision']==2
    terminal_row=read(state,created)
    assert terminal_row['state']==terminal and not terminal_row['privateSessionDisposed'] and private in state.sessions
    cleaned=read(state,created);assert cleaned['privateSessionDisposed'] and private not in state.sessions
    assert read(state,created)==cleaned
    assert command(state,cleaned,'purge')['purged'] is True
    with pytest.raises(ContractError):read(state,created)
    assert state.sessions=={'s':{'owner':'alice'}} and len(state.contexts)==2


@pytest.mark.parametrize('data',[None,{}, {'protocol':'wrong'}, {**body(),'extra':1}, {**body(),'source':None},
    {**body(),'source':'x'*4097},{**body(),'source':'\ud800'},{**body(),'source':'arbitrary code'},
    {**body(),'sourceSha256':'0'*64}])
def test_no_arbitrary_source_or_changed_hash_admission(data):
    state=fixture()
    with pytest.raises(ContractError):call(state,data=data)
    assert not state.managed_behaviors.rows and len(state.sessions)==1


def test_recipe_capacity_durable_and_private_identity_collision_refuse_without_overwriting(monkeypatch):
    state=fixture()
    with pytest.raises(ContractError):call(state,'create',data={**body(),'recipe':'fail'})
    state.store=object()
    with pytest.raises(ContractError):create(state)
    state.store=None
    for i in range(15):create(state)
    with pytest.raises(ContractError):create(state)
    state=fixture();state.managed_behaviors.rows={'x':{} for i in range(1)}
    state.managed_behaviors.rows={str(i):{} for i in range(16)}
    with pytest.raises(ContractError):create(state)
    state=fixture();state.sessions['a'*32]={'owner':'bob'}
    monkeypatch.setattr('klab_simulator.managed_behaviors.secrets.token_hex',lambda n:'a'*32)
    with pytest.raises(ContractError):create(state)
    assert state.sessions['a'*32]=={'owner':'bob'}


@pytest.mark.parametrize('change',[{'scope':'s.c.1'},{'headers':{}},{'route':'x','method':'GET'},{'method':'DELETE','route':'a'*32},
    {'method':'GET','route':'a'*32},{'method':'GET','route':'a'*32,'params':{'protocol':PROTOCOL,'epoch':['a','b']}},
    {'route':'a'*32,'data':{'protocol':PROTOCOL,'epoch':'a'*32,'expectedRevision':True}},
    {'route':'a'*32,'data':{'protocol':PROTOCOL,'epoch':'a'*32,'expectedRevision':0}},
    {'route':'validate','params':{'extra':1},'data':body()}])
def test_exact_origin_route_method_and_selectors_required(change):
    with pytest.raises(ContractError):call(fixture(),**change)


def test_wrong_root_epoch_commands_and_singleuse_refuse_with_no_replay():
    state=fixture();created=create(state);identity=created['reference']['agentId'];epoch=created['reference']['epoch']
    for params,scope in (({'protocol':PROTOCOL,'epoch':'0'*32},'s.c'),({'protocol':PROTOCOL,'epoch':epoch},'s.other')):
        with pytest.raises(ContractError):call(state,identity,'GET',params=params,scope=scope)
    with pytest.raises(ContractError):command(state,created,'purge')
    started=command(state,created,'start')
    with pytest.raises(ContractError):command(state,created,'start')
    with pytest.raises(ContractError):command(state,started,'start')
    with pytest.raises(ContractError):command(state,started,'unknown')
    with pytest.raises(ContractError):call(state,identity+'/extra','GET',params={'protocol':PROTOCOL,'epoch':epoch})
    # Prior invalid GET didn't tick; explicit stopping still needs independent terminal/disposal.
    stopped=command(state,started,'stop');assert stopped['state']=='STOPPING'


def test_session_interference_never_claims_disposal_or_deletes_unrelated_state():
    state=fixture();created=create(state);private=created['privateSessionId']
    del state.sessions[private]
    with pytest.raises(ContractError):read(state,created)
    state.sessions[private]={'owner':'bob','managedBehavior':'unrelated'}
    command(state,created,'stop');read(state,created)
    with pytest.raises(ContractError):read(state,created)
    assert state.sessions[private]['owner']=='bob'
    state.sessions[private]={'owner':'alice','managedBehavior':created['reference']['agentId']}
    state.contexts[private+'.independent']={'owner':'alice'}
    with pytest.raises(ContractError):read(state,created)
    assert private in state.sessions
    del state.contexts[private+'.independent']
    assert read(state,created)['privateSessionDisposed']


def test_owner_context_release_only_disposes_its_own_private_session_rows():
    state=fixture();created=create(state)
    other=call(state,'create',data={**body(),'recipe':'finish'},scope='s.other')
    state.managed_behaviors.release('foreign');state.managed_behaviors.release('s.c')
    assert created['privateSessionId'] not in state.sessions and other['privateSessionId'] in state.sessions
    assert list(state.managed_behaviors.rows)==[other['reference']['agentId']]


def test_release_of_already_disposed_row_is_local_and_invalid_read_protocol_refuses():
    state=fixture();created=create(state)
    with pytest.raises(ContractError):call(state,created['reference']['agentId'],'GET',params={'protocol':'wrong','epoch':'a'*32})
    command(state,created,'stop');read(state,created);read(state,created)
    state.managed_behaviors.release('s.c');assert not state.managed_behaviors.rows and len(state.sessions)==1
