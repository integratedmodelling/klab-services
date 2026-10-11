"""Origin checks before a bound asset becomes a focus or scientific read."""
import json
from pathlib import Path

import httpx
import pytest

from klab_client import Client, Context
from klab_client.dto import observation_from_wire
from klab_client.errors import InvalidRequestError


def context(identity, kind, handler, url="https://same.invalid/runtime"):
    client = Client(url, runtime_service_id=identity if kind == "configured" else None,
        agent_name="scientist", http_transport=httpx.MockTransport(handler))
    if kind == "cached":
        client._service_id_cache = {"runtime": identity}
    configuration = {"id": "s.c"}
    if kind == "reported":
        configuration["serviceId"] = identity
    return Context(client, configuration)


def observation(bound):
    payload = json.loads((Path(__file__).parent / "fixtures/elevation.json").read_text())
    payload["urn"] = bound.id + ".42"
    return observation_from_wire(payload, bound)


def operate(destination, asset, operation):
    if operation == "focus":
        return destination.within(asset)
    return destination.fetch_data(asset, [0], curve="D1_LINEAR", slice=None, semantics=None)


@pytest.mark.parametrize("operation", ["focus", "read"])
@pytest.mark.parametrize("source_kind,destination_kind", [
    ("configured", "configured"), ("cached", "configured"), ("reported", "configured"),
    ("configured", "cached"), ("configured", "reported"), ("reported", "reported")])
def test_different_known_runtime_at_same_url_and_root_fails_before_http(operation, source_kind, destination_kind):
    calls = []
    source = context("old-runtime", source_kind, lambda r: pytest.fail("No source HTTP"))
    destination = context("new-runtime", destination_kind, lambda r: calls.append(r) or httpx.Response(200, text="12"))
    with source.client, destination.client:
        with pytest.raises(InvalidRequestError, match="Runtime"):
            operate(destination, observation(source), operation)
    assert not calls


@pytest.mark.parametrize("operation", ["focus", "read"])
@pytest.mark.parametrize("source_kind,destination_kind", [
    ("configured", "configured"), ("cached", "cached"), ("reported", "reported"),
    ("unknown", "unknown"), ("configured", "unknown"), ("unknown", "configured")])
def test_same_runtime_equivalent_urls_and_legacy_unknown_ids_remain_supported(operation, source_kind, destination_kind):
    calls = []
    source = context("home", source_kind, lambda r: pytest.fail("No source HTTP"), "HTTPS://SAME.invalid:443/runtime/")
    destination = context("home", destination_kind, lambda r: calls.append(r) or httpx.Response(200, text="12"))
    with source.client, destination.client:
        result = operate(destination, observation(source), operation)
        if operation == "focus":
            assert result.get_context_id() == "s.c.42" and not calls
        else:
            assert result.values == (12,) and result.units == "m"
            assert len(calls) == 1 and json.loads(calls[0].content)["sourceId"] == 42


@pytest.mark.parametrize("operation", ["focus", "read"])
@pytest.mark.parametrize("side", ["source", "destination"])
@pytest.mark.parametrize("kind", ["cache", "reported", "invalid"])
def test_contradictory_or_invalid_binding_identity_is_local(operation, side, kind):
    source = context("home", "configured", lambda r: pytest.fail("No source HTTP"))
    destination = context("home", "configured", lambda r: pytest.fail("No destination HTTP"))
    inconsistent = source if side == "source" else destination
    if kind == "cache":
        inconsistent.client._service_id_cache = {"runtime": "other"}
    else:
        inconsistent.configuration["serviceId"] = "other" if kind == "reported" else 7
    with source.client, destination.client:
        with pytest.raises(InvalidRequestError, match="Runtime"):
            operate(destination, observation(source), operation)


def test_focus_keeps_observer_suffix_without_inventing_raw_id_origin():
    destination = context("home", "configured", lambda r: pytest.fail("Focus is offline"))
    with destination.client:
        focused = Context(destination.client, destination.configuration, token="s.c.8#3").within(42)
        assert focused.get_context_id() == "s.c.8.42#3"


@pytest.mark.parametrize("operation", ["focus", "read"])
def test_different_root_context_is_rejected_before_http(operation):
    destination = context("home", "configured", lambda r: pytest.fail("No foreign-root HTTP"))
    with destination.client:
        foreign = Context(destination.client, {"id": "s.other"})
        with pytest.raises(InvalidRequestError, match="context"):
            operate(destination, observation(foreign), operation)
