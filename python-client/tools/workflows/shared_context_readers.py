"""Controlled ACL admission and overlapping native reads; no new permission policy."""
import asyncio
import json
from pathlib import Path

from klab_client import ObservationOptions, ObservationRequest, RectangularGeometry, load_handle, save_handle
from klab_client.client import scope_token
from klab_client.errors import AuthorizationError, MissingAssetError, SubmissionOutcomeUnknown
from .async_handle_restoration import client
from .model_source_authoring import save_report
from .scientific_checks import require


def read_audit(calls):
    async def audit(request):
        path = request.url.path
        require(not any(part in path for part in ("/create", "/submit", "/release", "/jobs/cancel/")),
            "Collaborator workflow attempted recreation or remote disposal")
        calls.append({"method": request.method, "path": path})
    return audit


async def overlapping_reads(owner, collaborator, *, timeout=30):
    """Gate first point requests at HTTP hooks; do not claim Java scanner overlap."""
    entered = {name: asyncio.Event() for name in ("owner", "collaborator")}
    hooks = []
    def gate(name, other):
        async def hook(request):
            if request.url.path.endswith("/observation/value") and not entered[name].is_set():
                entered[name].set()
                await asyncio.wait_for(entered[other].wait(), timeout)
        return hook
    tasks = []
    try:
        for name, observation, other in (("owner", owner, "collaborator"), ("collaborator", collaborator, "owner")):
            requests = observation._context.client.transport._http.event_hooks["request"]
            hook = gate(name, other)
            requests.append(hook)
            hooks.append((requests, hook))
        tasks = [asyncio.create_task(owner.fetch_data(range(20), curve="D2_YX")),
            asyncio.create_task(collaborator.fetch_data([19, 0, 19, 1], curve="D2_YX"))]
        values = await asyncio.wait_for(asyncio.gather(*tasks), timeout + 10)
        require(all(event.is_set() for event in entered.values()), "Both native read operations did not overlap")
        return values
    finally:
        for task in tasks:
            if not task.done():
                task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        for requests, hook in hooks:
            requests.remove(hook)


def verify_values(data, offsets):
    require(data.offsets == tuple(offsets) and data.values == (234.5,) * len(offsets)
        and data.units == "m", "Shared reader changed actual values/units/order/duplicates")


def verify_observable(owner_data, collaborator_data, observation):
    require(owner_data.observable == collaborator_data.observable == observation.observable.raw["urn"],
        "Shared reads changed observable identity")


async def scenario(state):
    config = json.loads((state / "scientist.json").read_text())
    report = {"passed": False, "context_released": False, "session_released": False,
        "private_context_released": False, "collaborator_session_released": False,
        "permission_model": "Existing context ACL; admitted user is not claimed read-only",
        "server_scanner_concurrency": "NOT_CLAIMED", "cross_fetch_revision_lease": "NOT_CLAIMED"}
    session = private = shared = other_session = None
    primary = None
    async with client(config) as owner:
        try:
            await owner.initialize_user_scope(local_federation=True)
            session = await owner.create_session(name="owned shared native readers")
            def configuration(users):
                return {"persistence": "EXPLICIT_ACTION", "accessRights": {"public": False,
                    "allowedUsers": users, "excludedUsers": [], "allowedGroups": [], "excludedGroups": []}}
            private = await session.create_context(configuration=configuration([config["username"]]))
            require((await owner.attach_context(private.id)).id == private.id, "Owner private positive lookup failed")
            shared = await session.create_context(configuration=configuration([config["username"], "other-scientist"]))
            region = await (await shared.observe(ObservationRequest("consolidation:Region",
                geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4)))).result(120)
            original = await (await shared.within(region).observe("consolidation:Elevation of consolidation:Region in m",
                options=ObservationOptions(scenarios=["consolidation.scenario"]))).result(120)
            save_handle(original, state / "shared-observation.json")
            reference = load_handle(state / "shared-observation.json")
            calls = []
            async with client(config, foreign=True) as other:
                await other.initialize_user_scope(local_federation=True)
                other_session = await other.create_session(name="valid collaborator private positive control")
                require(bool(other_session.id), "Valid collaborator session positive control failed")
                report["collaborator_session_released"] = await other_session.release() is True
                require(report["collaborator_session_released"], "Collaborator positive-control cleanup failed")
                other.transport._http.event_hooks["request"].append(read_audit(calls))
                try:
                    await other.attach_context(private.id)
                except (AuthorizationError, MissingAssetError) as error:
                    report["private_denial"] = type(error).__name__
                else:
                    raise AssertionError("Non-admitted collaborator attached private owner context")
                restored = await other.restore_handle(reference)
                require(not restored._context.owned and restored.id == original.id
                    and scope_token(restored._context) == reference.scope,
                    "Collaborator restoration changed original identity/scope or acquired ownership")
                a, b = await overlapping_reads(original, restored)
                verify_values(a, range(20))
                verify_values(b, [19, 0, 19, 1])
                verify_observable(a, b, original)
                report.update(original_observation_id=original.id, restored_observation_id=restored.id,
                    scope=reference.scope, collaborator_unowned=True, client_operations_overlap=True,
                    owner_cells=20, collaborator_offsets=[19, 0, 19, 1], units="m")
            verify_values(await original.fetch_data([0, 19], curve="D2_YX"), [0, 19])
            report.update(passed=True, collaborator_closed_locally=True, owner_work_survived=True,
                collaborator_calls=calls, scientific_submissions=2, collaborator_scientific_submissions=0)
        except BaseException as error:
            primary = error
            report["error_type"] = type(error).__name__
            raise
        finally:
            if isinstance(primary, SubmissionOutcomeUnknown):
                report["cleanup_limit"] = "Unknown mutation outcome; no replay or dependent release"
            else:
                failures = []
                for key, scope in (("context_released", shared), ("private_context_released", private),
                    ("session_released", session)):
                    if scope is not None:
                        try:
                            report[key] = await scope.release() is True
                            require(report[key], "Owned shared-reader scope cleanup not confirmed")
                        except BaseException as error:
                            failures.append(type(error).__name__)
                if failures:
                    report.update(passed=False, cleanup_error_types=failures)
                    if primary is None:
                        primary = AssertionError("Owned shared-reader cleanup failed")
                        save_report(state / "shared-context-readers.json", report, primary)
                        raise primary
                    primary.add_note("Owned cleanup failed: " + ",".join(failures))
            save_report(state / "shared-context-readers.json", report, primary)
    return report


def run(state):
    return asyncio.run(scenario(Path(state)))
