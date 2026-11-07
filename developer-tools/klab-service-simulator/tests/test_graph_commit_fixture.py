"""Independent finite checkpoint policy controls; public HTTP acceptance separate."""
import json
from urllib.parse import urlencode

import pytest

from klab_simulator.graph_commits import PROTOCOL, PREFIX
from klab_simulator.state import State, ContractError


def fixture():
    state=State('graph-convergence');state.contexts['s.c']={'owner':'alice'}
    return state


def call(state,route='snapshot',method='GET',params=None,data=None,scope='s.c',headers=None):
    query={'protocol':PROTOCOL} if params is None else params
    return state.graph_commits.request(method,PREFIX+route+('?' + urlencode(query,doseq=True) if query else ''),
        'alice',scope,{'klab-service':state.service_ids['runtime']} if headers is None else headers,data)[0]


def checkpoint(state,revision):
    return call(state,'checkpoint','POST',params={},data={'protocol':PROTOCOL,'expectedRevision':revision})


def observation(state):
    state.observations[('s.c',1)]={'urn':'s.c.1','name':'initial','observable':{'urn':'reference:Entity'}}


def test_finite_history_cas_deltas_retention_epoch_and_cleanup():
    state=fixture();initial=call(state)
    observation(state);first=checkpoint(state,0)
    assert initial['revision']==0 and len(initial['nodes'])==1 and len(first['nodes'])==3
    state.graph_commits.admin('rename',json.dumps({'contextId':'s.c','observationId':1,'name':'new'}).encode())
    second=checkpoint(state,1)
    state.graph_commits.admin('drop',b'{"contextId":"s.c","observationId":1}')
    third=checkpoint(state,2)
    changes=call(state,'changes',params={'protocol':PROTOCOL,'epoch':initial['epoch'],'since':0,'baseDigest':initial['digest']})
    assert len(changes['deltas'])==3 and len(changes['deltas'][2]['removeNodes'])==2
    assert changes['deltas'][1]['upsertNodes'][0]['properties']['name']=='new'
    first['nodes'].clear();assert len(call(state,params={'protocol':PROTOCOL,'revision':1})['nodes'])==3
    for revision in range(3,10):checkpoint(state,revision)
    with pytest.raises(ContractError) as missing:call(state,params={'protocol':PROTOCOL,'revision':0})
    assert missing.value.status==410
    with pytest.raises(ContractError):call(state,'changes',params={'protocol':PROTOCOL,'epoch':initial['epoch'],'since':0,'baseDigest':initial['digest']})
    state.graph_commits.admin('epoch',b'{"contextId":"s.c"}')
    assert call(state)['epoch']!=initial['epoch']
    state.graph_commits.release('other');state.graph_commits.release('s.c');assert not state.graph_commits.rows


@pytest.mark.parametrize('change',[{'method':'DELETE'},{'params':{}},{'params':{'protocol':'wrong'}},
    {'params':{'protocol':[PROTOCOL,PROTOCOL]}},{'params':{'protocol':PROTOCOL,'extra':1}},
    {'params':{'protocol':PROTOCOL,'revision':'x'}},{'params':{'protocol':PROTOCOL,'revision':-1}},
    {'params':{'protocol':PROTOCOL,'revision':2**53}},{'route':'unknown'},
    {'route':'changes'},{'route':'changes','params':{'protocol':PROTOCOL,'epoch':'wrong','since':0,'baseDigest':'0'*64}},
    {'route':'changes','params':{'protocol':PROTOCOL,'epoch':'unused','since':'bad','baseDigest':'0'*64}},
    {'headers':{}},{'scope':'s.c.1'}])
def test_strict_read_route_selectors_and_current_origin_refuse(change):
    with pytest.raises(ContractError):call(fixture(),**change)


@pytest.mark.parametrize('data',[None,{}, {'protocol':PROTOCOL,'expectedRevision':True},
    {'protocol':PROTOCOL,'expectedRevision':-1},{'protocol':'wrong','expectedRevision':0},
    {'protocol':PROTOCOL,'expectedRevision':2}])
def test_checkpoint_exact_shape_and_cas_refuse(data):
    with pytest.raises(ContractError):call(fixture(),'checkpoint','POST',params={},data=data)


def test_capacity_and_durable_modes_never_truncate_or_claim_persistence():
    state=fixture();state.store=object()
    with pytest.raises(ContractError):call(state)
    state.store=None;initial=call(state)
    with pytest.raises(ContractError):call(state,'changes',params={'protocol':PROTOCOL,'epoch':initial['epoch'],'since':0,'baseDigest':'0'*64})
    state.graph_commits.rows['s.c'][0]['revision']=2**53-1
    with pytest.raises(ContractError):checkpoint(state,2**53-1)
    state.graph_commits.rows={str(i):[] for i in range(128)}
    with pytest.raises(ContractError):call(state)
    state=fixture();state.observations={('s.c',i):{} for i in range(128)}
    with pytest.raises(ContractError):checkpoint(state,0)
    assert call(state)['revision']==0


@pytest.mark.parametrize('action,body',[('epoch',b'x'*4097),('epoch',b'bad'),('epoch',b'[]'),
    ('epoch',b'{"contextId":"foreign"}'),('epoch',b'{"contextId":"s.c","extra":1}'),
    ('rename',b'{"contextId":"s.c","observationId":true,"name":"x"}'),
    ('rename',b'{"contextId":"s.c","observationId":2,"name":"x"}'),
    ('rename',b'{"contextId":"s.c","observationId":1,"name":""}')])
def test_authenticated_fixture_control_intake_not_a_native_mutation_api(action,body):
    state=fixture();observation(state)
    with pytest.raises(ContractError):state.graph_commits.admin(action,body)


def test_aggregate_graph_and_delta_response_budgets_refuse_without_truncation():
    state=fixture();observation(state)
    state.observations[('s.c',1)]['name']='x'*(2*1024*1024)
    with pytest.raises(ContractError):checkpoint(state,0)
    state.observations[('s.c',1)]['name']='x'*1100000
    initial=call(state);checkpoint(state,0)
    state.observations[('s.c',1)]['name']='y'*1100000
    checkpoint(state,1)
    with pytest.raises(ContractError) as error:
        call(state,'changes',params={'protocol':PROTOCOL,'epoch':initial['epoch'],'since':0,'baseDigest':initial['digest']})
    assert error.value.status==429 and call(state)['revision']==2
