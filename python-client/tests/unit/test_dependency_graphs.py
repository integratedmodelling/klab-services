"""Public reference graph pagination/source/cycle controls; no simulator imports."""
import asyncio,hashlib
import httpx,pytest
from test_selections import client,root
from klab_client import Endpoint
from klab_client.errors import InvalidRequestError,ProtocolError,UnsupportedOperationError
import klab_client.dependency_graphs as draft


def node(identity,urn,kind):
    source='literal reference source for '+urn
    return {'nodeId':identity,'urn':urn,'knowledgeClass':kind,'providerId':'resources-id',
        'version':'1.0.0','sourceRevision':1,'source':source,'sourceSha256':hashlib.sha256(source.encode()).hexdigest(),
        'access':{'subject':'alice','contextId':'s.c','right':'READ','revision':1}}


def page(index):
    return {'protocol':draft.PROTOCOL,'contextId':'s.c','resourcesServiceId':'resources-id',
        'requestedUrn':'synthetic.model','requestedClass':'MODEL','snapshotId':'f'*32,'revision':1,
        'rootNodeId':'a'*32,'nodeCount':2,'edgeCount':1,'complete':True,
        'providers':{'resources-id':'https://resources.test'},
        'nodes':[node('a'*32,'synthetic.model','MODEL')] if index==0 else [node('b'*32,'synthetic:input','RESOURCE')],
        'edges':[{'sourceId':'a'*32,'targetId':'b'*32,'kind':'DEPENDS_ON'}] if index==0 else [],
        'nextCursor':'c'*32 if index==0 else None}


@pytest.mark.parametrize('native_async',[False,True])
def test_two_pages_and_complete_sourcebound_reference_graph(native_async):
    pages=0
    def handler(request):
        nonlocal pages
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json={'serviceId':'resources-id','dependencyGraphProtocol':draft.PROTOCOL})
        assert request.url.host=='resources.test' and request.headers['klab-scope']=='s.c'
        result=page(pages);pages+=1
        return httpx.Response(200,json=result)
    async def run():
        async with client(handler,True,resources=Endpoint('https://resources.test')) as api:
            return await draft.AsyncDependencyGraphs(root(api),allow_experimental=True).inspect('synthetic.model',page_size=1)
    if native_async:value=asyncio.run(run())
    else:
        with client(handler,resources=Endpoint('https://resources.test')) as api:
            value=draft.DependencyGraphs(root(api),allow_experimental=True).inspect('synthetic.model',page_size=1)
    assert pages==2 and len(value.nodes)==2 and len(value.edges)==1
    assert all(n.source_sha256==hashlib.sha256(n.source.encode()).hexdigest() for n in value.nodes)


@pytest.mark.parametrize('defect',['changed-snapshot','cycle','missing-edge','unknown-access','source-hash','repeated-cursor','page-budget'])
@pytest.mark.parametrize('native_async',[False,True])
def test_refuses_partial_or_unverifiable_graph(defect,native_async):
    pages=0
    def handler(request):
        nonlocal pages
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json={'serviceId':'resources-id','dependencyGraphProtocol':draft.PROTOCOL})
        result=page(pages);pages+=1
        if defect=='changed-snapshot' and pages==2:result['snapshotId']='d'*32
        if defect=='cycle':
            result['edgeCount']=2
            if pages==2:result['edges']=[{'sourceId':'b'*32,'targetId':'a'*32,'kind':'DEPENDS_ON'}]
        if defect=='missing-edge' and pages==1:result['edges'][0]['targetId']='e'*32
        if defect=='unknown-access':result['nodes'][0]['access']['right']='UNKNOWN'
        if defect=='source-hash':result['nodes'][0]['sourceSha256']='0'*64
        if defect=='repeated-cursor':result['nextCursor']='c'*32
        return httpx.Response(200,json=result)
    async def run():
        async with client(handler,True,resources=Endpoint('https://resources.test')) as api:
            graphs=draft.AsyncDependencyGraphs(root(api),allow_experimental=True,max_pages=1 if defect=='page-budget' else 16)
            with pytest.raises(ProtocolError):await graphs.inspect('synthetic.model',page_size=1)
    if native_async:asyncio.run(run())
    else:
        with client(handler,resources=Endpoint('https://resources.test')) as api:
            graphs=draft.DependencyGraphs(root(api),allow_experimental=True,max_pages=1 if defect=='page-budget' else 16)
            with pytest.raises(ProtocolError):graphs.inspect('synthetic.model',page_size=1)


def invoke(transform,*,native_async=False,**limits):
    pages=0
    def handler(request):
        nonlocal pages
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json={'serviceId':'resources-id','dependencyGraphProtocol':draft.PROTOCOL})
        result=transform(page(pages),pages);pages+=1
        return httpx.Response(200,json=result)
    async def run():
        async with client(handler,True,resources=Endpoint('https://resources.test')) as api:
            return await draft.AsyncDependencyGraphs(root(api),allow_experimental=True,**limits).inspect('synthetic.model',page_size=1)
    if native_async:return asyncio.run(run())
    with client(handler,resources=Endpoint('https://resources.test')) as api:
        return draft.DependencyGraphs(root(api),allow_experimental=True,**limits).inspect('synthetic.model',page_size=1)


@pytest.mark.parametrize('change',[{'protocol':'other'},{'contextId':'other'},
    {'resourcesServiceId':'other'},{'requestedUrn':'other'},{'requestedClass':'RESOURCE'},
    {'snapshotId':None},{'rootNodeId':'bad'},{'complete':False},{'complete':1},
    {'revision':True},{'revision':0},{'nodeCount':0},{'edgeCount':-1},{'nodeCount':257},
    {'providers':[]},{'providers':{'':'https://provider.test'}},
    {'providers':{'provider':None}},{'providers':{'provider':'https://user:password@provider.test'}},
    {'nodes':None},{'edges':None},{'nextCursor':'https://untrusted.test/next'}, {'extra':True}])
def test_hostile_page_envelopes_never_become_graph_evidence(change):
    with pytest.raises(ProtocolError):invoke(lambda value,index:{**value,**change})


@pytest.mark.parametrize('change',[{'nodeId':'bad'},{'urn':None},{'knowledgeClass':''},{'knowledgeClass':'UNKNOWN'},{'providerId':'x'*513},
    {'version':None},{'version':'latest'},{'version':'1.*'},{'version':'1.0.2147483648'},
    {'sourceRevision':True},{'sourceRevision':0},{'source':None},{'source':'x'*16385},
    {'sourceSha256':'bad'},{'access':None},{'access':{'subject':'alice','contextId':'s.c','right':'READ','revision':True}},
    {'access':{'subject':'alice','contextId':'s.c','right':'READ','revision':2}},
    {'access':{'subject':'alice','contextId':'other','right':'READ','revision':1}}, {'extra':True}])
def test_source_identity_version_access_must_be_explicit_and_exact(change):
    def transform(value,index):
        value['nodes'][0].update(change);return value
    with pytest.raises(ProtocolError):invoke(transform)


@pytest.mark.parametrize('change',[{'sourceId':'bad'},{'targetId':None},{'kind':'HAS_CHILD'},{'extra':True}])
def test_edges_are_not_guessed_from_order_or_untyped_metadata(change):
    def transform(value,index):
        if value['edges']:value['edges'][0].update(change)
        return value
    with pytest.raises(ProtocolError):invoke(transform)


@pytest.mark.parametrize('defect',['duplicate-node','duplicate-identity','wrong-root','wrong-root-class',
    'missing-provider','different-subject','orphan','wrong-total','duplicate-edge'])
def test_whole_graph_completeness_and_consistency_cannot_be_forged(defect):
    def transform(value,index):
        if defect=='duplicate-node' and index==1:value['nodes'][0]['nodeId']='a'*32
        if defect=='duplicate-identity' and index==1:value['nodes'][0].update(urn='synthetic.model',knowledgeClass='MODEL')
        if defect=='wrong-root' and index==0:value['nodes'][0]['urn']='different'
        if defect=='wrong-root-class' and index==0:value['nodes'][0]['knowledgeClass']='RESOURCE'
        if defect=='missing-provider':value['providers']={}
        if defect=='different-subject' and index==1:value['nodes'][0]['access']['subject']='bob'
        if defect=='orphan':value.update(edges=[],edgeCount=0)
        if defect=='wrong-total':value['nodeCount']=3
        if defect=='duplicate-edge':
            value['edgeCount']=2
            if index==0:value['edges']*=2
        return value
    with pytest.raises(ProtocolError):invoke(transform)


@pytest.mark.parametrize('native_async',[False,True])
def test_unadvertised_graph_reference_never_calls_a_graph_endpoint(native_async):
    calls=[]
    def handler(request):
        calls.append(request)
        assert request.url.path.endswith('/capabilities')
        return httpx.Response(200,json={'serviceId':'resources-id'})
    async def run():
        async with client(handler,True,resources=Endpoint('https://resources.test')) as api:
            with pytest.raises(UnsupportedOperationError):await draft.AsyncDependencyGraphs(root(api),allow_experimental=True).inspect('synthetic.model')
    if native_async:asyncio.run(run())
    else:
        with client(handler,resources=Endpoint('https://resources.test')) as api:
            with pytest.raises(UnsupportedOperationError):draft.DependencyGraphs(root(api),allow_experimental=True).inspect('synthetic.model')
    assert len(calls)==1


@pytest.mark.parametrize('limits',[{'max_nodes':0},{'max_nodes':True},{'max_edges':1025},{'max_pages':65}])
def test_graph_budgets_are_explicit_before_any_http(limits):
    def offline(request):raise AssertionError('No HTTP before bounded options')
    with client(offline) as api:
        with pytest.raises(InvalidRequestError):draft.DependencyGraphs(root(api),allow_experimental=True,**limits)


def test_opt_in_root_and_input_refusals_before_http():
    def offline(request):raise AssertionError('No HTTP before valid explicit root/input')
    with client(offline) as api:
        with pytest.raises(InvalidRequestError):draft.DependencyGraphs(root(api))
        with pytest.raises(InvalidRequestError):draft.AsyncDependencyGraphs(root(api),allow_experimental=True)
        graphs=draft.DependencyGraphs(root(api),allow_experimental=True)
        with pytest.raises(InvalidRequestError):graphs.inspect('https://untrusted.test')
        with pytest.raises(UnsupportedOperationError):graphs.inspect('synthetic.model',knowledge_class='UNKNOWN')
        with pytest.raises(InvalidRequestError):graphs.inspect('synthetic.model',page_size=True)


@pytest.mark.parametrize('native_async',[False,True])
def test_aggregate_edge_budget_refuses_pages_even_when_each_page_is_small(native_async):
    def transform(value,index):
        value['edges']=[{'sourceId':'a'*32,'targetId':'b'*32,'kind':'DEPENDS_ON'}]
        return value
    with pytest.raises(ProtocolError,match='aggregate budget'):
        invoke(transform,native_async=native_async,max_edges=1)


def test_shared_dependency_dag_is_not_misclassified_as_cycle():
    def transform(value,index):
        value.update(nodeCount=3,edgeCount=3)
        if index==0:
            value['edges']=[{'sourceId':a*32,'targetId':b*32,'kind':'DEPENDS_ON'} for a,b in (('a','b'),('a','c'),('b','c'))]
        if index==1:value['nextCursor']='d'*32
        if index==2:value['nodes'][0].update(nodeId='c'*32,urn='synthetic:shared')
        return value
    snapshot=invoke(transform)
    assert len(snapshot.nodes)==len(snapshot.edges)==3


def test_configured_credential_reflection_never_returns_source_or_access_receipt():
    credential='unique-graph-secret'
    def handler(request):
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json={'serviceId':'resources-id','dependencyGraphProtocol':draft.PROTOCOL})
        value=page(0);value['nodes'][0]['source']=credential
        value['nodes'][0]['sourceSha256']=hashlib.sha256(credential.encode()).hexdigest()
        return httpx.Response(200,json=value)
    with client(handler,resources=Endpoint('https://resources.test',credential)) as api:
        with pytest.raises(ProtocolError) as error:draft.DependencyGraphs(root(api),allow_experimental=True).inspect('synthetic.model')
    assert credential not in str(error.value)


@pytest.mark.parametrize('native_async',[False,True])
def test_endpoint_drift_after_read_discards_entire_graph_without_following_url(native_async):
    api=None;calls=[]
    def handler(request):
        calls.append(request)
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json={'serviceId':'resources-id','dependencyGraphProtocol':draft.PROTOCOL})
        api.transport.endpoints['resources']=Endpoint('https://untrusted.test')
        return httpx.Response(200,json=page(0))
    async def run():
        nonlocal api
        async with client(handler,True,resources=Endpoint('https://resources.test')) as current:
            api=current
            with pytest.raises(ProtocolError):await draft.AsyncDependencyGraphs(root(api),allow_experimental=True).inspect('synthetic.model')
    if native_async:asyncio.run(run())
    else:
        with client(handler,resources=Endpoint('https://resources.test')) as current:
            api=current
            with pytest.raises(ProtocolError):draft.DependencyGraphs(root(api),allow_experimental=True).inspect('synthetic.model')
    assert len(calls)==2 and all(r.url.host=='resources.test' for r in calls)


def test_replacing_context_client_is_not_an_implicit_rebind_even_for_same_runtime():
    def offline(request):raise AssertionError('No HTTP after client replacement')
    with client(offline,resources=Endpoint('https://resources.test')) as first,client(offline,resources=Endpoint('https://untrusted.test')) as second:
        context=root(first);graphs=draft.DependencyGraphs(context,allow_experimental=True)
        context.client=second
        with pytest.raises(ProtocolError):graphs.inspect('synthetic.model')
