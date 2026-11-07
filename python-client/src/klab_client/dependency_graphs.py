"""Opt-in finite source-bound dependency graphs, not native closure authority."""
from dataclasses import dataclass, field
import hashlib
import re
from types import MappingProxyType
from typing import Mapping

from ._inspection import freeze
from .client import Client
from .errors import ConfigurationError, InvalidRequestError, ProtocolError, UnsupportedOperationError
from .shared_access import _SharedAccess
from .source_previews import SourcePreviews, AsyncSourcePreviews
from .transport import Endpoint

PROTOCOL='klab.experimental.dependency-graphs/1'
PREFIX='/experimental/dependencies/v1/graphs/'


@dataclass(frozen=True)
class DependencyNode:
    node_id: str
    urn: str
    knowledge_class: str
    provider_id: str
    version: str
    source_revision: int
    source: str = field(repr=False)
    source_sha256: str
    access: Mapping = field(repr=False)
    evidence: str='Server-reported finite reference source/access; not native or cryptographic authority'


@dataclass(frozen=True)
class DependencyEdge:
    source_id: str
    target_id: str
    kind: str


@dataclass(frozen=True)
class DependencyGraphSnapshot:
    context_id: str
    resources_id: str
    snapshot_id: str
    revision: int
    root_node_id: str
    nodes: tuple[DependencyNode,...]
    edges: tuple[DependencyEdge,...]
    providers: Mapping[str,str]
    evidence: str='REFERENCE_DECLARED_COMPLETE: finite source graph, not native closure/scientific/access authority'


def _integer(value,low,high,label):
    if type(value) is not int or not low<=value<=high:
        raise InvalidRequestError('Explicit bounded '+label+' required')
    return value


def _token(value):
    return isinstance(value,str) and re.fullmatch('[0-9a-f]{32}',value) is not None


class _Graphs(_SharedAccess):
    def _configure(self,context,allow_experimental,client_type,guard_type,max_nodes,max_edges,max_pages):
        self._bind(context,allow_experimental,client_type)
        self._guard=guard_type(context.client,max_response_bytes=1024*1024)
        self.max_nodes=_integer(max_nodes,1,256,'node budget')
        self.max_edges=_integer(max_edges,1,1024,'edge budget')
        self.max_pages=_integer(max_pages,1,64,'page budget')

    def _fence(self):
        self._options()
        if self.context.client is not self._guard._client or self.context.client.transport is not self._guard._transport:
            raise ProtocolError('Dependency graph client/transport changed; no implicit rebinding')

    def _input(self,urn,knowledge_class,page_size):
        if (not isinstance(urn,str) or re.fullmatch('[A-Za-z][A-Za-z0-9:_.-]{0,255}',urn) is None):
            raise InvalidRequestError('Choose one explicit bounded graph root, not a URL/path')
        if knowledge_class not in ('MODEL','RESOURCE','NAMESPACE'):
            raise UnsupportedOperationError('Unsupported reference dependency graph root class')
        _integer(page_size,1,32,'page size')
        self._public([urn,knowledge_class]);self._options()
        return knowledge_class+'/'+urn

    def _capability(self,before,capability):
        self._guard._after_identity(before,capability)
        self._fence()
        if capability.raw.get('dependencyGraphProtocol')!=PROTOCOL:
            raise UnsupportedOperationError('Resources does not advertise the exact dependency graph reference; no native fallback')

    def _node(self,row,revision):
        keys={'nodeId','urn','knowledgeClass','providerId','version','sourceRevision','source','sourceSha256','access'}
        if not isinstance(row,dict) or set(row)!=keys or not _token(row['nodeId']):
            raise ProtocolError('Exact source-bound dependency node required')
        if (any(not isinstance(row[k],str) or not row[k].strip() or len(row[k])>512 for k in ('urn','knowledgeClass','providerId','version'))
                or row['knowledgeClass'] not in ('MODEL','RESOURCE','NAMESPACE')):
            raise ProtocolError('Bounded exact node identity/provider/version required')
        version=row['version']
        if (re.fullmatch(r'(0|[1-9][0-9]{0,9})\.(0|[1-9][0-9]{0,9})\.(0|[1-9][0-9]{0,9})',version) is None
                or any(int(part)>2**31-1 for part in version.split('.'))):
            raise ProtocolError('Reference graph requires an exact bounded version triple, not latest/ranges')
        try:
            _integer(row['sourceRevision'],1,2**53-1,'source revision')
            source=row['source'].encode('utf-8') if isinstance(row['source'],str) else b''
        except InvalidRequestError:
            raise ProtocolError('Invalid source revision or UTF-8 node source') from None
        if not source or not row['source'].strip() or len(source)>16384 or hashlib.sha256(source).hexdigest()!=row['sourceSha256']:
            raise ProtocolError('Dependency source is missing, oversized or does not match its exact digest')
        access=row['access']
        if (not isinstance(access,dict) or set(access)!={'subject','contextId','right','revision'}
                or not isinstance(access['subject'],str) or not access['subject'].strip() or len(access['subject'])>128
                or access['contextId']!=self.reference.context_id or access['right']!='READ'
                or type(access['revision']) is not int or access['revision']!=revision):
            raise ProtocolError('Dependency node requires exact root/revision server-reported READ access')
        return DependencyNode(row['nodeId'],row['urn'],row['knowledgeClass'],row['providerId'],row['version'],
            row['sourceRevision'],row['source'],row['sourceSha256'],freeze(access))

    def _page(self,value,urn,knowledge_class,page_size,stamp):
        self._guard._safe(value)
        keys={'protocol','contextId','resourcesServiceId','requestedUrn','requestedClass','snapshotId','revision',
            'rootNodeId','nodeCount','edgeCount','nodes','edges','providers','complete','nextCursor'}
        if (not isinstance(value,dict) or set(value)!=keys or value['protocol']!=PROTOCOL
                or value['contextId']!=self.reference.context_id or value['resourcesServiceId']!=self._guard._binding[1]
                or value['requestedUrn']!=urn or value['requestedClass']!=knowledge_class or value['complete'] is not True
                or not _token(value['snapshotId']) or not _token(value['rootNodeId'])):
            raise ProtocolError('Graph page does not confirm exact root/Resources/request/snapshot/completeness')
        try:
            _integer(value['revision'],1,2**53-1,'graph revision')
            _integer(value['nodeCount'],1,self.max_nodes,'total nodes')
            _integer(value['edgeCount'],0,self.max_edges,'total edges')
        except InvalidRequestError: raise ProtocolError('Dependency graph exceeds exact revision/total budgets') from None
        providers=value['providers']
        if not isinstance(providers,dict) or len(providers)>self.max_nodes:
            raise ProtocolError('Bounded inert graph provider map required')
        for identity,url in providers.items():
            if not isinstance(identity,str) or not identity.strip() or len(identity)>512 or not isinstance(url,str):
                raise ProtocolError('Invalid dependency provider identity/URL')
            try: Endpoint(url)
            except (ConfigurationError,ValueError): raise ProtocolError('Unsafe dependency provider URL') from None
        current=(value['snapshotId'],value['revision'],value['rootNodeId'],value['nodeCount'],value['edgeCount'],providers)
        if stamp is not None and current!=stamp:
            raise ProtocolError('Dependency source/provider/revision snapshot changed between pages; no partial result')
        if (not isinstance(value['nodes'],list) or len(value['nodes'])>page_size
                or not isinstance(value['edges'],list) or len(value['edges'])>self.max_edges):
            raise ProtocolError('Graph page membership exceeds explicit budgets')
        cursor=value['nextCursor']
        if cursor is not None and not _token(cursor): raise ProtocolError('Graph continuation must be an opaque cursor, never a URL')
        nodes=tuple(self._node(row,value['revision']) for row in value['nodes'])
        edges=[]
        for row in value['edges']:
            if (not isinstance(row,dict) or set(row)!={'sourceId','targetId','kind'}
                    or not _token(row['sourceId']) or not _token(row['targetId']) or row['kind']!='DEPENDS_ON'):
                raise ProtocolError('Explicit typed dependency edge endpoints required')
            edges.append(DependencyEdge(row['sourceId'],row['targetId'],row['kind']))
        return current,nodes,tuple(edges),cursor

    def _finish(self,urn,knowledge_class,stamp,nodes,edges):
        snapshot,revision,root,count,edge_count,providers=stamp
        by_id={node.node_id:node for node in nodes}
        identities={(node.provider_id,node.knowledge_class,node.urn) for node in nodes}
        if (len(nodes)!=count or len(by_id)!=count or len(identities)!=count or len(edges)!=edge_count
                or len(set(edges))!=len(edges) or root not in by_id
                or by_id[root].urn!=urn or by_id[root].knowledge_class!=knowledge_class):
            raise ProtocolError('Graph root/total/duplicate identities do not form the exact declared snapshot')
        subjects={node.access['subject'] for node in nodes}
        if len(subjects)!=1 or any(node.provider_id not in providers for node in nodes):
            raise ProtocolError('Graph access subject/provider identity is missing or inconsistent')
        adjacency: dict[str,list[str]]={identity:[] for identity in by_id}
        for edge in edges:
            if edge.source_id not in by_id or edge.target_id not in by_id:
                raise ProtocolError('Dependency graph has missing edge endpoints; not a complete snapshot')
            adjacency[edge.source_id].append(edge.target_id)
        visiting=set();visited=set()
        def visit(identity):
            if identity in visiting: raise ProtocolError('Dependency cycle refused; no complete acyclic graph receipt')
            if identity in visited:return
            visiting.add(identity)
            for target in adjacency[identity]:visit(target)
            visiting.remove(identity);visited.add(identity)
        visit(root)
        if len(visited)!=len(nodes):raise ProtocolError('Graph contains unreachable/orphan nodes')
        return DependencyGraphSnapshot(self.reference.context_id,self._guard._binding[1],snapshot,revision,root,
            tuple(nodes),tuple(edges),MappingProxyType(dict(providers)))


class DependencyGraphs(_Graphs):
    def __init__(self,context,*,allow_experimental=False,max_nodes=64,max_edges=256,max_pages=16):
        self._configure(context,allow_experimental,Client,SourcePreviews,max_nodes,max_edges,max_pages)

    def _request(self,route,params):
        self._fence()
        self._public(route);self._public(params);options=self._options()
        before=self._guard._before_identity()
        self._capability(before,self.context.client.resources.capabilities(max_response_bytes=65536))
        value=self.context.client.transport.request('resources','GET',PREFIX+route,
            **{**options,'max_response_bytes':1024*1024},params=params)
        self._guard._before_identity()
        self._fence()
        self._guard._identity()
        return self._after_request(value,'GET')

    def inspect(self,urn,*,knowledge_class='MODEL',page_size=16):
        route=self._input(urn,knowledge_class,page_size)
        stamp=None;cursor=None;seen=set();nodes=[];edges=[]
        for _ in range(self.max_pages):
            params={'protocol':PROTOCOL,'pageSize':page_size}
            if stamp is not None:params.update(snapshot=stamp[0],cursor=cursor)
            stamp,new_nodes,new_edges,cursor=self._page(self._request(route,params),urn,knowledge_class,page_size,stamp)
            nodes.extend(new_nodes);edges.extend(new_edges)
            if len(nodes)>self.max_nodes or len(edges)>self.max_edges:raise ProtocolError('Graph aggregate budget exceeded')
            if cursor is None:return self._finish(urn,knowledge_class,stamp,nodes,edges)
            if cursor in seen:raise ProtocolError('Repeated graph cursor; no hidden pagination loop')
            seen.add(cursor)
        raise ProtocolError('Graph page budget exhausted; partial graph not returned')


class AsyncDependencyGraphs(_Graphs):
    def __init__(self,context,*,allow_experimental=False,max_nodes=64,max_edges=256,max_pages=16):
        from .async_client import AsyncClient
        self._configure(context,allow_experimental,AsyncClient,AsyncSourcePreviews,max_nodes,max_edges,max_pages)

    async def _request(self,route,params):
        self._fence()
        self._public(route);self._public(params);options=self._options()
        before=self._guard._before_identity()
        self._capability(before,await self.context.client.resources.capabilities(max_response_bytes=65536))
        value=await self.context.client.transport.request('resources','GET',PREFIX+route,
            **{**options,'max_response_bytes':1024*1024},params=params)
        self._guard._before_identity()
        self._fence()
        await self._guard._identity()
        return self._after_request(value,'GET')

    async def inspect(self,urn,*,knowledge_class='MODEL',page_size=16):
        route=self._input(urn,knowledge_class,page_size)
        stamp=None;cursor=None;seen=set();nodes=[];edges=[]
        for _ in range(self.max_pages):
            params={'protocol':PROTOCOL,'pageSize':page_size}
            if stamp is not None:params.update(snapshot=stamp[0],cursor=cursor)
            stamp,new_nodes,new_edges,cursor=self._page(await self._request(route,params),urn,knowledge_class,page_size,stamp)
            nodes.extend(new_nodes);edges.extend(new_edges)
            if len(nodes)>self.max_nodes or len(edges)>self.max_edges:raise ProtocolError('Graph aggregate budget exceeded')
            if cursor is None:return self._finish(urn,knowledge_class,stamp,nodes,edges)
            if cursor in seen:raise ProtocolError('Repeated graph cursor; no hidden pagination loop')
            seen.add(cursor)
        raise ProtocolError('Graph page budget exhausted; partial graph not returned')
