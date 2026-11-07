"""Opt-in finite source admission/lifecycle reference, never k.Actors execution."""
from dataclasses import dataclass
import hashlib,re

from ._inspection import freeze,_reject_credential_leaves
from .client import Client
from .errors import InvalidRequestError,KlabError,ProtocolError,SubmissionOutcomeUnknown,UnsupportedOperationError
from .shared_access import _SharedAccess

PROTOCOL='klab.experimental.managed-behaviors/1'
PREFIX='/experimental/managed-behaviors/v1/'
KIND='DECLARED_FINITE_SOURCE_STATE_MACHINE_NOT_KACTORS_EXECUTION'
TERMINAL=('FINISHED','FAILED','STOPPED')


@dataclass(frozen=True)
class BehaviorAdmission:
    source_sha256:str
    source_urn:str
    recipe:str
    protocol:str=PROTOCOL
    evidence:str=KIND


@dataclass(frozen=True)
class ManagedBehaviorReference:
    agent_id:str
    epoch:str
    runtime_id:str
    context_id:str
    admission:BehaviorAdmission
    private_session_id:str

    def to_wire(self):
        return {'agentId':self.agent_id,'epoch':self.epoch,'runtimeId':self.runtime_id,'contextId':self.context_id,
            'sourceSha256':self.admission.source_sha256,'sourceUrn':self.admission.source_urn,'recipe':self.admission.recipe,
            'privateSessionId':self.private_session_id,'protocol':PROTOCOL,'kind':KIND}


@dataclass(frozen=True)
class ManagedBehaviorStatus:
    reference:ManagedBehaviorReference
    revision:int
    state:str
    private_session_id:str
    private_session_disposed:bool
    outcome:str | None
    evidence:str=KIND


def _hex(value,n):return isinstance(value,str) and re.fullmatch('[0-9a-f]{'+str(n)+'}',value) is not None


class _Behaviors(_SharedAccess):
    def _configure(self,context,allow_experimental,client_type):
        self._bind(context,allow_experimental,client_type)
        self._client=context.client;self._transport=self._client.transport;self._endpoint=self._transport.endpoint('runtime')

    def _fence(self):
        try:self._options()
        except InvalidRequestError:raise ProtocolError('Managed behavior root/Runtime binding changed') from None
        if self.context.client is not self._client or self._client.transport is not self._transport or self._endpoint!=self._transport.endpoint('runtime'):
            raise ProtocolError('Managed behavior endpoint/client/credential changed; no implicit reconnect')

    def _capability(self,value):
        self._fence()
        if value.service_id!=self.reference.runtime_id:raise ProtocolError('Managed behavior Runtime identity changed')
        if value.raw.get('managedBehaviorProtocol')!=PROTOCOL:
            raise UnsupportedOperationError('Finite managed behavior protocol not advertised; no native fallback')

    def _safe(self,value):
        freeze(value)
        try:_reject_credential_leaves(value,self._transport.redact,'Managed behavior contains configured credential material')
        except InvalidRequestError:raise ProtocolError('Managed behavior contains configured credential material') from None

    def _source(self,source):
        self._fence()
        if not isinstance(source,str) or not source.strip():raise InvalidRequestError('Explicit finite source text required')
        try:raw=source.encode('utf-8')
        except UnicodeError:raise InvalidRequestError('Source must be UTF8') from None
        if len(raw)>4096:raise InvalidRequestError('Finite source exceeds4096byte budget')
        self._safe(source)
        return {'protocol':PROTOCOL,'source':source,'sourceSha256':hashlib.sha256(raw).hexdigest()}

    def _admission(self,value,digest):
        self._safe(value)
        if (not isinstance(value,dict) or set(value)!={'protocol','kind','sourceSha256','sourceUrn','recipe'}
                or value['protocol']!=PROTOCOL or value['kind']!=KIND or value['sourceSha256']!=digest or not _hex(digest,64)
                or value['recipe'] not in ('finish','fail') or value['sourceUrn']!='simulator.managed.'+value['recipe']):
            raise ProtocolError('Finite source admission did not confirm exact source hash/recipe; not native validation')
        return BehaviorAdmission(digest,value['sourceUrn'],value['recipe'])

    def _reference(self,value):
        self._fence();self._safe(value)
        fields={'agentId','epoch','runtimeId','contextId','sourceSha256','sourceUrn','recipe','protocol','kind','privateSessionId'}
        if (not isinstance(value,dict) or set(value)!=fields or value['runtimeId']!=self.reference.runtime_id
                or value['contextId']!=self.reference.context_id or not _hex(value['agentId'],32) or not _hex(value['epoch'],32)
                or not _hex(value['privateSessionId'],32)):
            raise ProtocolError('Explicit saved reference must match exact root/Runtime/incarnation')
        admission=self._admission({key:value[key] for key in ('protocol','kind','sourceSha256','sourceUrn','recipe')},value['sourceSha256'])
        return ManagedBehaviorReference(value['agentId'],value['epoch'],value['runtimeId'],value['contextId'],admission,value['privateSessionId'])

    def _selector(self,reference):
        if not isinstance(reference,ManagedBehaviorReference) or not isinstance(reference.admission,BehaviorAdmission):
            raise InvalidRequestError('Explicit managed behavior reference and source admission required')
        return self._reference(reference.to_wire())

    def _status(self,value,reference=None,admission=None):
        self._safe(value)
        if (not isinstance(value,dict) or set(value)!={'reference','revision','state','privateSessionId','privateSessionDisposed','outcome'}
                or type(value['revision']) is not int or not 1<=value['revision']<=16
                or value['state'] not in ('READY','RUNNING','STOPPING',*TERMINAL)
                or not _hex(value['privateSessionId'],32) or type(value['privateSessionDisposed']) is not bool
                or value['privateSessionId']==self.reference.context_id.split('.')[0]
                or value['privateSessionDisposed'] and value['state'] not in TERMINAL
                or value['outcome']!={'FINISHED':'DECLARED_SUCCESS','FAILED':'DECLARED_FAILURE','STOPPED':'DECLARED_STOP'}.get(value['state'])):
            raise ProtocolError('Managed behavior outcome/private-session state invalid')
        bound=self._reference(value['reference'])
        if value['privateSessionId']!=bound.private_session_id:raise ProtocolError('Private session changed from explicit agent binding')
        if reference is not None and bound!=reference or admission is not None and bound.admission!=admission:
            raise ProtocolError('Managed behavior response changed explicit source/agent identity')
        return ManagedBehaviorStatus(bound,value['revision'],value['state'],value['privateSessionId'],value['privateSessionDisposed'],value['outcome'])

    def _created(self,value,admission):
        result=self._status(value,admission=admission)
        if result.state!='READY' or result.revision!=1:raise ProtocolError('Creation must confirm deferred READY state')
        return result

    def _command(self,reference,command,revision):
        reference=self._selector(reference)
        if command not in ('start','stop','purge') or type(revision) is not int or not 1<=revision<=16:
            raise InvalidRequestError('Explicit bounded command and observed revision required')
        return reference,{'protocol':PROTOCOL,'epoch':reference.epoch,'expectedRevision':revision}

    def _ack(self,value,reference,command,revision):
        if command=='purge':
            if value!={'purged':True,'reference':reference.to_wire()} or value.get('purged') is not True:
                raise ProtocolError('Purge not confirmed for exact managed behavior')
            return True
        result=self._status(value,reference)
        if result.state!={'start':'RUNNING','stop':'STOPPING'}[command] or result.revision!=revision+1:
            raise ProtocolError('Exact managed behavior transition not confirmed')
        return result


class ManagedBehaviors(_Behaviors):
    def __init__(self,context,*,allow_experimental=False):self._configure(context,allow_experimental,Client)

    def _request(self,method,route,**kwargs):
        self._fence();self._capability(self._client.runtime.capabilities(max_response_bytes=65536))
        value=self._transport.request('runtime',method,PREFIX+route,**{**self._options(),'max_response_bytes':65536},**kwargs)
        try:self._fence();self._capability(self._client.runtime.capabilities(max_response_bytes=65536))
        except KlabError:
            if method!='GET' and route!='validate':raise SubmissionOutcomeUnknown('Managed behavior write outcome unknown; inspect, never replay') from None
            raise
        return value

    def validate(self,source):
        body=self._source(source)
        return self._admission(self._request('POST','validate',json=body),body['sourceSha256'])

    def create(self,source,*,admission):
        body=self._source(source)
        if not isinstance(admission,BehaviorAdmission) or admission.source_sha256!=body['sourceSha256']:
            raise InvalidRequestError('Explicit source-matched finite admission required')
        value=self._request('POST','create',json={**body,'recipe':admission.recipe},ambiguous=True)
        try:return self._created(value,admission)
        except ProtocolError:raise SubmissionOutcomeUnknown('Creation acknowledgement incomplete; no assumed private-session rollback') from None

    def reconnect(self,saved):
        reference=self._reference(saved)
        return self.read(reference)

    def read(self,reference):
        reference=self._selector(reference)
        return self._status(self._request('GET',reference.agent_id,params={'protocol':PROTOCOL,'epoch':reference.epoch}),reference)

    def command(self,reference,command,*,expected_revision):
        reference,body=self._command(reference,command,expected_revision)
        value=self._request('POST',reference.agent_id+'/'+command,json=body,ambiguous=True)
        try:return self._ack(value,reference,command,expected_revision)
        except ProtocolError:raise SubmissionOutcomeUnknown('Lifecycle acknowledgement incomplete; inspect without replay') from None


class AsyncManagedBehaviors(_Behaviors):
    def __init__(self,context,*,allow_experimental=False):
        from .async_client import AsyncClient
        self._configure(context,allow_experimental,AsyncClient)

    async def _request(self,method,route,**kwargs):
        self._fence();self._capability(await self._client.runtime.capabilities(max_response_bytes=65536))
        value=await self._transport.request('runtime',method,PREFIX+route,**{**self._options(),'max_response_bytes':65536},**kwargs)
        try:self._fence();self._capability(await self._client.runtime.capabilities(max_response_bytes=65536))
        except KlabError:
            if method!='GET' and route!='validate':raise SubmissionOutcomeUnknown('Managed behavior write outcome unknown; inspect, never replay') from None
            raise
        return value

    async def validate(self,source):
        body=self._source(source)
        return self._admission(await self._request('POST','validate',json=body),body['sourceSha256'])

    async def create(self,source,*,admission):
        body=self._source(source)
        if not isinstance(admission,BehaviorAdmission) or admission.source_sha256!=body['sourceSha256']:
            raise InvalidRequestError('Explicit source-matched finite admission required')
        value=await self._request('POST','create',json={**body,'recipe':admission.recipe},ambiguous=True)
        try:return self._created(value,admission)
        except ProtocolError:raise SubmissionOutcomeUnknown('Creation acknowledgement incomplete; no assumed private-session rollback') from None

    async def reconnect(self,saved):
        reference=self._reference(saved)
        return await self.read(reference)

    async def read(self,reference):
        reference=self._selector(reference)
        return self._status(await self._request('GET',reference.agent_id,params={'protocol':PROTOCOL,'epoch':reference.epoch}),reference)

    async def command(self,reference,command,*,expected_revision):
        reference,body=self._command(reference,command,expected_revision)
        value=await self._request('POST',reference.agent_id+'/'+command,json=body,ambiguous=True)
        try:return self._ack(value,reference,command,expected_revision)
        except ProtocolError:raise SubmissionOutcomeUnknown('Lifecycle acknowledgement incomplete; inspect without replay') from None
