"""Opt-in controlled Java graph navigation; not published-study reproduction."""
from decimal import Decimal
import json
import os

import pytest

from klab_client import Client, Endpoint, GraphBrowser, ObservationRequest, ObservationOptions
from klab_client.errors import ConfigurationError
from klab_client.experiment import rectangle_geometry, verify_grid

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_real_java_typed_graph_navigation(functional_state, record_property):
    if os.environ.get("KLAB_GRAPH_ALLOW_OWNED_WORK") != "1":
        raise ConfigurationError("Graph acceptance requires explicit owned-work authorization")
    config = json.loads((functional_state / "scientist.json").read_text())
    urls = config["urls"]
    report = {"scientific_reproduction": False, "session_released": False, "context_released": False}
    with Client(urls["runtime"], config["token"], agent_name=config["username"], timeout=15,
            **{name: Endpoint(urls[name], config["token"]) for name in ("resources", "reasoner", "resolver")}) as api:
        session = context = None
        primary = None
        try:
            api.initialize_user_scope(local_federation=True)
            session = api.create_session(name="Typed graph owned acceptance")
            context = session.create_context(configuration={"persistence": "EXPLICIT_ACTION",
                "accessRights": {"public": False, "allowedUsers": [config["username"]], "excludedUsers": [],
                                 "allowedGroups": [], "excludedGroups": []}})
            region = context.observe(ObservationRequest("earth:Terrestrial earth:Region", geometry=rectangle_geometry())).result(120)
            verify_grid(region)
            result = context.within(region).observe(ObservationRequest("geography:Elevation in m",
                options=ObservationOptions(namespace="python.fixture"))).result(120)
            assert result.fetch_data([0], curve="D2_YX").values == (Decimal("123.25"),)
            browser = GraphBrowser(context, max_links=16)
            page = browser.neighbors(result, direction="incoming", relationships=["MEASURED"])
            assert page.partial and page.links
            candidates = [edge.source for edge in page.links if edge.source.kind == "activity" and edge.source.activity_type == "MEASURE"]
            assert candidates
            activity = candidates[0]
            assert activity.outcome == "SUCCESS"
            next_page = browser.neighbors(activity, direction="outgoing", relationships=["MEASURED"])
            assert next_page.partial and any(edge.target.id == result.id for edge in next_page.links)
            report.update(graph_navigation_verified=True, membership_requests=2, link_requests=2,
                activity_type=activity.activity_type, activity_outcome=activity.outcome,
                limits="Recorded links only, not root commit proof, complete lineage or revision-consistent pagination")
        except BaseException as error:
            primary = error
            raise
        finally:
            failed = []
            for key, scope in (("context_released", context), ("session_released", session)):
                if scope is not None:
                    try:
                        report[key] = scope.release() is True
                        if not report[key]:
                            failed.append(key)
                    except BaseException as error:
                        failed.append(type(error).__name__)
            record_property("typed_graph_workflow", json.dumps(report))
            if failed:
                if primary is not None:
                    primary.add_note("Owned graph cleanup failed: " + ",".join(failed))
                else:
                    raise AssertionError("Owned graph cleanup failed: " + ",".join(failed))
