"""Prepared read-only native grouped membership. NOT_RUN, not complete graph science."""
import asyncio
import json

import pytest

from klab_client import AsyncClient, Client, AsyncDependencyInspector, DependencyInspector
from klab_client.evidence import _plain

pytestmark=[pytest.mark.live,pytest.mark.functional]


@pytest.mark.parametrize('native_async',[False,True])
def test_native_prepared_dependency_membership(functional_state,native_async):
    fixture=json.loads((functional_state/'dependency-inspection.json').read_text(encoding='utf-8'))
    assert fixture['allow_read_only_dependency_inspection'] is True and fixture['native_build']
    def check(snapshot):
        assert snapshot.resources_id==fixture['resources_service_id']
        assert [(m.service_id,m.knowledge_class,m.urn,_plain(m.version)) for m in snapshot.results]==[
            tuple(row) for row in fixture['expected_results']]
        for group,expected in fixture['expected_groups'].items():
            members=snapshot.groups[group]
            assert (None if members is None else [(m.service_id,m.knowledge_class,m.urn,_plain(m.version)) for m in members])==(
                None if expected is None else [tuple(row) for row in expected])
        assert 'UNKNOWN' in snapshot.closure_completeness and 'UNAVAILABLE' in snapshot.edge_completeness
    async def run():
        async with AsyncClient.from_env() as api:
            check(await AsyncDependencyInspector(api,fixture.get('scope')).inspect(fixture['urn'],knowledge_class=fixture['knowledge_class']))
    if native_async: asyncio.run(run())
    else:
        with Client.from_env() as api:
            check(DependencyInspector(api,fixture.get('scope')).inspect(fixture['urn'],knowledge_class=fixture['knowledge_class']))
