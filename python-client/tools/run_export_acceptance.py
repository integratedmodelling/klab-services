"""Development-only, owned TEST stack with an explicitly supplied genuine component archive."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

from run_local_acceptance import run_command


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-repo", type=Path, required=True, help="Explicit compiled server checkout; never silently change the integration base")
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--worldview", type=Path, required=True)
    parser.add_argument("--component", type=Path, required=True, help="Genuine published or locally built geospatial component archive")
    parser.add_argument("--component-sha256", help="Explicit expected hash for a locally built component")
    parser.add_argument("--component-source", type=Path, help="Source checkout for the explicitly pinned local component")
    parser.add_argument("--state-dir", type=Path, required=True)
    parser.add_argument("--throughput", action="store_true", help="Also verify numerical GeoTIFF fresh-process throughput")
    parser.add_argument("--functional-suite", action="append",
        choices=("compute-and-analyze", "resume-saved-work", "typed-compute", "geotiff-export"),
        help="Additional named user workflows against the owned exporter-enabled stack")
    parser.add_argument("--samples", type=int, default=3)
    parser.add_argument("--duration", type=float, default=15)
    parser.add_argument("--warmup", type=int, default=1)
    parser.add_argument("--concurrency", type=int, default=1)
    parser.add_argument("--throughput-mode", choices=("full-roundtrip", "repeated-export"), default="full-roundtrip")
    args = parser.parse_args()
    if args.throughput:
        import importlib.util
        if importlib.util.find_spec("rasterio") is None:
            parser.error("Numerical throughput requires the optional [throughput] reader dependency")
        import math
        if (args.samples < 1 or args.warmup < 0 or args.concurrency < 1 or not math.isfinite(args.duration)
                or args.duration < 0 or args.throughput_mode == "full-roundtrip" and args.concurrency != 1):
            parser.error("Invalid throughput settings; full recomputation uses the serial TEST field counter")
    if (os.name != "nt" or not args.java.is_file() or not args.component.is_file()
            or not args.state_dir.parent.is_dir() or args.state_dir.exists()):
        parser.error("Requires Windows, existing Java/component/state parent, and a fresh state destination")
    directory = Path(__file__).resolve().parents[1]
    server = args.server_repo.resolve()
    state = args.state_dir.resolve()
    digest = hashlib.sha256(args.component.read_bytes()).hexdigest()
    expected = "e7d470f69943e93ce48e9669bad6a41b618dfbe0e7bc748076e010583daef644"
    if bool(args.component_sha256) != bool(args.component_source):
        parser.error("Local components require both --component-sha256 and --component-source")
    if digest != (args.component_sha256 or expected):
        parser.error("Component archive SHA-256 differs from the explicit pin")
    state.mkdir()
    env = dict(os.environ, KLAB_FIXTURE_STATE_DIR=str(state), JAVA_HOME=str(args.java.resolve().parent.parent))
    env["PATH"] = str(args.java.resolve().parent) + os.pathsep + env.get("PATH", "")
    report = {"started_utc": datetime.now(timezone.utc).isoformat(), "passed": False, "steps": [],
        "component_version": "local-source-build" if args.component_source else "1.0-20260926.083046-222", "component_sha256": digest}
    if args.component_source:
        source = args.component_source.resolve()
        report["component_source_commit"] = subprocess.run(["git", "rev-parse", "HEAD"], cwd=source,
            capture_output=True, text=True, check=True).stdout.strip()
        report["component_source_dirty"] = bool(subprocess.run(["git", "status", "--porcelain"], cwd=source,
            capture_output=True, text=True, check=True).stdout)
        report["component_source_sha256_lf"] = {str(path.relative_to(source)): hashlib.sha256(
            path.read_bytes().replace(b"\r\n", b"\n")).hexdigest() for path in (source / "src").rglob("*.java")}
    for label, root in (("client", directory.parent), ("server", server)):
        report[label + "_commit"] = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True, check=True).stdout.strip()
        report[label + "_dirty"] = bool(subprocess.run(["git", "status", "--porcelain"], cwd=root, capture_output=True, text=True, check=True).stdout)
    report["client_production_sha256_lf"] = {str(path.relative_to(directory)): hashlib.sha256(
        path.read_bytes().replace(b"\r\n", b"\n")).hexdigest() for path in (directory / "src/klab_client").rglob("*.py")}
    hub, started = None, False
    def run(label, command, cwd):
        with (state / (label + ".log")).open("wb") as log:
            code = run_command(command, cwd=cwd, env=env, log=log, timeout=900)
        report["steps"].append({"name": label, "exit_code": code})
        print(label, code, flush=True)
        if code:
            raise RuntimeError(label + " failed; inspect its log")
    try:
        # Current scenario assets with explicitly selected compiled Runtime controls.
        run("prepare", [sys.executable, "tools/fixture_hub.py", "prepare", "--state-dir", str(state),
            "--worldview", str(args.worldview.resolve()), "--server-repo", str(server)], directory)
        for service in ("runtime", "resources"):
            plugins = state / "home/.klab/services" / service / "components/plugins"
            plugins.mkdir(parents=True)
            shutil.copyfile(args.component, plugins / "klab.component.geospatial.jar")
        with (state / "authority.log").open("wb") as log:
            hub = subprocess.Popen([sys.executable, "tools/fixture_hub.py", "serve", "--state-dir", str(state)], cwd=directory, env=env, stdout=log, stderr=subprocess.STDOUT)
        import httpx
        with httpx.Client(trust_env=False, timeout=1) as http:
            for _ in range(30):
                if hub.poll() is not None:
                    raise RuntimeError("TEST authority exited")
                try:
                    http.get("http://127.0.0.1:18090/health")
                    break
                except httpx.TransportError:
                    time.sleep(.1)
            else:
                raise RuntimeError("TEST authority did not start")
        started = True
        run("start", [sys.executable, "tools/local_stack.py", "start", "--java", str(args.java.resolve()), "--state-dir", str(state)], server / "python-client")
        run("export", [sys.executable, "tools/run_functional_acceptance.py", "--suite", "geojson-export",
            "--state-dir", str(state), "--report", str(state / "exports.xml")], directory)
        if args.functional_suite:
            suites = [item for name in dict.fromkeys(args.functional_suite) for item in ("--suite", name)]
            run("functional", [sys.executable, "tools/run_functional_acceptance.py", "--state-dir", str(state),
                "--report", str(state / "functional.xml"), *suites], directory)
        if args.throughput:
            run("geotiff-throughput", [sys.executable, "tools/run_workflow_throughput.py", "--workflow", "export-deliverables", "run",
                "--fixture-config", str(state / "scientist.json"), "--server-state-dir", str(state),
                "--work-dir", str(state), "--report", str(state / "geotiff-throughput.json"),
                "--samples", str(args.samples), "--duration", str(args.duration), "--warmup", str(args.warmup),
                "--concurrency", str(args.concurrency), "--mode", args.throughput_mode], directory)
        report["passed"] = True
    except Exception as error:
        report["error"] = type(error).__name__ + ": " + str(error)
    except KeyboardInterrupt:
        report["error"] = "Interrupted; owned process cleanup requested"
    finally:
        if started:
            try:
                code = subprocess.run([sys.executable, "tools/local_stack.py", "stop", "--state-dir", str(state)], cwd=server / "python-client", timeout=90).returncode
                report["cleanup_exit_code"] = code
                report["passed"] &= code == 0
            except subprocess.TimeoutExpired:
                report["cleanup_exit_code"] = None
                report["passed"] = False
        if hub is not None and hub.poll() is None:
            hub.terminate()
            try:
                hub.wait(timeout=10)
            except subprocess.TimeoutExpired:
                hub.kill()
                hub.wait(timeout=10)
        report["finished_utc"] = datetime.now(timezone.utc).isoformat()
        (state / "run-report.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report), flush=True)
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
