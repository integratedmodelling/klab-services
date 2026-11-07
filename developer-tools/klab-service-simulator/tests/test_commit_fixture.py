"""Supplementary cache source-shape/intake policy, not native execution proof."""
import pytest

from klab_simulator.state import ContractError, State


def fixture():
    state=State('commit-inspection');state.contexts['s.c']={'owner':'alice'}
    return state


def call(state,method='GET',query='?id=1001',scope='s.c',headers=None,path='/api/v1/commit'):
    return state.commit_reports.request(method,path+query,'alice',scope,
        {'klab-service':state.service_ids['runtime']} if headers is None else headers)[0]


def test_record_is_copied_and_scope_cleanup_cache_boundaries_explicit():
    state=fixture();source={'id':1,'metadata':{'delegate':{}}}
    assert call(state) is None
    state.commit_reports.record('s.c',source)
    assert source['metadata']['delegate']['im:commit-id']==1001
    report=call(state);assert report['addedAssets']==[1]
    report['addedAssets'].append(7);assert call(state)['addedAssets']==[1]
    for identity in range(2,130):
        state.commit_reports.record('s.c',{'id':identity,'metadata':{'delegate':{}}})
    assert len(state.commit_reports.rows)==128 and call(state) is None
    state.commit_reports.release('other');assert len(state.commit_reports.rows)==128
    state.commit_reports.release('s.c');assert not state.commit_reports.rows


@pytest.mark.parametrize('change',[{'method':'POST'},{'path':'/api/v1/other'},{'headers':{}},
    {'query':''},{'query':'?id=1&id=2'},{'query':'?id=1&extra=2'},{'query':'?id=True'},
    {'query':'?id=0'},{'query':'?id=-1'},{'query':'?id=9223372036854775808'}, {'query':'?id=１２'}])
def test_exact_native_shape_and_scope_requirements_refuse(change):
    with pytest.raises(ContractError):call(fixture(),**change)


def test_ephemeral_only_and_unsupported_focused_scope():
    state=fixture();state.store=object()
    with pytest.raises(ContractError):call(state)
    state.store=None
    with pytest.raises(ContractError):call(state,scope='s.c.1')
