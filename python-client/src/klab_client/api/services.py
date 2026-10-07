from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any, TYPE_CHECKING, TypeVar

from klab_client.api.knowledge import Concept, Observable
from klab_client.api.primitives import (
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

if TYPE_CHECKING:
    from klab_client.client import Job


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
    def retrieve(self, urn: str, asset_class: str | type[KlabAsset] = "RESOURCE", scope: UserScope | None = None) -> KlabAsset: ...

    @abstractmethod
    def resolve(self, urn: str, scope: Scope) -> ResourceSet: ...

    @abstractmethod
    def contextualize(self, resource: Any, observation: Observation, geometry: Geometry, scope: Scope) -> Any: ...


class RuntimeService(ABC):
    @abstractmethod
    def capabilities(self, scope: Scope | None) -> ServiceCapabilities: ...

    @abstractmethod
    def submit(self, observation: Observation, scope: ContextScope) -> Job: ...

    @abstractmethod
    def get_context_info(self, scope: Scope) -> list[ContextInfo]: ...

    @abstractmethod
    def connect_context(self, configuration: dict[str, Any], user_scope: UserScope) -> ContextScope | None: ...

    @abstractmethod
    def release_session(self, scope: SessionScope) -> bool: ...

    @abstractmethod
    def release_context(self, scope: ContextScope) -> bool: ...

    @abstractmethod
    def query_knowledge_graph(self, query: dict[str, Any], scope: Scope) -> list[dict[str, Any]]: ...


class Resolver(ABC):
    @abstractmethod
    def capabilities(self, scope: Scope | None) -> ServiceCapabilities: ...

    @abstractmethod
    def resolve(self, observation: Observation, context_scope: ContextScope) -> Job: ...

    @abstractmethod
    def encode_dataflow(self, dataflow: Dataflow) -> str: ...

    @abstractmethod
    def submit_resource(self, observation: Observation, context_scope: ContextScope) -> Any: ...

    @abstractmethod
    def get_submitted_resources(self, scope: ContextScope) -> list[Any]: ...


class _RemoteService:
    service: str

    def __init__(self, client=None):
        self.client = client

    def _require_client(self):
        from klab_client.errors import ConfigurationError
        if self.client is None:
            raise ConfigurationError("Construct service implementations through Client with explicit endpoints")
        return self.client

    def _request(self, method, route, scope=None, **kwargs):
        from klab_client.client import scope_token
        self._require_client()
        return self.client.transport.request(self.service, method, route, scope=scope_token(scope),
                                             service_id=self.client.runtime_service_id, **kwargs)

    def _checked(self, payload, label):
        from klab_client.dto import check_notifications, object_payload
        payload = object_payload(payload, label)
        check_notifications(payload, self.client.transport.redact)
        return payload

    def capabilities(self, scope: Scope | None = None) -> ServiceCapabilities:
        """Return the server ID and complete checked capabilities payload."""
        from klab_client.dto import required_text
        payload = self._checked(self._request("GET", "/public/capabilities", scope), "capabilities")
        return ServiceCapabilities(required_text(payload, "serviceId"), payload)


class ReasonerImpl(_RemoteService, Reasoner):
    service = "reasoner"

    def resolve_concept(self, definition: str, scope: Scope | None = None) -> Concept:
        from klab_client.dto import concept_from_wire
        return concept_from_wire(self._checked(self._request("POST", "/api/v1/resolve/concept", scope,
                                                             text=definition), "concept"))

    def resolve_observable(self, definition: str, scope: Scope | None = None) -> Observable:
        from klab_client.dto import observable_from_wire, check_notifications
        from klab_client.errors import MissingAssetError
        payload = self._checked(self._request("POST", "/api/v1/resolve/observable", scope,
                                              text=definition), "observable")
        check_notifications(payload.get("semantics", {}), self.client.transport.redact)
        observable = observable_from_wire(payload)
        if (payload.get("artifactType") == "VOID"
                or observable.semantics.urn == "owl:Nothing"
                or "NOTHING" in payload["semantics"].get("type", [])):
            raise MissingAssetError("Semantic definition did not resolve in the configured worldview; inspect the loaded projects")
        return observable


class ResourcesServiceImpl(_RemoteService, ResourcesService):
    service = "resources"

    @staticmethod
    def _knowledge_class(asset_class):
        from klab_client.errors import UnsupportedOperationError
        if isinstance(asset_class, str) and asset_class in {
            "RESOURCE", "MODEL", "NAMESPACE", "ONTOLOGY", "PROJECT", "WORKSPACE", "BEHAVIOR",
            "OBSERVATION_STRATEGY_DOCUMENT", "WORKFLOW", "FLOW"}:
            return asset_class
        if asset_class is KlabAsset:
            return "RESOURCE"
        raise UnsupportedOperationError("Pass a supported Java KnowledgeClass enum name (e.g. RESOURCE)")

    def retrieve(self, urn: str, asset_class: str | type[KlabAsset] = "RESOURCE", scope: UserScope | None = None) -> KlabAsset:
        """Retrieve an explicit knowledge class as a URN plus retained wire DTO."""
        from urllib.parse import quote
        from klab_client.dto import required_text
        from klab_client.errors import MissingAssetError
        knowledge_class = self._knowledge_class(asset_class)
        payload = self._request("GET", f"/api/v1/retrieve/{knowledge_class}/{quote(urn, safe='')}", scope)
        if payload is None:
            raise MissingAssetError("Resource service returned no asset")
        payload = self._checked(payload, "asset")
        return KlabAsset(required_text(payload, "urn"), payload)

    def list(self, knowledge_class: str = "RESOURCE", scope: UserScope | None = None) -> list[KlabAsset]:
        from klab_client.dto import required_text
        from klab_client.errors import ProtocolError
        payload = self._request("GET", f"/api/v1/list/{self._knowledge_class(knowledge_class)}", scope)
        if not isinstance(payload, list):
            raise ProtocolError("Asset list must be a JSON array")
        return [KlabAsset(required_text(self._checked(p, "asset"), "urn"), p) for p in payload]

    def resolve(self, urn: str, scope: Scope | None = None, *, knowledge_class: str = "RESOURCE") -> ResourceSet:
        from urllib.parse import quote
        from klab_client.dto import required_text, object_payload
        from klab_client.errors import ProtocolError
        payload = self._checked(self._request("GET", f"/api/v1/resolve/{self._knowledge_class(knowledge_class)}/{quote(urn, safe='')}", scope), "resource set")
        results = payload.get("results")
        if not isinstance(results, list):
            raise ProtocolError("ResourceSet results must be a list")
        return ResourceSet([required_text(object_payload(p, "resource descriptor"), "resourceUrn") for p in results], payload)

    def contextualize(self, resource, observation, geometry, scope):
        from klab_client.errors import UnsupportedOperationError
        raise UnsupportedOperationError("Resource contextualization requires binary Avro worker input; use Runtime.submit")


class RuntimeServiceImpl(_RemoteService, RuntimeService):
    service = "runtime"

    def submit(self, observation: Observation, scope: ContextScope) -> Job:
        from klab_client.errors import ConfigurationError
        if self.client is None:
            raise ConfigurationError("Construct runtime through Client")
        return self.client._submit(observation, scope)

    def get_context_info(self, scope: Scope | None = None) -> list[ContextInfo]:
        from klab_client.dto import object_payload, required_text
        from klab_client.errors import ProtocolError
        payload = self._request("GET", "/api/v1/contexts", scope)
        if not isinstance(payload, list):
            raise ProtocolError("Context info must be a JSON array")
        result = []
        for item in payload:
            item = self._checked(item, "context info")
            id = required_text(object_payload(item.get("configuration"), "configuration"), "id")
            result.append(ContextInfo(id.split(".")[0], [id], item))
        return result

    def connect_context(self, configuration: dict[str, Any], user_scope: UserScope | None = None) -> ContextScope:
        self._require_client()
        payload = self._request("POST", "/api/v1/connect", user_scope,
                                json=self.client._scope_request(configuration), ambiguous=True)
        return self.client._context(payload)

    def _release(self, route, scope):
        from klab_client.errors import ProtocolError
        result = self._request("GET", route, scope, ambiguous=True)
        if type(result) is not bool:
            raise ProtocolError("Release must return a boolean")
        return result

    def release_session(self, scope: SessionScope) -> bool:
        from klab_client.client import scope_token
        self._check_release_binding(scope)
        return self._release("/releaseSession", scope_token(scope).split(".")[0])

    def release_context(self, scope: ContextScope) -> bool:
        from klab_client.client import scope_token
        self._check_release_binding(scope)
        # Release the root context, never dispose an arbitrary focused observation.
        return self._release("/releaseContext", ".".join(scope_token(scope).split("#")[0].split(".")[:2]))

    def _check_release_binding(self, scope):
        """Bound handles must refer to this Runtime before destructive HTTP."""
        from klab_client.errors import InvalidRequestError
        self._require_client()
        bound = getattr(scope, "client", None)
        if bound is not None and (
                bound.transport.endpoint("runtime").url.rstrip("/")
                != self.client.transport.endpoint("runtime").url.rstrip("/")):
            raise InvalidRequestError("Release scope belongs to a different runtime")

    def query_knowledge_graph(self, query: dict[str, Any], scope: Scope) -> list[dict[str, Any]]:
        from klab_client.errors import ProtocolError, UnsupportedOperationError
        if not isinstance(query, dict):
            raise UnsupportedOperationError("Knowledge graph queries require a structured wire DTO, not query text")
        payload = self._request("POST", "/api/v1/query", scope, json=query)
        if not isinstance(payload, list):
            raise ProtocolError("Knowledge graph query must return an array")
        return [self._checked(p, "graph asset") for p in payload]


class ResolverImpl(_RemoteService, Resolver):
    service = "resolver"

    def resolve(self, observation: Observation, context_scope: ContextScope) -> Job:
        from klab_client.dto import object_payload
        self._require_client()
        return self.client._submit(observation, context_scope, service="resolver",
                                   decoder=lambda p: object_payload(p, "dataflow"))

    def encode_dataflow(self, dataflow):
        from klab_client.errors import UnsupportedOperationError
        raise UnsupportedOperationError("Dataflow encoding has no implemented Java client contract")

    def submit_resource(self, observation, context_scope):
        from klab_client.dto import observation_to_wire
        return self._checked(self._request("POST", "/api/v1/resource", context_scope,
                                           json=observation_to_wire(observation), ambiguous=True), "resource")

    def get_submitted_resources(self, scope):
        from klab_client.errors import ProtocolError
        payload = self._request("GET", "/api/v1/resources", scope)
        if not isinstance(payload, list):
            raise ProtocolError("Submitted resources must be an array")
        return [self._checked(p, "resource") for p in payload]
