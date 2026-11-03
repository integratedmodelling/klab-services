from dataclasses import FrozenInstanceError
import json
from pathlib import Path

import pytest

from klab_client import AgentStatusSnapshot, decode_agent_status
from klab_client.errors import InvalidRequestError, ProtocolError

ROOT = Path(__file__).resolve().parents[3]
FIXTURE = ROOT / "klab.core.common/src/test/resources/agent-status-wire.json"
URN = "test.agent.status"


def payload(**changes):
    result = json.loads(FIXTURE.read_bytes())[0]
    result.update(changes)
    return json.dumps(result).encode()


@pytest.mark.parametrize("index,state", list(enumerate(("READY", "RUNNING", "STOPPED", "FAILED"))))
def test_shared_java_fixture(index, state):
    data = json.loads(FIXTURE.read_bytes())[index]
    snapshot = decode_agent_status(json.dumps(data).encode(), expected_agent_urn=URN)
    assert isinstance(snapshot, AgentStatusSnapshot)
    assert snapshot.state == state
    assert snapshot.agent_urn == URN
    assert snapshot.viable == data["viable"]
    assert snapshot.detail == data.get("detail")
    assert snapshot.timestamp == 1760000000000
    assert (snapshot.observation_id, snapshot.started_at, snapshot.last_activity_at) == (-1, -1, -1)
    with pytest.raises(FrozenInstanceError):
        snapshot.state = "RUNNING"
    assert not hasattr(snapshot, "stop")
    assert not hasattr(snapshot, "cleanup_confirmed")


@pytest.mark.parametrize("urn", [None, 12, "", "wrong agent", "a\x00b", "a\nb", "x" * 4097, "\ud800", "a\u00a0b"])
def test_expected_urn_rejected(urn):
    with pytest.raises(InvalidRequestError):
        decode_agent_status(payload(), expected_agent_urn=urn)


@pytest.mark.parametrize("limit", [0, -1, 65537, 1.5, True, None])
def test_limits_rejected(limit):
    with pytest.raises(InvalidRequestError):
        decode_agent_status(payload(), expected_agent_urn=URN, max_bytes=limit)


@pytest.mark.parametrize("body", [None, "{}", bytearray(b"{}"), b"\xff", b"{", b"[]", b"null", b"true",
                                  b'{"state":"READY","state":"STOPPED"}',
                                  b"[" * 1500 + b"]" * 1500])
def test_malformed_body_rejected(body):
    with pytest.raises(ProtocolError):
        decode_agent_status(body, expected_agent_urn=URN)


@pytest.mark.parametrize("changes", [{"agentUrn": "other"}, {"agentUrn": None}, {"agentUrn": "\ud800"},
    {"state": "UNKNOWN"}, {"state": None}, {"state": []}, {"viable": 1}, {"viable": None},
    {"detail": {}}, {"detail": "x" * 16385}, {"detail": "\ud800"}, {"extra": True}, {"@CLASS": "wrong"},
    {"timestamp": -1}, {"timestamp": True}, {"timestamp": 2**63}, {"timestamp": 0.5},
    {"observationId": -2}, {"startedAt": None}, {"lastActivityAt": float("nan")}])
def test_invalid_snapshot_rejected(changes):
    with pytest.raises(ProtocolError):
        decode_agent_status(payload(**changes), expected_agent_urn=URN)


@pytest.mark.parametrize("key", ["@CLASS", "agentUrn", "state", "viable", "timestamp", "observationId", "startedAt", "lastActivityAt"])
def test_missing_fields_fail_closed(key):
    data = json.loads(payload())
    del data[key]
    with pytest.raises(ProtocolError):
        decode_agent_status(json.dumps(data).encode(), expected_agent_urn=URN)


def test_exact_byte_budget_and_full_java_range():
    body = payload(timestamp=2**63 - 1, observationId=0, startedAt=0, lastActivityAt=2**63 - 1, detail="")
    status = decode_agent_status(body, expected_agent_urn=URN, max_bytes=len(body))
    assert status.timestamp == status.last_activity_at == 2**63 - 1
    assert status.observation_id == status.started_at == 0
    assert status.detail == ""
    with pytest.raises(ProtocolError):
        decode_agent_status(body, expected_agent_urn=URN, max_bytes=len(body) - 1)


def test_no_clock_order_or_lifecycle_cleanup_inferences():
    status = decode_agent_status(payload(state="STOPPED", viable=True, timestamp=0,
        startedAt=100, lastActivityAt=50, detail="sensitive status"), expected_agent_urn=URN)
    assert status.viable is True  # Preserve independent Java field; do not infer execution policy.
    assert status.started_at > status.last_activity_at > status.timestamp
    assert "sensitive status" not in repr(status)


def test_do_not_echo_untrusted_values_in_errors():
    secret = "sensitive-principal-token"
    with pytest.raises(ProtocolError) as caught:
        decode_agent_status(payload(agentUrn=secret), expected_agent_urn=URN)
    assert secret not in str(caught.value)


def test_each_feature_coverage_step_declares_its_checkout_working_directory():
    workflow = (ROOT / ".github/workflows/python-client.yml").read_text(encoding="utf-8")
    steps = workflow.split("      - ")
    coverage_steps = [step for step in steps if "run: python tools/check_feature_coverage.py" in step]
    assert len(coverage_steps) == 11
    for step in coverage_steps:
        assert "\n        working-directory: python-client\n" in step
