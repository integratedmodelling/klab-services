"""Source-derived graph contracts, not a running Java graph acceptance claim."""
from dataclasses import replace
import json

import httpx
import pytest

from klab_client import Client, Context, GraphBrowser
from klab_client.dto import observation_from_wire
from klab_client.errors import (InvalidRequestError, MissingAssetError, ProtocolError,
                               UnsupportedOperationError, AuthorizationError)
from klab_client.provenance import CLASSES, _node
from test_provenance import payload, link, OBS


def setup(*, rows=None, membership=None, status=200):
    calls = []
    def handler(request):
        query = json.loads(request.content)
        calls.append(query)
        assert request.method == "POST" and request.url.path == "/runtime/api/v1/query"
        assert request.headers["klab-scope"] == "s.c" and request.headers["klab-service"] == "home"
        assert request.headers["authorization"] == "issued-secret"
        if query["resultType"] != "LINK":
            default = payload() if query["resultType"] == "OBSERVATION" else link()["source"]
            return httpx.Response(200, json=[default] if membership is None else membership)
        return httpx.Response(status, json=[link()] if rows is None else rows)
    client = Client("https://runtime.invalid/runtime", "issued-secret", runtime_service_id="home",
                    http_transport=httpx.MockTransport(handler))
    context = Context(client, {"id": "s.c", "serviceId": "home"})
    observation = observation_from_wire(payload(), context)
    return client, context, observation, calls


@pytest.mark.parametrize("direction", ["both", "incoming", "outgoing"])
def test_navigate_observation_to_activity_with_explicit_pages(direction):
    row = link()
    if direction == "outgoing":
        row["source"], row["target"] = row["target"], row["source"]
    api, ctx, obs, calls = setup(rows=[row, row])
    with api:
        browser = GraphBrowser(ctx, max_links=1)
        page = browser.neighbors(obs, direction=direction, relationships=["MEASURED"], offset=3)
        assert len(page.links) == 1 and page.next_offset == 4 and page.partial
        assert page.context.runtime_id == "home" and page.anchor.kind == "observation"
        assert page.fetched_at.tzinfo is not None and "No revision lease" in page.consistency
        activity = page.links[0].target if direction == "outgoing" else page.links[0].source
        second = browser.neighbors(activity, relationships=("MEASURED",))
        assert second.anchor.kind == "activity"
        assert len(calls) == 4 and calls[2]["resultType"] == "ACTIVITY"
        assert all(q.get("limit") == 2 for q in calls)
        with pytest.raises(TypeError):
            page.links[0].properties["support"] = "changed"


def test_default_filter_is_bounded_no_recursive_auto_paging_and_empty_is_partial():
    api, ctx, obs, calls = setup(rows=[])
    with api:
        page = GraphBrowser(ctx).neighbors(obs)
    assert page.links == () and page.next_offset is None and page.partial
    query = calls[1]
    assert query["type"] == "OR" and len(query["children"]) < 128
    assert query["limit"] == 65 and len(calls) == 2
    assert all(c["depth"] == c["minimumDepth"] == 1 for c in query["children"])


@pytest.mark.parametrize("kind,relationship", [("actuator", "CONTEXTUALIZED_BY"), ("cohort", "HAS_MEMBER")])
def test_other_admitted_nodes_use_typed_membership_and_link_queries(kind, relationship):
    descriptor = {"@CLASS": next(c for c, k in CLASSES.items() if k == kind), "id": 77, "urn": "node.77"}
    row = link(source=descriptor, relationship=relationship)
    api, ctx, _, calls = setup(membership=[descriptor], rows=[row])
    with api:
        page = GraphBrowser(ctx).neighbors(_node(descriptor, api.transport.redact),
            direction="outgoing", relationships=[relationship])
    assert page.anchor.kind == kind and calls[0]["resultType"] == kind.upper()
    assert calls[1]["source"] == {"type": kind.upper(), "id": 77}


def test_wrong_direction_is_not_treated_as_an_incident_link():
    api, ctx, obs, _ = setup()
    with api, pytest.raises(ProtocolError):
        GraphBrowser(ctx).neighbors(obs, direction="outgoing")


def test_properties_are_redacted_before_exposing_them():
    api, ctx, obs, _ = setup(rows=[link(properties={"token": "private", "info": "issued-secret"})])
    with api:
        props = GraphBrowser(ctx).neighbors(obs).links[0].properties
    assert "token" not in props and props["info"] == "[REDACTED]"


def test_detached_mediated_view_is_not_a_native_graph_anchor():
    api, ctx, obs, calls = setup()
    obs.metadata["im:storage-binding-source"] = "native-source"
    with api, pytest.raises(UnsupportedOperationError):
        GraphBrowser(ctx).neighbors(obs)
    assert not calls


def test_returned_node_with_nested_immutable_metadata_can_be_navigated_again():
    activity = {**link()["source"], "metadata": {"future": [{"values": [1, None, False]}]}}
    api, ctx, obs, calls = setup(rows=[link(source=activity)], membership=None)
    with api:
        browser = GraphBrowser(ctx)
        page = browser.neighbors(obs)
        node = page.links[0].source
        assert node.raw["metadata"]["future"][0]["values"] == (1, None, False)
        with pytest.raises(TypeError):
            node.raw["metadata"]["future"][0]["changed"] = True
        refreshed = browser.neighbors(node)
        assert refreshed.anchor.id == node.id and len(calls) == 4


def test_malformed_user_authored_raw_node_is_rejected_before_request():
    api, ctx, _, calls = setup()
    with api:
        node = replace(_node(link()["source"], api.transport.redact), raw=None)
        with pytest.raises(ProtocolError):
            GraphBrowser(ctx).neighbors(node)
    assert not calls


@pytest.mark.parametrize("changes", [{"max_links": 0}, {"max_links": True}, {"max_links": 129},
    {"max_response_bytes": 0}, {"max_response_bytes": True}, {"max_response_bytes": 16777217}])
def test_bad_budgets_send_nothing(changes):
    api, ctx, _, calls = setup()
    with api, pytest.raises(InvalidRequestError):
        GraphBrowser(ctx, **changes)
    assert not calls


def test_unbound_context_and_unknown_anchor_rejected():
    with pytest.raises(InvalidRequestError):
        GraphBrowser(None)
    api, ctx, _, calls = setup()
    with api:
        browser = GraphBrowser(ctx)
        with pytest.raises(InvalidRequestError):
            browser.neighbors(42)
    assert not calls


@pytest.mark.parametrize("kwargs", [{"direction": "sideways"}, {"direction": []}, {"offset": -1},
    {"offset": True}, {"offset": 2**31}, {"relationships": "MEASURED"}, {"relationships": []},
    {"relationships": ["RAW_CYPHER"]}, {"relationships": [[]]}, {"relationships": ["MEASURED", "MEASURED"]}])
def test_invalid_selection_never_reaches_server(kwargs):
    api, ctx, obs, calls = setup()
    with api, pytest.raises(InvalidRequestError):
        GraphBrowser(ctx).neighbors(obs, **kwargs)
    assert not calls


@pytest.mark.parametrize("mode", ["unbound", "context", "client", "node"])
def test_wrong_anchor_cannot_cross_context_or_lie_about_descriptor(mode):
    api, ctx, obs, calls = setup()
    with api:
        browser = GraphBrowser(ctx)
        if mode == "node":
            anchor = replace(_node(link()["source"], api.transport.redact), id=999)
        else:
            anchor = obs
            if mode == "unbound":
                anchor._context = None
            elif mode == "context":
                anchor._context = Context(api, {"id": "s.other"})
            else:
                with Client("https://other.invalid") as other:
                    anchor._context = Context(other, {"id": "s.c"})
        with pytest.raises(InvalidRequestError):
            browser.neighbors(anchor)
    assert not calls


@pytest.mark.parametrize("rows,error", [([], MissingAssetError), ({}, ProtocolError),
    ([payload(), payload()], ProtocolError), ([{**payload(), "id": 99}], ProtocolError),
    ([{**payload(), "urn": "different"}], ProtocolError)])
def test_membership_is_rechecked_and_identity_cannot_change(rows, error):
    api, ctx, obs, calls = setup(membership=rows)
    with api, pytest.raises(error):
        GraphBrowser(ctx).neighbors(obs)
    assert len(calls) == 1


@pytest.mark.parametrize("rows", [{}, [link(), link(), link()], [None],
    [link(relationship="OTHER")], [link(sequence=True)], [link(properties=[])],
    [link(target={"@CLASS": OBS, "id": 99, "urn": "different"})]])
def test_malformed_over_budget_or_nonincident_links_are_not_success(rows):
    api, ctx, obs, calls = setup(rows=rows)
    with api, pytest.raises(ProtocolError):
        GraphBrowser(ctx, max_links=1).neighbors(obs)
    assert len(calls) == 2


@pytest.mark.parametrize("status,error", [(422, UnsupportedOperationError), (400, InvalidRequestError), (403, AuthorizationError)])
def test_query_failure_never_becomes_empty_page(status, error):
    api, ctx, obs, _ = setup(status=status)
    with api, pytest.raises(error):
        GraphBrowser(ctx).neighbors(obs)


@pytest.mark.parametrize("during", [False, True])
def test_scope_identity_drift_fails_without_mislabeling_page(during):
    api, ctx, obs, calls = setup()
    with api:
        browser = GraphBrowser(ctx)
        if during:
            original = api.transport.request
            def changed(*args, **kwargs):
                result = original(*args, **kwargs)
                ctx.context_id = "s.c.8"
                return result
            api.transport.request = changed
        else:
            ctx.context_id = "s.c.8"
        with pytest.raises(ProtocolError):
            browser.neighbors(obs)
    assert len(calls) == (1 if during else 0)
