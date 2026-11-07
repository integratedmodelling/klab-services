"""Supplementary finite fixture intake; public HTTP tests remain primary acceptance."""
import pytest

from klab_simulator.source_validation import SOURCE, BAD_SOURCE, capabilities, document, respond
from klab_simulator.state import ContractError, State


def request(state):
    return {'namespace':document(state,SOURCE),'documentVersion':'edit-1','knowledgeRevision':1,
        'executableModels':False,'classifierPreparableDependencies':False,
        'executionContext':None,'preparableMemberQualityDependencies':[]}


def call(state, *, service='reasoner',method='POST',route='/api/v1/validate/document',principal='alice',data=None,body=b''):
    return respond(state,service,method,route,principal,data,body)


def test_literal_parse_and_revision_outcomes_do_not_allocate_assets():
    state=State('source-validation')
    for source in (SOURCE,BAD_SOURCE):
        parsed=call(state,service='resources',route='/parseAsset/NAMESPACE',body=source.encode())[0]
        result=call(state,data={**request(state),'namespace':parsed})[0]
        assert result['status']=='COMPLETE' and bool(result['notifications'])==(source==BAD_SOURCE)
        if source==BAD_SOURCE:
            location=result['notifications'][0]['lexicalContext']
            assert source[location['offsetInDocument']:location['offsetInDocument']+location['length']]=='synthetic:Unknown'
    stale=call(state,data={**request(state),'knowledgeRevision':0})[0]
    assert stale['status']=='STALE_KNOWLEDGE' and stale['notifications']==[]
    assert capabilities(state)['knowledgeRevision']==1
    assert not any(state.counts().values())


@pytest.mark.parametrize('change',[{'namespace':None},{'namespace':{}},{'executableModels':True},
    {'classifierPreparableDependencies':True},{'executionContext':'fake'},
    {'preparableMemberQualityDependencies':[{}]},{'knowledgeRevision':True},{'documentVersion':None},
    {'documentVersion':''},{'extra':True}])
def test_unsupported_intake_refuses(change):
    state=State('source-validation')
    with pytest.raises(ContractError): call(state,data={**request(state),**change})


def test_unsupported_routes_principal_and_source_refuse():
    state=State('source-validation')
    for kwargs in ({'service':'runtime'},{'method':'GET'},{'route':'/api/v1/publish'}): assert call(state,**kwargs) is None
    with pytest.raises(ContractError): call(state,principal='bob',data=request(state))
    with pytest.raises(ContractError): call(state,data=[])
    for source in (b'unknown',b'\xff'):
        with pytest.raises(ContractError): call(state,service='resources',route='/parseAsset/NAMESPACE',body=source)
    for change in ({'urn':'forged'},{'statements':[{}]}):
        with pytest.raises(ContractError): call(state,data={**request(state),'namespace':{**document(state,SOURCE),**change}})
