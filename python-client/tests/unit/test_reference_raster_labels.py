"""Independent public finite metadata controls; no simulator imports."""
import asyncio,hashlib,json
from dataclasses import replace
from types import SimpleNamespace

import httpx,pytest
from klab_client.dto import observation_from_wire
from klab_client.errors import InvalidRequestError,ProtocolError,UnsupportedOperationError
from test_selections import client,root,wire
import klab_client.reference_raster_labels as draft


def declared():
    return {'protocol':draft.PROTOCOL,'kind':draft.KIND,'runtimeId':'runtime-id','contextId':'s.c',
        'observationId':1,'epoch':'a'*32,'exportSha256':'b'*64,'arraySha256':'c'*64,
        'shape':[2,3],'dtype':'int16','crs':'EPSG:3857','transform':[100,0,0,0,-100,200],
        'units':None,'nodata':-32768,'dictionary':[[0,'bare'],[-2,'forest'],[7,'water']],
        'worldviewId':'declared-synthetic','worldviewRevision':1,'worldviewServiceId':'reasoner-declared',
        'bandLabels':[],'bandInstants':[]}


def invoke(*,native_async=False,change=lambda value:None,capability=None,drift=False,raw_response=None):
    value=declared();change(value);box={};calls=[]
    def handler(request):
        calls.append(request)
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json=capability or {'serviceId':'runtime-id','referenceRasterMetadataProtocol':draft.PROTOCOL})
        if drift:
            from klab_client import Endpoint
            box['api'].transport.endpoints['runtime']=Endpoint('https://runtime.test','changed-secret')
        return httpx.Response(200,json=value) if raw_response is None else httpx.Response(200,content=raw_response)
    async def run():
        async with client(handler,True) as api:
            box['api']=api;context=root(api)
            return await draft.AsyncReferenceRasterLabels(context,allow_experimental=True).metadata(observation_from_wire(wire(),context))
    if native_async:return asyncio.run(run()),calls
    with client(handler) as api:
        box['api']=api;context=root(api)
        return draft.ReferenceRasterLabels(context,allow_experimental=True).metadata(observation_from_wire(wire(),context)),calls


@pytest.mark.parametrize('native_async',[False,True])
def test_exact_public_metadata_preserves_server_declarations_without_ontology_authority(native_async):
    result,calls=invoke(native_async=native_async)
    assert result.to_wire()==declared() and result.evidence==draft.KIND
    copied=result.to_wire();copied['dictionary'][0][1]='edited'
    assert result.to_wire()['dictionary'][0]==[0,'bare']
    assert len([request for request in calls if not request.url.path.endswith('/capabilities')])==1


@pytest.mark.parametrize('field,bad',[('protocol','native'),('kind','NATIVE'),('runtimeId','foreign'),('contextId','foreign'),
    ('observationId',True),('epoch','bad'),('exportSha256',None),('arraySha256','bad'),('shape',[True,3]),
    ('dtype','object'),('crs','https://foreign.invalid/crs'),('transform',[100,True,0,0,-100,200]),
    ('units',[]),('nodata',True),('worldviewRevision',True),('worldviewServiceId',''),('dictionary',[]),
    ('dictionary',[[True,'bare']]),('dictionary',[[0,'bare'],[0,'other']]),('dictionary',[[0,'same'],[1,'same']]),
    ('bandLabels',['extra']),('bandInstants',['2026-01-01T00:00:00Z'])])
def test_hostile_or_incomplete_metadata_never_gains_source_semantic_or_time_authority(field,bad):
    def change(value):value[field]=bad
    with pytest.raises(ProtocolError):invoke(change=change)


def bands(value):
    value.update(shape=[3,2,3],dtype='float32',dictionary=[],nodata=-9999.,units='m',
        bandLabels=['first','second','third'],bandInstants=['2026-01-01T00:00:00Z','2026-01-02T00:00:00Z','2026-01-03T00:00:00Z'])


def test_explicit_band_instants_and_integer_dictionary_semantics_are_separate():
    result,_=invoke(change=bands)
    assert result.to_wire()['bandInstants'][1]=='2026-01-02T00:00:00Z'
    def wrong_time(value):bands(value);value['bandInstants'][0]='yesterday'
    with pytest.raises(ProtocolError):invoke(change=wrong_time)
    def noncanonical(value):bands(value);value['bandInstants'][0]='2026-1-01T00:00:00Z'
    with pytest.raises(ProtocolError):invoke(change=noncanonical)
    def floating_codes(value):value['dtype']='float32'
    with pytest.raises(ProtocolError):invoke(change=floating_codes)


@pytest.mark.parametrize('native_async',[False,True])
def test_capability_identity_credential_and_secret_guards_refuse_without_fallback(native_async):
    with pytest.raises(UnsupportedOperationError):invoke(native_async=native_async,capability={'serviceId':'runtime-id'})
    with pytest.raises(ProtocolError):invoke(native_async=native_async,capability={'serviceId':'other'})
    with pytest.raises(ProtocolError):invoke(native_async=native_async,drift=True)
    def secret(value):value['worldviewId']='current-secret'
    with pytest.raises(ProtocolError) as error:invoke(native_async=native_async,change=secret)
    assert 'current-secret' not in str(error.value)


def test_local_wrong_context_and_manual_reference_refuse_before_network():
    def offline(request):raise AssertionError('No implicit lookup for invalid local source')
    with client(offline) as api,client(offline) as other:
        context=root(api);facade=draft.ReferenceRasterLabels(context,allow_experimental=True)
        with pytest.raises(InvalidRequestError):draft.ReferenceRasterLabels(context)
        with pytest.raises(InvalidRequestError):facade.metadata(observation_from_wire(wire(),root(other)))
        with pytest.raises(ProtocolError):facade._capability(SimpleNamespace(service_id='foreign',raw={}))
        context.configuration['serviceId']='drift'
        with pytest.raises(ProtocolError):facade.metadata(observation_from_wire(wire(),context))


@pytest.mark.parametrize('native_async',[False,True])
def test_duplicate_json_metadata_identity_is_not_silently_last_value_wins(native_async):
    raw=('{"worldviewRevision":999,'+json.dumps(declared())[1:]).encode()
    with pytest.raises(ProtocolError):invoke(native_async=native_async,raw_response=raw)
