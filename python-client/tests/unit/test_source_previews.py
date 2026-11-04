from dataclasses import FrozenInstanceError
import hashlib
import json
from pathlib import Path

import httpx
import pytest

from klab_client import Client, Endpoint, SourcePreview, SourcePreviews
from klab_client.authoring import Authoring, DOCUMENTS, ParsedSource
from klab_client.errors import InvalidRequestError, ProtocolError, ServerError, UnsupportedOperationError
from klab_client.source_previews import DOCUMENT_CLASSES

FIXTURES = json.loads((Path(__file__).resolve().parents[3] /
    "klab.core.common/src/test/resources/source-preview-wire.json").read_bytes())
SOURCES = {row["kind"]: row["document"] for row in FIXTURES}
SOURCE = next(iter(SOURCES.values()))["sourceCode"]


def setup(change=None, callback=None, identity_change=None):
    calls, counts = [], {"caps": 0}
    def handler(request):
        calls.append(request)
        if callback: callback(request)
        assert request.url.host == "resources.example"
        assert request.headers["authorization"] == "resources-secret"
        if request.url.path == "/public/capabilities":
            counts["caps"] += 1
            return httpx.Response(200, json={"serviceId": "other" if counts["caps"] == identity_change else "resources-fixture"})
        kind = request.url.path.removeprefix("/parseAsset/")
        assert request.method == "POST" and kind in DOCUMENT_CLASSES
        assert request.content == SOURCE.encode()
        value = SOURCES[kind] | (change or {})
        return httpx.Response(200, content=json.dumps(value).encode(), headers={"Content-Type": "application/json"})
    client = Client("https://runtime.example", "runtime-secret", resources=Endpoint("https://resources.example", "resources-secret"),
                    http_transport=httpx.MockTransport(handler))
    return client, calls


@pytest.mark.parametrize("kind", DOCUMENT_CLASSES)
def test_read_only_native_document_identity_and_source_receipt(kind):
    api, calls = setup(change={"metadata": {"nested": [1, 2]}})
    with api:
        preview = SourcePreviews(api).parse(SOURCE, kind=kind)
        assert isinstance(preview, SourcePreview) and not isinstance(preview, ParsedSource)
        assert preview.kind == kind and preview.urn == "test.preview"
        assert preview.service_id == "resources-fixture" and preview.service_url == "https://resources.example"
        assert preview.source_sha256 == preview.parser_source_sha256 == hashlib.sha256(SOURCE.encode()).hexdigest()
        assert not preview.edge_whitespace_trimmed
        with pytest.raises(FrozenInstanceError): preview.kind = "other"
        with pytest.raises(TypeError): preview.raw["metadata"]["nested"] = ()
        assert SOURCE not in repr(preview)
        with pytest.raises(InvalidRequestError): Authoring(api).create_document(preview, None, allow_write=True)
    assert [call.url.path for call in calls] == ["/public/capabilities", "/parseAsset/" + kind, "/public/capabilities"]
    assert set(DOCUMENTS) == {"NAMESPACE"}
    assert not any("submit" in call.url.path or "instantiate" in call.url.path for call in calls)
    with pytest.raises(TypeError): DOCUMENT_CLASSES["OTHER"] = "unverified"


@pytest.mark.parametrize("source", [None, 1, b"source", "", " \n", "\ud800", "x" * (1024 * 1024 + 1)])
def test_invalid_source_rejected_before_network(source):
    api, calls = setup()
    with api, pytest.raises(InvalidRequestError): SourcePreviews(api).parse(source)
    assert not calls


@pytest.mark.parametrize("kind", [None, [], "OBSERVATION_STRATEGY", "MODEL", "APPLICATION", "SCRIPT", "ONTOLOGY/../NAMESPACE"])
def test_unadmitted_kind_rejected_before_network(kind):
    api, calls = setup()
    with api, pytest.raises(UnsupportedOperationError): SourcePreviews(api).parse(SOURCE, kind=kind)
    assert not calls


@pytest.mark.parametrize("kwargs", [{"max_source_bytes": True}, {"max_source_bytes": 0}, {"max_source_bytes": 1024 * 1024 + 1},
    {"max_response_bytes": True}, {"max_response_bytes": 0}, {"max_response_bytes": 16 * 1024 * 1024 + 1}])
def test_invalid_constructor_budgets(kwargs):
    api, calls = setup()
    with api, pytest.raises(InvalidRequestError): SourcePreviews(api, **kwargs)
    assert not calls


def test_synchronous_client_required():
    with pytest.raises(InvalidRequestError): SourcePreviews(object())


@pytest.mark.parametrize("policy", [None, 0, 1, "true"])
def test_source_policy_is_an_explicit_boolean(policy):
    api, calls = setup()
    with api, pytest.raises(InvalidRequestError): SourcePreviews(api).parse(SOURCE, allow_edge_trimming=policy)
    assert not calls


@pytest.mark.parametrize("changes", [{"@CLASS": "unknown.JavaClass"}, {"serviceId": "other"}, {"serviceId": None},
    {"sourceCode": None}, {"sourceCode": "changed"}, {"sourceCode": SOURCE.replace(" ", "  ")},
    {"urn": ""}, {"urn": "test/../preview"}, {"urn": "x" * 513}, {"urn": None}])
def test_malformed_or_changed_parser_identity_source_and_urn_rejected(changes):
    api, _ = setup(change=changes)
    with api, pytest.raises(ProtocolError): SourcePreviews(api).parse(SOURCE)


def test_parser_error_notification_does_not_become_success_or_write():
    api, calls = setup(change={"notifications": [{"level": "Error", "message": "not accepted"}]})
    with api, pytest.raises(ServerError): SourcePreviews(api).parse(SOURCE)
    assert len(calls) == 2


def test_trimmed_response_requires_explicit_policy_and_records_both_hashes():
    trimmed = SOURCE.strip(" \t\r\n")
    api, _ = setup(change={"sourceCode": trimmed})
    with api:
        session = SourcePreviews(api)
        with pytest.raises(ProtocolError): session.parse(SOURCE)
        receipt = session.parse(SOURCE, allow_edge_trimming=True)
    assert receipt.edge_whitespace_trimmed
    assert receipt.source_sha256 != receipt.parser_source_sha256
    assert receipt.parser_source_sha256 == hashlib.sha256(trimmed.encode()).hexdigest()


@pytest.mark.parametrize("changes", [{"metadata": {"not_finite": float("nan")}}, {"sourceCode": "\ud800"},
    {"metadata": {"not_utf8": "\ud800"}}])
def test_invalid_wire_json_rejected_without_coercion(changes):
    api, _ = setup(change=changes)
    with api, pytest.raises(ProtocolError): SourcePreviews(api).parse(SOURCE)


@pytest.mark.parametrize("credential", ["runtime-secret", "resources-secret"])
def test_credential_material_in_source_never_transmitted(credential):
    api, calls = setup()
    with api, pytest.raises(InvalidRequestError): SourcePreviews(api).parse(SOURCE + credential)
    assert not calls


def test_credential_material_in_response_not_retained():
    api, _ = setup(change={"metadata": {"secret": "resources-secret"}})
    with api, pytest.raises(InvalidRequestError): SourcePreviews(api).parse(SOURCE)


def test_identity_change_during_parse_rejected():
    api, calls = setup(identity_change=2)
    with api, pytest.raises(ProtocolError): SourcePreviews(api).parse(SOURCE)
    assert len(calls) == 3


def test_endpoint_or_credential_drift_rejected_before_new_request():
    api, calls = setup()
    with api:
        session = SourcePreviews(api)
        session.parse(SOURCE)
        api.transport.endpoints["resources"] = Endpoint("https://other.example", "new-secret")
        count = len(calls)
        with pytest.raises(ProtocolError): session.parse(SOURCE)
        assert len(calls) == count


@pytest.mark.parametrize("at", ["/public/capabilities", "/parseAsset/NAMESPACE"])
def test_endpoint_drift_inside_response_rejected(at):
    state = {}
    def callback(request):
        if request.url.path == at:
            state["api"].transport.endpoints["resources"] = Endpoint("https://other.example", "new-secret")
    api, calls = setup(callback=callback)
    state["api"] = api
    with api, pytest.raises(ProtocolError): SourcePreviews(api).parse(SOURCE)
    assert len(calls) == (1 if at == "/public/capabilities" else 2)


def test_transport_object_drift_before_preview_io():
    api, calls = setup()
    with api:
        session, transport = SourcePreviews(api), api.transport
        api.transport = object()
        try:
            with pytest.raises(ProtocolError): session.parse(SOURCE)
        finally: api.transport = transport
    assert not calls


def test_source_and_response_byte_budgets():
    api, calls = setup()
    with api:
        with pytest.raises(InvalidRequestError): SourcePreviews(api, max_source_bytes=1).parse(SOURCE)
        with pytest.raises(ProtocolError): SourcePreviews(api, max_response_bytes=1).parse(SOURCE)
    assert not calls


@pytest.mark.parametrize("secret", ['quoted-"key', "unicode-é-key", "backslash-\\-key"])
def test_json_escaping_cannot_hide_configured_credentials_in_source(secret):
    api, calls = setup()
    with api:
        api.transport.sensitive_values = (secret,)
        with pytest.raises(InvalidRequestError): SourcePreviews(api).parse(SOURCE + secret)
    assert not calls


def test_decoded_nested_response_credential_cannot_be_silently_redacted_as_source_evidence():
    secret = 'quoted-"key'
    api, _ = setup(change={"metadata": {"nested": [secret]}})
    with api:
        api.transport.sensitive_values = (secret,)
        with pytest.raises(InvalidRequestError): SourcePreviews(api).parse(SOURCE)
