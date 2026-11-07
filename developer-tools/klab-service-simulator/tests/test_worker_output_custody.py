"""Actual public worker output/refusal custody; not native ABI or scientific accuracy."""
import hashlib

import pytest

from klab_client import Client
from klab_worker import Field,Limits,WorkerClient,WorkerError
from klab_worker.protocol import encoded
from test_vertical import client_args,control


@pytest.mark.parametrize('failure',['aggregate-output','changed-unit'])
def test_failed_trusted_output_never_publishes_then_explicit_new_task_can_succeed(simulator,tmp_path,failure):
    field=Field((3,),('x',),((0.,1.,2.),),None,'m',(0.,-2.,None),(True,True,False))
    broken=tmp_path/'broken.py'
    if failure=='aggregate-output':
        broken.write_text("import sys\ndata=sys.stdin.read()\nsys.stdout.write(data+' '*500)\nsys.stderr.write('d'*500)\n")
    else:
        broken.write_text("import json,sys\ndata=json.load(sys.stdin)\ndata['unit']='cm'\nprint(json.dumps(data))\n")
    trusted=tmp_path/'trusted.py';trusted.write_text('import sys\nsys.stdout.write(sys.stdin.read())\n')
    with Client(simulator['endpoints']['runtime'],**client_args(simulator)) as api:
        session=api.create_session();context=session.create_context()
        try:
            with WorkerClient(simulator['endpoints']['runtime'],'sim-token-alice',scope=context.id,allow_experimental=True) as owner, WorkerClient(
                    simulator['endpoints']['runtime'],'sim-token-worker-alice',scope=context.id,allow_experimental=True) as worker:
                assert worker.register('custody',operation='copy')['state']=='REGISTERED'
                task=owner.submit(field,operation='copy')
                with pytest.raises(WorkerError):worker.run_once('custody',broken,limits=Limits(output_bytes=800))
                failed=owner.status(task['task_id'])
                assert failed['state']=='FAILED' and failed['output'] is None and failed['provenance'] is None
                # Failed task is retained; no automatic recovery/replay or assumed rollback.
                assert worker.claim('custody')['state']=='IDLE'
                submitted=owner.submit(field,operation='copy')
                result=worker.run_once('custody',trusted)
                digest=hashlib.sha256(encoded(field.to_dict())).hexdigest()
                assert result['state']=='PUBLISHED' and result['output_sha256']==digest
                published=owner.status(submitted['task_id'])
                assert Field.from_dict(published['output'])==field and published['provenance']['output_sha256']==digest
                assert published['provenance']['input_sha256']==digest
                assert published['provenance']['implementation_sha256']==hashlib.sha256(trusted.read_bytes()).hexdigest()
                assert owner.status(task['task_id'])['state']=='FAILED'
                assert worker.release('custody')['state']=='RELEASED'
        finally:context.release();session.release()
    assert not any(control(simulator)['counts'].values())
