"""Synthetic filesystem/process/wire tests, never real deployment acceptance."""
import hashlib
import json
import os
from pathlib import Path
from types import SimpleNamespace as NS

import httpx
import pytest

from klab_client import DeploymentProfile
from klab_client.errors import ConfigurationError
from workflows import deployment as check


@pytest.fixture
def launch(tmp_path, monkeypatch):
    checkout = tmp_path / "java"
    checkout.mkdir()
    world_source, world = tmp_path / "world-source", tmp_path / "world"
    world_source.mkdir()
    world.mkdir()
    (world / "empty").mkdir()
    (world_source / "a.kwv").write_text("synthetic source")
    (world / "a.kwv").write_text("synthetic source")
    project = tmp_path / "project"
    project.mkdir()
    (project / "empty").mkdir()
    (project / "model.kim").write_text("synthetic model")
    executable = tmp_path / "jdk/bin/java.exe"
    executable.parent.mkdir(parents=True)
    executable.write_bytes(b"not a real executable")
    release = executable.parent.parent / "release"
    release.write_text('JAVA_VERSION="21.0.12"\n')
    jar = tmp_path / "accepted.jar"
    jar.write_bytes(b"synthetic jar")
    component = tmp_path / "component.kar"
    component.write_bytes(b"synthetic component")
    digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    rows = []

    def add(path, role):
        rows.append(dict(path=str(path), role=role, sha256=digest(path)))

    for path, role in ((jar, "java-artifact"), (executable, "jdk"), (release, "jdk"),
                       (component, "component"), (world / "a.kwv", "worldview"), (project / "model.kim", "project")):
        add(path, role)
    urls = {name: f"http://127.0.0.1:{18090 + i}/{name}" for i, name in enumerate(sorted(check.SERVICES), 1)}
    services = {}
    deployment = dict(java_checkout_sha="a" * 40, worldview_revision="b" * 40, jdk="21.0.12",
                      component_sha256=digest(component), service_builds={})
    for i, name in enumerate(sorted(check.SERVICES), 1):
        home = tmp_path / name
        home.mkdir()
        cp = tmp_path / (name + ".cp")
        cp.write_text(str(jar))
        arguments = tmp_path / (name + ".args")
        arguments.write_text('\n'.join('"' + value.replace("\\", "/") + '"' for value in
            ("-Duser.home=" + str(home), "-cp", str(jar), "SyntheticMain")))
        add(cp, "classpath")
        add(arguments, "launch-arguments")
        deployment["service_builds"][name] = "classpath-sha256:" + digest(cp)
        services[name] = dict(id=name + "-id", url=urls[name], pid=1000 + i, created=100.0,
                              executable=str(executable), arguments=str(arguments), classpath=str(cp))
        if name in ("runtime", "resources"):
            plugin = home / (".klab/services/" + name + "/components/plugins/installed.jar")
            plugin.parent.mkdir(parents=True)
            (plugin.parent / "empty").mkdir()
            plugin.write_bytes(component.read_bytes())
            add(plugin, "component")
    config = tmp_path / "resources/.klab/services/resources/resources.yaml"
    config.parent.mkdir(parents=True, exist_ok=True)
    config.write_text(json.dumps(dict(projectConfiguration=dict(
        world=dict(localPath=str(world), served=True, worldview=True),
        model=dict(localPath=str(project), served=True, worldview=False),
        not_served=dict(served=False)))))
    add(config, "project")
    data = dict(version=1, kind="owned-local-launch", deployment=deployment, checkout=str(checkout),
                worldview=dict(checkout=str(world_source), staged=str(world)),
                resources_configuration=str(config), files=rows, services=services)
    path = tmp_path / "launch.json"
    profile = tmp_path / "profile.json"
    DeploymentProfile.from_urls("offline", urls, test_loopback=True).save(profile)

    def git(arguments, **options):
        if arguments[1:] == ["rev-parse", "HEAD"]:
            return NS(stdout=(("b" if Path(options["cwd"]) == world_source else "a") * 40).encode())
        if arguments[1:3] == ["status", "--porcelain"]:
            return NS(stdout=b"")
        if arguments[1:] == ["ls-files", "-z"]:
            return NS(stdout=b"a.kwv\0")
        raise AssertionError("Unexpected process operation")

    def process(pid):
        row = next(row for row in services.values() if row["pid"] == pid)
        return NS(create_time=lambda: 100.0, exe=lambda: str(executable),
                  cmdline=lambda: [str(executable), "@" + row["arguments"]])

    import psutil
    monkeypatch.setattr(psutil, "Process", process)
    monkeypatch.setattr(check.subprocess, "run", git)
    original_client = httpx.Client
    reply = {"status": 200, "service_override": None, "body": None}

    def handler(request):
        name = request.url.path.split("/")[1]
        return httpx.Response(reply["status"], content=reply["body"] if reply["body"] is not None else
            json.dumps({"serviceId": reply["service_override"] or name + "-id"}).encode())

    monkeypatch.setattr(check.httpx, "Client", lambda **kwargs: original_client(transport=httpx.MockTransport(handler), **kwargs))

    def verify():
        path.write_text(json.dumps(data))
        return check.verify_local_launch(path, NS(deployment=deployment), profile)

    return NS(data=data, rows=rows, verify=verify, path=path, profile=profile, deployment=deployment,
              executable=executable, jar=jar, world=world, project=project, config=config, reply=reply,
              tmp=tmp_path, digest=digest, add=add)


def test_local_launch_rechecks_actual_files_processes_and_service_ids_without_private_paths(launch, monkeypatch):
    # Exercise the Windows subprocess option on every CI platform; subprocess.run
    # is replaced by the fixture's inert Git responder, so no process is launched.
    monkeypatch.setattr(check.subprocess, "CREATE_NO_WINDOW", 0, raising=False)
    report = launch.verify()
    assert report == launch.verify()
    assert check.validate_recorded_launch(report, launch.deployment) == report
    assert report["input_files"] == len(launch.rows)
    assert str(launch.tmp) not in json.dumps(report)
    assert "not remote attestation" in report["limits"]


def test_directory_classpath_requires_each_class_and_handles_non_windows_subprocess_options(launch, monkeypatch):
    monkeypatch.delattr(check.subprocess, "CREATE_NO_WINDOW", raising=False)
    classes = launch.tmp / "classes"
    (classes / "empty").mkdir(parents=True)
    bytecode = classes / "Synthetic.class"
    bytecode.write_bytes(b"synthetic bytecode")
    launch.add(bytecode, "java-artifact")
    runtime = launch.data["services"]["runtime"]
    cp, arguments = Path(runtime["classpath"]), Path(runtime["arguments"])
    cp.write_text(str(classes))
    arguments.write_text(arguments.read_text().replace(str(launch.jar).replace("\\", "/"), str(classes).replace("\\", "/")))
    for row in launch.rows:
        if row["path"] in (str(cp), str(arguments)):
            row["sha256"] = launch.digest(Path(row["path"]))
    launch.deployment["service_builds"]["runtime"] = "classpath-sha256:" + launch.digest(cp)
    assert launch.verify()["input_files"] == len(launch.rows)
    (classes / "Unpinned.class").write_bytes(b"unreviewed")
    with pytest.raises(ConfigurationError):
        launch.verify()


@pytest.mark.parametrize("fault", ["version", "kind", "extra", "digest", "duplicate", "missing-role",
    "missing-component", "missing-world", "world-drift", "unlisted-world", "unlisted-project", "unlisted-class",
    "wrong-build", "wrong-id", "http-error", "oversized-response", "bad-json", "bad-url", "birth", "pid",
    "executable", "arguments", "classpath", "java-version", "configuration", "unlisted-plugin", "missing-plugin"])
def test_local_launch_fails_closed_on_identity_input_configuration_or_wire_drift(launch, fault):
    data = launch.data
    runtime = data["services"]["runtime"]
    if fault == "version":
        data["version"] = True
    elif fault == "kind":
        data["kind"] = "trust-me"
    elif fault == "extra":
        data["verified"] = True
    elif fault == "digest":
        launch.jar.write_bytes(b"changed")
    elif fault == "duplicate":
        data["files"].append(dict(data["files"][0]))
    elif fault == "missing-role":
        data["files"] = [row for row in data["files"] if row["role"] != "java-artifact"]
    elif fault == "missing-component":
        launch.deployment["component_sha256"] = "0" * 64
    elif fault == "missing-world":
        (launch.world / "a.kwv").unlink()
    elif fault == "world-drift":
        (launch.tmp / "world-source/a.kwv").write_text("wrong source")
    elif fault == "unlisted-world":
        extra = launch.world / "unlisted.kwv"
        extra.write_text("not in source revision")
        launch.add(extra, "worldview")
    elif fault == "unlisted-project":
        (launch.project / "unlisted.kim").write_text("not pinned")
    elif fault == "unlisted-class":
        cp = Path(runtime["classpath"])
        cp.write_text(str(launch.jar) + os.pathsep + str(launch.executable))
        for row in data["files"]:
            if row["path"] == str(cp):
                row["sha256"] = launch.digest(cp)
    elif fault == "wrong-build":
        launch.deployment["service_builds"]["runtime"] = "declared only"
    elif fault == "wrong-id":
        launch.reply["service_override"] = "other-service"
    elif fault == "http-error":
        launch.reply["status"] = 500
    elif fault == "oversized-response":
        launch.reply["body"] = b"x" * (1024 * 1024 + 1)
    elif fault == "bad-json":
        launch.reply["body"] = b"not JSON"
    elif fault == "bad-url":
        runtime["url"] = "http://example.invalid/runtime"
    elif fault == "birth":
        runtime["created"] = 101.0
    elif fault == "pid":
        runtime["pid"] = True
    elif fault == "executable":
        runtime["executable"] = str(launch.jar)
    elif fault == "arguments":
        runtime["arguments"] = str(launch.jar)
    elif fault == "classpath":
        runtime["classpath"] = str(launch.jar)
    elif fault == "java-version":
        launch.deployment["jdk"] = "unverified-version"
    elif fault == "configuration":
        data["resources_configuration"] = str(launch.jar)
    elif fault == "unlisted-plugin":
        plugin = launch.tmp / "runtime/.klab/services/runtime/components/plugins/extra.jar"
        plugin.write_bytes(b"not pinned")
    elif fault == "missing-plugin":
        plugin = launch.tmp / "runtime/.klab/services/runtime/components/plugins/installed.jar"
        data["files"] = [row for row in data["files"] if row["path"] != str(plugin)]
        plugin.unlink()
    with pytest.raises(ConfigurationError) as failure:
        launch.verify()
    assert str(launch.tmp) not in str(failure.value)


@pytest.mark.parametrize("value", [None, {}, {"version": True}, {"version": 1, "kind": "trust-me"}])
def test_recorded_boolean_is_not_provenance(value):
    with pytest.raises(ConfigurationError):
        check.validate_recorded_launch(value, {})
