"""Maintained experiment orchestration over explicit doubles, not native terrain science."""
from types import SimpleNamespace

import pytest

from klab_client.experiment import run_elevation
from klab_client.errors import JobFailedError, WaitTimeout


def setup(*, interrupted=False, context_release=True, session_release=True, units='m'):
    events = []
    dimension = {'type': 'SPACE', 'shape': [5, 4], 'regular': True,
                 'parameters': {'proj': 'EPSG:3857', 'bbox': [200000, 200500, 6000000, 6000400]}}
    geometry = SimpleNamespace(raw={'dimensions': [dimension]})
    region = SimpleNamespace(id=1, raw={'empty': False}, geometry=geometry,
                             observable=SimpleNamespace(raw={'artifactType': 'OBJECT'}))
    metres = SimpleNamespace(values=tuple(str(value) for value in range(100, 120)))
    millimetres = SimpleNamespace(values=tuple(str(value) for value in range(100000, 120000, 1000)))
    def fetch(cells, *, curve, semantics=None):
        assert list(cells) == list(range(20)) and curve == 'D2_YX'
        events.append(('fetch', semantics is not None))
        return metres if semantics is None else millimetres
    elevation = SimpleNamespace(id=2, raw={'resolvedCoverage': 1}, units=units, geometry=geometry,
                                observable=SimpleNamespace(raw={'urn': 'declared:elevation'}), fetch_data=fetch)
    class Context:
        id = 'session.context'
        def submit(self, observation):
            is_region = observation.urn == 'staging.storage:rectangle'
            events.append(('submit', is_region))
            def result(timeout):
                assert timeout == 2
                if interrupted:
                    raise WaitTimeout(SimpleNamespace(id=1))
                return region if is_region else elevation
            return SimpleNamespace(id=1 if is_region else 2, scope=self, result=result)
        def get_context_id(self):
            return self.id
        def within(self, value):
            assert value is region
            events.append('focus')
            return self
        def release(self):
            events.append('release-context')
            return context_release
    context = Context()
    class Session:
        id = 'session'
        def create_context(self, configuration):
            assert configuration['persistence'] == 'ONE_OFF'
            events.append('create-context')
            return context
        def release(self):
            events.append('release-session')
            return session_release
    def create_session(name):
        assert name == 'python-elevation-acceptance'
        events.append('create-session')
        return Session()
    def resolve(definition):
        # Explicit structural observable double. It carries no native semantics.
        return SimpleNamespace(raw={'@CLASS': 'DECLARED_TEST_DOUBLE_NOT_NATIVE_WIRE_ACCEPTANCE',
                                    'urn': definition, 'semantics': {'urn': 'declared:Elevation'},
                                    'unit': {'definition': 'mm' if definition.endswith(' in mm') else 'm'}})
    client = SimpleNamespace(create_session=create_session, reasoner=SimpleNamespace(resolve_observable=resolve))
    return client, context, events, (elevation, metres, millimetres)


@pytest.mark.parametrize('attached', [False, True])
def test_experiment_success_checks_finite_values_and_releases_only_created_scopes(attached):
    client, context, events, expected = setup()
    result = run_elevation(client, context=context if attached else None, timeout=2, report=lambda text: None)
    assert result == expected and events.count(('fetch', False)) == events.count(('fetch', True)) == 1
    assert ('release-context' in events) is not attached
    assert ('release-session' in events) is not attached


def test_interruption_and_failed_scientific_preconditions_preserve_scopes():
    client, context, events, _ = setup(interrupted=True)
    with pytest.raises(WaitTimeout):
        run_elevation(client, timeout=2, report=lambda text: None)
    assert 'release-context' not in events and 'release-session' not in events
    client, context, events, _ = setup(units='wrong')
    with pytest.raises(JobFailedError):
        run_elevation(client, timeout=2, report=lambda text: None)
    assert 'release-context' not in events and 'release-session' not in events


@pytest.mark.parametrize('options', [{'context_release': False}, {'session_release': False}])
def test_unconfirmed_owned_release_never_claims_success(options):
    client, context, events, _ = setup(**options)
    with pytest.raises(JobFailedError):
        run_elevation(client, timeout=2, report=lambda text: None)
    assert 'release-context' in events
    assert ('release-session' in events) == options.get('context_release', True)
