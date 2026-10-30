"""Explicit fixture loading, candidate identity and shared milestone acceptance helpers.

The fixture is data, never executable Python or credentials. This module does not
provision services, guess a scientific matrix or turn partial evidence into acceptance.
"""
from dataclasses import dataclass
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import sys
import time
from uuid import uuid4

from klab_client import (ContextOptions, DeploymentProfile, Experiment, HubCertificateProvider,
                         ObservationOptions, ObservationRequest, OwnedContextPolicy, RectangularGeometry, connect)
from klab_client.errors import ConfigurationError
from klab_client.handles import _invalid_constant, _unique
from .discover_compare_explain import MatrixCase, RasterOracle, run_matrix, validate_matrix
from .scientific_checks import require

PACKAGE = Path(__file__).resolve().parents[2]
HEX = re.compile(r"[0-9a-f]{64}")
PRIVATE = re.compile(r"(?i)password|credential|authorization|access.?token|secret|private.?key")


def digest(contents):
    return hashlib.sha256(contents).hexdigest()


def safe_json(value):
    """Reject credential-like fields/JWTs; not a general proof of arbitrary-text secrecy."""
    if isinstance(value, dict):
        if any(not isinstance(key, str) or PRIVATE.search(key) for key in value):
            raise ConfigurationError("Private/credential fields are not allowed in milestone evidence")
        for item in value.values():
            safe_json(item)
    elif isinstance(value, (list, tuple)):
        for item in value:
            safe_json(item)
    elif isinstance(value, str) and re.search(r"eyJ[\w-]+\.[\w-]+\.[\w-]+|-----BEGIN .*PRIVATE KEY", value):
        raise ConfigurationError("Private credential material is not allowed in milestone evidence")


def read_json(path, *, budget=8 * 1024 * 1024):
    try:
        with Path(path).open("rb") as stream:
            raw = stream.read(budget + 1)
        if len(raw) > budget:
            raise ValueError()
        value = json.loads(raw, object_pairs_hook=_unique, parse_constant=_invalid_constant)
        safe_json(value)
        return value, digest(raw)
    except (OSError, ValueError, TypeError):
        raise ConfigurationError("Milestone JSON is missing, malformed, duplicated or oversized") from None


def write_json(path, value):
    safe_json(value)
    encoded = json.dumps(value, sort_keys=True, indent=2, allow_nan=False)
    with Path(path).open("x", encoding="utf-8") as stream:
        stream.write(encoded)


def _request(value):
    if not isinstance(value, dict) or not {"definition"} <= value.keys() <= {"definition", "geometry", "scenarios", "namespace", "project"}:
        raise ConfigurationError("Use an explicit definition and supported typed request fields")
    geometry = RectangularGeometry(**value["geometry"]) if value.get("geometry") else None
    return ObservationRequest(value["definition"], geometry=geometry,
        options=ObservationOptions(scenarios=value.get("scenarios", ()), namespace=value.get("namespace"), project=value.get("project")))


@dataclass(frozen=True)
class Fixture:
    sha256: str
    cases: tuple
    model_urn: str
    expected_node_ids: tuple
    authority: dict
    deployment: dict
    reference: dict


def load_fixture(path):
    """Bounded explicit four-cell fixture. No imports, provisioning or inferred values."""
    data, identity = read_json(path)
    fields = {"version", "cases", "model_urn", "expected_node_ids", "authority", "deployment", "reference"}
    if not isinstance(data, dict) or set(data) != fields or type(data["version"]) is not int or data["version"] != 1:
        raise ConfigurationError("Unsupported milestone fixture schema")
    if not isinstance(data["cases"], list) or len(data["cases"]) != 4:
        raise ConfigurationError("Fixture requires exactly four scientific cases")
    deployment = data["deployment"]
    required = {"java_checkout_sha", "jdk", "component_sha256", "worldview_revision", "service_builds"}
    if not isinstance(deployment, dict) or set(deployment) != required:
        raise ConfigurationError("Record the unchanged Java/JDK/component/worldview/service identities")
    if (not re.fullmatch(r"[0-9a-f]{40}", deployment["java_checkout_sha"])
            or not HEX.fullmatch(deployment["component_sha256"])
            or any(not isinstance(deployment[k], str) or not deployment[k].strip() for k in ("jdk", "worldview_revision"))
            or not isinstance(deployment["service_builds"], dict)
            or set(deployment["service_builds"]) != {"reasoner", "resources", "resolver", "runtime"}
            or any(not isinstance(v, str) or not v.strip() for v in deployment["service_builds"].values())):
        raise ConfigurationError("Deployment provenance is incomplete")
    reference = data["reference"]
    if (not isinstance(reference, dict) or set(reference) != {"source", "revision", "method"}
            or any(not isinstance(v, str) or not v.strip() for v in reference.values())
            or reference["method"] not in ("independent-provider", "controlled-fixture")):
        raise ConfigurationError("Reference provenance must be independently specified")
    authority = data["authority"]
    if (not isinstance(authority, dict) or set(authority) != {"name", "query", "code", "concept", "parent_urn"}
            or any(not isinstance(v, str) or not v.strip() for v in authority.values())):
        raise ConfigurationError("Authority selection and independently expected hierarchy are required")
    if (not isinstance(data["model_urn"], str) or not data["model_urn"].strip()
            or not isinstance(data["expected_node_ids"], list) or not 1 <= len(data["expected_node_ids"]) <= 256
            or any(not isinstance(v, str) or not v for v in data["expected_node_ids"])):
        raise ConfigurationError("An existing model and expected recorded computation node identities are required")
    cases = []
    try:
        for row in data["cases"]:
            if not isinstance(row, dict) or set(row) != {"name", "variation", "resolution", "request", "context_request", "oracle"}:
                raise ConfigurationError("Unknown or missing case fields")
            cases.append(MatrixCase(Experiment(row["name"], _request(row["request"]),
                OwnedContextPolicy(ContextOptions(persistence="EXPLICIT_ACTION")),
                context_request=_request(row["context_request"]), variation=row["variation"]),
                row["resolution"], RasterOracle(**row["oracle"])))
        cases = validate_matrix(cases)
    except (TypeError, KeyError):
        raise ConfigurationError("Malformed typed scientific fixture") from None
    return Fixture(identity, cases, data["model_urn"], tuple(data["expected_node_ids"]), authority, deployment, reference)


def source_identity():
    """Bind evidence to SDK plus workflow/test/notebook code, normalized for Git line endings."""
    root = PACKAGE.parent
    def git(*arguments):
        return subprocess.run(["git", *arguments], cwd=root, check=True, capture_output=True, text=True).stdout.strip()
    files = {}
    for directory in ("src/klab_client", "tools", "tests", "examples/notebooks"):
        for path in sorted((PACKAGE / directory).rglob("*.py")):
            files[path.relative_to(PACKAGE).as_posix()] = digest(path.read_bytes().replace(b"\r\n", b"\n"))
    files["pyproject.toml"] = digest((PACKAGE / "pyproject.toml").read_bytes().replace(b"\r\n", b"\n"))
    import klab_client
    installed = {p.relative_to(Path(klab_client.__file__).parent).as_posix(): digest(p.read_bytes().replace(b"\r\n", b"\n"))
                 for p in Path(klab_client.__file__).parent.rglob("*.py")}
    expected = {name.removeprefix("src/klab_client/"): value for name, value in files.items() if name.startswith("src/klab_client/")}
    require(installed == expected, "Installed SDK sources do not match this candidate")
    distribution = importlib.metadata.distribution("klab-python-client")
    metadata = distribution.read_text("METADATA") or distribution.read_text("PKG-INFO")
    if not metadata:
        raise ConfigurationError("Installed SDK package metadata is unavailable")
    return {"candidate_sha": git("rev-parse", "HEAD"), "dirty": bool(git("status", "--porcelain")),
            "source_sha256": digest(json.dumps(files, sort_keys=True).encode()),
            "installed_package_sha256": digest(json.dumps(installed, sort_keys=True).encode()),
            "installed_metadata_sha256": digest(metadata.encode()),
            "package_version": distribution.version,
            "python": platform.python_version(), "platform": platform.platform()}


def factory_from_paths(profile_path, certificate_path, *, allow_owned_work=False):
    profile = DeploymentProfile.load(profile_path)
    # Load private credentials only inside the factory, never in reports or worker arguments.
    def factory():
        return connect(profile, credentials=HubCertificateProvider(Path(certificate_path)), initialize=allow_owned_work)
    return factory


def discover(factory, fixture):
    from klab_client import ResourceInspector, SemanticExplorer
    with factory() as connection:
        explorer = SemanticExplorer(connection.client)
        authority = fixture.authority
        page = explorer.search_authority(authority["name"], authority["query"], limit=20)
        require(page.status == "OK", "Configured authority search is unavailable/failed")
        candidates = [v for v in page.matches if v.code == authority["code"]]
        require(len(candidates) == 1, "Explicit authority identity absent or ambiguous; never select by label")
        docs = explorer.documentation(candidates[0])
        result = explorer.resolve_candidate(candidates[0])
        parents = explorer.parents(authority["concept"])
        require(any(p.urn == authority["parent_urn"] for p in parents), "Expected independent parent relationship absent")
        children = explorer.children(authority["parent_urn"])
        require(any(c.urn == authority["concept"] for c in children), "Expected independent child relationship absent")
        require(explorer.is_a(authority["concept"], authority["parent_urn"]) is True
                and explorer.satisfiable(authority["concept"]) is True, "Independent semantic checks disagree")
        model = ResourceInspector(connection.client).retrieve(fixture.model_urn)
        return {"authority_status": page.status, "selected_code": candidates[0].code,
                "resolved_urn": result.urn, "documentation_types": sorted(docs),
                "model_urn": model.urn, "model_selection": model.selection,
                "authority_influences_numeric_field": False}


def run_case_batch(factory, fixture, directory, *, workers):
    identity = source_identity()
    started = time.perf_counter()
    discovery = discover(factory, fixture)
    discovery_seconds = time.perf_counter() - started
    result = run_matrix(factory, fixture.cases, directory, model_urn=fixture.model_urn,
                        expected_node_ids=fixture.expected_node_ids, workers=workers)
    require(source_identity() == identity, "Candidate changed during the experiment batch")
    report = {"version": 1, "run_id": uuid4().hex, "identity": identity, "fixture_sha256": fixture.sha256,
        "deployment": fixture.deployment, "reference": fixture.reference, "discovery": discovery,
        "workers": workers, "batch": json.loads(result["batch"].dumps()),
        "processed": {o.name: o.processed for o in result["batch"].outcomes},
        "matrix_verified": result["matrix_verified"], "scientific_acceptance": False,
        "setup_seconds": result["setup_seconds"], "discovery_seconds": discovery_seconds,
        "complete_seconds": time.perf_counter() - started}
    write_json(Path(directory) / "run.json", report)
    return report


def restore_in_process(fixture_path, batch_path, directory, profile_path, certificate_path):
    """Worker operation, also directly testable; never initialize peers or submit work."""
    from .discover_compare_explain import restore_matrix
    fixture = load_fixture(fixture_path)
    batch, _ = read_json(batch_path, budget=1024 * 1024)
    factory = factory_from_paths(profile_path, certificate_path)
    started = time.perf_counter()
    records = restore_matrix(factory, fixture.cases, json.dumps(batch), directory, expected_node_ids=fixture.expected_node_ids)
    report = {"version": 1, "run_id": uuid4().hex, "pid": os.getpid(), "identity": source_identity(),
              "fixture_sha256": fixture.sha256, "results": records, "elapsed_seconds": time.perf_counter() - started}
    write_json(Path(directory) / "restoration.json", report)
    return report


def launch_restore(fixture_path, batch_path, directory, profile_path, certificate_path):
    """Fresh isolated Python; only file paths in arguments/environment, no bearer credentials."""
    env = dict(os.environ, KLAB_MILESTONE_PROFILE=str(Path(profile_path).resolve()),
               KLAB_MILESTONE_CERTIFICATE=str(Path(certificate_path).resolve()))
    operation = PACKAGE / "tools/restore_milestone_results.py"
    result = subprocess.run([sys.executable, "-I", str(operation), "--fixture", str(Path(fixture_path).resolve()),
        "--batch", str(Path(batch_path).resolve()), "--output", str(Path(directory).resolve())],
        env=env, capture_output=True, timeout=900)
    require(result.returncode == 0, "Fresh-process restoration failed; private child output was not published")
    report, _ = read_json(Path(directory) / "restoration.json")
    require(report["pid"] != os.getpid(), "Restoration did not execute in a fresh process")
    require(report["identity"] == source_identity(), "Candidate changed across process restoration")
    fixture = load_fixture(fixture_path)
    require(report["fixture_sha256"] == fixture.sha256, "Fixture changed during restoration")
    return report
