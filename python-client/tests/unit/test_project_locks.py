"""RC-P4.4/CAP14: explicit native project lock operations, never editor leases."""
import klab_client
import asyncio
from contextlib import asynccontextmanager
from dataclasses import FrozenInstanceError
import json
from types import SimpleNamespace

import httpx
import pytest

from klab_client import AsyncClient, AsyncProjectLock, Client, Endpoint, ProjectLock
from klab_client.errors import AuthorizationError, InvalidRequestError, ProtocolError, SubmissionOutcomeUnknown


def scenario(mode, *, results=None, identities=None, status=200, drop=False, mutate=None):
    calls, responses = [], iter([True, True] if results is None else results)
    identities = iter(["resources-id"] * 16 if identities is None else identities)
    def handler(request):
        calls.append(request)
        assert request.method == "GET" and request.headers["authorization"] == "resources-secret"
        if request.url.path == "/resources/public/capabilities":
            return httpx.Response(200, content=json.dumps({"serviceId": next(identities)}).encode(),
                                  headers={"content-type": "application/json"})
        assert request.url.path in ("/resources/api/v1/project/lock/owned", "/resources/api/v1/project/unlock/owned")
        if mutate:
            mutate(client)
        if drop:
            raise httpx.ReadError("deliberate test interruption")
        return httpx.Response(status, json=next(responses))
    client_type = AsyncClient if mode == "async" else Client
    client = client_type("https://runtime.invalid", resources=Endpoint("https://resources.invalid/resources", "resources-secret"),
                         http_transport=httpx.MockTransport(handler))
    return client, calls


@asynccontextmanager
async def managed(client, mode):
    if mode == "async":
        async with client:
            yield
    else:
        with client:
            yield


def factory(mode, client, project="owned", **kwargs):
    return (AsyncProjectLock if mode == "async" else ProjectLock)(client, project, **kwargs)


async def invoke(lock, mode, action="acquire", **kwargs):
    result = getattr(lock, action)(**kwargs)
    return await result if mode == "async" else result


def test_native_project_lock_facades_are_public():
    assert hasattr(klab_client, "ProjectLock")
    assert hasattr(klab_client, "AsyncProjectLock")


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_explicit_native_acquire_release_receipts_are_immutable_and_never_leases(mode):
    async def run():
        client, calls = scenario(mode)
        async with managed(client, mode):
            lock = factory(mode, client)
            assert lock.project == "owned" and lock.state == "NOT_REQUESTED" and lock.receipt is None
            receipt = await invoke(lock, mode, allow_write=True)
            assert receipt.confirmed and receipt.action == "ACQUIRE" and lock.state == "ACQUIRE_CONFIRMED"
            assert "principal-shared" in receipt.limit and "not an editor lease" in receipt.limit
            assert receipt.service_id == "resources-id" and receipt.service_url == "https://resources.invalid/resources"
            with pytest.raises(FrozenInstanceError):
                receipt.confirmed = False
            count = len(calls)
            with pytest.raises(InvalidRequestError):
                await invoke(lock, mode, allow_write=True)
            assert len(calls) == count
            released = await invoke(lock, mode, "release", allow_write=True)
            assert released.confirmed and released.action == "RELEASE" and lock.state == "RELEASE_CONFIRMED"
            assert lock.receipt is released
            with pytest.raises(InvalidRequestError):
                await invoke(lock, mode, "release", allow_write=True)
        assert len(calls) == 4  # two capability reads + exact native lock/unlock; client.close does no release
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_native_denial_can_be_explicitly_retried_but_unconfirmed_release_cannot(mode):
    async def run():
        client, calls = scenario(mode, results=[False, True, False])
        async with managed(client, mode):
            lock = factory(mode, client)
            assert not (await invoke(lock, mode, allow_write=True)).confirmed
            assert lock.state == "ACQUIRE_DENIED"
            assert (await invoke(lock, mode, allow_write=True)).confirmed
            assert not (await invoke(lock, mode, "release", allow_write=True)).confirmed
            assert lock.state == "RELEASE_UNCONFIRMED"
            with pytest.raises(InvalidRequestError):
                await invoke(lock, mode, "release", allow_write=True)
        assert len(calls) == 6
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("consent", [False, None, 1, "true"])
def test_exact_consent_before_any_http(mode, consent):
    async def run():
        client, calls = scenario(mode)
        async with managed(client, mode):
            lock = factory(mode, client)
            with pytest.raises(InvalidRequestError):
                await invoke(lock, mode, allow_write=consent)
            with pytest.raises(InvalidRequestError):
                await invoke(lock, mode, "release", allow_write=True)
        assert not calls
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("project,options", [(None, {}), ("", {}), ("foreign/project", {}), ("a..b", {}),
    ("https://other.invalid", {}), ("resources-secret", {}), ("owned", {"max_response_bytes": 0}),
    ("owned", {"max_response_bytes": True}), ("owned", {"max_response_bytes": 16777217})])
def test_invalid_or_credential_intake_never_reaches_http(mode, project, options):
    async def run():
        client, calls = scenario(mode)
        async with managed(client, mode):
            with pytest.raises(InvalidRequestError):
                factory(mode, client, project, **options)
        assert not calls
    asyncio.run(run())


def test_matching_client_facades_required():
    with pytest.raises(InvalidRequestError):
        ProjectLock(None, "owned")
    with pytest.raises(InvalidRequestError):
        AsyncProjectLock(None, "owned")
    client, _ = scenario("sync")
    with client:
        with pytest.raises(InvalidRequestError):
            AsyncProjectLock(client, "owned")
    async def run():
        client, _ = scenario("async")
        async with client:
            with pytest.raises(InvalidRequestError):
                ProjectLock(client, "owned")
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("options,error", [({"results": ["file:///legacy"]}, SubmissionOutcomeUnknown),
    ({"results": [1]}, SubmissionOutcomeUnknown), ({"results": [None]}, SubmissionOutcomeUnknown),
    ({"drop": True}, SubmissionOutcomeUnknown), ({"status": 500}, SubmissionOutcomeUnknown),
    ({"status": 403}, AuthorizationError)])
def test_ambiguous_or_invalid_native_reply_is_never_replayed_or_falsely_confirmed(mode, options, error):
    async def run():
        client, calls = scenario(mode, **options)
        async with managed(client, mode):
            lock = factory(mode, client)
            with pytest.raises(error):
                await invoke(lock, mode, allow_write=True)
            assert lock.state == "OUTCOME_UNKNOWN" and lock.receipt is None
            count = len(calls)
            with pytest.raises(InvalidRequestError):
                await invoke(lock, mode, allow_write=True)
            assert len(calls) == count
        assert len(calls) == 2
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("identity", ["x" * 4097, "bad\nidentity", "bad\ud800identity", "resources-secret"])
def test_bad_service_identity_cannot_admit_lock_request(mode, identity):
    async def run():
        client, calls = scenario(mode, identities=[identity])
        async with managed(client, mode):
            lock = factory(mode, client)
            with pytest.raises((InvalidRequestError, ProtocolError)):
                await invoke(lock, mode, allow_write=True)
            assert lock.state == "NOT_REQUESTED"
        assert len(calls) == 1
    asyncio.run(run())


def test_invalid_capability_object_is_rejected_by_shared_binding_validation():
    client, calls = scenario("sync")
    with client:
        lock = ProjectLock(client, "owned")
        for identity in (None, " ", 7):
            with pytest.raises(ProtocolError):
                lock._bind(SimpleNamespace(service_id=identity))
    assert not calls


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_unconfirmed_release_retains_only_historical_acquire_receipt_and_never_replays(mode):
    async def run():
        client, calls = scenario(mode, results=[True, "legacy-url"])
        async with managed(client, mode):
            lock = factory(mode, client)
            acquired = await invoke(lock, mode, allow_write=True)
            with pytest.raises(SubmissionOutcomeUnknown):
                await invoke(lock, mode, "release", allow_write=True)
            assert lock.receipt is acquired and lock.state == "OUTCOME_UNKNOWN"
            with pytest.raises(InvalidRequestError):
                await invoke(lock, mode, "release", allow_write=True)
        assert len(calls) == 4
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_service_drift_before_release_never_sends_unlock(mode):
    async def run():
        client, calls = scenario(mode, identities=["resources-id", "replacement-id"])
        async with managed(client, mode):
            lock = factory(mode, client)
            await invoke(lock, mode, allow_write=True)
            with pytest.raises(ProtocolError):
                await invoke(lock, mode, "release", allow_write=True)
        assert len(calls) == 3 and not any("/unlock/" in r.url.path for r in calls)
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("change", ["origin", "identity"])
def test_binding_drift_during_native_operation_keeps_outcome_unknown(mode, change):
    def mutate(client):
        if change == "origin":
            client.transport.endpoints["resources"] = Endpoint("https://replacement.invalid/resources", "resources-secret")
        else:
            client._service_id_cache["resources"] = "replacement-id"
    async def run():
        client, calls = scenario(mode, mutate=mutate)
        async with managed(client, mode):
            lock = factory(mode, client)
            with pytest.raises(SubmissionOutcomeUnknown):
                await invoke(lock, mode, allow_write=True)
            assert lock.state == "OUTCOME_UNKNOWN" and lock.receipt is None
        assert len(calls) == 2
    asyncio.run(run())


@pytest.mark.parametrize("action", ["acquire", "release"])
def test_async_cancellation_at_admission_and_competing_waiter_never_resend(action):
    async def run():
        calls, dispatched = [], asyncio.Event()
        async def handler(request):
            calls.append(request)
            if request.url.path.endswith("/public/capabilities"):
                return httpx.Response(200, json={"serviceId": "resources-id"})
            if ("/unlock/" in request.url.path) == (action == "release"):
                dispatched.set()
                await asyncio.Event().wait()
            return httpx.Response(200, json=True)
        async with AsyncClient("https://runtime.invalid", resources=Endpoint("https://resources.invalid/resources"),
                               http_transport=httpx.MockTransport(handler)) as client:
            lock = AsyncProjectLock(client, "owned")
            if action == "release":
                await lock.acquire(allow_write=True)
            first = asyncio.create_task(getattr(lock, action)(allow_write=True))
            await asyncio.wait_for(dispatched.wait(), timeout=2)
            second = asyncio.create_task(getattr(lock, action)(allow_write=True))
            first.cancel()
            with pytest.raises(asyncio.CancelledError):
                await first
            with pytest.raises(InvalidRequestError):
                await second
            assert lock.state == "OUTCOME_UNKNOWN"
        assert sum("/project/" in r.url.path for r in calls) == (2 if action == "release" else 1)
        assert sum("/unlock/" in r.url.path for r in calls) == int(action == "release")
    asyncio.run(run())


def test_async_cancelled_capability_read_is_not_native_admission_or_automatic_replay():
    async def run():
        calls, dispatched = [], asyncio.Event()
        initial = True
        async def handler(request):
            nonlocal initial
            calls.append(request)
            if request.url.path.endswith("/public/capabilities"):
                if initial:
                    initial = False
                    dispatched.set()
                    await asyncio.Event().wait()
                return httpx.Response(200, json={"serviceId": "resources-id"})
            return httpx.Response(200, json=True)
        async with AsyncClient("https://runtime.invalid", resources=Endpoint("https://resources.invalid/resources"),
                               http_transport=httpx.MockTransport(handler)) as client:
            lock = AsyncProjectLock(client, "owned")
            task = asyncio.create_task(lock.acquire(allow_write=True))
            await asyncio.wait_for(dispatched.wait(), timeout=2)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
            assert lock.state == "NOT_REQUESTED" and len(calls) == 1
            assert (await lock.acquire(allow_write=True)).confirmed  # explicit caller attempt
        assert len(calls) == 3 and sum("/project/" in r.url.path for r in calls) == 1
    asyncio.run(run())
