"""Supplementary finite group fixture, not public SDK acceptance replacement."""
import pytest

from klab_simulator.dependencies import respond
from klab_simulator.state import ContractError, State


def test_finite_groups_version_and_denials():
    state=State('dependency-inspection');state.contexts['s.c']={'owner':'alice'}
    assert respond(state,'POST','/api/v1/resolve/NAMESPACE/synthetic.root','alice','s.c') is None
    assert respond(state,'GET','/other','alice','s.c') is None
    with pytest.raises(ContractError): respond(state,'GET','/api/v1/resolve/NAMESPACE/other','alice','s.c')
    with pytest.raises(ContractError): respond(state,'GET','/api/v1/resolve/NAMESPACE/synthetic.root','bob','s.c')
    state.contexts['b.c']={'owner':'bob'}
    with pytest.raises(ContractError): respond(state,'GET','/api/v1/resolve/NAMESPACE/synthetic.root','bob','b.c')
    first=respond(state,'GET','/api/v1/resolve/NAMESPACE/synthetic.root','alice','s.c')[0]
    assert first['results'][0]['resourceVersion']['build']==1
    state.dependency_revision=2
    second=respond(state,'GET','/api/v1/resolve/NAMESPACE/synthetic.root','alice','s.c')[0]
    assert second['results'][0]['resourceVersion']['build']==2
    assert first['results'][0]['resourceVersion']['build']==1
    state.store=object()
    with pytest.raises(ContractError): respond(state,'GET','/api/v1/resolve/NAMESPACE/synthetic.root','alice','s.c')
