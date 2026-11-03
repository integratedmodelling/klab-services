"""Explicit transient AMQP activity hints. Never a replay or synchronization API."""
from dataclasses import dataclass, field
import ipaddress
import json
import math
import ssl
from urllib.parse import unquote, urlsplit

from ._inspection import bounds, freeze
from .client import Client, Context, scope_token
from .errors import (AuthenticationError, AuthorizationError, ConfigurationError,
                     InvalidRequestError, ProtocolError, TransportError, UnsupportedOperationError)
from .handles import to_handle

ACTIVITY_CLASS = "org.integratedmodelling.klab.api.provenance.impl.ActivityImpl"


@dataclass(frozen=True)
class NotificationBroker:
    """Operator-configured Qpid Broker-J origin; never discovered from HTTP hints.

    Remote connections require verified TLS. Explicit insecure opt-in admits only
    literal loopback/localhost. Broker ACLs, issuer trust and message-size limits
    must be provisioned separately; HTTP context access grants no broker access.
    """
    url: str
    username: str = field(repr=False)
    password: str = field(repr=False)
    allow_insecure_loopback: bool = False

    def __post_init__(self):
        try:
            parsed = urlsplit(self.url)
            port = parsed.port
            host = parsed.hostname
            loopback = host == "localhost" or ipaddress.ip_address(host).is_loopback
        except ValueError:
            loopback = False
            # A DNS hostname is valid for TLS, but never qualifies for plaintext.
            try:
                parsed = urlsplit(self.url); host = parsed.hostname; port = parsed.port
            except (ValueError, TypeError, AttributeError):
                raise ConfigurationError("Invalid explicit AMQP origin") from None
        except (TypeError, AttributeError):
            raise ConfigurationError("Invalid explicit AMQP origin") from None
        if (not isinstance(self.url, str) or any(ord(c) < 33 for c in self.url)
                or parsed.scheme not in {"amqp", "amqps"} or not host or port == 0
                or parsed.username is not None or parsed.password is not None
                or parsed.query or parsed.fragment or type(self.allow_insecure_loopback) is not bool
                or (parsed.scheme == "amqp" and not (loopback and self.allow_insecure_loopback))
                or any(not isinstance(value, str) or not value or any(ord(c) < 32 for c in value)
                       for value in (self.username, self.password))):
            raise ConfigurationError("Use an explicit credential-free AMQPS URL and separate broker credentials; plaintext requires loopback opt-in")


@dataclass(frozen=True)
class ActivityNotification:
    kind: str
    message_id: int
    timestamp: int
    context: object = field(repr=False)
    payload: object = field(repr=False)
    continuity: str = "UNKNOWN: transient hints, not ordered durable state or a replay cursor"


def _pika():
    try:
        import pika
    except ImportError:
        raise UnsupportedOperationError("Install klab-python-client[events] for live notifications") from None
    return pika


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key")
        result[key] = value
    return result


class ActivityNotifications:
    """Caller-driven, synchronous, root-context-only Qpid AMQP 0-9-1 receiver.

    One manual-ack delivery in flight, bounded exclusive queue, total admission
    budgets; no worker thread, callbacks, publishing, HTTP I/O or automatic retry.
    Message size is checked AFTER Pika assembles it: a trusted broker must enforce
    its own inbound size limit. Queue rejection/expiry/loss may not be observable
    by a receiver: continuity and upstream overflow are always UNKNOWN.
    """
    continuity = "UNKNOWN"
    upstream_overflow = "UNKNOWN"

    def __init__(self, context, broker, *, timeout=5, max_events=128,
                 max_message_bytes=1024 * 1024, max_queue_messages=128,
                 max_total_bytes=16 * 1024 * 1024):
        if (not isinstance(context, Context) or not isinstance(context.client, Client)
                or not isinstance(broker, NotificationBroker) or scope_token(context) != context.id):
            raise InvalidRequestError("Notifications require a synchronous bound root Context and explicit NotificationBroker")
        if type(timeout) not in (int, float) or not math.isfinite(timeout) or not 0 < timeout <= 60:
            raise InvalidRequestError("Notification timeout must be finite in (0,60] seconds")
        self.max_events = bounds(max_events, 10000, "Notification event budget")
        self.max_message_bytes = bounds(max_message_bytes, 1024 * 1024, "Notification message bytes")
        self.max_queue_messages = bounds(max_queue_messages, 128, "Notification queue messages")
        self.max_total_bytes = bounds(max_total_bytes, 16 * 1024 * 1024, "Notification total bytes")
        self.context, self.broker, self.timeout = context, broker, timeout
        self.reference = to_handle(context)
        self._configured_broker = broker
        if any(context.client.transport.redact(value) != value for value in (broker.url, broker.username, broker.password)):
            raise ConfigurationError("Broker configuration must not reuse configured HTTP credential material")
        path = urlsplit(broker.url).path
        if not path.startswith("/") or unquote(path[1:]) != context.id:
            raise InvalidRequestError("Notifications require an explicitly provisioned virtual host matching the exact root context; no shared/default host")
        self.state = "NEW"
        self.cleanup_confirmed = None
        self.received = self.received_bytes = 0
        self._connection = self._channel = self._iterator = self._queue = None

    def _binding(self):
        try:
            self.context.client._validate_context_binding(self.context)
            current = to_handle(self.context)
        except (InvalidRequestError, ConfigurationError):
            raise ProtocolError("Notification context identity changed; create a new receiver") from None
        if current != self.reference or self.broker != self._configured_broker:
            raise ProtocolError("Notification context identity changed; reconnect explicitly with a new receiver")

    def open(self):
        if self.state != "NEW":
            raise InvalidRequestError("Receiver cannot reopen; create a new receiver and treat the gap as unknown")
        self._binding()
        pika = _pika()
        parsed = urlsplit(self.broker.url)
        try:
            options = None if parsed.scheme == "amqp" else pika.SSLOptions(ssl.create_default_context(), parsed.hostname)
            parameters = pika.ConnectionParameters(host=parsed.hostname, port=parsed.port or (5671 if options else 5672),
                virtual_host=unquote(parsed.path[1:]),
                credentials=pika.PlainCredentials(self.broker.username, self.broker.password),
                ssl_options=options, connection_attempts=1, retry_delay=0, heartbeat=30,
                socket_timeout=self.timeout, stack_timeout=self.timeout + 1,
                blocked_connection_timeout=self.timeout, channel_max=1)
            self._connection = pika.BlockingConnection(parameters)
            self._channel = self._connection.channel()
            # Inspect only; never create/alter/delete the Runtime's shared exchange.
            self._channel.exchange_declare(exchange=self.context.id, exchange_type="fanout", passive=True)
            declared = self._channel.queue_declare(queue="", durable=False, exclusive=True, auto_delete=True,
                arguments={"qpid.max_count": self.max_queue_messages,
                           "qpid.max_size": min(self.max_total_bytes, self.max_queue_messages * self.max_message_bytes),
                           "qpid.policy_type": "reject"})
            self._queue = declared.method.queue
            self._channel.queue_bind(queue=self._queue, exchange=self.context.id, routing_key="")
            self._channel.basic_qos(prefetch_count=1)
            self._iterator = self._channel.consume(self._queue, auto_ack=False, inactivity_timeout=self.timeout)
            self.state = "OPEN"
            return self
        except Exception as error:
            self._cleanup()
            self.state = "DISCONNECTED"
            if isinstance(error, pika.exceptions.ProbableAuthenticationError):
                raise AuthenticationError("Broker authentication failed; no retry") from None
            if isinstance(error, pika.exceptions.ProbableAccessDeniedError) or getattr(error, "reply_code", None) == 403:
                self.state = "ACCESS_DENIED"
                raise AuthorizationError("Broker denied subscription; HTTP authorization does not grant broker access") from None
            raise TransportError("Broker subscription failed; no credential diagnostics or automatic retry") from None
        except BaseException:
            self.state = "DISCONNECTED"
            self._cleanup()
            raise

    def _decode(self, body):
        if not isinstance(body, bytes) or len(body) > self.max_message_bytes:
            raise ProtocolError("Notification exceeds admitted message byte budget")
        self.received_bytes += len(body)
        if self.received_bytes > self.max_total_bytes:
            raise ProtocolError("Notification total byte budget exhausted")
        try:
            data = json.loads(body.decode("utf-8"), object_pairs_hook=_pairs)
        except (ValueError, UnicodeError, RecursionError):
            raise ProtocolError("Malformed notification JSON") from None
        if (not isinstance(data, dict) or data.get("messageType") not in ("ActivityStarted", "ActivityFinished")
                or data.get("messageClass") != "DigitalTwin" or data.get("queue") != "Events"
                or data.get("payloadClass") != ACTIVITY_CLASS or not isinstance(data.get("payload"), dict)
                or type(data.get("id")) is not int or data["id"] < 1
                or type(data.get("timestamp")) is not int or data["timestamp"] < 0):
            raise ProtocolError("Unsupported or invalid activity notification envelope")
        try:
            dispatch = scope_token(data.get("dispatchId"))
        except InvalidRequestError:
            raise ProtocolError("Invalid notification context") from None
        if dispatch is None or ".".join(dispatch.split("#")[0].split(".")[:2]) != self.context.id:
            raise ProtocolError("Notification belongs to a different context")
        if data["payload"].get("serviceId") != self.reference.runtime_id:
            raise ProtocolError("Notification Activity has a different or absent Runtime identity")
        def redact(value):
            value = self.context.client.transport.redact(value)
            return value.replace(self.broker.password, "[REDACTED]").replace(self.broker.username, "[REDACTED]")
        return ActivityNotification(data["messageType"], data["id"], data["timestamp"],
                                    self.reference, freeze(data["payload"], redact))

    def read(self):
        """One event or timeout None. A timeout never establishes unchanged state.

        Unsupported/malformed messages terminate rather than becoming empty state.
        Duplicates/out-of-order IDs are delivered as hints, never deduplicated or
        interpreted as a cursor. Acknowledgement is local admission, not commit.
        """
        if self.state != "OPEN":
            raise InvalidRequestError("Open an unused receiver before reading; no automatic reconnect")
        try:
            self._binding()
            if self.received >= self.max_events:
                self.state = "LIMIT_REACHED"
                raise ProtocolError("Notification event budget exhausted")
            method, properties, body = next(self._iterator)
            if method is None:
                return None
            event = self._decode(body)
            self._binding()
            self._channel.basic_ack(method.delivery_tag)
            self.received += 1
            return event
        except ProtocolError:
            if self.state == "OPEN":
                self.state = "REJECTED"
            self._cleanup()
            raise
        except Exception as error:
            self.state = "DISCONNECTED"
            self._cleanup()
            if getattr(error, "reply_code", None) == 403:
                self.state = "ACCESS_DENIED"
                raise AuthorizationError("Broker revoked or denied notification access; no retry") from None
            raise TransportError("Notification delivery interrupted; continuity unknown, re-read authorized state explicitly") from None
        except BaseException:
            self.state = "INTERRUPTED"
            self._cleanup()
            raise

    def _cleanup(self):
        success = True
        try:
            if self._channel is not None and self._queue is not None:
                self._channel.queue_delete(queue=self._queue)
        except Exception:
            success = False
        try:
            if self._connection is not None:
                self._connection.close()
        except Exception:
            success = False
        self._queue = self._iterator = self._channel = self._connection = None
        self.cleanup_confirmed = success

    def close(self):
        """Only the owned queue/connection, never the exchange, context or Job."""
        if self.state == "CLOSED":
            return
        # Preserve earlier uncertain cleanup rather than relabelling it success.
        if self._connection is not None or self.cleanup_confirmed is None:
            self._cleanup()
        self.state = "CLOSED"
        if not self.cleanup_confirmed:
            raise TransportError("Local notification cleanup was not confirmed; exclusive queue expiry is broker-owned")

    def __enter__(self):
        return self.open()

    def __exit__(self, kind, value, traceback):
        try:
            self.close()
        except TransportError:
            if kind is None:
                raise
