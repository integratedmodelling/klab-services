"""Synthetic escaped credentials must not bypass decoded source/DTO preflight."""
import asyncio
from contextlib import asynccontextmanager

import httpx
import pytest

from klab_client import (AgentPreviews, AsyncAgentPreviews, AsyncAuthoring,
                         AsyncClient, AsyncSourcePreviews, Authoring, Client,
                         Endpoint, SourcePreviews)
from klab_client.agents import BEHAVIOR_CLASS
from klab_client.errors import InvalidRequestError

SOURCE = 'behavior protocol.draft\n    "Protocol only"\n    version 1.0\n\naction main:\n    return 0\n'
FACADES = {"authoring": (Authoring, AsyncAuthoring),
           "preview": (SourcePreviews, AsyncSourcePreviews),
           "agent": (AgentPreviews, AsyncAgentPreviews)}


@asynccontextmanager
async def managed(client, mode):
    if mode == "async":
        async with client:
            yield
    else:
        with client:
            yield


async def invoke(facade, mode, family, source):
    result = facade.preview(source) if family == "agent" else facade.parse(source, kind="BEHAVIOR")
    return await result if mode == "async" else result


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("family", FACADES)
@pytest.mark.parametrize("credential", ['resource"secret', 'resource\\secret'])
def test_escaped_configured_credential_in_source_is_rejected_before_any_http(mode, family, credential):
    calls = []
    def handler(request):
        calls.append(request)
        raise AssertionError("Secret-bearing source must fail before all HTTP")
    async def run():
        cls = AsyncClient if mode == "async" else Client
        client = cls("https://runtime.invalid", "runtime-secret",
            resources=Endpoint("https://resources.invalid", credential), http_transport=httpx.MockTransport(handler))
        async with managed(client, mode):
            facade = FACADES[family][int(mode == "async")](client)
            with pytest.raises(InvalidRequestError) as error:
                await invoke(facade, mode, family, SOURCE + "// " + credential)
            assert credential not in str(error.value)
        assert not calls
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("family", FACADES)
@pytest.mark.parametrize("credential", ['resource"secret', 'resource\\secret'])
@pytest.mark.parametrize("placement", ["value", "key"])
def test_escaped_parser_dto_credential_is_rejected_before_receipt_or_runtime_forwarding(mode, family, credential, placement):
    calls = []
    def handler(request):
        calls.append(request)
        if request.url.path == "/public/capabilities":
            return httpx.Response(200, json={"serviceId": request.url.host.split(".")[0] + "-id"})
        if request.url.path == "/parseAsset/BEHAVIOR":
            return httpx.Response(200, json={"@CLASS": BEHAVIOR_CLASS, "urn": "protocol.draft",
                "sourceCode": SOURCE, "serviceId": "resources-id", "behaviorType": "BEHAVIOR",
                "metadata": {"nested": [1, {"value": credential} if placement == "value" else {credential: "inert"}]}})
        raise AssertionError("Credential-bearing parser DTO must not reach Runtime translation")
    async def run():
        cls = AsyncClient if mode == "async" else Client
        client = cls("https://runtime.invalid", "runtime-secret",
            resources=Endpoint("https://resources.invalid", credential), http_transport=httpx.MockTransport(handler))
        async with managed(client, mode):
            facade = FACADES[family][int(mode == "async")](client)
            with pytest.raises(InvalidRequestError) as error:
                await invoke(facade, mode, family, SOURCE)
            assert credential not in str(error.value)
        assert not any("instantiate" in r.url.path or r.method == "PUT" for r in calls)
    asyncio.run(run())


@pytest.mark.parametrize("mode", ["sync", "async"])
@pytest.mark.parametrize("family", FACADES)
def test_nonsecret_quoted_source_and_metadata_are_not_rewritten_or_rejected(mode, family):
    calls = []
    harmless = 'ordinary "quoted" and \\ slashed text'
    def handler(request):
        calls.append(request)
        if request.url.path == "/public/capabilities":
            return httpx.Response(200, json={"serviceId": request.url.host.split(".")[0] + "-id"})
        if request.url.path == "/parseAsset/BEHAVIOR":
            return httpx.Response(200, json={"@CLASS": BEHAVIOR_CLASS, "urn": "protocol.draft",
                "sourceCode": SOURCE, "serviceId": "resources-id", "behaviorType": "BEHAVIOR",
                "metadata": {"nested": [harmless]}})
        return httpx.Response(200, json={"@CLASS": "org.integratedmodelling.common.runtime.actors.AgentImpl",
            "behaviorUrn": "protocol.draft", "viable": True, "alive": False, "startDeferred": False,
            "messagingConnected": False, "urn": None, "scopeId": None, "startedAt": -1,
            "observationId": -1, "javaCode": "public class Draft {}", "metadata": {"nested": [harmless]}})
    async def run():
        cls = AsyncClient if mode == "async" else Client
        client = cls("https://runtime.invalid", "runtime-secret",
            resources=Endpoint("https://resources.invalid", 'resource"secret'), http_transport=httpx.MockTransport(handler))
        async with managed(client, mode):
            facade = FACADES[family][int(mode == "async")](client)
            result = await invoke(facade, mode, family, SOURCE)
            assert result.raw["metadata"]["nested"] == (harmless,)
        parse = next(r for r in calls if r.url.path == "/parseAsset/BEHAVIOR")
        assert parse.content == SOURCE.encode()
    asyncio.run(run())
