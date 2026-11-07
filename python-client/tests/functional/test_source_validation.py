"""Approved namespace-only native diagnostics. Prepared/NOT_RUN here, no repair.

Fixture must authorize the existing native source-validation request explicitly.
No ontology, executable-mode, publication or Runtime computation is attempted.
"""
import asyncio
import json

import pytest

from klab_client import AsyncClient, AsyncSourceValidation, Client, SourceValidation

pytestmark=[pytest.mark.live,pytest.mark.functional]


@pytest.mark.parametrize('native_async',[False,True])
def test_native_prepared_namespace_source_version_and_revision(functional_state,native_async):
    fixture=json.loads((functional_state/'source-validation.json').read_text(encoding='utf-8'))
    assert fixture['allow_namespace_source_diagnostics'] is True
    assert fixture['native_build'] and fixture['namespace_only_no_ontology_repair'] is True
    source=fixture['source']; version=fixture['document_version']; revision=fixture['knowledge_revision']
    assert isinstance(source,str) and source.strip() and type(revision) is int and revision>=0
    def check(report):
        assert report.knowledge.revision==revision and report.document_version==version
        assert report.document_urn==fixture['namespace_urn'] and report.status==fixture['expected_status']
        assert len(report.raw['notifications'])==fixture['expected_notification_count']
    async def run():
        async with AsyncClient.from_env() as api:
            validator=AsyncSourceValidation(api)
            parsed=await validator.parse(source,kind='NAMESPACE')
            check(await validator.validate(parsed,document_version=version,expected_revision=revision))
    if native_async: asyncio.run(run())
    else:
        with Client.from_env() as api:
            validator=SourceValidation(api)
            parsed=validator.parse(source,kind='NAMESPACE')
            check(validator.validate(parsed,document_version=version,expected_revision=revision))
