"""Harness controls only; mocked values do not establish Java scientific acceptance."""
import asyncio
from types import SimpleNamespace

import httpx
import pytest

from workflows.shared_context_readers import overlapping_reads, read_audit, verify_observable, verify_values


def fake_observation(*, fail=False, never_request=False):
    hooks = {"request": []}
    async def fetch_data(offsets, **kwargs):
        if fail:
            raise ValueError("controlled reader failure")
        if never_request:
            await asyncio.Event().wait()
        for hook in list(hooks["request"]):
            await hook(httpx.Request("POST", "https://runtime.invalid/runtime/api/v1/observation/value"))
        return SimpleNamespace(offsets=tuple(offsets), values=(234.5,) * len(offsets), units="m")
    return SimpleNamespace(fetch_data=fetch_data,
        _context=SimpleNamespace(client=SimpleNamespace(transport=SimpleNamespace(_http=SimpleNamespace(event_hooks=hooks)))))


def test_barrier_overlaps_both_native_read_operations_and_removes_hooks():
    a, b = fake_observation(), fake_observation()
    values = asyncio.run(overlapping_reads(a, b, timeout=.5))
    verify_values(values[0], range(20))
    verify_values(values[1], [19, 0, 19, 1])
    assert not a._context.client.transport._http.event_hooks["request"]
    assert not b._context.client.transport._http.event_hooks["request"]


@pytest.mark.parametrize("failure", ["reader", "timeout"])
def test_failed_read_cancels_joins_peer_and_removes_hooks(failure):
    a = fake_observation()
    b = fake_observation(fail=failure == "reader", never_request=failure == "timeout")
    async def run():
        with pytest.raises(ValueError if failure == "reader" else TimeoutError):
            await overlapping_reads(a, b, timeout=.01)
        assert not [task for task in asyncio.all_tasks() if task is not asyncio.current_task() and not task.done()]
    asyncio.run(run())
    assert not a._context.client.transport._http.event_hooks["request"]
    assert not b._context.client.transport._http.event_hooks["request"]


@pytest.mark.parametrize("path", ["/createContext", "/submitObservation", "/releaseContext", "/jobs/cancel/1"])
def test_collaborator_audit_refuses_work_creation_and_disposal(path):
    calls = []
    with pytest.raises(AssertionError):
        asyncio.run(read_audit(calls)(httpx.Request("POST", "https://runtime.invalid" + path)))
    assert not calls


def test_audit_records_native_read_and_guards_scientific_assertions():
    calls = []
    asyncio.run(read_audit(calls)(httpx.Request("POST", "https://runtime.invalid/runtime/api/v1/observation/value")))
    assert calls == [{"method": "POST", "path": "/runtime/api/v1/observation/value"}]
    for changes in ({"offsets": (0, 1)}, {"values": (0, 0)}, {"units": "km"}):
        value = {"offsets": (1, 0), "values": (234.5, 234.5), "units": "m"} | changes
        with pytest.raises(AssertionError):
            verify_values(SimpleNamespace(**value), [1, 0])


def test_observable_identity_uses_actual_dto_raw_urn_not_nonexistent_attribute():
    observation = SimpleNamespace(observable=SimpleNamespace(raw={"urn": "served-native-urn"}))
    a = SimpleNamespace(observable="served-native-urn")
    verify_observable(a, a, observation)
    with pytest.raises(AssertionError):
        verify_observable(a, SimpleNamespace(observable="different"), observation)
