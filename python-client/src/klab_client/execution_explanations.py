"""Opt-in complete finite execution explanation reference, not native lineage."""
from dataclasses import dataclass,field
import hashlib,json,re
from typing import Mapping

from ._inspection import freeze,_reject_credential_leaves
from .client import Client
from .errors import InvalidRequestError,ProtocolError,UnsupportedOperationError
from .handles import to_handle
from .shared_access import _SharedAccess

PROTOCOL='klab.experimental.execution-explanations/1'
PREFIX='/experimental/execution-explanations/v1/observations/'
KIND='COMPLETE_FINITE_REFERENCE_NOT_NATIVE_SCIENTIFIC_LINEAGE'


@dataclass(frozen=True)
class ExecutionExplanation:
    context_id:str
    runtime_id:str
    observation_id:int
    epoch:str
    revision:int
    digest:str
    inputs:tuple[Mapping,...]=field(repr=False)
    selections:tuple[Mapping,...]=field(repr=False)
    planned:tuple[Mapping,...]=field(repr=False)
    recorded:tuple[Mapping,...]=field(repr=False)
    semantic_updates:tuple[Mapping,...]=field(repr=False)
    evidence:str=KIND


def _id(value):return type(value) is int and 0<value<=2**63-1
def _hash(value):return isinstance(value,str) and re.fullmatch('[0-9a-f]{64}',value) is not None
def _version(value):
    return isinstance(value,str) and len(value)<=128 and re.fullmatch(r'[0-9]+(?:\.[0-9]+){2}(?:[-+][a-zA-Z0-9.-]+)?',value) is not None


class _Explanations(_SharedAccess):
    def _configure(self,context,allow_experimental,client_type):
        self._bind(context,allow_experimental,client_type)
        self._client=context.client;self._transport=context.client.transport
        self._runtime=self._transport.endpoint('runtime')

    def _fence(self):
        try:self._options()
        except InvalidRequestError:raise ProtocolError('Execution explanation root/Runtime binding changed') from None
        if self.context.client is not self._client or self._client.transport is not self._transport or self._transport.endpoint('runtime')!=self._runtime:
            raise ProtocolError('Execution explanation client/endpoint/credential changed; no implicit rebind')

    def _capability(self,capability):
        if capability.service_id!=self.reference.runtime_id:raise ProtocolError('Execution explanation Runtime identity changed')
        self._fence()
        if capability.raw.get('executionExplanationProtocol')!=PROTOCOL:
            raise UnsupportedOperationError('Exact execution-explanation reference not advertised; no native fallback')

    def _observation(self,observation):
        self._fence();handle=to_handle(observation)
        if (handle.context_id!=self.reference.context_id or handle.runtime_id!=self.reference.runtime_id
                or getattr(getattr(observation,'_context',None),'client',None) is not self._client
                or not _id(observation.id)):
            raise InvalidRequestError('Choose an explicit observation from the exact client/root/Runtime')
        return observation.id

    def _decode(self,value,identity):
        freeze(value)
        try:_reject_credential_leaves(value,self._transport.redact,'Execution explanation contains configured credential material')
        except InvalidRequestError:raise ProtocolError('Execution explanation contains configured credential material') from None
        keys={'protocol','kind','contextId','runtimeId','observationId','epoch','revision','digest','complete',
            'inputs','selections','planned','recorded','semanticUpdates'}
        if (not isinstance(value,dict) or set(value)!=keys or value['protocol']!=PROTOCOL or value['kind']!=KIND
                or value['contextId']!=self.reference.context_id or value['runtimeId']!=self.reference.runtime_id
                or not _id(value['observationId']) or value['observationId']!=identity or value['complete'] is not True
                or not isinstance(value['epoch'],str) or re.fullmatch('[0-9a-f]{32}',value['epoch']) is None
                or type(value['revision']) is not int or not 0<=value['revision']<2**53):
            raise ProtocolError('Execution explanation does not confirm exact reference identity/completeness')
        groups=('inputs','selections','planned','recorded','semanticUpdates')
        if any(not isinstance(value[key],list) or len(value[key])>128 for key in groups):
            raise ProtocolError('Execution explanation exceeds finite membership budgets')
        if any(not value[key] for key in ('inputs','selections','planned','recorded')):
            raise ProtocolError('Finite completed execution explanation requires supplied input/selection/plan/recorded evidence')
        resources={}
        for row in value['inputs']:
            if (not isinstance(row,dict) or set(row)!={'urn','providerId','version','sourceSha256','binarySha256'}
                    or any(not isinstance(row[key],str) or not row[key].strip() for key in ('urn','providerId','version'))
                    or not _version(row['version'])
                    or not _hash(row['sourceSha256']) or row['binarySha256'] is not None and not _hash(row['binarySha256'])
                    or row['urn'] in resources):
                raise ProtocolError('Input source/provider/version/binary association invalid or duplicated')
            resources[row['urn']]=row
        selections={}
        for row in value['selections']:
            if (not isinstance(row,dict) or set(row)!={'id','modelUrn','modelVersion','modelSourceSha256',
                    'providerId','componentProviderId','componentId','componentVersion','prototype','inputUrns'}
                    or any(not isinstance(row[key],str) or not row[key].strip() for key in (
                        'id','modelUrn','modelVersion','providerId','componentProviderId','componentId','componentVersion','prototype'))
                    or not _hash(row['modelSourceSha256']) or not isinstance(row['inputUrns'],list)
                    or not _version(row['modelVersion']) or not _version(row['componentVersion'])
                    or len(row['inputUrns'])>128 or any(not isinstance(urn,str) or urn not in resources for urn in row['inputUrns'])
                    or len(set(row['inputUrns']))!=len(row['inputUrns']) or row['id'] in selections):
                raise ProtocolError('Selected model/component/prototype/input bindings incomplete or duplicated')
            selections[row['id']]=row
        planned={}
        for row in value['planned']:
            if (not isinstance(row,dict) or set(row)!={'id','selectionId','targetObservationId','effect'}
                    or not isinstance(row['id'],str) or not row['id'].strip() or row['id'] in planned
                    or not isinstance(row['selectionId'],str) or row['selectionId'] not in selections
                    or row['targetObservationId']!=identity or not _id(row['targetObservationId'])
                    or row['effect'] not in ('CREATE','SEMANTIC_UPDATE')):
                raise ProtocolError('Planned effect must bind one selected computation and exact target')
            planned[row['id']]=row
        recorded={}
        for row in value['recorded']:
            if (not isinstance(row,dict) or set(row)!={'id','plannedId','targetObservationId','outcome','effect'}
                    or not isinstance(row['id'],str) or not row['id'].strip() or row['id'] in recorded
                    or not isinstance(row['plannedId'],str) or row['plannedId'] not in planned
                    or not _id(row['targetObservationId']) or row['targetObservationId']!=identity
                    or row['outcome'] not in ('SUCCESS','FAILURE','INTERRUPTED')
                    or row['effect']!=planned[row['plannedId']]['effect']):
                raise ProtocolError('Recorded effect must link explicit plan; planned does not prove execution')
            recorded[row['id']]=row
        previous_revision=0
        semantic_records=set()
        for row in value['semanticUpdates']:
            if (not isinstance(row,dict) or set(row)!={'recordedId','previousRevision','revision','beforeObservable','afterObservable'}
                    or not isinstance(row['recordedId'],str) or row['recordedId'] not in recorded
                    or recorded[row['recordedId']]['outcome']!='SUCCESS' or recorded[row['recordedId']]['effect']!='SEMANTIC_UPDATE'
                    or type(row['previousRevision']) is not int or row['previousRevision']<0
                    or type(row['revision']) is not int or row['revision']!=row['previousRevision']+1
                    or row['previousRevision']!=previous_revision or row['revision']>value['revision']
                    or row['recordedId'] in semantic_records
                    or any(not isinstance(row[key],str) or not row[key].strip() for key in ('beforeObservable','afterObservable'))):
                raise ProtocolError('Semantic update requires explicit successful recorded effect and ordered before/after revisions')
            previous_revision=row['revision']
            semantic_records.add(row['recordedId'])
        expected_records={row['id'] for row in value['recorded'] if row['effect']=='SEMANTIC_UPDATE' and row['outcome']=='SUCCESS'}
        if semantic_records!=expected_records or previous_revision!=value['revision']:
            raise ProtocolError('Successful semantic effects require exact before/after revision records')
        content={key:value[key] for key in value if key!='digest'}
        try:raw=json.dumps(content,sort_keys=True,ensure_ascii=False,allow_nan=False,separators=(',',':')).encode()
        except (ValueError,TypeError,UnicodeError):raise ProtocolError('Execution explanation canonical JSON invalid') from None
        if len(raw)>1024*1024 or not _hash(value['digest']) or hashlib.sha256(raw).hexdigest()!=value['digest']:
            raise ProtocolError('Execution explanation does not match its exact bounded reference digest')
        def rows(key):return tuple(freeze(row) for row in value[key])
        return ExecutionExplanation(value['contextId'],value['runtimeId'],identity,value['epoch'],value['revision'],value['digest'],
            rows('inputs'),rows('selections'),rows('planned'),rows('recorded'),rows('semanticUpdates'))


class ExecutionExplanations(_Explanations):
    def __init__(self,context,*,allow_experimental=False):self._configure(context,allow_experimental,Client)

    def inspect(self,observation):
        identity=self._observation(observation)
        self._capability(self._client.runtime.capabilities(max_response_bytes=65536))
        value=self._transport.request('runtime','GET',PREFIX+str(identity),params={'protocol':PROTOCOL},
            **{**self._options(),'max_response_bytes':1024*1024})
        self._fence();self._capability(self._client.runtime.capabilities(max_response_bytes=65536))
        return self._decode(value,identity)


class AsyncExecutionExplanations(_Explanations):
    def __init__(self,context,*,allow_experimental=False):
        from .async_client import AsyncClient
        self._configure(context,allow_experimental,AsyncClient)

    async def inspect(self,observation):
        identity=self._observation(observation)
        self._capability(await self._client.runtime.capabilities(max_response_bytes=65536))
        value=await self._transport.request('runtime','GET',PREFIX+str(identity),params={'protocol':PROTOCOL},
            **{**self._options(),'max_response_bytes':1024*1024})
        self._fence();self._capability(await self._client.runtime.capabilities(max_response_bytes=65536))
        return self._decode(value,identity)
