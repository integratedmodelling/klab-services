"""Prepared approved read-only native commit cache fixture; NOT_RUN by default."""
import asyncio
import json

import pytest

from klab_client import AsyncClient, AsyncContext, Client, Context
from klab_client.commits import AsyncCommitInspector, CommitInspector

pytestmark=[pytest.mark.live,pytest.mark.functional]


@pytest.mark.parametrize('native_async',[False,True])
def test_approved_known_native_commit_report(functional_state,native_async):
    fixture=json.loads((functional_state/'commit-inspection.json').read_text(encoding='utf-8'))
    assert fixture['allow_read_only_commit_inspection'] is True and fixture['native_build']
    def check(report):
        assert report.runtime_id==fixture['runtime_service_id'] and report.commit_id==fixture['commit_id']
        for name,expected in fixture['expected_assets'].items():
            assert report.assets[name]==(None if expected is None else tuple(expected))
        for name,expected in fixture['expected_links'].items():
            actual=report.links[name]
            assert (None if actual is None else [(r.source_id,r.target_id,r.relationship) for r in actual])==(
                None if expected is None else [tuple(row) for row in expected])
    configuration={'id':fixture['context_id'],'serviceId':fixture['runtime_service_id']}
    async def run():
        async with AsyncClient.from_env() as api:
            check(await AsyncCommitInspector(AsyncContext(api,configuration)).inspect(fixture['commit_id']))
    if native_async:asyncio.run(run())
    else:
        with Client.from_env() as api:
            check(CommitInspector(Context(api,configuration)).inspect(fixture['commit_id']))
