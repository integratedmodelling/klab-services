"""SIMULATED_PROVISIONAL/JAVA-GAP-0009 private CAS; never source acceptance/publication."""
from concurrent.futures import ThreadPoolExecutor
from urllib.parse import urlsplit
import asyncio

import pytest

from klab_client import (Client, Context, AsyncClient, DraftConflict, read_draft, write_draft,
    async_read_draft, async_write_draft)
from klab_client.errors import AuthorizationError, InvalidRequestError, MissingAssetError, ServerError
from conftest import launch
from test_vertical import client_args, control
from klab_simulator.drafts import PROTOCOL


def test_two_writers_cannot_overwrite_same_revision_and_history_is_exact(simulator):
    with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
        session = client.create_session(); context = session.create_context()
        try:
            with pytest.raises(InvalidRequestError): write_draft(context, "source", "raw unvalidated", expected_revision=0)
            with pytest.raises(MissingAssetError): read_draft(context, "source", allow_experimental=True)
            first = write_draft(context, "source", "raw unvalidated", expected_revision=0, allow_experimental=True)
            def writer(source):
                try:
                    return write_draft(context, "source", source, expected_revision=1, allow_experimental=True)
                except DraftConflict:
                    return None
            with ThreadPoolExecutor(max_workers=2) as pool:
                results = list(pool.map(writer, ("draft A", "draft B")))
            assert sum(row is not None for row in results) == 1
            second = read_draft(context, "source", allow_experimental=True)
            assert second.revision == 2 and second.source in ("draft A", "draft B")
            assert read_draft(context, "source", revision=1, allow_experimental=True) == first
            assert second.validation == "UNVALIDATED_PRIVATE_DRAFT"
            with Client(simulator["endpoints"]["runtime"], **client_args(simulator, "sim-token-bob")) as foreign:
                denied = Context(foreign, {"id": context.id, "serviceId": simulator["service_ids"]["runtime"]})
                with pytest.raises(AuthorizationError): read_draft(denied, "source", allow_experimental=True)
                with pytest.raises(AuthorizationError): write_draft(denied, "source", "stolen", expected_revision=2, allow_experimental=True)
            assert control(simulator)["authoring_assets"] == 0
        finally:
            context.release(); session.release()
        assert control(simulator)["draft_contexts"] == 0 and not any(control(simulator)["counts"].values())


def test_draft_restart_retains_exact_history_and_fences_stale_expected_revision(tmp_path):
    state_file = tmp_path / "drafts.klab-sim.sqlite"
    with launch(state_file=state_file) as ready:
        port = urlsplit(ready["endpoints"]["runtime"]).port
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            session = client.create_session(); context = session.create_context(); root = context.id
            first = write_draft(context, "source", "one", expected_revision=0, allow_experimental=True)
            write_draft(context, "source", "two", expected_revision=1, allow_experimental=True)
    with launch(port=port, state_file=state_file) as ready:
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            context = client.attach_context(root)
            assert read_draft(context, "source", revision=1, allow_experimental=True) == first
            with pytest.raises(DraftConflict): write_draft(context, "source", "stale", expected_revision=1, allow_experimental=True)
            assert read_draft(context, "source", allow_experimental=True).source == "two"
            context.release(); client.runtime.release_session(session.id)
        assert control(ready)["draft_contexts"] == 0


def test_bounded_history_eviction_is_explicit_and_names_have_finite_capacity(simulator):
    with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
        session = client.create_session(); context = session.create_context()
        try:
            for revision in range(10):
                write_draft(context, "source", str(revision), expected_revision=revision, allow_experimental=True)
            with pytest.raises(ServerError) as lost: read_draft(context, "source", revision=1, allow_experimental=True)
            assert lost.value.status_code == 410
            assert read_draft(context, "source", revision=3, allow_experimental=True).source == "2"
            for n in range(7): write_draft(context, f"other{n}", "x", expected_revision=0, allow_experimental=True)
            with pytest.raises(ServerError) as capacity:
                write_draft(context, "ninth", "x", expected_revision=0, allow_experimental=True)
            assert capacity.value.status_code == 429
        finally:
            context.release(); session.release()


def test_async_owned_draft_flow_is_same_explicit_cas_contract(simulator):
    async def run():
        async with AsyncClient(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
            session = await client.create_session(); context = await session.create_context()
            try:
                first = await async_write_draft(context, "source", "unvalidated source", expected_revision=0, allow_experimental=True)
                assert await async_read_draft(context, "source", allow_experimental=True) == first
                with pytest.raises(DraftConflict):
                    await async_write_draft(context, "source", "stale", expected_revision=0, allow_experimental=True)
            finally:
                await context.release(); await session.release()
    asyncio.run(run())
    assert control(simulator)["draft_contexts"] == 0


@pytest.mark.parametrize("change", ["owner", "name", "history", "revision", "hash", "source", "validation", "noncontiguous"])
def test_corrupt_draft_storage_refuses_before_partial_restore(change):
    from klab_simulator.persistence import snapshot, restore
    from klab_simulator.state import State
    import hashlib
    state = State(); before = snapshot(state); saved = snapshot(state)
    saved["simple"]["contexts"] = {"s.c": {"owner": "alice"}}
    row = {"protocol": PROTOCOL, "name": "source", "revision": 1, "source": "x",
        "source_sha256": hashlib.sha256(b"x").hexdigest(), "validation": "UNVALIDATED_PRIVATE_DRAFT"}
    saved["drafts"] = {"s.c": {"source": [row]}}
    if change == "owner": saved["drafts"] = {"foreign": {"source": [row]}}
    elif change == "name": saved["drafts"]["s.c"] = {"..": [row]}
    elif change == "history": saved["drafts"]["s.c"]["source"] = []
    elif change == "revision": row["revision"] = True
    elif change == "hash": row["source_sha256"] = "bad"
    elif change == "source": row["source"] = "\ud800"
    elif change == "validation": row["validation"] = "ACCEPTED"
    else: saved["drafts"]["s.c"]["source"].append({**row, "revision": 3})
    with pytest.raises(ValueError): restore(state, saved)
    assert snapshot(state) == before


def test_old_checkpoint_migrates_without_invented_drafts():
    from klab_simulator.persistence import snapshot, restore
    from klab_simulator.state import State
    state = State(); saved = snapshot(state); del saved["drafts"]
    restore(state, saved)
    assert state.drafts.rows == {}


def test_failed_draft_persistence_does_not_acknowledge_or_leave_inmemory_write():
    from klab_simulator.state import State
    import json
    import hashlib
    state = State()
    state.contexts["s.c"] = {"owner": "alice"}
    class FailedStore:
        def save(self, _): raise OSError("Synthetic owned disk failure")
    state.store = FailedStore()
    headers = {"Authorization": "sim-token-alice", "klab-scope": "s.c", "klab-service": state.service_ids["runtime"]}
    body = {"protocol": PROTOCOL, "source": "x", "source_sha256": hashlib.sha256(b"x").hexdigest(), "expected_revision": 0}
    status, _, _ = state.request("PUT", "/runtime/experimental/drafts/v1/source", headers, json.dumps(body).encode())
    assert status == 507 and state.drafts.rows == {}
