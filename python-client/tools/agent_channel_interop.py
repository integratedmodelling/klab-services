"""Explicit owned-Qpid/native-wire test entrypoint. Never managed agent acceptance."""
import json
import logging
import sys

from klab_client import AgentChannel, AgentControlBinding, NotificationBroker
from klab_client.errors import AuthenticationError, AuthorizationError, InvalidRequestError, UnsupportedOperationError

PHASE = "validate"
BROKER_ERRORS = []


class SafeBrokerErrors(logging.Handler):
    def __init__(self, secrets):
        super().__init__(logging.ERROR)
        self.secrets = secrets

    def emit(self, record):
        if len(BROKER_ERRORS) < 8:
            text = record.getMessage()
            for secret in self.secrets:
                text = text.replace(secret, "[REDACTED]")
            BROKER_ERRORS.append(text[:1200])


def run(config):
    global PHASE
    if config.get("fixture") != "OWNED_NATIVE_WIRE_NOT_MANAGED_AGENT":
        raise ValueError("An explicitly owned native-wire fixture is required")
    broker_url = config["broker_url"]
    if not broker_url.startswith("amqp://127.0.0.1:"):
        raise ValueError("This test entrypoint admits only explicit loopback fixture ports")
    ref = AgentControlBinding(config["agent_urn"], "http://127.0.0.1:1", "native-wire-only",
                              "s.a", "test-principal", "test-operator-incarnation-assertion")
    broker = NotificationBroker(broker_url, config["username"], config["password"], allow_insecure_loopback=True)
    strict = AgentChannel(ref, broker, federation_id=config["federation_id"], timeout=3)
    try:
        strict.open()
    except UnsupportedOperationError:
        assert strict.local_cleanup_confirmed
    else:
        strict.close()
        raise AssertionError("This pinned Qpid fixture must expose its missing confirm capability honestly")
    channel = AgentChannel(ref, broker, federation_id=config["federation_id"], timeout=3, publication="unconfirmed")
    reports = []
    def until(state):
        for _ in range(8):
            event = channel.read(binding=ref)
            if event is not None:
                reports.append(event.status.state)
                if event.status.state == state:
                    return
        raise AssertionError("Expected bounded native lifecycle wire report")
    PHASE = "open"
    with channel:
        PHASE = "request-status"
        assert channel.request_status(binding=ref) is False
        PHASE = "read-ready"
        until("READY")
        PHASE = "start"
        assert channel.start(binding=ref).start_outcome == "UNCONFIRMED"
        PHASE = "read-running"
        until("RUNNING")
        try:
            channel.start(binding=ref)
        except InvalidRequestError:
            pass
        else:
            raise AssertionError("Duplicate start must not be published")
        PHASE = "stop"
        assert channel.stop(binding=ref).stop_outcome == "UNCONFIRMED"
        PHASE = "read-stopped"
        until("STOPPED")
        assert channel.view.remote_cleanup == "UNKNOWN"
        PHASE = "local-close"
    assert channel.local_cleanup_confirmed
    PHASE = "invalid-password"
    invalid = AgentChannel(ref, NotificationBroker(broker_url, config["username"], "invalid-" + config["password"],
        allow_insecure_loopback=True), federation_id=config["federation_id"], timeout=3)
    try:
        invalid.open()
    except AuthenticationError:
        assert invalid.local_cleanup_confirmed
    else:
        invalid.close()
        raise AssertionError("Invalid password must be denied")
    for wrong in (config["other_vhost_url"],):
        PHASE = "wrong-vhost"
        other = AgentChannel(ref, NotificationBroker(wrong, config["username"], config["password"],
            allow_insecure_loopback=True), federation_id=config["federation_id"], timeout=3)
        try:
            other.open()
        except (AuthenticationError, AuthorizationError):
            pass
        else:
            other.close()
            raise AssertionError("Wrong virtual-host principal must be denied")
    return {"reports": reports, "local_cleanup": channel.local_cleanup_confirmed,
            "broker_features": strict.broker_features, "publication": "UNCONFIRMED_OPT_IN",
            "invalid_password_denied": True,
            "wrong_vhost_denied": True, "managed_agent_execution": False, "remote_agent_cleanup": "UNKNOWN"}


if __name__ == "__main__":
    try:
        config = json.load(sys.stdin)
        logger = logging.getLogger("pika")
        logger.propagate = False
        logger.setLevel(logging.ERROR)
        logger.addHandler(SafeBrokerErrors([config["password"], config["username"], config["broker_url"]]))
        result = run(config)
    except Exception as error:
        # Never print credentials, raw exception text or a private broker URI.
        causes = []
        cause = error
        for _ in range(4):
            if cause is None:
                break
            trace = cause.__traceback__
            frames = []
            while trace is not None:
                frames.append([trace.tb_frame.f_code.co_name, trace.tb_lineno])
                trace = trace.tb_next
            causes.append({"type": type(cause).__name__, "frames": frames,
                "broker_reply_code": getattr(cause, "broker_reply_code", None),
                "broker_error_type": getattr(cause, "broker_error_type", None),
                "transport_phase": getattr(cause, "broker_transport_phase", None)})
            cause = cause.__context__
        print(json.dumps({"error_type": type(error).__name__, "phase": PHASE, "causes": causes,
                          "broker_errors": BROKER_ERRORS}), flush=True)
        raise SystemExit(1) from None
    print(json.dumps(result), flush=True)
