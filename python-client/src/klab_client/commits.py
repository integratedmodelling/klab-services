"""Read-only native commit-cache reports, not contiguous revision convergence."""
from dataclasses import dataclass, field
from typing import Mapping
from types import MappingProxyType

from ._inspection import bounds, freeze, _reject_credential_leaves
from .client import Client, Context, scope_token
from .dto import check_notifications
from .errors import InvalidRequestError, MissingAssetError, ProtocolError
from .handles import to_handle

ASSETS = ('addedAssets','addedObservations','addedCohorts','modifiedAssets','deletedAssets')
LINKS = ('addedLinks','deletedLinks')


@dataclass(frozen=True)
class CommitLink:
    source_id: int
    target_id: int
    relationship: str


@dataclass(frozen=True)
class CommitInspection:
    commit_id: int
    context_id: str
    runtime_id: str
    timestamp: int | None
    owner: str | None
    assets: Mapping[str,tuple[int,...] | None]
    links: Mapping[str,tuple[CommitLink,...] | None]
    raw: Mapping = field(repr=False)
    evidence: str = 'Native-shaped cached change report only; not full graph, contiguous revision or scientific acceptance'


def _id(value):
    return type(value) is int and 0 < value <= 2**63-1


class _Commits:
    def _configure(self,context,client_type,max_items,max_response_bytes):
        if (not isinstance(context,Context) or not isinstance(context.client,client_type)
                or scope_token(context)!=context.id or context.relationship_selection is not None):
            raise InvalidRequestError('Commit inspection requires an explicit matching root Context/client')
        self.context=context;self.reference=to_handle(context)
        self._client=context.client;self._transport=context.client.transport
        self._runtime=self._transport.endpoint('runtime')
        self._items=bounds(max_items,4096,'Commit total change budget')
        self._bytes=bounds(max_response_bytes,4*1024*1024,'Commit response byte budget')

    def _fence(self):
        try:current=to_handle(self.context)
        except InvalidRequestError:
            raise ProtocolError('Commit root/Runtime binding changed; report not accepted') from None
        if (current!=self.reference or self.context.client is not self._client or self.context.client.transport is not self._transport
                or self._transport.endpoint('runtime') != self._runtime):
            raise ProtocolError('Commit client/Runtime endpoint/credential changed; no implicit rebinding')

    def _options(self):
        self._fence()
        return {'scope':self.reference.context_id,'service_id':self.reference.runtime_id,'max_response_bytes':self._bytes}

    def _identity(self,capability):
        if capability.service_id != self.reference.runtime_id:
            raise ProtocolError('Commit capability does not confirm exact originating Runtime')
        self._fence()

    def _decode(self,value,commit_id):
        if value is None:
            raise MissingAssetError('Commit cache entry absent, expired or inaccessible; no durable-history inference')
        if not isinstance(value,dict) or value.get('id') != commit_id or not _id(value.get('id')):
            raise ProtocolError('Commit response must name the exact requested numeric identity')
        try:_reject_credential_leaves(value,self._transport.redact,'Commit report contains configured credential material')
        except InvalidRequestError:raise ProtocolError('Commit report contains configured credential material') from None
        check_notifications(value,self._transport.redact)
        timestamp=value.get('timestamp');owner=value.get('owner')
        if timestamp is not None and (type(timestamp) is not int or not 0 <= timestamp <= 2**63-1):
            raise ProtocolError('Commit timestamp must be a reported nonnegative Java-long or null')
        if owner is not None and (not isinstance(owner,str) or not owner.strip() or len(owner)>512):
            raise ProtocolError('Commit owner must be bounded public identity text or null')
        count=0
        def rows(key):
            nonlocal count
            entries=value.get(key)
            if entries is None:return None
            if not isinstance(entries,list):raise ProtocolError('Commit memberships must use explicit arrays')
            count+=len(entries)
            if count>self._items:raise ProtocolError('Commit total change budget exceeded; no truncation')
            return entries
        assets={};links={}
        for key in ASSETS:
            entries=rows(key)
            if entries is not None and (any(not _id(identity) for identity in entries) or len(set(entries))!=len(entries)):
                raise ProtocolError('Commit asset group has invalid or duplicate numeric identities')
            assets[key]=None if entries is None else tuple(entries)
        for key in LINKS:
            entries=rows(key);converted=[]
            for row in entries or []:
                if (not isinstance(row,dict) or set(row)!={'first','second','third'}
                        or not _id(row['first']) or not _id(row['second']) or not isinstance(row['third'],str)
                        or not row['third'].strip() or len(row['third'])>128):
                    raise ProtocolError('Native commit links require explicit numeric endpoints/relationship triples')
                converted.append(CommitLink(row['first'],row['second'],row['third']))
            if len(set(converted))!=len(converted):raise ProtocolError('Commit link group contains duplicate triples')
            links[key]=None if entries is None else tuple(converted)
        return CommitInspection(commit_id,self.reference.context_id,self.reference.runtime_id,timestamp,owner,
            MappingProxyType(assets),MappingProxyType(links),freeze(value,self._transport.redact))


class CommitInspector(_Commits):
    def __init__(self,context,*,max_items=1024,max_response_bytes=1024*1024):
        self._configure(context,Client,max_items,max_response_bytes)

    def inspect(self,commit_id):
        if not _id(commit_id):raise InvalidRequestError('Choose one explicit positive Java-long commit ID, not a guessed cursor')
        self._fence();self._identity(self._client.runtime.capabilities(max_response_bytes=65536))
        value=self._transport.request('runtime','GET','/api/v1/commit',params={'id':commit_id},
            **{**self._options(),'max_response_bytes':self._bytes})
        self._fence()
        self._identity(self._client.runtime.capabilities(max_response_bytes=65536))
        return self._decode(value,commit_id)


class AsyncCommitInspector(_Commits):
    def __init__(self,context,*,max_items=1024,max_response_bytes=1024*1024):
        from .async_client import AsyncClient
        self._configure(context,AsyncClient,max_items,max_response_bytes)

    async def inspect(self,commit_id):
        if not _id(commit_id):raise InvalidRequestError('Choose one explicit positive Java-long commit ID, not a guessed cursor')
        self._fence();self._identity(await self._client.runtime.capabilities(max_response_bytes=65536))
        value=await self._transport.request('runtime','GET','/api/v1/commit',params={'id':commit_id},
            **{**self._options(),'max_response_bytes':self._bytes})
        self._fence()
        self._identity(await self._client.runtime.capabilities(max_response_bytes=65536))
        return self._decode(value,commit_id)
