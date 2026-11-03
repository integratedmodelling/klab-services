"""Caller-driven native AMQP lifecycle transport; no reconnect or mutation replay."""
from dataclasses import replace
import json
import math
import ssl
import threading
import time
from urllib.parse import unquote, urlsplit
import uuid

from ._agent_wire import agent_exchange, decode_message, encode_command
from .agent_control import AgentControlBinding, AgentControlJournal
from .errors import (AuthenticationError, AuthorizationError, InvalidRequestError,
                     ProtocolError, TransportError, UnsupportedOperationError)
from .notifications import NotificationBroker, _pika


class AgentChannel:
    """One explicit operator-provisioned binding, one exclusive bounded queue.

    Uses SelectConnection with a bounded caller-driven I/O loop, not blocking
    publish-confirm waits, background workers or a hidden asyncio loop. No agent,
    scope or exchange is created/deleted. Only the private queue is owned locally.
    Broker confirmation is NOT execution/termination or cleanup confirmation.
    Binding incarnation/principal are caller assertions: existing wire snapshots
    contain neither an authenticated Runtime incarnation nor a principal proof.
    """
    def __init__(self, binding, broker, *, federation_id, timeout=5,
                 max_events=128, max_total_bytes=1024 * 1024, publication="required_confirms"):
        if not isinstance(binding, AgentControlBinding) or not isinstance(broker, NotificationBroker):
            raise InvalidRequestError("Explicit agent binding and separate broker configuration required")
        if type(timeout) not in (int, float) or not math.isfinite(timeout) or not 0 < timeout <= 60:
            raise InvalidRequestError("Agent channel timeout must be finite in (0,60] seconds")
        if not urlsplit(broker.url).path.startswith("/") or len(urlsplit(broker.url).path) < 2:
            raise InvalidRequestError("Agent broker requires an explicit nonempty encoded virtual host")
        if publication not in ("required_confirms", "unconfirmed"):
            raise InvalidRequestError("Choose required_confirms or explicitly opt in to unconfirmed publication")
        self._publication = publication
        self._broker_features = None
        self._exchange = agent_exchange(federation_id, binding.agent_urn)
        self._journal = AgentControlJournal(binding, max_reports=max_events, max_total_bytes=max_total_bytes)
        self._binding, self._broker, self._timeout = binding, broker, timeout
        self._max_events, self._max_bytes = max_events, max_total_bytes
        self._owner = threading.get_ident()
        self._state = "NEW"
        self._connection = self._channel = self._queue = self._delivery = None
        self._predicate = None
        self._ignore_failure = self._closing = self._closed = False
        self._queue_requested = self._queue_deleted = False
        self._local_cleanup = None
        self._failure = None
        self._transport_phase = "connect"
        self._events = self._bytes = self._sequence = self._status_requests = 0
        self._confirmed = None
        self._returned = False
        self._channel_id = str(uuid.uuid4())

    @property
    def view(self):
        return self._journal.view

    @property
    def state(self):
        return self._state

    @property
    def local_cleanup_confirmed(self):
        """Only owned queue deletion and connection close, never remote agent cleanup."""
        return self._local_cleanup

    @property
    def broker_features(self):
        return None if self._broker_features is None else dict(self._broker_features)

    def _thread(self):
        if threading.get_ident() != self._owner:
            raise InvalidRequestError("AgentChannel is caller-driven and thread-affine")

    def _ready(self, binding):
        self._thread()
        if binding != self._binding:
            raise ProtocolError("Agent transport binding changed; no operation sent")
        if self._state != "OPEN":
            raise InvalidRequestError("Open a new explicitly configured channel; no reconnect/reset")

    def _signal(self):
        if self._predicate is not None and ((self._failure is not None and not self._ignore_failure)
                                            or self._predicate()):
            self._connection.ioloop.stop()

    def _drive(self, predicate, *, cleanup=False):
        self._predicate, self._ignore_failure = predicate, cleanup
        expired = []
        def deadline():
            expired.append(True)
            self._connection.ioloop.stop()
        loop = self._connection.ioloop
        alarm = loop.call_later(self._timeout, deadline)
        try:
            if not predicate():
                loop.start()
            if self._failure is not None and not cleanup:
                raise self._failure
            if expired or not predicate():
                raise TransportError("Agent transport deadline expired; outcome/continuity unknown, no retry")
        finally:
            loop.remove_timeout(alarm)
            self._predicate = None

    def _failed(self, reason):
        if self._failure is not None:
            self._signal()
            return
        # SelectConnection may wrap the last handshake error in a workflow error.
        # Unwrap boundedly without printing exception text or broker credentials.
        for _ in range(4):
            failures = getattr(reason, "exceptions", ())
            nested = getattr(reason, "exception", None)
            if isinstance(failures, (list, tuple)) and failures:
                reason = failures[-1]
            elif isinstance(nested, BaseException):
                reason = nested
            else:
                break
        code = getattr(reason, "reply_code", None)
        if isinstance(reason, (AuthenticationError, AuthorizationError, TransportError)):
            self._failure = reason
        elif code in (403, 530) or isinstance(reason, _pika().exceptions.ProbableAccessDeniedError):
            self._failure = AuthorizationError("Broker denied agent access; no retry")
        elif isinstance(reason, _pika().exceptions.ProbableAuthenticationError):
            self._failure = AuthenticationError("Agent broker authentication failed; no retry")
        else:
            self._failure = TransportError("Agent transport interrupted; outcome/continuity unknown, no retry")
        self._failure.broker_reply_code = code if type(code) is int else None
        self._failure.broker_error_type = type(reason).__name__
        self._failure.broker_transport_phase = self._transport_phase
        self._journal.transport_lost()
        self._state = "DISCONNECTED"
        self._signal()

    def _connection_closed(self, connection, reason):
        self._closed = True
        if not self._closing:
            self._failed(reason)
        self._signal()

    def _channel_closed(self, channel, reason):
        if not self._closing:
            self._failed(reason)
        elif not self._connection.is_closed and not self._connection.is_closing:
            self._connection.close()
        self._signal()

    def _opened(self, connection):
        self._transport_phase = "channel"
        self._broker_features = {"publisher_confirms": bool(connection.publisher_confirms),
                                 "basic_nack": bool(connection.basic_nack)}
        connection.channel(on_open_callback=self._channel_opened)

    def _channel_opened(self, channel):
        self._transport_phase = "exchange"
        self._channel = channel
        channel.add_on_close_callback(self._channel_closed)
        channel.add_on_return_callback(self._return)
        channel.exchange_declare(exchange=self._exchange, exchange_type="fanout", passive=True,
                                 durable=False, auto_delete=True,
                                 callback=self._exchange_ready)

    def _exchange_ready(self, frame):
        self._transport_phase = "queue"
        self._queue_requested = True
        self._channel.queue_declare(queue="", durable=False, exclusive=True, auto_delete=True,
            arguments={"qpid.max_count": self._max_events, "qpid.max_size": self._max_bytes,
                       "qpid.policy_type": "reject"}, callback=self._queue_ready)

    def _queue_ready(self, frame):
        self._transport_phase = "bind"
        self._queue = frame.method.queue
        self._channel.queue_bind(queue=self._queue, exchange=self._exchange, routing_key="",
                                 callback=self._bound)

    def _bound(self, frame):
        self._transport_phase = "qos"
        self._channel.basic_qos(prefetch_count=1, callback=self._qos_ready)

    def _qos_ready(self, frame):
        self._transport_phase = "confirm"
        if self._publication == "unconfirmed":
            self._confirm_ready(None)
            return
        if not all(self._broker_features.values()):
            self._failure = UnsupportedOperationError("Broker does not advertise the confirm/nack capabilities required by Pika; no silent downgrade")
            self._journal.transport_lost()
            self._state = "DISCONNECTED"
            self._signal()
            return
        self._channel.confirm_delivery(self._confirmation, callback=self._confirm_ready)

    def _confirm_ready(self, frame):
        self._transport_phase = "consume"
        self._channel.basic_consume(queue=self._queue, on_message_callback=self._message,
                                    auto_ack=False, callback=self._consuming)

    def _consuming(self, frame):
        self._state = "OPEN"
        self._signal()

    def _message(self, channel, method, properties, body):
        if self._closing:
            return
        if self._delivery is not None:
            self._failed(None)
        else:
            self._delivery = (method, properties, body)
        self._signal()

    def _return(self, channel, method, properties, body):
        self._returned = True

    def _confirmation(self, frame):
        method = frame.method
        if method.delivery_tag == self._sequence or (method.multiple and
                (method.delivery_tag == 0 or method.delivery_tag >= self._sequence)):
            self._confirmed = method.NAME == "Basic.Ack" and not self._returned
        self._signal()

    def open(self):
        self._thread()
        if self._state != "NEW":
            raise InvalidRequestError("Agent channel cannot reopen; never replay an ambiguous mutation")
        self._state = "CONNECTING"
        try:
            pika = _pika()
            parsed = urlsplit(self._broker.url)
            options = None if parsed.scheme == "amqp" else pika.SSLOptions(ssl.create_default_context(), parsed.hostname)
            parameters = pika.ConnectionParameters(host=parsed.hostname, port=parsed.port or (5671 if options else 5672),
                virtual_host=unquote(parsed.path[1:]), credentials=pika.PlainCredentials(self._broker.username, self._broker.password),
                ssl_options=options, connection_attempts=1, retry_delay=0, heartbeat=30,
                socket_timeout=self._timeout, stack_timeout=self._timeout + 1,
                blocked_connection_timeout=self._timeout, channel_max=1)
            self._connection = pika.SelectConnection(parameters, on_open_callback=self._opened,
                on_open_error_callback=lambda connection, error: self._failed(error),
                on_close_callback=self._connection_closed)
            self._drive(lambda: self._state == "OPEN")
            return self
        except Exception as error:
            if not isinstance(error, (TransportError, AuthenticationError, AuthorizationError, UnsupportedOperationError)):
                self._failed(error)
            self._lost()
            raise self._failure or error from None
        except BaseException:
            self._lost()
            raise

    def _send(self, binding, action):
        self._ready(binding)
        self._sequence += 1
        self._confirmed, self._returned = None, False
        try:
            pika = _pika()
            body = encode_command(action, binding.agent_urn, self._sequence, int(time.time() * 1000))
            properties = pika.BasicProperties(headers={"channelId": self._channel_id}, delivery_mode=1, content_type="text/plain")
            self._channel.basic_publish(self._exchange, "", body, properties=properties, mandatory=True)
            if self._publication == "unconfirmed":
                # Drive a bounded local I/O turn, but NEVER call this broker ACK,
                # successful execution, or permission to replay the publication.
                pumped = []
                def turn():
                    pumped.append(True)
                    self._signal()
                token = self._connection.ioloop.call_later(min(.01, self._timeout / 2), turn)
                try:
                    self._drive(lambda: bool(pumped))
                finally:
                    self._connection.ioloop.remove_timeout(token)
                return False
            self._drive(lambda: self._confirmed is not None)
            return self._confirmed
        except Exception as error:
            self._failed(error)
            self._lost()
            raise self._failure from None
        except BaseException:
            self._lost()
            raise

    def start(self, *, binding):
        self._ready(binding)
        return self._journal.attempt("start", binding=binding, sender=self._send)

    def stop(self, *, binding):
        self._ready(binding)
        return self._journal.attempt("stop", binding=binding, sender=self._send)

    def request_status(self, *, binding):
        self._ready(binding)
        if self._status_requests >= 16:
            raise InvalidRequestError("Explicit status publication budget exhausted")
        self._status_requests += 1
        return self._send(binding, "status")

    def read(self, *, binding):
        self._ready(binding)
        try:
            if self._events >= self._max_events:
                raise ProtocolError("Agent delivery budget exhausted; continuity unknown")
            # A read timeout is not unchanged state. Close rather than infer continuity.
            self._drive(lambda: self._delivery is not None)
            method, properties, body = self._delivery
            self._delivery = None
            if not isinstance(body, bytes) or len(body) > 65536 or self._bytes + len(body) > self._max_bytes:
                raise ProtocolError("Agent delivery byte budget exhausted")
            self._events += 1
            self._bytes += len(body)
            event = decode_message(body, self._binding.agent_urn)
            headers = getattr(properties, "headers", None) or {}
            own = headers.get("channelId") == self._channel_id
            if event.status is not None and not own:
                detail = event.status.detail
                if detail:
                    detail = detail.replace(self._broker.password, "[REDACTED]").replace(self._broker.username, "[REDACTED]")
                status = replace(event.status, detail=detail)
                payload = {"@CLASS": "org.integratedmodelling.klab.api.actors.RuntimeAgent$Status",
                    "agentUrn": status.agent_urn, "state": status.state, "viable": status.viable,
                    "detail": status.detail, "timestamp": status.timestamp, "observationId": status.observation_id,
                    "startedAt": status.started_at, "lastActivityAt": status.last_activity_at}
                self._journal.observe(json.dumps(payload, ensure_ascii=False).encode("utf-8"), binding=binding)
                event = replace(event, status=status)
            self._channel.basic_ack(method.delivery_tag)
            return event if event.status is not None and not own else None
        except Exception as error:
            if isinstance(error, (ProtocolError, TransportError, AuthenticationError, AuthorizationError)):
                self._lost()
                raise
            self._failed(error)
            self._lost()
            raise self._failure from None
        except BaseException:
            self._lost()
            raise

    def _deleted(self, frame):
        self._queue_deleted = True
        self._connection.close()

    def _cleanup(self):
        if self._connection is None:
            if self._local_cleanup is None:
                self._local_cleanup = True
            return
        self._closing = True
        try:
            self._closed = self._closed or self._connection.is_closed
            if not self._closed:
                if self._queue is not None and self._channel is not None and self._channel.is_open:
                    self._channel.queue_delete(queue=self._queue, callback=self._deleted)
                else:
                    self._connection.close()
                self._drive(lambda: self._closed, cleanup=True)
            success = self._closed and (not self._queue_requested or self._queue_deleted)
            self._local_cleanup = success if self._local_cleanup is None else self._local_cleanup and success
        except BaseException as error:
            self._local_cleanup = False
            # Queue failure must not leave the connection running. If graceful
            # close also stalls, abort Pika's stream through its adapter hook.
            try:
                if not self._connection.is_closed:
                    if not self._connection.is_closing:
                        self._connection.close()
                    self._drive(lambda: self._closed, cleanup=True)
            except Exception:
                try:
                    self._connection._adapter_disconnect_stream()
                    self._drive(lambda: self._closed, cleanup=True)
                except Exception:
                    pass
            if not isinstance(error, Exception):
                raise
        finally:
            try:
                self._connection.ioloop.close()
            except Exception:
                self._local_cleanup = False
            self._connection = self._channel = self._queue = None

    def _lost(self):
        self._state = "DISCONNECTED"
        self._journal.transport_lost()
        self._cleanup()

    def close(self):
        self._thread()
        self._cleanup()
        self._journal.detach()
        self._state = "CLOSED"
        if not self._local_cleanup:
            raise TransportError("Owned agent queue/connection cleanup unconfirmed; remote agent cleanup UNKNOWN")

    def __enter__(self):
        return self.open()

    def __exit__(self, kind, value, traceback):
        try:
            self.close()
        except TransportError:
            if kind is None:
                raise
