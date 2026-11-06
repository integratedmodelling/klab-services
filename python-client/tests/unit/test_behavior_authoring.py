"""Source-derived nonexecuting BEHAVIOR ADD contract, not served Java acceptance."""
import asyncio
from contextlib import asynccontextmanager
from dataclasses import replace
import json

import httpx
import pytest

from klab_client import AsyncAuthoring, AsyncClient, Authoring, Client, Endpoint
from klab_client.errors import (AuthorizationError, InvalidRequestError, ProtocolError,
                                ServerError, SubmissionOutcomeUnknown, UnsupportedOperationError)

SOURCE = '''behavior python.authoring
    "Protocol-test draft; parsing remains server-authoritative."
    version 1.0

action main:
    return 0
'''
BEHAVIOR = "org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl"


def setup(mode, *, parsed_change=None, result=None, status=200, drop=False):
    calls = []
    def handler(request):
        calls.append(request)
        assert request.headers["authorization"] == "resources-secret"
        if request.url.path.endswith("/public/capabilities"):
            return httpx.Response(200, json={"serviceId": "resources-id"})
        if request.url.path.endswith("/parseAsset/BEHAVIOR"):
            assert request.content == SOURCE.encode()
            return httpx.Response(200, json={"@CLASS": BEHAVIOR, "urn": "python.authoring",
                "sourceCode": SOURCE, "behaviorType": "BEHAVIOR", "projectName": None,
                "metadata": {"draft": {"notes": ["retained"]}}, **(parsed_change or {})})
        assert request.method == "PUT" and "/api/v1/submit/" in request.url.path
        kind = request.url.path.split("/")[-2]
        urn = request.url.params["urn"]
        if kind == "BEHAVIOR":
            if drop:
                raise httpx.ReadError("deliberately lost reply")
            assert request.url.path == "/resources/api/v1/submit/BEHAVIOR/ADD"
            payload = json.loads(request.content)
            assert payload["sourceCode"] == SOURCE and payload["projectName"] == "project"
            assert payload["behaviorType"] == "BEHAVIOR" and payload["@CLASS"] == BEHAVIOR
            assert payload["metadata"] == {"draft": {"notes": ["retained"]}}
            return httpx.Response(status, json=result if result is not None else [{"empty": False,
                "behaviors": [{"knowledgeClass": "BEHAVIOR", "resourceUrn": urn,
                               "operation": "CREATE", "serviceId": "resources-id"}]}])
        return httpx.Response(200, json=[{"empty": False, "workspace": urn.split("/", 1)[0] if kind == "PROJECT" else urn,
            "results": [{"knowledgeClass": kind,
            "resourceUrn": urn.split("/", 1)[-1], "operation": "CREATE", "serviceId": "resources-id"}]}])
    cls = AsyncClient if mode == "async" else Client
    return cls("https://runtime.invalid", resources=Endpoint("https://resources.invalid/resources", "resources-secret"),
               http_transport=httpx.MockTransport(handler)), calls


@asynccontextmanager
async def managed(client, mode):
    if mode == "async":
        async with client:
            yield
    else:
        with client:
            yield


async def call(author, mode, method, *args, **kwargs):
    result = getattr(author, method)(*args, **kwargs)
    return await result if mode == "async" else result


async def owned_project(author, mode):
    workspace = await call(author, mode, "create_workspace", "owned", allow_write=True)
    return await call(author, mode, "create_project", workspace, "project", allow_write=True)


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_behavior_source_malformed_utf8_is_an_invalid_request_before_any_http(mode):
    async def run():
        client, calls = setup(mode)
        async with managed(client, mode):
            author = (AsyncAuthoring if mode == "async" else Authoring)(client)
            with pytest.raises(InvalidRequestError):
                await call(author, mode, "parse", "bad\ud800source", kind="BEHAVIOR")
        assert not calls
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_nonexecuting_behavior_add_preserves_exact_source_project_and_create_receipt(mode):
    async def run():
        client, calls = setup(mode)
        if mode == "async":
            async with client:
                author = AsyncAuthoring(client)
                parsed = await author.parse(SOURCE, kind="BEHAVIOR")
                workspace = await author.create_workspace("owned", allow_write=True)
                project = await author.create_project(workspace, "project", allow_write=True)
                receipt = await author.create_document(parsed, project, allow_write=True)
        else:
            with client:
                author = Authoring(client)
                parsed = author.parse(SOURCE, kind="BEHAVIOR")
                workspace = author.create_workspace("owned", allow_write=True)
                project = author.create_project(workspace, "project", allow_write=True)
                receipt = author.create_document(parsed, project, allow_write=True)
        assert receipt.kind == "BEHAVIOR" and receipt.urn == "python.authoring" and receipt.operation == "CREATE"
        assert len(author.created) == 3 and receipt in author.created.values()
        assert not hasattr(receipt, "start") and "sourceCode" not in repr(parsed)
        assert len(calls) == 8 and sum(r.method == "PUT" for r in calls) == 3
        assert not any("instantiate" in r.url.path or "createSession" in r.url.path or "unlock" in r.url.path for r in calls)
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("behavior_type", [None, "SCRIPT", "APP", "TESTCASE", "TRAIT", "LIBRARY"])
def test_parser_behavior_kind_cannot_admit_another_lifecycle_family(mode, behavior_type):
    async def run():
        client, calls = setup(mode, parsed_change={"behaviorType": behavior_type})
        async with managed(client, mode):
            author = (AsyncAuthoring if mode == "async" else Authoring)(client)
            with pytest.raises(UnsupportedOperationError):
                await call(author, mode, "parse", SOURCE, kind="BEHAVIOR")
            assert not author.created
        assert len(calls) == 2 and not any(r.method == "PUT" for r in calls)
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("changes,error", [({"@CLASS": "wrong"}, ProtocolError),
    ({"sourceCode": SOURCE.strip()}, ProtocolError), ({"urn": "../escape"}, ProtocolError),
    ({"notifications": [{"level": "ERROR", "message": "parser rejected source"}]}, ServerError)])
def test_behavior_parser_errors_never_create_a_draft(mode, changes, error):
    async def run():
        client, calls = setup(mode, parsed_change=changes)
        async with managed(client, mode):
            author = (AsyncAuthoring if mode == "async" else Authoring)(client)
            with pytest.raises(error):
                await call(author, mode, "parse", SOURCE, kind="BEHAVIOR")
        assert not any(r.method == "PUT" for r in calls)
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("changed,error", [("subtype", UnsupportedOperationError),
    ("source-selector", UnsupportedOperationError), ("consent", InvalidRequestError),
    ("foreign-source", InvalidRequestError)])
def test_draft_add_rechecks_subtype_existing_source_identity_and_consent_before_write(mode, changed, error):
    async def run():
        client, calls = setup(mode)
        async with managed(client, mode):
            author = (AsyncAuthoring if mode == "async" else Authoring)(client)
            parsed = await call(author, mode, "parse", SOURCE, kind="BEHAVIOR")
            project = await owned_project(author, mode)
            if changed == "subtype":
                parsed = replace(parsed, raw={**parsed.raw, "behaviorType": "SCRIPT"})
            elif changed == "source-selector":
                parsed = replace(parsed, raw={**parsed.raw, "metadata": {"klab.authoring.source": "existing"}})
            elif changed == "foreign-source":
                parsed = replace(parsed, service_id="other-resources")
            count = len(calls)
            with pytest.raises(error):
                await call(author, mode, "create_document", parsed, project, allow_write=changed != "consent")
            assert len(calls) == count and len(author.created) == 2
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("options,error", [({"result": []}, SubmissionOutcomeUnknown),
    ({"status": 500}, SubmissionOutcomeUnknown), ({"drop": True}, SubmissionOutcomeUnknown),
    ({"status": 403}, AuthorizationError),
    ({"result": [{"empty": False, "behaviors": [{"knowledgeClass": "BEHAVIOR", "resourceUrn": "python.authoring",
      "operation": "UPDATE", "serviceId": "resources-id"}]}]}, SubmissionOutcomeUnknown)])
def test_behavior_add_unknown_or_denied_outcome_is_not_confirmation_or_automatic_replay(mode, options, error):
    async def run():
        client, calls = setup(mode, **options)
        async with managed(client, mode):
            author = (AsyncAuthoring if mode == "async" else Authoring)(client)
            parsed = await call(author, mode, "parse", SOURCE, kind="BEHAVIOR")
            project = await owned_project(author, mode)
            with pytest.raises(error):
                await call(author, mode, "create_document", parsed, project, allow_write=True)
            assert len(author.created) == 2
        assert sum("/submit/BEHAVIOR/ADD" in r.url.path for r in calls) == 1
        assert not any("instantiate" in r.url.path or "unlock" in r.url.path for r in calls)
    asyncio.run(run())
