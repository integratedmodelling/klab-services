"""Supplementary finite derived manifests, not public SDK acceptance replacement."""
import hashlib
import json

import pytest

from klab_simulator.authoring import MODEL, NAMESPACE, RESOURCE, SOURCE
from klab_simulator.input_provenance import evidence
from klab_simulator.state import ContractError, State
from test_dependency_graph_fixture import call


def test_graph_and_recorded_manifest_require_owned_completed_upload_and_source():
    state=State('annotated-input')
    state.contexts['s.c']={'owner':'alice'}
    with pytest.raises(ContractError):call(state,route='MODEL/'+MODEL)
    binary=hashlib.sha256(b'declared finite input').hexdigest()
    state.imported_resources[RESOURCE]={'owner':'alice','scope':'s.c',
        'asset':{'metadata':{'delegate':{'sourceSha256':binary,'sourceBytes':21}}}}
    with pytest.raises(ContractError):call(state,route='MODEL/'+MODEL)
    state.authoring.assets[('NAMESPACE',NAMESPACE)]={'owner':'alice','payload':{'sourceCode':SOURCE}}
    graph=call(state,route='MODEL/'+MODEL,query='?protocol=klab.experimental.dependency-graphs/1&pageSize=3')
    assert graph['nodeCount']==3 and graph['edgeCount']==2
    resource=graph['nodes'][2]
    manifest=json.loads(resource['source'])
    assert manifest['binarySha256']==binary and resource['sourceSha256']!=binary
    recorded=evidence(state,'s.c','alice')[3]
    assert recorded['manifestSha256']==hashlib.sha256(resource['source'].encode()).hexdigest()
    assert recorded['annotationSha256']==hashlib.sha256(SOURCE.encode()).hexdigest()
    state.imported_resources[RESOURCE]['scope']='other.root'
    with pytest.raises(ContractError):call(state,route='MODEL/'+MODEL)
    state.dependency_graphs.release('s.c')
    assert not state.dependency_graphs.snapshots
