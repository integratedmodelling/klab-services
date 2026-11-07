"""Public complete finite-reference controls; no simulator imports/native science."""
import asyncio,hashlib,json
from copy import deepcopy
from dataclasses import replace
import httpx,pytest
from test_selections import client,root
from klab_client import Endpoint
from klab_client.client import Job
from klab_client.errors import InvalidRequestError,ProtocolError,ServerError,SubmissionOutcomeUnknown,UnsupportedOperationError,TransportError
import klab_client.reference_commits as draft


def digest(nodes,edges):
    body={'nodes':sorted(nodes,key=lambda n:n['id']),
        'edges':sorted(edges,key=lambda e:(e['sourceId'],e['targetId'],e['relationship']))}
    return hashlib.sha256(json.dumps(body,sort_keys=True,ensure_ascii=False,allow_nan=False,separators=(',',':')).encode()).hexdigest()


def snapshot(revision=0):
    nodes=[{'id':'context','kind':'CONTEXT','properties':{'contextId':'s.c'}}]
    edges=[]
    if revision:
        nodes.append({'id':'observation:1','kind':'OBSERVATION','properties':{'urn':'s.c.1','valueOrigin':'FINITE_REFERENCE'}})
        edges=[{'sourceId':'context','targetId':'observation:1','relationship':'CONTAINS'}]
    return {'protocol':draft.PROTOCOL,'kind':draft.KIND,'contextId':'s.c','runtimeId':'runtime-id','epoch':'a'*32,
        'revision':revision,'digest':digest(nodes,edges),'nodes':nodes,'edges':edges,'complete':True}


def chain():
    before,after=snapshot(),snapshot(1)
    return {'protocol':draft.PROTOCOL,'contextId':'s.c','runtimeId':'runtime-id','epoch':'a'*32,
        'baseRevision':0,'baseDigest':before['digest'],'headRevision':1,'headDigest':after['digest'],'complete':True,
        'deltas':[{'previousRevision':0,'revision':1,'previousDigest':before['digest'],'digest':after['digest'],
            'removeNodes':[],'removeEdges':[],'upsertNodes':[after['nodes'][1]],'upsertEdges':after['edges']}]}


def test_reference_delta_reconciliation_returns_same_complete_graph_and_old_view_immutable():
    def handler(request):
        if request.url.path.endswith('/capabilities'):return httpx.Response(200,json={'serviceId':'runtime-id','graphCommitProtocol':draft.PROTOCOL})
        return httpx.Response(200,json=chain() if request.url.path.endswith('/changes') else snapshot())
    with client(handler) as api:
        commits=draft.ReferenceCommits(root(api),allow_experimental=True)
        before=commits.snapshot();after=commits.reconcile(before)
        assert before.revision==0 and len(before.nodes)==1
        assert after.revision==1 and len(after.nodes)==2 and after.digest==snapshot(1)['digest']


@pytest.mark.parametrize('defect',['gap','wrong-digest','missing-endpoint','duplicate-upsert','partial','wrong-head'])
def test_invalid_delta_never_modifies_previously_accepted_snapshot(defect):
    value=chain()
    if defect=='gap':value['deltas'][0]['previousRevision']=2
    if defect=='wrong-digest':value['deltas'][0]['digest']='0'*64
    if defect=='missing-endpoint':value['deltas'][0]['upsertEdges'][0]['targetId']='unknown'
    if defect=='duplicate-upsert':value['deltas'][0]['upsertNodes']*=2
    if defect=='partial':value['complete']=False
    if defect=='wrong-head':value['headRevision']=2
    def handler(request):
        if request.url.path.endswith('/capabilities'):return httpx.Response(200,json={'serviceId':'runtime-id','graphCommitProtocol':draft.PROTOCOL})
        return httpx.Response(200,json=value if request.url.path.endswith('/changes') else snapshot())
    with client(handler) as api:
        commits=draft.ReferenceCommits(root(api),allow_experimental=True);before=commits.snapshot()
        with pytest.raises(ProtocolError):commits.reconcile(before)
        assert len(before.nodes)==1 and before.revision==0


def invoke(operation='snapshot',*,native_async=False,value=None,capability=None,hook=None,status=200):
    calls=[];box={}
    def handler(request):
        calls.append(request)
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json=capability or {'serviceId':'runtime-id','graphCommitProtocol':draft.PROTOCOL})
        if hook:hook(box,request)
        default=chain() if operation=='reconcile' else snapshot(1 if operation=='checkpoint' else 0)
        return httpx.Response(status,content=json.dumps(default if value is None else value).encode(),headers={'Content-Type':'application/json'})
    async def run():
        async with client(handler,True) as api:
            commits=draft.AsyncReferenceCommits(root(api),allow_experimental=True);box.update(api=api,commits=commits)
            if operation=='checkpoint':return await commits.checkpoint(expected_revision=0)
            if operation=='history':return await commits.snapshot(revision=0)
            if operation=='reconcile':return await commits.reconcile(commits._snapshot(snapshot()))
            return await commits.snapshot()
    if native_async:return asyncio.run(run()),calls
    with client(handler) as api:
        commits=draft.ReferenceCommits(root(api),allow_experimental=True);box.update(api=api,commits=commits)
        if operation=='checkpoint':result=commits.checkpoint(expected_revision=0)
        elif operation=='history':result=commits.snapshot(revision=0)
        elif operation=='reconcile':result=commits.reconcile(commits._snapshot(snapshot()))
        else:result=commits.snapshot()
        return result,calls


@pytest.mark.parametrize('native_async',[False,True])
@pytest.mark.parametrize('operation',['snapshot','history','checkpoint','reconcile'])
def test_public_sync_async_reference_operations_and_no_implicit_native_routes(native_async,operation):
    result,calls=invoke(operation,native_async=native_async)
    assert result.evidence==draft.KIND
    actual=[r for r in calls if not r.url.path.endswith('/capabilities')]
    assert len(actual)==1 and actual[0].url.path.startswith(draft.PREFIX)
    if operation=='checkpoint':assert json.loads(actual[0].content)=={'protocol':draft.PROTOCOL,'expectedRevision':0}


@pytest.mark.parametrize('field,bad',[('protocol','other'),('kind','NATIVE'),('contextId','foreign'),
    ('runtimeId','foreign'),('epoch','bad'),('revision',True),('complete',False),('nodes',None),('edges',{}),
    ('digest','0'*64)])
def test_hostile_snapshot_envelope_never_becomes_complete_graph(field,bad):
    value=snapshot();value[field]=bad
    with pytest.raises(ProtocolError):invoke(value=value)


@pytest.mark.parametrize('defect',['node-shape','node-id','node-kind','duplicate-node','no-context','wrong-context',
    'edge-shape','edge-endpoint','edge-kind','duplicate-edge','contains-kind','produced-kind','orphan','nodes-budget','edges-budget',
    'secret','unicode','bytes-budget'])
def test_complete_graph_membership_types_digest_privacy_and_budgets(defect):
    value=snapshot(1);nodes=value['nodes'];edges=value['edges']
    if defect=='node-shape':nodes[1]['extra']=1
    if defect=='node-id':nodes[1]['id']=True
    if defect=='node-kind':nodes[1]['kind']='NATIVE_ENGINE'
    if defect=='duplicate-node':nodes.append(deepcopy(nodes[1]))
    if defect=='no-context':nodes[0]['kind']='OBSERVATION'
    if defect=='wrong-context':nodes[0]['properties']['contextId']='foreign'
    if defect=='edge-shape':edges[0]['extra']=True
    if defect=='edge-endpoint':edges[0]['targetId']='missing'
    if defect=='edge-kind':edges[0]['relationship']='EXECUTED_NATIVE'
    if defect=='duplicate-edge':edges.append(deepcopy(edges[0]))
    if defect=='contains-kind':edges[0]['sourceId']='observation:1'
    if defect=='produced-kind':edges[0]['relationship']='PRODUCED'
    if defect=='orphan':edges.clear()
    if defect=='nodes-budget':value['nodes']=nodes*129
    if defect=='edges-budget':value['edges']=edges*1025
    if defect=='secret':nodes[1]['properties']['private']='current-secret'
    if defect=='unicode':nodes[1]['properties']['name']='\ud800'
    if defect=='bytes-budget':nodes[1]['properties']['payload']='x'*(2*1024*1024)
    with pytest.raises(ProtocolError):invoke(value=value)


@pytest.mark.parametrize('native_async',[False,True])
def test_missing_advertisement_wrong_runtime_and_postread_rebinding_refuse(native_async):
    for capability in ({'serviceId':'runtime-id'}, {'serviceId':'foreign','graphCommitProtocol':draft.PROTOCOL}):
        with pytest.raises((ProtocolError,UnsupportedOperationError)):
            invoke(native_async=native_async,capability=capability)
    def drift(box,request):box['api'].transport.endpoints['runtime']=Endpoint('https://runtime.test','different-secret')
    with pytest.raises(ProtocolError):invoke(native_async=native_async,hook=drift)
    with pytest.raises(SubmissionOutcomeUnknown):invoke('checkpoint',native_async=native_async,hook=drift)


@pytest.mark.parametrize('native_async',[False,True])
def test_wrong_checkpoint_ack_and_historical_revision_not_silently_accepted(native_async):
    with pytest.raises(SubmissionOutcomeUnknown):invoke('checkpoint',native_async=native_async,value=snapshot())
    with pytest.raises(ProtocolError):invoke('history',native_async=native_async,value=snapshot(1))
    def disconnected(box,request):raise httpx.ReadError('lost response')
    with pytest.raises(SubmissionOutcomeUnknown):invoke('checkpoint',native_async=native_async,hook=disconnected)


@pytest.mark.parametrize('field,bad',[('protocol','other'),('contextId','foreign'),('runtimeId','foreign'),('epoch','b'*32),
    ('baseRevision',True),('baseDigest','0'*64),('headRevision',True),('complete',False),('deltas',{}),('deltas',[{}]*9)])
def test_delta_chain_exact_base_and_continuity_required(field,bad):
    value=chain();value[field]=bad
    with pytest.raises(ProtocolError):invoke('reconcile',value=value)


@pytest.mark.parametrize('defect',['shape','bool-revision','previous-digest','list-shape','node-budget','edge-budget',
    'remove-missing-node','remove-bad-node','remove-edge-shape','remove-missing-edge','upsert-node-shape',
    'upsert-edge-shape','duplicate-edge-upsert'])
def test_invalid_delta_operations_preserve_original_view(defect):
    value=chain();delta=value['deltas'][0]
    if defect=='shape':delta['extra']=True
    if defect=='bool-revision':delta['revision']=True
    if defect=='previous-digest':delta['previousDigest']='0'*64
    if defect=='list-shape':delta['removeNodes']={}
    if defect=='node-budget':delta['upsertNodes']*=513
    if defect=='edge-budget':delta['upsertEdges']*=2049
    if defect=='remove-missing-node':delta['removeNodes']=['missing']
    if defect=='remove-bad-node':delta['removeNodes']=[True]
    if defect=='remove-edge-shape':delta['removeEdges']=[{'sourceId':[]}]
    if defect=='remove-missing-edge':delta['removeEdges']=deepcopy(delta['upsertEdges'])
    if defect=='upsert-node-shape':delta['upsertNodes']=[{}]
    if defect=='upsert-edge-shape':delta['upsertEdges']=[{}]
    if defect=='duplicate-edge-upsert':delta['upsertEdges']*=2
    with pytest.raises(ProtocolError):invoke('reconcile',value=value)


@pytest.mark.parametrize('native_async',[False,True])
def test_projection_explicit_resync_refresh_hints_history_and_failure(native_async):
    state={'value':snapshot(),'changes':chain(),'status':200};box={}
    def handler(request):
        if request.url.path.endswith('/capabilities'):return httpx.Response(200,json={'serviceId':'runtime-id','graphCommitProtocol':draft.PROTOCOL})
        return httpx.Response(state['status'],json=state['changes'] if request.url.path.endswith('/changes') else state['value'])
    async def run():
        async with client(handler,True) as api:
            context=root(api);commits=draft.AsyncReferenceCommits(context,allow_experimental=True)
            projection=draft.AsyncReferenceGraphProjection(commits)
            with pytest.raises(InvalidRequestError):await projection.refresh()
            before=(await projection.resync()).snapshot
            for identity in (2,1,2):projection.hint(Job(api,identity,context))
            assert projection.view.invalidated and projection.view.snapshot==before
            after=(await projection.refresh()).snapshot
            assert after.revision==1 and not projection.view.invalidated
            for status in (409,410,500):
                state['status']=status
                with pytest.raises(draft.ReferenceResyncRequired if status!=500 else ServerError):await projection.refresh()
                assert projection.view.snapshot==after and projection.view.invalidated
                if status!=500:
                    with pytest.raises(draft.ReferenceResyncRequired):await projection.refresh()
                state['status']=200;state['value']=snapshot(1);await projection.resync()
            projection.clear();assert projection.view.snapshot is None
    def sync():
        with client(handler) as api:
            context=root(api);commits=draft.ReferenceCommits(context,allow_experimental=True)
            projection=draft.ReferenceGraphProjection(commits)
            with pytest.raises(InvalidRequestError):projection.refresh()
            before=projection.resync().snapshot
            for identity in (2,1,2):projection.hint(Job(api,identity,context))
            assert projection.view.invalidated and projection.view.snapshot==before
            after=projection.refresh().snapshot
            assert after.revision==1 and not projection.view.invalidated
            for status in (409,410,500):
                state['status']=status
                with pytest.raises(draft.ReferenceResyncRequired if status!=500 else ServerError):projection.refresh()
                assert projection.view.snapshot==after and projection.view.invalidated
                if status!=500:
                    with pytest.raises(draft.ReferenceResyncRequired):projection.refresh()
                state['status']=200;state['value']=snapshot(1);projection.resync()
            projection.clear();assert projection.view.snapshot is None
    asyncio.run(run()) if native_async else sync()


@pytest.mark.parametrize('native_async',[False,True])
@pytest.mark.parametrize('revision',[True,-1,2**53,'1'])
def test_bad_explicit_revisions_refuse_without_network(native_async,revision):
    def offline(request):raise AssertionError('Invalid selector must not contact server')
    async def run():
        async with client(offline,True) as api:
            commits=draft.AsyncReferenceCommits(root(api),allow_experimental=True)
            with pytest.raises(InvalidRequestError):await commits.snapshot(revision=revision)
            with pytest.raises(InvalidRequestError):await commits.checkpoint(expected_revision=revision)
            with pytest.raises(InvalidRequestError):await commits.reconcile(None)
    if native_async:asyncio.run(run())
    else:
        with client(offline) as api:
            commits=draft.ReferenceCommits(root(api),allow_experimental=True)
            with pytest.raises(InvalidRequestError):commits.snapshot(revision=revision)
            with pytest.raises(InvalidRequestError):commits.checkpoint(expected_revision=revision)
            with pytest.raises(InvalidRequestError):commits.reconcile(None)


def test_facade_root_fences_optin_and_projection_binding_guards():
    def offline(request):raise AssertionError('No implicit request for local binding checks')
    with client(offline) as api:
        context=root(api)
        with pytest.raises(InvalidRequestError):draft.ReferenceCommits(context)
        with pytest.raises(InvalidRequestError):draft.ReferenceGraphProjection(None)
        commits=draft.ReferenceCommits(context,allow_experimental=True)
        projection=draft.ReferenceGraphProjection(commits)
        for hint in (None,Job(api,1,'foreign.root')):
            with pytest.raises(InvalidRequestError):projection.hint(hint)
        base=commits._snapshot(snapshot())
        for changed in (replace(base,context_id='foreign'),replace(base,epoch='bad'),replace(base,nodes=()),replace(base,digest='0'*64)):
            with pytest.raises((InvalidRequestError,ProtocolError)):commits.reconcile(changed)
        huge={'id':'context','kind':'CONTEXT','properties':{'contextId':'s.c','payload':'x'*(2*1024*1024)}}
        with pytest.raises(ProtocolError):commits.reconcile(replace(base,nodes=(huge,)))
        commits.context=context.within(1)
        with pytest.raises(ProtocolError):commits.snapshot()


@pytest.mark.parametrize('native_async',[False,True])
def test_node_edge_removal_and_empty_delta_chain_have_exact_digests(native_async):
    before=snapshot();after=snapshot(1);value=chain()
    # Add then remove within one pinned contiguous chain. Final graph equals revision0,
    # but reference head advanced twice, not inferred from equal content digests.
    value['deltas'].append({'previousRevision':1,'revision':2,'previousDigest':after['digest'],'digest':before['digest'],
        'removeNodes':['observation:1'],'removeEdges':after['edges'],'upsertNodes':[],'upsertEdges':[]})
    value.update(headRevision=2,headDigest=before['digest'])
    result,_=invoke('reconcile',native_async=native_async,value=value)
    assert result.revision==2 and len(result.nodes)==1 and not result.edges
    value.update(headRevision=0,headDigest=before['digest'],deltas=[])
    result,_=invoke('reconcile',native_async=native_async,value=value)
    assert result.revision==0


@pytest.mark.parametrize('native_async',[False,True])
def test_projection_retains_history_on_malformed_refresh_and_resync(native_async):
    state={'bad':False}
    def handler(request):
        if request.url.path.endswith('/capabilities'):return httpx.Response(200,json={'serviceId':'runtime-id','graphCommitProtocol':draft.PROTOCOL})
        bad={'epoch':'a'*32} if request.url.path.endswith('/changes') else {}
        return httpx.Response(200,json=bad if state['bad'] else snapshot())
    async def run():
        async with client(handler,True) as api:
            projection=draft.AsyncReferenceGraphProjection(draft.AsyncReferenceCommits(root(api),allow_experimental=True))
            before=(await projection.resync()).snapshot;state['bad']=True
            for read in (projection.refresh,projection.resync):
                with pytest.raises(ProtocolError):await read()
                assert projection.view.snapshot==before and projection.view.invalidated
    if native_async:asyncio.run(run())
    else:
        with client(handler) as api:
            projection=draft.ReferenceGraphProjection(draft.ReferenceCommits(root(api),allow_experimental=True))
            before=projection.resync().snapshot;state['bad']=True
            for read in (projection.refresh,projection.resync):
                with pytest.raises(ProtocolError):read()
                assert projection.view.snapshot==before and projection.view.invalidated


def test_async_cancellation_and_clear_during_read_do_not_repopulate_local_data():
    async def run():
        entered=asyncio.Event();release=asyncio.Event();state={'block':False}
        async def handler(request):
            if request.url.path.endswith('/capabilities'):return httpx.Response(200,json={'serviceId':'runtime-id','graphCommitProtocol':draft.PROTOCOL})
            if state['block']:entered.set();await release.wait()
            return httpx.Response(200,json=chain() if request.url.path.endswith('/changes') else snapshot())
        async with client(handler,True) as api:
            commits=draft.AsyncReferenceCommits(root(api),allow_experimental=True)
            projection=draft.AsyncReferenceGraphProjection(commits)
            original=(await projection.resync()).snapshot;state['block']=True
            task=asyncio.create_task(projection.refresh());await entered.wait();task.cancel()
            with pytest.raises(asyncio.CancelledError):await task
            assert projection.view.snapshot==original and projection.view.invalidated
            entered.clear();task=asyncio.create_task(projection.resync());await entered.wait()
            projection.clear();release.set()
            with pytest.raises(ProtocolError):await task
            assert projection.view.snapshot is None and projection.view.invalidated
    asyncio.run(run())


def test_async_hint_during_refresh_keeps_accepted_graph_invalidated():
    async def run():
        box={}
        async def handler(request):
            if request.url.path.endswith('/capabilities'):return httpx.Response(200,json={'serviceId':'runtime-id','graphCommitProtocol':draft.PROTOCOL})
            if request.url.path.endswith('/changes'):box['projection'].hint(Job(box['api'],1,box['context']))
            return httpx.Response(200,json=chain() if request.url.path.endswith('/changes') else snapshot())
        async with client(handler,True) as api:
            context=root(api);projection=draft.AsyncReferenceGraphProjection(draft.AsyncReferenceCommits(context,allow_experimental=True))
            box.update(api=api,context=context,projection=projection)
            await projection.resync();view=await projection.refresh()
            assert view.snapshot.revision==1 and view.invalidated and view.reason=='JOB_HINT_NOT_COMMIT'
    asyncio.run(run())


@pytest.mark.parametrize('native_async',[False,True])
def test_projection_slow_and_forked_snapshot_resync_refused(native_async):
    state={'value':snapshot(1)}
    def handler(request):
        if request.url.path.endswith('/capabilities'):return httpx.Response(200,json={'serviceId':'runtime-id','graphCommitProtocol':draft.PROTOCOL})
        return httpx.Response(200,json=state['value'])
    async def run():
        async with client(handler,True) as api:
            projection=draft.AsyncReferenceGraphProjection(draft.AsyncReferenceCommits(root(api),allow_experimental=True))
            first=(await projection.resync()).snapshot
            state['value']=snapshot()
            with pytest.raises(ProtocolError):await projection.resync()
            state['value']=snapshot(1);state['value']['nodes'][1]['properties']['name']='fork'
            state['value']['digest']=digest(state['value']['nodes'],state['value']['edges'])
            with pytest.raises(ProtocolError):await projection.resync()
            assert projection.view.snapshot==first
    if native_async:asyncio.run(run())
    else:
        with client(handler) as api:
            projection=draft.ReferenceGraphProjection(draft.ReferenceCommits(root(api),allow_experimental=True))
            first=projection.resync().snapshot;state['value']=snapshot()
            with pytest.raises(ProtocolError):projection.resync()
            assert projection.view.snapshot==first


@pytest.mark.parametrize('native_async',[False,True])
def test_projection_explicit_epoch_error_never_hides_full_resync(native_async):
    def handler(request):
        if request.url.path.endswith('/capabilities'):return httpx.Response(200,json={'serviceId':'runtime-id','graphCommitProtocol':draft.PROTOCOL})
        value=chain() if request.url.path.endswith('/changes') else snapshot()
        if 'deltas' in value:value['epoch']='b'*32
        return httpx.Response(200,json=value)
    async def run():
        async with client(handler,True) as api:
            projection=draft.AsyncReferenceGraphProjection(draft.AsyncReferenceCommits(root(api),allow_experimental=True))
            first=(await projection.resync()).snapshot
            with pytest.raises(draft.ReferenceResyncRequired):await projection.refresh()
            assert projection.view.snapshot==first and projection.view.reason=='EXPLICIT_RESYNC_REQUIRED'
    if native_async:asyncio.run(run())
    else:
        with client(handler) as api:
            projection=draft.ReferenceGraphProjection(draft.ReferenceCommits(root(api),allow_experimental=True))
            first=projection.resync().snapshot
            with pytest.raises(draft.ReferenceResyncRequired):projection.refresh()
            assert projection.view.snapshot==first and projection.view.reason=='EXPLICIT_RESYNC_REQUIRED'


def test_async_concurrent_old_epoch_response_cannot_replace_new_epoch_view():
    async def run():
        entered=asyncio.Event();release=asyncio.Event();state={'block':False,'epoch':'a'*32}
        async def handler(request):
            if request.url.path.endswith('/capabilities'):return httpx.Response(200,json={'serviceId':'runtime-id','graphCommitProtocol':draft.PROTOCOL})
            value=snapshot();value['epoch']=state['epoch']
            if state['block']:
                state['block']=False;entered.set();await release.wait()
            return httpx.Response(200,json=value)
        async with client(handler,True) as api:
            projection=draft.AsyncReferenceGraphProjection(draft.AsyncReferenceCommits(root(api),allow_experimental=True))
            await projection.resync();state['block']=True
            old=asyncio.create_task(projection.resync());await entered.wait()
            state['epoch']='b'*32;new=await projection.resync();release.set()
            with pytest.raises(ProtocolError):await old
            assert projection.view.snapshot==new.snapshot and projection.view.snapshot.epoch=='b'*32
    asyncio.run(run())
