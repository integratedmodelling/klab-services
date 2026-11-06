"""SIM-PROJECT-LOCK-001: only the native routes needed by RC-P4.4/CAP14 acceptance."""
import asyncio

import pytest

from klab_client import AsyncClient, AsyncProjectLock, Authoring, Client, ProjectLock
from klab_client.errors import AuthorizationError
from test_vertical import client_args, control


def options(ready, token):
    arguments = client_args(ready, token)
    arguments["agent_name"] = "bob" if token.endswith("bob") else "alice"
    return arguments


def project(ready):
    with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
        authoring = Authoring(client)
        workspace = authoring.create_workspace("locks", allow_write=True)
        authoring.create_project(workspace, "owned", allow_write=True)


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_native_project_lock_principal_sharing_and_explicit_release(simulator, mode):
    project(simulator)
    runtime = simulator["endpoints"]["runtime"]
    if mode == "async":
        async def run():
            async with AsyncClient(runtime, **options(simulator, "sim-token-admin-alice")) as client:
                lock = AsyncProjectLock(client, "owned")
                assert (await lock.acquire(allow_write=True)).confirmed
            assert control(simulator)["project_locks"] == 1  # close never implicitly unlocks
            async with AsyncClient(runtime, **options(simulator, "sim-token-admin-bob")) as other:
                denied = await AsyncProjectLock(other, "owned").acquire(allow_write=True)
                assert denied.confirmed is False
            async with AsyncClient(runtime, **options(simulator, "sim-token-admin-alice")) as same_principal:
                shared = AsyncProjectLock(same_principal, "owned")
                assert (await shared.acquire(allow_write=True)).confirmed
                assert (await shared.release(allow_write=True)).confirmed
        asyncio.run(run())
    else:
        with Client(runtime, **options(simulator, "sim-token-admin-alice")) as client:
            lock = ProjectLock(client, "owned")
            assert lock.acquire(allow_write=True).confirmed
        assert control(simulator)["project_locks"] == 1
        with Client(runtime, **options(simulator, "sim-token-admin-bob")) as other:
            assert ProjectLock(other, "owned").acquire(allow_write=True).confirmed is False
        with Client(runtime, **options(simulator, "sim-token-admin-alice")) as same_principal:
            shared = ProjectLock(same_principal, "owned")
            assert shared.acquire(allow_write=True).confirmed
            assert shared.release(allow_write=True).confirmed
    report = control(simulator)
    assert report["project_locks"] == 0 and not any(report["counts"].values())
    assert sum("/project/lock/" in row["path"] for row in report["trace"]) == 3
    assert sum("/project/unlock/" in row["path"] for row in report["trace"]) == 1
    assert report["authoring_assets"] == 2  # Explicitly created fixture assets, not a cleanup lease.


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_administrator_role_remains_server_authoritative_and_unknown_project_false_is_not_a_diagnosis(simulator, mode):
    project(simulator)
    runtime = simulator["endpoints"]["runtime"]
    if mode == "async":
        async def run():
            async with AsyncClient(runtime, **client_args(simulator)) as client:
                lock = AsyncProjectLock(client, "owned")
                with pytest.raises(AuthorizationError):
                    await lock.acquire(allow_write=True)
                assert lock.receipt is None and lock.state == "OUTCOME_UNKNOWN"
            async with AsyncClient(runtime, **options(simulator, "sim-token-admin-alice")) as admin:
                result = await AsyncProjectLock(admin, "absent").acquire(allow_write=True)
                assert result.confirmed is False
        asyncio.run(run())
    else:
        with Client(runtime, **client_args(simulator)) as client:
            lock = ProjectLock(client, "owned")
            with pytest.raises(AuthorizationError):
                lock.acquire(allow_write=True)
            assert lock.receipt is None and lock.state == "OUTCOME_UNKNOWN"
        with Client(runtime, **options(simulator, "sim-token-admin-alice")) as admin:
            assert ProjectLock(admin, "absent").acquire(allow_write=True).confirmed is False
    assert control(simulator)["project_locks"] == 0
