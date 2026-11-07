"""Finite exact-export label declarations; no native ontology/time/science engine."""
import hashlib,json,re
from urllib.parse import parse_qs,urlsplit

from .fixtures import category_code_fixture,declared_band_fixture
from .state import ContractError

PROTOCOL='klab.experimental.reference-raster-metadata/1'
PREFIX='/experimental/reference-raster-metadata/v1/'
KIND='SERVER_DECLARED_FINITE_RASTER_LABELS_NOT_NATIVE_SEMANTIC_AUTHORITY'


def response(state,method,path,principal,scope,headers):
    if state.scenario not in ('category-code-export','declared-band-export'):
        raise ContractError(404,'No reference raster metadata in this scenario')
    root,_=state._context(scope,principal)
    if root!=scope or headers.get('klab-service')!=state.service_ids['runtime']:
        raise ContractError(403,'Exact current owner root/Runtime required')
    url=urlsplit(path)
    match=re.fullmatch(r'observations/([1-9][0-9]{0,15})',url.path[len(PREFIX):])
    if method!='GET' or match is None or parse_qs(url.query,keep_blank_values=True)!={'protocol':[PROTOCOL]}:
        raise ContractError(422,'Exact reference raster source/protocol required')
    observation=int(match[1])
    if (root,observation) not in state.observations:raise ContractError(404,'Completed owned observation required')
    if state.scenario=='category-code-export':
        manifest,raw=category_code_fixture();dtype='int16';shape=manifest['shape']
        numbers=[number for row in manifest['values'] for number in row]
        entries=manifest['entries'];labels=[];instants=[];unit=None;nodata=manifest['nodata']
    else:
        manifest,raw=declared_band_fixture();dtype='float32';shape=manifest['shape']
        numbers=[float(number) for band in manifest['values'] for row in band for number in row]
        entries=[];instants=manifest['axis_coordinates'];labels=['Declared band '+str(index+1) for index in range(shape[0])]
        unit=manifest['unit'];nodata=manifest['nodata']
    valid=[number!=nodata for number in numbers]
    canonical={'dtype':dtype,'shape':shape,'values':numbers,'valid':valid}
    array_sha=hashlib.sha256(json.dumps(canonical,allow_nan=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    # These literal labels are adopted only by this explicit proposal, not
    # retroactively asserted to be embedded TIFF metadata or native worldview data.
    return {'protocol':PROTOCOL,'kind':KIND,'runtimeId':state.service_ids['runtime'],'contextId':root,
        'observationId':observation,'epoch':state.read_plans.epoch,'exportSha256':hashlib.sha256(raw).hexdigest(),
        'arraySha256':array_sha,'shape':shape,'dtype':dtype,'crs':'EPSG:3857',
        'transform':[100,0,0,0,-100,200],'units':unit,'nodata':nodata,'dictionary':entries,
        'worldviewId':state.worldview_id,'worldviewRevision':state.validation_revision,
        'worldviewServiceId':state.service_ids['reasoner'],'bandLabels':labels,'bandInstants':instants},'application/json'
