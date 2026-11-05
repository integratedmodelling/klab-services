"""Fresh-process native async recovery of original retained Java work; no replay."""
import asyncio
from dataclasses import replace
import json
import os
from pathlib import Path
import subprocess
import sys

from klab_client import (AsyncClient, Endpoint, ObservationOptions, ObservationRequest,
    RectangularGeometry, load_handle, save_handle)
from klab_client.client import scope_token
from klab_client.errors import AuthenticationError, AuthorizationError, MissingAssetError, SubmissionOutcomeUnknown
from cryptography.hazmat.primitives import serialization
from fixture_hub import token as issue_test_token
from .async_own_wait import cancel_own_wait
from .scientific_checks import require


def save_report(state, report, failure=None):
    try:
        (state / "async-handle-restoration.json").write_text(json.dumps(report, indent=2))
    except Exception as error:
        if failure is None:
            raise
        failure.add_note("Async restoration evidence save failed: " + type(error).__name__)


def client(config, *, foreign=False):
    token = config["other_token"] if foreign else config["token"]
    return AsyncClient(config["urls"]["runtime"], token,
        agent_name="other-scientist" if foreign else config["username"],
        **{name: Endpoint(config["urls"][name], token) for name in ("reasoner", "resources", "resolver")},
        timeout=30, poll_interval=.05)


async def recover(state):
    config = json.loads((state / "scientist.json").read_text())
    calls = []
    async with client(config) as reader:
        await reader.initialize_user_scope(local_federation=True)
        async def audit(request):
            path = request.url.path
            require(not any(part in path for part in ("/create", "/submit", "/release", "/jobs/cancel/")),
                "Restoration attempted work recreation or remote disposal")
            calls.append({"method": request.method, "path": path})
        reader.transport._http.event_hooks["request"].append(audit)
        context = await reader.restore_handle(load_handle(state / "saved-context.json"))
        observation = await reader.restore_handle(load_handle(state / "saved-observation.json"))
        job = await reader.restore_handle(load_handle(state / "saved-job.json"))
        again, wait_calls = await cancel_own_wait(job, reader.transport._http, calls)
        ref = load_handle(state / "saved-observation.json")
        require(not context.owned and not observation._context.owned and not job.scope.owned,
            "Restoration acquired creator ownership")
        require(observation.id == again.id == ref.observation_id and
            scope_token(context) == scope_token(observation._context) == scope_token(job.scope) == ref.scope,
            "Restoration changed original observation or focus")
        values = await observation.fetch_data(range(20), curve="D2_YX")
        require(values.units == observation.units == "m" and values.values == (234.5,) * 20,
            "Recovered original scenario values/units differ")
        result = {"passed": True, "pid": os.getpid(), "observation_id": observation.id,
            "job_id": job.id, "scope": ref.scope, "verified_cells": 20, "units": "m",
            "unowned": True, "local_wait_cancelled": True, "wait_calls": wait_calls, "calls": calls,
            "scientific_submissions": 0, "local_close_only": True}
    (state / "async-restoration-worker.json").write_text(json.dumps(result, indent=2))


async def scenario(state):
    config = json.loads((state / "scientist.json").read_text())
    report = {"passed": False, "context_released": False, "session_released": False,
        "restart_inflight_history": "NOT_CLAIMED"}
    session = context = None
    primary = None
    async with client(config) as owner:
        try:
            await owner.initialize_user_scope(local_federation=True)
            session = await owner.create_session(name="owned native async handle restoration")
            context = await session.create_context(configuration={"persistence": "EXPLICIT_ACTION",
                "accessRights": {"public": False, "allowedUsers": [config["username"]],
                    "excludedUsers": [], "allowedGroups": [], "excludedGroups": []}})
            region = await (await context.observe(ObservationRequest("consolidation:Region",
                geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4)))).result(120)
            focused = context.within(region)
            job = await focused.observe("consolidation:Elevation of consolidation:Region in m",
                options=ObservationOptions(scenarios=["consolidation.scenario"]))
            original = await job.result(120)
            for name, item in (("context", focused), ("job", job), ("observation", original)):
                save_handle(item, state / ("saved-" + name + ".json"))
            reference = load_handle(state / "saved-observation.json")
            key = serialization.load_pem_private_key((state / "fixture-authority.pem").read_bytes(), password=None)
            expired = config | {"token": issue_test_token(key, config["username"], lifetime=-60)}
            async with client(expired) as denied:
                try:
                    await denied.restore_handle(reference)
                except (AuthenticationError, AuthorizationError) as error:
                    report["known_expired_TEST_JWT_rejection"] = type(error).__name__
                else:
                    raise AssertionError("Known expired TEST credential restored state")
            async with client(config, foreign=True) as foreign:
                await foreign.initialize_user_scope(local_federation=True)
                try:
                    await foreign.restore_handle(reference)
                except (AuthorizationError, MissingAssetError) as error:
                    report["foreign_denial"] = type(error).__name__
                else:
                    raise AssertionError("Foreign principal restored private original state")
            # A separate interpreter imports the installed SDK; only workflow code
            # is supplied on sys.path. No threads wrapping SDK network operations.
            code = ("import sys,asyncio;from pathlib import Path;sys.path.insert(0,sys.argv[1]);"
                "from workflows.async_handle_restoration import recover;asyncio.run(recover(Path(sys.argv[2])))")
            process = await asyncio.create_subprocess_exec(sys.executable, "-c", code,
                str(Path(__file__).resolve().parents[1]), str(state),
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
            try:
                await asyncio.wait_for(process.wait(), 180)
                require(process.returncode == 0, "Fresh native async restoration worker failed")
            finally:
                if process.returncode is None:
                    process.kill()
                    await process.wait()
            worker = json.loads((state / "async-restoration-worker.json").read_text())
            require(worker["passed"] and worker["observation_id"] == original.id and worker["job_id"] == job.id,
                "Worker did not recover original Job/observation")
            require((await original.fetch_data([0], curve="D2_YX")).values == (234.5,),
                "Reader cancellation/close disposed creator work")
            # Successful same-family observation lookup above is the positive
            # control. Missing graph rows, never ProtocolError, establish absence.
            try:
                await owner.restore_handle(replace(reference, observation_id=2147483647))
            except MissingAssetError:
                report["missing_observation"] = "MissingAssetError after positive original lookup"
            else:
                raise AssertionError("Missing observation unexpectedly restored")
            report.update(passed=True, worker=worker, reader_close_preserved_work=True,
                scientific_submissions=2, worker_closed=True)
        except BaseException as error:
            primary = error
            report["error_type"] = type(error).__name__
            raise
        finally:
            if isinstance(primary, SubmissionOutcomeUnknown):
                report["cleanup_limit"] = "Unknown mutation outcome; no dependent release or replay"
            else:
                failures = []
                for key, scope in (("context_released", context), ("session_released", session)):
                    if scope is not None:
                        try:
                            report[key] = await scope.release() is True
                            require(report[key], "Owned async release not confirmed")
                        except BaseException as error:
                            failures.append(type(error).__name__)
                if failures:
                    report.update(passed=False, cleanup_error_types=failures)
                    if primary is None:
                        primary = AssertionError("Owned async restoration cleanup failed")
                        save_report(state, report, primary)
                        raise primary
                    primary.add_note("Owned cleanup failed: " + ",".join(failures))
            save_report(state, report, primary)
    return report


def run(state):
    return asyncio.run(scenario(Path(state)))
