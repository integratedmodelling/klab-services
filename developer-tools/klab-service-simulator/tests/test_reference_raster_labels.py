"""Public exact raw export plus declared labels, not native semantic/time acceptance."""
import asyncio

import pytest

np=pytest.importorskip('numpy',reason='Dedicated analysis workflow')
pytest.importorskip('rasterio',reason='Dedicated analysis workflow')
pytest.importorskip('xarray',reason='Dedicated analysis workflow')

from klab_client import Client,AsyncClient,Context,AsyncContext,ObservationRequest,RectangularGeometry
from klab_client.analysis import export_for_analysis,async_export_for_analysis
from klab_client.reference_raster_labels import ReferenceRasterLabels,AsyncReferenceRasterLabels,open_reference_raster,KIND
from klab_client.errors import AuthorizationError
from conftest import launch
from test_vertical import client_args,control
from copy import copy


@pytest.mark.parametrize('native_async',[False,True])
@pytest.mark.parametrize('scenario',['category-code-export','declared-band-export'])
def test_public_server_declared_dictionary_worldview_and_band_time_labels(scenario,native_async,tmp_path):
    with launch(scenario) as ready:
        request=ObservationRequest('synthetic:CategoryCode' if scenario=='category-code-export' else 'geography:Elevation in m',
            geometry=RectangularGeometry(0,300,0,200,3,2))
        def check(artifact,metadata):
            declared=metadata.to_wire();result=open_reference_raster(artifact,metadata)
            assert declared['worldviewId']=='synthetic' and declared['worldviewRevision']==1
            assert result.dataset.attrs['reference_metadata_evidence']==KIND
            assert result.dataset.attrs['reference_worldview_service_id']==declared['worldviewServiceId']
            assert result.dataset.attrs['reference_export_sha256']==artifact.sha256
            if scenario=='category-code-export':
                assert artifact.sha256=='e025556feac60d80c68f3dcd7465948f7a071186a9ff5f377c99b28cbc6eaccb'
                assert result.dataset.value.dtype==np.dtype('int16')
                assert result.dataset.value.values.tolist()==[[0,-2,-32768],[7,0,-2]]
                assert result.dataset.valid.values.tolist()==[[True,True,False],[True,True,True]]
                assert result.dataset.category.values.tolist()==[['bare','forest',''],['water','bare','forest']]
                assert result.dataset.category.attrs['category_source']=='SERVER_DECLARED_FINITE_REFERENCE'
                assert result.raster.dataset.value.values.tolist()==[[0,-2,-32768],[7,0,-2]]
            else:
                assert artifact.sha256=='6a41239e3b662ff32d901b80fa41ff9202a03523c27c72aae2e25451349763f0'
                assert result.dataset.value.dtype==np.dtype('float32') and result.dataset.value.shape==(3,2,3)
                assert result.dataset.reference_band.values.tolist()==['Declared band 1','Declared band 2','Declared band 3']
                assert result.dataset.attrs['reference_band_instants']==('2026-01-01T00:00:00Z','2026-01-02T00:00:00Z','2026-01-03T00:00:00Z')
                assert result.dataset.value.values[0].tolist()==[[0.,-2.,-9999.],[4.,5.,6.]]
                assert result.dataset.valid.values[0].tolist()==[[True,True,False],[True,True,True]]
            result.dataset.value.values.reshape(-1)[0]=99
            assert open_reference_raster(artifact,metadata).dataset.value.values.reshape(-1)[0]==0
        async def run():
            async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=await api.create_session();context=await session.create_context()
                try:
                    observation=await (await context.observe(request)).result(timeout=2)
                    metadata=await AsyncReferenceRasterLabels(context,allow_experimental=True).metadata(observation)
                    artifact=await async_export_for_analysis(observation,tmp_path/'reference.tif')
                    async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        foreign=AsyncContext(bob,context.configuration);borrowed=copy(observation);borrowed._context=foreign
                        with pytest.raises(AuthorizationError):await AsyncReferenceRasterLabels(foreign,allow_experimental=True).metadata(borrowed)
                finally:await context.release();await session.release()
            check(artifact,metadata)
        def sync():
            with Client(ready['endpoints']['runtime'],**client_args(ready)) as api:
                session=api.create_session();context=session.create_context()
                try:
                    observation=context.observe(request).result(timeout=2)
                    metadata=ReferenceRasterLabels(context,allow_experimental=True).metadata(observation)
                    artifact=export_for_analysis(observation,tmp_path/'reference.tif')
                    with Client(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        foreign=Context(bob,context.configuration);borrowed=copy(observation);borrowed._context=foreign
                        with pytest.raises(AuthorizationError):ReferenceRasterLabels(foreign,allow_experimental=True).metadata(borrowed)
                finally:context.release();session.release()
            check(artifact,metadata)
        asyncio.run(run()) if native_async else sync()
        assert not any(control(ready)['counts'].values())
