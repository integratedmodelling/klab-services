"""Mocked AMQP contracts, not real broker ACL or Runtime event acceptance."""
import builtins
from dataclasses import replace
import json
from types import SimpleNamespace as NS

import httpx
import pytest

from klab_client import ActivityNotifications, Client, Context, NotificationBroker
from klab_client import notifications as module
from klab_client.errors import (AuthenticationError, AuthorizationError, ConfigurationError,
                               InvalidRequestError, ProtocolError, TransportError, UnsupportedOperationError)


def broker(**kw):
    return NotificationBroker(**dict(url="amqp://127.0.0.1:18197/%2F", username="fixture-reader",
        password="broker-secret", allow_insecure_loopback=True, **kw))


def envelope(**kw):
    return dict(messageType="ActivityStarted", messageClass="DigitalTwin", queue="Events",
        dispatchId="s.c", payloadClass=module.ACTIVITY_CLASS, id=1, timestamp=0,
        payload={"serviceId": "home", "urn": "activity", "nested": [1, None, False]}, **kw)


class Driver:
    class AuthError(Exception):
        pass

    def __init__(self, deliveries=(), fault=None):
        self.calls = []
        self.deliveries = deliveries
        self.fault = fault
        self.exceptions = NS(ProbableAuthenticationError=self.AuthError)

    def _call(self, name, **kw):
        self.calls.append((name, kw))
        if self.fault and self.fault[0] == name:
            raise self.fault[1]

    def PlainCredentials(self, username, password):
        return (username, password)

    def SSLOptions(self, context, host):
        return context, host

    def ConnectionParameters(self, **kw):
        return kw

    def BlockingConnection(self, parameters):
        self._call("connect", **parameters)
        return self

    def channel(self):
        self._call("channel")
        return self

    def exchange_declare(self, **kw): self._call("exchange", **kw)
    def queue_declare(self, **kw):
        self._call("declare", **kw)
        return NS(method=NS(queue="owned-queue"))
    def queue_bind(self, **kw): self._call("bind", **kw)
    def basic_qos(self, **kw): self._call("qos", **kw)
    def consume(self, queue, **kw):
        self._call("consume", queue=queue, **kw)
        def stream():
            for value in self.deliveries:
                if isinstance(value, BaseException):
                    raise value
                yield (None, None, None) if value is None else (NS(delivery_tag=7), None, value)
        return stream()
    def basic_ack(self, tag): self._call("ack", tag=tag)
    def queue_delete(self, **kw): self._call("delete", **kw)
    def close(self): self._call("close")


@pytest.fixture
def setup(monkeypatch):
    clients = []
    def make(deliveries=(), fault=None, settings=None, **options):
        client = Client("https://runtime.invalid/runtime", "http-secret", runtime_service_id="home",
                        http_transport=httpx.MockTransport(lambda r: pytest.fail("Notifications must not send HTTP")))
        clients.append(client)
        context = Context(client, {"id": "s.c", "serviceId": "home"})
        driver = Driver(deliveries, fault)
        monkeypatch.setattr(module, "_pika", lambda: driver)
        return ActivityNotifications(context, settings or broker(), **options), driver
    yield make
    for client in clients:
        client.close()


def test_owned_queue_manual_ack_timeout_duplicate_and_out_of_order_hints(setup):
    data = envelope(); data["payload"]["secret"] = "never show"
    raw = json.dumps(data).encode()
    notification, driver = setup([None, raw, raw, json.dumps(dict(data, id=0)).encode()])
    with notification as stream:
        assert stream.read() is None and stream.state == "OPEN"
        first, duplicate = stream.read(), stream.read()
        assert first == duplicate and first.message_id == 1
        assert first.payload["nested"] == (1, None, False) and "secret" not in first.payload
        assert "activity" not in repr(first) and "broker-secret" not in repr(stream.broker)
        assert stream.continuity == stream.upstream_overflow == "UNKNOWN"
        with pytest.raises(TypeError): first.payload["new"] = 1
        with pytest.raises(ProtocolError): stream.read()
        assert stream.state == "REJECTED"
    assert stream.state == "CLOSED" and stream.cleanup_confirmed
    assert [name for name, _ in driver.calls].count("ack") == 2
    args = dict(driver.calls)
    assert args["connect"]["connection_attempts"] == 1 and args["connect"]["credentials"] == ("fixture-reader", "broker-secret")
    assert args["exchange"] == {"exchange": "s.c", "exchange_type": "fanout", "passive": True}
    assert args["declare"]["exclusive"] and not args["declare"]["durable"]
    assert args["declare"]["arguments"]["qpid.policy_type"] == "reject"
    assert args["qos"] == {"prefetch_count": 1}
    assert args["consume"]["auto_ack"] is False and args["delete"] == {"queue": "owned-queue"}
    stream.close()
    with pytest.raises(InvalidRequestError): stream.open()
    with pytest.raises(InvalidRequestError): stream.read()


def test_tls_origin_and_virtual_host_are_explicit_without_http_credentials(setup):
    stream, driver = setup(settings=replace(broker(), url="amqps://broker.example/%2F"))
    stream.open(); stream.close()
    params = dict(driver.calls)["connect"]
    assert params["host"] == "broker.example" and params["port"] == 5671 and params["virtual_host"] == "/"
    context, host = params["ssl_options"]
    assert context.check_hostname and host == "broker.example" and context.verify_mode == module.ssl.CERT_REQUIRED


@pytest.mark.parametrize("url", ["amqp://remote.invalid", "http://localhost", "amqp://u:p@localhost", "amqps://u@host",
    "amqps://host?token=x", "amqps://host#private", "amqps://host:0", "amqps://host:90000", "amqps://host:bad",
    "amqps://", "amqps://host/\nprivate", None, 42])
def test_bad_origins_are_rejected_before_io(url):
    with pytest.raises(ConfigurationError): replace(broker(), url=url)


@pytest.mark.parametrize("kw", [{"username": ""}, {"password": ""}, {"password": None}, {"username": "a\nb"},
    {"allow_insecure_loopback": 1}, {"allow_insecure_loopback": False}])
def test_broker_credentials_and_plaintext_opt_in(kw):
    with pytest.raises(ConfigurationError): replace(broker(), **kw)


@pytest.mark.parametrize("kw", [{"timeout": 0}, {"timeout": True}, {"timeout": float("inf")}, {"timeout": 61},
    {"max_events": 0}, {"max_events": True}, {"max_queue_messages": 129}, {"max_message_bytes": 1048577},
    {"max_total_bytes": 16777217}])
def test_receive_budgets_are_explicit_and_bounded(setup, kw):
    with pytest.raises(InvalidRequestError): setup(**kw)


def test_binding_root_identity_and_http_credential_reuse_guards(setup):
    stream, _ = setup()
    with pytest.raises(InvalidRequestError): ActivityNotifications(object(), stream.broker)
    with pytest.raises(InvalidRequestError): ActivityNotifications(stream.context, object())
    with pytest.raises(InvalidRequestError): ActivityNotifications(stream.context.within(1), stream.broker)
    for field in ("url", "username", "password"):
        value = "amqps://host/http-secret" if field == "url" else "http-secret"
        with pytest.raises(ConfigurationError): ActivityNotifications(stream.context, replace(stream.broker, **{field: value}))
    client = Client("https://runtime.invalid")
    try:
        with pytest.raises(ConfigurationError): ActivityNotifications(Context(client, {"id": "s.c"}), stream.broker)
    finally: client.close()
    stream.context.configuration["id"] = "s.other"
    with pytest.raises(ProtocolError): stream.open()
    assert stream.state == "NEW"
    stream.close()


@pytest.mark.parametrize("body", [b"{", b"\xff", b'{"id":1,"id":2}', b"[]", b"null", b'{"a":NaN}'])
def test_invalid_json_is_not_empty_state(setup, body):
    stream, driver = setup([body])
    stream.open()
    with pytest.raises(ProtocolError): stream.read()
    assert stream.state == "REJECTED" and not any(name == "ack" for name, _ in driver.calls)
    stream.close()


@pytest.mark.parametrize("field,value", [("messageType", "Info"), ("messageClass", "Notification"), ("queue", "UI"),
    ("payloadClass", "java.Other"), ("payload", []), ("id", True), ("id", -1), ("timestamp", False), ("timestamp", -1),
    ("dispatchId", "bad token"), ("dispatchId", None), ("dispatchId", "s.other"), ("payload", {"serviceId": "other"})])
def test_wrong_envelope_or_origin_is_not_an_activity(setup, field, value):
    data = envelope(); data[field] = value
    stream, _ = setup([json.dumps(data).encode()]); stream.open()
    with pytest.raises(ProtocolError): stream.read()
    stream.close()


def test_focused_dispatch_activityfinished_redaction_and_no_id_order_assumption(setup):
    data = envelope(); data.update(dispatchId="s.c.3#2", messageType="ActivityFinished", id=7)
    data["payload"]["description"] = "http-secret broker-secret fixture-reader"
    stream, _ = setup([json.dumps(data).encode(), json.dumps(dict(data, id=1)).encode()]); stream.open()
    assert stream.read().payload["description"] == "[REDACTED] [REDACTED] [REDACTED]"
    assert stream.read().message_id == 1
    stream.close()


@pytest.mark.parametrize("kw", [{"max_message_bytes": 1}, {"max_total_bytes": 1}])
def test_oversize_and_cumulative_admission_stop_without_ack(setup, kw):
    stream, driver = setup([json.dumps(envelope()).encode()], **kw); stream.open()
    with pytest.raises(ProtocolError): stream.read()
    assert not any(name == "ack" for name, _ in driver.calls)
    stream.close()


def test_event_budget_is_terminal_and_never_consumes_an_extra_delivery(setup):
    stream, driver = setup([json.dumps(envelope()).encode()], max_events=1); stream.open(); stream.read()
    with pytest.raises(ProtocolError): stream.read()
    assert stream.state == "LIMIT_REACHED" and stream.received == 1
    stream.close()


@pytest.mark.parametrize("stage", ["connect", "channel", "exchange", "declare", "bind", "qos", "consume"])
def test_partial_open_failure_closes_only_owned_resources_without_diagnostics(setup, stage):
    stream, driver = setup(fault=(stage, RuntimeError("broker-secret http-secret")))
    with pytest.raises(TransportError, match="no credential diagnostics") as error: stream.open()
    assert "broker-secret" not in str(error.value) and stream.state == "DISCONNECTED"
    stream.close()


def test_authentication_and_authorization_are_distinct_from_disconnect(setup):
    stream, _ = setup(fault=("connect", Driver.AuthError("secret")))
    with pytest.raises(AuthenticationError): stream.open()
    stream, _ = setup(fault=("bind", type("Denied", (Exception,), {"reply_code": 403})()))
    with pytest.raises(AuthorizationError): stream.open()


@pytest.mark.parametrize("fault", [ConnectionError("secret"), StopIteration()])
def test_connection_loss_and_cancelled_consumer_do_not_auto_reconnect(setup, fault):
    stream, driver = setup([fault]); stream.open()
    with pytest.raises(TransportError): stream.read()
    assert stream.state == "DISCONNECTED" and len([name for name, _ in driver.calls if name == "connect"]) == 1
    stream.close()


def test_cleanup_uncertainty_is_preserved_but_does_not_mask_primary_error(setup):
    for stage in ("delete", "close"):
        stream, driver = setup([b"bad"], fault=(stage, OSError("secret")))
        with pytest.raises(ProtocolError):
            with stream: stream.read()
        assert stream.state == "CLOSED" and stream.cleanup_confirmed is False
        stream.close()
        stream, _ = setup(fault=(stage, OSError("secret"))); stream.open()
        with pytest.raises(TransportError, match="cleanup"): stream.close()


def test_context_drift_after_open_and_after_delivery_is_terminal(setup, monkeypatch):
    stream, driver = setup([json.dumps(envelope()).encode()]); stream.open()
    stream.context.configuration["id"] = "s.other"
    with pytest.raises(ProtocolError): stream.read()
    stream.close()
    stream, driver = setup([json.dumps(envelope()).encode()]); stream.open()
    old = stream._decode
    def drift(body):
        event = old(body); stream.context.configuration["id"] = "s.other"; return event
    monkeypatch.setattr(stream, "_decode", drift)
    with pytest.raises(ProtocolError): stream.read()
    assert not any(name == "ack" for name, _ in driver.calls)
    stream.close()


def test_keyboard_interrupt_releases_owned_connection_and_remains_local(setup):
    stream, _ = setup([KeyboardInterrupt()]); stream.open()
    with pytest.raises(KeyboardInterrupt): stream.read()
    assert stream.state == "INTERRUPTED" and stream.cleanup_confirmed
    stream.close()
    stream, _ = setup(fault=("bind", KeyboardInterrupt()))
    with pytest.raises(KeyboardInterrupt): stream.open()
    assert stream.state == "DISCONNECTED" and stream.cleanup_confirmed


def test_missing_optional_library_is_explicit(monkeypatch):
    original = builtins.__import__
    def missing(name, *args, **kw):
        if name == "pika": raise ImportError()
        return original(name, *args, **kw)
    monkeypatch.setattr(builtins, "__import__", missing)
    with pytest.raises(UnsupportedOperationError, match=r"\[events\]"): module._pika()


def test_optional_library_is_loaded_only_when_requested(monkeypatch):
    import sys
    fake = NS()
    monkeypatch.setitem(sys.modules, "pika", fake)
    assert module._pika() is fake


def test_broker_binding_drift_cannot_forward_replaced_credentials(setup):
    stream, driver = setup(); stream.broker = replace(stream.broker, password="http-secret")
    with pytest.raises(ProtocolError): stream.open()
    assert not driver.calls


def test_broker_revocation_and_clean_exit_failure_remain_visible(setup):
    stream, _ = setup([type("Denied", (Exception,), {"reply_code": 403})()]); stream.open()
    with pytest.raises(AuthorizationError): stream.read()
    assert stream.state == "ACCESS_DENIED"
    stream.close()
    stream, _ = setup(fault=("delete", OSError("private")))
    with pytest.raises(TransportError, match="cleanup"):
        with stream: pass
