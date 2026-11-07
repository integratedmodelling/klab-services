"""Public revision-aware validation uses Java-shaped requests, not local admission."""
import asyncio
from dataclasses import replace
import hashlib
import json

import httpx
import pytest

from klab_client import AsyncClient, Client, Endpoint
from klab_client.errors import InvalidRequestError, ProtocolError, UnsupportedOperationError
from klab_client.semantics import StaleKnowledgeError
from klab_client.source_previews import SourcePreview
from klab_client.source_validation import AsyncSourceValidation, SourceValidation

SOURCE = 'namespace synthetic.validation;\n'
HASH = hashlib.sha256(SOURCE.encode()).hexdigest()
DOCUMENT = {'@CLASS':'org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl',
    'urn':'synthetic.validation','sourceCode':SOURCE,'serviceId':'resources-id','statements':[],'notifications':[]}


def preview():
    return SourcePreview('NAMESPACE','synthetic.validation','https://resources.test','resources-id',HASH,HASH,False,DOCUMENT)


def knowledge():
    return {'serviceId':'reasoner-id','consistent':True,'worldviewId':'synthetic','knowledgeRevision':7,
        'semanticSessionId':'session-id','semanticContentFingerprint':'content-id'}


def response():
    return {'status':'COMPLETE','documentUrn':'synthetic.validation','documentVersion':'edit-1','sourceHash':HASH,
        'knowledgeRevision':7,'executableModels':False,'classifierPreparableDependencies':False,
        'executionContext':None,'preparableMemberQualityDependencies':[], 'semanticSessionId':'session-id',
        'semanticContentFingerprint':'content-id','notifications':[]}


def run_validation(native_async, handler, candidate=None, **kwargs):
    peers = {'reasoner':Endpoint('https://reasoner.test','reasoner-secret'),
        'resources':Endpoint('https://resources.test','resources-secret')}
    async def run():
        async with AsyncClient('https://runtime.test', http_transport=httpx.MockTransport(handler), **peers) as api:
            return await AsyncSourceValidation(api).validate(preview() if candidate is None else candidate, document_version='edit-1', **kwargs)
    if native_async: return asyncio.run(run())
    with Client('https://runtime.test', http_transport=httpx.MockTransport(handler), **peers) as api:
        return SourceValidation(api).validate(preview() if candidate is None else candidate, document_version='edit-1', **kwargs)


def handler_for(value=None, state=None, calls=None, change_after=False):
    count = 0
    def handler(request):
        nonlocal count
        if calls is not None: calls.append(request)
        if request.url.host == 'resources.test': return httpx.Response(200,json={'serviceId':'resources-id'})
        if request.url.path.endswith('/capabilities'):
            count += 1
            return httpx.Response(200,json={**(knowledge() if state is None else state), **({'knowledgeRevision':8} if change_after and count > 1 else {})})
        assert request.url.host == 'reasoner.test' and request.url.path == '/api/v1/validate/document'
        body = json.loads(request.content)
        assert body == {'namespace':DOCUMENT,'documentVersion':'edit-1','knowledgeRevision':7,'executableModels':False,
            'classifierPreparableDependencies':False,'executionContext':None,'preparableMemberQualityDependencies':[]}
        return httpx.Response(200,json=response() if value is None else value)
    return handler


@pytest.mark.parametrize('native_async',[False,True])
def test_exact_source_version_and_knowledge_snapshot_no_publication_or_runtime(native_async):
    calls=[]
    receipt=run_validation(native_async,handler_for(calls=calls),expected_revision=7)
    assert receipt.source_sha256 == HASH and receipt.document_version == 'edit-1' and receipt.knowledge.revision == 7
    assert receipt.status == 'COMPLETE' and 'not publication' in receipt.limit
    assert len(calls) == 5 and sum(r.method=='POST' for r in calls)==1
    with pytest.raises(TypeError): receipt.raw['status']='ACCEPTED'


@pytest.mark.parametrize('native_async',[False,True])
def test_revision_race_discards_response_without_retry(native_async):
    calls=[]
    with pytest.raises(StaleKnowledgeError): run_validation(native_async,handler_for(calls=calls,change_after=True))
    assert sum(r.method=='POST' for r in calls)==1


@pytest.mark.parametrize('change,error',[({'sourceHash':'f'*64},ProtocolError),({'documentVersion':'edit-2'},ProtocolError),
    ({'documentUrn':'other'},ProtocolError),({'executableModels':True},ProtocolError),
    ({'knowledgeRevision':8},StaleKnowledgeError),({'semanticSessionId':'restarted'},StaleKnowledgeError),
    ({'semanticContentFingerprint':'changed'},StaleKnowledgeError),({'status':'ADMITTED'},ProtocolError)])
def test_mismatched_receipts_never_become_validation_evidence(change,error):
    with pytest.raises(error): run_validation(False,handler_for({**response(),**change}))


def test_expected_revision_mismatch_and_ontology_are_refused_before_validation_post():
    calls=[]
    with pytest.raises(StaleKnowledgeError): run_validation(False,handler_for(calls=calls),expected_revision=8)
    assert all(r.method=='GET' for r in calls)
    def offline(request): raise AssertionError('No request for unsupported ontology')
    with pytest.raises(UnsupportedOperationError): run_validation(False,offline,replace(preview(),kind='ONTOLOGY'))
    with pytest.raises(InvalidRequestError): run_validation(False,offline,expected_revision=True)


@pytest.mark.parametrize('candidate',[
    object(), replace(preview(),source_sha256='bad'), replace(preview(),parser_source_sha256=None),
    replace(preview(),source_sha256='0'*64),replace(preview(),edge_whitespace_trimmed=1),
    replace(preview(),raw={**DOCUMENT,'sourceCode':'different'}),
    replace(preview(),service_id='other'),replace(preview(),urn='other'),
    replace(preview(),raw={**DOCUMENT,'@CLASS':'unknown'})])
def test_invalid_parser_snapshot_refuses_offline(candidate):
    def offline(request): raise AssertionError('No HTTP for invalid parser snapshot')
    with pytest.raises(InvalidRequestError): run_validation(False,offline,candidate)


@pytest.mark.parametrize('version',[None,'',' ',True,'x'*129])
def test_document_version_is_explicit_offline(version):
    def offline(request): raise AssertionError('No HTTP for invalid version')
    with Client('https://runtime.test',http_transport=httpx.MockTransport(offline)) as api:
        with pytest.raises(InvalidRequestError): SourceValidation(api).validate(preview(),document_version=version)


@pytest.mark.parametrize('change',[{'consistent':False},{'consistent':1},{'knowledgeRevision':None},
    {'knowledgeRevision':True},{'knowledgeRevision':-1},{'knowledgeRevision':2**63},
    {'semanticSessionId':''},{'semanticContentFingerprint':None},{'serviceId':'x'*513}])
def test_unknown_or_malformed_knowledge_does_not_get_sent_as_current(change):
    calls=[]
    with pytest.raises(ProtocolError): run_validation(False,handler_for(state={**knowledge(),**change},calls=calls))
    assert all(r.method=='GET' for r in calls)


@pytest.mark.parametrize('status',['SYNTAX_ERRORS','UNAVAILABLE','FAILED'])
def test_explicit_noncomplete_results_are_not_replaced_with_parser_success(status):
    receipt=run_validation(False,handler_for({**response(),'status':status,'semanticSessionId':None,'semanticContentFingerprint':None}))
    assert receipt.status==status


@pytest.mark.parametrize('change',[{'notifications':None},{'notifications':[1]}, {'status':'STALE_KNOWLEDGE'},
    {'knowledgeRevision':True}, {'classifierPreparableDependencies':True}, {'executionContext':'forged'},
    {'preparableMemberQualityDependencies':[{}]}])
def test_hostile_or_stale_response_is_not_accepted(change):
    with pytest.raises((ProtocolError,StaleKnowledgeError)): run_validation(False,handler_for({**response(),**change}))


def test_foreign_resources_receipt_never_posts_to_reasoner():
    calls=[]
    with pytest.raises(ProtocolError): run_validation(False,handler_for(calls=calls),replace(preview(),service_url='https://foreign.test'))
    assert all(r.method=='GET' for r in calls)


@pytest.mark.parametrize('native_async',[False,True])
def test_credential_change_during_capability_refuses_before_sending_source(native_async):
    api=None; calls=[]
    base=handler_for(calls=calls)
    def handler(request):
        result=base(request)
        if request.url.host=='reasoner.test': api.transport.endpoints['reasoner']=Endpoint('https://foreign.test','changed-secret')
        return result
    peers={'reasoner':Endpoint('https://reasoner.test'),'resources':Endpoint('https://resources.test')}
    async def run():
        nonlocal api
        async with AsyncClient('https://runtime.test',http_transport=httpx.MockTransport(handler),**peers) as current:
            api=current
            with pytest.raises(StaleKnowledgeError): await AsyncSourceValidation(api).validate(preview(),document_version='edit-1')
    if native_async: asyncio.run(run())
    else:
        with Client('https://runtime.test',http_transport=httpx.MockTransport(handler),**peers) as current:
            api=current
            with pytest.raises(StaleKnowledgeError): SourceValidation(api).validate(preview(),document_version='edit-1')
    assert all(r.method=='GET' for r in calls)


def test_async_requires_native_client_and_async_parse_produces_exact_receipt():
    with Client('https://runtime.test') as api:
        with pytest.raises(InvalidRequestError): AsyncSourceValidation(api)
    def handler(request):
        if request.url.path.endswith('/capabilities'): return httpx.Response(200,json={'serviceId':'resources-id'})
        assert request.url.path=='/parseAsset/NAMESPACE' and request.content==SOURCE.encode()
        return httpx.Response(200,json=DOCUMENT)
    async def run():
        async with AsyncClient('https://runtime.test',resources=Endpoint('https://resources.test'),http_transport=httpx.MockTransport(handler)) as api:
            result=await AsyncSourceValidation(api).parse(SOURCE)
            assert result.source_sha256==HASH
    asyncio.run(run())
