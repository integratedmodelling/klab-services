"""Verify an explicitly supplied, owned LOCAL launch; never trust a verified flag.

This is an operator evidence checker, not remote binary attestation or a trust
service for hostile evidence authors. Private paths/arguments never enter reports.
psutil is optional and imported only for this explicitly local verification path.
"""
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import urlsplit

import httpx

from klab_client import DeploymentProfile
from klab_client.errors import ConfigurationError
from . import milestone

SERVICES = {"reasoner", "resources", "resolver", "runtime"}
ROLES = {"java-artifact", "component", "worldview", "project", "classpath", "launch-arguments", "jdk"}


def _require(condition):
    if not condition:
        raise ConfigurationError("Local deployment evidence is incomplete, changed, or does not match the running launch")


def _path(value):
    _require(isinstance(value, str) and value and len(value) < 32768)
    path = Path(value)
    _require(path.is_absolute() and path.is_file())
    _require(not any(parent.is_symlink() for parent in (path, *path.parents)))
    return path.resolve()


def _hash(path):
    _require(path.stat().st_size <= 256 * 1024 * 1024)
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def verify_local_launch(path, fixture, profile_path):
    """Recheck live identities and every pinned input. No imports/restarts/network writes.

    The operator supplies the launch manifest from the reviewed isolated harness.
    A declaration alone is insufficient: byte digests, repository identity,
    process birth/executable/@argfile bindings and selected service URLs must match.
    Call before and after acceptance and compare returned snapshots.
    """
    try:
        import psutil
        data, evidence_hash = milestone.read_json(path)
        _require(isinstance(data, dict) and set(data) == {"version", "kind", "deployment", "checkout", "worldview", "resources_configuration", "files", "services"})
        _require(type(data["version"]) is int and data["version"] == 1 and data["kind"] == "owned-local-launch")
        _require(data["deployment"] == fixture.deployment)
        checkout = data["checkout"]
        _require(isinstance(checkout, str) and Path(checkout).is_absolute() and Path(checkout).is_dir())
        options = {"cwd": checkout, "capture_output": True, "check": True, "timeout": 10}
        if hasattr(subprocess, "CREATE_NO_WINDOW"):
            options["creationflags"] = subprocess.CREATE_NO_WINDOW
        revision = subprocess.run(["git", "rev-parse", "HEAD"], **options).stdout.decode().strip()
        dirty = subprocess.run(["git", "status", "--porcelain", "--untracked-files=no"], **options).stdout.strip()
        _require(revision == fixture.deployment["java_checkout_sha"] and not dirty)
        files = data["files"]
        _require(isinstance(files, list) and 11 <= len(files) <= 16384)
        verified, roles, total = {}, set(), 0
        for row in files:
            _require(isinstance(row, dict) and set(row) == {"role", "path", "sha256"})
            _require(row["role"] in ROLES and isinstance(row["sha256"], str) and re.fullmatch(r"[a-f0-9]{64}", row["sha256"]))
            file = _path(row["path"])
            _require(file not in verified)
            total += file.stat().st_size
            _require(total <= 4 * 1024 * 1024 * 1024 and _hash(file) == row["sha256"])
            verified[file] = row
            roles.add(row["role"])
        _require(roles == ROLES)
        _require(any(row["role"] == "component" and row["sha256"] == fixture.deployment["component_sha256"] for row in files))
        worldview = data["worldview"]
        _require(isinstance(worldview, dict) and set(worldview) == {"checkout", "staged"})
        source, staged = (Path(worldview[key]).resolve() for key in ("checkout", "staged"))
        _require(source.is_dir() and staged.is_dir() and not source.is_symlink() and not staged.is_symlink())
        world_options = {**options, "cwd": source}
        world_revision = subprocess.run(["git", "rev-parse", "HEAD"], **world_options).stdout.decode().strip()
        _require(world_revision == fixture.deployment["worldview_revision"])
        _require(not subprocess.run(["git", "status", "--porcelain"], **world_options).stdout.strip())
        names = set(filter(None, subprocess.run(["git", "ls-files", "-z"], **world_options).stdout.decode().split("\0")))
        actual_names = set()
        for name in names:
            original, copied = _path(str(source / name)), _path(str(staged / name))
            _require(copied in verified and verified[copied]["role"] == "worldview" and _hash(original) == verified[copied]["sha256"])
        for member in staged.rglob("*"):
            _require(not member.is_symlink())
            if member.is_file():
                actual_names.add(member.relative_to(staged).as_posix())
        _require(actual_names == names and bool(names))
        configuration = _path(data["resources_configuration"])
        _require(configuration in verified and verified[configuration]["role"] == "project")
        projects, _ = milestone.read_json(configuration)
        projects = projects["projectConfiguration"]
        _require(isinstance(projects, dict) and projects)
        _require(any(Path(project["localPath"]).resolve() == staged and project.get("worldview") is True
                     and project.get("served") is True for project in projects.values()))
        for project in projects.values():
            if project.get("served") is not True:
                continue
            project_root = Path(project["localPath"]).resolve()
            _require(project_root.is_dir())
            for member in project_root.rglob("*"):
                _require(not member.is_symlink())
                if member.is_file():
                    _require(member.resolve() in verified and verified[member.resolve()]["role"] in ("worldview", "project"))
        profile = DeploymentProfile.load(profile_path)
        _require(profile.test_loopback is True and len(profile.services) == 4)
        expected_urls = {service.service_type: service.url for service in profile.services}
        services = data["services"]
        _require(isinstance(services, dict) and set(services) == SERVICES and set(expected_urls) == SERVICES)
        snapshots = {}
        for name, row in services.items():
            _require(isinstance(row, dict) and set(row) == {"id", "url", "pid", "created", "executable", "arguments", "classpath"})
            _require(isinstance(row["id"], str) and row["id"] and row["url"] == expected_urls[name])
            parsed = urlsplit(row["url"])
            _require(parsed.hostname is not None and ipaddress.ip_address(parsed.hostname).is_loopback)
            _require(type(row["pid"]) is int and row["pid"] > 0 and type(row["created"]) in (int, float))
            executable, arguments, classpath = (_path(row[key]) for key in ("executable", "arguments", "classpath"))
            for file, role in ((executable, "jdk"), (arguments, "launch-arguments"), (classpath, "classpath")):
                _require(file in verified and verified[file]["role"] == role)
            _require(fixture.deployment["service_builds"][name] == "classpath-sha256:" + verified[classpath]["sha256"])
            process = psutil.Process(row["pid"])
            _require(abs(process.create_time() - row["created"]) < .02 and Path(process.exe()).resolve() == executable)
            command = process.cmdline()
            _require(len(command) == 2 and command[1].startswith("@") and Path(command[1][1:]).resolve() == arguments)
            # The attested ordered classpath must actually be the launch's -cp.
            lines = arguments.read_text(encoding="utf-8").splitlines()
            values = [line.strip().strip('"') for line in lines]
            homes = [value.removeprefix("-Duser.home=") for value in values if value.startswith("-Duser.home=")]
            _require(len(homes) == 1)
            if name == "resources":
                _require(len(homes) == 1 and (Path(homes[0]) / ".klab/services/resources/resources.yaml").resolve() == configuration)
            plugins = Path(homes[0]) / ".klab/services" / name / "components/plugins"
            loaded_hashes = []
            if plugins.exists():
                for plugin in plugins.rglob("*"):
                    _require(not plugin.is_symlink())
                    if plugin.is_file():
                        _require(plugin.resolve() in verified and verified[plugin.resolve()]["role"] == "component")
                        loaded_hashes.append(verified[plugin.resolve()]["sha256"])
            if name in ("resources", "runtime"):
                _require(fixture.deployment["component_sha256"] in loaded_hashes)
            release = _path(str(executable.parent.parent / "release"))
            _require(release in verified and verified[release]["role"] == "jdk")
            versions = [line.split("=", 1)[1].strip('"') for line in release.read_text().splitlines() if line.startswith("JAVA_VERSION=")]
            _require(versions == [fixture.deployment["jdk"]])
            _require(values.count("-cp") == 1)
            index = values.index("-cp")
            _require(index + 1 < len(values) and values[index + 1] == classpath.read_text().strip().replace("\\", "/"))
            for entry in classpath.read_text().strip().split(os.pathsep):
                entry = Path(entry)
                _require(entry.is_absolute() and entry.exists() and not entry.is_symlink())
                entries = [entry] if entry.is_file() else entry.rglob("*")
                for member in entries:
                    _require(not member.is_symlink())
                    if member.is_file():
                        _require(member.resolve() in verified and verified[member.resolve()]["role"] == "java-artifact")
            with httpx.Client(trust_env=False, follow_redirects=False, timeout=5) as client:
                with client.stream("GET", row["url"] + "/public/capabilities") as response:
                    _require(response.status_code == 200)
                    body = bytearray()
                    for block in response.iter_bytes():
                        body.extend(block)
                        _require(len(body) <= 1024 * 1024)
                    _require(json.loads(body).get("serviceId") == row["id"])
            snapshots[name] = {"id": row["id"], "url": row["url"], "pid": row["pid"], "created": row["created"]}
        return {"version": 1, "kind": "verified-owned-local-launch", "deployment": data["deployment"],
                "evidence_sha256": evidence_hash, "services": snapshots,
                "input_files": len(files), "input_bytes": total,
                "input_identity_sha256": milestone.digest(str(sorted((row["role"], row["sha256"]) for row in files)).encode()),
                "limits": "Operator-reviewed local evidence; not remote attestation, hostile-author authentication, or scientific accuracy"}
    except ConfigurationError:
        raise
    except Exception:
        raise ConfigurationError("Local deployment verification failed; private paths, credentials and process details were withheld") from None


def validate_recorded_launch(report, deployment):
    """Consistency check for the report emitted by before/after live verification."""
    _require(isinstance(report, dict) and report.get("version") == 1 and report.get("kind") == "verified-owned-local-launch")
    _require(report.get("deployment") == deployment and set(report.get("services", {})) == SERVICES)
    _require(type(report.get("input_files")) is int and report["input_files"] >= 11)
    for key in ("evidence_sha256", "input_identity_sha256"):
        _require(isinstance(report.get(key), str) and re.fullmatch(r"[a-f0-9]{64}", report[key]))
    return report
