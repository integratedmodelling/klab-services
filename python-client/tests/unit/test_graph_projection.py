"""HTTP protocol doubles, not real Java graph/recovery acceptance."""
from dataclasses import FrozenInstanceError, replace
from pathlib import Path

import pytest

from klab_client import GraphBrowser, GraphProjection, GraphProjectionView, ProjectedGraphPage
from klab_client.errors import AuthorizationError, InvalidRequestError, ProtocolError
from test_graph import setup


def test_explicit_load_always_rechecks_membership_never_serves_a_cache_hit():
    api, ctx, obs, calls = setup()
    with api:
        projection = GraphProjection(GraphBrowser(ctx))
        first = projection.load(obs)
        second = projection.load(obs)
        view = projection.view
    assert isinstance(first, ProjectedGraphPage) and isinstance(view, GraphProjectionView)
    assert len(calls) == 4 and len(view.pages) == 1
    assert view.retained_json_bytes == first.retained_json_bytes == second.retained_json_bytes
    assert first.page.partial and view.partial
    assert "no revision lease" in view.consistency
    assert "NOT_RECHECKED" in view.current_authorization
    assert not first.invalidated and view.invalidation_reason is None
    with pytest.raises(FrozenInstanceError):
        first.invalidated = True


@pytest.mark.parametrize("kwargs", [dict(browser=None), dict(max_pages=True), dict(max_pages=0),
    dict(max_pages=33), dict(max_retained_json_bytes=False), dict(max_retained_json_bytes=0),
    dict(max_retained_json_bytes=16777217)])
def test_bad_budgets_or_browser_rejected_without_requests(kwargs):
    api, ctx, _, calls = setup()
    with api, pytest.raises(InvalidRequestError):
        GraphProjection(**(dict(browser=GraphBrowser(ctx)) | kwargs))
    assert calls == []


@pytest.mark.parametrize("reason", ["EXPLICIT_REFRESH", "NOTIFICATION_HINT", "TRANSPORT_LOST", "READ_FAILED"])
def test_invalidation_is_local_and_only_explicitly_refetched_pages_clear_the_marker(reason):
    api, ctx, obs, calls = setup()
    with api:
        projection = GraphProjection(GraphBrowser(ctx))
        old = projection.load(obs)
        projection.load(obs, offset=1)
        projection.invalidate(reason)
        view = projection.view
        assert all(entry.invalidated for entry in view.pages)
        assert view.invalidation_reason == reason and len(calls) == 4
        assert old.invalidated is False  # An immutable old view is not live state.
        projection.load(obs)
        updated = projection.view
    assert len(calls) == 6
    assert [entry.invalidated for entry in updated.pages] == [True, False]
    assert updated.pages[-1].page.offset == 0
    assert updated.consistency.startswith("UNKNOWN")


@pytest.mark.parametrize("reason", [None, [], "secret-token"])
def test_invalidation_reason_is_a_bounded_nonsecret_enum(reason):
    api, ctx, _, _ = setup()
    with api:
        projection = GraphProjection(GraphBrowser(ctx))
        with pytest.raises(InvalidRequestError) as caught:
            projection.invalidate(reason)
        assert "secret-token" not in str(caught.value)
        assert projection.view.generation == 0


def test_fifo_page_limit_and_query_selection_identity():
    api, ctx, obs, calls = setup()
    with api:
        projection = GraphProjection(GraphBrowser(ctx), max_pages=2)
        projection.load(obs)
        projection.load(obs, direction="incoming", relationships=("MEASURED",))
        projection.load(obs, offset=4)
        view = projection.view
    assert len(view.pages) == 2 and len(calls) == 6
    assert view.pages[0].page.direction == "incoming"
    assert view.pages[0].page.relationships == ("MEASURED",)
    assert view.pages[1].page.offset == 4


def test_exact_byte_limit_then_new_page_evicts_without_exceeding_budget():
    api, ctx, obs, _ = setup()
    with api:
        size = GraphProjection(GraphBrowser(ctx)).load(obs).retained_json_bytes
        projection = GraphProjection(GraphBrowser(ctx), max_retained_json_bytes=size)
        projection.load(obs)
        projection.load(obs, offset=1)
        view = projection.view
    assert len(view.pages) == 1 and view.retained_json_bytes == size
    assert view.pages[0].page.offset == 1


def test_too_large_page_is_not_retained_or_called_a_partial_success():
    api, ctx, obs, calls = setup()
    with api:
        projection = GraphProjection(GraphBrowser(ctx), max_retained_json_bytes=1)
        with pytest.raises(ProtocolError):
            projection.load(obs)
        assert projection.view.pages == ()
        assert projection.view.retained_json_bytes == 0
        assert projection.view.invalidation_reason == "READ_FAILED"
    assert len(calls) == 2  # Retention limit checked after bounded browser response.


@pytest.mark.parametrize("error", [AuthorizationError("Denied"), KeyboardInterrupt()])
def test_read_error_or_cancellation_propagates_instead_of_returning_old_data(monkeypatch, error):
    api, ctx, obs, calls = setup()
    with api:
        browser = GraphBrowser(ctx)
        projection = GraphProjection(browser)
        projection.load(obs)
        def fail(*_, **__):
            raise error
        monkeypatch.setattr(browser, "neighbors", fail)
        with pytest.raises(type(error)):
            projection.load(obs)
        view = projection.view
    assert view.invalidation_reason == "READ_FAILED" and view.pages[0].invalidated
    assert len(calls) == 2


def test_real_http_denial_does_not_become_a_cache_hit():
    api, ctx, obs, calls = setup(status=403)
    with api:
        projection = GraphProjection(GraphBrowser(ctx))
        with pytest.raises(AuthorizationError):
            projection.load(obs)
        assert projection.view.pages == ()
    assert len(calls) == 2


@pytest.mark.parametrize("when", ["before", "during"])
def test_identity_drift_is_rejected_not_rebound_to_cached_pages(monkeypatch, when):
    api, ctx, obs, _ = setup()
    with api:
        browser = GraphBrowser(ctx)
        projection = GraphProjection(browser)
        original = browser.neighbors
        def changed(*args, **kwargs):
            page = original(*args, **kwargs)
            browser.reference = replace(browser.reference, runtime_id="different")
            return page
        if when == "before":
            browser.reference = replace(browser.reference, runtime_id="different")
        else:
            monkeypatch.setattr(browser, "neighbors", changed)
        with pytest.raises(ProtocolError):
            projection.load(obs)
        with pytest.raises(ProtocolError):
            _ = projection.view
        projection.clear()  # Forgetting local data remains possible after identity drift.


def test_invalidation_during_fetch_marks_the_returned_page_too(monkeypatch):
    api, ctx, obs, _ = setup()
    with api:
        browser = GraphBrowser(ctx)
        projection = GraphProjection(browser)
        original = browser.neighbors
        def invalidated(*args, **kwargs):
            page = original(*args, **kwargs)
            projection.invalidate("NOTIFICATION_HINT")
            return page
        monkeypatch.setattr(browser, "neighbors", invalidated)
        page = projection.load(obs)
        assert page.invalidated and projection.view.pages[0].invalidated


def test_unserializable_page_fails_closed_without_retention(monkeypatch):
    api, ctx, obs, _ = setup()
    with api:
        browser = GraphBrowser(ctx)
        page = browser.neighbors(obs)
        bad = replace(page, anchor=replace(page.anchor, raw={"bad": object()}))
        monkeypatch.setattr(browser, "neighbors", lambda *_, **__: bad)
        projection = GraphProjection(browser)
        with pytest.raises(ProtocolError):
            projection.load(obs)
        assert projection.view.pages == ()


def test_clear_is_local_and_idempotent_never_context_disposal():
    api, ctx, obs, calls = setup()
    with api:
        projection = GraphProjection(GraphBrowser(ctx))
        held = projection.load(obs)
        projection.clear()
        projection.clear()
        view = projection.view
    assert view.pages == () and view.retained_json_bytes == 0
    assert held.page.links  # Immutable evidence held by the caller is unchanged.
    assert view.generation == 2 and len(calls) == 2


def test_older_inflight_read_cannot_replace_a_post_invalidation_refresh(monkeypatch):
    api, ctx, obs, calls = setup()
    with api:
        browser = GraphBrowser(ctx)
        projection = GraphProjection(browser)
        original = browser.neighbors
        nested = []
        def reordered(*args, **kwargs):
            page = original(*args, **kwargs)
            if not nested:
                nested.append(None)
                projection.invalidate("TRANSPORT_LOST")
                nested[0] = projection.load(obs)
            return page
        monkeypatch.setattr(browser, "neighbors", reordered)
        old = projection.load(obs)
        view = projection.view
    assert old.invalidated and old.generation == 0
    assert view.pages[0].generation == 1 and not view.pages[0].invalidated
    assert view.pages[0].page is nested[0].page and view.pages[0].page is not old.page
    assert len(view.pages) == 1 and len(calls) == 4


def test_cached_view_rejects_changed_focused_scope_without_network_access():
    api, ctx, obs, calls = setup()
    with api:
        projection = GraphProjection(GraphBrowser(ctx))
        projection.load(obs)
        ctx.context_id = "s.c.8"
        with pytest.raises(ProtocolError):
            _ = projection.view
        projection.clear()
    assert len(calls) == 2


def test_all_feature_coverage_steps_keep_their_explicit_working_directory():
    root = Path(__file__).resolve().parents[3]
    workflow = (root / ".github/workflows/python-client.yml").read_text(encoding="utf-8")
    steps = [step for step in workflow.split("      - ")
             if "run: python tools/check_feature_coverage.py" in step]
    assert len(steps) == 10
    assert all("\n        working-directory: python-client\n" in step for step in steps)
