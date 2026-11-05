"""Actual recorded neighborhood for original owned engineering work, not historic lineage."""
import asyncio
import json
from pathlib import Path

from klab_client import ObservationOptions, ObservationRequest, RectangularGeometry, async_fetch_provenance, to_handle
from klab_client.errors import SubmissionOutcomeUnknown
from .async_handle_restoration import client
from .model_source_authoring import save_report
from .scientific_checks import require


async def scenario(state):
    config = json.loads((state / "scientist.json").read_text())
    report = {"passed": False, "context_released": False, "session_released": False,
        "full_lineage": "NOT_CLAIMED", "atomic_revision_lease": "NOT_CLAIMED",
        "historical_study_provenance": "NOT_RECOVERED"}
    primary = None
    session = context = None
    async with client(config) as owner:
        try:
            await owner.initialize_user_scope(local_federation=True)
            session = await owner.create_session(name="owned async recorded provenance")
            context = await session.create_context(configuration={"persistence": "EXPLICIT_ACTION"})
            region = await (await context.observe(ObservationRequest("consolidation:Region",
                geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4)))).result(120)
            original = await (await context.within(region).observe("consolidation:Elevation of consolidation:Region in m",
                options=ObservationOptions(scenarios=["consolidation.scenario"]))).result(120)
            data = await original.fetch_data([0, 19], curve="D2_YX")
            require(data.values == (234.5, 234.5) and data.units == "m", "Engineering fixture value control differs")
            reference = to_handle(original)
            calls = []
            async def audit(request):
                require(request.method == "POST" and request.url.path.endswith("/runtime/api/v1/query"),
                    "Recorded provenance attempted a non-query operation")
                calls.append({"method": request.method, "path": request.url.path})
            hooks = owner.transport._http.event_hooks["request"]
            hooks.append(audit)
            try:
                view = await async_fetch_provenance(original)
                require(view.observation == reference and view.partial and view.consistency.startswith("unknown:"),
                    "Recorded view changed original identity or claimed complete/revision-pinned lineage")
                require(bool(view.links) and any(edge.evidence == "recorded relationship" for edge in view.links),
                    "Actual recorded execution neighborhood unavailable")
                first = await async_fetch_provenance(original, limit=1)
                require(len(first.links) == 1 and first.observation == reference, "Bounded first page differs")
                if first.next_offset is not None:
                    following = await async_fetch_provenance(original, limit=1, offset=first.next_offset)
                    require(following.observation == reference and following.offset == first.next_offset,
                        "Explicit bounded continuation changed original observation")
                    report["continuation_checked"] = True
                else:
                    report["continuation_checked"] = False
                report.update(passed=True, original_observation_id=original.id,
                    retained_handle_observation_id=view.observation.observation_id,
                    link_count=len(view.links), partial=True, consistency=view.consistency,
                    relationships=[edge.relationship for edge in view.links],
                    evidence=[edge.evidence for edge in view.links], calls=calls,
                    scientific_submissions=2, provenance_phase_scientific_submissions=0,
                    provenance_phase_mutations=0, numeric_value_control="234.5m at offsets0,19")
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
                        primary = AssertionError("Owned provenance cleanup failed")
                        save_report(state / "async-recorded-provenance.json", report, primary)
                        raise primary
                    primary.add_note("Owned cleanup failed: " + ",".join(failures))
            save_report(state / "async-recorded-provenance.json", report, primary)
    return report


def run(state):
    return asyncio.run(scenario(Path(state)))
