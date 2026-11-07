"""SIMULATED_PROVISIONAL/JAVA-GAP-0005: strict reference decoder, not native replay."""
import asyncio
from types import SimpleNamespace

import pytest

from klab_client import (Client, Context, NotificationCursor, NotificationResyncRequired,
    async_read_notifications, read_notifications)
from klab_client.durable_notifications import PROTOCOL, _decode, _prepare, _wire
from klab_client.errors import InvalidRequestError, ProtocolError

REF = SimpleNamespace(runtime_url="https://runtime.example/runtime", runtime_id="runtime", scope="session.context")


def cursor(sequence=0):
    return NotificationCursor(REF.runtime_url, REF.runtime_id, REF.scope, "epoch", sequence)


def payload():
    return {"protocol": PROTOCOL, "epoch": "epoch", "first": 1, "next": 2, "state": "OK",
        "coverage": "SINCE_EXPLICIT_REFERENCE_ACTIVATION_ONLY", "events": [
            {"sequence": 1, "kind": "JOB_FINISHED", "job_id": 1, "service": "runtime"}]}


def test_cursor_and_batch_are_immutable_explicit_processing_checkpoints():
    initial = cursor()
    assert NotificationCursor.from_dict(initial.to_dict()) == initial
    batch = _decode(payload(), REF, initial, 1)
    assert initial.sequence == 0 and batch.cursor.sequence == 1
    with pytest.raises(TypeError): batch.events[0]["kind"] = "corrupt"


@pytest.mark.parametrize("change", ["shape", "protocol", "coverage", "epoch", "bounds", "events", "state", "sequence", "event-fields", "kind", "job", "service", "missing", "oversized", "epoch-drift", "old-cursor", "initial-events", "partial-resync"])
def test_incomplete_unordered_foreign_or_ambiguous_history_never_advances_cursor(change):
    value = payload(); previous = cursor()
    if change == "shape": value["extra"] = 1
    elif change == "protocol": value["protocol"] = "unknown"
    elif change == "coverage": value["coverage"] = "ALL_HISTORY"
    elif change == "epoch": value["epoch"] = ""
    elif change == "bounds": value["first"] = 3
    elif change == "events": value["events"] = {}
    elif change == "state": value["state"] = "UNKNOWN"
    elif change == "sequence": value["events"][0]["sequence"] = 2
    elif change == "event-fields": value["events"][0]["extra"] = "unsafe"
    elif change == "kind": value["events"][0]["kind"] = "AUTO_COMPUTE"
    elif change == "job": value["events"][0]["job_id"] = True
    elif change == "service": value["events"][0]["service"] = "unknown"
    elif change == "missing": value["events"] = []
    elif change == "oversized": value["events"] *= 65
    elif change == "epoch-drift": value["epoch"] = "other"
    elif change == "old-cursor": value["first"] = 2
    elif change == "initial-events": previous = None
    else: value["state"] = "RESYNC_REQUIRED"
    with pytest.raises(ProtocolError): _decode(value, REF, previous, 64)
    assert previous is None or previous.sequence == 0


def test_epoch_or_retention_loss_is_explicit_resync_not_reset():
    value = payload(); value.update(state="RESYNC_REQUIRED", events=[])
    with pytest.raises(NotificationResyncRequired): _decode(value, REF, cursor(), 64)
    value.update(state="OK", next=1)
    assert _decode(value, REF, None, 64).cursor.sequence == 0


@pytest.mark.parametrize("value", [{}, {"extra": 1}, None])
def test_unknown_cursor_fields_refuse(value):
    with pytest.raises(InvalidRequestError): NotificationCursor.from_dict(value)


@pytest.mark.parametrize("change", [{"epoch": ""}, {"scope": None}, {"sequence": True}, {"sequence": -1}, {"sequence": 2**53}])
def test_invalid_cursor_identity_and_sequence(change):
    with pytest.raises(InvalidRequestError): NotificationCursor.from_dict({**cursor().to_dict(), **change})


def test_opt_in_root_and_transport_matching_refusals_are_zero_network():
    with pytest.raises(InvalidRequestError): read_notifications(object(), allow_experimental=True)
    with pytest.raises(InvalidRequestError): asyncio.run(async_read_notifications(object(), allow_experimental=True))
    with Client(REF.runtime_url, "synthetic", agent_name="alice", runtime_service_id="runtime") as client:
        context = Context(client, {"id": REF.scope, "serviceId": "runtime"})
        with pytest.raises(InvalidRequestError): read_notifications(context)
        with pytest.raises(InvalidRequestError): _prepare(object(), None, True, 64)
        with pytest.raises(InvalidRequestError): _prepare(context, None, True, 0)
        with pytest.raises(InvalidRequestError):
            _prepare(context, NotificationCursor(REF.runtime_url, "other-runtime", REF.scope, "epoch", 0), True, 64)
        with pytest.raises(InvalidRequestError): asyncio.run(async_read_notifications(context, allow_experimental=True))


@pytest.mark.parametrize("raw", [None, b'\xff', b'{}{}', b'{"epoch":"one","epoch":"two"}', b'NaN', b'x' * 65537])
def test_malformed_duplicate_or_nonfinite_wire_cannot_establish_continuity(raw):
    with pytest.raises(ProtocolError): _wire(raw, REF, cursor(), 64)
