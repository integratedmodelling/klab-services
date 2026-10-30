"""Bounded model/resource advertisements from one explicitly configured Resources service."""
from dataclasses import dataclass, field
from typing import Mapping

from ._inspection import bounds, freeze, html_summary, text
from .client import scope_token
from .dto import object_payload, required_text
from .errors import InvalidRequestError, ProtocolError, UnsupportedOperationError
from .transport import _normalize_service_url


@dataclass(frozen=True)
class InspectionOrigin:
    service_url: str
    service_id: str
    scope: str | None


@dataclass(frozen=True)
class AdvertisedObservable:
    urn: str | None
    formal_name: str | None
    unit: str | None
    optional: bool | None
    raw: Mapping = field(repr=False)


@dataclass(frozen=True)
class AssetInspection:
    urn: str
    knowledge_class: str
    origin: InspectionOrigin
    label: str | None
    advertised_observables: tuple[AdvertisedObservable, ...] | None
    listed_dependencies: tuple[AdvertisedObservable, ...] | None
    resource_urns: tuple | None
    version: object
    namespace: str | None
    project_name: str | None
    notifications: tuple
    provider_metadata: Mapping
    raw: Mapping = field(repr=False)
    dependency_completeness: str = "unknown: listed metadata only; not a dependency closure"
    selection: str = "advertised candidate; not evidence of Resolver selection or execution"

    def summary(self):
        dependencies = "unavailable" if self.listed_dependencies is None else str(len(self.listed_dependencies))
        return (f"{self.knowledge_class}: {self.urn}" + (f" — {self.label}" if self.label else "")
                + f"\nResources: {self.origin.service_id}\nListed dependencies: {dependencies}\n{self.selection}\n{self.dependency_completeness}")

    def _repr_html_(self):
        return html_summary(self.summary())


@dataclass(frozen=True)
class ResourceReference:
    urn: str
    service_id: str | None
    knowledge_class: str | None
    version: object
    raw: Mapping = field(repr=False)


@dataclass(frozen=True)
class ResourceResolution:
    requested_urn: str
    origin: InspectionOrigin
    results: tuple[ResourceReference, ...]
    services: Mapping
    raw: Mapping = field(repr=False)
    partial: bool = True

    def summary(self):
        return f"Resource resolution: {self.requested_urn}; {len(self.results)} listed results\nPartial metadata; no automatic provider routing or recursive dependency closure"

    def _repr_html_(self):
        return html_summary(self.summary())


def _optional_text(raw, key):
    value = raw.get(key)
    if value is not None and not isinstance(value, str):
        raise ProtocolError(f"Inspection {key} must be text or null")
    return value


class ResourceInspector:
    """Inspect only existing MODEL/RESOURCE list, retrieve and resolve operations.

    No arbitrary filters, global semantic search, model forcing or recursive loading.
    Returned provider descriptors remain data and cannot expand credential trust.
    """

    def __init__(self, client, scope=None, *, max_items=100, max_response_bytes=1024 * 1024):
        self.client, self.scope = client, scope
        self.max_items = bounds(max_items, 1000, "Inspection item budget")
        self.max_response_bytes = bounds(max_response_bytes, 16 * 1024 * 1024, "Response byte budget")

    def _origin(self):
        capabilities = self._call(self.client.resources.capabilities, self.scope)
        return InspectionOrigin(
            service_url=_normalize_service_url(self.client.transport.endpoint("resources").url),
            service_id=capabilities.service_id,
            scope=scope_token(self.scope),
        )

    def _call(self, operation, *args, **kwargs):
        try:
            return operation(*args, max_response_bytes=self.max_response_bytes, **kwargs)
        except InvalidRequestError as failure:
            if getattr(failure, "status_code", None) == 422:
                raise UnsupportedOperationError("This Resources service does not implement the requested operation") from None
            raise

    def _unchanged(self, origin):
        if self._origin() != origin:
            raise ProtocolError("Resources service/scope identity changed during inspection; refresh explicitly")

    def _rows(self, payload, label):
        if not isinstance(payload, list) or len(payload) > self.max_items:
            raise ProtocolError(f"{label} must be an array within the item budget; no server pagination is implied")
        return payload

    def _advertisements(self, payload, key):
        values = payload.get(key)
        if values is None:
            return None
        result = []
        for value in self._rows(values, key):
            value = object_payload(value, "advertised observable")
            raw = freeze(value, self.client.transport.redact)
            optional = raw.get("optional")
            if optional is not None and type(optional) is not bool:
                raise ProtocolError("Advertised optional must be boolean or null")
            result.append(AdvertisedObservable(
                urn=_optional_text(raw, "urn"),
                formal_name=_optional_text(raw, "formalName"),
                unit=_optional_text(raw, "unit"),
                optional=optional,
                raw=raw,
            ))
        return tuple(result)

    def _asset(self, asset, knowledge_class, origin):
        payload = asset.raw
        raw = freeze(payload, self.client.transport.redact)
        resources = payload.get("resourceUrns")
        if resources is not None:
            resources = freeze(self._rows(resources, "resourceUrns"), self.client.transport.redact)
        provider_metadata = {key: payload[key] for key in ("serviceId", "adapterType") if key in payload}
        return AssetInspection(
            urn=asset.urn,
            knowledge_class=knowledge_class,
            origin=origin,
            label=_optional_text(raw, "label"),
            advertised_observables=self._advertisements(payload, "observables"),
            listed_dependencies=self._advertisements(payload, "dependencies"),
            resource_urns=resources,
            version=raw.get("version"),
            namespace=_optional_text(raw, "namespace"),
            project_name=_optional_text(raw, "projectName"),
            notifications=raw.get("notifications", ()),
            provider_metadata=freeze(provider_metadata, self.client.transport.redact),
            raw=raw,
        )

    @staticmethod
    def _knowledge_class(value):
        if value not in ("MODEL", "RESOURCE"):
            raise UnsupportedOperationError("Inspector supports MODEL and RESOURCE only")
        return value

    def list(self, knowledge_class="MODEL"):
        """Return a bounded whole list; duplicates are retained, never silently collapsed."""
        knowledge_class = self._knowledge_class(knowledge_class)
        origin = self._origin()
        assets = self._call(self.client.resources.list, knowledge_class, self.scope)
        result = tuple(self._asset(asset, knowledge_class, origin) for asset in self._rows(assets, "asset list"))
        self._unchanged(origin)
        return result

    def retrieve(self, urn, knowledge_class="MODEL"):
        text(urn, "Asset URN")
        knowledge_class = self._knowledge_class(knowledge_class)
        origin = self._origin()
        asset = self._call(self.client.resources.retrieve, urn, knowledge_class, self.scope)
        if asset.urn != urn:
            raise ProtocolError("Resources returned an asset with a different requested identity")
        result = self._asset(asset, knowledge_class, origin)
        self._unchanged(origin)
        return result

    def resolve(self, urn, knowledge_class="RESOURCE"):
        text(urn, "Asset URN")
        knowledge_class = self._knowledge_class(knowledge_class)
        origin = self._origin()
        resource_set = self._call(self.client.resources.resolve, urn, self.scope, knowledge_class=knowledge_class)
        payload = resource_set.raw
        raw = freeze(payload, self.client.transport.redact)
        results = []
        for row in self._rows(payload["results"], "ResourceSet results"):
            value = freeze(row, self.client.transport.redact)
            results.append(ResourceReference(
                urn=required_text(row, "resourceUrn"),
                service_id=_optional_text(value, "serviceId"),
                knowledge_class=_optional_text(value, "knowledgeClass"),
                version=value.get("resourceVersion"),
                raw=value,
            ))
        services = payload.get("services", {})
        if not isinstance(services, dict) or any(not isinstance(url, str) for url in services.values()):
            raise ProtocolError("ResourceSet services must map service IDs to URL strings")
        self._unchanged(origin)
        return ResourceResolution(
            requested_urn=urn,
            origin=origin,
            results=tuple(results),
            services=freeze(services, self.client.transport.redact),
            raw=raw,
        )
