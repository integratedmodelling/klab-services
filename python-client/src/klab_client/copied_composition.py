"""Public opt-in copied reference composition, never native live federation."""
from dataclasses import dataclass,field
import hashlib,json,math,re
from typing import Mapping
from urllib.parse import urlsplit

from ._inspection import freeze,_reject_credential_leaves
from .client import Client
from .errors import InvalidRequestError,KlabError,ProtocolError,SubmissionOutcomeUnknown,UnsupportedOperationError
from .evidence import _plain
from .read_plans import (open_read_plan,read_plan_shard,release_read_plan,assemble_read_plan,
    async_open_read_plan,async_read_plan_shard,async_release_read_plan)
from .shared_access import _SharedAccess

PROTOCOL='klab.experimental.copied-composition/1'
PREFIX='/experimental/copied-composition/v1/'
REVISION_KIND='REFERENCE_CONTENT_SHA256_NOT_NATIVE_COMMIT'
AUTHORITY='CALLER_ASSERTED_COPIED_REFERENCE_NOT_REMOTE_GRANT'


@dataclass(frozen=True)
class CopiedSourceIdentity:
    origin:Mapping
    epoch:str
    plan_id:str
    revision:str
    revision_kind:str=REVISION_KIND

    def to_wire(self):
        return {'origin':_plain(self.origin),'epoch':self.epoch,'plan_id':self.plan_id,
            'revision':self.revision,'revision_kind':self.revision_kind}


@dataclass(frozen=True)
class CopiedSourceSnapshot:
    identity:CopiedSourceIdentity
    descriptor:Mapping
    values:tuple=field(repr=False)
    valid:tuple=field(repr=False)
    authority:str=AUTHORITY

    def to_wire(self):
        return {**self.identity.to_wire(),'descriptor':_plain(self.descriptor),'values':list(self.values),'valid':list(self.valid)}


@dataclass(frozen=True)
class CopiedCompositionResult:
    result_id:str
    runtime_id:str
    context_id:str
    source_vector:tuple[CopiedSourceIdentity,...]
    descriptor:Mapping
    values:tuple=field(repr=False)
    valid:tuple=field(repr=False)
    revision:str
    evidence:str=AUTHORITY
    live_federation:bool=False
    durable:bool=False


def _digest(values,valid):
    return hashlib.sha256(json.dumps({'values':values,'valid':valid},sort_keys=True,separators=(',',':'),allow_nan=False).encode()).hexdigest()


def _data(descriptor,values,valid,revision):
    if (not isinstance(descriptor,dict) or set(descriptor)!={'shape','crs','transform','units','dtype','order'}
            or descriptor['dtype']!='float64' or descriptor['order']!='ROW_MAJOR_NORTH_TO_SOUTH'
            or not isinstance(descriptor['shape'],list) or len(descriptor['shape'])!=2
            or any(type(n) is not int or not 1<=n<=4096 for n in descriptor['shape'])
            or not isinstance(descriptor['crs'],str) or not 1<=len(descriptor['crs'])<=4096
            or not isinstance(descriptor['units'],str) or not 1<=len(descriptor['units'])<=256
            or not isinstance(descriptor['transform'],list) or len(descriptor['transform'])!=6
            or any(type(n) not in (int,float) or abs(n)>10**12 or not math.isfinite(n) for n in descriptor['transform'])):
        raise ProtocolError('Copied reference requires exact finite shape/coordinates/units, no inferred mediation')
    cells=math.prod(descriptor['shape'])
    if (cells>4096 or not isinstance(values,list) or not isinstance(valid,list) or len(values)!=cells or len(valid)!=cells
            or any(type(ok) is not bool for ok in valid)
            or any((type(v) not in (int,float) or abs(v)>1e308 or not math.isfinite(v)) if ok else v is not None for v,ok in zip(values,valid))
            or not isinstance(revision,str) or re.fullmatch('[0-9a-f]{64}',revision) is None or _digest(values,valid)!=revision):
        raise ProtocolError('Copied values/masks/content digest invalid or beyond finite cell budget')


def _identity(value):
    if not isinstance(value,dict) or set(value)!={'origin','epoch','plan_id','revision','revision_kind'} or value['revision_kind']!=REVISION_KIND:
        raise ProtocolError('Exact origin-qualified finite reference identity required')
    origin=value['origin']
    if (not isinstance(origin,dict) or set(origin)!={'runtime_url','runtime_id','context_id','observation_id'}
            or any(not isinstance(origin[key],str) or not 1<=len(origin[key])<=4096 for key in ('runtime_url','runtime_id','context_id'))
            or type(origin['observation_id']) is not int or not 0<origin['observation_id']<2**63
            or re.fullmatch(r'[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+',origin['context_id']) is None):
        raise ProtocolError('Copied source origin/root/observation identity invalid')
    try:
        url=urlsplit(origin['runtime_url'])
        url.port  # Validate an explicit numeric port even though C never fetches origins.
        if url.scheme not in ('http','https') or not url.hostname or url.username or url.password or url.query or url.fragment:
            raise ValueError('Unsafe copied source URL')
    except ValueError:raise ProtocolError('Copied source URL must be public origin metadata, never a delegated credential') from None
    for key,length in (('epoch',32),('plan_id',32),('revision',64)):
        if not isinstance(value[key],str) or re.fullmatch('[0-9a-f]{'+str(length)+'}',value[key]) is None:
            raise ProtocolError('Copied source epoch/plan/content revision invalid')
    return CopiedSourceIdentity(freeze(origin),value['epoch'],value['plan_id'],value['revision'])


def _snapshot(value):
    if not isinstance(value,dict) or set(value)!={'origin','epoch','plan_id','revision','revision_kind','descriptor','values','valid'}:
        raise ProtocolError('Exact complete copied source snapshot required')
    identity=_identity({key:value[key] for key in ('origin','epoch','plan_id','revision','revision_kind')})
    _data(value['descriptor'],value['values'],value['valid'],value['revision'])
    return CopiedSourceSnapshot(identity,freeze(value['descriptor']),tuple(value['values']),tuple(value['valid']))


def _capture(plan,data):
    value={'origin':{'runtime_url':plan.source.runtime_url,'runtime_id':plan.source.runtime_id,
        'context_id':plan.source.context_id,'observation_id':plan.source.observation_id},
        'epoch':plan.epoch,'plan_id':plan.plan_id,'revision':plan.revision,'revision_kind':plan.revision_kind,
        'descriptor':{'shape':list(plan.shape),'crs':plan.crs,'transform':list(plan.transform),'units':plan.units,
            'dtype':plan.dtype,'order':plan.order},'values':list(data.values),'valid':list(data.valid)}
    try:_reject_credential_leaves(value,plan.context.client.transport.redact,'Copied source contains configured credential material')
    except InvalidRequestError:raise ProtocolError('Copied source contains configured credential material; nothing delegated') from None
    return _snapshot(value)


def capture_copied_source(observation,*,allow_experimental=False):
    """Read independently authorized source; release read grant, retain only copied data."""
    plan=open_read_plan(observation,allow_experimental=allow_experimental)
    try:
        shards=tuple(read_plan_shard(plan,spec.index,allow_experimental=allow_experimental) for spec in plan.shards)
        return _capture(plan,assemble_read_plan(plan,shards))
    finally:release_read_plan(plan,allow_experimental=allow_experimental)


async def async_capture_copied_source(observation,*,allow_experimental=False):
    plan=await async_open_read_plan(observation,allow_experimental=allow_experimental)
    try:
        shards=[]
        for spec in plan.shards:shards.append(await async_read_plan_shard(plan,spec.index,allow_experimental=allow_experimental))
        return _capture(plan,assemble_read_plan(plan,tuple(shards)))
    finally:await async_release_read_plan(plan,allow_experimental=allow_experimental)


class _Compositions(_SharedAccess):
    def _configure(self,context,allow_experimental,client_type):
        self._bind(context,allow_experimental,client_type)
        self._client=context.client;self._transport=context.client.transport;self._runtime=self._transport.endpoint('runtime')

    def _fence(self):
        try:self._options()
        except InvalidRequestError:raise ProtocolError('Copied composition root/Runtime binding changed') from None
        if self.context.client is not self._client or self._client.transport is not self._transport or self._transport.endpoint('runtime')!=self._runtime:
            raise ProtocolError('Copied composition client/endpoint/credential changed; no implicit rebind')

    def _capability(self,capability):
        if capability.service_id!=self.reference.runtime_id:raise ProtocolError('Composition Runtime identity changed')
        self._fence()
        if capability.raw.get('copiedCompositionProtocol')!=PROTOCOL:
            raise UnsupportedOperationError('Exact copied-composition reference not advertised; no native federation fallback')

    def _safe(self,value):
        freeze(value)
        try:_reject_credential_leaves(value,self._transport.redact,'Copied reference contains configured credential material')
        except InvalidRequestError:raise ProtocolError('Copied reference contains configured credential material') from None

    def _body(self,sources,expected_vector):
        self._fence()
        if (not isinstance(sources,(tuple,list)) or len(sources)!=2
                or any(not isinstance(source,CopiedSourceSnapshot) or not isinstance(source.identity,CopiedSourceIdentity)
                    or not isinstance(source.values,tuple) or not isinstance(source.valid,tuple)
                    or len(source.values)>4096 or len(source.valid)>4096 for source in sources)):
            raise InvalidRequestError('Choose exactly two explicit independently captured copied source snapshots')
        snapshots=[_snapshot(source.to_wire()) for source in sources]
        self._safe([source.to_wire() for source in snapshots])
        identities=self._vector(expected_vector)
        if tuple(source.identity for source in snapshots)!=identities:
            raise InvalidRequestError('Pinned copied-source vector differs; no automatic source refresh')
        origins=[source.identity.origin['runtime_id'] for source in snapshots]
        if len(set(origins))!=2 or self.reference.runtime_id in origins or snapshots[0].descriptor!=snapshots[1].descriptor:
            raise InvalidRequestError('Choose distinct A/B Runtimes, not C; exact descriptors required, no mediation')
        body={'protocol':PROTOCOL,'operation':'PAIRWISE_SUM_REFERENCE','sources':[source.to_wire() for source in snapshots],
            'expected_vector':[identity.to_wire() for identity in identities]}
        if len(json.dumps(body,separators=(',',':')).encode())>262144:raise InvalidRequestError('Copied source upload exceeds finite262144byte budget')
        return body

    def _vector(self,vector):
        if not isinstance(vector,(list,tuple)) or len(vector)!=2 or any(not isinstance(item,CopiedSourceIdentity) for item in vector):
            raise InvalidRequestError('An explicit exact two-source origin/epoch/plan/content revision vector is required')
        return tuple(_identity(item.to_wire()) for item in vector)

    def _decode(self,value,expected_vector,expected_descriptor,expected_id=None):
        self._safe(value)
        keys={'protocol','result_id','origin','source_vector','descriptor','values','valid','revision','revision_kind',
            'source_authority','operation','live_federation','durable'}
        if (not isinstance(value,dict) or set(value)!=keys or value['protocol']!=PROTOCOL
                or not isinstance(value['result_id'],str) or re.fullmatch('[1-9][0-9]{0,18}',value['result_id']) is None
                or expected_id is not None and value['result_id']!=expected_id
                or value['origin']!={'runtime_id':self.reference.runtime_id,'context_id':self.reference.context_id}
                or value['revision_kind']!=REVISION_KIND or value['source_authority']!=AUTHORITY
                or value['operation']!='PAIRWISE_SUM_REFERENCE' or value['live_federation'] is not False or value['durable'] is not False
                or not isinstance(value['source_vector'],list) or len(value['source_vector'])!=2):
            raise ProtocolError('Copied C result must confirm exact finite scope/result/source authority, never live federation')
        vector=tuple(_identity(item) for item in value['source_vector'])
        if vector!=expected_vector:raise ProtocolError('Composition result source vector differs from explicit pins')
        if value['descriptor']!=_plain(expected_descriptor):raise ProtocolError('Composition result descriptor differs; no implicit mediation')
        _data(value['descriptor'],value['values'],value['valid'],value['revision'])
        return CopiedCompositionResult(value['result_id'],self.reference.runtime_id,self.reference.context_id,vector,
            freeze(value['descriptor']),tuple(value['values']),tuple(value['valid']),value['revision'])

    def _receipt(self,result):
        self._fence()
        if (not isinstance(result,CopiedCompositionResult) or result.runtime_id!=self.reference.runtime_id
                or result.context_id!=self.reference.context_id or not isinstance(result.result_id,str)
                or re.fullmatch('[1-9][0-9]{0,18}',result.result_id) is None):
            raise InvalidRequestError('Choose one explicit C-owned copied result; source receipts are not release grants')
        return result.result_id,self._vector(result.source_vector)


class CopiedCompositions(_Compositions):
    def __init__(self,context,*,allow_experimental=False):self._configure(context,allow_experimental,Client)

    def _request(self,method,route,**kwargs):
        self._fence();self._capability(self._client.runtime.capabilities(max_response_bytes=65536))
        value=self._transport.request('runtime',method,PREFIX+route,**{**self._options(),'max_response_bytes':262144},**kwargs)
        try:self._fence();self._capability(self._client.runtime.capabilities(max_response_bytes=65536))
        except KlabError:
            if method!='GET':raise SubmissionOutcomeUnknown('Composition outcome unknown; inspect C explicitly, never replay mutation') from None
            raise
        return value

    def compose(self,sources,*,expected_vector):
        body=self._body(sources,expected_vector)
        pins=tuple(_identity(item) for item in body['expected_vector'])
        value=self._request('POST','create',json=body,ambiguous=True)
        try:return self._decode(value,pins,body['sources'][0]['descriptor'])
        except ProtocolError:raise SubmissionOutcomeUnknown('Composition acknowledgement incomplete; inspect C, never replay blindly') from None

    def inspect(self,result):
        identity,vector=self._receipt(result)
        descriptor=freeze(_plain(result.descriptor))
        return self._decode(self._request('GET',identity),vector,descriptor,identity)

    def release(self,result):
        identity,_=self._receipt(result)
        value=self._request('DELETE',identity,ambiguous=True)
        if value!={'released':True,'source_mutations':0} or value.get('released') is not True or type(value.get('source_mutations')) is not int:
            raise SubmissionOutcomeUnknown('Composition release acknowledgement incomplete; no source mutation or rollback inferred')
        return True


class AsyncCopiedCompositions(_Compositions):
    def __init__(self,context,*,allow_experimental=False):
        from .async_client import AsyncClient
        self._configure(context,allow_experimental,AsyncClient)

    async def _request(self,method,route,**kwargs):
        self._fence();self._capability(await self._client.runtime.capabilities(max_response_bytes=65536))
        value=await self._transport.request('runtime',method,PREFIX+route,**{**self._options(),'max_response_bytes':262144},**kwargs)
        try:self._fence();self._capability(await self._client.runtime.capabilities(max_response_bytes=65536))
        except KlabError:
            if method!='GET':raise SubmissionOutcomeUnknown('Composition outcome unknown; inspect C explicitly, never replay mutation') from None
            raise
        return value

    async def compose(self,sources,*,expected_vector):
        body=self._body(sources,expected_vector)
        pins=tuple(_identity(item) for item in body['expected_vector'])
        value=await self._request('POST','create',json=body,ambiguous=True)
        try:return self._decode(value,pins,body['sources'][0]['descriptor'])
        except ProtocolError:raise SubmissionOutcomeUnknown('Composition acknowledgement incomplete; inspect C, never replay blindly') from None

    async def inspect(self,result):
        identity,vector=self._receipt(result)
        descriptor=freeze(_plain(result.descriptor))
        return self._decode(await self._request('GET',identity),vector,descriptor,identity)

    async def release(self,result):
        identity,_=self._receipt(result)
        value=await self._request('DELETE',identity,ambiguous=True)
        if value!={'released':True,'source_mutations':0} or value.get('released') is not True or type(value.get('source_mutations')) is not int:
            raise SubmissionOutcomeUnknown('Composition release acknowledgement incomplete; no source mutation or rollback inferred')
        return True
