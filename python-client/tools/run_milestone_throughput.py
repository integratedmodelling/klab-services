"""Verified complete experiments/minute; explicit authorized 5-batch configurations.

Requires passing correctness at this candidate first. No service restart or cold
Java claim; failures stay in denominators and invalidate scientific throughput.
"""
import argparse
import math
from pathlib import Path
import statistics
import time
from threading import Event, Thread

from build_milestone_deliverable import validate_acceptance
from workflows import milestone
from workflows.scientific_checks import require


class PythonMonitor:
    """Sample this parent process (all worker threads), not arbitrary Java processes."""
    def __enter__(self):
        self.stop, self.peak, self.samples = Event(), 0, 0
        try:
            import psutil
            self.process = psutil.Process()
        except ImportError:
            self.process = None
        def sample():
            while not self.stop.is_set():
                if self.process is not None:
                    self.peak = max(self.peak, self.process.memory_info().rss)
                    self.samples += 1
                self.stop.wait(.1)
        self.thread = Thread(target=sample, daemon=True)
        self.thread.start()
        return self

    def __exit__(self, *args):
        self.stop.set()
        self.thread.join()

    def report(self):
        return {"collected": self.process is not None, "peak_rss_bytes": self.peak if self.samples else None,
                "samples": self.samples, "method": "parent Python RSS, 100ms polling; excludes child recovery and remote Java"}


def summarize(records, elapsed):
    outcomes = [o for record in records for o in record.get("batch", {}).get("outcomes", [])]
    processed = [result for record in records for result in record.get("processed", {}).values() if result]
    complete = [o for o in outcomes if o["state"] == "succeeded"]
    failed_batches = sum(not record.get("matrix_verified", False) for record in records)
    durations = sorted(o["elapsed"] for o in complete)
    cells = sum(result["raster"]["verified_cells"] for result in processed)
    exported = sum(result["exported_bytes"] for result in processed)
    success = len(records) == 5 and len(outcomes) == len(complete) == 20 and failed_batches == 0 and elapsed > 0
    stage_names = {name for result in processed for name in result.get("stages_seconds", {})}
    stages = {}
    for name in sorted(stage_names):
        values = sorted(result["stages_seconds"][name] for result in processed if name in result.get("stages_seconds", {}))
        stages[name] = {"median": statistics.median(values), "p95": values[math.ceil(.95 * len(values)) - 1], "samples": len(values)}
    return {"passed": success, "attempted": len(records) * 4, "completed": len(complete),
        "failed_or_unknown": len(records) * 4 - len(complete), "failed_batches": failed_batches,
        "wall_seconds": elapsed, "successful_experiments_per_minute": len(complete) * 60 / elapsed if success else None,
        "latency_median_seconds": statistics.median(durations) if durations else None,
        "latency_p95_seconds": durations[math.ceil(.95 * len(durations)) - 1] if durations else None,
        "p95_method": "nearest rank over successful complete-experiment durations",
        "verified_cells": cells, "exported_bytes": exported,
        "cells_per_second": cells / elapsed if success else None, "bytes_per_second": exported / elapsed if success else None,
        "setup_seconds": [r.get("setup_seconds") for r in records],
        "discovery_seconds": [r.get("discovery_seconds") for r in records],
        "cleanup_seconds": [o["cleanup_elapsed"] for o in outcomes],
        "stages_seconds": stages,
        "states": {state: sum(o["state"] == state for o in outcomes) for state in sorted({o["state"] for o in outcomes})}}


def measure(factory, fixture, output, *, workers, operation=milestone.run_case_batch):
    output = Path(output)
    output.mkdir()
    records = []
    started = time.perf_counter()
    with PythonMonitor() as memory:
        for index in range(5):
            try:
                record = operation(factory, fixture, output / str(index), workers=workers)
            except Exception as error:
                record = {"matrix_verified": False, "error_type": type(error).__name__}
            records.append(record)
            # Stop creating more remote work after failure. Unrun batches aren't submissions.
            if not record.get("matrix_verified", False):
                break
    result = summarize(records, time.perf_counter() - started)
    result["unrun_batches"] = 5 - len(records)
    result["run_ids"] = [r["run_id"] for r in records if "run_id" in r]
    result["python_memory"] = memory.report()
    milestone.write_json(output / "summary.json", result)
    return result


def _workload_identity(case):
    """Scientific choices that must stay fixed when only grid resolution changes."""
    oracle = case.oracle
    x_size, _, west, _, y_size, north = oracle.transform
    return {
        "name": case.experiment.name,
        "variation": case.experiment.variation,
        "resolution": case.resolution,
        "definition": case.experiment.request.observable,
        "options": case.experiment.request.options,
        "context": case.experiment.context_request.observable,
        "units": oracle.units,
        "crs": oracle.crs,
        "dtype": oracle.dtype,
        "extent": (west, north + y_size * oracle.height, west + x_size * oracle.width, north),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--acceptance", required=True, type=Path)
    parser.add_argument("--milestone-fixture", required=True, type=Path)
    parser.add_argument("--large-fixture", required=True, type=Path)
    parser.add_argument("--profile", required=True, type=Path)
    parser.add_argument("--certificate", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--allow-owned-work", action="store_true")
    parser.add_argument("--allow-throughput", action="store_true", help="Explicitly authorize up to 80 new complete experiments")
    args = parser.parse_args()
    if not args.allow_owned_work or not args.allow_throughput:
        parser.error("Explicit --allow-owned-work and --allow-throughput are both required")
    acceptance = validate_acceptance(args.acceptance)
    small, large = milestone.load_fixture(args.milestone_fixture), milestone.load_fixture(args.large_fixture)
    require(small.sha256 == acceptance["fixture_sha256"], "Correctness fixture differs from the accepted baseline")
    require(small.deployment == large.deployment and small.model_urn == large.model_urn,
            "Larger workload must use the same verified deployment and model")
    require(all(b.oracle.width * b.oracle.height > a.oracle.width * a.oracle.height for a, b in zip(small.cases, large.cases)),
            "Larger workload must actually contain more independently specified cells in every case")
    require(all(_workload_identity(small_case) == _workload_identity(large_case)
                for small_case, large_case in zip(small.cases, large.cases)),
            "Larger-grid comparison must preserve named scientific variations, units, extent and precision")
    identity = milestone.source_identity()
    args.output.mkdir(exist_ok=False)
    factory = milestone.factory_from_paths(args.profile, args.certificate, allow_owned_work=True)
    results = {}
    for name, fixture, fixture_path in (("correctness-grid", small, args.milestone_fixture), ("larger-grid", large, args.large_fixture)):
        for workers in (1, 4):
            key = name + "-workers-" + str(workers)
            results[key] = measure(factory, fixture, args.output / key, workers=workers)
            if results[key]["passed"]:
                recovery_started = time.perf_counter()
                try:
                    restored = milestone.launch_restore(fixture_path, args.output / key / "4/batch.json",
                        args.output / (key + "-restored"), args.profile, args.certificate)
                    results[key]["recovery_run_id"] = restored["run_id"]
                except Exception as error:
                    results[key]["passed"] = False
                    results[key]["recovery_error_type"] = type(error).__name__
                    for metric in ("successful_experiments_per_minute", "cells_per_second", "bytes_per_second"):
                        results[key][metric] = None
                results[key]["recovery_seconds"] = time.perf_counter() - recovery_started
                results[key]["full_journey_seconds"] = results[key]["wall_seconds"] + results[key]["recovery_seconds"]
    passed = all(r["passed"] for r in results.values()) and milestone.source_identity() == identity
    milestone.write_json(args.output / "throughput.json", {"version": 1, "kind": "milestone-throughput",
        "identity": identity, "acceptance_run_id": acceptance["run_id"], "passed": passed,
        "fixture_sha256": small.sha256, "large_fixture_sha256": large.sha256, "results": results,
        "warmup_batches": 0, "conditions": "No service restart; caches and remote process history unknown",
        "memory": {"python": "parent process sampled per configuration", "server": "unavailable: no verified owned process identities"},
        "remote_cleanup": "retained contexts; local cleanup reported separately; owner disposal still explicit",
        "recovery_timing": "fresh-process recovery of the last measured batch per configuration; separate and in full journey total"})
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
