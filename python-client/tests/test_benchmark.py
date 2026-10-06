import importlib.util
import math
from pathlib import Path
import threading

import pytest


def load_benchmark():
    file = Path(__file__).parents[1] / "tools/workflow_benchmark.py"
    spec = importlib.util.spec_from_file_location("benchmark_under_test", file)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_failed_sample_cannot_publish_successful_throughput():
    bench = load_benchmark()
    records = [{"outcome": "verified", "elapsed_seconds": 1, "verified_cells": 40},
               {"outcome": "failed", "elapsed_seconds": 2}]
    result = bench.summarize(records, 3)
    assert result["failures"] == 1 and not result["acceptance_passed"]
    assert "verified_workflows_per_second" not in result
    assert "verified_cells_per_second" not in result


def test_summary_uses_wall_interval_and_nearest_rank_latencies():
    bench = load_benchmark()
    records = [{"outcome": "verified", "elapsed_seconds": i, "verified_cells": 40} for i in range(1, 5)]
    result = bench.summarize(records, 2)
    assert result["verified_workflows_per_second"] == 2
    assert result["verified_cells_per_second"] == 80
    assert result["latency_seconds"] == {"p50": 2, "p95": 4, "p99": 4}
    assert not bench.summarize([], 1)["acceptance_passed"]
    for elapsed in (0, -1, math.nan, math.inf):
        with pytest.raises(ValueError):
            bench.summarize(records, elapsed)


def test_workload_is_bounded_and_records_actual_samples():
    bench = load_benchmark()
    lock = threading.Lock()
    active = [0]
    peak = [0]
    observed = []
    def operation(number):
        with lock:
            active[0] += 1
            peak[0] = max(active[0], peak[0])
        with lock:
            active[0] -= 1
        return {"sample": number, "outcome": "verified", "elapsed_seconds": .1, "verified_cells": 40}
    records, elapsed = bench.run_workload(operation, samples=7, concurrency=2, duration=0, on_result=observed.append)
    assert len(records) == len(observed) == 7 and peak[0] <= 2 and elapsed > 0
    assert sorted(r["sample"] for r in records) == list(range(7))


def test_workload_stops_new_submissions_after_failure():
    bench = load_benchmark()
    records, elapsed = bench.run_workload(lambda i: {"sample": i, "outcome": "failed"},
                                         samples=1000, concurrency=1, duration=10, on_result=lambda r: None)
    assert len(records) == 1 and elapsed < 1


def test_workload_turns_worker_exception_into_failed_record():
    bench = load_benchmark()
    def fail(i):
        raise RuntimeError("private value must not be copied into generic record")
    records, elapsed = bench.run_workload(fail, samples=1, concurrency=1, duration=0, on_result=lambda r: None)
    assert records == [{"outcome": "failed", "error_type": "RuntimeError"}]
    assert not bench.summarize(records, elapsed)["acceptance_passed"]
