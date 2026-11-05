"""Bounded actual Java native stream, not external-provider/whole-scale acceptance."""
from contextlib import contextmanager
import hashlib
import json
import math
from pathlib import Path
import time

import httpx
import psutil

from klab_client import Client, Endpoint, ObservationOptions, ObservationRequest, RectangularGeometry, fetch_execution_evidence
from klab_client.errors import TransportError
from .request_plan import model_urns
from .scientific_checks import release_owned, require

SIDE = 1024
MAX_BYTES = 64 * 1024 * 1024
VALUE = 234.5
MEDIA = "image/tiff;application=geotiff"
EXPECTED_TRANSFORM = (100, 0, 200000, 0, -100, 6102400)
EXPECTED_BOUNDS = (200000, 6000000, 302400, 6102400)


def digest(path):
    result = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(65536), b""):
            result.update(block)
    return result.hexdigest()


@contextmanager
def measured_stream(client, *, interrupt=False):
    """Measure emitted chunks/RSS; optional deliberate read fault, no write replay."""
    process = psutil.Process()
    rss = process.memory_info().rss
    stats = {"chunks": 0, "bytes_emitted": 0, "largest_chunk": 0, "rss_before": rss,
        "sampled_rss_max": rss, "injected": False, "response_closed": False}
    original = client.transport.stream
    started = time.monotonic()
    @contextmanager
    def observed(*args, **kwargs):
        response = None
        try:
            with original(*args, **kwargs) as response:
                native_iterator = response.iter_raw
                def chunks(chunk_size=None):
                    for chunk in native_iterator(chunk_size=chunk_size):
                        if interrupt and stats["chunks"] >= 1:
                            stats["injected"] = True
                            raise httpx.ReadError("Controlled interruption of a read-only native stream")
                        stats["chunks"] += 1
                        stats["bytes_emitted"] += len(chunk)
                        stats["largest_chunk"] = max(stats["largest_chunk"], len(chunk))
                        stats["sampled_rss_max"] = max(stats["sampled_rss_max"], process.memory_info().rss)
                        yield chunk
                response.iter_raw = chunks
                try:
                    yield response
                finally:
                    response.iter_raw = native_iterator
        finally:
            stats["response_closed"] = response is not None and response.is_closed
    client.transport.stream = observed
    try:
        yield stats
    finally:
        client.transport.stream = original
        stats["elapsed_seconds"] = time.monotonic() - started
        stats["sampled_rss_growth"] = stats["sampled_rss_max"] - rss


def validate_native(path):
    """Independent fixed-support oracle; at most128x128 cells allocated per read."""
    import numpy as np
    import rasterio
    from rasterio.windows import Window
    cells = windows = 0
    with rasterio.open(path) as native:
        require((native.width, native.height, native.count) == (SIDE, SIDE, 1), "Native workload shape differs")
        require(native.crs is not None and native.crs.to_epsg() == 3857, "Native workload CRS differs")
        require(native.dtypes == ("float64",) and native.units == ("m",), "Native dtype/units differ")
        require(native.nodata is not None and math.isnan(native.nodata), "Native missing-value metadata differs")
        require(all(math.isclose(a, b, rel_tol=0, abs_tol=1e-7)
            for a, b in zip(tuple(native.transform)[:6], EXPECTED_TRANSFORM)), "Native axis/transform differs")
        require(all(math.isclose(a, b, rel_tol=0, abs_tol=1e-7)
            for a, b in zip(tuple(native.bounds), EXPECTED_BOUNDS)), "Native bounds differ")
        for row in range(0, SIDE, 128):
            for column in range(0, SIDE, 128):
                window = Window(column, row, min(128, SIDE - column), min(128, SIDE - row))
                values = native.read(1, window=window)
                valid = native.read_masks(1, window=window)
                require(np.all(values == VALUE) and np.all(valid == 255), "Native values/validity differ from independent literal oracle")
                cells += values.size
                windows += 1
    require(cells == SIDE * SIDE, "Native validation omitted cells")
    return {"cells": cells, "windows": windows, "largest_window_cells": 128 * 128,
        "expected_value": VALUE, "value_tolerance": 0, "units": "m", "dtype": "float64",
        "crs": "EPSG:3857", "bounds": EXPECTED_BOUNDS, "transform": EXPECTED_TRANSFORM,
        "coordinate_tolerance_m": 1e-7}


def interrupted_export(client, observation, destination, directory, *, overwrite=False):
    original = digest(destination) if destination.exists() else None
    with measured_stream(client, interrupt=True) as stats:
        try:
            observation.export_to_file(destination, media_type=MEDIA, max_bytes=MAX_BYTES, overwrite=overwrite)
        except TransportError:
            require(stats["injected"], "A genuine transport failure is not injected-interruption evidence")
        else:
            raise AssertionError("Controlled native stream interruption did not occur")
    require(stats["response_closed"] and stats["chunks"] == 1, "Interrupted native response cleanup unconfirmed")
    require(not list(directory.glob(".klab-export-*")), "Interrupted native staging file remains")
    if original is None:
        require(not destination.exists(), "Interrupted read published a partial new destination")
    else:
        require(digest(destination) == original, "Interrupted overwrite changed the existing valid destination")
    return stats | {"existing_destination_preserved": original is not None, "partial_destination_published": False}


def run(state):
    state = Path(state)
    config = json.loads((state / "scientist.json").read_text())
    directory = state / "native-stream-workload"
    directory.mkdir()
    session = context = None
    primary = None
    with Client(config["urls"]["runtime"], config["token"], agent_name=config["username"],
        **{name: Endpoint(config["urls"][name], config["token"]) for name in ("reasoner", "resources", "resolver")}) as client:
        try:
            client.initialize_user_scope(local_federation=True)
            session = client.create_session(name="owned bounded native stream workload")
            context = session.create_context()
            region = context.observe(ObservationRequest("consolidation:Region",
                geometry=RectangularGeometry(200000, 302400, 6000000, 6102400, SIDE, SIDE))).result(120)
            observation = context.within(region).observe("consolidation:Elevation of consolidation:Region in m",
                options=ObservationOptions(scenarios=["consolidation.scenario"])).result(300)
            evidence = fetch_execution_evidence(observation)
            urns = model_urns(evidence)
            require(any(urn.startswith(("consolidation.scenario.", "consolidation.scenario:")) for urn in urns),
                "Recorded served plan did not use the accepted scenario model")
            require(observation.fetch_data([0, SIDE - 1, SIDE * SIDE // 2, SIDE * SIDE - 1], curve="D2_YX").values ==
                (VALUE,) * 4, "Sparse native cells differ from independent literal oracle")
            path = directory / "native.tiff"
            with measured_stream(client) as transfer:
                exported = observation.export_to_file(path, media_type=MEDIA, max_bytes=MAX_BYTES)
            require(8 * 1024 * 1024 <= exported.bytes_written <= MAX_BYTES, "Native workload did not meet the prespecified byte range")
            require(transfer["bytes_emitted"] == exported.bytes_written and
                0 < transfer["largest_chunk"] <= 65536 and transfer["response_closed"], "Native chunk/byte/response invariants failed")
            validation = validate_native(path)
            checksum = digest(path)
            interruptions = [interrupted_export(client, observation, directory / "aborted.tiff", directory),
                interrupted_export(client, observation, path, directory, overwrite=True)]
            require(digest(path) == checksum, "Valid native artifact changed during interruption checks")
            report = {"passed": True, "model_urns": urns, "recorded_evidence_partial": evidence.partial,
                "observation_id": observation.id, "validation": validation, "native_sha256": checksum,
                "native_bytes": exported.bytes_written, "transfer": transfer, "interruptions": interruptions,
                "provider_capacity_soak": "NOT_ESTABLISHED", "categorical_time_multishard": "NOT_TESTED",
                "historical_replication": "NOT_CLAIMED", "rss_limit": "sampled at emitted chunks, not universal peak RSS"}
        except BaseException as error:
            primary = error
            raise
        finally:
            release_owned(context, session, failure=primary)
    (state / "native-stream-workload.json").write_text(json.dumps(report, indent=2))
    return report
