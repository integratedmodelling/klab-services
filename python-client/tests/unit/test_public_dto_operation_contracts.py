"""Public local DTO/modeler accessors, not remote roles or scientific authority."""
import json

import httpx
import pytest

from klab_client.api.knowledge import ConceptImpl, ObservableImpl, ResolutionDirective
from klab_client.api.modeler import ModelerImpl
from klab_client.api.runtime import ObservationImpl
from klab_client.api.scopes import UserScopeImpl, SessionScopeImpl, ContextScopeImpl
from klab_client.errors import SubmissionOutcomeUnknown
from klab_client.errors import InvalidRequestError
from klab_client import Context
from test_client import make_client


def test_user_only_token_cannot_become_a_context_selection():
    with make_client(lambda request: pytest.fail('Invalid local scope must not send HTTP')) as client:
        with pytest.raises(InvalidRequestError):
            Context(client, {'id': 'session.context'}, token=UserScopeImpl('alice'))


def test_declared_concept_observable_and_observation_accessors_preserve_exact_values():
    concept = ConceptImpl('declared:Concept', raw={'contextualization': 'FUTURE_DECLARED_KIND'})
    observer = ConceptImpl('declared:Observer')
    assert concept.contextualization == 'FUTURE_DECLARED_KIND'
    assert concept.get_qualifier() is None and concept.get_notifications() == []
    observable = ObservableImpl(concept, observer_semantics=observer, optional=True,
                                default_value=0, resolution_directives=[ResolutionDirective.NODATA])
    assert observable.get_semantics() is concept and observable.get_observer_semantics() is observer
    assert observable.is_optional() is True and observable.get_default_value() == 0
    assert observable.get_resolution_directives() == [ResolutionDirective.NODATA]
    observation = ObservationImpl(urn='declared:observation', name='Declared label', observable=observable)
    assert observation.get_urn() == 'declared:observation' and observation.get_name() == 'Declared label'
    assert observation.get_observable() is observable and observation.get_notifications() == []
    assert ObservableImpl(concept).get_observer_semantics() is None
    assert ConceptImpl('declared:missing').contextualization is None


def test_local_modeler_session_and_scope_metadata_do_not_grant_remote_rights():
    user = UserScopeImpl('alice', roles={'DECLARED_READER'})
    session = SessionScopeImpl('alice', session_id='declared-session')
    context = ContextScopeImpl('alice', session_id='declared-session', context_id='declared-session.context',
                               observer='declared:Observer')
    modeler = ModelerImpl()
    assert modeler.get_open_context() is None
    modeler.open_user(user)
    modeler.open_session(session)
    modeler.open_context(context)
    assert user.get_roles() == {'DECLARED_READER'} and context.get_observer() == 'declared:Observer'
    assert modeler.session_scope is session and modeler.get_open_context() is context
    assert modeler.event_log == ['user:alice', 'session:declared-session', 'context:declared-session.context']
    assert ContextScopeImpl('alice').get_observer() is None


def test_runtime_connect_bridge_uses_existing_public_scope_contract_without_replay():
    calls = []
    def handler(request):
        calls.append(request)
        assert request.url.path == '/api/v1/connect' and request.method == 'POST'
        assert request.headers['authorization'] == 'runtime-secret'
        assert json.loads(request.content)['configuration']['id'] == 'session.context'
        return httpx.Response(200, json={'id': 'session.context', 'serviceId': 'runtime-id'})
    with make_client(handler) as client:
        context = client.runtime.connect_context({'id': 'session.context'})
        assert context.id == 'session.context' and context.client is client
    assert len(calls) == 1
    def lost(request):
        raise httpx.ReadError('Synthetic ambiguous connect', request=request)
    with make_client(lost) as client:
        with pytest.raises(SubmissionOutcomeUnknown):
            client.runtime.connect_context({'id': 'session.context'})
