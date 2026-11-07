"""Opt-in native persisted endpoint/read-only collection compatibility. NOT_RUN here.

Prepared fixture must supply authorized v1 context/source/target handles and an
exact expected direct-child ID list. No synthetic observable or native computation
is invented, and this check never creates/release scopes or submits Jobs.
"""
import asyncio
import json

import pytest

from klab_client import Client, AsyncClient, Handle, to_handle

pytestmark = [pytest.mark.live, pytest.mark.functional]


@pytest.mark.parametrize("native_async", [False, True])
def test_native_persisted_selection_and_collection_without_mutation(functional_state, native_async):
    fixture = json.loads((functional_state / "relationship-selection.json").read_text(encoding="utf-8"))
    context_ref, source_ref, target_ref = (Handle.loads(json.dumps(fixture[key])) for key in ("context", "source", "target"))
    expected = fixture["expected_direct_child_ids"]
    assert len(expected) <= 128 and len(set(expected)) == len(expected)
    async def run():
        async with AsyncClient.from_env() as api:
            context = await api.restore_handle(context_ref)
            source = await api.restore_handle(source_ref); target = await api.restore_handle(target_ref)
            selected = context.between(source, target)
            restored = await api.restore_handle(to_handle(selected))
            page = await restored.observations(limit=128)
            assert [item.id for item in page.items] == expected and page.next_offset is None
            assert not restored.owned and restored.relationship_selection.source_id == source.id
    if native_async: asyncio.run(run())
    else:
        with Client.from_env() as api:
            context = api.restore_handle(context_ref)
            source = api.restore_handle(source_ref); target = api.restore_handle(target_ref)
            selected = context.between(source, target)
            restored = api.restore_handle(to_handle(selected))
            page = restored.observations(limit=128)
            assert [item.id for item in page.items] == expected and page.next_offset is None
            assert not restored.owned and restored.relationship_selection.target_id == target.id
