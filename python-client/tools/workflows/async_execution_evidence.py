"""Recorded activities/plans for owned numeric work; never historical lineage."""
import asyncio
import json
from pathlib import Path

from klab_client import (ObservationOptions, ObservationRequest, RectangularGeometry,
    async_fetch_execution_evidence, to_handle)
from klab_client.errors import SubmissionOutcomeUnknown
from .async_handle_restoration import client
from .model_source_authoring import save_report
from .scientific_checks import require


def validate_evidence(result, reference):
    require(result.provenance.observation == reference and result.partial and result.provenance.partial,
        "Execution evidence changed original identity or claimed completeness")
    require(result.durable_root_commit.startswith("unknown:") and result.provenance.consistency.startswith("unknown:"),
        "Execution evidence claimed durable commit or shared revision")
    require(bool(result.activities), "Actual scoped activities unavailable")
    for activity in result.activities:
        require(activity.activity_type is not None, "Recorded activity type unavailable")
        require(all(chart.phase in ("resolution plan", "contextualization plan") for chart in activity.charts),
            "Recorded chart acquired execution meaning")
        require(bool(activity.charts) or bool(activity.missing), "Unavailable diagrams were silently dropped")
    return [{"id": item.id, "type": item.activity_type, "outcome": item.outcome,
        "chart_phases": [chart.phase for chart in item.charts], "missing": list(item.missing)}
        for item in result.activities]


async def scenario(state):
    config = json.loads((state / "scientist.json").read_text())
    report = {"passed": False, "context_released": False, "session_released": False,
        "historical_study_provenance": "NOT_RECOVERED", "durable_commit": "NOT_CLAIMED"}
    primary = None
    session = context = None
    async with client(config) as owner:
        try:
            await owner.initialize_user_scope(local_federation=True)
            session = await owner.create_session(name="owned async execution evidence")
            context = await session.create_context(configuration={"persistence": "EXPLICIT_ACTION"})
            region = await (await context.observe(ObservationRequest("consolidation:Region",
                geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4)))).result(120)
            original = await (await context.within(region).observe("consolidation:Elevation of consolidation:Region in m",
                options=ObservationOptions(scenarios=["consolidation.scenario"]))).result(120)
            data = await original.fetch_data([0, 19], curve="D2_YX")
            require(data.values == (234.5, 234.5) and data.units == "m", "Engineering value control differs")
            reference = to_handle(original)
            calls = []
            async def audit(request):
                require(request.method == "POST" and request.url.path.endswith("/runtime/api/v1/query"),
                    "Execution evidence attempted a non-query operation")
                query = json.loads(request.content)
                calls.append({"method": request.method, "path": request.url.path, "result_type": query.get("resultType")})
            hooks = owner.transport._http.event_hooks["request"]
            hooks.append(audit)
            try:
                result = await async_fetch_execution_evidence(original)
                activities = validate_evidence(result, reference)
                require(any(call["result_type"] == "ACTIVITY" for call in calls), "No actual scoped activity read")
                report.update(passed=True, original_observation_id=original.id,
                    retained_handle_observation_id=result.provenance.observation.observation_id,
                    link_count=len(result.provenance.links), activities=activities,
                    diagnostics=list(result.diagnostics), partial=True, calls=calls,
                    engineering_submissions=2, evidence_phase_submissions=0, evidence_phase_mutations=0,
                    numeric_value_control="234.5m at offsets0,19")
            finally:
                hooks.remove(audit)
        except BaseException as error:
            primary = error
            report.update(passed=False, error_type=type(error).__name__)
            raise
        finally:
            if isinstance(primary, SubmissionOutcomeUnknown):
                report["cleanup_limit"] = "Unknown mutation outcome; no replay/dependent release"
            else:
                failures = []
                for key, scope in (("context_released", context), ("session_released", session)):
                    if scope is not None:
                        try:
                            report[key] = await scope.release() is True
                            require(report[key], "Owned scope release not confirmed")
                        except BaseException as error:
                            failures.append(type(error).__name__)
                if failures:
                    report.update(passed=False, cleanup_error_types=failures)
                    if primary is None:
                        primary = AssertionError("Owned execution-evidence cleanup failed")
                        save_report(state / "async-execution-evidence.json", report, primary)
                        raise primary
                    primary.add_note("Owned cleanup failed: " + ",".join(failures))
            save_report(state / "async-execution-evidence.json", report, primary)
    return report


def run(state):
    return asyncio.run(scenario(Path(state)))
