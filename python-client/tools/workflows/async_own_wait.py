"""Cancel one local async wait without remote cancellation or scientific replay."""
import asyncio
import json
from pathlib import Path

from klab_client import AsyncClient, Endpoint, ObservationOptions, ObservationRequest, RectangularGeometry
from klab_client.errors import AuthorizationError, MissingAssetError, SubmissionOutcomeUnknown
from .scientific_checks import require


def forbidden_during_wait(call):
    path = call["path"]
    return any(part in path for part in ("/jobs/cancel/", "/release", "/create", "/submit")) or call["method"] == "DELETE"


async def cancel_own_wait(job, http, calls):
    entered = asyncio.Event()
    hold = asyncio.Event()
    cancelled_task = None
    async def block_own_status(request):
        if asyncio.current_task() is cancelled_task and request.url.path.endswith(f"/jobs/status/{job.id}"):
            entered.set()
            await hold.wait()
    http.event_hooks["request"].append(block_own_status)
    start = len(calls)
    waiter = None
    try:
        cancelled_task = asyncio.create_task(job.result(timeout=120))
        await asyncio.wait_for(entered.wait(), 5)
        waiter = asyncio.create_task(job.result(timeout=120))
        cancelled_task.cancel()
        try:
            await cancelled_task
        except asyncio.CancelledError:
            pass
        else:
            raise AssertionError("Own local wait did not raise CancelledError")
        result = await waiter
        require(not any(forbidden_during_wait(call) for call in calls[start:]),
            "Own-wait cancellation caused remote cancellation/mutation/disposal")
        return result, list(calls[start:])
    finally:
        hold.set()
        http.event_hooks["request"].remove(block_own_status)
        pending = [task for task in (cancelled_task, waiter) if task is not None]
        for task in pending:
            if not task.done():
                task.cancel()
        await asyncio.gather(*pending, return_exceptions=True)


async def scenario(state):
    config = json.loads((state / "scientist.json").read_text())
    def client(token, username):
        return AsyncClient(config["urls"]["runtime"], token, agent_name=username,
            **{name: Endpoint(config["urls"][name], token) for name in ("reasoner", "resources", "resolver")},
            max_concurrency=2, timeout=30, poll_interval=.05)
    calls = []
    async def audit(request):
        calls.append({"method": request.method, "path": request.url.path})
    session = context = None
    primary = None
    report = {"passed": False, "context_released": False, "session_released": False,
        "running_simulation_interrupt": "NOT_CLAIMED", "notebook_agent_event_acceptance": "NOT_TESTED"}
    async with client(config["token"], config["username"]) as owner:
        owner.transport._http.event_hooks["request"].append(audit)
        try:
            await owner.initialize_user_scope(local_federation=True)
            session = await owner.create_session(name="owned async own-wait acceptance")
            context = await session.create_context(configuration={"persistence": "EXPLICIT_ACTION",
                "accessRights": {"public": False, "allowedUsers": [config["username"]], "excludedUsers": [],
                                 "allowedGroups": [], "excludedGroups": []}})
            require(context.configuration.get("owner") == config["username"] and
                context.configuration.get("accessRights", {}).get("public") is False, "Private owner contract differs")
            region = await (await context.observe(ObservationRequest("consolidation:Region",
                geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4)))).result(120)
            focused = context.within(region)
            job = await focused.observe("consolidation:Elevation of consolidation:Region in m",
                options=ObservationOptions(scenarios=["consolidation.scenario"]))
            original_id = job.id
            observation, wait_calls = await cancel_own_wait(job, owner.transport._http, calls)
            reference = job.to_handle()
            require(job.id == original_id and reference.job_id == original_id and reference.context_id == context.id,
                "Local cancellation changed original Job/context identity")
            data = await observation.fetch_data(range(20), curve="D2_YX")
            require(observation.units == data.units == "m" and data.values == (234.5,) * 20,
                "Async actual cells differ from independent scenario literal/unit")
            left, right = await asyncio.gather(observation.fetch_data([0], curve="D2_YX"),
                                              observation.fetch_data([19], curve="D2_YX"))
            require(left.values == right.values == (234.5,), "Concurrent reads differ from original result")
            async with client(config["other_token"], "other-scientist") as foreign:
                await foreign.initialize_user_scope(local_federation=True)
                try:
                    await foreign.restore_handle(reference)
                except (AuthorizationError, MissingAssetError) as error:
                    report["foreign_handle_denial"] = type(error).__name__
                else:
                    raise AssertionError("Foreign principal restored private original Job")
            # Closing this independent reader must not dispose creator work.
            async with client(config["token"], config["username"]) as reader:
                await reader.initialize_user_scope(local_federation=True)
                restored = await reader.restore_handle(reference)
                again = await restored.result(120)
                require(again.id == observation.id, "Same-principal async reader changed observation identity")
            require((await observation.fetch_data([0], curve="D2_YX")).values == (234.5,),
                "Reader close disposed creator work")
            submits = [call for call in calls if call["method"] == "POST" and call["path"].endswith("/api/v1/submit")]
            require(len(submits) == 2, "Unexpected scientific submission count; no replay permitted")
            require(not any("/jobs/cancel/" in call["path"] for call in calls), "Unexpected remote Job cancellation")
            report.update(passed=True, job_id=original_id, observation_id=observation.id,
                local_wait_cancelled=True, other_wait_completed=True, wait_audit=wait_calls,
                verified_cells=20, units="m", expected_value=234.5, concurrent_reads=2,
                scientific_submissions=2, remote_job_cancellations=0, reader_close_preserved_work=True)
        except BaseException as error:
            primary = error
            raise
        finally:
            if isinstance(primary, SubmissionOutcomeUnknown):
                report["cleanup_limit"] = "Unknown mutation outcome: no dependent release, preserve private fixture"
            else:
                failures = []
                for key, scope in (("context_released", context), ("session_released", session)):
                    if scope is not None:
                        try:
                            report[key] = await scope.release() is True
                            require(report[key], "Async owned release not confirmed")
                        except BaseException as error:
                            failures.append(type(error).__name__)
                if failures:
                    report["cleanup_error_types"] = failures
                    if primary is None:
                        raise AssertionError("Async owned cleanup failed")
                    primary.add_note("Async owned cleanup failed: " + ",".join(failures))
            (state / "async-own-wait.json").write_text(json.dumps(report, indent=2))
    return report


def run(state):
    return asyncio.run(scenario(Path(state)))
