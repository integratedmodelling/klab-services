"""Explicit reference conditional source publication, not native authoring admission.

Server-owned source revisions/validation/publication are conditional operations;
private draft receipts and caller-supplied validation never constitute authority.
"""
from dataclasses import dataclass, field
import hashlib
import re
from typing import Mapping,cast

from ._inspection import freeze
from .client import Client
from .errors import InvalidRequestError, ProtocolError, SubmissionOutcomeUnknown, UnsupportedOperationError
from .shared_access import _SharedAccess

PROTOCOL = 'klab.experimental.accepted-documents/1'
PREFIX = '/experimental/accepted-documents/v1/'
LIMIT = 'Server-reported finite source reference; not native publication, executable or scientific admission'


@dataclass(frozen=True)
class AuthoringKnowledge:
    service_id: str
    worldview_id: str
    revision: int
    session_id: str
    content_fingerprint: str
    evidence: str = LIMIT


@dataclass(frozen=True)
class DocumentRevision:
    context_id: str
    resources_id: str
    name: str
    revision: int
    source: str = field(repr=False)
    source_sha256: str
    validation: Mapping | None = field(repr=False)
    evidence: str = LIMIT


@dataclass(frozen=True)
class DocumentPublication:
    context_id: str
    resources_id: str
    name: str
    document_revision: int
    source: str = field(repr=False)
    source_sha256: str
    publication_id: str
    knowledge: AuthoringKnowledge
    evidence: str = LIMIT


def _name(value):
    if (not isinstance(value,str) or re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)*',value) is None
            or len(value)>128):
        raise InvalidRequestError('Choose an explicit bounded document identity, not a path')
    return value


def _revision(value, *, creating=False):
    if type(value) is not int or not (0 if creating else 1)<=value<2**53:
        raise InvalidRequestError('Expected revision requires an explicit bounded integer')
    return value


def _source(value):
    try: raw=value.encode('utf-8') if isinstance(value,str) else b''
    except UnicodeError: raise InvalidRequestError('Document source requires valid UTF-8') from None
    if not raw or not value.strip() or len(raw)>16384:
        raise InvalidRequestError('Document source must be nonblank UTF-8 within16KiB')
    return hashlib.sha256(raw).hexdigest()


def _knowledge(value):
    keys={'serviceId','worldviewId','revision','sessionId','contentFingerprint'}
    if (not isinstance(value,dict) or set(value)!=keys or type(value['revision']) is not int
            or not 1<=value['revision']<2**53 or any(not isinstance(value[k],str) or not value[k].strip()
            or len(value[k])>512 for k in keys-{'revision'})):
        raise ProtocolError('Reference authoring knowledge requires exact bounded identity/revision')
    return AuthoringKnowledge(value['serviceId'],value['worldviewId'],value['revision'],value['sessionId'],value['contentFingerprint'])


class _Documents(_SharedAccess):
    def _configure(self, context, allow_experimental, client_type):
        self._bind(context,allow_experimental,client_type)
        self._resources=None

    def _capability(self, before, capability):
        self._options()
        after=self.context.client.transport.endpoint('resources')
        binding=(before.url,capability.service_id)
        if after!=before or self._resources is not None and binding!=self._resources:
            raise ProtocolError('Resources authoring binding changed; no implicit rebind or source transmission')
        if capability.raw.get('acceptedDocumentProtocol')!=PROTOCOL:
            raise UnsupportedOperationError('Resources does not advertise conditional source reference; no fallback')
        self._public(list(binding))
        self._resources=binding

    def _envelope(self,value,name,keys):
        if (not isinstance(value,dict) or set(value)!=keys or value.get('protocol')!=PROTOCOL
                or value.get('contextId')!=self.reference.context_id or value.get('name')!=name
                or value.get('resourcesServiceId')!=cast(tuple[str,str],self._resources)[1]):
            raise ProtocolError('Conditional document response does not confirm exact root/Resources/name/protocol')

    def _document(self,value,name,expected=None):
        self._envelope(value,name,{'protocol','contextId','resourcesServiceId','name','revision','source','sourceSha256','validation'})
        try:
            revision=_revision(value['revision']); digest=_source(value['source'])
        except InvalidRequestError: raise ProtocolError('Invalid document source/revision response') from None
        if digest!=value['sourceSha256'] or expected is not None and revision!=expected:
            raise ProtocolError('Document digest/revision does not match exact request')
        validation=value['validation']
        if validation is not None:
            if (not isinstance(validation,dict) or set(validation)!={'revision','sourceSha256','knowledge','status','diagnostics'}
                    or type(validation['revision']) is not int or validation['revision']!=revision
                    or validation['sourceSha256']!=digest or validation['status'] not in ('VALID','INVALID')
                    or not isinstance(validation['diagnostics'],list)
                    or any(not isinstance(row,dict) for row in validation['diagnostics'])):
                raise ProtocolError('Validation does not match exact source revision/diagnostic contract')
            _knowledge(validation['knowledge'])
            errors=any(row.get('level')=='Error' for row in validation['diagnostics'])
            if (validation['status']=='VALID')==errors:
                raise ProtocolError('Validation status contradicts its Error diagnostics')
        return DocumentRevision(self.reference.context_id,cast(tuple[str,str],self._resources)[1],name,revision,value['source'],digest,
            freeze(validation,self.context.client.transport.redact) if validation is not None else None)

    def _publication(self,value,name,expected=None):
        self._envelope(value,name,{'protocol','contextId','resourcesServiceId','name','documentRevision','source','sourceSha256','publicationId','knowledge'})
        try: revision=_revision(value['documentRevision']);digest=_source(value['source'])
        except InvalidRequestError: raise ProtocolError('Invalid published source/revision') from None
        if (digest!=value['sourceSha256'] or expected is not None and revision!=expected
                or not isinstance(value['publicationId'],str) or re.fullmatch('[0-9a-f]{32}',value['publicationId']) is None):
            raise ProtocolError('Publication does not confirm exact source/revision/opaque identity')
        return DocumentPublication(self.reference.context_id,cast(tuple[str,str],self._resources)[1],name,revision,value['source'],digest,
            value['publicationId'],_knowledge(value['knowledge']))

    def _update_body(self,source,expected_revision):
        digest=_source(source);self._public(source)
        return {'protocol':PROTOCOL,'expectedRevision':_revision(expected_revision,creating=True),'source':source,'sourceSha256':digest}

    def _condition(self,expected_revision,expected_knowledge_revision):
        return {'protocol':PROTOCOL,'expectedRevision':_revision(expected_revision),
            'expectedKnowledgeRevision':_revision(expected_knowledge_revision)}


class AcceptedDocuments(_Documents):
    """Synchronous proposed conditional source workflow; each mutation needs explicit conditions."""
    def __init__(self,context,*,allow_experimental=False):
        self._configure(context,allow_experimental,Client)

    def _request(self,method,route,**kwargs):
        self._public(route);self._public(kwargs)
        options=self._options();before=self.context.client.transport.endpoint('resources')
        self._capability(before,self.context.client.resources.capabilities(max_response_bytes=65536))
        value=self.context.client.transport.request('resources',method,PREFIX+route,
            **{**options,'max_response_bytes':65536},**kwargs)
        result=self._after_request(value,method)
        if self.context.client.transport.endpoint('resources')!=before:
            error=ProtocolError if method=='GET' else SubmissionOutcomeUnknown
            raise error('Resources binding changed during conditional request; inspect original server before retrying')
        return result

    def knowledge(self):
        return _knowledge(self._request('GET','knowledge',params={'protocol':PROTOCOL}))

    def read(self,name,*,revision=None):
        name=_name(name);params={'protocol':PROTOCOL}
        if revision is not None: params['revision']=_revision(revision)
        return self._document(self._request('GET','documents/'+name,params=params),name,revision)

    def update(self,name,source,*,expected_revision):
        name=_name(name);body=self._update_body(source,expected_revision)
        result=self._document(self._request('PUT','documents/'+name,json=body,ambiguous=True),name,expected_revision+1)
        if result.validation is not None or result.source!=source or result.source_sha256!=body['sourceSha256']:
            raise ProtocolError('Source update must confirm exact source and invalidate prior validation')
        return result

    def validate(self,name,*,expected_revision,expected_knowledge_revision):
        name=_name(name);body=self._condition(expected_revision,expected_knowledge_revision)
        result=self._document(self._request('POST','documents/'+name+'/validate',json=body,ambiguous=True),name,expected_revision)
        if result.validation is None or result.validation['knowledge']['revision']!=expected_knowledge_revision:
            raise ProtocolError('Validation response does not confirm expected knowledge')
        return result

    def publish(self,name,*,expected_revision,expected_knowledge_revision):
        name=_name(name);body=self._condition(expected_revision,expected_knowledge_revision)
        result=self._publication(self._request('POST','documents/'+name+'/publish',json=body,ambiguous=True),name,expected_revision)
        if result.knowledge.revision!=expected_knowledge_revision: raise ProtocolError('Publication knowledge differs from expected revision')
        return result

    def published(self,name):
        name=_name(name)
        return self._publication(self._request('GET','publications/'+name,params={'protocol':PROTOCOL}),name)


class AsyncAcceptedDocuments(_Documents):
    """Native async proposed conditional source workflow; no background publication or retry."""
    def __init__(self,context,*,allow_experimental=False):
        from .async_client import AsyncClient
        self._configure(context,allow_experimental,AsyncClient)

    async def _request(self,method,route,**kwargs):
        self._public(route);self._public(kwargs)
        options=self._options();before=self.context.client.transport.endpoint('resources')
        self._capability(before,await self.context.client.resources.capabilities(max_response_bytes=65536))
        value=await self.context.client.transport.request('resources',method,PREFIX+route,
            **{**options,'max_response_bytes':65536},**kwargs)
        result=self._after_request(value,method)
        if self.context.client.transport.endpoint('resources')!=before:
            error=ProtocolError if method=='GET' else SubmissionOutcomeUnknown
            raise error('Resources binding changed during conditional request; inspect original server before retrying')
        return result

    async def knowledge(self):
        return _knowledge(await self._request('GET','knowledge',params={'protocol':PROTOCOL}))

    async def read(self,name,*,revision=None):
        name=_name(name);params={'protocol':PROTOCOL}
        if revision is not None: params['revision']=_revision(revision)
        return self._document(await self._request('GET','documents/'+name,params=params),name,revision)

    async def update(self,name,source,*,expected_revision):
        name=_name(name);body=self._update_body(source,expected_revision)
        result=self._document(await self._request('PUT','documents/'+name,json=body,ambiguous=True),name,expected_revision+1)
        if result.validation is not None or result.source!=source or result.source_sha256!=body['sourceSha256']:
            raise ProtocolError('Source update must confirm exact source and invalidate prior validation')
        return result

    async def validate(self,name,*,expected_revision,expected_knowledge_revision):
        name=_name(name);body=self._condition(expected_revision,expected_knowledge_revision)
        result=self._document(await self._request('POST','documents/'+name+'/validate',json=body,ambiguous=True),name,expected_revision)
        if result.validation is None or result.validation['knowledge']['revision']!=expected_knowledge_revision:
            raise ProtocolError('Validation response does not confirm expected knowledge')
        return result

    async def publish(self,name,*,expected_revision,expected_knowledge_revision):
        name=_name(name);body=self._condition(expected_revision,expected_knowledge_revision)
        result=self._publication(await self._request('POST','documents/'+name+'/publish',json=body,ambiguous=True),name,expected_revision)
        if result.knowledge.revision!=expected_knowledge_revision: raise ProtocolError('Publication knowledge differs from expected revision')
        return result

    async def published(self,name):
        name=_name(name)
        return self._publication(await self._request('GET','publications/'+name,params={'protocol':PROTOCOL}),name)
