"""Validate one scenario, delegate to the existing owned harness, retain a receipt.

No deployment abstraction: Windows TEST stack, same ports, pins and cleanup. A
workflow must supply explicit expectation outcomes; harness success alone is not L4.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys
import uuid
import zipfile

from scientific_scenario import AXES, load, outcome, record, require, sha, validate

PACKAGE = Path(__file__).resolve().parents[1]


def installed_wheel(wheel):
    import klab_client
    installed = Path(klab_client.__file__).parent
    require(not installed.resolve().is_relative_to(PACKAGE), "Noneditable installed wheel required")
    with zipfile.ZipFile(wheel) as archive:
        members = [i for i in archive.infolist() if i.filename.startswith("klab_client/") and i.filename.endswith(".py")]
        require(members and len(members) <= 256 and sum(i.file_size for i in members) <= 8 * 1024 * 1024,
                "Wheel source byte budget exceeded")
        require(len({i.filename for i in members}) == len(members), "Duplicate wheel source")
        expected = {}
        for item in members:
            relative = Path(item.filename).relative_to("klab_client")
            require(".." not in relative.parts and not relative.is_absolute(), "Unsafe wheel member")
            digest = hashlib.sha256(archive.read(item)).hexdigest()
            require(sha(installed / relative) == digest and sha(PACKAGE / "src/klab_client" / relative) == digest,
                    "Wheel/installed/source mismatch")
            expected[relative.as_posix()] = digest
        require(set(expected) == {p.relative_to(installed).as_posix() for p in installed.rglob("*.py")},
                "Unexpected installed Python sources")
    return sha(wheel)


def now():
    return datetime.now(timezone.utc).isoformat()


def execute(args):
    global PACKAGE
    if getattr(args, "workflow_package", None) is not None:
        selected = args.workflow_package.resolve()
        require(selected == args.server_repo.resolve() / "python-client",
                "Workflow package must belong to the explicit server checkout")
        PACKAGE = selected
    manifest = validate(load(args.manifest), PACKAGE)
    require(manifest["claim_level"] == "L4", "L5 execution is excluded from this milestone")
    wheel_hash = installed_wheel(args.wheel)
    # Use the existing harness directly so its finally block owns every cleanup.
    import run_reliability_acceptance as harness
    harness.PACKAGE = PACKAGE
    args.scenario = manifest["execution"]["operation"]
    required = {"nonuniform-export": (1, 2), "request-plan": (4, 6)}[args.scenario]
    require(manifest["execution"]["max_contexts"] >= required[0]
            and manifest["execution"]["max_jobs"] >= required[1], "Budget below existing workflow requirements")
    args.resources_overlay = None
    args.allow_owned_authoring = False
    started = now()
    code = harness.run(args)
    ended = now()
    state = args.state_dir.resolve()
    raw = load(state / "result.json")
    config = load(state / "scientist.json") if (state / "scientist.json").is_file() else {}
    science = raw.get("science", {})
    actual = science.get("expectations", {})
    checks = {c["expectation_id"]: actual.get(c["expectation_id"], outcome("NOT_RUN", "Workflow did not record this check"))
              for c in manifest["checks"]}
    cleaned = all(raw.get(k) is True for k in ("owned_processes_closed", "existing_java_unchanged", "listeners_closed"))
    axes = science.get("axes", {axis: outcome("NOT_RUN", "No independently evaluated workflow axis") for axis in AXES})
    if code and "axes" not in science:
        axes["python"] = outcome("FAIL", "Harness/functional execution failed; owner unclassified pending private diagnostic")
    elapsed = (datetime.fromisoformat(ended) - datetime.fromisoformat(started)).total_seconds()
    if elapsed > manifest["execution"]["max_seconds"]:
        axes["python"] = outcome("FAIL", "Execution exceeded declared elapsed budget; harness cleanup retained")
    services = {}
    for name in harness.PORTS:
        path = state / f"home/.klab/services/{name}/{name}.yaml"
        if path.is_file():
            services[name] = {"id": load(path).get("serviceId"), "url": config.get("urls", {}).get(name)}
    # Sanitized projection: never copy arbitrary stdout, wire payloads or credentials.
    summary = {"schema_version": 1, "scenario_id": manifest["id"], "harness_passed": raw.get("passed"),
        "elapsed_seconds": elapsed,
        "error_type": raw.get("error_type"), "manifest_sha256": sha(args.manifest),
        "fixture_sha256": raw.get("fixture_sha256", {}), "test_source_sha256": raw.get("test_source_sha256"),
        "test_class_sha256": raw.get("test_class_sha256"), "compiled_sha256": raw.get("compiled_sha256", {}),
        "installed_sha256": raw.get("installed_sha256", {}), "functional_sha256": raw.get("functional_sha256", {}),
        "components": [{"sha256": c["sha256"]} for c in raw.get("components", [])],
        "checks": checks, "axes": axes, "cleanup": cleaned,
        "private_diagnostic_hashes": {p.name: sha(p) for p in state.glob("*")
            if p.is_file() and (p.suffix == ".log" or p.name == "failure-private.txt")}}
    encoded = json.dumps(summary, sort_keys=True, indent=2)
    require(all(value not in encoded for key, value in config.items()
                if "token" in key and isinstance(value, str) and value), "Credential leaked into summary")
    summary_path = state / "scientific-sanitized.json"
    summary_path.write_text(encoded, encoding="utf-8")
    identities = {"source_sha": raw["server_head"], "build_hashes": {"manifest_sha256": sha(summary_path)},
        "wheel_sha256": wheel_hash, "services": services or {"state": "NOT_AVAILABLE"},
        "auth_principal": config.get("username", "NOT_AVAILABLE"), "java_version": raw.get("java_version", "NOT_AVAILABLE"),
        "components": summary["components"] or ["NOT_AVAILABLE"]}
    receipt = record(manifest, run_id=str(uuid.uuid4()), identities=identities,
        command=[sys.executable, str(Path(__file__).resolve()), *sys.argv[1:]], started=started, ended=ended,
        checks=checks, axes=axes, evidence=[{"path": summary_path.name, "sha256": sha(summary_path), "sanitized": True}],
        cleanup=outcome("PASS" if cleaned else "FAIL", "Captured owned processes/listeners and unrelated Java preservation"))
    (state / "scientific-execution.json").write_text(json.dumps(receipt, sort_keys=True, indent=2), encoding="utf-8")
    print(json.dumps({"scenario": manifest["id"], "status": receipt["status"], "axes": axes}))
    return int(receipt["status"] != "PASS")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("manifest", "wheel", "server-repo", "java", "components", "state-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--workflow-package", type=Path,
                        help="Explicit python-client directory in server checkout for independent accepted-base scenario branches")
    return execute(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
