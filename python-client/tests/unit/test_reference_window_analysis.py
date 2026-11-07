"""Detached selected arrays retain declared provenance, never native time authority."""
import builtins
from dataclasses import replace
import socket

import pytest

np=pytest.importorskip('numpy',reason='Optional analysis extra')
pytest.importorskip('xarray',reason='Optional analysis extra')

from klab_client.errors import InvalidRequestError,ProtocolError,UnsupportedOperationError
from klab_client.reference_windows import analyze_selected_read_plan,ReferenceWindow,KIND
from test_reference_windows import invoke


def test_closed_client_selection_analysis_is_offline_exact_and_independent(monkeypatch):
    source,_=invoke()
    def forbidden(*args,**kwargs):raise AssertionError('Detached analysis must not open sockets')
    monkeypatch.setattr(socket,'socket',forbidden)
    result=analyze_selected_read_plan(source)
    np.testing.assert_equal(result.value.values,[[0.,-2.5],[np.nan,7.]])
    assert result.valid.values.tolist()==[[True,True],[False,True]]
    assert result.x.values.tolist()==[50.,150.] and result.y.values.tolist()==[150.,50.]
    attrs=result.attrs
    assert attrs['units']=='m' and attrs['selection_kind']==KIND
    assert attrs['reference_state_id']=='baseline' and attrs['reference_state_label']=='Declared baseline'
    assert attrs['reference_state_instant']=='2020-01-01T00:00:00Z'
    assert attrs['source_state_revision']==source.selection.temporal_state.source_revision
    assert attrs['selection_window']==(0,2,0,2)
    assert attrs['revision']==source.data.plan.revision
    assert attrs['selection_hash_kind']=='LOCAL_REFERENCE_SELECTION_NOT_NATIVE_TIME_OR_COMMIT'
    original=attrs['reference_selection_sha256']
    result.value.values[0,0]=99
    assert source.data.values==(0.,-2.5,None,7.)
    assert analyze_selected_read_plan(source).value.values[0,0]==0
    changed=replace(source,selection=replace(source.selection,
        temporal_state=replace(source.selection.temporal_state,label='Caller-edited local label')))
    assert analyze_selected_read_plan(changed).attrs['reference_selection_sha256']!=original


@pytest.mark.parametrize('budget',[True,0,-1,8192,8193])
def test_selected_analysis_budget_refuses_before_allocation(budget):
    source,_=invoke()
    with pytest.raises(InvalidRequestError):analyze_selected_read_plan(source,max_bytes=budget)


def test_selected_analysis_manual_dto_and_layout_refusal():
    source,_=invoke()
    for value in (None,replace(source,data=None),replace(source,selection=None),
            replace(source,selection=replace(source.selection,window=None))):
        with pytest.raises(InvalidRequestError):analyze_selected_read_plan(value)
    for state in (replace(source.selection.temporal_state,epoch='c'*32),
            replace(source.selection.temporal_state,units='cm'),
            replace(source.selection.temporal_state,shape=(1,2)),
            replace(source.selection.temporal_state,transform=(100,0,100,0,-100,200))):
        with pytest.raises(ProtocolError):analyze_selected_read_plan(replace(source,selection=replace(source.selection,temporal_state=state)))
    with pytest.raises(ProtocolError):analyze_selected_read_plan(replace(source,selection=replace(source.selection,window=ReferenceWindow(0,1,0,2))))
    with pytest.raises(ProtocolError):analyze_selected_read_plan(replace(source,data=replace(source.data,values=(1.,-2.5,None,7.))))


def test_selected_analysis_optional_dependency_failure_is_explicit(monkeypatch):
    source,_=invoke();original=builtins.__import__
    def blocked(name,*args,**kwargs):
        if name=='numpy':raise ImportError('Synthetic absent optional dependency')
        return original(name,*args,**kwargs)
    monkeypatch.setattr(builtins,'__import__',blocked)
    with pytest.raises(UnsupportedOperationError):analyze_selected_read_plan(source)
