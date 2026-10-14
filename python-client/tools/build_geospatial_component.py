"""Build the branch-carried GeoTIFF correction using the existing component layout.

Stages a fresh checkout of the pinned upstream base, verifies/applies the tracked
patch, runs component tests and packages the deployable archive. No user checkout
or production service is modified. JDK 21 and compatible host Maven artifacts are
prerequisites, as for the standalone component build.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import signal
import subprocess
import sys

PATCH_DIRECTORY = Path(__file__).resolve().parents[1] / "component-patches/geospatial"
ARCHIVE = "target/klab.component.geospatial-component-1.0-SNAPSHOT.jar"


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def load_patch(directory=PATCH_DIRECTORY):
    manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("schema_version") != 1:
        raise ValueError("Unsupported component patch manifest")
    name = manifest["patch"]
    if Path(name).name != name:
        raise ValueError("Component patch must be a local filename")
    patch = directory / name
    if digest(patch) != manifest["patch_sha256"]:
        raise ValueError("Tracked GeoTIFF patch differs from its explicit hash")
    return manifest, patch


def git_output(arguments, cwd):
    return subprocess.run(["git", "-c", "core.autocrlf=false", *arguments], cwd=cwd,
        capture_output=True, text=True, check=True, timeout=60).stdout.strip()


def stop_owned(process):
    if os.name == "nt":
        subprocess.run(["taskkill.exe", "/PID", str(process.pid), "/T", "/F"],
            capture_output=True, timeout=20)
    else:
        os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        if os.name != "nt":
            os.killpg(process.pid, signal.SIGKILL)
        process.kill()
        process.wait(timeout=10)


def build(args):
    manifest, patch = load_patch()
    work = args.work_dir.resolve()
    if work.exists() or not work.parent.is_dir():
        raise ValueError("Use a fresh work directory with an existing parent")
    if args.source_repo is not None and not args.source_repo.is_dir():
        raise ValueError("The optional local component source repository must exist")
    if args.independent_reader:
        import importlib.util
        if importlib.util.find_spec("rasterio") is None:
            raise ValueError("Independent reader requires the optional [throughput] dependency")
    work.mkdir()
    source = work / "component"
    evidence = work / "tiff-evidence"
    evidence.mkdir()
    env = dict(os.environ, JAVA_HOME=str(args.java_home.resolve()))
    env["PATH"] = str(args.java_home.resolve() / "bin") + os.pathsep + env.get("PATH", "")
    report = {"started_utc": datetime.now(timezone.utc).isoformat(), "passed": False,
        "manifest": manifest, "steps": [], "work_dir": str(work),
        "client_commit": git_output(["rev-parse", "HEAD"], PATCH_DIRECTORY.parents[2]),
        "client_dirty": bool(git_output(["status", "--porcelain"], PATCH_DIRECTORY.parents[2]))}

    def run(label, command, cwd):
        with (work / (label + ".log")).open("wb") as log:
            process = subprocess.Popen(command, cwd=cwd, env=env, stdout=log,
                stderr=subprocess.STDOUT, start_new_session=os.name != "nt")
            try:
                code = process.wait(timeout=args.timeout)
            except (subprocess.TimeoutExpired, KeyboardInterrupt):
                stop_owned(process)
                report["steps"].append({"name": label, "command": command, "interrupted_or_timed_out": True})
                raise
        report["steps"].append({"name": label, "command": command, "exit_code": code})
        print(label, code, flush=True)
        if code:
            raise RuntimeError(label + " failed; inspect its log")

    try:
        repository = str(args.source_repo.resolve()) if args.source_repo else manifest["repository"]
        run("clone", ["git", "-c", "core.autocrlf=false", "clone", "--no-hardlinks", "--no-checkout",
            repository, str(source)], work)
        run("checkout", ["git", "-c", "core.autocrlf=false", "checkout", "--detach", manifest["base_commit"]], source)
        if git_output(["rev-parse", "HEAD"], source) != manifest["base_commit"]:
            raise ValueError("Component checkout differs from the pinned base")
        run("patch-check", ["git", "-c", "core.autocrlf=false", "apply", "--check", str(patch)], source)
        run("patch", ["git", "-c", "core.autocrlf=false", "apply", str(patch)], source)
        run("index", ["git", "-c", "core.autocrlf=false", "add", "--all"], source)
        tree = git_output(["write-tree"], source)
        if tree != manifest["patched_tree"]:
            raise ValueError("Applied component tree differs from the reviewed correction")
        report["component_tree"] = tree
        report["source_sha256_lf"] = {str(path.relative_to(source)): hashlib.sha256(
            path.read_bytes().replace(b"\r\n", b"\n")).hexdigest() for path in (source / "src").rglob("*.java")}
        maven = (["cmd.exe", "/c", str(source / "mvnw.cmd")] if os.name == "nt"
                 else ["sh", str(source / "mvnw")])
        maven += ["-B", "-ntp"] + (["-o"] if args.offline else [])
        run("test-package", [*maven, "-Dgeotiff.evidence.dir=" + str(evidence), "package"], source)
        if args.independent_reader:
            run("independent-reader", [sys.executable, "-O", str(source / "tools/check_geotiff_reference.py"), str(evidence)], source)
        report["independent_reader"] = "Passed" if args.independent_reader else "Not run"
        run("source-recheck", ["git", "-c", "core.autocrlf=false", "add", "--all"], source)
        if git_output(["write-tree"], source) != manifest["patched_tree"]:
            raise ValueError("Component sources changed during testing/packaging")
        artifact = source / ARCHIVE
        report.update(artifact=str(artifact), artifact_sha256=digest(artifact), passed=True)
    except (Exception, KeyboardInterrupt) as error:
        report["error_type"] = type(error).__name__
        if isinstance(error, (ValueError, RuntimeError)):
            report["error"] = str(error)
    finally:
        report["finished_utc"] = datetime.now(timezone.utc).isoformat()
        (work / "component-build.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({key: report[key] for key in ("passed", "artifact", "artifact_sha256") if key in report}), flush=True)
    return 0 if report["passed"] else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-dir", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--source-repo", type=Path, help="Optional existing local component repository for offline staging")
    parser.add_argument("--offline", action="store_true", help="Use already resolved Maven dependencies")
    parser.add_argument("--independent-reader", action="store_true", help="Also check written fixtures with optional Rasterio/GDAL")
    parser.add_argument("--timeout", type=float, default=900, help="Finite deadline per build step")
    args = parser.parse_args()
    if args.java_home is None or not args.java_home.is_dir() or not math.isfinite(args.timeout) or args.timeout <= 0:
        parser.error("Select an existing JDK 21 JAVA_HOME and a finite positive step timeout")
    try:
        return build(args)
    except (OSError, ValueError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    raise SystemExit(main())
