"""Prepared native ACL read compatibility only. NOT_RUN here; no grant/mutation calls.

This does not assume granular native READ/WRITE roles or our proposed grant API.
Native negative mutations require separately authorized disposable fixtures.
"""
import asyncio
import json
import os

import pytest

from klab_client import AsyncClient, Client, Handle

pytestmark = [pytest.mark.live, pytest.mark.functional]


@pytest.mark.parametrize("native_async", [False, True])
def test_prepared_native_shared_read_uses_reader_credentials_without_owner_disposal(functional_state, native_async):
    fixture = json.loads((functional_state / "shared-context-read.json").read_text(encoding="utf-8"))
    reference = Handle.loads(json.dumps(fixture["observation"]))
    credential = os.environ[fixture["reader_credential_env"]]
    if not credential: raise ValueError("Prepared reader credential environment variable is empty")
    assert fixture["native_build"] and fixture["authorized_reader_principal"]
    offsets = tuple(fixture["offsets"]); expected = tuple(fixture["expected_texts"])
    assert 1 <= len(offsets) <= 256 and len(expected) == len(offsets)
    async def run():
        async with AsyncClient.from_env() as owner:
            original = await owner.restore_handle(reference)
            async with AsyncClient(reference.runtime_url, credential, agent_name=fixture["authorized_reader_principal"],
                    runtime_service_id=reference.runtime_id) as reader:
                shared = await reader.restore_handle(reference)
                assert not shared._context.owned
                data = await shared.fetch_data(offsets)
                assert data.text == expected and data.units == fixture["unit"]
            assert (await original.fetch_data(offsets)).text == expected
    if native_async: asyncio.run(run())
    else:
        with Client.from_env() as owner:
            original = owner.restore_handle(reference)
            with Client(reference.runtime_url, credential, agent_name=fixture["authorized_reader_principal"],
                    runtime_service_id=reference.runtime_id) as reader:
                shared = reader.restore_handle(reference)
                assert not shared._context.owned
                data = shared.fetch_data(offsets)
                assert data.text == expected and data.units == fixture["unit"]
            assert original.fetch_data(offsets).text == expected
