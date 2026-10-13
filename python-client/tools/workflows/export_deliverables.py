"""Verified numerical handle-to-GeoTIFF round trips; optional reader, never fake throughput."""
import argparse
from datetime import datetime, timezone
from decimal import Decimal
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import subprocess
import sys
import time
import uuid

from klab_client import ContextOptions, ObservationRequest, RectangularGeometry, Session, load_handle
from klab_client.experiment import verify_grid
from .compute_and_analyze import initialize, make_client, run_workload, summarize

MEDIA_TYPE = "image/tiff;application=geotiff"
WEST, SOUTH, EAST, NORTH = 200000, 6000000, 200500, 6000400
COLUMNS, ROWS, CELL_SIZE = 5, 4, 100


def native_values():
    """Independent XY-coordinate oracle, with a valid zero and a missing cell."""
    return tuple(None if (x, y) == (2, 1) else Decimal(x * 10 + y)
        for y in range(ROWS) for x in range(COLUMNS))


def validate_dataset(dataset, source_unit=None):
    """Validate world coordinates, not just array shape or decoder round-tripping."""
    if (dataset.width, dataset.height, dataset.count) != (COLUMNS, ROWS, 1):
        raise AssertionError("GeoTIFF shape/band count differs from the reference")
    if dataset.crs is None or dataset.crs.to_epsg() != 3857:
        raise AssertionError("GeoTIFF CRS is not EPSG:3857")
    expected_transform = (100, 0, WEST, 0, -100, NORTH)
    if any(abs(a-b) > 1e-8 for a, b in zip(tuple(dataset.transform)[:6], expected_transform)):
        raise AssertionError("GeoTIFF transform/orientation differs from the reference")
    if tuple(dataset.bounds) != (WEST, SOUTH, EAST, NORTH) or dataset.dtypes[0] not in ("float32", "float64"):
        raise AssertionError("GeoTIFF bounds/dtype differs from the numeric reference")
    if dataset.nodata is None or not math.isnan(dataset.nodata):
        raise AssertionError("GeoTIFF must advertise NaN nodata for the missing reference cell")
    if source_unit is not None and dataset.units[0] != source_unit:
        raise AssertionError("GeoTIFF measurement unit is missing or differs from the source")
    values, masks = dataset.read(1), dataset.read_masks(1)
    missing = zeros = 0
    for row in range(ROWS):
        for column in range(COLUMNS):
            x, y = dataset.xy(row, column)
            expected_x, expected_y = WEST + (column + .5) * CELL_SIZE, NORTH - (row + .5) * CELL_SIZE
            if abs(x-expected_x) > 1e-8 or abs(y-expected_y) > 1e-8:
                raise AssertionError("GeoTIFF cell center is not the reference coordinate")
            # Native grid y increases northward; GeoTIFF rows increase southward.
            native_y = ROWS - 1 - row
            expected_missing = (column, native_y) == (2, 1)
            value = float(values[row][column])
            if expected_missing:
                if not math.isnan(value):
                    raise AssertionError(f"GeoTIFF missing cell differs at column={column}, row={row}")
                missing += 1
            elif not math.isfinite(value) or value != column * 10 + native_y:
                raise AssertionError(f"GeoTIFF value/orientation differs at column={column}, row={row}: {value}")
            else:
                zeros += value == 0
            if bool(masks[row][column]) == expected_missing:
                raise AssertionError("GeoTIFF advertised nodata mask differs from the reference")
    if missing != 1 or zeros != 1:
        raise AssertionError("GeoTIFF zero/missingness cardinality differs")
    nodata = dataset.nodata
    return {"verified_cells": COLUMNS * ROWS, "dtype": dataset.dtypes[0], "crs": "EPSG:3857",
        "transform": list(tuple(dataset.transform)[:6]), "missing_nan_cells": missing, "valid_zero_cells": zeros,
        "nodata_tag": "NaN" if nodata is not None and math.isnan(nodata) else nodata,
        "mask_checked": nodata is not None, "file_unit": dataset.units[0], "file_tags": dataset.tags(),
        "metadata_note": "NaN nodata and mask are required; source units are checked when supplied. Revision tags are not inferred."}


def worker(configuration, bundle, output):
    """Separate process: current authorized identity -> original Job -> genuine export -> reader."""
    record = {"outcome": "failed", "stages_seconds": {}}
    started = time.perf_counter()
    def step(name, operation):
        at = time.perf_counter()
        try:
            return operation()
        finally:
            record["stages_seconds"][name] = time.perf_counter() - at
    try:
        with make_client(configuration) as client:
            observation = step("restore_observation", lambda: client.restore_handle(load_handle(bundle / "observation.json")))
            job = step("restore_job", lambda: client.restore_handle(load_handle(bundle / "job.json")))
            original = step("retrieve_original_job", lambda: job.result(120))
            record.update(observation_id=observation.id, job_id=job.id,
                scope=observation._context.get_context_id(), source_unit=observation.units, worker_pid=os.getpid())
            if original.id != observation.id or observation.units != json.loads((bundle / "expected.json").read_text())["source_unit"]:
                raise AssertionError("Restored Job/result identity or source unit changed")
            native = step("native_readback", lambda: observation.fetch_data(range(20), curve="D2_YX"))
            if native.values != native_values():
                raise AssertionError("Fresh-process native readback differs from the coordinate oracle")
            if not any(item.media_type == MEDIA_TYPE for item in client.export_formats(observation)):
                raise AssertionError("No advertised numeric GeoTIFF exporter")
            result = step("export", lambda: observation.export_to_file(output, media_type=MEDIA_TYPE))
            record.update(exported_bytes=result.bytes_written, schema_id=result.schema_id, output=str(output))
            digest = hashlib.sha256()
            with output.open("rb") as file:
                for chunk in iter(lambda: file.read(65536), b""):
                    digest.update(chunk)
            record["file_sha256"] = digest.hexdigest()
            import rasterio
            record.update(rasterio_version=rasterio.__version__, gdal_version=rasterio.__gdal_version__)
            def verify():
                with rasterio.open(output) as dataset:
                    record["raster_observed"] = {"shape": [dataset.height, dataset.width], "crs": str(dataset.crs),
                        "transform": list(tuple(dataset.transform)[:6]), "dtype": dataset.dtypes[0],
                        "nodata_tag": str(dataset.nodata), "file_unit": dataset.units[0],
                        "first_cell_center": dataset.xy(0, 0), "first_cell": float(dataset.read(1)[0][0])}
                    return validate_dataset(dataset, observation.units)
            record["raster"] = step("raster_validation", verify)
            record.update(outcome="verified", observation_id=observation.id, job_id=job.id,
                scope=observation._context.get_context_id(), exported_bytes=result.bytes_written,
                verified_cells=record["raster"]["verified_cells"], schema_id=result.schema_id,
                source_unit=observation.units)
    except Exception as error:
        record["error_type"] = type(error).__name__
        # Known-fixture numeric validation errors contain no supplied credentials.
        if isinstance(error, AssertionError):
            record["validation_error"] = str(error)
    record["elapsed_seconds"] = time.perf_counter() - started
    return record


def compute(configuration, session_id, controls, directory, number):
    record = {"sample": number, "stages_seconds": {}, "outcome": "failed"}
    context = None
    started = time.perf_counter()
    with make_client(configuration) as client:
        try:
            at = time.perf_counter()
            context = Session(client, session_id).create_context(options=ContextOptions(persistence="EXPLICIT_ACTION", name=f"geotiff-{number}"))
            record["context_id"] = context.id
            region = context.observe(ObservationRequest("earth:Region", geometry=RectangularGeometry(WEST, EAST, SOUTH, NORTH, COLUMNS, ROWS))).result(120)
            verify_grid(region)
            focused = context.within(region)
            (controls / "reset-field").write_text("reset this serial test-owned field")
            job = focused.observe("geography:Aspect in degree_angle")
            observation = job.result(120)
            verify_grid(observation)
            if observation.fetch_data(range(20), curve="D2_YX").values != native_values() or not observation.units:
                raise AssertionError("Computed field/source unit differs from the native oracle")
            record["stages_seconds"]["create_compute_verify"] = time.perf_counter() - at
            observation.to_handle().save(directory / "observation.json")
            job.to_handle().save(directory / "job.json")
            (directory / "expected.json").write_text(json.dumps({"source_unit": observation.units}))
            record.update(outcome="verified", observation_id=observation.id, job_id=job.id)
        except Exception as error:
            record["error_type"] = type(error).__name__
            if isinstance(error, AssertionError):
                record["validation_error"] = str(error)
    record["elapsed_seconds"] = time.perf_counter() - started
    return record, context


def export_sample(configuration_path, bundle, directory, number):
    record_path = directory / "worker.json"
    started = time.perf_counter()
    process = subprocess.run([sys.executable, str(Path(__file__).resolve().parents[1] / "geotiff_throughput.py"), "worker",
        "--fixture-config", str(configuration_path), "--bundle", str(bundle), "--output", str(directory / "field.tiff"),
        "--report", str(record_path)], capture_output=True, text=True, timeout=180)
    record = json.loads(record_path.read_text()) if record_path.exists() else {"outcome": "failed", "error_type": "WorkerProcessError"}
    if process.returncode and record["outcome"] == "verified":
        record = {"outcome": "failed", "error_type": "WorkerExitError"}
    record.update(sample=number, fresh_process_wall_seconds=time.perf_counter()-started)
    record["elapsed_seconds"] = record["fresh_process_wall_seconds"]
    return record


def summary(records, elapsed):
    result = summarize(records, elapsed)
    if result["acceptance_passed"]:
        result["verified_exported_bytes_per_wall_second"] = sum(record["exported_bytes"] for record in records) / elapsed
        result["measurement_note"] = "Includes fresh-process restore, export, independent reader and mode-specific setup; not network bandwidth or capacity."
    return result


def run(args):
    configuration = json.loads(args.fixture_config.read_text())
    directory = args.work_dir / ("geotiff-run-" + uuid.uuid4().hex)
    directory.mkdir()
    controls = args.server_state_dir / "controls"
    if not controls.is_dir():
        raise ValueError("Full numerical acceptance requires the explicit TEST controls")
    report = {"started_utc": datetime.now(timezone.utc).isoformat(), "mode": args.mode,
        "python": platform.python_version(), "concurrency": args.concurrency, "records": [], "warmup": [],
        "client_commit": subprocess.run(["git", "rev-parse", "HEAD"], cwd=Path(__file__).resolve().parents[3], capture_output=True, text=True, check=True).stdout.strip(),
        "working_tree_dirty": bool(subprocess.run(["git", "status", "--porcelain"], cwd=Path(__file__).resolve().parents[3], capture_output=True, text=True, check=True).stdout),
        "source_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), "work_dir": str(directory)}
    for filename, key in (("fixture-provenance.json", "fixture_provenance"), ("run-report.json", "stack_provenance")):
        path = args.server_state_dir / filename
        if path.is_file():
            report[key] = json.loads(path.read_text())
    session_id = None
    try:
        report["services"] = initialize(configuration)
        with make_client(configuration) as client:
            session_id = client.create_session(name="verified-geotiff-throughput").id
        def sample(number, seed=None):
            work = directory / f"sample-{number}"
            work.mkdir()
            started = time.perf_counter()
            context = None
            if seed is None:
                computed, context = compute(configuration, session_id, controls, work, number)
                if computed["outcome"] != "verified":
                    return computed
                bundle = work
            else:
                computed, bundle = {}, seed
            record = export_sample(args.fixture_config, bundle, work, number)
            if context is not None:
                with make_client(configuration) as client:
                    if not client.attach_context(context.id).release():
                        record.update(outcome="failed", error_type="ContextCleanupError")
                record.setdefault("stages_seconds", {}).update(computed["stages_seconds"])
            record["elapsed_seconds"] = time.perf_counter() - started
            return record
        for number in range(args.warmup):
            warm = sample(-number-1)
            report["warmup"].append(warm)
            if warm["outcome"] != "verified":
                raise RuntimeError("Warmup scientific acceptance failed; measurement not started")
        seed = None
        if args.mode == "repeated-export":
            seed = directory / "seed"
            seed.mkdir()
            report["seed"], _ = compute(configuration, session_id, controls, seed, -1000)
            if report["seed"]["outcome"] != "verified":
                raise RuntimeError("Seed scientific acceptance failed")
        records, elapsed = run_workload(lambda number: sample(number, seed), samples=args.samples,
            concurrency=args.concurrency, duration=args.duration, on_result=lambda record: print(json.dumps(record), flush=True))
        report["records"] = records
        report["summary"] = summary(records, elapsed)
    except Exception as error:
        report["error_type"] = type(error).__name__
        report["summary"] = {"acceptance_passed": False}
    finally:
        if session_id is not None:
            try:
                with make_client(configuration) as client:
                    report["session_cleanup"] = Session(client, session_id).release()
            except Exception:
                report["session_cleanup"] = False
            if not report["session_cleanup"]:
                report["summary"] = {"acceptance_passed": False, "error_type": "SessionCleanupError"}
        report["finished_utc"] = datetime.now(timezone.utc).isoformat()
        args.report.write_text(json.dumps(report, indent=2))
    print(json.dumps(report["summary"]), flush=True)
    return 0 if report["summary"]["acceptance_passed"] else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    parent = sub.add_parser("run")
    parent.add_argument("--fixture-config", type=Path, required=True)
    parent.add_argument("--server-state-dir", type=Path, required=True)
    parent.add_argument("--work-dir", type=Path, required=True)
    parent.add_argument("--report", type=Path, required=True)
    parent.add_argument("--samples", type=int, default=3)
    parent.add_argument("--duration", type=float, default=15)
    parent.add_argument("--warmup", type=int, default=1)
    parent.add_argument("--mode", choices=("full-roundtrip", "repeated-export"), default="full-roundtrip")
    parent.add_argument("--concurrency", type=int, default=1)
    child = sub.add_parser("worker")
    child.add_argument("--fixture-config", type=Path, required=True)
    child.add_argument("--bundle", type=Path, required=True)
    child.add_argument("--output", type=Path, required=True)
    child.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "worker":
        record = worker(json.loads(args.fixture_config.read_text()), args.bundle, args.output)
        args.report.write_text(json.dumps(record, indent=2))
        return 0 if record["outcome"] == "verified" else 1
    if (args.samples < 1 or args.concurrency < 1 or args.warmup < 0 or not math.isfinite(args.duration)
            or args.duration < 0 or not args.work_dir.is_dir() or not args.report.parent.is_dir() or args.report.exists()
            or args.mode == "full-roundtrip" and args.concurrency != 1):
        parser.error("Require bounded valid counts/paths; full recomputation is serial because the TEST field counter is shared")
    try:
        import rasterio
    except ImportError:
        parser.error("Install the optional [throughput] reader extra before running live numerical acceptance")
    return run(args)


if __name__ == "__main__":
    raise SystemExit(main())
