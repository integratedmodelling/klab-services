from dataclasses import FrozenInstanceError
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from types import SimpleNamespace as NS

import pytest

from klab_client import AgentExecutions, AsyncContext, Context, DeferredAgent
from klab_client.errors import (InvalidRequestError, ProtocolError, SubmissionOutcomeUnknown,
                                UnsupportedOperationError)
from test_agent_previews import SOURCE, setup


def scenario(**kwargs):
    changes = dict(urn="registered-agent", startDeferred=True, messagingConnected=True,
        scopeId="s.c", javaCode=None)
    changes.update(kwargs.pop("result_change", {}) or {})
    client, calls = setup(result_change=changes, **kwargs)
    client.runtime_service_id = "runtime-id"
    context_type = AsyncContext if kwargs.get("async_mode") else Context
    context = context_type(client, {"id": "s.c", "serviceId": "runtime-id"})
    return client, context, calls


def factory(context, **kwargs):
    return AgentExecutions(context, **(dict(principal_key="test-principal", runtime_incarnation="test-incarnation") | kwargs))


def test_compiled_registration_is_deferred_unbound_and_exactly_one_post():
    api, ctx, calls = scenario(result_change={"metadata": {"nested": [1]}})
    with api:
        creator = factory(ctx)
        agent = creator.create(SOURCE, allow_execute=True)
        assert isinstance(agent, DeferredAgent)
        receipt = agent.receipt
        assert receipt.binding.runtime_id == "runtime-id" and receipt.binding.scope == "s.c"
        assert receipt.binding.agent_urn == "registered-agent"
        assert receipt.source_sha256 == hashlib.sha256(SOURCE.encode()).hexdigest()
        assert receipt.parser_source_sha256 == receipt.source_sha256
        assert not receipt.parser_edge_whitespace_trimmed and receipt.messaging_connected
        assert agent.view is None
        with pytest.raises(FrozenInstanceError):
            receipt.behavior_urn = "other"
        with pytest.raises(TypeError):
            receipt.raw["metadata"]["nested"] = ()
        with pytest.raises(AttributeError):
            agent.receipt = None
        count = len(calls)
        with pytest.raises(InvalidRequestError):
            creator.create(SOURCE, allow_execute=True)
        assert len(calls) == count
        agent.close()
    writes = [call for call in calls if call.url.path == "/api/v1/instantiate/agent"]
    assert len(writes) == 1
    assert writes[0].headers["klab-scope"] == "s.c"
    assert writes[0].headers["klab-service"] == "runtime-id"
    body = json.loads(writes[0].content)
    assert body["compileOnly"] is False and body["doNotStart"] is True
    assert body["doNotBindSession"] is body["doNotBindObservation"] is True
    assert body["observationId"] == -1 and body["reportJavaCode"] is False
    assert not any("createSession" in str(call.url) or "createContext" in str(call.url) for call in calls)


@pytest.mark.parametrize("source", [None, "", " \n", 3, "\ud800", "x" * (1024 * 1024 + 1)])
def test_bad_source_does_not_read_or_write(source):
    api, ctx, calls = scenario()
    with api, pytest.raises(InvalidRequestError):
        factory(ctx).create(source, allow_execute=True)
    assert calls == []


@pytest.mark.parametrize("consent", [False, None, 1, "true"])
def test_explicit_execution_consent_required(consent):
    api, ctx, calls = scenario()
    with api, pytest.raises(InvalidRequestError):
        factory(ctx).create(SOURCE, allow_execute=consent)
    assert calls == []


@pytest.mark.parametrize("changes", [dict(principal_key=None), dict(runtime_incarnation=""),
    dict(max_source_bytes=0), dict(max_response_bytes=True)])
def test_invalid_factory_metadata_or_budgets_before_io(changes):
    api, ctx, calls = scenario()
    with api, pytest.raises(InvalidRequestError):
        factory(ctx, **changes)
    assert not calls


def test_requires_root_context_not_generic_client_or_focused_context():
    with pytest.raises(InvalidRequestError):
        factory(None)
    api, ctx, calls = scenario()
    with api, pytest.raises(InvalidRequestError):
        ctx.context_id = "s.c.7"
        factory(ctx)
    assert not calls


@pytest.mark.parametrize("kind", ["SCRIPT", "APP", "UNITTEST", "TASK", "USER", "COMPONENT", "TRAIT"])
def test_private_session_or_unverified_behavior_kinds_never_registered(kind):
    api, ctx, calls = scenario(parsed_change={"behaviorType": kind})
    with api, pytest.raises(UnsupportedOperationError):
        factory(ctx).create(SOURCE, allow_execute=True)
    assert not any(call.url.path == "/api/v1/instantiate/agent" for call in calls)


@pytest.mark.parametrize("changes", [{"sourceCode": "changed"}, {"sourceCode": None}, {"@CLASS": "wrong"}, {"serviceId": "wrong"}])
def test_parser_fidelity_and_identity_fail_before_registration(changes):
    api, ctx, calls = scenario(parsed_change=changes)
    with api, pytest.raises(ProtocolError):
        factory(ctx).create(SOURCE, allow_execute=True)
    assert not any(call.url.path == "/api/v1/instantiate/agent" for call in calls)


def test_whitespace_policy_and_separate_source_hashes_are_explicit():
    source = " \n" + SOURCE
    api, ctx, _ = scenario(parsed_change={"sourceCode": source.strip(" \t\r\n")})
    with api:
        receipt = factory(ctx).create(source, allow_execute=True).receipt
    assert receipt.parser_edge_whitespace_trimmed
    assert receipt.source_sha256 != receipt.parser_source_sha256


@pytest.mark.parametrize("changes", [{"@CLASS": "wrong"}, {"behaviorUrn": "wrong"}, {"viable": False},
    {"alive": True}, {"startDeferred": False}, {"messagingConnected": "true"}, {"scopeId": "other.context"},
    {"startedAt": 1}, {"observationId": 1}, {"startedAt": -1.0}, {"observationId": -1.0},
    {"urn": ""}, {"urn": None}])
def test_unconfirmed_post_result_never_replayed(changes):
    api, ctx, calls = scenario(result_change=changes)
    with api:
        creator = factory(ctx)
        with pytest.raises(SubmissionOutcomeUnknown):
            creator.create(SOURCE, allow_execute=True)
        count = len(calls)
        with pytest.raises(InvalidRequestError):
            creator.create(SOURCE, allow_execute=True)
        assert len(calls) == count
    assert len([call for call in calls if call.url.path == "/api/v1/instantiate/agent"]) == 1


def test_lost_compilation_reply_never_reissued_or_cleaned_up_blindly():
    api, ctx, calls = scenario(drop="runtime")
    with api:
        creator = factory(ctx)
        with pytest.raises(SubmissionOutcomeUnknown):
            creator.create(SOURCE, allow_execute=True)
        count = len(calls)
        with pytest.raises(InvalidRequestError):
            creator.create(SOURCE, allow_execute=True)
        assert len(calls) == count


@pytest.mark.parametrize("count,expected", [(1, ProtocolError), (2, ProtocolError), (3, SubmissionOutcomeUnknown)])
def test_runtime_identity_drift_is_not_silently_rebound(count, expected):
    api, ctx, calls = scenario(identity_change=("runtime", count))
    with api, pytest.raises(expected):
        factory(ctx).create(SOURCE, allow_execute=True)
    assert len([call for call in calls if call.url.path == "/api/v1/instantiate/agent"]) == (1 if count == 3 else 0)


def test_local_context_and_origin_drift_prevents_post():
    api, ctx, calls = scenario()
    with api:
        creator = factory(ctx)
        ctx.context_id = "s.c.7"
        with pytest.raises(ProtocolError):
            creator.create(SOURCE, allow_execute=True)
    assert calls == []


class FakeAttached:
    def __init__(self, *args, **kwargs):
        self.commands = []
        self.view = NS(remote_cleanup="UNKNOWN")
    def open(self):
        self.commands.append("open")
    def start(self, **kwargs):
        self.commands.append("start"); return "published"
    def stop(self, **kwargs):
        self.commands.append("stop"); return "published"
    def read(self, **kwargs):
        self.commands.append("read"); return "report"
    def request_status(self, **kwargs):
        self.commands.append("status"); return True
    def close(self):
        self.commands.append("close")


def test_explicit_attach_control_and_local_close_separation(monkeypatch):
    api, ctx, calls = scenario()
    monkeypatch.setattr("klab_client.agent_execution.AgentChannel", FakeAttached)
    with api:
        agent = factory(ctx).create(SOURCE, allow_execute=True)
        with pytest.raises(InvalidRequestError):
            agent.start()
        assert agent.attach(object(), federation_id="test") is agent
        assert agent.start() == agent.stop() == "published"
        assert agent.request_status() is True and agent.read() == "report"
        assert agent.view.remote_cleanup == "UNKNOWN"
        with pytest.raises(InvalidRequestError):
            agent.attach(object(), federation_id="test")
        channel = agent._channel
        agent.close()
        assert channel.commands == ["open", "start", "stop", "status", "read", "close"]
        with pytest.raises(InvalidRequestError):
            agent.start()
    assert not any("release" in str(call.url).lower() for call in calls)


def test_disconnected_registration_retains_receipt_but_never_claims_operational_control():
    api, ctx, _ = scenario(result_change={"messagingConnected": False})
    with api:
        agent = factory(ctx).create(SOURCE, allow_execute=True)
        assert not agent.receipt.messaging_connected
        with pytest.raises(UnsupportedOperationError):
            agent.attach(object(), federation_id="test")
        agent.close()
        with pytest.raises(InvalidRequestError):
            agent.attach(object(), federation_id="test")


def test_invalid_deferred_handle_constructor_rejected():
    with pytest.raises(InvalidRequestError):
        DeferredAgent(None, lambda: None)


def test_registered_handle_thread_affinity_prevents_competing_attachments(monkeypatch):
    api, ctx, _ = scenario()
    monkeypatch.setattr("klab_client.agent_execution.AgentChannel", FakeAttached)
    with api:
        agent = factory(ctx).create(SOURCE, allow_execute=True)
        with ThreadPoolExecutor(max_workers=1) as pool:
            for operation in (lambda: agent.attach(object(), federation_id="test"), agent.start, agent.close):
                with pytest.raises(InvalidRequestError):
                    pool.submit(operation).result(timeout=2)
        assert agent._channel is None
        agent.attach(object(), federation_id="test")
        agent.close()
