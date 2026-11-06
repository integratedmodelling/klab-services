"""Native awaited bounded graph browsing; not a snapshot or revision lease."""
import klab_client
import asyncio

import pytest

from klab_client import AsyncGraphBrowser, AsyncGraphProjection, GraphBrowser, GraphProjection
from klab_client.errors import AuthorizationError, InvalidRequestError, MissingAssetError, ProtocolError, UnsupportedOperationError
from test_graph import setup
from test_provenance import payload, link, OBS


def test_native_awaited_graph_browser_is_public():
    assert hasattr(klab_client, "AsyncGraphBrowser")


def test_native_awaited_graph_projection_is_public():
    assert hasattr(klab_client, "AsyncGraphProjection")


@pytest.mark.parametrize("direction", ["both", "incoming", "outgoing"])
def test_awaited_graph_membership_navigation_and_explicit_bounded_pages(direction):
    async def run():
        row = link()
        if direction == "outgoing":
            row["source"], row["target"] = row["target"], row["source"]
        client, context, observation, calls = setup(async_mode=True, rows=[row, row])
        async with client:
            browser = AsyncGraphBrowser(context, max_links=1)
            page = await browser.neighbors(observation, direction=direction, relationships=["MEASURED"], offset=3)
            assert page.next_offset == 4 and len(page.links) == 1 and page.partial
            assert page.context.runtime_id == "home" and "No revision lease" in page.consistency
            activity = page.links[0].target if direction == "outgoing" else page.links[0].source
            second = await browser.neighbors(activity, relationships=("MEASURED",))
            assert second.anchor.kind == "activity" and len(calls) == 4
            assert calls[2]["resultType"] == "ACTIVITY" and all(q["limit"] == 2 for q in calls)
            with pytest.raises(TypeError):
                page.links[0].properties["new"] = "changed"
    asyncio.run(run())


def test_empty_awaited_graph_is_an_explicit_partial_page_not_auto_paged():
    async def run():
        client, context, observation, calls = setup(async_mode=True, rows=[])
        async with client:
            page = await AsyncGraphBrowser(context).neighbors(observation)
        assert page.links == () and page.partial and page.next_offset is None
        assert len(calls) == 2 and calls[1]["limit"] == 65
    asyncio.run(run())


@pytest.mark.parametrize("kwargs", [{"direction": "sideways"}, {"offset": True}, {"offset": -1},
    {"relationships": []}, {"relationships": ["MEASURED", "MEASURED"]}, {"relationships": ["RAW_CYPHER"]}])
def test_invalid_awaited_selection_is_zero_http(kwargs):
    async def run():
        client, context, observation, calls = setup(async_mode=True)
        async with client:
            with pytest.raises(InvalidRequestError):
                await AsyncGraphBrowser(context).neighbors(observation, **kwargs)
        assert not calls
    asyncio.run(run())


def test_explicit_facades_and_budgets_prevent_coroutines_in_sync_projection():
    with pytest.raises(InvalidRequestError):
        AsyncGraphBrowser(None)
    with pytest.raises(InvalidRequestError):
        AsyncGraphProjection(None)
    client, context, _, calls = setup()
    with client:
        with pytest.raises(InvalidRequestError):
            AsyncGraphBrowser(context)
        with pytest.raises(InvalidRequestError):
            AsyncGraphProjection(GraphBrowser(context))
    assert not calls
    async def run():
        client, context, _, calls = setup(async_mode=True)
        async with client:
            with pytest.raises(InvalidRequestError):
                GraphBrowser(context)
            with pytest.raises(InvalidRequestError):
                AsyncGraphBrowser(context, max_links=True)
            browser = AsyncGraphBrowser(context)
            with pytest.raises(InvalidRequestError):
                GraphProjection(browser)
            with pytest.raises(InvalidRequestError):
                AsyncGraphProjection(browser, max_pages=0)
        assert not calls
    asyncio.run(run())


@pytest.mark.parametrize("rows,error", [([], MissingAssetError), ({}, ProtocolError),
    ([payload(), payload()], ProtocolError), ([{**payload(), "id": 99}], ProtocolError)])
def test_awaited_membership_is_current_and_cannot_be_invented(rows, error):
    async def run():
        client, context, observation, calls = setup(async_mode=True, membership=rows)
        async with client:
            with pytest.raises(error):
                await AsyncGraphBrowser(context).neighbors(observation)
        assert len(calls) == 1
    asyncio.run(run())


@pytest.mark.parametrize("rows", [{}, [link(), link(), link()], [None], [link(relationship="OTHER")],
    [link(sequence=True)], [link(properties=[])], [link(target={"@CLASS": OBS, "id": 99, "urn": "different"})]])
def test_awaited_bad_or_unrelated_links_do_not_become_pages(rows):
    async def run():
        client, context, observation, calls = setup(async_mode=True, rows=rows)
        async with client:
            with pytest.raises(ProtocolError):
                await AsyncGraphBrowser(context, max_links=1).neighbors(observation)
        assert len(calls) == 2
    asyncio.run(run())


@pytest.mark.parametrize("status,error", [(422, UnsupportedOperationError), (400, InvalidRequestError), (403, AuthorizationError)])
def test_awaited_query_errors_never_become_empty_success(status, error):
    async def run():
        client, context, observation, calls = setup(async_mode=True, status=status)
        async with client:
            with pytest.raises(error):
                await AsyncGraphBrowser(context).neighbors(observation)
        assert len(calls) == 2
    asyncio.run(run())


@pytest.mark.parametrize("phase", [0, 1, 2])
def test_scope_drift_before_or_across_await_is_not_a_confirmed_page(phase):
    async def run():
        client, context, observation, calls = setup(async_mode=True)
        async with client:
            browser = AsyncGraphBrowser(context)
            original = client.transport.request
            async def changed(*args, **kwargs):
                result = await original(*args, **kwargs)
                if len(calls) == phase:
                    context.context_id = "s.c.7"
                return result
            client.transport.request = changed
            if phase == 0:
                context.context_id = "s.c.7"
            with pytest.raises(ProtocolError):
                await browser.neighbors(observation)
        assert len(calls) == phase
    asyncio.run(run())


def test_mutable_relationship_selection_is_snapshotted_before_membership_await():
    async def run():
        client, context, observation, calls = setup(async_mode=True)
        selected = ["MEASURED"]
        async with client:
            original = client.transport.request
            async def changed(*args, **kwargs):
                result = await original(*args, **kwargs)
                selected[:] = ["HAS_ACTIVITY"]
                return result
            client.transport.request = changed
            page = await AsyncGraphBrowser(context).neighbors(observation, relationships=selected)
        assert page.relationships == ("MEASURED",) and selected == ["HAS_ACTIVITY"]
        assert all(child["relationship"] == "MEASURED" for child in calls[1]["children"])
    asyncio.run(run())


@pytest.mark.parametrize("phase", [1, 2])
def test_cancelled_projection_refresh_invalidates_all_pages_and_refetches_only_one(phase):
    async def run():
        client, context, observation, calls = setup(async_mode=True)
        async with client:
            projection = AsyncGraphProjection(AsyncGraphBrowser(context))
            first = await projection.load(observation, offset=0)
            await projection.load(observation, offset=1)
            original, dispatched = client.transport.request, asyncio.Event()
            attempts = 0
            async def delayed(*args, **kwargs):
                nonlocal attempts
                attempts += 1
                if attempts == phase:
                    dispatched.set()
                    await asyncio.Event().wait()
                return await original(*args, **kwargs)
            client.transport.request = delayed
            task = asyncio.create_task(projection.load(observation))
            await asyncio.wait_for(dispatched.wait(), timeout=2)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
            view = projection.view
            assert len(view.pages) == 2 and all(p.invalidated for p in view.pages)
            assert view.invalidation_reason == "READ_FAILED" and "NOT_RECHECKED" in view.current_authorization
            assert "UNKNOWN" in view.consistency and first.page in [p.page for p in view.pages]
            assert len(calls) == 4 + phase - 1
            client.transport.request = original
            refreshed = await projection.load(observation)
            assert not refreshed.invalidated and sum(p.invalidated for p in projection.view.pages) == 1
            count = len(calls)
            projection.clear()
            assert not projection.view.pages and not projection.view.retained_json_bytes
            assert len(calls) == count
    asyncio.run(run())


def test_pre_invalidation_async_read_cannot_replace_later_refreshed_page():
    async def run():
        client, context, observation, calls = setup(async_mode=True)
        async with client:
            browser = AsyncGraphBrowser(context)
            projection = AsyncGraphProjection(browser)
            original, dispatched, finish = browser.neighbors, asyncio.Event(), asyncio.Event()
            first = True
            async def delayed(*args, **kwargs):
                nonlocal first
                is_first, first = first, False
                page = await original(*args, **kwargs)
                if is_first:
                    dispatched.set()
                    await finish.wait()
                return page
            browser.neighbors = delayed
            old = asyncio.create_task(projection.load(observation))
            await asyncio.wait_for(dispatched.wait(), timeout=2)
            projection.invalidate("NOTIFICATION_HINT")
            fresh = await projection.load(observation)
            finish.set()
            stale = await old
            assert stale.invalidated and stale.generation < fresh.generation
            assert projection.view.pages == (fresh,) and len(calls) == 4
    asyncio.run(run())


def test_awaited_projection_budget_error_is_not_retained_as_a_success():
    async def run():
        client, context, observation, _ = setup(async_mode=True)
        async with client:
            projection = AsyncGraphProjection(AsyncGraphBrowser(context), max_retained_json_bytes=1)
            with pytest.raises(ProtocolError):
                await projection.load(observation)
            assert projection.view.pages == () and projection.view.invalidation_reason == "READ_FAILED"
    asyncio.run(run())
