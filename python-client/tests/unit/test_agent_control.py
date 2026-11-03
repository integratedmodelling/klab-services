from concurrent.futures import ThreadPoolExecutor
from dataclasses import FrozenInstanceError, replace
import json
from pathlib import Path
from threading import Event

import pytest

from klab_client import AgentControlBinding, AgentControlJournal, AgentControlView
from klab_client.errors import InvalidRequestError, ProtocolError, TransportError

ROOT = Path(__file__).resolve().parents[3]
WIRE = json.loads((ROOT / "klab.core.common/src/test/resources/agent-status-wire.json").read_bytes())


def binding(**kwargs):
    fields = dict(agent_urn="test.agent.status", runtime_url="https://runtime.example/api/",
        runtime_id="runtime-one", scope="session.context", principal_key="principal-one",
        runtime_incarnation="test-boot-one")
    return AgentControlBinding(**(fields | kwargs))


def payload(state="READY", **kwargs):
    return json.dumps(next(row for row in WIRE if row["state"] == state) | kwargs).encode()


def test_binding_is_immutable_normalized_and_not_authority():
    ref = binding()
    assert ref == binding(runtime_url="https://runtime.example/api")
    assert "principal-one" not in repr(ref)
    assert "test-boot-one" not in repr(ref)
    with pytest.raises(FrozenInstanceError):
        ref.agent_urn = "other"
    journal = AgentControlJournal(ref)
    assert isinstance(journal.view, AgentControlView)
    assert journal.view.attachment == "ACTIVE"
    assert journal.view.remote_cleanup == "UNKNOWN"
    assert journal.view.last_report is None
    with pytest.raises(FrozenInstanceError):
        journal.view.start_outcome = "PUBLISHED"


@pytest.mark.parametrize("kwargs", [dict(agent_urn=""), dict(scope=None), dict(scope="bad scope"),
    dict(scope="session..context"), dict(principal_key=""), dict(runtime_incarnation=""),
    dict(runtime_id=None), dict(runtime_url="https://user:password@example.com"),
    dict(runtime_url="https://runtime.example?token=secret")])
def test_binding_rejects_invalid_fields(kwargs):
    with pytest.raises(InvalidRequestError):
        binding(**kwargs)


@pytest.mark.parametrize("kwargs", [dict(binding=None), dict(max_reports=True), dict(max_reports=0),
    dict(max_reports=10001), dict(max_total_bytes=False), dict(max_total_bytes=0),
    dict(max_total_bytes=16 * 1024 * 1024 + 1)])
def test_constructor_bounds(kwargs):
    with pytest.raises(InvalidRequestError):
        AgentControlJournal(**(dict(binding=binding()) | kwargs))


@pytest.mark.parametrize("field,value", [("agent_urn", "other"), ("runtime_url", "https://other.example"),
    ("runtime_id", "other"), ("scope", "other.context"), ("principal_key", "other"),
    ("runtime_incarnation", "other")])
def test_every_binding_component_is_compared_before_callback_and_observation(field, value):
    ref = binding()
    journal = AgentControlJournal(ref)
    wrong = replace(ref, **{field: value})
    calls = []
    with pytest.raises(ProtocolError):
        journal.attempt("start", binding=wrong, sender=lambda *args: calls.append(args))
    with pytest.raises(ProtocolError):
        journal.observe(payload(), binding=wrong)
    assert calls == [] and journal.view.start_outcome == "NOT_ATTEMPTED"
    assert journal.view.admitted_reports == 0


def test_wrong_binding_type_is_not_authority():
    journal = AgentControlJournal(binding())
    with pytest.raises(ProtocolError):
        journal.observe(payload(), binding={})


@pytest.mark.parametrize("action,sender", [(None, lambda *_: True), ([], lambda *_: True),
    ("restart", lambda *_: True), ("start", None)])
def test_invalid_action_or_callback_does_not_consume_admission(action, sender):
    ref = binding()
    journal = AgentControlJournal(ref)
    with pytest.raises(InvalidRequestError):
        journal.attempt(action, binding=ref, sender=sender)
    assert journal.view.start_outcome == "NOT_ATTEMPTED"


@pytest.mark.parametrize("published,outcome", [(True, "PUBLISHED"), (False, "UNCONFIRMED")])
def test_publication_result_is_not_execution_or_terminal_confirmation(published, outcome):
    ref = binding()
    journal = AgentControlJournal(ref)
    calls = []
    def send(actual, action):
        assert actual == ref
        assert journal.view.start_outcome == "UNKNOWN"  # Consumed before callback.
        calls.append(action)
        return published
    view = journal.attempt("start", binding=ref, sender=send)
    assert view.start_outcome == outcome
    assert view.last_report is None and view.remote_cleanup == "UNKNOWN"
    with pytest.raises(InvalidRequestError):
        journal.attempt("start", binding=ref, sender=send)
    assert calls == ["start"]


@pytest.mark.parametrize("error", [RuntimeError("secret-token"), KeyboardInterrupt(), SystemExit(2)])
def test_failure_or_cancellation_permanently_consumes_admission(error):
    ref = binding()
    journal = AgentControlJournal(ref)
    def fail(*_):
        raise error
    expected = TransportError if isinstance(error, Exception) else type(error)
    with pytest.raises(expected) as caught:
        journal.attempt("start", binding=ref, sender=fail)
    assert "secret-token" not in str(caught.value)
    assert journal.view.start_outcome == "UNKNOWN"
    with pytest.raises(InvalidRequestError):
        journal.attempt("start", binding=ref, sender=lambda *_: True)
    # An independently requested bounded stop remains possible after unknown start.
    assert journal.attempt("stop", binding=ref, sender=lambda *_: True).stop_outcome == "PUBLISHED"
    assert journal.view.remote_cleanup == "UNKNOWN"


@pytest.mark.parametrize("reply", [None, 1, "true", {}])
def test_nonboolean_reply_is_unknown_not_retryable(reply):
    ref = binding()
    journal = AgentControlJournal(ref)
    with pytest.raises(ProtocolError):
        journal.attempt("stop", binding=ref, sender=lambda *_: reply)
    assert journal.view.stop_outcome == "UNKNOWN"
    with pytest.raises(InvalidRequestError):
        journal.attempt("stop", binding=ref, sender=lambda *_: True)


@pytest.mark.parametrize("state,viable", [("RUNNING", True), ("READY", False)])
def test_start_refuses_incompatible_sender_status(state, viable):
    ref = binding()
    journal = AgentControlJournal(ref)
    journal.observe(payload(state, viable=viable), binding=ref)
    with pytest.raises(InvalidRequestError):
        journal.attempt("start", binding=ref, sender=lambda *_: True)
    assert journal.view.start_outcome == "NOT_ATTEMPTED"


def test_stop_before_start_prevents_start_even_with_a_later_ready_report():
    ref = binding()
    journal = AgentControlJournal(ref)
    journal.attempt("stop", binding=ref, sender=lambda *_: False)
    journal.observe(payload(), binding=ref)
    with pytest.raises(InvalidRequestError):
        journal.attempt("start", binding=ref, sender=lambda *_: True)


def test_ready_running_terminal_reports_do_not_confirm_remote_cleanup():
    ref = binding()
    journal = AgentControlJournal(ref)
    journal.observe(payload(), binding=ref)
    journal.attempt("start", binding=ref, sender=lambda *_: True)
    journal.observe(payload("RUNNING", timestamp=1), binding=ref)
    assert journal.view.conflicting_reports is False  # Do not order by sender clock.
    journal.attempt("stop", binding=ref, sender=lambda *_: True)
    view = journal.observe(payload("STOPPED", timestamp=0), binding=ref)
    assert view.terminal_reported is True
    assert view.remote_cleanup == "UNKNOWN"
    assert view.conflicting_reports is False
    assert view.admitted_reports == 3


@pytest.mark.parametrize("terminal", ["STOPPED", "FAILED"])
def test_terminal_latches_and_reordered_reports_never_reopen(terminal):
    ref = binding()
    journal = AgentControlJournal(ref)
    journal.observe(payload(terminal), binding=ref)
    journal.observe(payload(terminal), binding=ref)
    assert journal.view.conflicting_reports is False
    for action in ("start", "stop"):
        with pytest.raises(InvalidRequestError):
            journal.attempt(action, binding=ref, sender=lambda *_: True)
    view = journal.observe(payload("RUNNING", timestamp=2**63 - 1), binding=ref)
    assert view.terminal_reported is True and view.conflicting_reports is True
    assert view.last_report.state == "RUNNING"  # Evidence, not current-state authority.


def test_running_then_ready_is_a_conflict_not_a_restart_opportunity():
    ref = binding()
    journal = AgentControlJournal(ref)
    journal.observe(payload("RUNNING"), binding=ref)
    journal.observe(payload(), binding=ref)
    assert journal.view.conflicting_reports is True
    with pytest.raises(InvalidRequestError):
        journal.attempt("start", binding=ref, sender=lambda *_: True)


@pytest.mark.parametrize("body", [None, "{}", bytearray(b"{}"), b"x" * 65537])
def test_invalid_byte_admission_does_not_invoke_a_sender(body):
    ref = binding()
    journal = AgentControlJournal(ref)
    with pytest.raises(ProtocolError):
        journal.observe(body, binding=ref)
    assert journal.view.admitted_reports == journal.view.admitted_bytes == 0


def test_malformed_payload_consumes_budget_but_not_a_status_or_command():
    ref = binding()
    journal = AgentControlJournal(ref, max_reports=1)
    with pytest.raises(ProtocolError):
        journal.observe(b"{", binding=ref)
    assert journal.view.last_report is None and journal.view.admitted_bytes == 1
    with pytest.raises(ProtocolError):
        journal.observe(payload(), binding=ref)
    assert journal.view.attachment == "LOST"
    with pytest.raises(InvalidRequestError):
        journal.attempt("start", binding=ref, sender=lambda *_: True)


def test_exact_total_budget_and_overflow_loses_continuity():
    ref = binding()
    body = payload()
    journal = AgentControlJournal(ref, max_total_bytes=len(body))
    journal.observe(body, binding=ref)
    with pytest.raises(ProtocolError):
        journal.observe(body, binding=ref)
    assert journal.view.admitted_bytes == len(body)
    assert journal.view.attachment == "LOST"


def test_loss_and_detach_do_not_send_stop_or_claim_cleanup():
    ref = binding()
    journal = AgentControlJournal(ref)
    assert journal.transport_lost().attachment == "LOST"
    assert journal.transport_lost().attachment == "LOST"
    assert journal.detach().attachment == "DETACHED"
    assert journal.detach().attachment == "DETACHED"
    assert journal.transport_lost().attachment == "DETACHED"
    assert journal.view.stop_outcome == "NOT_ATTEMPTED"
    assert journal.view.remote_cleanup == "UNKNOWN"
    with pytest.raises(InvalidRequestError):
        journal.observe(payload(), binding=ref)


def test_concurrent_admission_is_one_shot_even_during_detach():
    ref = binding()
    journal = AgentControlJournal(ref)
    entered, finish = Event(), Event()
    calls = []
    def send(*_):
        calls.append("start")
        entered.set()
        assert finish.wait(5)
        return True
    with ThreadPoolExecutor(max_workers=1) as pool:
        future = pool.submit(journal.attempt, "start", binding=ref, sender=send)
        try:
            assert entered.wait(5)
            with pytest.raises(InvalidRequestError):
                journal.attempt("start", binding=ref, sender=send)
            journal.detach()
        finally:
            finish.set()
        view = future.result(timeout=5)
    assert calls == ["start"]
    assert view.attachment == "DETACHED" and view.start_outcome == "PUBLISHED"
    assert view.remote_cleanup == "UNKNOWN"


def test_reentrant_sender_cannot_reserve_the_same_command_again():
    ref = binding()
    journal = AgentControlJournal(ref)
    calls = []
    def send(*_):
        calls.append("start")
        with pytest.raises(InvalidRequestError):
            journal.attempt("start", binding=ref, sender=send)
        return True
    journal.attempt("start", binding=ref, sender=send)
    assert calls == ["start"]


def test_payload_identity_cannot_override_binding_or_restore_consumed_start():
    ref = binding()
    journal = AgentControlJournal(ref)
    journal.attempt("start", binding=ref, sender=lambda *_: False)
    with pytest.raises(ProtocolError):
        journal.observe(payload(agentUrn="wrong"), binding=ref)
    assert journal.view.last_report is None
    assert journal.view.admitted_reports == 1
    journal.observe(payload("READY"), binding=ref)
    with pytest.raises(InvalidRequestError):
        journal.attempt("start", binding=ref, sender=lambda *_: True)


def test_publication_does_not_recover_from_loss_reported_by_callback():
    ref = binding()
    journal = AgentControlJournal(ref)
    def send(*_):
        journal.transport_lost()
        return True
    view = journal.attempt("stop", binding=ref, sender=send)
    assert view.stop_outcome == "PUBLISHED" and view.attachment == "LOST"
    assert view.remote_cleanup == "UNKNOWN"
