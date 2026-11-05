"""Actual native Java exports through awaited streams; unchanged independent oracles."""
import asyncio
from contextlib import asynccontextmanager
import json
from pathlib import Path

import httpx

from klab_client import AsyncClient, Endpoint, ObservationOptions, ObservationRequest, RectangularGeometry
from klab_client.errors import SubmissionOutcomeUnknown, TransportError
from .geojson_export import validate_reference
from .native_stream_workload import SIDE, MAX_BYTES, MEDIA, digest, validate_native
from .scientific_checks import require


@asynccontextmanager
async def measured_stream(client, *, interrupt=False):
    stats = {"chunks": 0, "bytes": 0, "largest_chunk": 0, "injected": False, "response_closed": False}
    original = client.transport.stream
    @asynccontextmanager
    async def observed(*args, **kwargs):
        response = None
        try:
            async with original(*args, **kwargs) as response:
                iterator = response.aiter_raw
                async def chunks(chunk_size=None):
                    async for chunk in iterator(chunk_size=chunk_size):
                        if interrupt and stats["chunks"] >= 1:
                            stats["injected"] = True
                            raise httpx.ReadError("Controlled interruption of native read-only export")
                        stats["chunks"] += 1
                        stats["bytes"] += len(chunk)
                        stats["largest_chunk"] = max(stats["largest_chunk"], len(chunk))
                        yield chunk
                response.aiter_raw = chunks
                try:
                    yield response
                finally:
                    response.aiter_raw = iterator
        finally:
            stats["response_closed"] = response is not None and response.is_closed
    client.transport.stream = observed
    try:
        yield stats
    finally:
        client.transport.stream = original


def save_report(state, report, failure=None):
    try:
        (state / "async-native-export.json").write_text(json.dumps(report, indent=2))
    except Exception as error:
        if failure is None:
            raise
        failure.add_note("Async export evidence save failed: " + type(error).__name__)


async def scenario(state):
    config = json.loads((state / "scientist.json").read_text())
    directory = state / "async-native-export"
    directory.mkdir()
    session = context = None
    primary = None
    report = {"passed": False, "context_released": False, "session_released": False,
        "category_time_provider_revision_matrix": "NOT_TESTED"}
    async with AsyncClient(config["urls"]["runtime"], config["token"], agent_name=config["username"],
            **{name: Endpoint(config["urls"][name], config["token"]) for name in ("reasoner", "resources", "resolver")}) as client:
        try:
            await client.initialize_user_scope(local_federation=True)
            session = await client.create_session(name="owned native async export acceptance")
            context = await session.create_context()
            region = await (await context.observe(ObservationRequest("consolidation:Region",
                geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4)))).result(120)
            geojson = await client.export_to_file(region, directory / "region.geojson", media_type="application/geo+json")
            feature = validate_reference(json.loads(geojson.path.read_bytes()), region)
            report["geojson"] = {"bytes": geojson.bytes_written, "schema_id": geojson.schema_id,
                "observation_id": region.id, "geometry": feature["geometry"], "coordinate_tolerance_degrees": 1e-6}
            large = await (await context.observe(ObservationRequest("consolidation:Region",
                geometry=RectangularGeometry(200000, 302400, 6000000, 6102400, SIDE, SIDE)))).result(120)
            observation = await (await context.within(large).observe("consolidation:Elevation of consolidation:Region in m",
                options=ObservationOptions(scenarios=["consolidation.scenario"]))).result(300)
            require(observation.units == "m", "Native source unit differs")
            destination = directory / "native.tif"
            async with measured_stream(client) as stats:
                exported = await client.export_to_file(observation, destination, media_type=MEDIA, max_bytes=MAX_BYTES)
            require(stats["response_closed"] and stats["chunks"] > 1 and stats["largest_chunk"] <= 65536
                and stats["bytes"] == exported.bytes_written == destination.stat().st_size,
                "Native async stream not bounded/complete/closed")
            report["native"] = validate_native(destination) | {"stream": stats, "schema_id": exported.schema_id,
                "sha256": digest(destination), "observation_id": observation.id}
            interruptions = []
            for overwrite in (False, True):
                target = destination if overwrite else directory / "partial.tif"
                before = digest(target) if target.exists() else None
                async with measured_stream(client, interrupt=True) as interrupted:
                    try:
                        await client.export_to_file(observation, target, media_type=MEDIA, max_bytes=MAX_BYTES, overwrite=overwrite)
                    except TransportError:
                        require(interrupted["injected"], "Unexpected real stream failure, not injected evidence")
                    else:
                        raise AssertionError("Controlled stream interruption did not occur")
                require(interrupted["response_closed"] and interrupted["chunks"] == 1 and
                    not list(directory.glob(".klab-export-*")), "Interrupted async stream/staging cleanup failed")
                require(digest(target) == before if before else not target.exists(), "Interrupted stream published partial or changed destination")
                interruptions.append(interrupted | {"overwrite": overwrite, "destination_preserved": True})
            report.update(passed=True, interruptions=interruptions)
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
                            require(report[key], "Owned async export scope release not confirmed")
                        except BaseException as error:
                            failures.append(type(error).__name__)
                if failures:
                    report.update(passed=False, cleanup_error_types=failures)
                    if primary is None:
                        primary = AssertionError("Owned async export cleanup failed")
                        save_report(state, report, primary)
                        raise primary
                    primary.add_note("Owned cleanup failed: " + ",".join(failures))
            save_report(state, report, primary)
    return report


def run(state):
    return asyncio.run(scenario(Path(state)))
