"""Fail-closed admission for the existing Windows N/S harness, not a deployment tool.

Operator supplies a byte-pinned private resource manifest and isolated worker.
No artifact download, credential provisioning, schedule, or shared-service control.
"""
import argparse
import hashlib
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import platform
import socket
import subprocess
import sys
import uuid

from scientific_scenario import load, require, sha, validate
import run_scientific_acceptance as adapter

PACKAGE = Path(__file__).resolve().parents[1]
SCENARIOS = ("n-nonuniform.json", "s-model-selection.json")
WINDOWS = os.name == "nt"


class PrerequisiteUnavailable(RuntimeError):
    pass


def preflight(path, *, worker, repo):
    if not path.is_file():
        raise PrerequisiteUnavailable("Operator resource manifest unavailable")
    manifest = load(path)
    require(manifest.get("schema_version") == 1, "Unsupported resource manifest")
    authorization = manifest.get("authorization", {})
    require(authorization.get("worker") == worker and authorization.get("isolated") is True
            and isinstance(authorization.get("reference"), str) and authorization["reference"].strip(),
            "Operator must attest the exact isolated worker and authorization reference")
    require(manifest.get("source_sha") == subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip(), "Worker source differs from approved source")
    require(not subprocess.check_output(["git", "status", "--porcelain", "--untracked-files=no"],
        cwd=repo, text=True).strip(), "Tracked worker source must be clean")
    require(manifest.get("python_version") == platform.python_version(), "Python version differs")
    if not WINDOWS:
        raise PrerequisiteUnavailable("Existing owned harness requires an isolated Windows worker")
    files = manifest.get("files")
    require(isinstance(files, dict) and files and len(files) <= 20000, "Bounded resource file pins required")
    for name, digest in files.items():
        require(isinstance(name, str) and isinstance(digest, str) and len(digest) == 64, "Invalid resource pin")
        target = Path(name)
        if not target.is_file():
            raise PrerequisiteUnavailable("Pinned artifact unavailable")
        require(sha(target) == digest, "Pinned artifact byte identity differs")
    java, wheel, components = (Path(manifest[k]) for k in ("java", "wheel", "components"))
    for file in (java, wheel, components):
        require(str(file) in files, "Executable/wheel/component manifest must be byte-pinned")
    version = subprocess.run([str(java), "-version"], capture_output=True, check=True).stderr.decode(errors="replace")
    require(version == manifest.get("java_version") and '"21.' in version, "Approved Java21 version differs")
    archives = load(components)
    require(isinstance(archives, list) and len(archives) == 2, "Existing two-component tuple required")
    for item in archives:
        require(files.get(item["path"]) == item["sha256"], "Components must match manifest pins")
    from run_reliability_acceptance import CORE, SERVICES, classpath
    required_files = {repo / "klab.services.reasoner/src/main/resources/knowledge/odo.owl"}
    require(sha(next(iter(required_files))) == CORE, "Accepted core ontology differs")
    # Pin every compiled and dependency byte actually used by the existing harness.
    for module, *_ in SERVICES.values():
        for entry in classpath(repo, module).split(os.pathsep):
            target = Path(entry)
            if target.is_dir():
                required_files.update(target.rglob("*.class"))
            else:
                required_files.add(target)
    required_files.update((repo / "klab.services.runtime/target/test-classes").rglob("*.class"))
    require(required_files and all(str(p) in files for p in required_files), "Loaded classpath artifacts are not fully pinned")
    for name in SCENARIOS:
        path = PACKAGE / "tests/acceptance/scenarios" / name
        scenario = validate(load(path), PACKAGE)
        adapter.bind_workflow(scenario)
        required_files.add(path)
        required_files.update(PACKAGE / f["path"] for f in scenario["fixture"]["input_files"])
    required_files.update(p for p in (PACKAGE / "tests/fixtures/scientific-reliability").rglob("*") if p.is_file())
    require(all(str(p) in files for p in required_files), "All scenario/model/worldview pins required")
    adapter.installed_wheel(wheel)
    return manifest


def independent_cleanup(state):
    import psutil
    from run_reliability_acceptance import PORTS, GRAPH
    for row in load(state / "owned-processes.json"):
        try:
            process = psutil.Process(row["pid"])
            require(process.create_time() != row["created"] or not process.is_running(), "Owned Java remains running")
        except psutil.NoSuchProcess:
            pass
    for port in (*PORTS.values(), *GRAPH, 18090):
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", port))


def restrict_output(path):
    sid = subprocess.check_output(["whoami", "/user", "/fo", "csv", "/nh"], text=True).strip().split(",")[-1].strip('"')
    subprocess.run(["icacls", str(path), "/grant:r", f"*{sid}:(OI)(CI)F", "*S-1-5-18:(OI)(CI)F",
        "*S-1-5-32-544:(OI)(CI)F", "/inheritance:r"], check=True, capture_output=True)


def execute(args):
    require(args.output.parent.is_dir() and not args.output.exists(), "Fresh run directory required")
    args.output.mkdir()
    receipt = {"schema_version": 1, "run_id": str(uuid.uuid4()), "started": datetime.now(timezone.utc).isoformat(),
        "status": "NOT_RUN", "delivery_state": "NOT_STARTED", "scheduled_L4": "NOT_RUN",
        "scheduled_reason": "Only an independently verified authorized scheduled run can establish scheduled acceptance",
        "scenarios": [], "cleanup": "NOT_RUN"}
    lock = args.output.parent / ".scientific-worker.lock"
    owned_lock = False
    try:
        manifest = preflight(args.resources, worker=args.worker, repo=PACKAGE.parent)
        receipt.update(resource_manifest_sha256=sha(args.resources), source_sha=manifest["source_sha"],
            authorization_reference_sha256=hashlib.sha256(manifest["authorization"]["reference"].encode()).hexdigest())
        if not args.execute:
            receipt.update(status="PASS", delivery_state="IMPLEMENTED_OFFLINE")
            return 0
        restrict_output(args.output)
        try:
            with lock.open("x", encoding="utf-8") as stream:
                stream.write(receipt["run_id"])
            owned_lock = True
        except FileExistsError:
            raise PrerequisiteUnavailable("Isolated worker already reserved; no foreign lock/process cleanup") from None
        for name in SCENARIOS:
            state = args.output / name.removesuffix(".json")
            command = [sys.executable, str(PACKAGE / "tools/run_scientific_acceptance.py"),
                "--manifest", str(PACKAGE / "tests/acceptance/scenarios" / name),
                "--server-repo", str(PACKAGE.parent), "--java", manifest["java"], "--wheel", manifest["wheel"],
                "--components", manifest["components"], "--state-dir", str(state)]
            # Harness owns bounded startup/waits/finally cleanup; don't kill its parent
            # on a second watchdog and orphan Java. Preserve private diagnostics.
            with (args.output / (name + ".private.log")).open("xb") as log:
                completed = subprocess.run(command, cwd=PACKAGE, stdout=log, stderr=log,
                    creationflags=subprocess.CREATE_NO_WINDOW)
            path = state / "scientific-execution.json"
            result = load(path) if path.is_file() else {"status": "FAIL", "cleanup": {"status": "UNKNOWN"}}
            receipt["scenarios"].append({"scenario": name, "status": result["status"],
                "run_id": result.get("run_id"), "receipt_sha256": sha(path) if path.is_file() else None,
                "cleanup": result["cleanup"]["status"], "process_exit": completed.returncode})
            require(completed.returncode == 0 and result["status"] == "PASS"
                    and result["cleanup"]["status"] == "PASS", "Scientific run or owned cleanup failed; no retry")
            independent_cleanup(state)
            receipt["scenarios"][-1]["independent_process_listener_check"] = "PASS"
        receipt.update(status="PASS", delivery_state="LOCALLY_ACCEPTED_BOUNDED", cleanup="PASS")
        return 0
    except PrerequisiteUnavailable as error:
        receipt.update(status="BLOCKED", delivery_state="BLOCKED_EXTERNAL", reason=str(error))
        return 2
    except (ValueError, OSError, RuntimeError, subprocess.SubprocessError, KeyError, TypeError) as error:
        receipt.update(status="FAIL", delivery_state="FAILED", error_type=type(error).__name__)
        if receipt["scenarios"]:
            receipt["cleanup"] = receipt["scenarios"][-1]["cleanup"]
        return 1
    finally:
        if owned_lock:
            require(lock.read_text() == receipt["run_id"], "Worker lock ownership changed; no foreign removal")
            lock.unlink()
        receipt["ended"] = datetime.now(timezone.utc).isoformat()
        (args.output / "worker-receipt.json").write_text(json.dumps(receipt, sort_keys=True, indent=2), encoding="utf-8")
        print(json.dumps({k: receipt[k] for k in ("run_id", "status", "delivery_state", "scheduled_L4", "cleanup")}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", type=Path, required=True)
    parser.add_argument("--worker", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--execute", action="store_true")
    raise SystemExit(execute(parser.parse_args()))
