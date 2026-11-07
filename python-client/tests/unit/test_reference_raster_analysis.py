"""Independent offline raster-label association, exact bytes/codes/mask authority only."""
import builtins,hashlib,json,socket
from dataclasses import replace
from datetime import datetime,timezone

import pytest

np=pytest.importorskip('numpy',reason='Optional analysis extra')
pytest.importorskip('rasterio',reason='Optional analysis extra')
pytest.importorskip('xarray',reason='Optional analysis extra')

from klab_client.analysis import AnalysisArtifact
from klab_client.exports import ExportResult
from klab_client.errors import InvalidRequestError,ProtocolError,UnsupportedOperationError
from test_raster_categories import raster
from test_reference_raster_labels import declared,invoke
import klab_client.reference_raster_labels as draft


def source(tmp_path):
    numeric=raster(tmp_path);path=tmp_path/'codes.tif';metadata,_=invoke();value=declared()
    value['exportSha256']=hashlib.sha256(path.read_bytes()).hexdigest()
    canonical={'dtype':'int16','shape':[2,3],'values':[0,-2,-32768,7,0,-2],'valid':[True,True,False,True,True,True]}
    value['arraySha256']=hashlib.sha256(json.dumps(canonical,allow_nan=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    artifact=AnalysisArtifact(ExportResult(path,path.stat().st_size,'image/tiff;application=geotiff','reference-test'),
        metadata.source,datetime.now(timezone.utc),None,value['exportSha256'])
    return artifact,draft._decode(value,metadata.source)


def test_detached_server_label_association_has_no_network_and_preserves_raw_integer_codes(tmp_path,monkeypatch):
    artifact,metadata=source(tmp_path)
    def forbidden(*args,**kwargs):raise AssertionError('Offline label association must not open sockets')
    monkeypatch.setattr(socket,'socket',forbidden)
    result=draft.open_reference_raster(artifact,metadata)
    assert result.dataset.value.values.tolist()==[[0,-2,-32768],[7,0,-2]]
    assert result.dataset.valid.values.tolist()==[[True,True,False],[True,True,True]]
    assert result.dataset.category.values.tolist()==[['bare','forest',''],['water','bare','forest']]
    assert result.dataset.x.values.tolist()==[50.,150.,250.] and result.dataset.y.values.tolist()==[150.,50.]
    assert result.dataset.attrs['reference_worldview_id']=='declared-synthetic'
    assert result.dataset.attrs['reference_worldview_revision']==1 and result.evidence==draft.KIND
    assert result.dataset.category.attrs['category_source']=='SERVER_DECLARED_FINITE_REFERENCE'
    result.dataset.value.values[0,0]=99
    assert result.raster.dataset.value.values[0,0]==0
    assert draft.open_reference_raster(artifact,metadata).dataset.value.values[0,0]==0


@pytest.mark.parametrize('field,bad',[('exportSha256','0'*64),('arraySha256','0'*64),('dtype','int32'),
    ('shape',[3,2]),('units','m'),('nodata',-9999),('crs','EPSG:4326'),('crs','EPSG:999999'),('transform',[100,0,100,0,-100,200])])
def test_wrong_byte_layout_or_content_association_refuses_not_relabels(tmp_path,field,bad):
    artifact,metadata=source(tmp_path);value=metadata.to_wire();value[field]=bad
    altered=draft._decode(value,metadata.source)
    with pytest.raises(ProtocolError):draft.open_reference_raster(artifact,altered)


def test_unknown_codes_are_not_default_categories_or_missingness(tmp_path):
    artifact,metadata=source(tmp_path);value=metadata.to_wire();value['dictionary']=[[0,'bare']]
    with pytest.raises(UnsupportedOperationError):draft.open_reference_raster(artifact,draft._decode(value,metadata.source))


@pytest.mark.parametrize('budget',[True,0,-1,8192,8193])
def test_budget_is_checked_before_arrays_or_labels(tmp_path,budget):
    artifact,metadata=source(tmp_path)
    with pytest.raises(InvalidRequestError):draft.open_reference_raster(artifact,metadata,max_bytes=budget)


def test_optional_analysis_dependencies_and_manual_dtos_fail_explicitly(tmp_path,monkeypatch):
    artifact,metadata=source(tmp_path)
    for left,right in ((None,metadata),(artifact,None),(artifact,replace(metadata,source=None)),
            (artifact,replace(metadata,evidence='NATIVE'))):
        with pytest.raises(InvalidRequestError):draft.open_reference_raster(left,right)
    original=builtins.__import__
    def blocked(name,*args,**kwargs):
        if name=='numpy':raise ImportError('Synthetic absent optional dependency')
        return original(name,*args,**kwargs)
    monkeypatch.setattr(builtins,'__import__',blocked)
    with pytest.raises(UnsupportedOperationError):draft.open_reference_raster(artifact,metadata)


def test_array_identity_checks_mask_dtype_and_nonfinite_validity_without_coercion():
    for values,valid in ((np.array([True]),np.array([True])),(np.array([1]),np.array([1])),
            (np.zeros(4097,dtype='int16'),np.zeros(4097,dtype='bool'))):
        with pytest.raises(ProtocolError):draft._array_digest(values,valid)
    values=np.array([np.nan,np.inf,-np.inf],dtype='float32');valid=np.array([False]*3)
    assert len(draft._array_digest(values,valid))==64
    valid[0]=True
    with pytest.raises(ProtocolError):draft._array_digest(values,valid)


def test_raw_multiband_values_keep_explicit_reference_labels_and_utc_instants(tmp_path):
    import rasterio
    from rasterio.transform import from_origin
    path=tmp_path/'bands.tif';numbers=np.array([[[0.,-2.]],[[10.,-9999.]]],dtype='float32')
    with rasterio.open(path,'w',driver='GTiff',width=2,height=1,count=2,dtype='float32',
            nodata=-9999,crs='EPSG:3857',transform=from_origin(0,100,100,100)) as writer:
        writer.write(numbers)
    metadata,_=invoke();value=declared()
    canonical={'dtype':'float32','shape':[2,1,2],'values':[0.,-2.,10.,-9999.],'valid':[True,True,True,False]}
    digest=hashlib.sha256(path.read_bytes()).hexdigest()
    value.update(exportSha256=digest,arraySha256=hashlib.sha256(json.dumps(canonical,allow_nan=False,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
        shape=[2,1,2],dtype='float32',dictionary=[],nodata=-9999.,transform=[100,0,0,0,-100,100],
        bandLabels=['first declared','second declared'],bandInstants=['2026-01-01T00:00:00Z','2026-01-02T00:00:00Z'])
    artifact=AnalysisArtifact(ExportResult(path,path.stat().st_size,'image/tiff;application=geotiff','reference-test'),
        metadata.source,datetime.now(timezone.utc),None,digest)
    result=draft.open_reference_raster(artifact,draft._decode(value,metadata.source))
    assert result.dataset.reference_band.values.tolist()==['first declared','second declared']
    assert result.dataset.attrs['reference_band_instants']==tuple(value['bandInstants'])
    assert result.dataset.value.attrs['band_axis_source']=='SERVER_DECLARED_FINITE_REFERENCE'
    np.testing.assert_array_equal(result.dataset.value.values,numbers)
    assert result.dataset.valid.values.tolist()==[[[True,True]],[[True,False]]]
