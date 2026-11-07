"""Explicit finite state/window read-plan selections; never native time or mediation."""
from dataclasses import dataclass
from datetime import datetime
import hashlib,json,math,re

from ._inspection import freeze,_reject_credential_leaves
from .client import Client
from .errors import InvalidRequestError,KlabError,ProtocolError,SubmissionOutcomeUnknown,UnsupportedOperationError
from .handles import Handle,to_handle
from .read_plans import (PROTOCOL as PLAN_PROTOCOL,ReadPlan,ReadPlanData,ReadShardSpec,_json,_plan,read_plan_shard,release_read_plan,assemble_read_plan,
    async_read_plan_shard,async_release_read_plan)
from .shared_access import _SharedAccess

PROTOCOL='klab.experimental.read-plan-selections/1'
PREFIX='/experimental/read-plan-selections/v1/'
KIND='DECLARED_FINITE_STATE_WINDOW_NOT_NATIVE_TIME_OR_COMMIT'


@dataclass(frozen=True)
class ReferenceWindow:
    row_start:int
    row_stop:int
    column_start:int
    column_stop:int

    def to_wire(self):
        values=(self.row_start,self.row_stop,self.column_start,self.column_stop)
        if (any(type(value) is not int for value in values) or not 0<=self.row_start<self.row_stop<=4096
                or not 0<=self.column_start<self.column_stop<=4096
                or (self.row_stop-self.row_start)*(self.column_stop-self.column_start)>4096):
            raise InvalidRequestError('Choose a bounded nonempty half-open integer index window')
        return dict(zip(('rowStart','rowStop','columnStart','columnStop'),values))


@dataclass(frozen=True)
class ReferenceTemporalState:
    state_id:str
    label:str
    instant:str
    runtime_id:str
    context_id:str
    observation_id:int
    epoch:str
    source_revision:str
    shape:tuple[int,int]
    crs:str
    transform:tuple
    units:str | None
    evidence:str=KIND

    def to_wire(self):
        return {'id':self.state_id,'label':self.label,'instant':self.instant,'runtimeId':self.runtime_id,
            'contextId':self.context_id,'observationId':self.observation_id,'epoch':self.epoch,
            'sourceRevision':self.source_revision,'shape':list(self.shape),'crs':self.crs,
            'transform':list(self.transform),'units':self.units}


@dataclass(frozen=True)
class SelectedReadPlan:
    plan:ReadPlan
    window:ReferenceWindow
    temporal_state:ReferenceTemporalState
    evidence:str=KIND


@dataclass(frozen=True)
class SelectedReadData:
    data:ReadPlanData
    selection:SelectedReadPlan
    evidence:str=KIND


def _hex(value,n):return isinstance(value,str) and re.fullmatch('[0-9a-f]{'+str(n)+'}',value) is not None


def _decode_state(value,runtime_id,context_id,observation_id):
    freeze(value)
    fields={'id','label','instant','runtimeId','contextId','observationId','epoch','sourceRevision','shape','crs','transform','units'}
    if (not isinstance(value,dict) or set(value)!=fields or value['runtimeId']!=runtime_id
            or value['contextId']!=context_id or type(value['observationId']) is not int
            or value['observationId']!=observation_id or not _hex(value['epoch'],32) or not _hex(value['sourceRevision'],64)
            or any(not isinstance(value[key],str) or not 1<=len(value[key])<=256 for key in ('id','label','instant','crs'))
            or re.fullmatch('[A-Za-z0-9_-]{1,64}',value['id']) is None
            or not isinstance(value['shape'],list) or len(value['shape'])!=2
            or any(type(n) is not int or not 1<=n<=4096 for n in value['shape']) or math.prod(value['shape'])>4096
            or not isinstance(value['transform'],list) or len(value['transform'])!=6
            or any(type(n) not in (int,float) or abs(n)>10**12 or not math.isfinite(n) for n in value['transform'])
            or value['transform'][0]<=0 or value['transform'][4]>=0 or value['transform'][1]!=0 or value['transform'][3]!=0
            or value['units'] is not None and (not isinstance(value['units'],str) or not 1<=len(value['units'])<=256)):
        raise ProtocolError('Exact finite source state/shape/layout/units/content identity required')
    try:
        instant=datetime.strptime(value['instant'],'%Y-%m-%dT%H:%M:%SZ')
        if instant.strftime('%Y-%m-%dT%H:%M:%SZ')!=value['instant']:raise ValueError('Noncanonical time')
    except ValueError:raise ProtocolError('Declared state instant must be exact UTC calendar text; no guessed time semantics') from None
    return ReferenceTemporalState(value['id'],value['label'],value['instant'],value['runtimeId'],value['contextId'],
        observation_id,value['epoch'],value['sourceRevision'],tuple(value['shape']),value['crs'],tuple(value['transform']),value['units'])


def analyze_selected_read_plan(snapshot,*,max_bytes=64*1024*1024):
    """Offline arrays retain exact server-listed time/window/source pins, never a grant."""
    from .read_plan_analysis import analyze_read_plan
    if (not isinstance(snapshot,SelectedReadData) or not isinstance(snapshot.selection,SelectedReadPlan)
            or not isinstance(snapshot.data,ReadPlanData) or snapshot.data.plan!=snapshot.selection.plan
            or not isinstance(snapshot.data.plan,ReadPlan) or not isinstance(snapshot.data.plan.source,Handle)
            or not isinstance(snapshot.selection.temporal_state,ReferenceTemporalState)
            or not isinstance(snapshot.selection.window,ReferenceWindow)
            or type(max_bytes) is not int or max_bytes<=8192):
        raise InvalidRequestError('Analysis requires explicit complete selected data and bounded retained-metadata budget')
    plan=snapshot.data.plan;source=plan.source
    state=_decode_state(snapshot.selection.temporal_state.to_wire(),source.runtime_id,source.context_id,source.observation_id)
    window=snapshot.selection.window;window.to_wire()
    r0,r1,c0,c1=window.row_start,window.row_stop,window.column_start,window.column_stop
    sx,_,west,_,sy,north=state.transform
    if (r1>state.shape[0] or c1>state.shape[1] or plan.shape!=(r1-r0,c1-c0) or plan.epoch!=state.epoch
            or plan.units!=state.units or plan.crs!=state.crs or plan.transform!=(sx,0,west+c0*sx,0,sy,north+r0*sy)):
        raise ProtocolError('Detached selected layout/state/window pins disagree; no inferred mediation')
    analysis=analyze_read_plan(snapshot.data,max_bytes=max_bytes-8192)
    metadata={'protocol':PROTOCOL,'kind':KIND,'state':state.to_wire(),'window':window.to_wire(),
        'readPlanManifestSha256':analysis.manifest_sha256}
    digest=hashlib.sha256(json.dumps(metadata,sort_keys=True,separators=(',',':'),allow_nan=False).encode()).hexdigest()
    analysis.dataset.attrs.update(selection_protocol=PROTOCOL,selection_kind=KIND,reference_state_id=state.state_id,
        reference_state_label=state.label,reference_state_instant=state.instant,source_state_revision=state.source_revision,
        selection_window=(r0,r1,c0,c1),reference_selection_sha256=digest,
        selection_hash_kind='LOCAL_REFERENCE_SELECTION_NOT_NATIVE_TIME_OR_COMMIT')
    return analysis.dataset


class _Windows(_SharedAccess):
    def _configure(self,context,allow_experimental,client_type):
        self._bind(context,allow_experimental,client_type)
        self._client=context.client;self._transport=self._client.transport;self._endpoint=self._transport.endpoint('runtime')

    def _fence(self):
        try:self._options()
        except InvalidRequestError:raise ProtocolError('Selected read-plan root/Runtime binding changed') from None
        if self.context.client is not self._client or self._client.transport is not self._transport or self._endpoint!=self._transport.endpoint('runtime'):
            raise ProtocolError('Selected read-plan client/endpoint/credential changed; no implicit rebind')

    def _capability(self,value):
        self._fence()
        if value.service_id!=self.reference.runtime_id:raise ProtocolError('Selected read-plan Runtime identity changed')
        if value.raw.get('readPlanSelectionProtocol')!=PROTOCOL:
            raise UnsupportedOperationError('Finite state/window protocol not advertised; no native fallback')

    def _safe(self,value):
        freeze(value)
        try:_reject_credential_leaves(value,self._transport.redact,'Selected reference metadata contains configured credential material')
        except InvalidRequestError:raise ProtocolError('Selected reference metadata contains configured credential material') from None

    def _observation(self,observation):
        self._fence();source=to_handle(observation)
        if (source.context_id!=self.reference.context_id or source.runtime_id!=self.reference.runtime_id
                or getattr(getattr(observation,'_context',None),'client',None) is not self._client
                or type(source.observation_id) is not int or not 0<source.observation_id<2**53):
            raise InvalidRequestError('Choose an explicit observation in the exact client/root/Runtime')
        return source

    def _state(self,value,observation_id):
        self._safe(value)
        return _decode_state(value,self.reference.runtime_id,self.reference.context_id,observation_id)

    def _catalog(self,value,source):
        self._safe(value)
        if (not isinstance(value,dict) or set(value)!={'protocol','kind','states'} or value['protocol']!=PROTOCOL or value['kind']!=KIND
                or not isinstance(value['states'],list) or not 1<=len(value['states'])<=8):
            raise ProtocolError('Bounded advertised finite state catalog required')
        result=tuple(self._state(row,source.observation_id) for row in value['states'])
        if len({row.state_id for row in result})!=len(result) or len({row.epoch for row in result})!=1:
            raise ProtocolError('State catalog identities/epoch disagree')
        return result

    def _selection_body(self,observation,window,state,shard_cells,lease_seconds,max_cells):
        source=self._observation(observation)
        if not isinstance(window,ReferenceWindow) or not isinstance(state,ReferenceTemporalState):
            raise InvalidRequestError('Choose explicit bounded window and server-listed temporal state')
        selected=window.to_wire()
        state=self._state(state.to_wire(),source.observation_id)
        if (window.row_stop>state.shape[0] or window.column_stop>state.shape[1]
                or type(shard_cells) is not int or not 1<=shard_cells<=128
                or type(lease_seconds) is not int or not 1<=lease_seconds<=60
                or type(max_cells) is not int or not 1<=max_cells<=4096
                or (window.row_stop-window.row_start)*(window.column_stop-window.column_start)>max_cells):
            raise InvalidRequestError('Window outside exact selected source state or finite grant budgets')
        return source,state,{'protocol':PROTOCOL,'observationId':source.observation_id,'window':selected,
            'stateId':state.state_id,'sourceRevision':state.source_revision,'epoch':state.epoch,
            'shardCells':shard_cells,'leaseSeconds':lease_seconds,'maxCells':max_cells}

    def _opened(self,value,source,state,body):
        self._safe(value)
        if (not isinstance(value,dict) or set(value)!={'protocol','kind','state','window','plan'} or value['protocol']!=PROTOCOL
                or value['kind']!=KIND or value['state']!=state.to_wire() or value['window']!=body['window']):
            raise ProtocolError('Selected grant did not confirm exact request-time source/window/state pins')
        # Python equality alone treats True==1 and False==0. Revalidate wire
        # identities and exact integer offsets before accepting the association.
        self._state(value['state'],source.observation_id)
        if any(type(offset) is not int for offset in value['window'].values()):
            raise ProtocolError('Selected grant window offsets must be exact integers, not booleans')
        plan=_plan(value['plan'],self.context,source,body['shardCells'],body['maxCells'])
        r0,r1,c0,c1=(body['window'][key] for key in ('rowStart','rowStop','columnStart','columnStop'))
        sx,_,west,_,sy,north=state.transform
        if (plan.shape!=(r1-r0,c1-c0) or plan.crs!=state.crs or plan.units!=state.units or plan.epoch!=state.epoch
                or plan.transform!=(sx,0,west+c0*sx,0,sy,north+r0*sy)):
            raise ProtocolError('Selected grant layout differs from exact finite index window; no mediation')
        return SelectedReadPlan(plan,ReferenceWindow(r0,r1,c0,c1),state)

    def _selected(self,selection):
        self._fence()
        if (not isinstance(selection,SelectedReadPlan) or not isinstance(selection.plan,ReadPlan)
                or selection.plan.context is not self.context or not isinstance(selection.temporal_state,ReferenceTemporalState)
                or not isinstance(selection.window,ReferenceWindow) or not isinstance(selection.plan.source,Handle)
                or type(selection.plan.shards) is not tuple or not 1<=len(selection.plan.shards)<=4096
                or any(not isinstance(spec,ReadShardSpec) or type(spec.count) is not int or not 1<=spec.count<=128 for spec in selection.plan.shards)):
            raise InvalidRequestError('Use one established selected grant from the exact originating facade context')
        plan=selection.plan;source=plan.source
        if (source.context_id!=self.reference.context_id or source.runtime_id!=self.reference.runtime_id
                or source.runtime_url!=to_handle(self.context).runtime_url or not _hex(plan.plan_id,32)):
            raise InvalidRequestError('Selected grant belongs to another exact source/root/Runtime')
        state=self._state(selection.temporal_state.to_wire(),source.observation_id)
        manifest={'protocol':PLAN_PROTOCOL,'plan_id':plan.plan_id,'epoch':plan.epoch,'revision':plan.revision,
            'revision_kind':plan.revision_kind,'observation_id':source.observation_id,'scope':source.scope,
            'runtime_id':source.runtime_id,'dtype':plan.dtype,'shape':list(plan.shape),'crs':plan.crs,
            'transform':list(plan.transform),'order':plan.order,'units':plan.units,'fixture_origin':plan.fixture_origin,
            'shards':[{'index':spec.index,'start':spec.start,'count':spec.count,'sha256':spec.sha256} for spec in plan.shards]}
        window=selection.window.to_wire()
        self._opened({'protocol':PROTOCOL,'kind':KIND,'state':state.to_wire(),'window':window,'plan':manifest},source,state,
            {'window':window,'shardCells':max(spec.count for spec in plan.shards),'maxCells':4096})
        return plan


class ReadPlanSelections(_Windows):
    def __init__(self,context,*,allow_experimental=False):self._configure(context,allow_experimental,Client)

    def _request(self,method,route,**kwargs):
        self._fence();self._capability(self._client.runtime.capabilities(max_response_bytes=65536))
        raw=self._transport.request('runtime',method,PREFIX+route,**{**self._options(),'max_response_bytes':65536,'response':'bytes'},**kwargs)
        try:self._fence();self._capability(self._client.runtime.capabilities(max_response_bytes=65536));return _json(raw)
        except KlabError:
            if method=='POST':raise SubmissionOutcomeUnknown('Selected grant outcome unknown; no automatic open replay') from None
            raise

    def states(self,observation):
        source=self._observation(observation)
        return self._catalog(self._request('GET','observations/'+str(source.observation_id)+'/states',params={'protocol':PROTOCOL}),source)

    def open(self,observation,window,state,*,shard_cells=128,lease_seconds=30,max_cells=4096):
        source,pinned,body=self._selection_body(observation,window,state,shard_cells,lease_seconds,max_cells)
        value=self._request('POST','open',json=body,ambiguous=True)
        try:return self._opened(value,source,pinned,body)
        except ProtocolError:raise SubmissionOutcomeUnknown('Selected grant acknowledgement incomplete; no assumed rollback/replan') from None

    def read(self,selection):
        plan=self._selected(selection);self._capability(self._client.runtime.capabilities(max_response_bytes=65536))
        shards=tuple(read_plan_shard(plan,spec.index,allow_experimental=True) for spec in plan.shards)
        self._fence();self._capability(self._client.runtime.capabilities(max_response_bytes=65536))
        return SelectedReadData(assemble_read_plan(plan,shards),selection)

    def release(self,selection):
        plan=self._selected(selection)
        try:result=release_read_plan(plan,allow_experimental=True);self._fence()
        except ProtocolError:raise SubmissionOutcomeUnknown('Selected grant release acknowledgement unknown; no automatic replay') from None
        return result

    def capture(self,observation,window,state,**budgets):
        """Read one established grant and attempt release even on read failure."""
        selection=self.open(observation,window,state,**budgets)
        try:return self.read(selection)
        finally:self.release(selection)


class AsyncReadPlanSelections(_Windows):
    def __init__(self,context,*,allow_experimental=False):
        from .async_client import AsyncClient
        self._configure(context,allow_experimental,AsyncClient)

    async def _request(self,method,route,**kwargs):
        self._fence();self._capability(await self._client.runtime.capabilities(max_response_bytes=65536))
        raw=await self._transport.request('runtime',method,PREFIX+route,**{**self._options(),'max_response_bytes':65536,'response':'bytes'},**kwargs)
        try:self._fence();self._capability(await self._client.runtime.capabilities(max_response_bytes=65536));return _json(raw)
        except KlabError:
            if method=='POST':raise SubmissionOutcomeUnknown('Selected grant outcome unknown; no automatic open replay') from None
            raise

    async def states(self,observation):
        source=self._observation(observation)
        return self._catalog(await self._request('GET','observations/'+str(source.observation_id)+'/states',params={'protocol':PROTOCOL}),source)

    async def open(self,observation,window,state,*,shard_cells=128,lease_seconds=30,max_cells=4096):
        source,pinned,body=self._selection_body(observation,window,state,shard_cells,lease_seconds,max_cells)
        value=await self._request('POST','open',json=body,ambiguous=True)
        try:return self._opened(value,source,pinned,body)
        except ProtocolError:raise SubmissionOutcomeUnknown('Selected grant acknowledgement incomplete; no assumed rollback/replan') from None

    async def read(self,selection):
        plan=self._selected(selection);self._capability(await self._client.runtime.capabilities(max_response_bytes=65536))
        shards=[]
        for spec in plan.shards:shards.append(await async_read_plan_shard(plan,spec.index,allow_experimental=True))
        self._fence();self._capability(await self._client.runtime.capabilities(max_response_bytes=65536))
        return SelectedReadData(assemble_read_plan(plan,tuple(shards)),selection)

    async def release(self,selection):
        plan=self._selected(selection)
        try:result=await async_release_read_plan(plan,allow_experimental=True);self._fence()
        except ProtocolError:raise SubmissionOutcomeUnknown('Selected grant release acknowledgement unknown; no automatic replay') from None
        return result

    async def capture(self,observation,window,state,**budgets):
        selection=await self.open(observation,window,state,**budgets)
        try:return await self.read(selection)
        finally:await self.release(selection)
