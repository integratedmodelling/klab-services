"""Owned Runtime outage, explicit refetch and actual Java private-context ACLs."""
import json
import time

import pytest

from klab_client import Client, Endpoint, GraphBrowser, GraphProjection, ObservationRequest, RectangularGeometry
from klab_client.errors import AuthorizationError, TransportError

pytestmark = [pytest.mark.live, pytest.mark.functional]


def control(state, action):
    (state / (action + "-runtime.request")).write_text("owned Runtime only; never replay")
    deadline = time.monotonic() + 150
    while not (state / (action + "-runtime.done")).exists():
        if time.monotonic() > deadline:
            raise TimeoutError("Parent-owned Runtime control unconfirmed")
        time.sleep(.1)


def test_owned_disconnect_partial_refetch_and_denial(functional_state):
    state = functional_state
    config = json.loads((state / "scientist.json").read_text())
    def client(token, name):
        return Client(config["urls"]["runtime"], token, agent_name=name, timeout=5,
            **{n: Endpoint(config["urls"][n], token) for n in ("resources", "reasoner", "resolver")})
    report = {"durable_replay": False, "consistency": "UNKNOWN"}
    session = context = None
    primary = None
    with client(config["token"], config["username"]) as api:
        try:
            api.initialize_user_scope(local_federation=True)
            session = api.create_session(name="owned partial-refresh acceptance")
            context = session.create_context(configuration={"persistence": "EXPLICIT_ACTION",
                "accessRights": {"public": False, "allowedUsers": [config["username"]],
                    "excludedUsers": [], "allowedGroups": [], "excludedGroups": []}})
            region = context.observe(ObservationRequest("consolidation:Region",
                geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4))).result(120)
            projection = GraphProjection(GraphBrowser(context, max_links=8))
            projection.load(region, direction="incoming")
            projection.load(region, direction="outgoing")
            before = projection.view
            assert len(before.pages) == 2
            with client(config["other_token"], "other-scientist") as other:
                other.initialize_user_scope(local_federation=True)
                with pytest.raises(AuthorizationError):
                    other.attach_context(context.id)
                with pytest.raises(AuthorizationError):
                    other.transport.request("runtime", "GET", "/releaseContext", scope=context.id, ambiguous=True)
            # The owner must still be able to read after the attempted foreign release.
            projection.load(region, direction="incoming")
            before = projection.view
            report.update(other_attach_denied=True, other_release_denied=True, owner_read_after_denial=True)
            control(state, "stop")
            with pytest.raises(TransportError):
                projection.load(region, direction="incoming")
            lost = projection.view
            assert lost.invalidation_reason == "READ_FAILED"
            assert all(p.invalidated for p in lost.pages)
            assert [p.page for p in lost.pages] == [p.page for p in before.pages]
            control(state, "start")
            api.initialize_user_scope(local_federation=True)
            attached = api.attach_context(context.id)
            assert attached.id == context.id
            refreshed = projection.load(region, direction="incoming")
            after = projection.view
            assert not refreshed.invalidated
            assert len(after.pages) == 2 and sum(p.invalidated for p in after.pages) == 1
            assert "UNKNOWN" in after.consistency and "NOT_RECHECKED" in after.current_authorization
            stale = next(p for p in after.pages if p.invalidated)
            assert stale.page == next(p.page for p in before.pages if p.page.direction == "outgoing")
            report.update(retained_pages=2, failed_read_invalidated_all=True, explicit_refetch_pages=1,
                stale_pages=1, owner_reattach=True, scientific_submissions=1)
        except BaseException as error:
            primary = error
            raise
        finally:
            failed = []
            for label, owned in (("context", context), ("session", session)):
                if owned is not None:
                    try:
                        if owned.release() is not True:
                            failed.append(label)
                    except Exception:
                        failed.append(label)
            report["owned_releases_confirmed"] = not failed
            (state / "partial-refresh.json").write_text(json.dumps(report, indent=2))
            if failed:
                if primary is not None:
                    primary.add_note("Owned release unconfirmed")
                else:
                    raise AssertionError("Owned release unconfirmed")
