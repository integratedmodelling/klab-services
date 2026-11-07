"""Public parse/validate/current revision diagnostics without creating remote assets."""
import asyncio

import httpx
import pytest

from klab_client import AsyncClient, Client
from klab_client.semantics import StaleKnowledgeError
from klab_client.source_previews import AsyncSourcePreviews, SourcePreviews
from klab_client.source_validation import AsyncSourceValidation, SourceValidation
from conftest import launch
from test_vertical import client_args, control

# Deliberately independent literals, not copied from imported simulator policy.
SOURCE = 'namespace synthetic.validation;\n'
BAD = 'namespace synthetic.validation;\nmodel synthetic:Unknown;\n'


def advance(ready):
    base=ready['endpoints']['runtime'].rsplit('/',1)[0]
    response=httpx.post(base+'/__sim__/advanceValidation',headers={'Authorization':'sim-admin-control'},trust_env=False,timeout=3)
    response.raise_for_status()


@pytest.mark.parametrize('native_async',[False,True])
def test_public_parse_semantic_diagnostics_and_explicit_revision_revalidation(native_async):
    with launch('source-validation') as ready:
        async def run():
            async with AsyncClient(ready['endpoints']['runtime'],**client_args(ready)) as api:
                previews=AsyncSourcePreviews(api); validator=AsyncSourceValidation(api)
                parsed=await previews.parse(SOURCE)
                good=await validator.validate(parsed,document_version='edit-1',expected_revision=1)
                assert good.status=='COMPLETE' and good.raw['notifications']==()
                invalid=await previews.parse(BAD)
                diagnostic=await validator.validate(invalid,document_version='edit-2',expected_revision=1)
                assert diagnostic.raw['notifications'][0]['level']=='Error'
                location=diagnostic.raw['notifications'][0]['lexicalContext']
                assert BAD[location['offsetInDocument']:location['offsetInDocument']+location['length']]=='synthetic:Unknown'
                assert diagnostic.parser_source_sha256 != good.parser_source_sha256
                assert parsed.source_sha256==good.source_sha256
                advance(ready)
                with pytest.raises(StaleKnowledgeError): await validator.validate(parsed,document_version='edit-1',expected_revision=1)
                revised=await validator.validate(parsed,document_version='edit-1',expected_revision=2)
                assert revised.knowledge.revision==2 and revised.source_sha256==good.source_sha256
                assert revised.knowledge.content_fingerprint!=good.knowledge.content_fingerprint
        def sync():
            with Client(ready['endpoints']['runtime'],**client_args(ready)) as api:
                previews=SourcePreviews(api); validator=SourceValidation(api)
                parsed=previews.parse(SOURCE)
                good=validator.validate(parsed,document_version='edit-1',expected_revision=1)
                assert good.status=='COMPLETE' and good.raw['notifications']==()
                invalid=previews.parse(BAD)
                diagnostic=validator.validate(invalid,document_version='edit-2',expected_revision=1)
                assert diagnostic.raw['notifications'][0]['level']=='Error'
                location=diagnostic.raw['notifications'][0]['lexicalContext']
                assert BAD[location['offsetInDocument']:location['offsetInDocument']+location['length']]=='synthetic:Unknown'
                assert diagnostic.parser_source_sha256!=good.parser_source_sha256
                advance(ready)
                with pytest.raises(StaleKnowledgeError): validator.validate(parsed,document_version='edit-1',expected_revision=1)
                revised=validator.validate(parsed,document_version='edit-1',expected_revision=2)
                assert revised.knowledge.revision==2 and revised.source_sha256==good.source_sha256
                assert revised.knowledge.content_fingerprint!=good.knowledge.content_fingerprint
        asyncio.run(run()) if native_async else sync()
        assert not any(control(ready)['counts'].values())
