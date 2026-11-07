"""Supplementary finite graph intake/retention; public SDK HTTP workflows primary."""
import pytest

from klab_simulator.dependency_graphs import PREFIX,PROTOCOL
from klab_simulator.state import ContractError,State


def fixture():
    state=State('dependency-graphs');state.contexts['s.c']={'owner':'alice'}
    state.contexts['other.c']={'owner':'alice'};state.contexts['bob.c']={'owner':'bob'}
    return state


def call(state,*,method='GET',route='MODEL/synthetic.model',query=None,scope='s.c',principal='alice',headers=None):
    if query is None:query='?protocol='+PROTOCOL+'&pageSize=1'
    return state.dependency_graphs.request(method,PREFIX+route+query,principal,scope,
        {'klab-service':state.service_ids['runtime']} if headers is None else headers)[0]


def continuation(page,size=1,**changes):
    data={'snapshot':page['snapshotId'],'cursor':page['nextCursor'],**changes}
    return '?protocol='+PROTOCOL+'&pageSize='+str(size)+''.join('&'+k+'='+v for k,v in data.items())


def test_snapshot_pages_are_bound_immutable_sources_and_owned_cleanup():
    state=fixture();first=call(state)
    second=call(state,query=continuation(first))
    assert len(first['nodes'])==len(second['nodes'])==1 and len(first['edges'])==4 and second['edges']==[]
    assert first['nodes'][0]['urn']=='synthetic.model' and second['nodes'][0]['urn']=='synthetic.namespace'
    first['nodes'][0]['source']='forged'
    snapshot=state.dependency_graphs.snapshots[('s.c',first['snapshotId'])][0]
    assert snapshot['nodes'][0]['source']!='forged'
    third=call(state,query=continuation(second));fourth=call(state,query=continuation(third))
    fifth=call(state,query=continuation(fourth));assert fifth['nextCursor'] is None
    state.dependency_graphs.release('s.c');assert not state.dependency_graphs.snapshots


@pytest.mark.parametrize('kwargs',[{'method':'POST'},{'route':'RESOURCE/unknown:input'},{'headers':{}},
    {'principal':'bob'},{'scope':'bob.c','principal':'bob'},
    {'query':'?protocol=unknown&pageSize=1'}, {'query':'?protocol='+PROTOCOL+'&pageSize=1&extra=x'},
    {'query':'?protocol='+PROTOCOL+'&pageSize=1&protocol='+PROTOCOL},
    {'query':'?protocol='+PROTOCOL+'&pageSize=false'}, {'query':'?protocol='+PROTOCOL+'&pageSize=0'},
    {'query':'?protocol='+PROTOCOL+'&pageSize=33'}, {'query':'?protocol='+PROTOCOL+'&pageSize=１２'}])
def test_unsupported_or_unauthorized_requests_refuse(kwargs):
    state=fixture()
    with pytest.raises(ContractError):call(state,**kwargs)
    assert not state.dependency_graphs.snapshots


def test_continuations_require_exact_snapshot_root_cursor_size_and_current_revision():
    state=fixture();first=call(state)
    for query,scope in ((continuation(first,snapshot='bad'),'s.c'),
        (continuation(first,cursor='bad'),'s.c'),(continuation(first,snapshot='e'*32),'s.c'),
        (continuation(first,cursor='e'*32),'s.c'),(continuation(first,size=2),'s.c'),
        (continuation(first),'other.c')):
        with pytest.raises(ContractError):call(state,query=query,scope=scope)
    state.dependency_graphs.revision+=1
    with pytest.raises(ContractError) as conflict:call(state,query=continuation(first))
    assert conflict.value.status==409


def test_ephemeral_snapshot_and_cursor_capacities_refuse_no_silent_eviction():
    state=fixture();state.store=object()
    with pytest.raises(ContractError):call(state)
    state.store=None
    first=call(state)
    snapshot,cursors=state.dependency_graphs.snapshots[('s.c',first['snapshotId'])]
    for i in range(64):cursors[format(i,'032x')]=(1,1)
    with pytest.raises(ContractError) as fullcursor:call(state,query=continuation(first))
    assert fullcursor.value.status==429
    for _ in range(7):call(state)
    with pytest.raises(ContractError) as fullroot:call(state)
    assert fullroot.value.status==429
    state.dependency_graphs.snapshots={(str(i),'other'):None for i in range(128)}
    with pytest.raises(ContractError) as globalfull:call(state)
    assert globalfull.value.status==429


def test_declared_fault_modes_are_explicit_not_local_semantic_inference():
    state=fixture();state.dependency_graphs.mode='cycle'
    assert call(state)['edgeCount']==5
    state.dependency_graphs.mode='missing-endpoint'
    assert call(state)['edges'][-1]['targetId']=='f'*32


def test_resource_root_graph_and_cursor_cannot_be_rebound_to_another_asset():
    state=fixture();first=call(state,route='RESOURCE/synthetic:input')
    assert first['requestedClass']=='RESOURCE' and first['nodeCount']==2 and first['edgeCount']==1
    second=call(state,route='RESOURCE/synthetic:input',query=continuation(first))
    assert second['nextCursor'] is None and second['nodes'][0]['urn']=='synthetic:base-input'
    with pytest.raises(ContractError):call(state,query=continuation(first))
