"""Actual isolated HTTP graph refresh with honest stale/authorization boundaries."""
import httpx
import pytest

from klab_client import Client, GraphBrowser, GraphProjection, ObservationRequest
from klab_client.errors import AuthorizationError, ServerError, TransportError
from test_vertical import client_args, control, geometry


def fault(ready, scope, mode):
    response = httpx.post(ready["endpoints"]["runtime"].rsplit("/", 1)[0] + "/__sim__/queryFault",
        headers={"Authorization": "sim-admin-control"}, json={"scope": scope, "mode": mode},
        trust_env=False, timeout=3)
    response.raise_for_status()


@pytest.mark.parametrize("mode,error", [("denied", AuthorizationError), ("http500", ServerError), ("disconnect", TransportError)])
def test_failed_refresh_invalidates_all_pages_then_only_explicit_page_becomes_fresh(simulator, mode, error):
    with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
        session = client.create_session()
        context = session.create_context()
        try:
            proposal = ObservationRequest("geography:Elevation in m", geometry=geometry())
            observations = [context.observe(proposal).result(timeout=2) for _ in range(2)]
            projection = GraphProjection(GraphBrowser(context), max_pages=2)
            for observation in observations:
                page = projection.load(observation, relationships=("MEASURED",))
                assert len(page.page.links) == 1
            assert len(projection.view.pages) == 2 and not any(page.invalidated for page in projection.view.pages)
            before = control(simulator)
            fault(simulator, context.id, mode)
            with pytest.raises(error):
                projection.load(observations[0], relationships=("MEASURED",))
            failed = projection.view
            assert all(page.invalidated for page in failed.pages)
            assert failed.partial and failed.invalidation_reason == "READ_FAILED"
            assert "UNKNOWN" in failed.consistency and "NOT_RECHECKED" in failed.current_authorization
            # Retained pages are readable as stale local evidence, never a successful refresh.
            assert len(failed.pages) == 2 and all(page.page.partial for page in failed.pages)
            assert control(simulator)["counts"] == before["counts"]
            fault(simulator, context.id, "none")
            refreshed = projection.load(observations[0], relationships=("MEASURED",))
            assert not refreshed.invalidated
            view = projection.view
            assert sum(page.invalidated for page in view.pages) == 1  # Not a global resync.
            assert view.partial and "UNKNOWN" in view.consistency
            report = control(simulator)
            assert sum(row["path"].endswith("/submit") for row in report["trace"]) == 2
            if mode == "disconnect":
                failures = [row for row in report["trace"] if row["transport_outcome"] == "DISCONNECTED_BY_TEST"]
                assert len(failures) == 1 and failures[0]["status"] is None
            projection.clear()
            assert not projection.view.pages
            assert control(simulator)["counts"] == before["counts"]  # Local clear is not disposal.
        finally:
            context.release()
            session.release()
    assert not any(control(simulator)["counts"].values())
