"""Independent local/mock-wire boundary controls; never real scientific acceptance."""
from copy import deepcopy
from types import SimpleNamespace

import httpx
import pytest

from klab_client import Client, Endpoint
from klab_client.api.knowledge import ConceptImpl, ObservableImpl
from klab_client.api.runtime import GeometryImpl, ObservationImpl
from klab_client.api.services import RuntimeServiceImpl
from klab_client.dto import (concept_from_wire, decode_cell, observable_from_wire,
                             observation_to_wire, storage_semantics)
from klab_client.errors import (ConfigurationError, InvalidRequestError, ProtocolError,
                                TransportError, UnsupportedOperationError)
from klab_client.experiment import rectangle_geometry, verify_elevation, verify_grid, verify_region
from klab_client.transport import Transport


@pytest.mark.parametrize('change', [
    {'contextualization': 1}, {'type': 'QUALITY'}, {'type': [None]}, {'qualifier': 'FUTURE'}])
def test_concept_rejects_malformed_declared_fields(change):
    with pytest.raises(ProtocolError):
        concept_from_wire({'urn': 'fixture:Concept', **change})


@pytest.mark.parametrize('change', [{'contextualization': []}, {'resolutionDirectives': {}}])
def test_observable_rejects_malformed_declared_fields(change):
    with pytest.raises(ProtocolError):
        observable_from_wire({'urn': 'fixture:Observable', 'semantics': {'urn': 'fixture:Concept'}, **change})


def test_observation_mapping_requires_discriminator_and_preserves_explicit_value():
    with pytest.raises(InvalidRequestError, match='discriminator'):
        observation_to_wire({'observable': {'urn': 'fixture:Observable'}})
    raw = {'@CLASS': 'fixture.Observable', 'urn': 'fixture:Observable',
           'semantics': {'urn': 'fixture:Concept'}}
    observation = ObservationImpl('fixture:Observation', observable_from_wire(raw), value=0)
    wire = observation_to_wire(observation)
    assert wire['value'] == 0 and type(wire['value']) is int
    assert wire['observable'] == raw


@pytest.mark.parametrize('change', [{'range': '0..1'}, {'currency': 'USD'},
                                   {'unit': {'contextual': True}},
                                   {'unit': {'aggregatedDimensions': ['time']}}])
def test_contextual_storage_never_silently_uses_native_semantics(change):
    observable = ObservableImpl(ConceptImpl('fixture:Concept'), raw={
        '@CLASS': 'fixture.Observable', 'urn': 'fixture:Observable',
        'semantics': {'urn': 'fixture:Concept'}, **change})
    with pytest.raises(UnsupportedOperationError, match='explicit StorageScan'):
        storage_semantics(observable)


def test_unknown_storage_type_and_unbound_geometry_or_observation_refuse():
    with pytest.raises(UnsupportedOperationError, match='artifact type'):
        decode_cell('private-local-text', 'FUTURE')
    with pytest.raises(UnsupportedOperationError, match='from_wire'):
        GeometryImpl('space', (2, 3)).to_wire()
    observation = ObservationImpl('fixture:Observation', ObservableImpl(ConceptImpl('fixture:Concept')))
    with pytest.raises(UnsupportedOperationError, match='remotely bound'):
        observation.fetch_data((0,))


@pytest.mark.parametrize('operation,route', [
    (lambda c: c.resources.list(), '/resources/api/v1/list/RESOURCE'),
    (lambda c: c.runtime.get_context_info(), '/runtime/api/v1/contexts'),
    (lambda c: c.runtime.release_context('s.c'), '/runtime/releaseContext'),
    (lambda c: c.runtime.query_knowledge_graph({}, 's.c'), '/runtime/api/v1/query'),
    (lambda c: c.resolver.get_submitted_resources('s.c'), '/resolver/api/v1/resources')])
def test_public_service_array_and_boolean_contracts_refuse_malformed_reply(operation, route):
    calls = []
    def handler(request):
        calls.append(request.url.path)
        return httpx.Response(200, json={'not': 'the required array or boolean'})
    with Client('https://runtime.invalid/runtime',
                resources=Endpoint('https://resources.invalid/resources'),
                resolver=Endpoint('https://resolver.invalid/resolver'),
                http_transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(ProtocolError):
            operation(client)
    assert calls == [route]


def test_detached_runtime_submission_refuses_before_using_observation():
    with pytest.raises(ConfigurationError, match='through Client'):
        RuntimeServiceImpl().submit(None, None)


@pytest.mark.parametrize('timeout,poll', [(float('nan'), .5), (30, 0), (-1, .5)])
def test_transport_rejects_invalid_wait_configuration_before_http(timeout, poll):
    with pytest.raises(ConfigurationError, match='finite and positive'):
        Transport({'runtime': Endpoint('https://runtime.invalid')}, timeout=timeout, poll_interval=poll)


def test_expired_transport_deadline_never_sends_request(monkeypatch):
    monkeypatch.setattr('klab_client.transport.time.monotonic', lambda: 10)
    transport = Transport({'runtime': Endpoint('https://runtime.invalid')},
                          http_transport=httpx.MockTransport(lambda request: pytest.fail('Must not send HTTP')))
    try:
        with pytest.raises(TransportError, match='deadline expired'):
            transport.request('runtime', 'GET', '/read', deadline=9)
    finally:
        transport.close()


def test_deadline_rechecked_after_empty_body_before_decoding(monkeypatch):
    clock = iter((1, 3))
    monkeypatch.setattr('klab_client.transport.time.monotonic', lambda: next(clock))
    calls = []
    def handler(request):
        calls.append(request.url.path)
        return httpx.Response(200, content=b'')
    transport = Transport({'runtime': Endpoint('https://runtime.invalid')}, http_transport=httpx.MockTransport(handler))
    try:
        with pytest.raises(TransportError, match='deadline expired'):
            transport.request('runtime', 'GET', '/read', deadline=2)
        assert calls == ['/read']
    finally:
        transport.close()


def test_nonserializable_transport_body_has_bounded_error_without_sending_private_data():
    transport = Transport({'runtime': Endpoint('https://runtime.invalid')}, http_transport=httpx.MockTransport(
        lambda request: pytest.fail('Serialization failure must precede HTTP')))
    try:
        with pytest.raises(InvalidRequestError, match='cannot be serialized') as caught:
            transport.request('runtime', 'POST', '/write', json={'private-source-material': {1, 2}})
        assert 'private-source-material' not in str(caught.value)
    finally:
        transport.close()


def grid_observation():
    geometry = rectangle_geometry()
    geometry.raw['dimensions'][0]['parameters']['delegate']['bbox'] = [200000, 200500, 6000000, 6000400]
    return SimpleNamespace(id=1, raw={}, geometry=geometry,
                           observable=SimpleNamespace(raw={'artifactType': 'OBJECT'}))


def test_independent_grid_and_substantial_guards_do_not_infer_missing_geometry():
    valid = grid_observation()
    verify_grid(valid)
    verify_region(valid)
    with pytest.raises(AssertionError, match='lacks scientific geometry'):
        verify_grid(SimpleNamespace(geometry=None))
    malformed = deepcopy(valid)
    malformed.geometry.raw['dimensions'][0]['regular'] = False
    with pytest.raises(AssertionError, match='regular 5 x 4'):
        verify_grid(malformed)
    malformed = deepcopy(valid)
    malformed.id = 0
    with pytest.raises(AssertionError, match='committed substantial'):
        verify_region(malformed)


@pytest.mark.parametrize('metres,millimetres,message', [
    ([1] * 19, [1000] * 20, 'Expected 20'),
    ([None] * 20, [None] * 20, 'No real scientific'),
    ([None] + [1] * 19, [0] + [1000] * 19, 'changed missingness'),
    ([0] + [1] * 19, [None] + [1000] * 19, 'changed missingness'),
    ([float('nan')] * 20, [0] * 20, 'Nonfinite'),
    ([-501] * 20, [-501000] * 20, 'implausible'),
    ([1] * 20, [999] * 20, 'independent metres')])
def test_scientific_invariant_negative_controls_are_not_provider_accuracy(metres, millimetres, message):
    with pytest.raises(AssertionError, match=message):
        verify_elevation(metres, millimetres)


def test_scientific_invariant_preserves_matching_missingness_with_real_local_values():
    verify_elevation([None] + [1] * 19, [None] + [1000] * 19)
