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


def test_basic_client_workflow() -> None:
    user = UserScopeImpl(user_id="u1", roles={"user"})
    runtime = RuntimeServiceImpl()
    reasoner = ReasonerImpl()
    resolver = ResolverImpl()
    resources = ResourcesServiceImpl()

    concept = reasoner.resolve_concept("im:Concept")
    observable = ObservableImpl(semantics=concept)
    observation = ObservationImpl(urn="obs:1", observable=observable)

    context = runtime.connect_context({"session_id": "s1", "context_id": "c1"}, user)
    assert context is not None
    runtime.submit(observation, context)
    df = resolver.resolve(observation, context)

    assert resolver.encode_dataflow(df).startswith("df:")
    assert resources.resolve("urn:resource", Scope(id="public")).urns == ["urn:resource"]

    modeler = ModelerImpl()
    modeler.open_user(user)
    modeler.open_context(context)
    assert modeler.get_open_context() is context


def test_concept_collective_roundtrip() -> None:
    c = ConceptImpl(urn="im:Thing")
    assert c.collective().is_collective()
    assert not c.collective().singular().is_collective()
