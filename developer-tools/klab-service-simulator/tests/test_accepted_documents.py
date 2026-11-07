"""Actual public SDK conditional source workflow, not native accepted publication."""
import asyncio
from concurrent.futures import ThreadPoolExecutor

import httpx
import pytest

from klab_client import AsyncClient,AsyncContext,Client,Context
from klab_client.accepted_documents import AcceptedDocuments,AsyncAcceptedDocuments
from klab_client.errors import AuthorizationError,MissingAssetError,ServerError,SubmissionOutcomeUnknown
from conftest import launch
from test_vertical import client_args,control

NAME='synthetic.document0'
SOURCE='namespace synthetic.document0;\n'


def advance(ready):
    base=ready['endpoints']['runtime'].rsplit('/',1)[0]
    response=httpx.post(base+'/__sim__/advanceValidation',headers={'Authorization':'sim-admin-control'},trust_env=False,timeout=3)
    response.raise_for_status()


@pytest.mark.parametrize('native_async',[False,True])
def test_public_conditional_source_conflicts_validation_publication_and_owner_cleanup(native_async):
    with launch('accepted-documents') as ready:
        async def run():
            async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready)) as owner:
                session=await owner.create_session();context=await session.create_context();other=await session.create_context()
                try:
                    docs=AsyncAcceptedDocuments(context,allow_experimental=True)
                    knowledge=await docs.knowledge();assert knowledge.revision==1
                    first=await docs.update(NAME,SOURCE,expected_revision=0)
                    assert first.revision==1 and first.validation is None and first.source==SOURCE
                    assert await docs.read(NAME)==first
                    with pytest.raises(ServerError): await docs.publish(NAME,expected_revision=1,expected_knowledge_revision=1)
                    valid=await docs.validate(NAME,expected_revision=1,expected_knowledge_revision=1)
                    assert valid.validation['status']=='VALID'
                    published=await docs.publish(NAME,expected_revision=1,expected_knowledge_revision=1)
                    assert await docs.published(NAME)==published
                    assert published.source==SOURCE and published.document_revision==1 and 'not native' in published.evidence
                    changed=await docs.update(NAME,'invalid source',expected_revision=1)
                    assert changed.validation is None and changed.revision==2
                    invalid=await docs.validate(NAME,expected_revision=2,expected_knowledge_revision=1)
                    assert invalid.validation['status']=='INVALID' and invalid.validation['diagnostics'][0]['level']=='Error'
                    with pytest.raises(ServerError): await docs.publish(NAME,expected_revision=2,expected_knowledge_revision=1)
                    assert (await docs.published(NAME)).document_revision==1
                    with pytest.raises(ServerError): await docs.update(NAME,SOURCE,expected_revision=1)
                    latest=await docs.update(NAME,SOURCE,expected_revision=2)
                    assert latest.revision==3 and (await docs.read(NAME,revision=1)).source==SOURCE
                    await docs.validate(NAME,expected_revision=3,expected_knowledge_revision=1)
                    advance(ready)
                    for expected in (1,2):
                        with pytest.raises(ServerError): await docs.publish(NAME,expected_revision=3,expected_knowledge_revision=expected)
                    current=await docs.knowledge();assert current.revision==2
                    await docs.validate(NAME,expected_revision=3,expected_knowledge_revision=current.revision)
                    updated=await docs.publish(NAME,expected_revision=3,expected_knowledge_revision=current.revision)
                    assert updated.document_revision==3 and updated.publication_id!=published.publication_id
                    with pytest.raises(MissingAssetError): await AsyncAcceptedDocuments(other,allow_experimental=True).read(NAME)
                    async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        denied=AsyncAcceptedDocuments(AsyncContext(bob,context.configuration),allow_experimental=True)
                        with pytest.raises(AuthorizationError): await denied.read(NAME)
                        with pytest.raises(AuthorizationError): await denied.update(NAME,SOURCE,expected_revision=3)
                    assert (await docs.published(NAME)).document_revision==3
                finally:
                    await other.release();await context.release();await session.release()
        def sync():
            with Client(ready['endpoints']['runtime'],**client_args(ready)) as owner:
                session=owner.create_session();context=session.create_context();other=session.create_context()
                try:
                    docs=AcceptedDocuments(context,allow_experimental=True)
                    knowledge=docs.knowledge();assert knowledge.revision==1
                    first=docs.update(NAME,SOURCE,expected_revision=0)
                    assert first.revision==1 and first.validation is None and first.source==SOURCE
                    assert docs.read(NAME)==first
                    with pytest.raises(ServerError): docs.publish(NAME,expected_revision=1,expected_knowledge_revision=1)
                    valid=docs.validate(NAME,expected_revision=1,expected_knowledge_revision=1)
                    assert valid.validation['status']=='VALID'
                    published=docs.publish(NAME,expected_revision=1,expected_knowledge_revision=1)
                    assert docs.published(NAME)==published
                    assert published.source==SOURCE and published.document_revision==1 and 'not native' in published.evidence
                    changed=docs.update(NAME,'invalid source',expected_revision=1)
                    assert changed.validation is None and changed.revision==2
                    invalid=docs.validate(NAME,expected_revision=2,expected_knowledge_revision=1)
                    assert invalid.validation['status']=='INVALID' and invalid.validation['diagnostics'][0]['level']=='Error'
                    with pytest.raises(ServerError): docs.publish(NAME,expected_revision=2,expected_knowledge_revision=1)
                    assert docs.published(NAME).document_revision==1
                    with pytest.raises(ServerError): docs.update(NAME,SOURCE,expected_revision=1)
                    latest=docs.update(NAME,SOURCE,expected_revision=2)
                    assert latest.revision==3 and docs.read(NAME,revision=1).source==SOURCE
                    docs.validate(NAME,expected_revision=3,expected_knowledge_revision=1)
                    advance(ready)
                    for expected in (1,2):
                        with pytest.raises(ServerError): docs.publish(NAME,expected_revision=3,expected_knowledge_revision=expected)
                    current=docs.knowledge();assert current.revision==2
                    docs.validate(NAME,expected_revision=3,expected_knowledge_revision=current.revision)
                    updated=docs.publish(NAME,expected_revision=3,expected_knowledge_revision=current.revision)
                    assert updated.document_revision==3 and updated.publication_id!=published.publication_id
                    with pytest.raises(MissingAssetError): AcceptedDocuments(other,allow_experimental=True).read(NAME)
                    with Client(ready['endpoints']['runtime'],**client_args(ready,'sim-token-bob')) as bob:
                        denied=AcceptedDocuments(Context(bob,context.configuration),allow_experimental=True)
                        with pytest.raises(AuthorizationError): denied.read(NAME)
                        with pytest.raises(AuthorizationError): denied.update(NAME,SOURCE,expected_revision=3)
                    assert docs.published(NAME).document_revision==3
                finally:
                    other.release();context.release();session.release()
        asyncio.run(run()) if native_async else sync()
        assert not any(control(ready)['counts'].values())


@pytest.mark.parametrize('native_async',[False,True])
def test_lost_publication_reply_reconciles_by_readback_not_automatic_replay(native_async):
    with launch('accepted-documents') as ready:
        class DropReply(httpx.BaseTransport):
            def __init__(self): self.delegate=httpx.HTTPTransport(trust_env=False);self.publications=0
            def handle_request(self,request):
                response=self.delegate.handle_request(request)
                if request.method=='POST' and request.url.path.endswith('/publish'):
                    self.publications+=1;response.read();response.close()
                    raise httpx.ReadError('Owned reference publication reply loss',request=request)
                return response
            def close(self): self.delegate.close()
        class AsyncDropReply(httpx.AsyncBaseTransport):
            def __init__(self): self.delegate=httpx.AsyncHTTPTransport(trust_env=False);self.publications=0
            async def handle_async_request(self,request):
                response=await self.delegate.handle_async_request(request)
                if request.method=='POST' and request.url.path.endswith('/publish'):
                    self.publications+=1;await response.aread();await response.aclose()
                    raise httpx.ReadError('Owned reference publication reply loss',request=request)
                return response
            async def aclose(self): await self.delegate.aclose()
        async def run():
            transport=AsyncDropReply()
            async with AsyncClient(ready['endpoints']['runtime'],http_transport=transport,**client_args(ready)) as owner:
                session=await owner.create_session();context=await session.create_context()
                try:
                    docs=AsyncAcceptedDocuments(context,allow_experimental=True)
                    await docs.update(NAME,SOURCE,expected_revision=0)
                    await docs.validate(NAME,expected_revision=1,expected_knowledge_revision=1)
                    with pytest.raises(SubmissionOutcomeUnknown): await docs.publish(NAME,expected_revision=1,expected_knowledge_revision=1)
                    assert (await docs.published(NAME)).document_revision==1 and transport.publications==1
                finally:
                    await context.release();await session.release()
        def sync():
            transport=DropReply()
            with Client(ready['endpoints']['runtime'],http_transport=transport,**client_args(ready)) as owner:
                session=owner.create_session();context=session.create_context()
                try:
                    docs=AcceptedDocuments(context,allow_experimental=True)
                    docs.update(NAME,SOURCE,expected_revision=0)
                    docs.validate(NAME,expected_revision=1,expected_knowledge_revision=1)
                    with pytest.raises(SubmissionOutcomeUnknown): docs.publish(NAME,expected_revision=1,expected_knowledge_revision=1)
                    assert docs.published(NAME).document_revision==1 and transport.publications==1
                finally:
                    context.release();session.release()
        asyncio.run(run()) if native_async else sync()
        assert not any(control(ready)['counts'].values())


@pytest.mark.parametrize('native_async',[False,True])
def test_two_independent_owner_editors_race_one_source_revision_without_silent_overwrite(native_async):
    with launch('accepted-documents') as ready:
        with Client(ready['endpoints']['runtime'],**client_args(ready)) as owner:
            session=owner.create_session();context=session.create_context()
            try:
                docs=AcceptedDocuments(context,allow_experimental=True)
                docs.update(NAME,SOURCE,expected_revision=0)
                sources=(SOURCE+'// editor A\n',SOURCE+'// editor B\n')
                async def async_edits():
                    async def edit(source):
                        async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready)) as editor:
                            selected=AsyncContext(editor,context.configuration)
                            return await AsyncAcceptedDocuments(selected,allow_experimental=True).update(NAME,source,expected_revision=1)
                    return await asyncio.gather(*(edit(source) for source in sources),return_exceptions=True)
                def edit(source):
                    with Client(ready['endpoints']['runtime'],**client_args(ready)) as editor:
                        try: return AcceptedDocuments(Context(editor,context.configuration),allow_experimental=True).update(NAME,source,expected_revision=1)
                        except ServerError as error: return error
                if native_async: results=asyncio.run(async_edits())
                else:
                    with ThreadPoolExecutor(max_workers=2) as pool: results=list(pool.map(edit,sources))
                failures=[result for result in results if isinstance(result,ServerError)]
                successes=[result for result in results if not isinstance(result,BaseException)]
                assert len(failures)==len(successes)==1 and failures[0].status_code==409
                assert successes[0].revision==2 and successes[0].source in sources
                current=docs.read(NAME)
                assert current==successes[0] and current.validation is None
            finally:
                context.release();session.release()
        assert not any(control(ready)['counts'].values())
