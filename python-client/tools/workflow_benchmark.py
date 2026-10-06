"""Real full-workflow constant-field benchmark; never substitutes local values.

Requires the isolated fixture_hub deployment and python.fixture model, or an
explicitly configured deployment with the same known scientific contract.
Failures produce a report and nonzero exit, never successful throughput figures.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor, wait, FIRST_COMPLETED
from datetime import datetime, timezone
from decimal import Decimal
import hashlib
import json
import math
from pathlib import Path
import platform
import subprocess
import time
import threading

from klab_client import Client, Endpoint, ObservationImpl, Session
from klab_client.dto import storage_semantics
from klab_client.experiment import rectangle_geometry, verify_region, verify_grid


def make_client(configuration):
    if configuration is None:
        return Client.from_env()
    return Client(configuration["urls"]["runtime"], configuration["token"],
        **{s: Endpoint(configuration["urls"][s], configuration["token"]) for s in ("reasoner", "resources", "resolver")},
        agent_name=configuration["username"], timeout=30, poll_interval=.1,
        service_ids=configuration.get("service_ids", ()), runtime_service_id=configuration.get("runtime_service_id"))


def initialize(configuration):
    with make_client(configuration) as client:
        services = client.initialize_user_scope(email_address="test@example.invalid" if configuration else None,
                                                local_federation=configuration is not None)
        if configuration is not None:
            configuration["service_ids"] = list(client.service_ids)
            configuration["runtime_service_id"] = client.runtime_service_id
        models = client.resources.list("MODEL")
        if not models:
            raise RuntimeError("No executable model is available; fixture provisioning did not complete")
        return list(services)


def sample(configuration, session_id, number, *, retain=False):
    record = {"sample": number, "stages_seconds": {}}
    started = time.perf_counter()
    with make_client(configuration) as client:
        def step(name, operation):
            at = time.perf_counter()
            result = operation()
            record["stages_seconds"][name] = time.perf_counter() - at
            return result
        try:
            context = step("create_context", lambda: Session(client, session_id).create_context(name=f"throughput-{number}"))
            record["context_id"] = context.id
            print(f"sample {number}: context {context.id}", flush=True)
            region_semantics = step("resolve_region", lambda: client.reasoner.resolve_observable("earth:Region"))
            region_job = step("submit_region", lambda: context.submit(ObservationImpl(urn=f"python.fixture:region{number}",
                name=f"region{number}", observable=region_semantics, geometry=rectangle_geometry())))
            record["region_job_id"] = region_job.id
            region = step("wait_region", lambda: region_job.result(120))
            verify_region(region)
            record["region_model_coverage"] = region.raw.get("resolvedCoverage")
            record["region_id"] = region.id
            focused = context.within(region)
            semantics = step("resolve_elevation", lambda: client.reasoner.resolve_observable("geography:Elevation in m"))
            if semantics.raw.get("unit", {}).get("definition") != "m":
                raise AssertionError("Reasoner did not preserve the requested metre unit")
            job = step("submit_elevation", lambda: focused.submit(ObservationImpl(urn="", observable=semantics)))
            record["job_id"] = job.id
            elevation = step("wait_elevation", lambda: job.result(120))
            if elevation.id <= 0 or elevation.raw.get("resolvedCoverage") != 1 or elevation.units != "m":
                raise AssertionError("Elevation is not a complete committed metre observation")
            record["observation_id"] = elevation.id
            verify_grid(elevation)
            data = step("read_20_cells", lambda: elevation.fetch_data(range(20), curve="D2_YX"))
            if any(value is None or Decimal(value) != Decimal("123.25") for value in data.values):
                raise AssertionError("Actual server values differ from independent constant-field oracle 123.25 m")
            mm_semantics = storage_semantics(client.reasoner.resolve_observable("geography:Elevation in mm"))
            mm = step("read_20_converted_cells", lambda: elevation.fetch_data(range(20), curve="D2_YX", semantics=mm_semantics))
            if any(value is None or Decimal(value) != Decimal("123250") for value in mm.values):
                raise AssertionError("Server unit conversion differs from oracle 123250 mm")
            record["values_sha256"] = hashlib.sha256(json.dumps(data.text).encode()).hexdigest()
            record["verified_cells"] = 40
            # A separate client must retrieve the actual persisted result and data.
            with make_client(configuration) as other:
                attached = step("attach_context", lambda: other.attach_context(context.id))
                again = step("resume_job", lambda: attached.within(region.id).job(job.id).result(120))
                if again.id != elevation.id or again.fetch_data([0], curve="D2_YX").values != (Decimal("123.25"),):
                    raise AssertionError("Attached readback differs from completed computation")
            if not retain and not context.release():
                raise AssertionError("Explicit disposable context release failed")
            record["context_retained"] = retain
            record["outcome"] = "verified"
        except Exception as error:
            record["outcome"] = "failed"
            record["error"] = client.transport.redact(type(error).__name__ + ": " + str(error))
            record["cleanup"] = "failed context retained for diagnosis; see recorded IDs"
    record["elapsed_seconds"] = time.perf_counter() - started
    return record


def read_sample(configuration, seed, number):
    record = {"sample": number, "stages_seconds": {}, "context_id": seed["context_id"],
              "observation_id": seed["observation_id"]}
    started = time.perf_counter()
    with make_client(configuration) as client:
        try:
            context = client.attach_context(seed["context_id"]).within(seed["region_id"])
            observation = context.job(seed["job_id"]).result(120)
            if observation.id != seed["observation_id"]:
                raise AssertionError("Repeated-read job result changed identity")
            verify_grid(observation)
            at = time.perf_counter()
            data = observation.fetch_data(range(20), curve="D2_YX")
            record["stages_seconds"]["read_20_cells"] = time.perf_counter() - at
            if any(v is None or Decimal(v) != Decimal("123.25") for v in data.values):
                raise AssertionError("Repeated actual storage values differ from oracle")
            record.update(outcome="verified", verified_cells=20)
        except Exception as error:
            record.update(outcome="failed", error=client.transport.redact(type(error).__name__ + ": " + str(error)))
    record["elapsed_seconds"] = time.perf_counter() - started
    return record


class ResourceMonitor:
    """Optional OS-observed counters for only the explicitly supplied server state."""
    def __init__(self, state):
        self.stop_event = threading.Event()
        self.peak = 0
        self.processes = []
        if state:
            import psutil
            for name in ("graphdb", "resources", "reasoner", "resolver", "runtime"):
                file = state / f"{name}.pid"
                if file.exists():
                    process = psutil.Process(int(file.read_text()))
                    if str(state / f"{name}.args") not in " ".join(process.cmdline()):
                        raise RuntimeError("Resource counter PID identity does not match the supplied state")
                    self.processes.append(process)
        self.initial = self.counters()
        self.thread = threading.Thread(target=self.watch, daemon=True)
    def counters(self):
        cpu = read = written = rss = 0
        for process in self.processes:
            times = process.cpu_times()
            io = process.io_counters()
            cpu += times.user + times.system
            read += io.read_bytes
            written += io.write_bytes
            rss += process.memory_info().rss
        self.peak = max(self.peak, rss)
        return {"cpu_seconds": cpu, "os_read_bytes": read, "os_write_bytes": written}
    def watch(self):
        while not self.stop_event.wait(.2):
            try:
                self.counters()
            except Exception:
                return
    def __enter__(self):
        self.thread.start()
        return self
    def __exit__(self, *args):
        self.stop_event.set()
        self.thread.join()
    def result(self):
        if not self.processes:
            return {"collected": False}
        final = self.counters()
        return {"collected": True, "peak_aggregate_rss_bytes": self.peak,
                **{key: final[key] - self.initial[key] for key in final},
                "io_note": "OS process counters; not physical-disk or network bandwidth measurements"}


def run_workload(operation, *, samples, concurrency, duration, on_result):
    """Bound queued work, sustain the declared interval, drain active work on failure."""
    records, next_number, failed = [], 0, False
    started = time.perf_counter()
    until = started + duration
    with ThreadPoolExecutor(max_workers=concurrency) as workers:
        active = set()
        while active or (not failed and (next_number < samples or time.perf_counter() < until)):
            while not failed and len(active) < concurrency and (next_number < samples or time.perf_counter() < until):
                active.add(workers.submit(operation, next_number))
                next_number += 1
            if not active:
                break
            completed, active = wait(active, return_when=FIRST_COMPLETED)
            for future in completed:
                try:
                    record = future.result()
                except Exception as error:
                    record = {"outcome": "failed", "error_type": type(error).__name__}
                records.append(record)
                failed |= record["outcome"] != "verified"
                on_result(record)
    return records, time.perf_counter() - started


def summarize(records, elapsed):
    verified = [r for r in records if r["outcome"] == "verified"]
    failures = len(records) - len(verified)
    result = {"submitted_samples": len(records), "verified_samples": len(verified), "failures": failures,
              "wall_seconds": elapsed, "acceptance_passed": failures == 0 and bool(verified)}
    if not math.isfinite(elapsed) or elapsed <= 0:
        raise ValueError("Measured wall interval must be finite and positive")
    if result["acceptance_passed"]:
        times = sorted(r["elapsed_seconds"] for r in verified)
        result.update(verified_workflows_per_second=len(verified)/elapsed,
                      verified_cells_per_second=sum(r["verified_cells"] for r in verified)/elapsed,
                      latency_seconds={f"p{p}": times[max(0, math.ceil(len(times)*p/100)-1)] for p in (50, 95, 99)})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture-config", type=Path, help="Explicit generated scientist.json; never log its contents")
    parser.add_argument("--samples", type=int, default=1)
    parser.add_argument("--concurrency", type=int, default=1)
    parser.add_argument("--duration", type=float, default=0, help="Minimum sustained measurement seconds")
    parser.add_argument("--warmup", type=int, default=0, help="Verified workflows excluded from measurement")
    parser.add_argument("--mode", choices=("full-workflow", "repeated-read"), default="full-workflow")
    parser.add_argument("--server-state-dir", type=Path, help="Optional explicit state for OS server counters")
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    if args.samples < 1 or args.concurrency < 1 or args.warmup < 0 or not math.isfinite(args.duration) or args.duration < 0 or not args.report.parent.is_dir():
        parser.error("Positive sample/concurrency counts and an existing report parent are required")
    configuration = json.loads(args.fixture_config.read_text()) if args.fixture_config else None
    report = {"started_utc": datetime.now(timezone.utc).isoformat(), "python": platform.python_version(),
              "mode": args.mode, "concurrency": args.concurrency, "minimum_samples": args.samples,
              "minimum_duration_seconds": args.duration, "warmup_count": args.warmup,
              "fixture": "123.25 metre constant field through Runtime", "records": []}
    revision = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True)
    report["client_head"] = revision.stdout.strip()
    report["working_tree_dirty"] = bool(subprocess.run(["git", "status", "--porcelain"], capture_output=True, text=True).stdout)
    report["implementation_sha256"] = {}
    root = Path(__file__).resolve().parents[2]
    for relative in ("python-client/tools/workflow_benchmark.py", "python-client/tools/fixture_hub.py",
                     "python-client/src/klab_client/client.py", "python-client/src/klab_client/transport.py",
                     "klab.core.services/src/main/java/org/integratedmodelling/klab/services/scopes/ScopeManager.java",
                     "klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/RuntimeService.java"):
        report["implementation_sha256"][relative] = hashlib.sha256((root / relative).read_bytes()).hexdigest()
    if args.server_state_dir:
        fixture = args.server_state_dir / "assets/python.fixture/src/python.fixture.kim"
        if fixture.exists():
            report["fixture_model_sha256"] = hashlib.sha256(fixture.read_bytes()).hexdigest()
    session_id = None
    started = time.perf_counter()
    try:
        report["services"] = initialize(configuration)
        with make_client(configuration) as client:
            session_id = client.create_session(name="reproducible-throughput").id
        report["session_id"] = session_id
        report["warmup"] = []
        for i in range(args.warmup):
            warm = sample(configuration, session_id, -i-1)
            report["warmup"].append(warm)
            if warm["outcome"] != "verified":
                raise AssertionError("Warmup failed; measurement not started")
        seed = None
        if args.mode == "repeated-read":
            seed = sample(configuration, session_id, -1000, retain=True)
            report["seed"] = seed
            if seed["outcome"] != "verified":
                raise AssertionError("Repeated-read seed computation failed")
        operation = (lambda i: sample(configuration, session_id, i)) if seed is None else (
                     lambda i: read_sample(configuration, seed, i))
        with ResourceMonitor(args.server_state_dir.resolve() if args.server_state_dir else None) as monitor:
            records, elapsed = run_workload(operation, samples=args.samples, concurrency=args.concurrency,
                duration=args.duration, on_result=lambda record: print(json.dumps(record), flush=True))
            report["records"] = records
            report["resources"] = monitor.result()
        report["summary"] = summarize(records, elapsed)
        if seed is not None and "verified_workflows_per_second" in report["summary"]:
            report["summary"]["verified_read_cycles_per_second"] = report["summary"].pop("verified_workflows_per_second")
        report["summary"]["measurement_scope"] = ("fresh-context complete scientific workflows, server code/model caches may be warm"
            if seed is None else "reattach/resume and repeated point reads after one computation; no recomputation")
        report["summary"]["total_run_seconds"] = time.perf_counter() - started
        if report["summary"]["acceptance_passed"]:
            with make_client(configuration) as client:
                if seed and not client.attach_context(seed["context_id"]).release():
                    raise AssertionError("Repeated-read seed context release failed")
                if not Session(client, session_id).release():
                    raise AssertionError("Session release failed")
    except Exception as error:
        with make_client(configuration) as client:
            report["error"] = client.transport.redact(type(error).__name__ + ": " + str(error))
        report["summary"] = {"acceptance_passed": False, "wall_seconds": time.perf_counter()-started}
    report["finished_utc"] = datetime.now(timezone.utc).isoformat()
    args.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report["summary"]), flush=True)
    return 0 if report["summary"]["acceptance_passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
