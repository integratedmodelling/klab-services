"""Reproducible TEST deployment -> scientific/lifecycle tests -> throughput -> cleanup."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
from datetime import datetime, timezone


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--worldview", type=Path, required=True)
    parser.add_argument("--state-dir", type=Path, required=True)
    parser.add_argument("--duration", type=float, default=15)
    parser.add_argument("--samples", type=int, default=8)
    parser.add_argument("--build", action="store_true")
    args = parser.parse_args()
    if os.name != "nt" or not args.java.is_file() or not args.state_dir.parent.is_dir():
        parser.error("Requires Windows, existing Java executable and existing state parent")
    if args.duration <= 0 or args.samples < 1:
        parser.error("Positive duration and sample count required")
    state = args.state_dir.resolve()
    state.mkdir(exist_ok=True)
    if (state / "fixture-authority.json").exists():
        parser.error("Use a new state directory for a fresh reproducible deployment")
    directory = Path(__file__).resolve().parents[1]
    root = directory.parent
    env = os.environ.copy()
    env["JAVA_HOME"] = str(args.java.resolve().parent.parent)
    env["PATH"] = str(args.java.resolve().parent) + os.pathsep + env.get("PATH", "")
    env["KLAB_FIXTURE_STATE_DIR"] = str(state)
    hub = None
    started = False
    report = {"started_utc": datetime.now(timezone.utc).isoformat(), "passed": False, "steps": []}
    def run(label, command, cwd=directory):
        with (state / f"{label}.log").open("wb") as log:
            result = subprocess.run(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT)
        report["steps"].append({"step": label, "exit_code": result.returncode})
        print(f"{label}: exit {result.returncode}; log {state / (label + '.log')}", flush=True)
        if result.returncode:
            raise RuntimeError(f"{label} failed; inspect its log")
    try:
        modules = "klab.services.resources.server,klab.services.reasoner.server,klab.services.resolver.server,klab.services.runtime.server,support/klab.support.graphdb"
        mvn = ["cmd.exe", "/c", str(root / "mvnw.cmd"), "-B", "-ntp"]
        if args.build:
            run("build-servers", mvn + ["-pl", modules, "-am", "-DskipTests", "compile"], root)
            run("build-webui", mvn + ["-pl", "klab.core.services", "-Pwebui", "-DskipTests", "process-resources"], root)
            run("build-classpaths", mvn + ["-pl", modules, "dependency:build-classpath", "-DincludeScope=runtime",
                                           "-Dmdep.outputFile=target/local-stack-classpath.txt"], root)
        run("prepare", [sys.executable, "tools/fixture_hub.py", "prepare", "--state-dir", str(state), "--worldview", str(args.worldview.resolve())])
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
        run("start", [sys.executable, "tools/local_stack.py", "start", "--java", str(args.java.resolve()), "--state-dir", str(state)])
        started = True
        run("acceptance", [sys.executable, "-m", "pytest", "-o", "addopts=", "-o", "junit_family=xunit1",
                           "-m", "live", "-s", "tests/test_local_workflow.py", "--junitxml=" + str(state / "acceptance.xml")])
        for concurrency in (1, 2, 4):
            run(f"full-c{concurrency}", [sys.executable, "tools/workflow_benchmark.py", "--fixture-config", str(state / "scientist.json"),
                "--samples", str(args.samples), "--concurrency", str(concurrency), "--duration", str(args.duration),
                "--warmup", "1", "--server-state-dir", str(state), "--report", str(state / f"full-c{concurrency}.json")])
        run("repeated-read", [sys.executable, "tools/workflow_benchmark.py", "--fixture-config", str(state / "scientist.json"),
            "--mode", "repeated-read", "--samples", str(args.samples), "--concurrency", "4", "--duration", str(args.duration),
            "--warmup", "1", "--server-state-dir", str(state), "--report", str(state / "repeated-read.json")])
        report["passed"] = True
    except Exception as error:
        report["error"] = type(error).__name__ + ": " + str(error)
    finally:
        if started:
            cleanup = subprocess.run([sys.executable, "tools/local_stack.py", "stop", "--state-dir", str(state)], cwd=directory)
            report["cleanup_exit_code"] = cleanup.returncode
            if cleanup.returncode:
                report["passed"] = False
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
