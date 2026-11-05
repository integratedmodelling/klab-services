"""Authorized listing witnesses for deletion; a restore error is not absence proof."""
import asyncio
import json
from pathlib import Path

from klab_client import ObservationOptions, ObservationRequest, RectangularGeometry, load_handle, save_handle
from klab_client.client import scope_token
from klab_client.errors import (JobUnavailableError, MissingAssetError, ProtocolError, ServerError,
    SubmissionOutcomeUnknown)
from .async_handle_restoration import client
from .scientific_checks import require


def verify_listing(infos, deleted_id, survivor_id, *, deleted):
    ids = [identity for info in infos for identity in info.context_ids]
    require(len(ids) == len(set(ids)), "Authorized context listing contains duplicate identities")
    require(survivor_id in ids, "Same-family survivor positive control is not visible")
    require((deleted_id not in ids) if deleted else (deleted_id in ids),
        "Authorized listing differs from explicit release state")
    return {"visible_context_ids": sorted(ids), "survivor_visible": True,
        "released_context_absent": deleted, "absence_basis": "successful authorized context listing; not restore error"}


def allowed_call(method, path):
    return (method == "GET" and (path == "/runtime/public/capabilities" or
        path.startswith(("/runtime/jobs/status/", "/runtime/jobs/retrieve/")))) or (
        method == "POST" and path in ("/runtime/api/v1/connect", "/runtime/api/v1/query", "/runtime/api/v1/observation/value"))


def save_report(state, report, failure=None):
    try:
        (state / "deleted-state-recovery.json").write_text(json.dumps(report, indent=2))
    except Exception as error:
        if failure is None:
            raise
        failure.add_note("Deleted-state evidence save failed: " + type(error).__name__)


async def original(context):
    region = await (await context.observe(ObservationRequest("consolidation:Region",
        geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4)))).result(120)
    focused = context.within(region)
    job = await focused.observe("consolidation:Elevation of consolidation:Region in m",
        options=ObservationOptions(scenarios=["consolidation.scenario"]))
    observation = await job.result(120)
    data = await observation.fetch_data(range(20), curve="D2_YX")
    require(observation.units == data.units == "m" and data.values == (234.5,) * 20,
        "Original source-defined scenario values/units differ")
    return focused, job, observation


async def survivor_read(reader, state, expected):
    context = await reader.restore_handle(load_handle(state / "survivor-context.json"))
    job = await reader.restore_handle(load_handle(state / "survivor-job.json"))
    observation = await reader.restore_handle(load_handle(state / "survivor-observation.json"))
    result = await job.result(120)
    require(observation.id == result.id == expected.id and scope_token(context) == scope_token(expected._context)
        == scope_token(job.scope) == scope_token(observation._context), "Surviving original identity/focus changed")
    require(not context.owned and not job.scope.owned and not observation._context.owned,
        "Reader acquired creator ownership")
    values = await observation.fetch_data(range(20), curve="D2_YX")
    require(values.units == "m" and values.values == (234.5,) * 20, "Surviving original values differ")
    return {"job_id": job.id, "observation_id": observation.id, "scope": scope_token(context),
        "verified_cells": 20, "units": "m", "unowned": True}


async def scenario(state):
    config = json.loads((state / "scientist.json").read_text())
    report = {"passed": False, "deleted_context_released": False, "survivor_context_released": False,
        "session_released": False, "absence_from_protocol_error": False, "restart_history": "NOT_CLAIMED"}
    session = deleted = survivor = None
    primary = None
    async with client(config) as owner:
        try:
            await owner.initialize_user_scope(local_federation=True)
            session = await owner.create_session(name="owned deleted-state recovery acceptance")
            configuration = {"persistence": "EXPLICIT_ACTION", "accessRights": {"public": False,
                "allowedUsers": [config["username"]], "excludedUsers": [], "allowedGroups": [], "excludedGroups": []}}
            deleted = await session.create_context(configuration=configuration)
            survivor = await session.create_context(configuration=configuration)
            for label, context in (("deleted", deleted), ("survivor", survivor)):
                focused, job, observation = await original(context)
                for kind, item in (("context", focused), ("job", job), ("observation", observation)):
                    save_handle(item, state / (label + "-" + kind + ".json"))
                if label == "survivor":
                    surviving_observation = observation
            report["listing_before"] = verify_listing(await owner.runtime.get_context_info(), deleted.id, survivor.id, deleted=False)
            save_report(state, report)
            report["deleted_context_released"] = await deleted.release() is True
            require(report["deleted_context_released"], "Creator root deletion was not confirmed")
            report["listing_after_release"] = verify_listing(await owner.runtime.get_context_info(), deleted.id, survivor.id, deleted=True)
            save_report(state, report)
            calls = []
            async with client(config) as reader:
                await reader.initialize_user_scope(local_federation=True)
                async def audit(request):
                    require(allowed_call(request.method, request.url.path), "Restoration attempted recreation/disposal or unsupported route")
                    calls.append({"method": request.method, "path": request.url.path})
                reader.transport._http.event_hooks["request"].append(audit)
                report["survivor_before"] = await survivor_read(reader, state, surviving_observation)
                failures = {}
                for kind in ("context", "observation", "job"):
                    try:
                        await reader.restore_handle(load_handle(state / ("deleted-" + kind + ".json")))
                    except (MissingAssetError, ProtocolError, ServerError, JobUnavailableError) as error:
                        failures[kind] = type(error).__name__
                        report["restore_failures"] = dict(failures)
                        save_report(state, report)
                    else:
                        raise AssertionError("Deleted-state handle unexpectedly restored; no recreation allowed")
                report["survivor_after"] = await survivor_read(reader, state, surviving_observation)
            report["listing_after_attempts"] = verify_listing(await owner.runtime.get_context_info(), deleted.id, survivor.id, deleted=True)
            require((await surviving_observation.fetch_data([0], curve="D2_YX")).values == (234.5,),
                "Reader close disposed surviving creator work")
            report.update(passed=True, restore_calls=calls, recovery_scientific_submissions=0,
                creator_scientific_submissions=4, reader_close_preserved_survivor=True)
        except BaseException as error:
            primary = error
            report["error_type"] = type(error).__name__
            raise
        finally:
            if isinstance(primary, SubmissionOutcomeUnknown):
                report["cleanup_limit"] = "Unknown mutation outcome; no dependent release or replay"
            else:
                failures = []
                scopes = [("survivor_context_released", survivor), ("session_released", session)]
                if not report["deleted_context_released"]:
                    scopes.insert(0, ("deleted_context_released", deleted))
                for key, scope in scopes:
                    if scope is not None:
                        try:
                            report[key] = await scope.release() is True
                            require(report[key], "Owned recovery scope release not confirmed")
                        except BaseException as error:
                            failures.append(type(error).__name__)
                if failures:
                    report.update(passed=False, cleanup_error_types=failures)
                    if primary is None:
                        primary = AssertionError("Owned deleted-state cleanup failed")
                        save_report(state, report, primary)
                        raise primary
                    primary.add_note("Owned cleanup failed: " + ",".join(failures))
            save_report(state, report, primary)
    return report


def run(state):
    return asyncio.run(scenario(Path(state)))
