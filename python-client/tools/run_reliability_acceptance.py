"""Owned TEST-only Windows scientific fixture; explicit compiled server/artifact pins.

Never attach to shared services, replay writes or replace the legacy scientific
fixture. Uses the separately labelled minimal synthetic consolidation worldview.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

import httpx
import psutil

import fixture_hub
from local_stack import SERVICES

PORTS = {"reasoner": 38091, "resources": 38092, "resolver": 38093, "runtime": 38094}
GRAPH = (38382, 37687, 37474)
CORE = "5c020dac9bd9d1cd4f9673b8075cf08f75ecc7194c74378bdb8f0498110ddba3"
PACKAGE = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def classpath(repo, module):
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    modules = {ET.parse(p).getroot().findtext("m:artifactId", namespaces=ns): p.parent / "target/classes"
               for p in repo.rglob("pom.xml") if "target" not in p.parts and (p.parent / "target/classes").is_dir()}
    file = repo / module / "target/local-stack-classpath.txt"
    if not file.is_file():
        file = repo / module / "local-stack-classpath.txt"
    parts = [str(repo / module / "target/classes")]
    for raw in file.read_text().strip().split(os.pathsep):
        p = Path(raw)
        parts.append(str(modules.get(p.parent.parent.name, p)))
    return os.pathsep.join(dict.fromkeys(parts))


def ready(port, route, process):
    deadline = time.monotonic() + 120
    with httpx.Client(trust_env=False, timeout=2) as http:
        while process.poll() is None and time.monotonic() < deadline:
            try:
                status = http.get(f"http://127.0.0.1:{port}{route}").status_code
                if status == 200 or port == 18090 and status == 501:
                    return
            except httpx.TransportError:
                pass
            time.sleep(.5)
    raise RuntimeError("Owned fixture endpoint did not become ready")


def runtime_control(state, launches, spawn, actions):
    """One ordered stop/start pair, only for the parent's captured Runtime."""
    for action in ("stop", "start"):
        if not (state / (action + "-runtime.request")).exists() or action in actions:
            continue
        if action == "start" and actions != ["stop"]:
            raise RuntimeError("Runtime control sequence rejected")
        process, command, cwd, route = launches["runtime"]
        if action == "stop":
            process.terminate(); process.wait(timeout=20)
        else:
            process = spawn("runtime-restarted", command, cwd)
            launches["runtime"] = (process, command, cwd, route)
            ready(PORTS["runtime"], route, process)
        actions.append(action)
        (state / "runtime-actions.json").write_text(json.dumps(actions))
        (state / (action + "-runtime.done")).write_text("confirmed")


def run(args):
    import klab_client
    state, repo = args.state_dir.resolve(), args.server_repo.resolve()
    if os.name != "nt" or state.exists() or not state.parent.is_dir():
        raise ValueError("Requires Windows and a fresh state directory with an existing parent")
    if Path(klab_client.__file__).resolve().is_relative_to(PACKAGE):
        raise ValueError("Install the noneditable SDK first")
    if sha(repo / "klab.services.reasoner/src/main/resources/knowledge/odo.owl") != CORE:
        raise ValueError("Explicit core ontology pin differs; do not rewrite it")
    archives = json.loads(args.components.read_text(encoding="utf-8"))
    if not isinstance(archives, list) or len(archives) != 2:
        raise ValueError("Exactly two pinned generators/geospatial archives are required")
    for item in archives:
        if sha(Path(item["path"])) != item["sha256"]:
            raise ValueError("Component pin differs")
    for port in (*PORTS.values(), *GRAPH, 18090):
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", port))
    state.mkdir()
    sid = subprocess.check_output(["whoami", "/user", "/fo", "csv", "/nh"], text=True).strip().split(",")[-1].strip('"')
    subprocess.run(["icacls", str(state), "/grant:r", f"*{sid}:(OI)(CI)F", "*S-1-5-18:(OI)(CI)F", "*S-1-5-32-544:(OI)(CI)F", "/inheritance:r"], check=True, capture_output=True)
    existing = {p.pid: p.create_time() for p in psutil.process_iter() if p.name().lower() in ("java.exe", "javaw.exe")}
    processes, logs = [], []
    result = {"passed": False, "server_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip(),
              "components": archives, "core_sha256": CORE, "mutations": [], "scenario": args.scenario, "owned_processes": []}
    result["java_version"] = subprocess.run([str(args.java), "-version"], capture_output=True, check=True).stderr.decode(errors="replace")
    result["tool_sha256"] = {str(p.relative_to(PACKAGE)): sha(p) for p in
        (Path(__file__), PACKAGE / "tools/workflows/nonuniform_export.py", PACKAGE / "tests/fixtures/nonuniform-export.json")}
    result["functional_sha256"] = {p.relative_to(PACKAGE).as_posix(): sha(p)
        for p in (PACKAGE / "tests/functional").glob("*.py")}
    def spawn(name, command, cwd):
        log = (state / (name + ".log")).open("xb"); logs.append(log)
        env = {k: v for k, v in os.environ.items() if not k.startswith("KLAB_") and k not in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")}
        process = subprocess.Popen(command, cwd=cwd, env=env, stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
        processes.append(process)
        result["owned_processes"].append({"name": name, "pid": process.pid, "created": psutil.Process(process.pid).create_time()})
        (state / "owned-processes.json").write_text(json.dumps(result["owned_processes"], indent=2), encoding="utf-8")
        return process
    try:
        projects = state / "projects"
        shutil.copytree(PACKAGE / "tests/fixtures/scientific-reliability", projects)
        result["fixture_sha256"] = {p.relative_to(projects).as_posix(): sha(p) for p in projects.rglob("*") if p.is_file()}
        result["installed_sha256"] = {p.relative_to(Path(klab_client.__file__).parent).as_posix(): sha(p) for p in Path(klab_client.__file__).parent.rglob("*.py")}
        result["compiled_sha256"] = {p.relative_to(repo).as_posix(): sha(p) for p in repo.glob("**/target/classes/**/*.class")}
        key = fixture_hub.rsa.generate_private_key(public_exponent=65537, key_size=2048)
        secret = secrets.token_urlsafe(32)
        (state / "fixture-authority.pem").write_bytes(key.private_bytes(fixture_hub.serialization.Encoding.PEM, fixture_hub.serialization.PrivateFormat.PKCS8, fixture_hub.serialization.NoEncryption()))
        (state / "fixture-authority.json").write_text(json.dumps({"enrollment_secret": secret}))
        for name, port in PORTS.items():
            directory = state / "home/.klab/services" / name; directory.mkdir(parents=True)
            (directory / "service.cert").write_text("\n".join([f"klab.certificate.type={name.upper()}", "klab.certificate.level=TEST", f"klab.nodename=python-test-{name}", "klab.partner.hub=http://127.0.0.1:18090", "klab.partner.email=test@example.invalid", f"klab.url=http://127.0.0.1:{port}/{name}", f"klab.certificate={secret}", f"klab.signature={secret}", ""]))
            config = {"serviceId": str(uuid.uuid4())}
            if name == "runtime":
                config.update(url=f"http://127.0.0.1:{port}/{name}", graphDatabaseUrl=f"bolt://127.0.0.1:{GRAPH[1]}")
            if name == "resources":
                config.update(servicePath=name, workspaces={"reliability": [p.name for p in projects.iterdir()]},
                    projectConfiguration={p.name: {"served": True, "worldview": p.name == "consolidation", "locallyManaged": True,
                        "authoritative": False, "syncIntervalMinutes": 0, "localPath": str(p), "workspaceName": "reliability",
                        "storageType": "FILE", "privileges": {"public": True}} for p in projects.iterdir()})
            (directory / (name + ".yaml")).write_text(json.dumps(config))
        authority = spawn("authority", [sys.executable, str(Path(__file__).resolve()), "--authority", "--state-dir", str(state)], repo)
        ready(18090, "/health", authority)
        source = repo / "klab.services.runtime/src/test/java/org/integratedmodelling/klab/services/runtime/testing/NonuniformExportFixture.java"
        classes = repo / "klab.services.runtime/target/test-classes"
        subprocess.run([str(args.java.with_name("javac.exe")), "-d", str(classes), str(source)], check=True, capture_output=True)
        result["test_class_sha256"] = sha(classes / "org/integratedmodelling/klab/services/runtime/testing/NonuniformExportFixture.class")
        result["test_source_sha256"] = sha(source)
        launches = {}
        for name in ("graphdb", "resources", "reasoner", "resolver", "runtime"):
            module, main, _, route = SERVICES[name]
            port = GRAPH[0] if name == "graphdb" else PORTS[name]
            cp = classpath(repo, module)
            if name == "runtime":
                cp = str(classes) + os.pathsep + cp
                result["runtime_classpath_jars_sha256"] = {raw: sha(Path(raw)) for raw in cp.split(os.pathsep) if Path(raw).is_file()}
            options = ["-Xmx768m", "-Xlog:class+load=info", "-Dklab.test.controls=" + str(state), "-Duser.home=" + str(state / "home"),
                "-Dserver.address=127.0.0.1", f"-Dserver.port={port}", "-cp", cp, main]
            options += ([f"--server.port={port}", f"--app.neo4j.bolt-port={GRAPH[1]}", f"--app.neo4j.http-port={GRAPH[2]}"] if name == "graphdb" else
                        ["-dataDir", str(state / "home/.klab"), "-port", str(port), "-contextPath", "/" + name, "-cert", str(state / f"home/.klab/services/{name}/service.cert")])
            argfile = state / (name + ".args")
            argfile.write_text("\n".join('"' + arg.replace("\\", "/").replace('"', '\\"') + '"' for arg in options), encoding="utf-8")
            command = [str(args.java), "@" + str(argfile)]
            process = spawn(name, command, repo / module); ready(port, route, process)
            launches[name] = (process, command, repo / module, route)
        with httpx.Client(trust_env=False, timeout=30) as http:
            for service in ("resources", "runtime"):
                base = f"http://127.0.0.1:{PORTS[service]}/{service}"
                headers = {"Authorization": "018fc355-c123-7608-be4a-89ea1059c33e", "server-key": (state / f"home/.klab/services/{service}/secret.key").read_text().strip()}
                for item in archives:
                    entry = {"service": service, "sha256": item["sha256"], "outcome": "UNKNOWN"}
                    result["mutations"].append(entry)
                    (state / "import-journal.json").write_text(json.dumps(result["mutations"], indent=2))
                    with Path(item["path"]).open("rb") as stream:
                        response = http.post(base + "/import/component.kar.import/X:X:X:X", headers=headers, files={"file": (Path(item["path"]).name, stream, "application/java-archive")})
                    response.raise_for_status(); job = response.json()
                    if type(job) is not int or job <= 0:
                        raise RuntimeError("Import outcome unknown; never replay")
                    deadline = time.monotonic() + 120
                    while time.monotonic() < deadline:
                        response = http.get(base + f"/jobs/status/{job}", headers=headers); response.raise_for_status()
                        status = response.json().get("status")
                        if status == "FINISHED":
                            entry["outcome"] = "FINISHED"; break
                        if status not in ("WAITING", "STARTED", "CHANGED"):
                            raise RuntimeError("Import failed; never replay")
                        time.sleep(.5)
                    else:
                        raise RuntimeError("Import deadline; never replay")
            process, command, cwd, route = launches["resources"]
            process.terminate(); process.wait(timeout=20)
            process = spawn("resources-reindexed", command, cwd); ready(PORTS["resources"], route, process)
        fixture_hub.SERVICES = PORTS
        fixture_hub.issue(state, key)
        with (state / "functional-private.log").open("xb") as log:
            functional = subprocess.Popen([sys.executable, str(PACKAGE / "tools/run_functional_acceptance.py"),
                "--suite", args.scenario, "--state-dir", str(state), "--report", str(state / "functional.xml")],
                cwd=PACKAGE, stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
            try:
                actions = []
                deadline = time.monotonic() + 420
                while functional.poll() is None:
                    if time.monotonic() > deadline:
                        raise TimeoutError("Owned functional deadline expired; never replay")
                    if args.scenario == "partial-refresh":
                        runtime_control(state, launches, spawn, actions)
                    time.sleep(.1)
                if functional.returncode:
                    raise RuntimeError("Owned functional suite failed; inspect private evidence")
            finally:
                if functional.poll() is None:
                    # The named-suite launcher owns a pytest child. Capture only
                    # this process tree before terminating its parent on failure.
                    descendants = psutil.Process(functional.pid).children(recursive=True)
                    for child in reversed(descendants):
                        try:
                            child.terminate()
                        except psutil.NoSuchProcess:
                            pass
                    functional.terminate(); functional.wait(timeout=20)
                    _, alive = psutil.wait_procs(descendants, timeout=20)
                    for child in alive:
                        child.kill()
                    _, alive = psutil.wait_procs(alive, timeout=20)
                    if alive:
                        raise RuntimeError("Owned functional descendant cleanup unconfirmed")
        result_file = {"parser-preview": "parser-preview.json", "partial-refresh": "partial-refresh.json", "request-plan": "request-plan.json",
            "nonuniform-export": "nonuniform-science.json"}[args.scenario]
        result["science"] = json.loads((state / result_file).read_text())
        result["passed"] = True
    except Exception as error:
        result["error_type"] = type(error).__name__  # Private service/credential text is not echoed.
        import traceback
        (state / "failure-private.txt").write_text(traceback.format_exc(), encoding="utf-8")
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    process.kill(); process.wait(timeout=20)
        for log in logs:
            log.close()
        result["owned_processes_closed"] = all(p.poll() is not None for p in processes)
        result["existing_java_unchanged"] = all(psutil.pid_exists(pid) and psutil.Process(pid).create_time() == created for pid, created in existing.items())
        result["listeners_closed"] = True
        for port in (*PORTS.values(), *GRAPH, 18090):
            with socket.socket() as probe:
                result["listeners_closed"] &= probe.connect_ex(("127.0.0.1", port)) != 0
        result["passed"] &= result["owned_processes_closed"] and result["existing_java_unchanged"] and result["listeners_closed"]
        (state / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        print(json.dumps({key: result.get(key) for key in ("passed", "error_type", "science", "owned_processes_closed", "existing_java_unchanged", "listeners_closed")}), flush=True)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--authority", action="store_true")
    parser.add_argument("--server-repo", type=Path)
    parser.add_argument("--java", type=Path)
    parser.add_argument("--components", type=Path, help="JSON list of exactly two explicit {path,sha256} archives")
    parser.add_argument("--state-dir", type=Path, required=True)
    parser.add_argument("--scenario", choices=("nonuniform-export", "partial-refresh", "request-plan", "parser-preview"), default="nonuniform-export")
    args = parser.parse_args()
    if args.authority:
        fixture_hub.SERVICES = PORTS
        fixture_hub.serve(args.state_dir)
    else:
        if args.server_repo is None or args.java is None or args.components is None:
            parser.error("Explicit server, Java and components are required")
        raise SystemExit(run(args))
