"""Explicit server-declared finite raster labels; not native semantic/time authority."""
from dataclasses import dataclass
from datetime import datetime
import hashlib,json,math,re

from ._inspection import freeze,_reject_credential_leaves
from .analysis import AnalysisArtifact,DEFAULT_ANALYSIS_BYTES,RasterBandAxis,open_raster
from .client import Client
from .errors import InvalidRequestError,ProtocolError,UnsupportedOperationError
from .evidence import _plain
from .handles import Handle,to_handle
from .raster_categories import RasterCategories,categorize_raster
from .read_plans import _json
from .shared_access import _SharedAccess

PROTOCOL='klab.experimental.reference-raster-metadata/1'
PREFIX='/experimental/reference-raster-metadata/v1/'
KIND='SERVER_DECLARED_FINITE_RASTER_LABELS_NOT_NATIVE_SEMANTIC_AUTHORITY'


@dataclass(frozen=True)
class ReferenceRasterMetadata:
    source:Handle
    payload:object
    evidence:str=KIND

    def to_wire(self):return _plain(self.payload)


@dataclass(frozen=True)
class ReferenceRasterAnalysis:
    dataset:object
    metadata:ReferenceRasterMetadata
    raster:object
    evidence:str=KIND


def _hash(value):return isinstance(value,str) and re.fullmatch('[0-9a-f]{64}',value) is not None


def _decode(value,source):
    freeze(value)
    fields={'protocol','kind','runtimeId','contextId','observationId','epoch','exportSha256','arraySha256',
        'shape','dtype','crs','transform','units','nodata','dictionary','worldviewId','worldviewRevision',
        'worldviewServiceId','bandLabels','bandInstants'}
    if (not isinstance(source,Handle) or source.kind!='observation' or not isinstance(value,dict) or set(value)!=fields
            or value['protocol']!=PROTOCOL or value['kind']!=KIND or value['runtimeId']!=source.runtime_id
            or value['contextId']!=source.context_id or type(value['observationId']) is not int
            or value['observationId']!=source.observation_id or not isinstance(value['epoch'],str)
            or re.fullmatch('[0-9a-f]{32}',value['epoch']) is None
            or not _hash(value['exportSha256']) or not _hash(value['arraySha256'])
            or not isinstance(value['shape'],list) or len(value['shape']) not in (2,3)
            or any(type(n) is not int or not 1<=n<=4096 for n in value['shape']) or math.prod(value['shape'])>4096
            or value['dtype'] not in ('int16','int32','int64','uint8','uint16','uint32','float32','float64')
            or any(not isinstance(value[key],str) or not 1<=len(value[key])<=256 for key in ('crs','worldviewId','worldviewServiceId'))
            or re.fullmatch(r'EPSG:[1-9][0-9]{0,5}',value['crs']) is None
            or type(value['worldviewRevision']) is not int or not 0<=value['worldviewRevision']<2**53
            or not isinstance(value['transform'],list) or len(value['transform'])!=6
            or any(type(n) not in (int,float) or abs(n)>10**12 or not math.isfinite(n) for n in value['transform'])
            or value['transform'][0]<=0 or value['transform'][4]>=0 or value['transform'][1]!=0 or value['transform'][3]!=0
            or value['units'] is not None and (not isinstance(value['units'],str) or not 1<=len(value['units'])<=256)
            or value['nodata'] is not None and (type(value['nodata']) not in (int,float) or abs(value['nodata'])>2**63 or not math.isfinite(value['nodata']))):
        raise ProtocolError('Exact finite raster/source/layout/content/worldview declaration required')
    entries=value['dictionary'];labels=value['bandLabels'];instants=value['bandInstants']
    if (not isinstance(entries,list) or len(entries)>256 or not isinstance(labels,list) or len(labels)>256
            or not isinstance(instants,list) or len(instants)!=len(labels)
            or any(not isinstance(label,str) or not label.strip() or len(label)>256 for label in labels)
            or len(set(labels))!=len(labels) or bool(labels)!=(len(value['shape'])==3)
            or labels and len(labels)!=value['shape'][0] or not entries and not labels):
        raise ProtocolError('Explicit bounded category dictionary or exact band labels required')
    if entries:
        if value['dtype'].startswith('float'):raise ProtocolError('Categorical declarations require raw integer dtype, no conversion')
        try:RasterCategories(tuple(tuple(entry) if isinstance(entry,list) else () for entry in entries),KIND)
        except InvalidRequestError:raise ProtocolError('Category dictionary requires exact unique integer codes/labels') from None
    for instant in instants:
        try:
            if not isinstance(instant,str) or datetime.strptime(instant,'%Y-%m-%dT%H:%M:%SZ').strftime('%Y-%m-%dT%H:%M:%SZ')!=instant:
                raise ValueError('Noncanonical instant')
        except ValueError:raise ProtocolError('Explicit canonical declared UTC band instants required') from None
    return ReferenceRasterMetadata(source,freeze(value))


def _array_digest(values,valid):
    if values.shape!=valid.shape or valid.dtype.kind!='b' or values.dtype.kind not in 'iuf' or not 1<=values.size<=4096:
        raise ProtocolError('Exact finite numeric array and boolean mask required')
    numbers=[];mask=valid.reshape(-1).tolist()
    for number,flag in zip(values.reshape(-1).tolist(),mask):
        if type(number) is float and not math.isfinite(number):
            if flag:raise ProtocolError('Nonfinite valid cell cannot establish finite reference identity')
            number='NaN' if math.isnan(number) else 'POSITIVE_INFINITY' if number>0 else 'NEGATIVE_INFINITY'
        numbers.append(number)
    value={'dtype':str(values.dtype),'shape':list(values.shape),'values':numbers,'valid':mask}
    return hashlib.sha256(json.dumps(value,allow_nan=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()


def open_reference_raster(artifact,metadata,*,max_bytes=DEFAULT_ANALYSIS_BYTES):
    """Offline exact-byte/content association, preserving numerical codes and masks."""
    if (not isinstance(artifact,AnalysisArtifact) or not isinstance(metadata,ReferenceRasterMetadata)
            or metadata.source!=artifact.observation or metadata.evidence!=KIND
            or type(max_bytes) is not int or max_bytes<=8192):
        raise InvalidRequestError('Explicit source-matched raster artifact/metadata and finite allocation budget required')
    declared=_decode(metadata.to_wire(),metadata.source).to_wire()
    if artifact.sha256!=declared['exportSha256']:raise ProtocolError('Raster metadata describes different raw export bytes')
    metadata_bytes=len(json.dumps(declared,allow_nan=False).encode())+8192
    axis=RasterBandAxis('reference_band',tuple(declared['bandLabels']),KIND) if declared['bandLabels'] else None
    raster=open_raster(artifact,band_axis=axis,max_bytes=max_bytes-metadata_bytes)
    from rasterio.crs import CRS
    descriptor=raster.descriptor
    try:declared_crs=CRS.from_epsg(int(declared['crs'].split(':')[1]))
    except ValueError:raise ProtocolError('Declared reference CRS is unavailable or invalid; no guessed projection') from None
    if (descriptor.sha256!=declared['exportSha256'] or descriptor.shape!=tuple(declared['shape'])
            or descriptor.dtype!=declared['dtype'] or CRS.from_wkt(descriptor.crs)!=declared_crs
            or descriptor.transform!=tuple(declared['transform']) or descriptor.units!=declared['units']
            or descriptor.nodata!=declared['nodata']
            or _array_digest(raster.dataset['value'].values,raster.dataset['valid'].values)!=declared['arraySha256']):
        raise ProtocolError('Raw raster layout/value/mask/content differs from explicit server declaration')
    dataset=raster.dataset
    if declared['dictionary']:
        dictionary=RasterCategories(tuple(tuple(entry) for entry in declared['dictionary']),KIND)
        dataset=categorize_raster(raster,dictionary,max_bytes=max_bytes-metadata_bytes).dataset
        dataset['category'].attrs.update(category_source='SERVER_DECLARED_FINITE_REFERENCE',category_evidence=KIND)
    if declared['bandLabels']:
        dataset['value'].attrs.update(band_axis_source='SERVER_DECLARED_FINITE_REFERENCE',band_axis_provenance=KIND)
    dataset.attrs.update(reference_metadata_protocol=PROTOCOL,reference_metadata_evidence=KIND,
        reference_worldview_id=declared['worldviewId'],reference_worldview_revision=declared['worldviewRevision'],
        reference_worldview_service_id=declared['worldviewServiceId'],reference_array_sha256=declared['arraySha256'],
        reference_export_sha256=declared['exportSha256'],reference_band_instants=tuple(declared['bandInstants']),
        reference_metadata_sha256=hashlib.sha256(json.dumps(declared,allow_nan=False,sort_keys=True,separators=(',',':')).encode()).hexdigest())
    return ReferenceRasterAnalysis(dataset,metadata,raster)


class _Labels(_SharedAccess):
    def _configure(self,context,allow_experimental,client_type):
        self._bind(context,allow_experimental,client_type)
        self._client=context.client;self._transport=self._client.transport;self._endpoint=self._transport.endpoint('runtime')

    def _fence(self):
        try:self._options()
        except InvalidRequestError:raise ProtocolError('Reference raster root/Runtime binding changed') from None
        if self.context.client is not self._client or self._client.transport is not self._transport or self._endpoint!=self._transport.endpoint('runtime'):
            raise ProtocolError('Reference raster client/endpoint/credential changed; no implicit rebind')

    def _source(self,observation):
        self._fence();source=to_handle(observation)
        if (source.kind!='observation' or source.runtime_id!=self.reference.runtime_id or source.context_id!=self.reference.context_id
                or getattr(getattr(observation,'_context',None),'client',None) is not self._client):
            raise InvalidRequestError('Exact current originating observation required')
        return source

    def _capability(self,value):
        self._fence()
        if value.service_id!=self.reference.runtime_id:raise ProtocolError('Reference raster Runtime identity changed')
        if value.raw.get('referenceRasterMetadataProtocol')!=PROTOCOL:
            raise UnsupportedOperationError('Reference raster labels not advertised; no native inference/fallback')

    def _result(self,value,source):
        freeze(value)
        try:_reject_credential_leaves(value,self._transport.redact,'Reference metadata contains configured credential material')
        except InvalidRequestError:raise ProtocolError('Reference metadata contains configured credential material') from None
        return _decode(value,source)


class ReferenceRasterLabels(_Labels):
    def __init__(self,context,*,allow_experimental=False):self._configure(context,allow_experimental,Client)

    def metadata(self,observation):
        source=self._source(observation);self._capability(self._client.runtime.capabilities(max_response_bytes=65536))
        value=self._transport.request('runtime','GET',PREFIX+'observations/'+str(source.observation_id),
            **{**self._options(),'max_response_bytes':65536},params={'protocol':PROTOCOL},response='bytes')
        self._fence();self._capability(self._client.runtime.capabilities(max_response_bytes=65536))
        return self._result(_json(value),source)


class AsyncReferenceRasterLabels(_Labels):
    def __init__(self,context,*,allow_experimental=False):
        from .async_client import AsyncClient
        self._configure(context,allow_experimental,AsyncClient)

    async def metadata(self,observation):
        source=self._source(observation);self._capability(await self._client.runtime.capabilities(max_response_bytes=65536))
        value=await self._transport.request('runtime','GET',PREFIX+'observations/'+str(source.observation_id),
            **{**self._options(),'max_response_bytes':65536},params={'protocol':PROTOCOL},response='bytes')
        self._fence();self._capability(await self._client.runtime.capabilities(max_response_bytes=65536))
        return self._result(_json(value),source)
