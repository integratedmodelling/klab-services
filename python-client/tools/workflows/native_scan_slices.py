"""Typed initialization read on genuine Java work; no simulated temporal execution."""
import asyncio
import json
from pathlib import Path

from klab_client import ObservationOptions, ObservationRequest, RectangularGeometry, ScanSlice
from klab_client.errors import InvalidRequestError, SubmissionOutcomeUnknown
from .async_handle_restoration import client
from .model_source_authoring import save_report
from .scientific_checks import require


async def scenario(state):
    config = json.loads((state / "scientist.json").read_text())
    report = {"passed": False, "context_released": False, "session_released": False,
        "temporal_transition_execution": "NOT_RUN", "input_change_execution": "NOT_RUN",
        "committed_revision_lease": "NOT_CLAIMED"}
    session = context = None
    primary = None
    async with client(config) as owner:
        try:
            await owner.initialize_user_scope(local_federation=True)
            session = await owner.create_session(name="owned typed native slice acceptance")
            context = await session.create_context(configuration={"persistence": "EXPLICIT_ACTION"})
            region = await (await context.observe(ObservationRequest("consolidation:Region",
                geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4)))).result(120)
            observation = await (await context.within(region).observe("consolidation:Elevation of consolidation:Region in m",
                options=ObservationOptions(scenarios=["consolidation.scenario"]))).result(120)
            calls = []
            async def audit(request):
                calls.append((request.method, request.url.path))
            owner.transport._http.event_hooks["request"].append(audit)
            for changes in ({"key": " "}, {"start": 2**63},
                {"type": "TEMPORAL_TRANSITION", "start": 2, "end": 1}):
                try:
                    await observation.fetch_data([0], slice=ScanSlice.initialization().to_wire() | changes)
                except InvalidRequestError:
                    pass
                else:
                    raise AssertionError("Invalid native slice reached scientific read")
            require(not calls, "Invalid slice made HTTP requests")
            selected = ScanSlice.initialization()
            typed = await observation.fetch_data(range(20), curve="D2_YX", slice=selected)
            raw = await observation.fetch_data([0, 19, 0], curve="D2_YX", slice=selected.to_wire())
            require(typed.values == (234.5,) * 20 and raw.values == (234.5,) * 3
                and typed.units == raw.units == observation.units == "m", "Java source literal values/unit changed")
            require(typed.slice == raw.slice == selected.to_wire(), "Explicit initialization metadata changed")
            require(len(calls) == 23 and all(method == "POST" and path == "/runtime/api/v1/observation/value"
                for method, path in calls), "Slice reads caused a non-read operation")
            report.update(passed=True, observation_id=observation.id, verified_cells=20, units="m",
                explicit_slice=selected.to_wire(), dict_typed_parity=True, invalid_slice_zero_http=True,
                read_requests=23, scientific_submissions=2)
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
                            require(report[key], "Owned native slice scope release not confirmed")
                        except BaseException as error:
                            failures.append(type(error).__name__)
                if failures:
                    report.update(passed=False, cleanup_error_types=failures)
                    if primary is None:
                        primary = AssertionError("Owned native slice cleanup failed")
                        save_report(state / "native-scan-slices.json", report, primary)
                        raise primary
                    primary.add_note("Owned cleanup failed: " + ",".join(failures))
            save_report(state / "native-scan-slices.json", report, primary)
    return report


def run(state):
    return asyncio.run(scenario(Path(state)))
