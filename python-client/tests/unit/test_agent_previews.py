"""Source-derived preview contract; mocks do not establish native compilation acceptance."""
from dataclasses import FrozenInstanceError
import hashlib
import json

import httpx
import pytest

from klab_client import Client, Endpoint
from klab_client.agents import AgentPreviews, AGENT_CLASS, BEHAVIOR_CLASS
from klab_client.errors import (InvalidRequestError, ProtocolError, ServerError,
                                TransportError, UnsupportedOperationError)

SOURCE = "behavior python.preview\n\naction main:\n  print('preview')\n"


def setup(*, parsed_change=None, result_change=None, drop=None, identity_change=None):
    calls = []
    counts = {"resources": 0, "runtime": 0}
    def handler(request):
        calls.append(request)
        service = request.url.host.split(".")[0]
        if request.url.path == "/public/capabilities":
            counts[service] += 1
            identity = service + "-id"
            if identity_change == (service, counts[service]):
                identity = "replacement-id"
            return httpx.Response(200, json={"serviceId": identity})
        assert request.headers["authorization"] == service + "-secret"
        if drop == service:
            raise httpx.ReadError("private transport detail")
        if service == "resources":
            assert request.method == "POST" and request.url.path == "/parseAsset/BEHAVIOR"
            payload = {"@CLASS": BEHAVIOR_CLASS, "urn": "python.preview", "serviceId": "resources-id",
                       "sourceCode": request.content.decode(), "behaviorType": "BEHAVIOR", "notifications": []}
            if parsed_change:
                payload.update(parsed_change)
            return httpx.Response(200, json=payload)
        assert request.method == "POST" and request.url.path == "/api/v1/instantiate/agent"
        payload = {"@CLASS": AGENT_CLASS, "behaviorUrn": "python.preview", "viable": True,
                   "alive": False, "messagingConnected": False, "startDeferred": False,
                   "urn": None, "scopeId": None, "startedAt": -1, "observationId": -1,
                   "javaCode": "public class Preview {}", "notifications": []}
        if result_change:
            payload.update(result_change)
        return httpx.Response(200, json=payload)
    client = Client("https://runtime.invalid", credential="runtime-secret",
                    resources=Endpoint("https://resources.invalid", "resources-secret"),
                    http_transport=httpx.MockTransport(handler))
    return client, calls


def test_source_only_preview_has_fixed_nonexecution_flags_and_frozen_evidence():
    api, calls = setup(result_change={"metadata": {"nested": [1]}})
    with api:
        result = AgentPreviews(api).preview(SOURCE)
    assert result.behavior_urn == "python.preview"
    assert result.source_sha256 == hashlib.sha256(SOURCE.encode()).hexdigest()
    assert result.resources_id == "resources-id" and result.runtime_id == "runtime-id"
    assert result.resources_url == "https://resources.invalid" and result.runtime_url == "https://runtime.invalid"
    assert result.execution == "NOT_STARTED_NOT_REGISTERED_NOT_JAVA_COMPILED"
    assert result.java_code == "public class Preview {}" and "public class" not in repr(result)
    with pytest.raises(FrozenInstanceError):
        result.behavior_urn = "changed"
    with pytest.raises(TypeError):
        result.raw["metadata"]["nested"] = ()
    writes = [call for call in calls if call.method == "POST"]
    assert len(writes) == 2 and writes[0].content == SOURCE.encode()
    body = json.loads(writes[1].content)
    assert {key: value for key, value in body.items() if key != "behavior"} == {
        "compileOnly": True, "reportJavaCode": True, "doNotStart": True,
        "doNotBindSession": True, "doNotBindObservation": True, "observationId": -1}
    assert body["behavior"]["sourceCode"] == SOURCE
    assert not hasattr(result, "start") and not hasattr(result, "stop")


@pytest.mark.parametrize("options", [{"max_source_bytes": 0}, {"max_source_bytes": True},
    {"max_source_bytes": 1048577}, {"max_response_bytes": 0}, {"max_response_bytes": True},
    {"max_response_bytes": 16777217}])
def test_bounds_before_io(options):
    api, calls = setup()
    with api, pytest.raises(InvalidRequestError):
        AgentPreviews(api, **options)
    assert not calls


def test_sync_client_required():
    with pytest.raises(InvalidRequestError):
        AgentPreviews(None)


@pytest.mark.parametrize("source", [None, " ", "x" * 101, "resources-secret"])
def test_invalid_source_is_never_transmitted(source):
    api, calls = setup()
    with api, pytest.raises(InvalidRequestError):
        AgentPreviews(api, max_source_bytes=100).preview(source)
    assert not calls


@pytest.mark.parametrize("value", [object(), float("nan")])
def test_non_json_values_rejected(value):
    api, calls = setup()
    with api, pytest.raises(InvalidRequestError):
        AgentPreviews(api)._safe(value)
    assert not calls


def test_json_recursion_rejected():
    value = []; value.append(value)
    api, calls = setup()
    with api, pytest.raises(InvalidRequestError):
        AgentPreviews(api)._safe(value)
    assert not calls


def test_payload_budget_checked_before_io():
    api, calls = setup()
    with api, pytest.raises(InvalidRequestError):
        AgentPreviews(api, max_response_bytes=3).preview(SOURCE)
    assert not calls


@pytest.mark.parametrize("change,error", [({"@CLASS": "other"}, ProtocolError),
    ({"sourceCode": "altered"}, ProtocolError), ({"behaviorType": "SCRIPT"}, UnsupportedOperationError),
    ({"serviceId": "other"}, ProtocolError), ({"urn": ""}, ProtocolError),
    ({"notifications": [{"message": "bad behavior", "level": "ERROR"}]}, ServerError),
    ({"extra": "runtime-secret"}, InvalidRequestError)])
def test_parser_response_fails_closed_before_translation(change, error):
    api, calls = setup(parsed_change=change)
    with api, pytest.raises(error):
        AgentPreviews(api).preview(SOURCE)
    assert not any(call.url.path == "/api/v1/instantiate/agent" for call in calls)


@pytest.mark.parametrize("change", [{"@CLASS": "other"}, {"behaviorUrn": "other"},
    {"viable": False}, {"alive": True}, {"messagingConnected": True}, {"startDeferred": True},
    {"urn": "registered"}, {"scopeId": "session"}, {"startedAt": 0}, {"observationId": 1},
    {"javaCode": " "}])
def test_result_requires_viable_unregistered_source_only_descriptor(change):
    api, calls = setup(result_change=change)
    with api, pytest.raises(ProtocolError):
        AgentPreviews(api).preview(SOURCE)
    assert len([call for call in calls if call.url.path == "/api/v1/instantiate/agent"]) == 1


@pytest.mark.parametrize("change,error", [({"notifications": [{"message": "translation failed", "level": "ERROR"}]}, ServerError),
    ({"javaCode": "runtime-secret"}, InvalidRequestError)])
def test_bad_translation_is_not_success(change, error):
    api, _ = setup(result_change=change)
    with api, pytest.raises(error):
        AgentPreviews(api).preview(SOURCE)


@pytest.mark.parametrize("service,phase", [("resources", 2), ("runtime", 2), ("resources", 3), ("runtime", 3)])
def test_identity_change_denies_preview_without_replay(service, phase):
    api, calls = setup(identity_change=(service, phase))
    with api, pytest.raises(ProtocolError):
        AgentPreviews(api).preview(SOURCE)
    assert len([call for call in calls if call.url.path == "/api/v1/instantiate/agent"]) == phase - 2


@pytest.mark.parametrize("service", ["resources", "runtime"])
def test_lost_response_has_no_automatic_retry(service):
    api, calls = setup(drop=service)
    with api, pytest.raises(TransportError):
        AgentPreviews(api).preview(SOURCE)
    assert len([call for call in calls if call.method == "POST" and call.url.host.startswith(service)]) == 1
