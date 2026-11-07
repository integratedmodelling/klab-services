"""Public typed Resolver planning preserves all observe inputs without execution."""
import asyncio
import json

import httpx
import pytest

from klab_client import Endpoint, ObservationOptions, ObservationRequest
from klab_client.dto import observable_from_wire
from klab_client.errors import AuthorizationError, InvalidRequestError
from test_selections import client, root, wire, offline


@pytest.mark.parametrize("native_async", [False, True])
@pytest.mark.parametrize("form", ["text", "resolved", "request"])
@pytest.mark.parametrize("observer", [None, 7])
@pytest.mark.parametrize("relationship", [False, True])
def test_typed_planning_preserves_selection_options_without_runtime_submission(native_async, form, observer, relationship):
    calls = []
    declared = wire()["observable"]
    declared["@CLASS"] = "org.integratedmodelling.common.knowledge.ObservableImpl"
    options = ObservationOptions(namespace="test.ns", project="test.project", scenarios=("test.scenario",), observer=observer)
    resolved = observable_from_wire(declared)
    proposal = declared["urn"] if form == "text" else resolved if form == "resolved" else ObservationRequest(resolved, options=options)
    kwargs = {} if form == "request" else {"options": options}
    def handler(request):
        calls.append(request)
        assert request.headers["Authorization"] == "current-secret"
        if request.url.host == "reasoner.test":
            assert form == "text" and request.content.decode() == declared["urn"]
            return httpx.Response(200, json=declared)
        assert request.url.host == "resolver.test"
        assert request.headers["klab-scope"] == ("s.c#7" if observer else "s.c")
        assert request.headers["klab-service"] == "runtime-id"
        if relationship and request.method == "POST":
            assert request.headers["klab-source-observation"] == "1" and request.headers["klab-target-observation"] == "2"
        else: assert "klab-source-observation" not in request.headers
        if request.url.path.endswith("/resolve"):
            data = json.loads(request.content)
            expected = {"ResolutionNamespace": ["test.ns"], "ResolutionProject": ["test.project"], "Scenarios": ["test.scenario"]}
            if observer: expected["Observer"] = [observer]
            assert {row["type"]: row["data"] for row in data["resolutionConstraints"]} == expected
            assert data["observation"]["observable"] == declared
            return httpx.Response(200, text="5")
        if "/status/" in request.url.path: return httpx.Response(200, json={"status": "FINISHED"})
        assert "/retrieve/" in request.url.path
        return httpx.Response(200, json={"selection": {"model": "finite-reference"}, "empty": False})
    peers = {"reasoner": Endpoint("https://reasoner.test", "current-secret"), "resolver": Endpoint("https://resolver.test", "current-secret")}
    async def run():
        async with client(handler, True, **peers) as api:
            context = root(api); selected = context.between(1, 2) if relationship else context
            job = await selected.resolve(proposal, **kwargs)
            assert job.service == "resolver" and job.result_kind == "dataflow"
            assert job.scope.relationship_selection == selected.relationship_selection
            assert (await job.result(timeout=2))["selection"]["model"] == "finite-reference"
            assert context.relationship_selection is None
    if native_async: asyncio.run(run())
    else:
        with client(handler, **peers) as api:
            context = root(api); selected = context.between(1, 2) if relationship else context
            job = selected.resolve(proposal, **kwargs)
            assert job.service == "resolver" and job.result_kind == "dataflow"
            assert job.scope.relationship_selection == selected.relationship_selection
            assert job.result(timeout=2)["selection"]["model"] == "finite-reference"
            assert context.relationship_selection is None
    assert sum(request.url.path.endswith("/resolve") for request in calls) == 1
    assert all(request.url.host != "runtime.test" for request in calls)


@pytest.mark.parametrize("native_async", [False, True])
def test_failed_typed_planning_never_falls_back_to_runtime_or_retries(native_async):
    calls = []
    def handler(request):
        calls.append(request)
        assert request.url.host == "reasoner.test"
        return httpx.Response(403, json={"message": "denied"})
    peers = {"reasoner": Endpoint("https://reasoner.test", "current-secret"), "resolver": Endpoint("https://resolver.test", "current-secret")}
    async def run():
        async with client(handler, True, **peers) as api:
            with pytest.raises(AuthorizationError): await root(api).resolve("synthetic:Signal in m")
    if native_async: asyncio.run(run())
    else:
        with client(handler, **peers) as api:
            with pytest.raises(AuthorizationError): root(api).resolve("synthetic:Signal in m")
    assert len(calls) == 1


@pytest.mark.parametrize("native_async", [False, True])
def test_typed_planning_refuses_mutated_foreign_root_before_http(native_async):
    async def run():
        async with client(offline, True) as api:
            context = root(api); context.context_id = "other.root"
            with pytest.raises(InvalidRequestError): await context.resolve("synthetic:Signal in m")
    if native_async: asyncio.run(run())
    else:
        with client(offline) as api:
            context = root(api); context.context_id = "other.root"
            with pytest.raises(InvalidRequestError): context.resolve("synthetic:Signal in m")
