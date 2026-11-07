"""Public conditional source reference guards/receipts, no simulator imports."""
import asyncio
from dataclasses import FrozenInstanceError
import hashlib
import json

import httpx
import pytest

from klab_client import Endpoint
from klab_client.accepted_documents import AcceptedDocuments,AsyncAcceptedDocuments
from klab_client.errors import InvalidRequestError,ProtocolError,SubmissionOutcomeUnknown,UnsupportedOperationError
from test_selections import client,root,offline

PROTOCOL='klab.experimental.accepted-documents/1'
NAME='synthetic.document0';SOURCE='namespace synthetic.document0;\n';HASH=hashlib.sha256(SOURCE.encode()).hexdigest()


def knowledge():
    return {'serviceId':'reasoner-id','worldviewId':'synthetic','revision':1,'sessionId':'session','contentFingerprint':'content'}


def document(validation=None):
    return {'protocol':PROTOCOL,'contextId':'s.c','resourcesServiceId':'resources-id','name':NAME,
        'revision':1,'source':SOURCE,'sourceSha256':HASH,'validation':validation}


def validation():
    return {'revision':1,'sourceSha256':HASH,'knowledge':knowledge(),'status':'VALID','diagnostics':[]}


def publication():
    return {'protocol':PROTOCOL,'contextId':'s.c','resourcesServiceId':'resources-id','name':NAME,
        'documentRevision':1,'source':SOURCE,'sourceSha256':HASH,'publicationId':'a'*32,'knowledge':knowledge()}


def handler_for(value,calls=None,capability=None):
    def handler(request):
        if calls is not None: calls.append(request)
        assert request.url.host=='resources.test' and request.headers['Authorization']=='resource-secret'
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json=capability if capability is not None else {'serviceId':'resources-id','acceptedDocumentProtocol':PROTOCOL})
        assert request.headers['klab-scope']=='s.c' and request.headers['klab-service']=='runtime-id'
        return httpx.Response(200,json=value)
    return handler


def run(native_async,handler,operation,**kwargs):
    async def awaited():
        async with client(handler,True,resources=Endpoint('https://resources.test','resource-secret')) as api:
            return await getattr(AsyncAcceptedDocuments(root(api),allow_experimental=True),operation)(**kwargs)
    if native_async: return asyncio.run(awaited())
    with client(handler,resources=Endpoint('https://resources.test','resource-secret')) as api:
        return getattr(AcceptedDocuments(root(api),allow_experimental=True),operation)(**kwargs)


@pytest.mark.parametrize('native_async',[False,True])
def test_all_public_operations_exact_conditional_requests_and_immutable_receipts(native_async):
    calls=[]
    def handler(request):
        calls.append(request)
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json={'serviceId':'resources-id','acceptedDocumentProtocol':PROTOCOL})
        assert request.headers['klab-scope']=='s.c'
        if request.url.path.endswith('/knowledge'): return httpx.Response(200,json=knowledge())
        if request.method=='PUT':
            assert json.loads(request.content)=={'protocol':PROTOCOL,'expectedRevision':0,'source':SOURCE,'sourceSha256':HASH}
            return httpx.Response(200,json=document())
        if request.url.path.endswith('/validate'):
            assert json.loads(request.content)=={'protocol':PROTOCOL,'expectedRevision':1,'expectedKnowledgeRevision':1}
            return httpx.Response(200,json=document(validation()))
        if request.url.path.endswith('/publish') or '/publications/' in request.url.path:
            return httpx.Response(200,json=publication())
        assert request.url.params['revision']=='1'
        return httpx.Response(200,json=document())
    async def awaited():
        async with client(handler,True,resources=Endpoint('https://resources.test')) as api:
            docs=AsyncAcceptedDocuments(root(api),allow_experimental=True)
            assert calls==[]
            assert (await docs.knowledge()).revision==1
            revision=await docs.update(NAME,SOURCE,expected_revision=0)
            assert await docs.read(NAME,revision=1)==revision
            assert (await docs.validate(NAME,expected_revision=1,expected_knowledge_revision=1)).validation['status']=='VALID'
            published=await docs.publish(NAME,expected_revision=1,expected_knowledge_revision=1)
            assert await docs.published(NAME)==published
            return revision,published
    if native_async: revision,published=asyncio.run(awaited())
    else:
        with client(handler,resources=Endpoint('https://resources.test')) as api:
            docs=AcceptedDocuments(root(api),allow_experimental=True)
            assert calls==[]
            assert docs.knowledge().revision==1
            revision=docs.update(NAME,SOURCE,expected_revision=0)
            assert docs.read(NAME,revision=1)==revision
            assert docs.validate(NAME,expected_revision=1,expected_knowledge_revision=1).validation['status']=='VALID'
            published=docs.publish(NAME,expected_revision=1,expected_knowledge_revision=1)
            assert docs.published(NAME)==published
    assert len(calls)==12 and published.source==SOURCE and 'not native' in published.evidence
    with pytest.raises(FrozenInstanceError): revision.source='changed'


@pytest.mark.parametrize('name',[None,'','..','a/b','a?x','x'*129])
def test_name_refusals_are_offline(name):
    with client(offline) as api:
        with pytest.raises(InvalidRequestError): AcceptedDocuments(root(api),allow_experimental=True).read(name)


@pytest.mark.parametrize('revision',[True,-1,2**53,None,1.5])
def test_update_revision_refusals_are_offline(revision):
    with client(offline) as api:
        with pytest.raises(InvalidRequestError): AcceptedDocuments(root(api),allow_experimental=True).update(NAME,SOURCE,expected_revision=revision)


@pytest.mark.parametrize('source',[None,'',' ','\ud800','x'*16385])
def test_source_refusals_are_offline(source):
    with client(offline) as api:
        with pytest.raises(InvalidRequestError): AcceptedDocuments(root(api),allow_experimental=True).update(NAME,source,expected_revision=0)


def test_explicit_opt_in_and_matching_client_required_offline():
    with client(offline) as api:
        with pytest.raises(InvalidRequestError): AcceptedDocuments(root(api))
        with pytest.raises(InvalidRequestError): AsyncAcceptedDocuments(root(api),allow_experimental=True)


@pytest.mark.parametrize('native_async',[False,True])
def test_no_unadvertised_reference_call_or_fallback(native_async):
    calls=[]
    with pytest.raises(UnsupportedOperationError): run(native_async,handler_for({},calls,{'serviceId':'resources-id'}),'knowledge')
    assert len(calls)==1


@pytest.mark.parametrize('change',[{'revision':True},{'revision':0},{'sourceSha256':'wrong'}, {'source':'different'},
    {'protocol':'unknown'},{'contextId':'foreign.root'},{'resourcesServiceId':'foreign'}, {'name':'foreign'}, {'extra':True}])
def test_mismatched_document_refuses(change):
    with pytest.raises(ProtocolError): run(False,handler_for({**document(),**change}),'read',name=NAME)


@pytest.mark.parametrize('change',[{'revision':True},{'revision':0},{'serviceId':''},{'sessionId':None},{'extra':True}])
def test_unknown_knowledge_is_not_current(change):
    with pytest.raises(ProtocolError): run(False,handler_for({**knowledge(),**change}),'knowledge')


@pytest.mark.parametrize('change',[{'revision':True},{'revision':2},{'sourceSha256':'wrong'},
    {'status':'ADMITTED_NATIVE'},{'diagnostics':None},{'diagnostics':[1]},
    {'diagnostics':[{'level':'Error','message':'bad'}]},{'status':'INVALID'},{'knowledge':{}},{'extra':True}])
def test_mismatched_or_contradictory_validation_is_not_success(change):
    with pytest.raises(ProtocolError): run(False,handler_for(document({**validation(),**change})),'read',name=NAME)


@pytest.mark.parametrize('change',[{'documentRevision':True},{'documentRevision':2},{'sourceSha256':'wrong'},
    {'publicationId':'bad'},{'source':None},{'knowledge':{}}])
def test_publication_confirmation_refuses_forged_or_mismatched_identity(change):
    with pytest.raises(ProtocolError): run(False,handler_for({**publication(),**change}),'publish',name=NAME,expected_revision=1,expected_knowledge_revision=1)


@pytest.mark.parametrize('native_async',[False,True])
def test_update_must_confirm_exact_source_and_validation_invalidation(native_async):
    with pytest.raises(ProtocolError): run(native_async,handler_for(document(validation())),'update',name=NAME,source=SOURCE,expected_revision=0)
    other='other source'
    value={**document(),'source':other,'sourceSha256':hashlib.sha256(other.encode()).hexdigest()}
    with pytest.raises(ProtocolError): run(native_async,handler_for(value),'update',name=NAME,source=SOURCE,expected_revision=0)


@pytest.mark.parametrize('native_async',[False,True])
def test_validate_and_publish_confirm_expected_knowledge_not_only_source(native_async):
    with pytest.raises(ProtocolError): run(native_async,handler_for(document()),'validate',name=NAME,expected_revision=1,expected_knowledge_revision=1)
    value=document({**validation(),'knowledge':{**knowledge(),'revision':2}})
    with pytest.raises(ProtocolError): run(native_async,handler_for(value),'validate',name=NAME,expected_revision=1,expected_knowledge_revision=1)
    with pytest.raises(ProtocolError): run(native_async,handler_for({**publication(),'knowledge':{**knowledge(),'revision':2}}),'publish',name=NAME,expected_revision=1,expected_knowledge_revision=1)


def test_reflected_credentials_never_return_as_source():
    value=document();value['source']='resource-secret';value['sourceSha256']=hashlib.sha256(value['source'].encode()).hexdigest()
    with pytest.raises(ProtocolError) as error: run(False,handler_for(value),'read',name=NAME)
    assert 'resource-secret' not in str(error.value)


@pytest.mark.parametrize('native_async',[False,True])
@pytest.mark.parametrize('mutation',[False,True])
def test_resources_rebinding_during_response_discards_read_or_marks_mutation_unknown(native_async,mutation):
    api=None
    def handler(request):
        if request.url.path.endswith('/capabilities'):
            return httpx.Response(200,json={'serviceId':'resources-id','acceptedDocumentProtocol':PROTOCOL})
        api.transport.endpoints['resources']=Endpoint('https://foreign.test')
        return httpx.Response(200,json=document())
    error=SubmissionOutcomeUnknown if mutation else ProtocolError
    async def awaited():
        nonlocal api
        async with client(handler,True,resources=Endpoint('https://resources.test')) as current:
            api=current;docs=AsyncAcceptedDocuments(root(api),allow_experimental=True)
            with pytest.raises(error):
                if mutation: await docs.update(NAME,SOURCE,expected_revision=0)
                else: await docs.read(NAME)
    if native_async: asyncio.run(awaited())
    else:
        with client(handler,resources=Endpoint('https://resources.test')) as current:
            api=current;docs=AcceptedDocuments(root(api),allow_experimental=True)
            with pytest.raises(error):
                if mutation: docs.update(NAME,SOURCE,expected_revision=0)
                else: docs.read(NAME)


def test_capability_rebinding_refuses_before_sending_source_or_implicitly_rebinding():
    api=None;changed=False;calls=[]
    def handler(request):
        calls.append(request)
        if request.url.path.endswith('/capabilities'):
            if changed: return httpx.Response(200,json={'serviceId':'other-resources','acceptedDocumentProtocol':PROTOCOL})
            return httpx.Response(200,json={'serviceId':'resources-id','acceptedDocumentProtocol':PROTOCOL})
        return httpx.Response(200,json=knowledge())
    with client(handler,resources=Endpoint('https://resources.test')) as current:
        api=current;docs=AcceptedDocuments(root(api),allow_experimental=True)
        docs.knowledge();changed=True
        with pytest.raises(ProtocolError): docs.update(NAME,SOURCE,expected_revision=0)
    assert all(request.method=='GET' for request in calls)


@pytest.mark.parametrize('native_async',[False,True])
def test_credential_in_public_route_is_refused_before_any_http(native_async):
    async def awaited():
        async with client(offline,True,resources=Endpoint('https://resources.test',NAME)) as api:
            with pytest.raises(ProtocolError): await AsyncAcceptedDocuments(root(api),allow_experimental=True).read(NAME)
    if native_async: asyncio.run(awaited())
    else:
        with client(offline,resources=Endpoint('https://resources.test',NAME)) as api:
            with pytest.raises(ProtocolError): AcceptedDocuments(root(api),allow_experimental=True).read(NAME)
