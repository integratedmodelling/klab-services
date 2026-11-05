"""Installed fresh-process recovery; completed retained work, not Runtime restart."""
import json

import pytest

from klab_client import Context, ObservationRequest, RectangularGeometry, Session, load_handle
from klab_client.errors import AuthorizationError, MissingAssetError, ServerError
from workflows.completed_recovery import client, deleted_context_refusal, expected_values, run_child
from workflows.scientific_checks import require

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_completed_job_and_observation_survive_local_process_close(functional_state):
    state = functional_state
    config = json.loads((state / "scientist.json").read_text())
    directory = state / "completed-recovery"
    directory.mkdir()
    session = context = None
    primary = None
    report = {"passed": False, "runtime_restart": "NOT_TESTED", "in_flight_recovery": "NOT_TESTED"}
    try:
        with client(config) as api:
            api.initialize_user_scope(local_federation=True)
            session = api.create_session(name="owned fresh-process recovery")
            context = session.create_context(configuration={"persistence": "EXPLICIT_ACTION",
                "accessRights": {"public": False, "allowedUsers": [config["username"]],
                    "excludedUsers": [], "allowedGroups": [], "excludedGroups": []}})
            region = context.observe(ObservationRequest("consolidation:Region",
                geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4))).result(120)
            focused = context.within(region)
            job = focused.observe("consolidation:Elevation of consolidation:Region in m")
            observation = job.result(120)
            assert observation.fetch_data(range(20), curve="D2_YX").values == expected_values()
            for name, handle in (("context", focused), ("job", job), ("observation", observation)):
                path = directory / (name + ".json")
                handle.to_handle().save(path)
                assert config["token"] not in path.read_text()
                assert config["other_token"] not in path.read_text()
            original = {"job_id": job.id, "observation_id": observation.id, "scope": focused.get_context_id()}
        # Creator transport is now closed before the new process attempts recovery.
        recovered = run_child(state, directory)
        assert all(recovered[key] == value for key, value in original.items())
        with client(config) as api:
            api.initialize_user_scope(local_federation=True)
            observation = api.restore_handle(load_handle(directory / "observation.json"))
            # Positive creator control after both local closes proves no disposal.
            assert observation.fetch_data(range(20), curve="D2_YX").values == expected_values()
            with client(config | {"token": config["other_token"], "username": "other-scientist"}) as other:
                other.initialize_user_scope(local_federation=True)
                with pytest.raises(AuthorizationError):
                    other.restore_handle(observation.to_handle())
            # Explicit creator cleanup only; detached/restored clients never own it.
            assert Context(api, context.configuration, owned=True).release() is True
            context = None
            with pytest.raises((MissingAssetError, ServerError)) as refusal:
                api.restore_handle(observation.to_handle())
            assert deleted_context_refusal(refusal.value), "Unexpected failure is not deleted-context evidence"
            report.update(passed=True, original=original, recovered=recovered, creator_transport_closed=True,
                owner_read_after_close=True, foreign_restore_denied=True, deleted_restore_denied=True,
                deleted_context_error=type(refusal.value).__name__)
    except BaseException as error:
        primary = error
        raise
    finally:
        try:
            # Only the creator's captured scope configuration/ID are used here;
            # opening a new local transport does not confer ownership of others.
            with client(config) as cleanup:
                cleanup.initialize_user_scope(local_federation=True)
                failures = []
                for owned in (Context(cleanup, context.configuration, owned=True) if context else None,
                              Session(cleanup, session.id) if session else None):
                    if owned is not None:
                        try:
                            require(owned.release() is True, "Creator release unconfirmed")
                        except BaseException as error:
                            failures.append(type(error).__name__)
                report["owned_releases_confirmed"] = not failures
                require(not failures, "Creator releases unconfirmed")
        except BaseException as error:
            report["owned_releases_confirmed"] = False
            if primary is not None:
                primary.add_note("Creator cleanup failed: " + type(error).__name__)
            else:
                raise
        finally:
            report["passed"] &= report.get("owned_releases_confirmed", False)
            (state / "completed-recovery.json").write_text(json.dumps(report, indent=2))
