"""Independent finite behavior wire controls; no simulator imports or actor execution."""
import asyncio,hashlib,json
from dataclasses import replace
from types import SimpleNamespace
import httpx,pytest

from klab_client import Endpoint
from klab_client.errors import InvalidRequestError,ProtocolError,SubmissionOutcomeUnknown,UnsupportedOperationError
from test_selections import client,root
import klab_client.managed_behaviors as draft

SOURCE='literal independent source fixture\n'
DIGEST=hashlib.sha256(SOURCE.encode()).hexdigest()


def admission():
    return {'protocol':draft.PROTOCOL,'kind':draft.KIND,'sourceSha256':DIGEST,'sourceUrn':'simulator.managed.finish','recipe':'finish'}


def reference():
    return {**admission(),'agentId':'a'*32,'epoch':'b'*32,'runtimeId':'runtime-id','contextId':'s.c','privateSessionId':'c'*32}


def status(state='READY',revision=1,disposed=False):
    return {'reference':reference(),'revision':revision,'state':state,'privateSessionId':'c'*32,
        'privateSessionDisposed':disposed,'outcome':{'FINISHED':'DECLARED_SUCCESS','FAILED':'DECLARED_FAILURE','STOPPED':'DECLARED_STOP'}.get(state)}


def invoke(operation='validate',*,native_async=False,value=None,capability=None,hook=lambda box:None):
    box={};calls=[]
    def handler(request):
        calls.append(request)
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json=capability or {'serviceId':'runtime-id','managedBehaviorProtocol':draft.PROTOCOL})
        hook(box)
        default=admission() if operation=='validate' else {'purged':True,'reference':reference()} if operation=='purge' else status(
            'RUNNING' if operation=='start' else 'STOPPING' if operation=='stop' else 'READY',2 if operation in ('start','stop') else 1)
        return httpx.Response(200,content=json.dumps(default if value is None else value).encode(),headers={'Content-Type':'application/json'})
    async def run():
        async with client(handler,True) as api:
            box['api']=api;facade=draft.AsyncManagedBehaviors(root(api),allow_experimental=True)
            admitted=draft.BehaviorAdmission(DIGEST,'simulator.managed.finish','finish')
            ref=facade._reference(reference())
            if operation=='validate':return await facade.validate(SOURCE)
            if operation=='create':return await facade.create(SOURCE,admission=admitted)
            if operation=='read':return await facade.read(ref)
            if operation=='reconnect':return await facade.reconnect(reference())
            return await facade.command(ref,operation,expected_revision=1)
    if native_async:return asyncio.run(run()),calls
    with client(handler) as api:
        box['api']=api;facade=draft.ManagedBehaviors(root(api),allow_experimental=True)
        admitted=draft.BehaviorAdmission(DIGEST,'simulator.managed.finish','finish');ref=facade._reference(reference())
        if operation=='validate':result=facade.validate(SOURCE)
        elif operation=='create':result=facade.create(SOURCE,admission=admitted)
        elif operation=='read':result=facade.read(ref)
        elif operation=='reconnect':result=facade.reconnect(reference())
        else:result=facade.command(ref,operation,expected_revision=1)
        return result,calls


@pytest.mark.parametrize('native_async',[False,True])
@pytest.mark.parametrize('operation',['validate','create','read','reconnect','start','stop','purge'])
def test_explicit_public_source_admission_and_lifecycle_roundtrips(native_async,operation):
    result,calls=invoke(operation,native_async=native_async)
    actual=[request for request in calls if not request.url.path.endswith('/capabilities')]
    assert len(actual)==1 and actual[0].url.path.startswith(draft.PREFIX)
    if operation=='purge':assert result is True
    elif operation=='validate':assert result.source_sha256==DIGEST and result.evidence==draft.KIND
    else:assert result.reference.admission.source_sha256==DIGEST and not result.private_session_disposed


@pytest.mark.parametrize('field,bad',[('protocol','native'),('kind','NATIVE_EXECUTION'),('sourceSha256','0'*64),
    ('sourceUrn','foreign'),('recipe','execute-arbitrary')])
def test_admission_exact_hash_recipe_and_provisional_scope_required(field,bad):
    value=admission();value[field]=bad
    with pytest.raises(ProtocolError):invoke(value=value)


@pytest.mark.parametrize('field,bad',[('revision',True),('state','WAITING'),('privateSessionId','s'),
    ('privateSessionDisposed',True),('outcome','SUCCESS'),('reference',{})])
def test_malformed_status_not_accepted_and_bad_create_is_outcome_unknown(field,bad):
    value=status();value[field]=bad
    with pytest.raises(ProtocolError):invoke('read',value=value)
    with pytest.raises(SubmissionOutcomeUnknown):invoke('create',value=value)


@pytest.mark.parametrize('field,bad',[('contextId','foreign'),('runtimeId','other'),('agentId','bad'),('epoch','bad'),
    ('sourceSha256','0'*64),('recipe','fail')])
def test_response_cannot_replace_explicit_agent_source_or_incarnation(field,bad):
    value=status();value['reference'][field]=bad
    with pytest.raises(ProtocolError):invoke('read',value=value)


@pytest.mark.parametrize('native_async',[False,True])
def test_wrong_create_transition_or_command_ack_never_assumes_rollback(native_async):
    with pytest.raises(SubmissionOutcomeUnknown):invoke('create',native_async=native_async,value=status('RUNNING',2))
    for operation in ('start','stop','purge'):
        with pytest.raises(SubmissionOutcomeUnknown):invoke(operation,native_async=native_async,value={})
    with pytest.raises(SubmissionOutcomeUnknown):invoke('start',native_async=native_async,value=status())
    with pytest.raises(SubmissionOutcomeUnknown):invoke('start',native_async=native_async,value=status('RUNNING',3))


@pytest.mark.parametrize('native_async',[False,True])
def test_capability_and_credential_fences_never_fallback_or_rebind(native_async):
    for capability in ({'serviceId':'runtime-id'},{'serviceId':'other','managedBehaviorProtocol':draft.PROTOCOL}):
        with pytest.raises((ProtocolError,UnsupportedOperationError)):invoke(native_async=native_async,capability=capability)
    def drift(box):box['api'].transport.endpoints['runtime']=Endpoint('https://runtime.test','different-secret')
    with pytest.raises(ProtocolError):invoke('read',native_async=native_async,hook=drift)
    with pytest.raises(ProtocolError):invoke('validate',native_async=native_async,hook=drift)
    with pytest.raises(SubmissionOutcomeUnknown):invoke('create',native_async=native_async,hook=drift)


@pytest.mark.parametrize('native_async',[False,True])
def test_local_bad_source_admission_reference_command_refuse_without_http(native_async):
    def offline(request):raise AssertionError('No implicit server request for invalid local input')
    async def run():
        async with client(offline,True) as api:
            facade=draft.AsyncManagedBehaviors(root(api),allow_experimental=True);ref=facade._reference(reference())
            for source in (None,'','x'*4097,'\ud800'):
                with pytest.raises(InvalidRequestError):await facade.validate(source)
            with pytest.raises(InvalidRequestError):await facade.create(SOURCE,admission=None)
            with pytest.raises(InvalidRequestError):await facade.read(None)
            with pytest.raises(InvalidRequestError):await facade.command(ref,'execute',expected_revision=1)
            with pytest.raises(InvalidRequestError):await facade.command(ref,'start',expected_revision=True)
            with pytest.raises(ProtocolError):await facade.reconnect({})
    if native_async:asyncio.run(run())
    else:
        with client(offline) as api:
            context=root(api);facade=draft.ManagedBehaviors(context,allow_experimental=True);ref=facade._reference(reference())
            with pytest.raises(InvalidRequestError):draft.ManagedBehaviors(context)
            for source in (None,'','x'*4097,'\ud800'):
                with pytest.raises(InvalidRequestError):facade.validate(source)
            with pytest.raises(InvalidRequestError):facade.create(SOURCE,admission=None)
            with pytest.raises(InvalidRequestError):facade.read(None)
            with pytest.raises(InvalidRequestError):facade.command(ref,'execute',expected_revision=1)
            with pytest.raises(InvalidRequestError):facade.command(ref,'start',expected_revision=True)
            with pytest.raises(ProtocolError):facade.reconnect({})
            context.configuration['serviceId']='drift'
            with pytest.raises(ProtocolError):facade.validate(SOURCE)


def test_configured_credentials_never_become_source_or_outcome_evidence():
    with pytest.raises(ProtocolError) as error:invoke('validate',value={**admission(),'sourceUrn':'current-secret'})
    assert 'current-secret' not in str(error.value)
    def offline(request):raise AssertionError('No implicit network')
    with client(offline) as api:
        facade=draft.ManagedBehaviors(root(api),allow_experimental=True)
        with pytest.raises(ProtocolError):facade._capability(SimpleNamespace(service_id='foreign',raw={}))


def test_private_session_binding_cannot_change_between_reference_and_status():
    value=status();value['privateSessionId']='d'*32
    with pytest.raises(ProtocolError):invoke('read',value=value)


def test_cancelled_async_creation_does_not_replay_or_assume_private_session_rollback():
    async def run():
        entered=asyncio.Event();calls=[]
        async def handler(request):
            calls.append(request)
            if request.url.path.endswith('/capabilities'):
                return httpx.Response(200,json={'serviceId':'runtime-id','managedBehaviorProtocol':draft.PROTOCOL})
            entered.set();await asyncio.Event().wait()
        async with client(handler,True) as api:
            facade=draft.AsyncManagedBehaviors(root(api),allow_experimental=True)
            task=asyncio.create_task(facade.create(SOURCE,admission=draft.BehaviorAdmission(DIGEST,'simulator.managed.finish','finish')))
            await entered.wait();task.cancel()
            with pytest.raises(asyncio.CancelledError):await task
            assert len([request for request in calls if request.url.path.endswith('/create')])==1
    asyncio.run(run())
