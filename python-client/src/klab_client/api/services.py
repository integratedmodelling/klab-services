from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, Generic, TypeVar

from klab_client.api.knowledge import Concept, Observable
from klab_client.api.primitives import (
    CRUDOperation,
    ContextInfo,
    Dataflow,
    KlabAsset,
    ResourceSet,
    RuntimeAsset,
    Scope,
    ServiceCapabilities,
)
from klab_client.api.runtime import Geometry, Observation
from klab_client.api.scopes import ContextScope, SessionScope, UserScope


TAsset = TypeVar("TAsset", bound=KlabAsset)
TRuntime = TypeVar("TRuntime", bound=RuntimeAsset)


class Reasoner(ABC):
    @abstractmethod
    def capabilities(self, scope: Scope | None) -> ServiceCapabilities: ...

    @abstractmethod
    def resolve_concept(self, definition: str, scope: Scope | None = None) -> Concept: ...

    @abstractmethod
    def resolve_observable(self, definition: str, scope: Scope | None = None) -> Observable: ...


class ResourcesService(ABC):
    @abstractmethod
    def capabilities(self, scope: Scope | None) -> ServiceCapabilities: ...

    @abstractmethod
    def retrieve(self, urn: str, asset_class: type[TAsset], scope: UserScope) -> TAsset | None: ...

    @abstractmethod
    def resolve(self, urn: str, scope: Scope) -> ResourceSet: ...

    @abstractmethod
    def contextualize(self, resource: Any, observation: Observation, geometry: Geometry, scope: Scope) -> Any: ...


class RuntimeService(ABC):
    @abstractmethod
    def capabilities(self, scope: Scope | None) -> ServiceCapabilities: ...

    @abstractmethod
    def submit(self, observation: Observation, scope: ContextScope) -> Observation: ...

    @abstractmethod
    def get_context_info(self, scope: Scope) -> list[ContextInfo]: ...

    @abstractmethod
    def connect_context(self, configuration: dict[str, Any], user_scope: UserScope) -> ContextScope | None: ...

    @abstractmethod
    def release_session(self, scope: SessionScope) -> bool: ...

    @abstractmethod
    def release_context(self, scope: ContextScope) -> bool: ...

    @abstractmethod
    def query_knowledge_graph(self, query: Any, scope: Scope) -> list[TRuntime]: ...


class Resolver(ABC):
    @abstractmethod
    def capabilities(self, scope: Scope | None) -> ServiceCapabilities: ...

    @abstractmethod
    def resolve(self, observation: Observation, context_scope: ContextScope) -> Dataflow: ...

    @abstractmethod
    def encode_dataflow(self, dataflow: Dataflow) -> str: ...

    @abstractmethod
    def submit_resource(self, observation: Observation, context_scope: ContextScope) -> Any: ...

    @abstractmethod
    def get_submitted_resources(self, scope: ContextScope) -> list[Any]: ...


@dataclass(slots=True)
class ReasonerImpl(Reasoner):
    service_id: str = "reasoner"

    def capabilities(self, scope: Scope | None) -> ServiceCapabilities:
        return ServiceCapabilities(service_id=self.service_id)

    def resolve_concept(self, definition: str, scope: Scope | None = None) -> Concept:
        from klab_client.api.knowledge import ConceptImpl

        return ConceptImpl(urn=definition)

    def resolve_observable(self, definition: str, scope: Scope | None = None) -> Observable:
        from klab_client.api.knowledge import ObservableImpl

        return ObservableImpl(semantics=self.resolve_concept(definition, scope=scope))


@dataclass(slots=True)
class ResourcesServiceImpl(ResourcesService):
    service_id: str = "resources"
    catalog: dict[str, KlabAsset] = field(default_factory=dict)

    def capabilities(self, scope: Scope | None) -> ServiceCapabilities:
        return ServiceCapabilities(service_id=self.service_id)

    def retrieve(self, urn: str, asset_class: type[TAsset], scope: UserScope) -> TAsset | None:
        asset = self.catalog.get(urn)
        if asset is not None and isinstance(asset, asset_class):
            return asset
        return None

    def resolve(self, urn: str, scope: Scope) -> ResourceSet:
        return ResourceSet(urns=[urn])

    def contextualize(self, resource: Any, observation: Observation, geometry: Geometry, scope: Scope) -> Any:
        return {
            "resource": resource,
            "observation": observation.get_urn(),
            "geometry": geometry.dimension(),
            "scope": scope.id,
        }


@dataclass(slots=True)
class RuntimeServiceImpl(RuntimeService):
    service_id: str = "runtime"
    sessions: dict[str, list[str]] = field(default_factory=dict)

    def capabilities(self, scope: Scope | None) -> ServiceCapabilities:
        return ServiceCapabilities(service_id=self.service_id)

    def submit(self, observation: Observation, scope: ContextScope) -> Observation:
        self.sessions.setdefault(scope.get_session_id(), []).append(scope.get_context_id())
        return observation

    def get_context_info(self, scope: Scope) -> list[ContextInfo]:
        return [ContextInfo(session_id=s, context_ids=list(dict.fromkeys(c))) for s, c in self.sessions.items()]

    def connect_context(self, configuration: dict[str, Any], user_scope: UserScope) -> ContextScope | None:
        from klab_client.api.scopes import ContextScopeImpl

        session_id = configuration.get("session_id", "")
        context_id = configuration.get("context_id", "")
        return ContextScopeImpl(
            user_id=user_scope.get_user_id(),
            roles=set(user_scope.get_roles()),
            session_id=session_id,
            context_id=context_id,
        )

    def release_session(self, scope: SessionScope) -> bool:
        return self.sessions.pop(scope.get_session_id(), None) is not None

    def release_context(self, scope: ContextScope) -> bool:
        contexts = self.sessions.get(scope.get_session_id(), [])
        if scope.get_context_id() in contexts:
            contexts[:] = [c for c in contexts if c != scope.get_context_id()]
            return True
        return False

    def query_knowledge_graph(self, query: Any, scope: Scope) -> list[TRuntime]:
        return []


@dataclass(slots=True)
class ResolverImpl(Resolver):
    service_id: str = "resolver"
    submitted_resources: dict[str, list[Any]] = field(default_factory=dict)

    def capabilities(self, scope: Scope | None) -> ServiceCapabilities:
        return ServiceCapabilities(service_id=self.service_id)

    def resolve(self, observation: Observation, context_scope: ContextScope) -> Dataflow:
        return Dataflow(urn=f"df:{observation.get_urn()}:{context_scope.get_context_id()}")

    def encode_dataflow(self, dataflow: Dataflow) -> str:
        return dataflow.urn

    def submit_resource(self, observation: Observation, context_scope: ContextScope) -> Any:
        resource = {"observation": observation.get_urn(), "context": context_scope.get_context_id()}
        self.submitted_resources.setdefault(context_scope.get_context_id(), []).append(resource)
        return resource

    def get_submitted_resources(self, scope: ContextScope) -> list[Any]:
        return self.submitted_resources.get(scope.get_context_id(), [])
