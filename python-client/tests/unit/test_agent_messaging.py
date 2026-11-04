from collections import deque
from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace
import json
from types import SimpleNamespace as NS

import pytest

from klab_client.agent_control import AgentControlBinding
from klab_client.agent_messaging import AgentChannel
from klab_client.errors import (AuthenticationError, AuthorizationError, InvalidRequestError,
                                ProtocolError, TransportError, UnsupportedOperationError)
from klab_client.notifications import NotificationBroker
from test_agent_wire import FIXTURE, URN


class FakeLoop:
    def __init__(self):
        self.events, self.timers = deque(), {}
        self.stopped = self.closed = False
        self.counter = 0
        self.close_error = None

    def call_later(self, timeout, callback):
        self.counter += 1
        self.timers[self.counter] = callback
        return self.counter

    def remove_timeout(self, token):
        self.timers.pop(token, None)

    def start(self):
        self.stopped = False
        while not self.stopped:
            callback = self.events.popleft() if self.events else next(iter(self.timers.values()))
            callback()

    def stop(self):
        self.stopped = True

    def close(self):
        self.closed = True
        if self.close_error:
            raise self.close_error


class FakeChannel:
    def __init__(self, connection):
        self.connection = connection
        self.is_open = True
        self.sent, self.acks, self.deleted, self.incoming = [], [], [], deque()
        self.inflight = False
        self.publish_mode = "ack"
        self.delete_error = self.publish_error = self.ack_error = None

    def add_on_close_callback(self, callback):
        self.closed_callback = callback

    def add_on_return_callback(self, callback):
        self.return_callback = callback

    def exchange_declare(self, **kwargs):
        assert kwargs["passive"] is True and kwargs["exchange"] == FIXTURE["exchange"]
        assert kwargs["auto_delete"] is True and kwargs["durable"] is False
        kwargs["callback"](None)

    def queue_declare(self, **kwargs):
        assert kwargs["exclusive"] and kwargs["auto_delete"] and not kwargs["durable"]
        self.queue_options = kwargs
        kwargs["callback"](NS(method=NS(queue="owned-queue")))

    def queue_bind(self, **kwargs):
        kwargs["callback"](None)

    def basic_qos(self, **kwargs):
        assert kwargs["prefetch_count"] == 1
        kwargs["callback"](None)

    def confirm_delivery(self, ack_nack_callback, **kwargs):
        self.confirm_callback = ack_nack_callback
        kwargs["callback"](None)

    def basic_consume(self, **kwargs):
        assert kwargs["auto_ack"] is False
        self.message_callback = kwargs["on_message_callback"]
        kwargs["callback"](None)

    def deliver(self, body, properties=None):
        self.incoming.append((body, properties or NS(headers={})))
        self.schedule()

    def schedule(self):
        if self.incoming and not self.inflight:
            self.inflight = True
            body, properties = self.incoming.popleft()
            self.connection.ioloop.events.append(lambda: self.message_callback(
                self, NS(delivery_tag=1), properties, body))

    def basic_publish(self, exchange, key, body, **kwargs):
        if self.publish_error:
            raise self.publish_error
        self.sent.append(json.loads(body))
        assert kwargs["mandatory"] is True
        self.deliver(body, kwargs["properties"])
        if self.publish_mode == "return":
            self.connection.ioloop.events.append(lambda: self.return_callback(self, None, None, body))
        if self.publish_mode != "timeout" and hasattr(self, "confirm_callback"):
            name = "Basic.Nack" if self.publish_mode == "nack" else "Basic.Ack"
            tag = len(self.sent)
            self.connection.ioloop.events.append(lambda: self.confirm_callback(
                NS(method=NS(NAME=name, delivery_tag=tag, multiple=False))))

    def basic_ack(self, tag):
        if self.ack_error:
            raise self.ack_error
        self.acks.append(tag)
        self.inflight = False
        self.schedule()

    def queue_delete(self, **kwargs):
        if self.delete_error:
            raise self.delete_error
        self.deleted.append(kwargs["queue"])
        self.connection.ioloop.events.append(lambda: kwargs["callback"](None))


class FakeConnection:
    def __init__(self, parameters, *, on_open_callback, on_open_error_callback, on_close_callback):
        self.ioloop, self.parameters = FakeLoop(), parameters
        self.is_closed = self.is_closing = False
        self.publisher_confirms = self.basic_nack = True
        self.channel_object = FakeChannel(self)
        self.on_close = on_close_callback
        self.close_mode = "normal"
        self.aborted = 0
        self.ioloop.events.append(lambda: on_open_callback(self))

    def channel(self, on_open_callback):
        on_open_callback(self.channel_object)

    def close(self):
        self.is_closing = True
        if self.close_mode == "stall":
            return
        self.ioloop.events.append(self.closed)

    def closed(self):
        self.is_closed = True
        self.on_close(self, NS(reply_code=200))

    def _adapter_disconnect_stream(self):
        self.aborted += 1
        self.ioloop.events.append(self.closed)


class FakeAuthentication(Exception):
    pass


class FakeAccessDenied(Exception):
    pass


@pytest.fixture
def environment(monkeypatch):
    connections = []
    def connect(*args, **kwargs):
        value = FakeConnection(*args, **kwargs)
        connections.append(value)
        return value
    pika = NS(SelectConnection=connect, ConnectionParameters=lambda **kw: NS(**kw),
        PlainCredentials=lambda u, p: NS(username=u, password=p), BasicProperties=lambda **kw: NS(**kw),
        SSLOptions=lambda *args: NS(args=args), exceptions=NS(ProbableAuthenticationError=FakeAuthentication,
                                                           ProbableAccessDeniedError=FakeAccessDenied))
    monkeypatch.setattr("klab_client.agent_messaging._pika", lambda: pika)
    ref = AgentControlBinding(URN, "https://runtime.example", "runtime-one", "s.a", "principal", "test-incarnation")
    broker = NotificationBroker("amqp://127.0.0.1/s.a", "agent-reader", "private-generated-test-password",
                                allow_insecure_loopback=True)
    return ref, broker, pika, connections


def make(environment, **kwargs):
    ref, broker, _, _ = environment
    return AgentChannel(ref, broker, federation_id=FIXTURE["federationId"], **kwargs)


def test_real_connection_parameters_and_one_shot_commands(environment):
    ref, _, _, connections = environment
    channel = make(environment)
    with channel:
        assert channel.start(binding=ref).start_outcome == "PUBLISHED"
        assert channel.read(binding=ref) is None  # Own command echo, not status.
        assert channel.request_status(binding=ref) is True
        assert channel.read(binding=ref) is None
        assert channel.stop(binding=ref).stop_outcome == "PUBLISHED"
        with pytest.raises(InvalidRequestError):
            channel.stop(binding=ref)
        conn = connections[0]
        assert conn.parameters.connection_attempts == 1
        assert conn.parameters.virtual_host == "s.a"
        assert conn.parameters.ssl_options is None
    assert channel.local_cleanup_confirmed and conn.is_closed and conn.ioloop.closed
    assert conn.channel_object.deleted == ["owned-queue"]
    assert channel.view.remote_cleanup == "UNKNOWN"
    channel.close()
    with pytest.raises(InvalidRequestError):
        channel.open()


def test_status_envelope_read_updates_report_without_cleanup_claim(environment):
    ref, _, _, connections = environment
    channel = make(environment).open()
    frame = json.loads(json.dumps(FIXTURE["messages"][3]))
    frame["payload"]["detail"] = "agent-reader private-generated-test-password"
    connections[0].channel_object.deliver(json.dumps(frame).encode())
    event = channel.read(binding=ref)
    assert event.status.detail == "[REDACTED] [REDACTED]"
    assert channel.view.last_report.state == "RUNNING"
    assert channel.view.remote_cleanup == "UNKNOWN"
    channel.close()


@pytest.mark.parametrize("mode", ["nack", "return"])
def test_negative_confirm_never_allows_mutation_replay(environment, mode):
    ref, _, _, connections = environment
    channel = make(environment).open()
    connections[0].channel_object.publish_mode = mode
    assert channel.start(binding=ref).start_outcome == "UNCONFIRMED"
    with pytest.raises(InvalidRequestError):
        channel.start(binding=ref)
    channel.close()


def test_confirm_deadline_leaves_unknown_and_closes_owned_transport(environment):
    ref, _, _, connections = environment
    channel = make(environment).open()
    connections[0].channel_object.publish_mode = "timeout"
    with pytest.raises(TransportError):
        channel.start(binding=ref)
    assert channel.view.start_outcome == "UNKNOWN" and channel.state == "DISCONNECTED"
    assert connections[0].is_closed
    with pytest.raises(InvalidRequestError):
        channel.start(binding=ref)


def test_read_timeout_is_loss_not_unchanged_state(environment):
    ref, _, _, connections = environment
    channel = make(environment).open()
    with pytest.raises(TransportError):
        channel.read(binding=ref)
    assert channel.state == "DISCONNECTED" and connections[0].is_closed


def test_wrong_binding_and_wrong_thread_cannot_send(environment):
    ref, _, _, connections = environment
    channel = make(environment).open()
    with pytest.raises(ProtocolError):
        channel.start(binding=replace(ref, runtime_incarnation="other"))
    with ThreadPoolExecutor(max_workers=1) as pool:
        with pytest.raises(InvalidRequestError):
            pool.submit(channel.start, binding=ref).result(timeout=2)
    assert connections[0].channel_object.sent == []
    channel.close()


@pytest.mark.parametrize("kwargs", [dict(timeout=True), dict(timeout=0), dict(timeout=61), dict(timeout=float("nan")),
    dict(max_events=0), dict(max_total_bytes=0)])
def test_constructor_budgets(environment, kwargs):
    with pytest.raises(InvalidRequestError):
        make(environment, **kwargs)


def test_requires_explicit_binding_and_broker(environment):
    ref, broker, _, _ = environment
    with pytest.raises(InvalidRequestError):
        AgentChannel(None, broker, federation_id="test")
    with pytest.raises(InvalidRequestError):
        AgentChannel(ref, None, federation_id="test")


def test_tls_configuration_uses_verified_context(environment):
    ref, _, _, connections = environment
    broker = NotificationBroker("amqps://broker.example/s.a", "reader", "secret-password")
    channel = AgentChannel(ref, broker, federation_id=FIXTURE["federationId"]).open()
    assert connections[0].parameters.port == 5671
    assert connections[0].parameters.ssl_options.args[1] == "broker.example"
    channel.close()


@pytest.mark.parametrize("body", [b"{", b"x" * 65537, "not-bytes"])
def test_malformed_delivery_fails_closed(environment, body):
    ref, _, _, connections = environment
    channel = make(environment).open()
    connections[0].channel_object.deliver(body)
    with pytest.raises(ProtocolError):
        channel.read(binding=ref)
    assert connections[0].is_closed


def test_event_and_byte_budgets_include_command_echoes(environment):
    ref, _, _, _ = environment
    channel = make(environment, max_events=1).open()
    channel.request_status(binding=ref)
    assert channel.read(binding=ref) is None
    with pytest.raises(ProtocolError):
        channel.read(binding=ref)


def test_cumulative_byte_budget_exhaustion(environment):
    ref, _, _, connections = environment
    body = json.dumps(FIXTURE["messages"][3]).encode()
    channel = make(environment, max_total_bytes=len(body)).open()
    connections[0].channel_object.deliver(body)
    channel.read(binding=ref)
    connections[0].channel_object.deliver(body)
    with pytest.raises(ProtocolError):
        channel.read(binding=ref)


def test_status_request_publication_budget(environment):
    ref, _, _, _ = environment
    channel = make(environment).open()
    for _ in range(16):
        channel.request_status(binding=ref)
        channel.read(binding=ref)
    with pytest.raises(InvalidRequestError):
        channel.request_status(binding=ref)
    channel.close()


def test_cleanup_failure_preserved_but_connection_still_closed(environment):
    _, _, _, connections = environment
    channel = make(environment).open()
    conn = connections[0]
    conn.channel_object.delete_error = RuntimeError("private-generated-test-password")
    with pytest.raises(TransportError) as caught:
        channel.close()
    assert "private-generated" not in str(caught.value)
    assert conn.is_closed and channel.local_cleanup_confirmed is False
    with pytest.raises(TransportError):
        channel.close()


def test_stalled_graceful_close_uses_bounded_abort_and_does_not_invent_cleanup(environment):
    _, _, _, connections = environment
    channel = make(environment).open()
    conn = connections[0]
    conn.close_mode = "stall"
    with pytest.raises(TransportError):
        channel.close()
    assert conn.aborted == 1 and conn.is_closed and not channel.local_cleanup_confirmed


def test_optional_dependency_failure_is_explicit_no_connection(environment, monkeypatch):
    def absent():
        raise UnsupportedOperationError("Install events extra")
    monkeypatch.setattr("klab_client.agent_messaging._pika", absent)
    channel = make(environment)
    with pytest.raises(UnsupportedOperationError):
        channel.open()
    assert not environment[3] and channel.state == "DISCONNECTED"


@pytest.mark.parametrize("url", ["amqps://broker.example", "amqps://broker.example/"])
def test_explicit_vhost_required(environment, url):
    ref = environment[0]
    with pytest.raises(InvalidRequestError):
        AgentChannel(ref, NotificationBroker(url, "user", "secret"), federation_id="test")


@pytest.mark.parametrize("reason,expected", [(NS(reply_code=403), AuthorizationError),
    (NS(reply_code=530), AuthorizationError), (FakeAuthentication(), AuthenticationError),
    (FakeAccessDenied(), AuthorizationError),
    (NS(exceptions=[NS(exception=FakeAuthentication())]), AuthenticationError),
    (RuntimeError("private-generated-test-password"), TransportError)])
def test_broker_setup_errors_are_typed_and_sanitized(environment, reason, expected):
    _, _, pika, connections = environment
    original = pika.SelectConnection
    def failed(*args, **kwargs):
        conn = original(*args, **kwargs)
        conn.ioloop.events.clear()
        conn.is_closed = True
        conn.ioloop.events.append(lambda: kwargs["on_open_error_callback"](conn, reason))
        return conn
    pika.SelectConnection = failed
    channel = make(environment)
    with pytest.raises(expected) as caught:
        channel.open()
    assert "private-generated" not in str(caught.value)
    assert connections[0].ioloop.closed and channel.local_cleanup_confirmed


def test_bounded_error_unwrapping_does_not_loop_on_cyclic_error(environment):
    channel = make(environment)
    error = RuntimeError("private-generated-test-password")
    error.exception = error
    channel._failed(error)
    assert channel.state == "DISCONNECTED"
    channel.close()


def test_unexpected_constructor_error_sanitized(environment):
    pika = environment[2]
    def broken(*args, **kwargs):
        raise RuntimeError("private-generated-test-password")
    pika.SelectConnection = broken
    with pytest.raises(TransportError) as caught:
        make(environment).open()
    assert "private-generated" not in str(caught.value)


@pytest.mark.parametrize("operation", ["open", "send", "read", "close"])
def test_interruptions_preserve_one_shot_and_cleanup_state(environment, operation):
    ref, _, pika, connections = environment
    channel = make(environment)
    if operation == "open":
        original = pika.SelectConnection
        def interrupt(*args, **kwargs):
            conn = original(*args, **kwargs)
            conn.ioloop.events.clear()
            def raise_interrupt():
                raise KeyboardInterrupt()
            conn.ioloop.events.append(raise_interrupt)
            return conn
        pika.SelectConnection = interrupt
        with pytest.raises(KeyboardInterrupt):
            channel.open()
    else:
        channel.open()
        conn = connections[0]
        if operation == "send":
            conn.channel_object.publish_error = KeyboardInterrupt()
            with pytest.raises(KeyboardInterrupt):
                channel.start(binding=ref)
            assert channel.view.start_outcome == "UNKNOWN"
        elif operation == "read":
            conn.channel_object.deliver(json.dumps(FIXTURE["messages"][3]).encode())
            conn.channel_object.ack_error = KeyboardInterrupt()
            with pytest.raises(KeyboardInterrupt):
                channel.read(binding=ref)
        else:
            conn.channel_object.delete_error = KeyboardInterrupt()
            with pytest.raises(KeyboardInterrupt):
                channel.close()
    assert connections[0].is_closed and connections[0].ioloop.closed


def test_unexpected_publish_and_ack_errors_sanitized(environment):
    ref, _, _, connections = environment
    channel = make(environment).open()
    connections[0].channel_object.publish_error = RuntimeError("private-generated-test-password")
    with pytest.raises(TransportError) as caught:
        channel.start(binding=ref)
    assert "private-generated" not in str(caught.value)
    assert channel.view.start_outcome == "UNKNOWN"
    other = make(environment).open()
    connections[1].channel_object.deliver(json.dumps(FIXTURE["messages"][3]).encode())
    connections[1].channel_object.ack_error = RuntimeError("private-generated-test-password")
    with pytest.raises(TransportError) as caught:
        other.read(binding=ref)
    assert "private-generated" not in str(caught.value)


def test_bad_duplicate_delivery_and_confirmation_tags(environment):
    channel = make(environment).open()
    conn = environment[3][0]
    channel._sequence = 2
    channel._confirmation(NS(method=NS(delivery_tag=1, multiple=False, NAME="Basic.Ack")))
    assert channel._confirmed is None
    channel._confirmation(NS(method=NS(delivery_tag=0, multiple=True, NAME="Basic.Ack")))
    assert channel._confirmed is True
    channel._confirmation(NS(method=NS(delivery_tag=4, multiple=True, NAME="Basic.Ack")))
    channel._message(conn.channel_object, NS(delivery_tag=1), None, b"{}")
    channel._message(conn.channel_object, NS(delivery_tag=2), None, b"{}")
    assert channel.state == "DISCONNECTED"
    channel.close()


def test_closing_discards_queued_delivery_without_status_inference(environment):
    channel = make(environment).open()
    environment[3][0].channel_object.deliver(json.dumps(FIXTURE["messages"][3]).encode())
    channel.close()
    assert channel.view.last_report is None and channel.local_cleanup_confirmed


def test_own_status_echo_and_absent_header_are_not_principal_proof(environment):
    ref = environment[0]
    channel = make(environment).open()
    native = environment[3][0].channel_object
    body = json.dumps(FIXTURE["messages"][3]).encode()
    native.deliver(body, NS(headers={"channelId": channel._channel_id}))
    assert channel.read(binding=ref) is None and channel.view.last_report is None
    native.deliver(body, NS(headers=None))
    assert channel.read(binding=ref).status.state == "RUNNING"
    assert channel.view.remote_cleanup == "UNKNOWN"
    channel.close()


def test_unexpected_channel_close_and_close_while_cleanup(environment):
    channel = make(environment).open()
    conn = environment[3][0]
    conn.channel_object.is_open = False
    channel._channel_closed(conn.channel_object, NS(reply_code=403))
    assert channel.state == "DISCONNECTED"
    channel._closing = True
    channel._channel_closed(conn.channel_object, NS(reply_code=200))
    with pytest.raises(TransportError):
        channel.close()
    # Connection may already be closing; never resend the close request.
    assert conn.is_closed


def test_unexpected_connection_close_and_already_closed_channel_do_not_confirm_queue_cleanup(environment):
    channel = make(environment).open()
    conn = environment[3][0]
    conn.is_closed = True
    channel._connection_closed(conn, RuntimeError("network failure"))
    assert channel.state == "DISCONNECTED"
    channel._closing = True
    channel._channel_closed(conn.channel_object, None)
    with pytest.raises(TransportError):
        channel.close()


def test_cleanup_failure_after_external_close_and_failed_abort_remains_unknown(environment, monkeypatch):
    channel = make(environment).open()
    conn = environment[3][0]
    def fail_after_close(**_):
        conn.closed()
        raise RuntimeError("queue deletion unknown")
    monkeypatch.setattr(conn.channel_object, "queue_delete", fail_after_close)
    with pytest.raises(TransportError):
        channel.close()
    other = make(environment).open()
    second = environment[3][1]
    second.close_mode = "stall"
    def failed_abort():
        raise RuntimeError("abort failed")
    monkeypatch.setattr(second, "_adapter_disconnect_stream", failed_abort)
    with pytest.raises(TransportError):
        other.close()
    assert other.local_cleanup_confirmed is False


def test_ioloop_close_failure_and_context_error_do_not_claim_cleanup(environment):
    channel = make(environment).open()
    environment[3][0].ioloop.close_error = RuntimeError("secret")
    with pytest.raises(TransportError):
        channel.__exit__(None, None, None)
    assert not channel.local_cleanup_confirmed
    other = make(environment).open()
    environment[3][1].channel_object.delete_error = RuntimeError("secret")
    other.__exit__(ValueError, ValueError("original"), None)
    assert not other.local_cleanup_confirmed


def test_close_without_open_owns_no_resources(environment):
    channel = make(environment)
    channel.close()
    assert channel.local_cleanup_confirmed


def test_secondary_stream_loss_preserves_first_broker_denial_diagnosis(environment):
    channel = make(environment)
    channel._failed(NS(reply_code=403))
    first = channel._failure
    channel._failed(RuntimeError("secondary stream failure"))
    assert channel._failure is first and first.broker_reply_code == 403
    assert isinstance(first, AuthorizationError)
    channel.close()


def test_required_confirms_rejects_missing_capabilities_without_downgrade(environment):
    pika = environment[2]
    original = pika.SelectConnection
    def old_broker(*args, **kwargs):
        connection = original(*args, **kwargs)
        connection.basic_nack = False
        return connection
    pika.SelectConnection = old_broker
    channel = make(environment)
    with pytest.raises(UnsupportedOperationError):
        channel.open()
    assert not environment[3][0].channel_object.sent
    assert channel.broker_features == {"publisher_confirms": True, "basic_nack": False}
    assert channel.local_cleanup_confirmed


def test_explicit_unconfirmed_mode_never_reports_published_or_retries(environment):
    ref = environment[0]
    channel = make(environment, publication="unconfirmed").open()
    environment[3][0].channel_object.publish_mode = "timeout"
    assert channel.start(binding=ref).start_outcome == "UNCONFIRMED"
    with pytest.raises(InvalidRequestError):
        channel.start(binding=ref)
    assert channel.read(binding=ref) is None
    channel.close()


def test_bad_publication_mode_rejected(environment):
    with pytest.raises(InvalidRequestError):
        make(environment, publication="auto-downgrade")
    assert make(environment).broker_features is None
