from klab_client import (
    ConceptImpl,
    ContextScopeImpl,
    ModelerImpl,
    ObservableImpl,
    ObservationImpl,
    ReasonerImpl,
    ResolverImpl,
    ResourcesServiceImpl,
    RuntimeServiceImpl,
    UserScopeImpl,
)
from klab_client.api.primitives import Scope


def test_local_scope_tracking() -> None:
    user = UserScopeImpl(user_id="u1", roles={"user"})
    context = ContextScopeImpl(user_id="u1", session_id="s1", context_id="s1.c1")

    modeler = ModelerImpl()
    modeler.open_user(user)
    modeler.open_context(context)
    assert modeler.get_open_context() is context


def test_concept_collective_roundtrip() -> None:
    c = ConceptImpl(urn="im:Thing")
    assert c.collective().is_collective()
    assert not c.collective().singular().is_collective()
