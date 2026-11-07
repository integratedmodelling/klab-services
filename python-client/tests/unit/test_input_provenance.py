"""SDK-only independent wire claims; no simulator imports or fixture internals."""
import asyncio
from dataclasses import replace
import hashlib
import json

import httpx
import pytest

from klab_client import Endpoint
from klab_client.authoring import AuthoringReceipt
from klab_client.async_client import AsyncJob
from klab_client.client import Job
from klab_client.dto import observation_from_wire
from klab_client.errors import InvalidRequestError, JobFailedError, ProtocolError, TransportError
from klab_client.imports import ImportSubmission
from klab_client.input_provenance import AsyncInputProvenance, InputProvenance, MANIFEST
from test_dependency_graphs import node
from test_selections import client, root, wire

SOURCE = 'namespace input.example;\nmodel example:input as geography:Elevation in m;\n'
BINARY = hashlib.sha256(b'independent binary').hexdigest()


def claims():
    manifest = json.dumps({'protocol':MANIFEST, 'resourceUrn':'example:input', 'schemaId':'example.tiff',
        'binarySha256':BINARY, 'binaryBytes':18}, sort_keys=True)
    nodes = [node('a'*32, 'input.example.model', 'MODEL'), node('b'*32, 'input.example', 'NAMESPACE'),
        node('c'*32, 'example:input', 'RESOURCE')]
    for entry, source in zip(nodes, ('literal model binding', SOURCE, manifest)):
        entry.update(source=source, sourceSha256=hashlib.sha256(source.encode()).hexdigest())
    graph = {'protocol':'klab.experimental.dependency-graphs/1', 'contextId':'s.c', 'resourcesServiceId':'resources-id',
        'requestedUrn':'input.example.model', 'requestedClass':'MODEL', 'snapshotId':'f'*32, 'revision':1,
        'rootNodeId':'a'*32, 'nodeCount':3, 'edgeCount':2, 'complete':True,
        'providers':{'resources-id':'https://resources.test'}, 'nodes':nodes,
        'edges':[{'sourceId':'a'*32, 'targetId':target*32, 'kind':'DEPENDS_ON'} for target in ('b','c')], 'nextCursor':None}
    plan = {'resolutionOutcome':'RESOLVED', 'empty':False,
        'computation':[{'actuatorType':'OBSERVE','computation':[{'urn':'input.example.model'}],'children':[]}],
        'requirements':{'results':[
        {'resourceUrn':n['urn'], 'knowledgeClass':n['knowledgeClass'], 'serviceId':n['providerId'],
            'resourceVersion':n['version'], 'sourceSha256':n['sourceSha256']} for n in (nodes[0], nodes[2])]}}
    imported = {'empty':False, 'results':[{'resourceUrn':'example:input', 'knowledgeClass':'RESOURCE', 'serviceId':'resources-id'}]}
    observation = wire()
    observation['metadata'] = {'delegate':{'input':{'resourceUrn':'example:input', 'modelUrn':'input.example.model',
        'sourceSha256':BINARY, 'sourceBytes':18, 'manifestSha256':nodes[2]['sourceSha256'],
        'annotationSha256':nodes[1]['sourceSha256'], 'resourceVersion':'1.0.0', 'modelVersion':'1.0.0'}}}
    return {'graph':graph, 'plan':plan, 'imported':imported, 'observation':observation}


def invoke(transform=lambda data: None, *, native_async=False, change_args=lambda args: args, status='FINISHED', disconnect=False, after_query=lambda api:None, timeout=5):
    data = claims(); transform(data); calls=[]; owner={}
    def handler(request):
        calls.append(request)
        assert request.url.host in ('resources.test','resolver.test','runtime.test')
        if '/jobs/status/' in request.url.path:
            return httpx.Response(200,json={'status':status})
        if '/jobs/retrieve/' in request.url.path:
            if disconnect: raise httpx.ReadError('transport failure')
            return httpx.Response(200,json=data['imported'] if request.url.host=='resources.test' else data['plan'])
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json={'serviceId':'resources-id','dependencyGraphProtocol':'klab.experimental.dependency-graphs/1'})
        if '/experimental/dependencies/' in request.url.path:return httpx.Response(200,json=data['graph'])
        assert request.url.path.endswith('/query')
        after_query(owner['api'])
        return httpx.Response(200,json=data.get('membership',[data['observation']]))
    peers = {'resources':Endpoint('https://resources.test'), 'resolver':Endpoint('https://resolver.test')}
    def args(api, context):
        owner['api']=api
        cls = AsyncJob if native_async else Job
        submission = ImportSubmission(cls(api,1,context,service='resources'), 'https://resources.test', 'resources-id',
            'example.tiff', BINARY, 18, 'example:input')
        binding = AuthoringReceipt('NAMESPACE','input.example','CREATE','https://resources.test','resources-id',())
        plan = cls(api,2,context,service='resolver',decoder=lambda value:value,result_kind='dataflow')
        observation = observation_from_wire(wire(),context)  # No cached input evidence.
        return change_args([submission,binding,SOURCE,plan,observation])
    async def run():
        async with client(handler,True,**peers) as api:
            context=root(api)
            return await AsyncInputProvenance(context,allow_experimental=True).inspect(*args(api,context),model_urn='input.example.model',timeout=timeout)
    if native_async: result=asyncio.run(run())
    else:
        with client(handler,**peers) as api:
            context=root(api)
            result=InputProvenance(context,allow_experimental=True).inspect(*args(api,context),model_urn='input.example.model',timeout=timeout)
    return result,calls


@pytest.mark.parametrize('native_async',[False,True])
def test_public_join_refreshes_observation_and_preserves_distinct_hash_meanings(native_async):
    result,calls=invoke(native_async=native_async)
    assert result.binary_sha256==BINARY and result.manifest_sha256!=BINARY
    assert result.annotation_sha256==hashlib.sha256(SOURCE.encode()).hexdigest()
    assert result.observation_id==1 and result.resource_urn=='example:input'
    assert 'not native' in result.evidence
    assert sum('/jobs/retrieve/' in request.url.path for request in calls)==2
    assert calls[-1].method=='POST' and calls[-1].url.path.endswith('/query')


@pytest.mark.parametrize('changed',[{'results':None},{'results':[1]},{'results':[]},
    {'results':[{'resourceUrn':'other'}]}, {'empty':True}])
def test_failed_or_foreign_import_never_yields_admitted_input(changed):
    with pytest.raises((ProtocolError,JobFailedError)):
        invoke(lambda data:data['imported'].update(changed))


@pytest.mark.parametrize('changed',[{'requirements':None},{'requirements':{'results':None}},
    {'resolutionOutcome':'UNRESOLVED'},{'empty':True},{'computation':[]},
    {'computation':[{'actuatorType':'OBSERVE','computation':[{'urn':'different.model'}],'children':[]}]}])
def test_unresolved_or_incomplete_plan_is_not_selected_lineage(changed):
    with pytest.raises((ProtocolError,JobFailedError)):
        invoke(lambda data:data['plan'].update(changed))


@pytest.mark.parametrize('key,value',[('resourceVersion','1.0.2'),('sourceSha256','0'*64),('serviceId','other'),('resourceUrn','other')])
def test_plan_must_pin_exact_selected_source_version_provider(key,value):
    def transform(data):data['plan']['requirements']['results'][0][key]=value
    with pytest.raises(ProtocolError):invoke(transform)


@pytest.mark.parametrize('kind',['missing-resource','missing-namespace','source','manifest-json','manifest-list',
    'manifest-hash','manifest-byte-bool','recorded','missing-current','different-current','wrong-graph-provider','indirect-edge'])
def test_graph_manifest_source_and_current_input_claims_cannot_be_substituted(kind):
    def transform(data):
        graph=data['graph']; nodes=graph['nodes']
        if kind=='missing-resource':nodes[2]['urn']='other:input'
        if kind=='missing-namespace':nodes[1]['urn']='other.namespace'
        if kind=='source':nodes[1]['source']=SOURCE+'changed'
        if kind.startswith('manifest-'):
            value = {'manifest-json':'not-json','manifest-list':'[]',
                'manifest-hash':nodes[2]['source'].replace(BINARY,'0'*64),
                'manifest-byte-bool':nodes[2]['source'].replace('18','true')}
            nodes[2]['source']=value[kind]
        if kind=='recorded':data['observation']['metadata']['delegate']['input']['sourceSha256']='0'*64
        if kind=='missing-current':data['membership']=[]
        if kind=='different-current':data['observation']['urn']='s.c.other'
        if kind=='wrong-graph-provider':
            graph['resourcesServiceId']='other'
        if kind=='indirect-edge':graph['edges'][1]['sourceId']='b'*32
        for n in nodes:n['sourceSha256']=hashlib.sha256(n['source'].encode()).hexdigest()
    with pytest.raises(ProtocolError):invoke(transform)


@pytest.mark.parametrize('native_async',[False,True])
def test_failed_import_job_and_uncertain_transport_never_replay_upload(native_async):
    with pytest.raises(JobFailedError):invoke(native_async=native_async,status='ABORTED')
    with pytest.raises(TransportError):invoke(native_async=native_async,disconnect=True)


@pytest.mark.parametrize('change',[
    lambda a:[None,*a[1:]], lambda a:[a[0],None,*a[2:]],
    lambda a:[a[0],replace(a[1],operation='UPDATE'),*a[2:]],
    lambda a:[*a[:2],'',*a[3:]], lambda a:[*a[:2],'x'*16385,*a[3:]],
    lambda a:[*a[:2],'\ud800',*a[3:]],
    lambda a:[replace(a[0],source_sha256='bad'),*a[1:]],
    lambda a:[replace(a[0],source_bytes=True),*a[1:]],
    lambda a:[replace(a[0],service_id='other'),*a[1:]],
    lambda a:[replace(a[0],service_url='https://other.test'),*a[1:]],
    lambda a:[*a[:3],object(),a[4]], lambda a:[*a[:4],object()]])
def test_explicit_owned_inputs_and_exact_sources_required_before_http(change):
    with pytest.raises(InvalidRequestError):invoke(change_args=change)


def test_configured_secret_in_current_recorded_input_is_refused_without_echo():
    def transform(data):data['observation']['metadata']['delegate']['input']['private']='current-secret'
    with pytest.raises(ProtocolError) as error:invoke(transform)
    assert 'current-secret' not in str(error.value)


def test_matching_but_foreign_receipt_provider_is_not_the_current_graph_provider():
    def change(args):
        args[0]=replace(args[0],service_id='foreign-id')
        args[1]=replace(args[1],service_id='foreign-id')
        return args
    with pytest.raises(ProtocolError):invoke(change_args=change)


def test_foreign_job_root_or_custom_decoder_is_not_an_import_or_plan_receipt():
    def foreign(args):
        job=args[0].job
        args[0]=replace(args[0],job=Job(job.client,1,'other.root',service='resources'))
        return args
    def decoded(args):
        job=args[0].job
        args[0]=replace(args[0],job=Job(job.client,1,job.scope,service='resources',decoder=lambda value:value))
        return args
    for change in (foreign,decoded):
        with pytest.raises(InvalidRequestError):invoke(change_args=change)


@pytest.mark.parametrize('native_async',[False,True])
def test_credential_or_service_rebinding_during_current_query_discards_join(native_async):
    def drift(api):api.transport.endpoints['runtime']=Endpoint('https://runtime.test','other-credential')
    with pytest.raises(ProtocolError):invoke(native_async=native_async,after_query=drift)


@pytest.mark.parametrize('timeout',[None,True,0,-1,61,float('inf'),float('nan')])
def test_unbounded_or_nonpositive_wait_is_not_implicit(timeout):
    with pytest.raises(InvalidRequestError):invoke(timeout=timeout)
