"""SIMULATED_PROVISIONAL/JAVA-GAP-0005; no native AMQP ordering/replay acceptance."""
import asyncio
from urllib.parse import urlsplit

import pytest
import httpx

from klab_client import (AsyncClient, Client, NotificationCursor, NotificationResyncRequired,
    ObservationRequest, async_read_notifications, read_notifications)
from klab_client.errors import AuthorizationError, InvalidRequestError
from conftest import launch
from test_vertical import client_args, control, geometry


def test_retained_events_replay_with_explicit_checkpoint_and_survive_process_restart(tmp_path):
    state_file = tmp_path / "notifications.klab-sim.sqlite"
    with launch(state_file=state_file) as ready:
        port = urlsplit(ready["endpoints"]["runtime"]).port
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            session = client.create_session()
            context = session.create_context()
            with pytest.raises(InvalidRequestError): read_notifications(context)
            initial = read_notifications(context, allow_experimental=True)
            assert initial.events == () and initial.cursor.sequence == 0
            job = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))
            job.result(timeout=2)
            first = read_notifications(context, initial.cursor, allow_experimental=True, limit=1)
            assert first.events[0]["kind"] == "JOB_WAITING" and first.cursor.sequence == 1
            duplicate = read_notifications(context, initial.cursor, allow_experimental=True, limit=1)
            assert duplicate == first  # Reading never commits caller processing implicitly.
            saved = NotificationCursor.from_dict(first.cursor.to_dict())
            root = context.id
    with launch(port=port, state_file=state_file) as restarted:
        with Client(restarted["endpoints"]["runtime"], **client_args(restarted)) as client:
            context = client.attach_context(root)
            remaining = read_notifications(context, saved, allow_experimental=True)
            assert [e["kind"] for e in remaining.events] == ["JOB_STARTED", "JOB_FINISHED"]
            assert remaining.cursor.sequence == 3 and remaining.cursor.epoch == saved.epoch
            assert read_notifications(context, remaining.cursor, allow_experimental=True).events == ()
            with Client(restarted["endpoints"]["runtime"], **client_args(restarted, "sim-token-bob")) as foreign:
                from klab_client.client import Context
                denied = Context(foreign, {"id": root, "serviceId": restarted["service_ids"]["runtime"]})
                with pytest.raises(AuthorizationError): read_notifications(denied, saved, allow_experimental=True)
            with pytest.raises(NotificationResyncRequired):
                read_notifications(context, NotificationCursor(saved.runtime_url, saved.runtime_id, root, "wrong-epoch", 1), allow_experimental=True)
            context.release()
            client.runtime.release_session(session.id)
        assert not any(control(restarted)["counts"].values())
        assert control(restarted)["notification_streams"] == 0


def test_retention_overflow_requires_explicit_resync_never_partial_replay(simulator):
    with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
        session = client.create_session(); context = session.create_context()
        try:
            initial = read_notifications(context, allow_experimental=True).cursor
            for _ in range(45):
                last_observation = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry())).result(timeout=2)
            with pytest.raises(NotificationResyncRequired):
                read_notifications(context, initial, allow_experimental=True)
            # Explicit decision after a bounded server read, NOT complete graph convergence.
            assert last_observation.fetch_data((0, 399)).values == (100, 195)
            new = read_notifications(context, allow_experimental=True)
            assert new.events == () and new.cursor.sequence == 135
        finally:
            context.release(); session.release()


def test_native_async_reference_uses_same_explicit_cursor_protocol(simulator):
    async def run():
        async with AsyncClient(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
            session = await client.create_session()
            context = await session.create_context()
            try:
                initial = await async_read_notifications(context, allow_experimental=True)
                job = await context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))
                await job.result(timeout=2)
                batch = await async_read_notifications(context, initial.cursor, allow_experimental=True)
                assert [event["sequence"] for event in batch.events] == [1, 2, 3]
            finally:
                await context.release(); await session.release()
    asyncio.run(run())


def test_unknown_version_and_invalid_cursor_queries_never_activate_or_leak_history(simulator):
    with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
        session = client.create_session(); context = session.create_context()
        try:
            url = simulator["endpoints"]["runtime"] + "/experimental/notifications/v1/events"
            headers = {"Authorization": "sim-token-alice", "klab-scope": context.id,
                "klab-service": simulator["service_ids"]["runtime"]}
            assert httpx.get(url, headers=headers, params={"protocol": "unknown", "limit": 1}, trust_env=False).status_code == 422
            assert control(simulator)["notification_streams"] == 0
            for params in ({"after": "-1", "epoch": "wrong"}, {"epoch": "wrong"}, {"after": "0"}):
                assert httpx.get(url, headers=headers, params={"protocol": "klab.experimental.notifications/1", "limit": 1, **params}, trust_env=False).status_code == 422
            assert control(simulator)["notification_streams"] == 0
        finally:
            context.release(); session.release()


def test_old_checkpoint_migration_preserves_state_without_fabricating_event_history():
    from klab_simulator.persistence import snapshot, restore
    from klab_simulator.state import State
    state = State()
    saved = snapshot(state)
    del saved["notifications"]
    restore(state, saved)
    assert state.notifications.logs == {} and state.next_job == saved["counters"]["next_job"]


@pytest.mark.parametrize("log", [None, {"epoch": "short", "next": 1, "events": []},
    {"epoch": "a" * 32, "next": 1, "events": [{"sequence": 0}]},
    {"epoch": "a" * 32, "next": True, "events": []}])
def test_corrupt_retained_cursor_state_fails_before_partial_restore(log):
    from klab_simulator.persistence import snapshot, restore
    from klab_simulator.state import State
    state = State()
    before = snapshot(state)
    invalid = snapshot(state)
    invalid["simple"]["contexts"] = {"s.c": {"owner": "alice"}}
    invalid["notifications"] = {"s.c": log}
    with pytest.raises(ValueError): restore(state, invalid)
    assert snapshot(state) == before
