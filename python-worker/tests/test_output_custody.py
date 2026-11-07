"""Independent public receipt/output-budget controls, never native ABI acceptance."""
import hashlib
from copy import deepcopy

import httpx
import pytest

from klab_worker import WorkerError,Field,Limits,execute
from klab_worker.protocol import PROTOCOL,encoded
from klab_worker import _responses
from test_client_failures import client
from test_worker import sample


def leased():
    return {'protocol':PROTOCOL,'state':'LEASED','task_id':'task','lease':'synthetic-lease','input':sample().to_dict()}


def published():
    field=sample().to_dict();digest=hashlib.sha256(encoded(field)).hexdigest()
    return {'protocol':PROTOCOL,'task_id':'task','state':'PUBLISHED','output':field,
        'provenance':{'worker_id':'worker','scope':'s.c','protocol':PROTOCOL,'input_sha256':'a'*64,
            'implementation_sha256':'b'*64,'output_sha256':digest,'origin':'FINITE_REFERENCE_NOT_NATIVE_SCIENCE'}}


@pytest.mark.parametrize('operation',['register','submit','claim','status','cancel','recover','release','fail','publish'])
def test_public_operation_exact_receipts_and_no_implicit_replay(monkeypatch,operation):
    calls=[]
    def handler(request):
        calls.append(request)
        value={
            'register':{'protocol':PROTOCOL,'state':'REGISTERED','worker_id':'worker'},
            'submit':{'protocol':PROTOCOL,'state':'QUEUED','task_id':'task'},
            'claim':leased(),'status':published(),
            'cancel':{'protocol':PROTOCOL,'state':'CANCELLED'},
            'recover':{'protocol':PROTOCOL,'state':'QUEUED','task_id':'task'},
            'release':{'protocol':PROTOCOL,'state':'RELEASED'},
            'fail':{'protocol':PROTOCOL,'state':'FAILED'},
            'publish':{'protocol':PROTOCOL,'state':'PUBLISHED','output_sha256':hashlib.sha256(encoded(sample().to_dict())).hexdigest()}
        }[operation]
        return httpx.Response(200,json=value)
    with client(monkeypatch,handler) as worker:
        result=call(worker,operation)
        assert result['protocol']==PROTOCOL and len(calls)==1


def call(worker,operation):
    if operation=='register':return worker.register('worker',operation='double')
    if operation=='submit':return worker.submit(sample(),operation='double')
    if operation=='claim':return worker.claim('worker')
    if operation=='status':return worker.status('task')
    if operation=='cancel':return worker.cancel('task')
    if operation=='recover':return worker.recover_task('task',allow_recompute=True)
    if operation=='release':return worker.release('worker')
    if operation=='fail':return worker.fail(leased())
    return worker.publish(leased(),sample(),implementation_sha256='a'*64)


@pytest.mark.parametrize('operation',['register','submit','claim','status','cancel','recover','release','fail','publish'])
def test_protocol_only_successful_response_is_never_an_operation_receipt(monkeypatch,operation):
    calls=[]
    def handler(request):calls.append(request);return httpx.Response(200,json={'protocol':PROTOCOL})
    with client(monkeypatch,handler) as worker:
        with pytest.raises(WorkerError):call(worker,operation)
    assert len(calls)==1


@pytest.mark.parametrize('field,bad',[('task_id','../other'),('task_id','..'),('state','QUEUED'),('lease',''),('lease','bad\nlease'),
    ('lease','x'*129),('input',{}),('protocol','native')])
def test_bad_claim_never_authorizes_script_execution(monkeypatch,tmp_path,field,bad):
    value=leased();value[field]=bad;calls=[];marker=tmp_path/'must-not-run'
    script=tmp_path/'trusted.py';script.write_text(f'open({str(marker)!r},"w").write("unsafe")\n')
    def handler(request):calls.append(request);return httpx.Response(200,json=value)
    with client(monkeypatch,handler) as worker:
        with pytest.raises(WorkerError):worker.run_once('worker',script)
    assert not marker.exists() and len(calls)==1


@pytest.mark.parametrize('defect',['foreign-task','bad-state','unpublished-output','missing-provenance','foreign-scope',
    'bad-worker','bad-input-hash','bad-implementation-hash','bad-output-hash','bad-origin','invalid-output'])
def test_status_requires_exact_task_publication_and_scoped_content_provenance(monkeypatch,defect):
    value=published()
    if defect=='foreign-task':value['task_id']='other'
    if defect=='bad-state':value['state']='DONE'
    if defect=='unpublished-output':value['state']='LEASED'
    if defect=='missing-provenance':value['provenance']=None
    if defect=='foreign-scope':value['provenance']['scope']='foreign'
    if defect=='bad-worker':value['provenance']['worker_id']='../other'
    if defect=='bad-input-hash':value['provenance']['input_sha256']='bad'
    if defect=='bad-implementation-hash':value['provenance']['implementation_sha256']='bad'
    if defect=='bad-output-hash':value['provenance']['output_sha256']='0'*64
    if defect=='bad-origin':value['provenance']['origin']=None
    if defect=='invalid-output':value['output']['values'][0]=True
    with client(monkeypatch,lambda request:httpx.Response(200,json=value)) as worker:
        with pytest.raises(WorkerError):worker.status('task')


def test_unpublished_status_and_idle_are_explicit_not_publication_or_lease(monkeypatch):
    with client(monkeypatch,lambda request:httpx.Response(200,json={'protocol':PROTOCOL,'task_id':'task',
            'state':'RECOVERY_REQUIRED','output':None,'provenance':None})) as worker:
        assert worker.status('task')['state']=='RECOVERY_REQUIRED'
    with pytest.raises(WorkerError):_responses.task_input({'protocol':PROTOCOL,'state':'IDLE'})
    with pytest.raises(WorkerError):_responses.assigned({'protocol':PROTOCOL,'state':'QUEUED','task_id':'other'},'task')


def test_aggregate_stdout_plus_stderr_budget_is_not_two_per_stream_budgets(tmp_path):
    source=Field((1,),('x',),((0,),),None,None,(1.,),(True,))
    script=tmp_path/'trusted.py'
    script.write_text("import sys\ndata=sys.stdin.read()\nsys.stdout.write(data+' '*500)\nsys.stderr.write('d'*500)\n")
    with pytest.raises(WorkerError,match='byte budget'):execute(script,source,limits=Limits(output_bytes=800))


def test_nonfinite_json_and_enormous_deadline_fail_with_secret_safe_worker_errors():
    with pytest.raises(WorkerError):encoded({'number':float('nan')})
    with pytest.raises(WorkerError):Limits(seconds=10**1000)


def test_dot_segment_task_selector_is_refused_before_http(monkeypatch):
    def offline(request):pytest.fail('No request may escape the worker task route')
    with client(monkeypatch,offline) as worker:
        for task_id in ('.','..'):
            with pytest.raises(WorkerError):worker.status(task_id)


def test_aggregate_stream_budget_is_checked_while_trusted_child_still_runs(tmp_path):
    source=Field((1,),('x',),((0,),),None,None,(1.,),(True,))
    script=tmp_path/'trusted.py'
    script.write_text("import sys,time\ndata=sys.stdin.read()\nsys.stdout.write(data+' '*500)\nsys.stdout.flush()\nsys.stderr.write('d'*500)\nsys.stderr.flush()\ntime.sleep(10)\n")
    with pytest.raises(WorkerError,match='byte budget'):execute(script,source,limits=Limits(seconds=2,output_bytes=800))


def test_owned_tree_stop_precedes_final_output_read_and_validation(monkeypatch,tmp_path):
    import klab_worker.execution as execution
    factory=execution.subprocess.Popen;tree_factory=execution.ProcessTree;files={}
    def launch(*args,**kwargs):
        files['stdout']=kwargs['stdout']
        return factory(*args,**kwargs)
    class TailAtStop:
        def __init__(self,*args):self.tree=tree_factory(*args)
        def close(self):
            self.tree.close()
            files['stdout'].seek(0,2);files['stdout'].write(b'late invalid output');files['stdout'].flush()
    monkeypatch.setattr(execution.subprocess,'Popen',launch)
    monkeypatch.setattr(execution,'ProcessTree',TailAtStop)
    script=tmp_path/'trusted.py';script.write_text('import sys\nsys.stdout.write(sys.stdin.read())\n')
    with pytest.raises(WorkerError,match='Invalid worker response'):execute(script,sample())


def test_final_aggregate_budget_refusal_is_deterministic_after_tree_stop(monkeypatch,tmp_path):
    import klab_worker.execution as execution
    factory=execution.subprocess.Popen;tree_factory=execution.ProcessTree;files={}
    def launch(*args,**kwargs):
        files['stderr']=kwargs['stderr']
        return factory(*args,**kwargs)
    class GrowAtStop:
        def __init__(self,*args):self.tree=tree_factory(*args)
        def close(self):
            self.tree.close()
            # The monitor has already stopped: this exercises final acceptance,
            # independent of process scheduling or which write the polling loop saw.
            files['stderr'].write(b'x'*801);files['stderr'].flush()
    monkeypatch.setattr(execution.subprocess,'Popen',launch)
    monkeypatch.setattr(execution,'ProcessTree',GrowAtStop)
    script=tmp_path/'trusted.py';script.write_text('import sys\nsys.stdout.write(sys.stdin.read())\n')
    with pytest.raises(WorkerError,match='byte budget'):execute(script,sample(),limits=Limits(output_bytes=800))
