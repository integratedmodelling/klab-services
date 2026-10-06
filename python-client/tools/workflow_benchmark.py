"""Real full-workflow constant-field benchmark; never substitutes local values.

Requires the isolated fixture_hub deployment and python.fixture model, or an
explicitly configured deployment with the same known scientific contract.
Failures produce a report and nonzero exit, never successful throughput figures.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from decimal import Decimal
import hashlib
import json
import math
from pathlib import Path
import platform
import subprocess
import time

from klab_client import Client, Endpoint, ObservationImpl, Session
from klab_client.dto import storage_semantics
from klab_client.experiment import rectangle_geometry


def make_client(configuration):
    if configuration is None:
        return Client.from_env()
    return Client(configuration["urls"]["runtime"], configuration["token"],
        **{s: Endpoint(configuration["urls"][s], configuration["token"]) for s in ("reasoner", "resources", "resolver")},
        agent_name=configuration["username"], timeout=30, poll_interval=.1,
        service_ids=configuration.get("service_ids", ()), runtime_service_id=configuration.get("runtime_service_id"))


def initialize(configuration):
    with make_client(configuration) as client:
        services = []
        for name in ("resources", "reasoner", "resolver", "runtime"):
            capability = getattr(client, name).capabilities()
            status = client.transport.request(name, "GET", "/public/status")
            services.append({"id": capability.service_id, "type": name.upper(),
                             "url": client.transport.endpoint(name).url, "status": status})
        for name in ("resources", "reasoner", "resolver", "runtime"):
            result = client.transport.request(name, "POST", "/notifyUserScope",
                json={"services": services, "emailAddress": "test@example.invalid", "localFederation": True}, ambiguous=True)
            if result is not True:
                raise RuntimeError(f"User-scope advertisement not accepted by {name}")
        if configuration is not None:
            configuration["service_ids"] = [s["id"] for s in services]
            configuration["runtime_service_id"] = next(s["id"] for s in services if s["type"] == "RUNTIME")
        models = client.resources.list("MODEL")
        if not models:
            raise RuntimeError("No executable model is available; fixture provisioning did not complete")
        return [{"type": s["type"], "id": s["id"], "operational": s["status"].get("operational")} for s in services]


def sample(configuration, session_id, number):
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
            if region.id <= 0 or region.raw.get("resolvedCoverage") != 1:
                raise AssertionError("Region did not reach full committed coverage")
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
            if elevation.geometry is None or elevation.geometry.shape != (5, 4):
                raise AssertionError("Scientific grid shape differs from the known 5 x 4 fixture")
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
            if not context.release():
                raise AssertionError("Explicit disposable context release failed")
            record["outcome"] = "verified"
        except Exception as error:
            record["outcome"] = "failed"
            record["error"] = client.transport.redact(type(error).__name__ + ": " + str(error))
            record["cleanup"] = "failed context retained for diagnosis; see recorded IDs"
    record["elapsed_seconds"] = time.perf_counter() - started
    return record


def summarize(records, elapsed):
    verified = [r for r in records if r["outcome"] == "verified"]
    failures = len(records) - len(verified)
    result = {"submitted_samples": len(records), "verified_samples": len(verified), "failures": failures,
              "wall_seconds": elapsed, "acceptance_passed": failures == 0 and bool(verified)}
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
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    if args.samples < 1 or args.concurrency < 1 or not args.report.parent.is_dir():
        parser.error("Positive sample/concurrency counts and an existing report parent are required")
    configuration = json.loads(args.fixture_config.read_text()) if args.fixture_config else None
    report = {"started_utc": datetime.now(timezone.utc).isoformat(), "python": platform.python_version(),
              "mode": "fresh-context-full-workflow", "concurrency": args.concurrency,
              "fixture": "123.25 metre constant field through Runtime", "records": []}
    revision = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True)
    report["client_head"] = revision.stdout.strip()
    session_id = None
    started = time.perf_counter()
    try:
        report["services"] = initialize(configuration)
        with make_client(configuration) as client:
            session_id = client.create_session(name="reproducible-throughput").id
        report["session_id"] = session_id
        with ThreadPoolExecutor(max_workers=args.concurrency) as workers:
            futures = [workers.submit(sample, configuration, session_id, i) for i in range(args.samples)]
            for future in as_completed(futures):
                result = future.result()
                report["records"].append(result)
                print(json.dumps(result), flush=True)
        report["summary"] = summarize(report["records"], time.perf_counter()-started)
        if report["summary"]["acceptance_passed"]:
            with make_client(configuration) as client:
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
