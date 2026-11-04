import json
from pathlib import Path

import pytest

from klab_client._agent_wire import agent_exchange, decode_message, encode_command
from klab_client.errors import InvalidRequestError, ProtocolError

FIXTURE = json.loads((Path(__file__).resolve().parents[3] /
    "klab.core.common/src/test/resources/agent-messaging-wire.json").read_bytes())
URN = FIXTURE["agentUrn"]


def test_exchange_uses_the_production_java_name_uuid_algorithm():
    assert agent_exchange(FIXTURE["federationId"], URN) == FIXTURE["exchange"]
    assert agent_exchange("different", URN) != FIXTURE["exchange"]
    assert agent_exchange(FIXTURE["federationId"], "different") != FIXTURE["exchange"]


@pytest.mark.parametrize("federation,urn", [(None, URN), ("", URN), ("test", "a\0b"), ("a b", URN)])
def test_invalid_exchange_identity_rejected(federation, urn):
    with pytest.raises(InvalidRequestError):
        agent_exchange(federation, urn)


@pytest.mark.parametrize("index,action", [(0, "start"), (1, "stop"), (2, "status")])
def test_python_commands_equal_production_java_json(index, action):
    expected = FIXTURE["messages"][index]
    body = encode_command(action, URN, expected["id"], expected["timestamp"])
    assert json.loads(body) == expected
    assert decode_message(body, URN).status is None


@pytest.mark.parametrize("changes", [dict(action=None), dict(action=[]), dict(action="restart"),
    dict(urn=""), dict(message_id=True), dict(message_id=0), dict(message_id=2**63),
    dict(timestamp=-1), dict(timestamp=True), dict(timestamp=2**63)])
def test_bad_command_rejected(changes):
    kwargs = dict(action="start", urn=URN, message_id=1, timestamp=0) | changes
    with pytest.raises(InvalidRequestError):
        encode_command(**kwargs)


@pytest.mark.parametrize("index", [3, 4, 5, 6])
def test_actual_java_status_envelopes_decode(index):
    data = FIXTURE["messages"][index]
    result = decode_message(json.dumps(data).encode(), URN)
    assert result.kind == data["messageType"]
    assert result.status.state == data["payload"]["state"]
    assert result.message_id == data["id"]


@pytest.mark.parametrize("body", [None, "{}", b"\xff", b"{", b"[]", b"x" * 65537,
    b'{"id":1,"id":2}'])
def test_malformed_bytes_rejected(body):
    with pytest.raises(ProtocolError):
        decode_message(body, URN)


@pytest.mark.parametrize("changes", [{"@CLASS": "wrong"}, {"messageClass": "DigitalTwin"},
    {"dispatchId": "wrong"}, {"extra": True}, {"id": 0}, {"id": True}, {"id": 2**63},
    {"timestamp": -1}, {"inResponseTo": None}, {"activityTransientId": 0.5},
    {"messageType": []}, {"messageType": "Unknown"}, {"queue": "Status"},
    {"payloadClass": "wrong"}, {"payload": None}])
def test_bad_command_envelope_rejected(changes):
    data = FIXTURE["messages"][0] | changes
    with pytest.raises(ProtocolError):
        decode_message(json.dumps(data).encode(), URN)


@pytest.mark.parametrize("change", ["missing", "payload_class", "queue", "nan", "surrogate", "urn", "state"])
def test_bad_status_envelope_or_payload_rejected(change):
    data = json.loads(json.dumps(FIXTURE["messages"][3]))
    if change == "missing":
        del data["timestamp"]
    elif change == "payload_class":
        data["payloadClass"] = "wrong"
    elif change == "queue":
        data["queue"] = "Status"
    elif change == "nan":
        data["payload"]["timestamp"] = float("nan")
    elif change == "surrogate":
        data["payload"]["detail"] = "\ud800"
    elif change == "urn":
        data["payload"]["agentUrn"] = "wrong"
    else:
        data["payload"]["state"] = "READY"
    with pytest.raises(ProtocolError):
        decode_message(json.dumps(data).encode(), URN)


@pytest.mark.parametrize("index", [4, 6])
def test_other_terminal_envelope_state_mismatch_rejected(index):
    data = json.loads(json.dumps(FIXTURE["messages"][index]))
    data["payload"]["state"] = "RUNNING"
    with pytest.raises(ProtocolError):
        decode_message(json.dumps(data).encode(), URN)


def test_agent_stopped_can_carry_failed_terminal_state_like_java_runtime():
    data = json.loads(json.dumps(FIXTURE["messages"][4]))
    data["payload"]["state"] = "FAILED"
    assert decode_message(json.dumps(data).encode(), URN).status.state == "FAILED"
