"""SIM-BEHAVIOR-DRAFT-001: nonexecuting source ADD, never an actor/compiler port."""
import asyncio
from contextlib import asynccontextmanager

import pytest

from klab_client import AsyncAuthoring, AsyncClient, Authoring, Client
from klab_client.errors import InvalidRequestError, SubmissionOutcomeUnknown
from klab_simulator.authoring import BEHAVIOR, BEHAVIOR_SOURCE
from test_vertical import client_args, control


@asynccontextmanager
async def managed(client, mode):
    if mode == "async":
        async with client:
            yield
    else:
        with client:
            yield


async def call(author, mode, method, *args, **kwargs):
    result = getattr(author, method)(*args, **kwargs)
    return await result if mode == "async" else result


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_nonexecuting_behavior_draft_add_uses_exact_native_source_and_never_instantiates(simulator, mode):
    async def run():
        client = (AsyncClient if mode == "async" else Client)(simulator["endpoints"]["runtime"], **client_args(simulator))
        async with managed(client, mode):
            author = (AsyncAuthoring if mode == "async" else Authoring)(client)
            with pytest.raises(InvalidRequestError):
                await call(author, mode, "parse", BEHAVIOR_SOURCE + "// unknown fixture", kind="BEHAVIOR")
            parsed = await call(author, mode, "parse", BEHAVIOR_SOURCE, kind="BEHAVIOR")
            workspace = await call(author, mode, "create_workspace", "drafts", allow_write=True)
            project = await call(author, mode, "create_project", workspace, "owned", allow_write=True)
            before = control(simulator)
            with pytest.raises(InvalidRequestError):
                await call(author, mode, "create_document", parsed, project)
            after = control(simulator)
            write_count = lambda report: sum(row["method"] == "PUT" for row in report["trace"])
            assert write_count(before) == write_count(after) == 2
            receipt = await call(author, mode, "create_document", parsed, project, allow_write=True)
            assert receipt.kind == "BEHAVIOR" and receipt.operation == "CREATE" and receipt.urn == BEHAVIOR
            assert len(author.created) == 3 and not hasattr(receipt, "start")
            with pytest.raises(SubmissionOutcomeUnknown):
                await call(author, mode, "create_document", parsed, project, allow_write=True)
            assert len(author.created) == 3
        report = control(simulator)
        assert report["authoring_assets"] == 3  # Explicit retained fixture drafts, not a cleanup lease.
        assert not any(report["counts"].values()) and report["project_locks"] == 0
        assert sum("/submit/BEHAVIOR/ADD" in row["path"] for row in report["trace"]) == 2
        assert not any("instantiate" in row["path"] or "/jobs/" in row["path"]
                       or "unlock" in row["path"] or "createSession" in row["path"] for row in report["trace"])
    asyncio.run(run())
