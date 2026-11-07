"""Bounded native ResourceSet dependency membership, not an edge-complete graph.

The native API documents closure, but inspected implementations leave some asset
dependencies unresolved and do not transmit edges. Never invent those guarantees.
"""
from dataclasses import dataclass, field
from typing import Mapping
from types import MappingProxyType

from ._inspection import bounds, freeze, text
from .client import scope_token
from .dto import check_notifications
from .errors import ConfigurationError, InvalidRequestError, ProtocolError, UnsupportedOperationError
from .source_previews import SourcePreviews, AsyncSourcePreviews
from .transport import Endpoint

GROUPS = ('namespaces', 'ontologies', 'behaviors', 'resources', 'observationStrategies', 'projects')
CLASSES = ('NAMESPACE', 'ONTOLOGY', 'BEHAVIOR', 'RESOURCE', 'OBSERVATION_STRATEGY_DOCUMENT', 'PROJECT', 'MODEL', 'DEFINITION')


@dataclass(frozen=True)
class DependencyMember:
    urn: str
    knowledge_class: str
    service_id: str
    version: object
    timestamp: int | None
    optional: bool | None
    project_urn: str | None
    metadata: Mapping = field(repr=False)
    access: str = 'unknown: descriptor availability is not a permission lease'


@dataclass(frozen=True)
class DependencySnapshot:
    requested_urn: str
    requested_class: str
    resources_url: str
    resources_id: str
    scope: str | None
    results: tuple[DependencyMember, ...]
    groups: Mapping[str, tuple[DependencyMember, ...] | None]
    services: Mapping[str, str]
    notifications: tuple
    closure_completeness: str = 'UNKNOWN: native closure documentation exceeds inspected implementation guarantees'
    edge_completeness: str = 'UNAVAILABLE: ResourceSet does not transmit dependency edges'
    currentness: str = 'Historical server report; versions are claims, not atomic snapshot or access leases'


class DependencyInspector:
    """Read all advertised dependency groups from one selected Resources service.

    No recursive fetches, server URL following, provider credential forwarding,
    implicit compilation, graph loading or source/knowledge mutation.
    """
    def __init__(self, client, scope=None, *, max_items=100, max_response_bytes=1024 * 1024):
        self._guard = SourcePreviews(client, max_response_bytes=max_response_bytes)
        self._configure(scope, max_items)

    def _configure(self, scope, max_items):
        self.scope = scope
        self.max_items = bounds(max_items, 1000, 'Dependency membership item budget')
        self._scope = scope_token(scope)

    def _input(self, urn, knowledge_class):
        text(urn, 'Dependency asset URN')
        if len(urn) > 512 or ',' in urn:
            raise InvalidRequestError('Inspect one explicit bounded asset, not an implicit multi-root closure')
        if not isinstance(knowledge_class, str) or knowledge_class not in CLASSES:
            raise UnsupportedOperationError('Unsupported dependency root knowledge class')
        if scope_token(self.scope) != self._scope:
            raise ProtocolError('Dependency scope changed; create a new explicit inspector')
        self._guard._safe([urn, knowledge_class, self._scope])

    def _member(self, row):
        if not isinstance(row, dict): raise ProtocolError('Dependency member must be an object')
        for key in ('resourceUrn', 'knowledgeClass', 'serviceId'):
            if not isinstance(row.get(key), str) or not row[key].strip() or len(row[key]) > 512:
                raise ProtocolError('Dependency member requires explicit bounded identity/class/provider')
        timestamp = row.get('timestamp')
        if timestamp is not None and (type(timestamp) is not int or not 0 <= timestamp < 2**63):
            raise ProtocolError('Dependency timestamp must be a nonnegative Java-long integer or null')
        optional = row.get('optional')
        if optional is not None and type(optional) is not bool:
            raise ProtocolError('Dependency optional flag must be boolean or null')
        project = row.get('projectUrn')
        if project is not None and (not isinstance(project, str) or len(project) > 512):
            raise ProtocolError('Dependency project identity must be bounded text or null')
        metadata = row.get('metadata', {})
        if not isinstance(metadata, dict): raise ProtocolError('Dependency metadata must be an object')
        return DependencyMember(row['resourceUrn'], row['knowledgeClass'], row['serviceId'],
            freeze(row.get('resourceVersion')), timestamp, optional, project, freeze(metadata))

    def _snapshot(self, payload, urn, knowledge_class, identity):
        try: self._guard._safe(payload)
        except InvalidRequestError:
            raise ProtocolError('Dependency response contains configured credential material') from None
        # Public Resources.resolve already requires a ResourceSet JSON object.
        check_notifications(payload, self._guard._transport.redact)
        count = 0
        def members(key, required=False):
            nonlocal count
            rows = payload.get(key)
            if rows is None and not required: return None
            if not isinstance(rows, list): raise ProtocolError('Dependency membership must use explicit arrays')
            count += len(rows)
            if count > self.max_items: raise ProtocolError('Dependency total membership budget exceeded; no truncation')
            converted = tuple(self._member(row) for row in rows)
            identities = [(item.service_id, item.knowledge_class, item.urn) for item in converted]
            if len(set(identities)) != len(identities):
                raise ProtocolError('Duplicate dependency member identity within group')
            return converted
        results = members('results', required=True)
        if any(item.urn != urn or item.knowledge_class != knowledge_class for item in results):
            raise ProtocolError('Dependency results do not identify the explicitly requested asset')
        groups = {key: members(key) for key in GROUPS}
        seen: dict[tuple[str, str, str], DependencyMember] = {}
        for item in (*results, *(member for group in groups.values() if group is not None for member in group)):
            key = (item.service_id, item.knowledge_class, item.urn)
            if (key in seen and seen[key].version is not None and item.version is not None
                    and seen[key].version != item.version):
                raise ProtocolError('Dependency identity has conflicting known version claims across groups')
            seen[key] = item
        services = payload.get('services', {})
        if not isinstance(services, dict): raise ProtocolError('Dependency providers must be an inert service map')
        for service_id, url in services.items():
            if not isinstance(service_id, str) or not service_id.strip() or len(service_id) > 512 or not isinstance(url, str):
                raise ProtocolError('Dependency provider identity/URL must be bounded text')
            # Validate, never route. URLs with embedded credentials are refused.
            try: Endpoint(url)
            except (ConfigurationError, ValueError):
                raise ProtocolError('Unsafe dependency provider URL') from None
        notifications = payload.get('notifications', [])
        # check_notifications above already validates this array and its entries.
        return DependencySnapshot(urn, knowledge_class, identity[0], identity[1], self._scope,
            results, MappingProxyType(groups), freeze(services), freeze(notifications))

    def inspect(self, urn, *, knowledge_class='RESOURCE'):
        self._input(urn, knowledge_class)
        identity = self._guard._identity()
        resource_set = self._guard._client.resources.resolve(urn, self.scope, knowledge_class=knowledge_class,
            max_response_bytes=self._guard._max_response)
        result = self._snapshot(resource_set.raw, urn, knowledge_class, identity)
        self._guard._identity()
        self._input(urn, knowledge_class)
        return result


class AsyncDependencyInspector(DependencyInspector):
    """Native awaited bounded membership inspection; not sync offloading."""
    def __init__(self, client, scope=None, *, max_items=100, max_response_bytes=1024 * 1024):
        self._guard = AsyncSourcePreviews(client, max_response_bytes=max_response_bytes)
        self._configure(scope, max_items)

    async def inspect(self, urn, *, knowledge_class='RESOURCE'):
        self._input(urn, knowledge_class)
        identity = await self._guard._identity()
        resource_set = await self._guard._client.resources.resolve(urn, self.scope, knowledge_class=knowledge_class,
            max_response_bytes=self._guard._max_response)
        result = self._snapshot(resource_set.raw, urn, knowledge_class, identity)
        await self._guard._identity()
        self._input(urn, knowledge_class)
        return result
