"""Read-only typed Resources contracts from ResourceCRUDController/KimModelImpl/ResourceSet."""
import json

import httpx
import pytest

from klab_client import Client, Endpoint, ResourceInspector
from klab_client.errors import (AuthenticationError, AuthorizationError, InvalidRequestError,
    MissingAssetError, ProtocolError, ServerError, UnsupportedOperationError)

MODEL = {"urn": "fixture.elevation", "label": "<Elevation>", "namespace": "fixture", "projectName": "test-models",
    "observables": [{"urn": "geography:Elevation", "unit": "m", "formalName": "height", "optional": False}],
    "dependencies": [{"urn": "geography:Reference", "optional": True}], "resourceUrns": [{"urn": "local:field"}],
    "version": {"major": 1, "minor": 2}, "notifications": [{"message": "candidate only", "level": "INFO"}]}
RESOURCE_SET = {"results": [{"resourceUrn": "local:field", "knowledgeClass": "RESOURCE", "serviceId": "remote-source",
    "resourceVersion": {"major": 2}, "accessKey": "do-not-retain"}],
    "services": {"remote-source": "https://untrusted.invalid/resources"}, "empty": False}


def setup(*, payload=None, status=200, **options):
    calls = []
    caps = {"serviceId": "resources-origin"}
    def handler(request):
        calls.append(request)
        assert request.headers["authorization"] == "resource-secret"
        assert request.headers["klab-scope"] == "s.c"
        assert request.headers["klab-service"] == "home"
        assert request.headers["accept-encoding"] == "identity"
        if request.url.path.endswith("/public/capabilities"):
            return httpx.Response(200, json=caps)
        if status != 200:
            return httpx.Response(status, json={"message": "request failed"})
        if payload is not None:
            result = payload(request)
        elif "/list/" in request.url.path:
            result = [MODEL, MODEL]
        elif "/resolve/" in request.url.path:
            result = RESOURCE_SET
        else:
            result = MODEL
        return httpx.Response(200, content=json.dumps(result))
    client = Client("https://runtime.invalid", "runtime-secret", runtime_service_id="home",
        resources=Endpoint("https://resources.invalid/base", "resource-secret"), http_transport=httpx.MockTransport(handler))
    return client, ResourceInspector(client, "s.c", **options), calls, caps


def test_inspect_existing_model_preserves_advertisement_not_execution():
    client, inspector, calls, _ = setup()
    with client:
        result = inspector.retrieve("fixture.elevation")
    assert result.urn == "fixture.elevation" and result.knowledge_class == "MODEL"
    assert result.namespace == "fixture" and result.project_name == "test-models"
    assert result.origin.service_id == "resources-origin" and result.origin.scope == "s.c"
    assert result.origin.service_url == "https://resources.invalid/base"
    assert result.advertised_observables[0].urn == "geography:Elevation"
    assert result.advertised_observables[0].unit == "m" and result.advertised_observables[0].formal_name == "height"
    assert result.advertised_observables[0].optional is False and result.listed_dependencies[0].optional is True
    assert result.resource_urns[0]["urn"] == "local:field" and result.version["major"] == 1
    assert result.notifications[0]["message"] == "candidate only" and not result.provider_metadata
    assert "not evidence of Resolver selection" in result.summary() and "&lt;Elevation&gt;" in result._repr_html_()
    assert "not a dependency closure" in result.dependency_completeness
    assert len(calls) == 3 and all(call.method == "GET" for call in calls)
    with pytest.raises(TypeError):
        result.raw["label"] = "overwrite"


def test_whole_lists_retain_duplicate_urns_and_do_not_imply_pagination():
    client, inspector, calls, _ = setup()
    with client:
        results = inspector.list()
    assert len(results) == 2 and results[0].urn == results[1].urn
    assert calls[1].url.path.endswith("/list/MODEL") and not calls[1].url.query


def test_missing_and_empty_metadata_are_different_and_resource_fields_retained():
    minimal = {"urn": "local:field", "adapterType": "geospatial", "serviceId": "provider", "future": {"code": "resource-secret"}}
    client, inspector, _, _ = setup(payload=lambda _: minimal)
    with client:
        result = inspector.retrieve("local:field", "RESOURCE")
    assert result.listed_dependencies is result.advertised_observables is result.resource_urns is None
    assert result.version is result.label is result.namespace is result.project_name is None
    assert result.provider_metadata == {"adapterType": "geospatial", "serviceId": "provider"}
    assert result.raw["future"]["code"] == "[REDACTED]" and "unavailable" in result.summary()
    client, inspector, _, _ = setup(payload=lambda _: {**minimal, "dependencies": [], "observables": [], "resourceUrns": []})
    with client:
        result = inspector.retrieve("local:field", "RESOURCE")
    assert result.listed_dependencies == result.resource_urns == result.advertised_observables == ()


def test_resource_resolution_preserves_origin_and_advertised_remote_without_following_it():
    client, inspector, calls, _ = setup()
    with client:
        result = inspector.resolve("local:field")
    assert result.partial and result.requested_urn == "local:field"
    reference = result.results[0]
    assert reference.urn == "local:field" and reference.service_id == "remote-source"
    assert reference.knowledge_class == "RESOURCE" and reference.version["major"] == 2
    assert "accessKey" not in reference.raw and "accessKey" not in result.raw["results"][0]
    assert result.services["remote-source"] == "https://untrusted.invalid/resources"
    assert all(call.url.host == "resources.invalid" for call in calls)
    assert "Partial metadata" in result._repr_html_()


def test_partial_resource_resolution_does_not_make_up_provider_or_version():
    client, inspector, _, _ = setup(payload=lambda _: {"results": [{"resourceUrn": "local:field"}]})
    with client:
        result = inspector.resolve("local:field")
    assert not result.services and result.results[0].service_id is result.results[0].version is None


def test_asset_identity_and_origin_views_do_not_expose_echoed_credentials():
    model = {**MODEL, "urn": "fixture.resource-secret"}
    client, inspector, _, caps = setup(payload=lambda _: model)
    caps["serviceId"] = "resources-resource-secret"
    with client:
        result = inspector.retrieve(model["urn"])
    assert result.urn == "fixture.[REDACTED]"
    assert result.origin.service_id == "resources-[REDACTED]"
    for display in (repr(result), result.summary(), result._repr_html_()):
        assert "resource-secret" not in display


def test_resolution_identity_views_do_not_expose_echoed_credentials():
    payload = {"results": [{"resourceUrn": "local:resource-secret"}]}
    client, inspector, _, caps = setup(payload=lambda _: payload)
    caps["serviceId"] = "resources-resource-secret"
    with client:
        result = inspector.resolve("requested-resource-secret")
    assert result.requested_urn == "requested-[REDACTED]"
    assert result.results[0].urn == "local:[REDACTED]"
    assert result.origin.service_id == "resources-[REDACTED]"
    for display in (repr(result), result.summary(), result._repr_html_()):
        assert "resource-secret" not in display


def test_redacted_origin_display_does_not_hide_actual_service_identity_changes():
    caps = None
    def payload(_):
        caps["serviceId"] = "resources-runtime-secret"
        return MODEL
    client, inspector, _, caps = setup(payload=payload)
    caps["serviceId"] = "resources-resource-secret"
    with client, pytest.raises(ProtocolError, match="identity changed"):
        inspector.retrieve("fixture.elevation")


@pytest.mark.parametrize("method", ["list", "retrieve", "resolve"])
@pytest.mark.parametrize("status,error", [(400, InvalidRequestError), (401, AuthenticationError),
    (403, AuthorizationError), (404, MissingAssetError), (422, UnsupportedOperationError), (503, ServerError)])
def test_distinct_http_errors(method, status, error):
    client, inspector, _, _ = setup(status=status)
    with client, pytest.raises(error):
        getattr(inspector, method)(*([] if method == "list" else ["fixture.elevation"]))


@pytest.mark.parametrize("method", ["list", "retrieve", "resolve"])
def test_unsupported_class_no_requests(method):
    client, inspector, calls, _ = setup()
    with client, pytest.raises(UnsupportedOperationError):
        getattr(inspector, method)(*(["NAMESPACE"] if method == "list" else ["fixture.elevation", "NAMESPACE"]))
    assert not calls


@pytest.mark.parametrize("changes", [{"dependencies": {}}, {"observables": [None]}, {"dependencies": [{"optional": "yes"}]},
    {"observables": [{"urn": 1}]}, {"dependencies": [MODEL] * 101}, {"resourceUrns": "not an array"},
    {"label": 2}, {"namespace": []}, {"projectName": {}}, {"urn": "different-identity"}])
def test_malformed_asset_and_budget_reject(changes):
    client, inspector, _, _ = setup(payload=lambda _: {**MODEL, **changes})
    with client, pytest.raises(ProtocolError):
        inspector.retrieve("fixture.elevation")


def test_null_missing_and_over_budget_whole_list():
    client, inspector, _, _ = setup(payload=lambda _: None)
    with client, pytest.raises(MissingAssetError):
        inspector.retrieve("fixture.elevation")
    client, inspector, _, _ = setup(max_items=1)
    with client, pytest.raises(ProtocolError, match="item budget"):
        inspector.list()
    client, inspector, _, _ = setup(max_response_bytes=80)
    with client, pytest.raises(ProtocolError, match="byte budget"):
        inspector.retrieve("fixture.elevation")


@pytest.mark.parametrize("payload", [{"results": []}, {"results": [{"resourceUrn": "local:field"}], "services": []},
    {"results": [{"resourceUrn": "local:field"}], "services": {"provider": 42}},
    {"results": [{"resourceUrn": "local:field", "serviceId": []}]},
    {"results": [{"resourceUrn": "local:field", "knowledgeClass": False}]}])
def test_empty_resolution_is_partial_but_malformed_metadata_fails(payload):
    client, inspector, _, _ = setup(payload=lambda _: payload)
    with client:
        if payload == {"results": []}:
            assert inspector.resolve("local:field").results == ()
        else:
            with pytest.raises(ProtocolError):
                inspector.resolve("local:field")


def test_origin_change_is_not_silently_attached_to_old_metadata():
    caps = None
    def payload(_):
        caps["serviceId"] = "replacement-service"
        return MODEL
    client, inspector, _, caps = setup(payload=payload)
    with client, pytest.raises(ProtocolError, match="identity changed"):
        inspector.retrieve("fixture.elevation")


def test_invalid_local_limits_and_identifiers():
    for kwargs in ({"max_items": True}, {"max_items": 1001}, {"max_response_bytes": 0}):
        client, _, _, _ = setup()
        with client, pytest.raises(InvalidRequestError):
            ResourceInspector(client, **kwargs)
    client, inspector, calls, _ = setup()
    with client, pytest.raises(InvalidRequestError):
        inspector.retrieve("")
    assert not calls
