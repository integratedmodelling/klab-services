"""Source-derived ADD contracts; mocked responses are not actual authoring acceptance."""
from dataclasses import replace
import json

import httpx
import pytest

from klab_client import Authoring, Client, Endpoint
from klab_client.authoring import DOCUMENTS, ParsedSource
from klab_client.errors import (AuthorizationError, InvalidRequestError, ProtocolError,
                               ServerError, SubmissionOutcomeUnknown, UnsupportedOperationError)

SOURCE = "namespace python.authoring;\n"


def setup(*, parse=None, write=None, write_status=200, transport_error=False):
    calls = []
    def handler(request):
        calls.append(request)
        if request.url.path == "/resources/public/capabilities":
            return httpx.Response(200, json={"serviceId": "resources-id"})
        assert request.headers["authorization"] == "resources-secret"
        if request.url.path.startswith("/resources/parseAsset/"):
            kind = request.url.path.rsplit("/", 1)[1]
            return httpx.Response(200, content=json.dumps(parse if parse is not None else {
                "@CLASS": DOCUMENTS[kind], "urn": "python.authoring", "projectName": "no.project",
                "sourceCode": request.content.decode(), "behaviorType": "SCRIPT", "metadata": {"nested": [{"items": [1]}]}}))
        assert request.method == "PUT" and "/ADD" in request.url.path
        if transport_error:
            raise httpx.ReadError("private details")
        kind = request.url.path.split("/")[-2]
        urn = request.url.params["urn"]
        return httpx.Response(write_status, content=json.dumps(write if write is not None else [{
            "workspace": urn.split("/", 1)[0] if kind == "PROJECT" else urn,
            "results": [{"knowledgeClass": kind, "resourceUrn": urn.split("/", 1)[1] if kind == "PROJECT" else urn,
                         "operation": "CREATE", "serviceId": "resources-id"}]}]))
    client = Client("https://runtime.invalid", resources=Endpoint("https://resources.invalid/resources", "resources-secret"),
                    http_transport=httpx.MockTransport(handler))
    return client, calls


@pytest.mark.parametrize("kind", ["NAMESPACE"])
def test_explicit_add_only_flow_retains_exact_source_and_confirmation(kind):
    api, calls = setup()
    with api:
        author = Authoring(api)
        parsed = author.parse(SOURCE, kind=kind)
        workspace = author.create_workspace("owned-test", allow_write=True)
        project = author.create_project(workspace, "owned.project", allow_write=True)
        receipt = author.create_document(parsed, project, allow_write=True)
        assert receipt.urn == parsed.urn and receipt.operation == "CREATE"
        assert receipt.service_id == "resources-id"
        assert len(author.created) == 3 and "sourceCode" not in repr(parsed)
        with pytest.raises(TypeError):
            parsed.raw["metadata"]["nested"][0]["items"] = ()
        with pytest.raises(TypeError):
            author.created[("OTHER", "id")] = receipt
    writes = [request for request in calls if request.method == "PUT"]
    assert len(writes) == 3 and len(calls) == 8
    payload = json.loads(writes[-1].content)
    assert payload["sourceCode"] == SOURCE and payload["projectName"] == "owned.project"
    assert payload["metadata"]["nested"][0]["items"] == [1]
    assert writes[1].url.params["urn"] == "owned-test/owned.project"


@pytest.mark.parametrize("options", [{"max_source_bytes": 0}, {"max_source_bytes": True},
    {"max_source_bytes": 1048577}, {"max_response_bytes": 0}, {"max_response_bytes": True},
    {"max_response_bytes": 16777217}])
def test_invalid_budgets_rejected_without_io(options):
    api, calls = setup()
    with api, pytest.raises(InvalidRequestError):
        Authoring(api, **options)
    assert not calls


def test_requires_sync_client_and_explicit_write_consent():
    with pytest.raises(InvalidRequestError):
        Authoring(None)
    api, calls = setup()
    with api, pytest.raises(InvalidRequestError):
        Authoring(api).create_workspace("new")
    assert not calls


@pytest.mark.parametrize("kind,source,error", [("ONTOLOGY", SOURCE, UnsupportedOperationError),
    ([], SOURCE, UnsupportedOperationError), ("NAMESPACE", None, InvalidRequestError),
    ("NAMESPACE", " ", InvalidRequestError), ("NAMESPACE", "resources-secret", InvalidRequestError),
    ("NAMESPACE", "x" * 11, InvalidRequestError)])
def test_invalid_or_secret_source_is_never_transmitted(kind, source, error):
    api, calls = setup()
    with api, pytest.raises(error):
        Authoring(api, max_source_bytes=10).parse(source, kind=kind)
    assert not calls


@pytest.mark.parametrize("change,error", [({"@CLASS": "wrong"}, ProtocolError),
    ({"sourceCode": "changed"}, ProtocolError), ({"urn": ""}, ProtocolError),
    ({"urn": "../outside"}, ProtocolError),
    ({"metadata": {"value": "resources-secret"}}, InvalidRequestError),
    ({"notifications": [{"level": "Error", "message": "invalid"}]}, ServerError)])
def test_invalid_parser_responses_never_produce_authorable_source(change, error):
    payload = {"@CLASS": DOCUMENTS["NAMESPACE"], "urn": "python.authoring", "sourceCode": SOURCE, **change}
    api, _ = setup(parse=payload)
    with api, pytest.raises(error):
        Authoring(api).parse(SOURCE)


def test_script_authoring_is_blocked_until_its_exact_source_contract_is_verified():
    api, calls = setup()
    with api, pytest.raises(UnsupportedOperationError):
        Authoring(api).parse(SOURCE, kind="SCRIPT")
    assert not calls


@pytest.mark.parametrize("name", ["../foreign", ".", "..", "/foreign", "bad name", "", "1invalid"])
def test_local_project_workspace_names_cannot_be_filesystem_paths(name):
    api, calls = setup()
    with api:
        author = Authoring(api)
        with pytest.raises(InvalidRequestError):
            author.create_workspace(name, allow_write=True)
        with pytest.raises(InvalidRequestError):
            author.create_project(None, name, allow_write=True)
    assert not calls


def test_foreign_or_forged_receipts_cannot_select_existing_project_for_creation():
    api, _ = setup()
    with api:
        first, second = Authoring(api), Authoring(api)
        workspace = first.create_workspace("owned", allow_write=True)
        with pytest.raises(InvalidRequestError):
            second.create_project(workspace, "project", allow_write=True)
        with pytest.raises(InvalidRequestError):
            first.create_document(None, workspace, allow_write=True)
        project = first.create_project(workspace, "project", allow_write=True)
        parsed = first.parse(SOURCE)
        with pytest.raises(InvalidRequestError):
            first.create_document(replace(parsed, service_id="foreign"), project, allow_write=True)
        with pytest.raises(InvalidRequestError):
            first.create_document(replace(parsed, raw={"urn": "different"}), project, allow_write=True)
        raw = dict(parsed.raw)
        raw["urn"] = "../outside"
        with pytest.raises(InvalidRequestError):
            first.create_document(replace(parsed, urn="../outside", raw=raw), project, allow_write=True)


@pytest.mark.parametrize("metadata,error", [({"im:authoring-source-id": "other"}, UnsupportedOperationError),
    (["bad"], InvalidRequestError)])
def test_add_never_selects_an_existing_authoring_source(metadata, error):
    api, _ = setup()
    with api:
        author = Authoring(api)
        w = author.create_workspace("owned", allow_write=True)
        project = author.create_project(w, "project", allow_write=True)
        parsed = author.parse(SOURCE)
        raw = dict(parsed.raw)
        raw["metadata"] = metadata
        with pytest.raises(error):
            author.create_document(replace(parsed, raw=raw), project, allow_write=True)


@pytest.mark.parametrize("rows", [[], {}, [None], [{"empty": True}], [{"results": {}}], [{"results": [None]}],
    [{"results": [{"resourceUrn": "wrong"}]}], [{"notifications": [{"level": "Error", "message": "saved but invalid"}]}],
    [{"results": [{"notifications": [{"level": "Error", "message": "failure"}]}]}]])
def test_unconfirmed_creation_is_unknown_not_rollback_or_retry(rows):
    api, calls = setup(write=rows)
    with api:
        author = Authoring(api)
        with pytest.raises(SubmissionOutcomeUnknown, match="persistence may have occurred"):
            author.create_workspace("owned", allow_write=True)
        assert not author.created
    assert len([r for r in calls if r.method == "PUT"]) == 1


@pytest.mark.parametrize("status,error", [(403, AuthorizationError), (500, SubmissionOutcomeUnknown)])
def test_http_denial_and_unknown_mutation_remain_distinct(status, error):
    api, calls = setup(write_status=status)
    with api, pytest.raises(error):
        Authoring(api).create_workspace("owned", allow_write=True)
    assert len([r for r in calls if r.method == "PUT"]) == 1


def test_network_failure_never_replays_or_claims_newly_created_asset():
    api, calls = setup(transport_error=True)
    with api:
        author = Authoring(api)
        with pytest.raises(SubmissionOutcomeUnknown):
            author.create_workspace("owned", allow_write=True)
        assert not author.created
    assert len(calls) == 2


def test_source_and_payload_budgets_and_non_json_values_rejected():
    api, calls = setup()
    with api:
        author = Authoring(api, max_response_bytes=32)
        with pytest.raises(InvalidRequestError, match="byte budget"):
            author._safe("x" * 33)
        with pytest.raises(InvalidRequestError, match="finite JSON"):
            author._safe({"value": object()})
    assert not calls


def test_identity_drift_before_or_during_creation_is_not_silently_accepted():
    api, _ = setup()
    with api:
        author = Authoring(api)
        author.parse(SOURCE)
        author._binding = ("https://different.invalid", "resources-id")
        with pytest.raises(ProtocolError, match="identity changed"):
            author.create_workspace("owned", allow_write=True)
        author._binding = None
        original = api.transport.request
        def changed(*args, **kwargs):
            result = original(*args, **kwargs)
            if args[1] == "PUT":
                api._service_id_cache["resources"] = "changed"
            return result
        api.transport.request = changed
        with pytest.raises(SubmissionOutcomeUnknown):
            author.create_workspace("owned", allow_write=True)


def test_project_acknowledgement_must_name_its_workspace():
    api, _ = setup()
    with api:
        author = Authoring(api)
        workspace = author.create_workspace("owned", allow_write=True)
        original = api.transport.request
        def wrong_workspace(*args, **kwargs):
            result = original(*args, **kwargs)
            if args[1] == "PUT":
                result[0]["workspace"] = "foreign"
            return result
        api.transport.request = wrong_workspace
        with pytest.raises(SubmissionOutcomeUnknown):
            author.create_project(workspace, "project", allow_write=True)
