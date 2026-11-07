"""Opt-in finite graph checkpoint/delta reference, never native science commits."""
from dataclasses import dataclass,field
import hashlib,json,re
from threading import RLock
from typing import Mapping

from ._inspection import freeze,_reject_credential_leaves
from .client import Client,Job,scope_token
from .errors import InvalidRequestError,KlabError,ProtocolError,ServerError,SubmissionOutcomeUnknown,UnsupportedOperationError
from .evidence import _plain
from .shared_access import _SharedAccess

PROTOCOL='klab.experimental.graph-commits/1'
PREFIX='/experimental/graph-commits/v1/'
KIND='REFERENCE_COMPLETE_FINITE_GRAPH_NOT_NATIVE_SCIENTIFIC_COMMIT'


@dataclass(frozen=True)
class ReferenceGraphSnapshot:
    context_id:str
    runtime_id:str
    epoch:str
    revision:int
    digest:str
    nodes:tuple[Mapping,...]=field(repr=False)
    edges:tuple[Mapping,...]=field(repr=False)
    evidence:str=KIND


def _revision(value):
    return type(value) is int and 0<=value<2**53


def _digest(nodes,edges):
    body={'nodes':sorted((_plain(n) for n in nodes),key=lambda n:n['id']),
        'edges':sorted((_plain(e) for e in edges),key=lambda e:(e['sourceId'],e['targetId'],e['relationship']))}
    try:encoded=json.dumps(body,sort_keys=True,ensure_ascii=False,allow_nan=False,separators=(',',':')).encode()
    except (ValueError,TypeError,UnicodeError,RecursionError):
        raise ProtocolError('Reference graph is not bounded finite UTF8 JSON data') from None
    if len(encoded)>2*1024*1024:raise ProtocolError('Aggregate retained reference graph exceeds2MiB JSON budget')
    return hashlib.sha256(encoded).hexdigest()


class _Reference(_SharedAccess):
    def _configure(self,context,allow_experimental,client_type):
        self._bind(context,allow_experimental,client_type)
        self._client=context.client;self._transport=context.client.transport
        self._runtime=self._transport.endpoint('runtime')

    def _fence(self):
        try:self._options()
        except InvalidRequestError:raise ProtocolError('Reference root/Runtime binding changed; no result accepted') from None
        if self.context.client is not self._client or self._client.transport is not self._transport or self._transport.endpoint('runtime')!=self._runtime:
            raise ProtocolError('Reference graph client/transport/endpoint/credential changed; no implicit rebinding')

    def _capability(self,capability):
        if capability.service_id!=self.reference.runtime_id:
            raise ProtocolError('Reference graph capability does not confirm exact Runtime')
        self._fence()
        if capability.raw.get('graphCommitProtocol')!=PROTOCOL:
            raise UnsupportedOperationError('Runtime does not advertise exact graph-commit reference; no native fallback')

    def _safe(self,value):
        freeze(value,self._transport.redact)
        try:_reject_credential_leaves(value,self._transport.redact,'Reference graph contains configured credential material')
        except InvalidRequestError:raise ProtocolError('Reference graph contains configured credential material') from None

    def _graph(self,nodes,edges,digest):
        if not isinstance(nodes,list) or not isinstance(edges,list) or len(nodes)>256 or len(edges)>1024:
            raise ProtocolError('Complete finite reference graph exceeds explicit membership budgets')
        by_id={}
        for node in nodes:
            if (not isinstance(node,dict) or set(node)!={'id','kind','properties'}
                    or not isinstance(node['id'],str) or re.fullmatch('[a-z][a-z0-9:_-]{0,127}',node['id']) is None
                    or node['kind'] not in ('CONTEXT','OBSERVATION','RECORDED_ACTIVITY') or not isinstance(node['properties'],dict)
                    or node['id'] in by_id):
                raise ProtocolError('Graph node identity/kind/properties invalid or duplicated')
            by_id[node['id']]=node
        contexts=[n for n in nodes if n['kind']=='CONTEXT']
        if len(contexts)!=1 or contexts[0]['id']!='context' or contexts[0]['properties'].get('contextId')!=self.reference.context_id:
            raise ProtocolError('Complete finite graph must name exactly its explicit reference context')
        seen=set()
        for edge in edges:
            if (not isinstance(edge,dict) or set(edge)!={'sourceId','targetId','relationship'}
                    or not isinstance(edge['sourceId'],str) or not isinstance(edge['targetId'],str)
                    or edge['sourceId'] not in by_id or edge['targetId'] not in by_id
                    or edge['relationship'] not in ('CONTAINS','PRODUCED')):
                raise ProtocolError('Graph edge has missing endpoints or unsupported declared relationship')
            key=(edge['sourceId'],edge['targetId'],edge['relationship'])
            if key in seen:raise ProtocolError('Reference graph edge duplicated')
            source_kind,target_kind=by_id[edge['sourceId']]['kind'],by_id[edge['targetId']]['kind']
            if ((edge['relationship']=='CONTAINS' and (source_kind!='CONTEXT' or target_kind=='CONTEXT'))
                    or (edge['relationship']=='PRODUCED' and (source_kind!='RECORDED_ACTIVITY' or target_kind!='OBSERVATION'))):
                raise ProtocolError('Reference edge does not bind its declared context/activity/observation kinds')
            seen.add(key)
        reachable={'context'}
        while True:
            expanded=reachable | {edge['targetId'] for edge in edges if edge['sourceId'] in reachable}
            if expanded==reachable:break
            reachable=expanded
        if reachable!=set(by_id):raise ProtocolError('Reference complete graph contains unreachable/orphan nodes')
        if (not isinstance(digest,str) or re.fullmatch('[0-9a-f]{64}',digest) is None
                or _digest(nodes,edges)!=digest):
            raise ProtocolError('Reference complete graph does not match its canonical digest')
        return (tuple(freeze(node) for node in sorted(nodes,key=lambda node:node['id'])),
            tuple(freeze(edge) for edge in sorted(edges,key=lambda edge:(edge['sourceId'],edge['targetId'],edge['relationship']))))

    def _snapshot(self,value):
        self._safe(value)
        keys={'protocol','kind','contextId','runtimeId','epoch','revision','digest','nodes','edges','complete'}
        if (not isinstance(value,dict) or set(value)!=keys or value['protocol']!=PROTOCOL or value['kind']!=KIND
                or value['contextId']!=self.reference.context_id or value['runtimeId']!=self.reference.runtime_id
                or not isinstance(value['epoch'],str) or re.fullmatch('[0-9a-f]{32}',value['epoch']) is None
                or not _revision(value['revision']) or value['complete'] is not True):
            raise ProtocolError('Snapshot does not confirm exact finite reference scope/Runtime/epoch/revision/completeness')
        nodes,edges=self._graph(value['nodes'],value['edges'],value['digest'])
        return ReferenceGraphSnapshot(value['contextId'],value['runtimeId'],value['epoch'],value['revision'],value['digest'],nodes,edges)

    def _base(self,base):
        self._fence()
        if (not isinstance(base,ReferenceGraphSnapshot) or base.context_id!=self.reference.context_id
                or base.runtime_id!=self.reference.runtime_id or not _revision(base.revision)
                or not isinstance(base.epoch,str) or re.fullmatch('[0-9a-f]{32}',base.epoch) is None
                or not isinstance(base.nodes,tuple) or not isinstance(base.edges,tuple)
                or len(base.nodes)>256 or len(base.edges)>1024):
            raise InvalidRequestError('Choose a complete reference snapshot for the exact current root/Runtime')
        value={'nodes':[_plain(n) for n in base.nodes],'edges':[_plain(e) for e in base.edges],'digest':base.digest}
        self._safe(value);self._graph(value['nodes'],value['edges'],base.digest)

    def _apply(self,base,value):
        self._safe(value)
        if isinstance(value,dict) and isinstance(value.get('epoch'),str) and value['epoch']!=base.epoch:
            raise ReferenceResyncRequired('Reference epoch changed; explicit full resync required')
        keys={'protocol','contextId','runtimeId','epoch','baseRevision','baseDigest','headRevision','headDigest','deltas','complete'}
        if (not isinstance(base,ReferenceGraphSnapshot) or base.context_id!=self.reference.context_id or base.runtime_id!=self.reference.runtime_id
                or not isinstance(value,dict) or set(value)!=keys or value['protocol']!=PROTOCOL or value['contextId']!=base.context_id
                or value['runtimeId']!=base.runtime_id or value['epoch']!=base.epoch or value['baseRevision']!=base.revision
                or value['baseDigest']!=base.digest or not _revision(value['baseRevision']) or not _revision(value['headRevision'])
                or value['complete'] is not True or not isinstance(value['deltas'],list) or len(value['deltas'])>8):
            raise ProtocolError('Reference delta chain is not pinned to exact accepted base; explicit resync required')
        revision,digest=base.revision,base.digest
        nodes={n['id']:_plain(n) for n in base.nodes}
        edges={(e['sourceId'],e['targetId'],e['relationship']):_plain(e) for e in base.edges}
        for delta in value['deltas']:
            keys={'previousRevision','revision','previousDigest','digest','removeNodes','removeEdges','upsertNodes','upsertEdges'}
            if (not isinstance(delta,dict) or set(delta)!=keys or delta['previousRevision']!=revision
                    or delta['revision']!=revision+1 or type(delta['revision']) is not int
                    or type(delta['previousRevision']) is not int or delta['previousDigest']!=digest
                    or any(not isinstance(delta[key],list) for key in ('removeNodes','removeEdges','upsertNodes','upsertEdges'))
                    or len(delta['removeNodes'])+len(delta['upsertNodes'])>512
                    or len(delta['removeEdges'])+len(delta['upsertEdges'])>2048):
                raise ProtocolError('Reference delta gap/order/budget mismatch; prior graph unchanged')
            removed=set()
            for identity in delta['removeNodes']:
                if not isinstance(identity,str) or identity not in nodes or identity in removed:
                    raise ProtocolError('Delta removes missing or duplicated node')
                removed.add(identity);del nodes[identity]
            removed_edges=set()
            for edge in delta['removeEdges']:
                if (not isinstance(edge,dict) or set(edge)!={'sourceId','targetId','relationship'}
                        or any(not isinstance(edge[k],str) for k in edge)):
                    raise ProtocolError('Delta edge removal is not an exact declared triple')
                key=(edge['sourceId'],edge['targetId'],edge['relationship'])
                if key not in edges or key in removed_edges:
                    raise ProtocolError('Delta removes missing or duplicated edge')
                removed_edges.add(key);del edges[key]
            upserts=set()
            for node in delta['upsertNodes']:
                if not isinstance(node,dict) or not isinstance(node.get('id'),str) or node['id'] in upserts:
                    raise ProtocolError('Delta node upsert identity invalid or duplicated')
                upserts.add(node['id']);nodes[node['id']]=node
            edge_upserts=set()
            for edge in delta['upsertEdges']:
                if (not isinstance(edge,dict) or set(edge)!={'sourceId','targetId','relationship'}
                        or any(not isinstance(edge[k],str) for k in edge)):
                    raise ProtocolError('Delta edge upsert is not an exact declared triple')
                key=(edge['sourceId'],edge['targetId'],edge['relationship'])
                if key in edge_upserts:raise ProtocolError('Duplicate delta edge upsert')
                edge_upserts.add(key);edges[key]=edge
            self._graph(list(nodes.values()),list(edges.values()),delta['digest'])
            revision,digest=delta['revision'],delta['digest']
        if revision!=value['headRevision'] or digest!=value['headDigest']:
            raise ProtocolError('Reference delta chain does not reach exact advertised head; no partial result')
        result=self._snapshot({'protocol':PROTOCOL,'kind':KIND,'contextId':base.context_id,'runtimeId':base.runtime_id,
            'epoch':base.epoch,'revision':revision,'digest':digest,'nodes':list(nodes.values()),'edges':list(edges.values()),'complete':True})
        return result


class ReferenceCommits(_Reference):
    def __init__(self,context,*,allow_experimental=False):
        self._configure(context,allow_experimental,Client)

    def _request(self,method,route,**kwargs):
        self._fence();self._capability(self._client.runtime.capabilities(max_response_bytes=65536))
        value=self._transport.request('runtime',method,PREFIX+route,
            **{**self._options(),'max_response_bytes':2*1024*1024},**kwargs)
        try:
            self._fence()
            self._capability(self._client.runtime.capabilities(max_response_bytes=65536))
        except KlabError:
            if method=='POST':raise SubmissionOutcomeUnknown('Checkpoint outcome unknown after binding change; inspect head, never replay') from None
            raise
        return value

    def snapshot(self,*,revision=None):
        params={'protocol':PROTOCOL}
        if revision is not None:
            if not _revision(revision):raise InvalidRequestError('Explicit nonnegative retained reference revision required')
            params['revision']=revision
        result=self._snapshot(self._request('GET','snapshot',params=params))
        if revision is not None and result.revision!=revision:raise ProtocolError('Historical snapshot did not confirm exact requested revision')
        return result

    def checkpoint(self,*,expected_revision):
        if not _revision(expected_revision):raise InvalidRequestError('Explicit nonnegative expected reference revision required')
        value=self._request('POST','checkpoint',json={'protocol':PROTOCOL,'expectedRevision':expected_revision},ambiguous=True)
        try:
            result=self._snapshot(value)
            if result.revision!=expected_revision+1:raise ProtocolError('Checkpoint acknowledgement did not confirm next CAS revision')
            return result
        except ProtocolError:raise SubmissionOutcomeUnknown('Checkpoint acknowledgement incomplete; inspect head, never replay blindly') from None

    def reconcile(self,base):
        self._base(base)
        value=self._request('GET','changes',params={'protocol':PROTOCOL,'epoch':base.epoch,'since':base.revision,'baseDigest':base.digest})
        return self._apply(base,value)


class AsyncReferenceCommits(_Reference):
    def __init__(self,context,*,allow_experimental=False):
        from .async_client import AsyncClient
        self._configure(context,allow_experimental,AsyncClient)

    async def _request(self,method,route,**kwargs):
        self._fence();self._capability(await self._client.runtime.capabilities(max_response_bytes=65536))
        value=await self._transport.request('runtime',method,PREFIX+route,
            **{**self._options(),'max_response_bytes':2*1024*1024},**kwargs)
        try:
            self._fence()
            self._capability(await self._client.runtime.capabilities(max_response_bytes=65536))
        except KlabError:
            if method=='POST':raise SubmissionOutcomeUnknown('Checkpoint outcome unknown after acknowledgement; inspect head, never replay') from None
            raise
        return value

    async def snapshot(self,*,revision=None):
        params={'protocol':PROTOCOL}
        if revision is not None:
            if not _revision(revision):raise InvalidRequestError('Explicit nonnegative retained reference revision required')
            params['revision']=revision
        result=self._snapshot(await self._request('GET','snapshot',params=params))
        if revision is not None and result.revision!=revision:raise ProtocolError('Historical snapshot did not confirm exact requested revision')
        return result

    async def checkpoint(self,*,expected_revision):
        if not _revision(expected_revision):raise InvalidRequestError('Explicit nonnegative expected reference revision required')
        value=await self._request('POST','checkpoint',json={'protocol':PROTOCOL,'expectedRevision':expected_revision},ambiguous=True)
        try:
            result=self._snapshot(value)
            if result.revision!=expected_revision+1:raise ProtocolError('Checkpoint acknowledgement did not confirm next CAS revision')
            return result
        except ProtocolError:raise SubmissionOutcomeUnknown('Checkpoint acknowledgement incomplete; inspect head, never replay blindly') from None

    async def reconcile(self,base):
        self._base(base)
        value=await self._request('GET','changes',params={'protocol':PROTOCOL,'epoch':base.epoch,'since':base.revision,'baseDigest':base.digest})
        return self._apply(base,value)


class ReferenceResyncRequired(ProtocolError):
    """A gap/epoch requires explicit full reference resync; prior view remains historical."""


@dataclass(frozen=True)
class ReferenceGraphView:
    snapshot:ReferenceGraphSnapshot | None
    invalidated:bool
    reason:str | None
    current_authorization:str='NOT_RECHECKED: retained local reference data is not a permission lease'


class _Projection:
    def _configure(self,commits,client_type):
        if not isinstance(commits,client_type):raise InvalidRequestError('Choose an explicit matching reference-commit facade')
        commits._fence()
        self._commits=commits;self._reference=commits.reference
        self._snapshot:ReferenceGraphSnapshot | None=None
        self._invalidated=True;self._reason:str | None='EXPLICIT_RESYNC_REQUIRED'
        self._generation=0;self._lock=RLock()
        self._cleared_generation=0;self._accepted=0

    def _fence(self):
        self._commits._fence()
        if self._commits.reference!=self._reference:raise ProtocolError('Reference projection root changed; no implicit rebind')

    @property
    def view(self):
        with self._lock:
            self._fence()
            return ReferenceGraphView(self._snapshot,self._invalidated,self._reason)

    def hint(self,job):
        """Pure local invalidation: duplicate/reordered Job hints never imply commits."""
        with self._lock:
            self._fence()
            if (not isinstance(job,Job) or job.client is not self._commits.context.client
                    or scope_token(job.scope)!=self._reference.context_id):
                raise InvalidRequestError('Job hint must name the same explicit client/root; not a commit cursor')
            job._check_binding()
            self._generation+=1;self._invalidated=True;self._reason='JOB_HINT_NOT_COMMIT'

    def _capture(self,require_snapshot):
        with self._lock:
            self._fence()
            if require_snapshot and self._snapshot is None:
                raise InvalidRequestError('Explicit full resync required before incremental reference refresh')
            if require_snapshot and self._reason=='EXPLICIT_RESYNC_REQUIRED':
                raise ReferenceResyncRequired('Explicit full resync required before another incremental refresh')
            return self._snapshot,(self._generation,self._accepted)

    def _accept(self,snapshot,generation,*,resync):
        with self._lock:
            self._fence()
            previous=self._snapshot
            started,accepted=generation
            if started<self._cleared_generation:
                raise ProtocolError('Read begun before local clear cannot restore forgotten data')
            if previous is not None and accepted!=self._accepted and previous.epoch!=snapshot.epoch:
                raise ProtocolError('Concurrent epoch response requires a fresh explicit resync')
            if previous is not None:
                if previous.epoch==snapshot.epoch and (snapshot.revision<previous.revision
                        or snapshot.revision==previous.revision and snapshot.digest!=previous.digest):
                    raise ProtocolError('Slow/forked reference response cannot replace a later accepted graph')
            self._snapshot=snapshot
            self._accepted+=1
            self._invalidated=started!=self._generation
            self._reason='JOB_HINT_NOT_COMMIT' if self._invalidated else None
            return ReferenceGraphView(snapshot,self._invalidated,self._reason)

    def _failed(self,resync_required=False):
        with self._lock:
            self._generation+=1;self._invalidated=True
            self._reason='EXPLICIT_RESYNC_REQUIRED' if resync_required else 'READ_FAILED'

    def clear(self):
        """Forget only retained local graph; never release or reset remote state."""
        with self._lock:
            self._snapshot=None;self._generation+=1
            self._cleared_generation=self._generation
            self._invalidated=True;self._reason='EXPLICIT_RESYNC_REQUIRED'


class ReferenceGraphProjection(_Projection):
    """Explicit finite full resync/delta refresh; no hidden reads or native promotion."""
    def __init__(self,commits):self._configure(commits,ReferenceCommits)

    def resync(self):
        _,generation=self._capture(False)
        try:return self._accept(self._commits.snapshot(),generation,resync=True)
        except BaseException:
            self._failed();raise

    def refresh(self):
        base,generation=self._capture(True)
        try:return self._accept(self._commits.reconcile(base),generation,resync=False)
        except ServerError as error:
            if getattr(error,'status_code',None) in (409,410):
                self._failed(True)
                raise ReferenceResyncRequired('Reference history gap/epoch change; explicit full resync required, no partial graph') from None
            self._failed();raise
        except ReferenceResyncRequired:
            self._failed(True);raise
        except BaseException:
            self._failed();raise


class AsyncReferenceGraphProjection(_Projection):
    """Native awaited I/O, local locks never span awaits; cancellation keeps old view."""
    def __init__(self,commits):self._configure(commits,AsyncReferenceCommits)

    async def resync(self):
        _,generation=self._capture(False)
        try:return self._accept(await self._commits.snapshot(),generation,resync=True)
        except BaseException:
            self._failed();raise

    async def refresh(self):
        base,generation=self._capture(True)
        try:return self._accept(await self._commits.reconcile(base),generation,resync=False)
        except ServerError as error:
            if getattr(error,'status_code',None) in (409,410):
                self._failed(True)
                raise ReferenceResyncRequired('Reference history gap/epoch change; explicit full resync required, no partial graph') from None
            self._failed();raise
        except ReferenceResyncRequired:
            self._failed(True);raise
        except BaseException:
            self._failed();raise
