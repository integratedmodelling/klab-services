"""Reproducible TEST deployment -> scientific/lifecycle tests -> throughput -> cleanup."""
import argparse
import json
import math
import os
from pathlib import Path
import subprocess
import sys
from datetime import datetime, timezone


def run_command(command, *, cwd, env, log, timeout):
    """Bound each child step and terminate its owned process tree on timeout."""
    process = subprocess.Popen(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT)
    try:
        return process.wait(timeout=timeout)
    except (subprocess.TimeoutExpired, KeyboardInterrupt):
        subprocess.run(["taskkill.exe", "/PID", str(process.pid), "/T", "/F"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)
        process.wait(timeout=10)
        raise


def checkout_identity(root):
    """Identify the selected source tree without changing it or trusting branch labels."""
    def git(*arguments):
        return subprocess.run(["git", *arguments], cwd=root, capture_output=True,
                              text=True, check=True, timeout=10).stdout.strip()
    revision = git("rev-parse", "HEAD")
    dirty = bool(git("status", "--porcelain"))
    merge = subprocess.run(["git", "rev-parse", "-q", "--verify", "MERGE_HEAD"], cwd=root,
                           capture_output=True, text=True, timeout=10)
    return {"directory": str(root), "commit": revision, "dirty": dirty,
            "merge_parent": merge.stdout.strip() if merge.returncode == 0 else None}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--server-repo", type=Path,
        help="Explicit Java server checkout; defaults to this Python checkout's repository, not develop")
    parser.add_argument("--worldview", type=Path, required=True)
    parser.add_argument("--semantic-catalog", type=Path, help="Verified pinned semantic dependency catalog; never a remote fallback")
    parser.add_argument("--state-dir", type=Path, required=True)
    parser.add_argument("--duration", type=float, default=15)
    parser.add_argument("--samples", type=int, default=8)
    parser.add_argument("--build", action="store_true")
    parser.add_argument("--step-timeout", type=float, default=900)
    parser.add_argument("--saved-handles", action="store_true", help="Also verify fresh-process saved handle restoration")
    parser.add_argument("--typed-observe", action="store_true", help="Also verify the typed observation example")
    parser.add_argument("--readiness-only", action="store_true",
        help="Start only graphdb/Resources/Reasoner and test semantic publication; no Runtime controls or scientific/restart acceptance")
    args = parser.parse_args()
    if os.name != "nt" or not args.java.is_file() or not args.state_dir.parent.is_dir():
        parser.error("Requires Windows, existing Java executable and existing state parent")
    if not math.isfinite(args.duration) or args.duration <= 0 or args.samples < 1 or not math.isfinite(args.step_timeout) or args.step_timeout <= 0:
        parser.error("Positive duration and sample count required")
    if args.readiness_only and (args.saved_handles or args.typed_observe):
        parser.error("Readiness-only does not run saved-handle or typed-observe acceptance")
    directory = Path(__file__).resolve().parents[1]
    root = directory.parent
    server = args.server_repo.resolve() if args.server_repo else root
    if not (server / "pom.xml").is_file() or not (server / "mvnw.cmd").is_file():
        parser.error("--server-repo must identify a Java server checkout with pom.xml and mvnw.cmd")
    try:
        identities = {"client": checkout_identity(root), "server": checkout_identity(server)}
    except (OSError, subprocess.SubprocessError) as error:
        parser.error("Cannot identify the selected Git checkouts: " + type(error).__name__)
    state = args.state_dir.resolve()
    state.mkdir(exist_ok=True)
    if (state / "fixture-authority.json").exists():
        parser.error("Use a new state directory for a fresh reproducible deployment")
    env = os.environ.copy()
    env["JAVA_HOME"] = str(args.java.resolve().parent.parent)
    env["PATH"] = str(args.java.resolve().parent) + os.pathsep + env.get("PATH", "")
    env["KLAB_FIXTURE_STATE_DIR"] = str(state)
    hub = None
    started = False
    report = {"started_utc": datetime.now(timezone.utc).isoformat(), "passed": False, "steps": [],
              "mode": "semantic-readiness-only" if args.readiness_only else "scientific-lifecycle-restart",
              "scientific_acceptance": False, "checkouts": identities}
    if args.readiness_only:
        report["not_run"] = ["scientific roundtrips", "Runtime restart", "cold ACL checks", "throughput"]
    print(f"Selected Java server: {server} @ {identities['server']['commit']} (dirty={identities['server']['dirty']})", flush=True)
    def run(label, command, cwd=directory):
        with (state / f"{label}.log").open("wb") as log:
            try:
                code = run_command(command, cwd=cwd, env=env, log=log, timeout=args.step_timeout)
            except subprocess.TimeoutExpired:
                report["steps"].append({"step": label, "timed_out": True})
                raise RuntimeError(f"{label} exceeded step timeout") from None
        report["steps"].append({"step": label, "exit_code": code})
        print(f"{label}: exit {code}; log {state / (label + '.log')}", flush=True)
        if code:
            raise RuntimeError(f"{label} failed; inspect its log")
    try:
        modules = ("klab.services.resources.server,klab.services.reasoner.server,support/klab.support.graphdb"
                   if args.readiness_only else
                   "klab.services.resources.server,klab.services.reasoner.server,klab.services.resolver.server,klab.services.runtime.server,support/klab.support.graphdb")
        mvn = ["cmd.exe", "/c", str(server / "mvnw.cmd"), "-B", "-ntp"]
        if args.build:
            run("build-servers", mvn + ["-pl", modules, "-am", "-DskipTests", "test-compile"], server)
            run("build-webui", mvn + ["-pl", "klab.core.services", "-Pwebui", "-DskipTests", "process-resources"], server)
            run("build-classpaths", mvn + ["-pl", modules, "dependency:build-classpath", "-DincludeScope=runtime",
                                           "-Dmdep.outputFile=target/local-stack-classpath.txt"], server)
        run("prepare", [sys.executable, "tools/fixture_hub.py", "prepare", "--state-dir", str(state), "--worldview", str(args.worldview.resolve()),
            "--server-repo", str(server),
            *(["--semantic-catalog", str(args.semantic_catalog.resolve())] if args.semantic_catalog else []),
            *(["--readiness-only"] if args.readiness_only else [])])
        with (state / "authority.log").open("wb") as log:
            hub = subprocess.Popen([sys.executable, "tools/fixture_hub.py", "serve", "--state-dir", str(state)],
                                   cwd=directory, env=env, stdout=log, stderr=subprocess.STDOUT)
        import httpx
        import time
        with httpx.Client(trust_env=False, timeout=1) as http:
            for attempt in range(30):
                if hub.poll() is not None:
                    raise RuntimeError("TEST authority exited during startup")
                try:
                    http.get("http://127.0.0.1:18090/health")
                    break  # Listener active; authority intentionally has no public login GET route.
                except httpx.TransportError:
                    time.sleep(.1)
            else:
                raise RuntimeError("TEST authority listener did not start")
        started = True
        start = [sys.executable, "tools/local_stack.py", "start", "--repo", str(server), "--java", str(args.java.resolve()), "--state-dir", str(state)]
        if args.readiness_only:
            for name in ("graphdb", "resources", "reasoner"):
                run("start-" + name, start + ["--only", name])
            run("semantic-readiness", [sys.executable, "tools/run_functional_acceptance.py", "--suite", "semantic-readiness",
                "--state-dir", str(state), "--report", str(state / "semantic-readiness.xml")])
            report["passed"] = True
        else:
            run("start", start)
            run("develop-compatibility", [sys.executable, "tools/run_functional_acceptance.py", "--suite", "develop-compatibility",
                "--state-dir", str(state), "--report", str(state / "develop-compatibility.xml")])
            run("acceptance", [sys.executable, "tools/run_functional_acceptance.py", "--suite", "compute-and-analyze",
                "--state-dir", str(state), "--report", str(state / "acceptance.xml")])
            if args.saved_handles:
                run("saved-handles", [sys.executable, "tools/run_functional_acceptance.py", "--suite", "resume-saved-work",
                    "--state-dir", str(state), "--report", str(state / "saved-handles.xml")])
            if args.typed_observe:
                run("typed-observe", [sys.executable, "tools/run_functional_acceptance.py", "--suite", "typed-compute",
                    "--state-dir", str(state), "--report", str(state / "typed-observe.xml")])
            for concurrency in (1, 2, 4):
                run(f"full-c{concurrency}", [sys.executable, "tools/run_workflow_throughput.py", "--workflow", "compute-and-analyze", "--fixture-config", str(state / "scientist.json"),
                    "--samples", str(args.samples), "--concurrency", str(concurrency), "--duration", str(args.duration),
                    "--warmup", "1", "--server-state-dir", str(state), "--report", str(state / f"full-c{concurrency}.json")])
            run("repeated-read", [sys.executable, "tools/run_workflow_throughput.py", "--workflow", "compute-and-analyze", "--fixture-config", str(state / "scientist.json"),
                "--mode", "repeated-read", "--samples", str(args.samples), "--concurrency", "4", "--duration", str(args.duration),
                "--warmup", "1", "--server-state-dir", str(state), "--report", str(state / "repeated-read.json")])
            report["passed"] = True
            report["scientific_acceptance"] = True
    except Exception as error:
        report["error"] = type(error).__name__ + ": " + str(error)
    except KeyboardInterrupt:
        report["error"] = "Interrupted; owned process cleanup requested"
    finally:
        if started:
            report["cleanup_exit_code"] = 0
            names = ("reasoner", "resources", "graphdb") if args.readiness_only else (None,)
            for name in names:
                command = [sys.executable, "tools/local_stack.py", "stop", "--repo", str(server), "--state-dir", str(state)]
                if name is not None:
                    command += ["--only", name]
                try:
                    cleanup = subprocess.run(command, cwd=directory, timeout=90)
                    if cleanup.returncode and report["cleanup_exit_code"] == 0:
                        report["cleanup_exit_code"] = cleanup.returncode
                except (OSError, subprocess.TimeoutExpired) as error:
                    report["cleanup_exit_code"] = None
                    report.setdefault("cleanup_errors", []).append(type(error).__name__)
            if report["cleanup_exit_code"] != 0:
                report["passed"] = False
                report["scientific_acceptance"] = False
        if hub is not None and hub.poll() is None:
            hub.terminate()
            try:
                hub.wait(timeout=10)
            except subprocess.TimeoutExpired:
                hub.kill()
                hub.wait()
        report["finished_utc"] = datetime.now(timezone.utc).isoformat()
        (state / "run-report.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report), flush=True)
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
