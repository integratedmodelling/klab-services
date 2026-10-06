"""Development-only Windows launcher/probe for this checkout's compiled services.

Requires explicit Java/state paths and Maven-generated classpaths. This is not
the product CLI, a scientist login flow, or a scientific throughput benchmark.
"""
from __future__ import annotations

import argparse
import json
import math
import os
from pathlib import Path
import socket
import subprocess
import time

import httpx

SERVICES = {
    "graphdb": ("support/klab.support.graphdb", "org.integratedmodelling.klab.support.graphdb.KlabNeo4jStarter", 8382, "/health"),
    "resources": ("klab.services.resources.server", "org.integratedmodelling.resources.server.ResourcesServer", 8092, "/resources/public/capabilities"),
    "reasoner": ("klab.services.reasoner.server", "org.integratedmodelling.klab.services.reasoner.ReasonerServer", 8091, "/reasoner/public/capabilities"),
    "resolver": ("klab.services.resolver.server", "org.integratedmodelling.klab.services.resolver.server.ResolverServer", 8093, "/resolver/public/capabilities"),
    "runtime": ("klab.services.runtime.server", "org.integratedmodelling.klab.services.runtime.server.RuntimeServer", 8094, "/runtime/public/capabilities"),
}


def classpath(repo, module):
    values = [str(repo / module / "target/classes")]
    file = repo / module / "target/local-stack-classpath.txt"
    for text in file.read_text().strip().split(os.pathsep):
        path = Path(text)
        artifact = path.parent.parent.name
        local = repo / artifact / "target/classes"
        # Prefer the compiled source revision over installed reactor snapshot jars.
        values.append(str(local) if artifact.startswith("klab.") and local.is_dir() else text)
    return os.pathsep.join(dict.fromkeys(values))


def launch(repo, java, state, name, auth_package):
    module, main, port, _ = SERVICES[name]
    with socket.socket() as sock:
        if sock.connect_ex(("127.0.0.1", port)) == 0:
            raise RuntimeError(f"Port {port} is occupied; refusing to replace an existing service")
    home = state / "home"
    home.mkdir(parents=True, exist_ok=True)
    args = ["-Xmx768m", f"-Duser.home={home.as_posix()}", "-Dserver.address=127.0.0.1",
            "-Dspring.main.banner-mode=off", "-cp", classpath(repo, module).replace("\\", "/"), main]
    if name == "runtime" and (state / "runtime-fixture.json").is_file():
        fixture = json.loads((state / "runtime-fixture.json").read_text())
        classes = Path(fixture["classes"])
        expected = repo / "klab.services.runtime/target/test-classes"
        if classes.resolve() != expected.resolve():
            raise RuntimeError("Test fixture classpath must belong to this checkout's Runtime tests")
        args[args.index("-cp") + 1] = str(classes).replace("\\", "/") + os.pathsep + args[args.index("-cp") + 1]
        args.insert(0, "-Dklab.test.controls=" + Path(fixture["controls"]).as_posix())
    if name != "graphdb":
        args.extend(["-dataDir", (home / ".klab").as_posix(), "-port", str(port), "-contextPath", "/" + name])
        certificate = home / ".klab/services" / name / "service.cert"
        if certificate.is_file():
            # The explicit certificate branch validates/loads properties before use.
            args.extend(["-cert", certificate.as_posix()])
    argfile = state / f"{name}.args"
    argfile.write_text("\n".join('"' + a.replace('"', '\\"') + '"' for a in args), encoding="utf-8")
    env = os.environ.copy()
    for key in list(env):
        if key.startswith("KLAB_") or key in {"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"}:
            env.pop(key)
    if auth_package is not None and name != "graphdb":
        # Only an explicitly supplied existing engine response; never synthesize identity.
        env["KLAB_LOCAL_AUTHENTICATION_RESPONSE"] = auth_package
    with (state / f"{name}.console.log").open("wb") as output:
        process = subprocess.Popen([str(java), "@" + str(argfile)], cwd=repo / module,
                                   env=env, stdout=output, stderr=subprocess.STDOUT)
    try:
        (state / f"{name}.pid").write_text(str(process.pid))
    except OSError:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)
        raise
    print(f"Launched {name}: PID {process.pid}, port {port}, log {state / (name + '.console.log')}", flush=True)
    return process


def wait_ready(name, process, seconds):
    _, _, port, route = SERVICES[name]
    deadline = time.monotonic() + seconds
    with httpx.Client(trust_env=False, timeout=2) as http:
        while time.monotonic() < deadline:
            if process.poll() is not None:
                return False
            try:
                if http.get(f"http://127.0.0.1:{port}{route}").status_code == 200:
                    print(f"{name}: public endpoint HTTP 200 (not proof of scientific readiness)", flush=True)
                    return True
            except httpx.TransportError:
                pass
            time.sleep(.5)
    return False


def inspect_process(pid):
    command = (f"$p = Get-CimInstance Win32_Process -Filter 'ProcessId={pid}' -ErrorAction Stop; "
               "if ($p) { @{exists=$true; command_line=$p.CommandLine} | ConvertTo-Json -Compress } "
               "else { @{exists=$false} | ConvertTo-Json -Compress }")
    result = subprocess.run(["powershell.exe", "-NoProfile", "-Command", command],
                            capture_output=True, text=True, timeout=10)
    if result.returncode:
        raise RuntimeError("Process inspection failed")
    payload = json.loads(result.stdout)
    if type(payload.get("exists")) is not bool:
        raise RuntimeError("Process inspection returned an invalid status")
    return payload


def stop(state, names):
    failures = 0
    for name in reversed(list(names)):
        file = state / f"{name}.pid"
        if not file.exists():
            continue
        try:
            pid = int(file.read_text())
            check = inspect_process(pid)
            if not check["exists"]:
                continue
            if str(state / f"{name}.args") not in (check.get("command_line") or ""):
                raise RuntimeError("Live process identity differs; refusing termination")
            result = subprocess.run(["taskkill.exe", "/PID", str(pid), "/T", "/F"],
                                    capture_output=True, text=True, timeout=10)
            if result.returncode or inspect_process(pid)["exists"]:
                raise RuntimeError("Owned process termination was not confirmed")
            print(f"Stop {name}: confirmed exited")
        except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
            failures += 1
            print(f"Stop {name}: failed ({type(error).__name__}); inspect state before retry")
    return failures


def probe(names):
    failures = 0
    with httpx.Client(trust_env=False, timeout=5) as http:
        for name in names:
            _, _, port, route = SERVICES[name]
            paths = [route] if name == "graphdb" else [route, f"/{name}/public/status", f"/{name}/public/health"]
            for path in paths:
                result = {"service": name, "path": path}
                try:
                    response = http.get(f"http://127.0.0.1:{port}{path}")
                    result["http_status"] = response.status_code
                    failures += response.status_code != 200
                    if response.status_code == 200:
                        payload = response.json()
                        for key in ("status", "serviceId", "serviceName", "available", "operational",
                                    "worldviewId", "worldviewProvider", "adoptedWorldview", "workspaceNames"):
                            if key in payload:
                                result[key] = payload[key]
                except (httpx.TransportError, ValueError) as error:
                    result["error_type"] = type(error).__name__
                    failures += 1
                print(json.dumps(result), flush=True)
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["start", "probe", "stop"])
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--state-dir", type=Path, required=True)
    parser.add_argument("--java", type=Path)
    parser.add_argument("--auth-package-file", type=Path)
    parser.add_argument("--only", choices=SERVICES)
    parser.add_argument("--wait", type=float, default=60)
    args = parser.parse_args()
    if os.name != "nt":
        parser.error("This local process/PID lifecycle utility has been verified only on Windows")
    state, repo = args.state_dir.resolve(), args.repo.resolve()
    names = [args.only] if args.only else list(SERVICES)
    if args.action == "probe":
        return bool(probe(names))
    if args.action == "stop":
        return bool(stop(state, names))
    if not args.java or not args.java.is_file() or not math.isfinite(args.wait) or args.wait <= 0:
        parser.error("start requires an existing --java executable and a positive --wait")
    if not state.parent.is_dir():
        parser.error("--state-dir parent must already exist")
    state.mkdir(exist_ok=True)
    package = args.auth_package_file.read_text().strip() if args.auth_package_file else None
    if package == "":
        parser.error("The explicitly supplied authentication package cannot be empty")
    started = []
    try:
        for name in names:
            process = launch(repo, args.java.resolve(), state, name, package)
            started.append(name)
            if not wait_ready(name, process, args.wait):
                print(f"Startup failed for {name}; inspect its console log", flush=True)
                stop(state, started)
                return 1
    except (OSError, RuntimeError) as error:
        print(str(error))
        stop(state, started)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
