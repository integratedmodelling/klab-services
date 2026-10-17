"""Bounded, paced semantic API diagnostic on explicitly trusted existing services.

No peer initialization, sessions, contexts, observations, imports or restarts.
This measures request latency/rate, NOT scientific computation or server capacity.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import statistics
import time

from klab_client import DeploymentProfile, HubCertificateProvider, connect


class ProbeValidationError(ValueError):
    """A response did not satisfy the independently specified diagnostic oracle."""


def validate_budget(samples, warmup, interval):
    if type(samples) is not int or not 1 <= samples <= 20:
        raise ValueError("Choose 1..20 measured requests; this is not a load-test runner")
    if type(warmup) is not int or not 0 <= warmup <= 2:
        raise ValueError("Choose 0..2 warmup requests")
    if not isinstance(interval, (float, int)) or isinstance(interval, bool) or not math.isfinite(interval) or interval < .5:
        raise ValueError("At least 0.5 seconds of idle pacing is required between requests")


def candidate_summary(entry):
    metadata = entry.status.get("metadata", {})
    metadata = metadata.get("delegate", metadata)
    candidate = metadata.get("semantic.candidate")
    if not isinstance(candidate, dict):
        raise ProbeValidationError("Reasoner did not expose accepted-generation evidence")
    generation = candidate.get("acceptedGeneration")
    if type(generation) is not int or generation <= 0:
        raise ProbeValidationError("Reasoner has no accepted semantic generation")
    sources = candidate.get("acceptedSources")
    return {"accepted_generation": generation, "bean_status": candidate.get("beanStatus"),
            "logical_status": candidate.get("logicalStatus"),
            "diagnostic_count": len(candidate.get("diagnostics", [])),
            "accepted_source_manifest_sha256": hashlib.sha256(
                json.dumps(sources, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
                if isinstance(sources, dict) else None}


def check_observable(observable, expected_urn, expected_unit):
    if observable.semantics.urn != expected_urn or observable.raw.get("artifactType") != "NUMBER":
        raise ProbeValidationError("Observable semantic identity or numeric artifact type differs from the oracle")
    unit = observable.raw.get("unit")
    definition = unit.get("definition") if isinstance(unit, dict) else None
    if expected_unit is not None and definition != expected_unit:
        raise ProbeValidationError("Requested unit is absent or differs in the resolved observable; do not invent mediation")
    return {"semantic_urn": observable.semantics.urn, "artifact_type": "NUMBER",
            "unit_definition": definition}


def measure(client, *, definition, expected_urn, expected_unit=None, samples=3, warmup=1, interval=.5):
    validate_budget(samples, warmup, interval)
    latencies = []
    observed = None
    started = None
    for index in range(warmup + samples):
        if index:
            time.sleep(interval)
        if index == warmup:
            started = time.perf_counter()
        request_started = time.perf_counter()
        observable = client.reasoner.resolve_observable(definition)
        elapsed = time.perf_counter() - request_started
        observed = check_observable(observable, expected_urn, expected_unit)
        if index >= warmup:
            latencies.append(elapsed)
    wall = time.perf_counter() - started
    return {"verified_requests": len(latencies), "warmup_requests": warmup,
            "idle_seconds_between_requests": interval, "measurement_wall_seconds": wall,
            "paced_diagnostic_requests_per_second": len(latencies) / wall if wall > 0 else None,
            "request_latency_seconds": {"samples": latencies, "median": statistics.median(latencies),
                                        "min": min(latencies), "max": max(latencies)},
            "observed": observed}


def run(profile, provider, *, definition, expected_urn, expected_unit=None, samples=3, warmup=1, interval=.5):
    validate_budget(samples, warmup, interval)
    report = {"schema_version": 1, "started_utc": datetime.now(timezone.utc).isoformat(),
              "passed": False, "scientific_acceptance": False,
              "scope": "Authenticated, sequential, paced semantic API diagnostic; not computation/data/throughput-capacity certification",
              "definition": definition, "expected_semantic_urn": expected_urn,
              "expected_unit": expected_unit, "samples_requested": samples}
    try:
        # Read-only metadata/semantic requests; never alter an existing peer catalog.
        with connect(profile, credentials=provider, initialize=False) as connection:
            report["services"] = list(connection.catalog.summary())
            report["reasoner"] = candidate_summary(connection.selected["reasoner"])
            report["measurement"] = measure(connection.client, definition=definition,
                expected_urn=expected_urn, expected_unit=expected_unit,
                samples=samples, warmup=warmup, interval=interval)
            # Refresh also enforces the SDK's selected-service identity pins.
            connection.refresh()
            after = candidate_summary(connection.selected["reasoner"])
            report["reasoner_after"] = after
            if any(after[key] != report["reasoner"][key] for key in
                   ("accepted_generation", "accepted_source_manifest_sha256")):
                raise ProbeValidationError("Accepted semantic revision changed during measurement")
            report["passed"] = True
    except Exception as error:
        # Real credential exchanges may contain sensitive upstream text. Do not
        # persist arbitrary exception messages, raw responses or certificate paths.
        report["passed"] = False
        report["error_type"] = type(error).__name__
        if isinstance(error, ProbeValidationError):
            report["validation_error"] = str(error)
        report.pop("measurement", None)
    report["finished_utc"] = datetime.now(timezone.utc).isoformat()
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", type=Path, required=True, help="Explicit secret-free trusted endpoint profile")
    parser.add_argument("--certificate", type=Path, required=True, help="Private ENGINE certificate; never copied into reports")
    parser.add_argument("--definition", required=True)
    parser.add_argument("--expected-urn", required=True, help="Independent expected numeric semantic identity")
    parser.add_argument("--expected-unit", help="Optional strict unit-metadata gate; no synthesized units")
    parser.add_argument("--samples", type=int, default=3)
    parser.add_argument("--warmup", type=int, default=1)
    parser.add_argument("--interval", type=float, default=.5)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    try:
        validate_budget(args.samples, args.warmup, args.interval)
    except ValueError as error:
        parser.error(str(error))
    if args.report.exists() or not args.report.parent.is_dir():
        parser.error("Report requires an existing parent and a fresh destination")
    profile = DeploymentProfile.load(args.profile)
    report = run(profile, HubCertificateProvider(args.certificate), definition=args.definition,
                 expected_urn=args.expected_urn, expected_unit=args.expected_unit,
                 samples=args.samples, warmup=args.warmup, interval=args.interval)
    with args.report.open("x", encoding="utf-8") as stream:
        json.dump(report, stream, indent=2)
    print("Semantic API diagnostic " + ("passed" if report["passed"] else "failed") + "; scientific_acceptance=false")
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
