"""Independent finite raster declaration policies, no SDK import."""
import pytest
from klab_simulator.state import State,ContractError
from klab_simulator.reference_raster_labels import response,PROTOCOL,PREFIX


def owned(scenario='category-code-export'):
    state=State(scenario);state.contexts['s.c']={'owner':'alice'};state.observations[('s.c',1)]={'id':1}
    return state


def call(state,method='GET',route='observations/1',query='?protocol='+PROTOCOL,scope='s.c',headers=None):
    return response(state,method,PREFIX+route+query,'alice',scope,
        {'klab-service':state.service_ids['runtime']} if headers is None else headers)[0]


@pytest.mark.parametrize('scenario',['category-code-export','declared-band-export'])
def test_declared_exact_fixture_bytes_worldview_and_labels_not_native_metadata(scenario):
    state=owned(scenario);value=call(state)
    assert value['worldviewId']=='synthetic' and value['worldviewRevision']==1
    assert value['worldviewServiceId']==state.service_ids['reasoner']
    if scenario=='category-code-export':
        assert value['exportSha256']=='e025556feac60d80c68f3dcd7465948f7a071186a9ff5f377c99b28cbc6eaccb'
        assert value['dictionary']==[[0,'bare'],[-2,'forest'],[7,'water']] and value['dtype']=='int16'
    else:
        assert value['exportSha256']=='6a41239e3b662ff32d901b80fa41ff9202a03523c27c72aae2e25451349763f0'
        assert value['bandLabels']==['Declared band 1','Declared band 2','Declared band 3']
        assert value['bandInstants'][1]=='2026-01-02T00:00:00Z' and value['dictionary']==[]


@pytest.mark.parametrize('options',[{'method':'POST'},{'route':'observations/01'}, {'query':''},
    {'query':'?protocol='+PROTOCOL+'&protocol='+PROTOCOL},{'scope':'s.c.1'},{'headers':{}},{'route':'observations/2'}])
def test_ambiguous_unowned_or_unsupported_selectors_refuse(options):
    with pytest.raises(ContractError):call(owned(),**options)


def test_unselected_scenario_never_invents_dictionary():
    with pytest.raises(ContractError):call(owned('elevation'))
