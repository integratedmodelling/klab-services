"""Owned exact-SDK kernel consumes public declared metadata, never fixture internals."""
import json,os,sys,textwrap
from pathlib import Path

import pytest

nbformat=pytest.importorskip('nbformat',reason='Notebook acceptance extra')
pytest.importorskip('nbclient',reason='Notebook acceptance extra')
pytest.importorskip('ipykernel',reason='Notebook acceptance extra')
psutil=pytest.importorskip('psutil',reason='Owned-kernel cleanup inspection')
pytest.importorskip('rasterio',reason='Optional analysis extra')
pytest.importorskip('xarray',reason='Optional analysis extra')

import klab_client
from conftest import REPO,launch
from test_vertical import control


def test_owned_sdk_only_kernel_reads_server_dictionary_and_temporal_band_labels(tmp_path):
    sys.path.insert(0,str(REPO/'python-client/tools'))
    try:from workflows.async_notebook import execute_private_kernel,check_outputs
    finally:sys.path.pop(0)
    with launch('category-code-export') as categories,launch('declared-band-export') as bands:
        code=textwrap.dedent('''
            import json,os,sys
            from pathlib import Path
            from klab_client import AsyncClient,Endpoint,ObservationRequest,RectangularGeometry
            from klab_client.analysis import async_export_for_analysis
            from klab_client.reference_raster_labels import AsyncReferenceRasterLabels,open_reference_raster,KIND
            services=json.loads(os.environ['KLAB_SIM_RASTER_LABELS'])
            results=[]
            for index,ready in enumerate(services):
                urls=ready['endpoints'];token=ready['credential']
                options=dict(credential=token,agent_name='alice',runtime_service_id=ready['service_ids']['runtime'],
                    reasoner=Endpoint(urls['reasoner'],token),resources=Endpoint(urls['resources'],token),
                    resolver=Endpoint(urls['resolver'],token),poll_interval=.01,timeout=3)
                async with AsyncClient(urls['runtime'],**options) as api:
                    session=await api.create_session();context=await session.create_context()
                    try:
                        request=ObservationRequest('synthetic:CategoryCode' if index==0 else 'geography:Elevation in m',
                            geometry=RectangularGeometry(0,300,0,200,3,2))
                        observation=await (await context.observe(request)).result(timeout=2)
                        metadata=await AsyncReferenceRasterLabels(context,allow_experimental=True).metadata(observation)
                        artifact=await async_export_for_analysis(observation,Path('labels-'+str(index)+'.tif'))
                    finally:await context.release();await session.release()
                result=open_reference_raster(artifact,metadata)
                assert result.evidence==KIND and result.dataset.attrs['reference_worldview_id']=='synthetic'
                if index==0:
                    assert str(result.dataset.value.dtype)=='int16'
                    assert result.dataset.value.values.tolist()==[[0,-2,-32768],[7,0,-2]]
                    assert result.dataset.category.values.tolist()==[['bare','forest',''],['water','bare','forest']]
                else:
                    assert str(result.dataset.value.dtype)=='float32'
                    assert result.dataset.reference_band.values.tolist()==['Declared band 1','Declared band 2','Declared band 3']
                    assert result.dataset.attrs['reference_band_instants'][1]=='2026-01-02T00:00:00Z'
                results.append(dict(kind=result.evidence,shape=list(result.dataset.value.shape)))
            assert 'klab_simulator' not in sys.modules
            print(json.dumps(dict(results=results,native='NOT_RUN')))
        ''')
        notebook=nbformat.v4.new_notebook(cells=[nbformat.v4.new_code_cell(
            'import json,sys,os,psutil,klab_client\nprint(json.dumps(dict(interpreter=sys.executable,sdk_file=klab_client.__file__,pid=os.getpid(),created=psutil.Process().create_time())))'),
            nbformat.v4.new_code_cell(code),nbformat.v4.new_code_cell(
                "assert len(results)==2 and all(row['kind']==KIND for row in results)\nprint('Finite reference metadata only; native science NOT_RUN')")])
        notebook.metadata.kernelspec={'name':'klab-owned-async','display_name':'owned','language':'python'}
        env={key:value for key,value in os.environ.items() if not key.startswith('KLAB_') and key!='PYTHONPATH'}
        env['PYTHONPATH']=str(Path(klab_client.__file__).resolve().parents[1]);env['KLAB_SIM_RASTER_LABELS']=json.dumps([ready|{'credential':'sim-token-alice'} for ready in (categories,bands)])
        execute_private_kernel(notebook,tmp_path,env)
        check_outputs(notebook,['sim-token-alice','sim-token-bob','sim-admin-control'])
        identity=json.loads(notebook.cells[0].outputs[0].text)
        assert Path(identity['interpreter']).resolve()==Path(sys.executable).resolve()
        assert Path(identity['sdk_file']).resolve()==Path(klab_client.__file__).resolve()
        try:assert psutil.Process(identity['pid']).create_time()!=identity['created']
        except psutil.NoSuchProcess:pass
        assert not list(tmp_path.glob('private-kernel-*'))
        result=json.loads(notebook.cells[1].outputs[0].text)
        assert result['native']=='NOT_RUN' and [row['shape'] for row in result['results']]==[[2,3],[3,2,3]]
        assert not any(control(categories)['counts'].values()) and not any(control(bands)['counts'].values())
