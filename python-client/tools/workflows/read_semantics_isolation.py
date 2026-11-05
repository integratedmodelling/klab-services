"""Actual Java native-meaning reads while caller-owned request metadata mutates."""
import asyncio
import json
from pathlib import Path

from klab_client import ObservationOptions, ObservationRequest, RectangularGeometry
from klab_client.errors import SubmissionOutcomeUnknown
from .async_handle_restoration import client
from .model_source_authoring import save_report
from .scientific_checks import require


def native_request(observation):
    raw = observation.observable.raw
    unit = raw.get("unit", {})
    require(unit.get("definition") == observation.units == "m" and unit.get("contextual") is False
        and unit.get("aggregatedDimensions") == {} and raw.get("range") is None and raw.get("currency") is None,
        "Fixture does not expose ordinary metre semantics with empty dimensions/range/currency")
    # StorageScan.semantics renders an empty UnitImpl aggregatedDimensions map as
    # '{}'. ValueMediation.target retains source meaning for an exact native URN.
    return {"observable": raw["urn"], "unit": observation.units, "range": "", "currency": "",
        "contextualDimensions": "{}"}


async def scenario(state):
    config = json.loads((state / "scientist.json").read_text())
    report = {"passed": False, "context_released": False, "session_released": False,
        "java_scientific_mediation": "UNCHANGED; requested native source semantics only",
        "cross_fetch_revision_lease": "NOT_CLAIMED"}
    session = context = None
    primary = None
    async with client(config) as owner:
        try:
            await owner.initialize_user_scope(local_federation=True)
            session = await owner.create_session(name="owned read semantics isolation acceptance")
            context = await session.create_context(configuration={"persistence": "EXPLICIT_ACTION"})
            region = await (await context.observe(ObservationRequest("consolidation:Region",
                geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4)))).result(120)
            observation = await (await context.within(region).observe("consolidation:Elevation of consolidation:Region in m",
                options=ObservationOptions(scenarios=["consolidation.scenario"]))).result(120)
            supplied = native_request(observation)
            expected = dict(supplied)
            calls = []
            async def mutate_caller(request):
                require(request.method == "POST" and request.url.path == "/runtime/api/v1/observation/value",
                    "Indexed read caused a non-read operation")
                body = json.loads(request.content)
                calls.append({"offset": body["offset"], "semantics": body["semantics"]})
                if len(calls) == 1:
                    supplied.update(unit="km", observable="caller mutation; not a scientific request")
            owner.transport._http.event_hooks["request"].append(mutate_caller)
            try:
                values = await observation.fetch_data(range(20), curve="D2_YX", semantics=supplied)
            finally:
                owner.transport._http.event_hooks["request"].remove(mutate_caller)
            require(len(calls) == 20 and all(row["semantics"] == expected for row in calls),
                "Actual point requests did not retain their initial native semantics")
            require(values.values == (234.5,) * 20 and values.units == "m" and values.observable == expected["observable"],
                "Actual Java values or recorded requested semantics changed")
            require(supplied["unit"] == "km" and supplied["observable"] != expected["observable"],
                "SDK overwrote the caller's independent dictionary mutation")
            report.update(passed=True, observation_id=observation.id, verified_cells=20, units="m",
                initial_request=expected, caller_mutation_preserved=True, request_snapshot_verified=True,
                read_requests=calls, scientific_submissions=2)
        except BaseException as error:
            primary = error
            report["error_type"] = type(error).__name__
            raise
        finally:
            if isinstance(primary, SubmissionOutcomeUnknown):
                report["cleanup_limit"] = "Unknown mutation outcome; no dependent releases or replay"
            else:
                failures = []
                for key, scope in (("context_released", context), ("session_released", session)):
                    if scope is not None:
                        try:
                            report[key] = await scope.release() is True
                            require(report[key], "Owned semantics isolation scope release not confirmed")
                        except BaseException as error:
                            failures.append(type(error).__name__)
                if failures:
                    report.update(passed=False, cleanup_error_types=failures)
                    if primary is None:
                        primary = AssertionError("Owned semantics isolation cleanup failed")
                        save_report(state / "read-semantics-isolation.json", report, primary)
                        raise primary
                    primary.add_note("Owned cleanup failed: " + ",".join(failures))
            save_report(state / "read-semantics-isolation.json", report, primary)
    return report


def run(state):
    return asyncio.run(scenario(Path(state)))
