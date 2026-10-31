"""Fail-closed milestone tooling. Synthetic evidence here is never live acceptance."""
import json
import os
from pathlib import Path
import subprocess
import sys
from types import SimpleNamespace as NS

import pytest

from workflows import milestone as tool
from klab_client.errors import ConfigurationError


def fixture_data():
    rows = []
    for unit in ("m", "mm"):
        for resolution, size, span in (("fine", 2, 100), ("coarse", 1, 200)):
            rows.append({"name": unit + "-" + resolution, "variation": unit, "resolution": resolution,
                "request": {"definition": "geography:Elevation in " + unit},
                "context_request": {"definition": "earth:Region", "geometry": dict(west=200000, east=200200,
                    south=6000000, north=6000200, columns=size, rows=size)},
                "oracle": dict(source_sha256="a" * 64, units=unit, crs="EPSG:3857", width=size, height=size,
                    transform=[span, 0, 200000, 0, -span, 6000200], dtype="float64", values=["0"] * (size * size))})
    return {"version": 1, "cases": rows, "model_urn": "fixture.model", "expected_node_ids": ["fixture-node"],
        "authority": dict(name="TEST", query="explicit", code="1", concept="fixture:Child", parent_urn="fixture:Parent"),
        "deployment": dict(java_checkout_sha="b" * 40, jdk="21", component_sha256="c" * 64,
            worldview_revision="fixture-only", service_builds={name: "test-only" for name in ("reasoner", "resources", "resolver", "runtime")}),
        "reference": dict(source="independently specified unit-test oracle", revision="1", method="controlled-fixture")}


def write_fixture(path, data=None):
    path.write_text(json.dumps(fixture_data() if data is None else data))
    return tool.load_fixture(path)


def test_fixture_loads_typed_requests_without_network(tmp_path):
    fixture = write_fixture(tmp_path / "fixture.json")
    assert len(fixture.cases) == 4 and fixture.cases[0].experiment.context_request.geometry is not None
    assert fixture.cases[0].experiment.context_policy.options.persistence == "EXPLICIT_ACTION"
    assert len(fixture.sha256) == 64


@pytest.mark.parametrize("geometry", [False, 0, [], "", {}])
def test_explicit_malformed_geometry_is_not_silently_omitted(tmp_path, geometry):
    data = fixture_data()
    data["cases"][0]["context_request"]["geometry"] = geometry
    with pytest.raises(ConfigurationError, match="geometry"):
        write_fixture(tmp_path / "fixture.json", data)


@pytest.mark.parametrize("field", ["java_checkout_sha", "component_sha256"])
def test_nontext_deployment_digest_has_a_configuration_error(tmp_path, field):
    data = fixture_data()
    data["deployment"][field] = 123
    with pytest.raises(ConfigurationError, match="provenance"):
        write_fixture(tmp_path / "fixture.json", data)


@pytest.mark.parametrize("field,value", [("version", 2), ("version", True), ("cases", []), ("deployment", {}),
    ("reference", {}), ("authority", {}), ("model_urn", ""), ("expected_node_ids", []), ("expected_node_ids", [None])])
def test_fixture_missing_prerequisites_fail(tmp_path, field, value):
    data = fixture_data()
    data[field] = value
    with pytest.raises(ConfigurationError):
        write_fixture(tmp_path / "fixture.json", data)


@pytest.mark.parametrize("change", ["deployment", "reference", "case", "request", "oracle"])
def test_malformed_nested_fixture(tmp_path, change):
    data = fixture_data()
    if change == "deployment":
        data["deployment"]["component_sha256"] = "unknown"
    elif change == "reference":
        data["reference"]["method"] = "SDK-output"
    elif change == "case":
        data["cases"][0]["unknown"] = True
    elif change == "request":
        data["cases"][0]["request"]["force_model"] = "unsupported"
    else:
        data["cases"][0]["oracle"]["extra"] = "unsupported"
    with pytest.raises(ConfigurationError):
        write_fixture(tmp_path / "fixture.json", data)


@pytest.mark.parametrize("value", [{"accessToken": "private"}, {"password": "private"},
    {"data": ["eyJabc.def.ghi"]}, {"data": "-----BEGIN RSA PRIVATE KEY"}])
def test_evidence_rejects_credentials_before_publication(tmp_path, value):
    with pytest.raises(ConfigurationError):
        tool.write_json(tmp_path / "not-published.json", value)
    assert not (tmp_path / "not-published.json").exists()


@pytest.mark.parametrize("text", ["null trailing", '{"x":1,"x":2}', 'NaN'])
def test_bounded_strict_json(tmp_path, text):
    path = tmp_path / "data.json"
    path.write_text(text)
    with pytest.raises(Exception):
        tool.read_json(path)
    path.write_text("{}")
    with pytest.raises(ConfigurationError):
        tool.read_json(path, budget=1)
    with pytest.raises(ConfigurationError):
        tool.read_json(tmp_path / "missing")


def test_source_identity_matches_installed_current_sources():
    value = tool.source_identity()
    assert len(value["source_sha256"]) == len(value["installed_package_sha256"]) == 64
    assert len(value["candidate_sha"]) == 40 and value["package_version"]


@pytest.mark.parametrize("metadata_file", ["METADATA", "PKG-INFO"])
def test_source_identity_handles_wheel_and_editable_metadata(monkeypatch, metadata_file):
    metadata = "Metadata-Version: 2.4\nName: klab-python-client\nVersion: 0.1.0\n"
    monkeypatch.setattr(tool.importlib.metadata, "distribution",
        lambda name: NS(version="0.1.0", read_text=lambda filename: metadata if filename == metadata_file else None))
    assert tool.source_identity()["installed_metadata_sha256"] == tool.digest(metadata.encode())


def test_source_identity_rejects_missing_metadata_instead_of_inventing_a_hash(monkeypatch):
    monkeypatch.setattr(tool.importlib.metadata, "distribution", lambda name: NS(read_text=lambda filename: None))
    with pytest.raises(ConfigurationError, match="metadata"):
        tool.source_identity()


def test_factory_keeps_credentials_in_provider_only(tmp_path, monkeypatch):
    calls = []
    monkeypatch.setattr(tool.DeploymentProfile, "load", lambda path: "explicit-profile")
    monkeypatch.setattr(tool, "HubCertificateProvider", lambda path: "private-provider")
    monkeypatch.setattr(tool, "connect", lambda profile, **kwargs: calls.append((profile, kwargs)))
    tool.factory_from_paths(tmp_path / "profile", tmp_path / "private")()
    assert calls == [("explicit-profile", dict(credentials="private-provider", initialize=False))]


class Owner:
    def __enter__(self):
        return self
    def __exit__(self, *args):
        pass
    @property
    def client(self):
        return self


def test_discovery_requires_explicit_candidate_and_parent(tmp_path, monkeypatch):
    from klab_client import ResourceInspector, SemanticExplorer
    fixture = write_fixture(tmp_path / "fixture.json")
    candidate = NS(code="1")
    monkeypatch.setattr(SemanticExplorer, "search_authority", lambda *a, **k: NS(status="OK", matches=[candidate]))
    monkeypatch.setattr(SemanticExplorer, "documentation", lambda *a: {"text/html": "https://example.invalid"})
    monkeypatch.setattr(SemanticExplorer, "resolve_candidate", lambda *a: NS(urn="TEST:1"))
    monkeypatch.setattr(SemanticExplorer, "parents", lambda *a: [NS(urn="fixture:Parent")])
    monkeypatch.setattr(SemanticExplorer, "children", lambda *a: [NS(urn="fixture:Child")])
    monkeypatch.setattr(SemanticExplorer, "is_a", lambda *a: True)
    monkeypatch.setattr(SemanticExplorer, "satisfiable", lambda *a: True)
    monkeypatch.setattr(ResourceInspector, "retrieve", lambda *a: NS(urn="fixture.model", selection="advertised"))
    assert tool.discover(Owner, fixture)["selected_code"] == "1"
    monkeypatch.setattr(SemanticExplorer, "search_authority", lambda *a, **k: NS(status="OK", matches=[candidate, candidate]))
    with pytest.raises(AssertionError, match="ambiguous"):
        tool.discover(Owner, fixture)


def test_batch_report_binds_identity_and_preserves_failures(tmp_path, monkeypatch):
    fixture = write_fixture(tmp_path / "fixture.json")
    identity = {"candidate_sha": "a" * 40}
    monkeypatch.setattr(tool, "source_identity", lambda: identity)
    monkeypatch.setattr(tool, "discover", lambda *a: {"model_urn": "fixture.model"})
    directory = tmp_path / "run"
    def run(*args, **kwargs):
        directory.mkdir()
        return dict(batch=NS(dumps=lambda: '{"outcomes":[]}', outcomes=[]), matrix_verified=False, setup_seconds=.1)
    monkeypatch.setattr(tool, "run_matrix", run)
    result = tool.run_case_batch(Owner, fixture, directory, workers=1)
    assert result["matrix_verified"] is False and result["scientific_acceptance"] is False
    assert tool.read_json(directory / "run.json")[0]["fixture_sha256"] == fixture.sha256


def test_isolated_worker_fails_safely_without_prerequisites(tmp_path):
    worker = tool.PACKAGE / "tools/restore_milestone_results.py"
    process = subprocess.run([sys.executable, "-I", str(worker), "--fixture", str(tmp_path / "missing"),
        "--batch", str(tmp_path / "batch"), "--output", str(tmp_path / "output")],
        capture_output=True, text=True, timeout=30, env={k: v for k, v in os.environ.items() if not k.startswith("KLAB_MILESTONE_")})
    assert process.returncode == 1 and "Restoration failed: KeyError" in process.stdout
    assert "Traceback" not in process.stderr and not (tmp_path / "output").exists()


def test_throughput_failure_does_not_publish_success_rate(tmp_path):
    from run_milestone_throughput import measure, summarize
    outcomes = [dict(state="succeeded", elapsed=i + 1, cleanup_elapsed=.1) for i in range(4)]
    record = dict(matrix_verified=True, run_id="one", batch=dict(outcomes=outcomes), processed={
        str(i): dict(raster=dict(verified_cells=4), exported_bytes=80, stages_seconds={"export": .2}) for i in range(4)}, setup_seconds=.1)
    summary = summarize([record] * 5, 10)
    assert summary["passed"] and summary["completed"] == 20 and summary["successful_experiments_per_minute"] == 120
    assert summary["latency_p95_seconds"] == 4 and summary["exported_bytes"] == 1600
    assert summarize([record], 1)["successful_experiments_per_minute"] is None
    def fail(*args, **kwargs):
        raise RuntimeError("private failure text must never be serialized")
    result = measure(None, None, tmp_path / "failed", workers=4, operation=fail)
    assert not result["passed"] and result["unrun_batches"] == 4 and result["attempted"] == 4
    assert "private failure" not in (tmp_path / "failed/summary.json").read_text()
    result = measure(None, None, tmp_path / "success", workers=1, operation=lambda *a, **k: record)
    assert result["passed"] and result["unrun_batches"] == 0 and result["stages_seconds"]["export"]["samples"] == 20


def test_builder_rejects_missing_evidence_and_never_labels_ready(tmp_path):
    from build_milestone_deliverable import build
    status = build(tmp_path / "missing", tmp_path / "missing-throughput", tmp_path / "blocked")
    assert status == 1 and tool.read_json(tmp_path / "blocked/delivery.json")[0]["status"] == "blocked"


@pytest.mark.parametrize("name", ["../private.json", "/private.json", "C:/private.json", "sequential/../../secret", "sequential\\file"])
def test_builder_rejects_path_escape(tmp_path, name):
    from build_milestone_deliverable import checked_file
    with pytest.raises(AssertionError):
        checked_file(tmp_path, name, "a" * 64)


def test_builder_hash_and_artifact_whitelist(tmp_path):
    from build_milestone_deliverable import allowed_artifact, checked_file
    path = tmp_path / "field"
    path.write_bytes(b"fixture")
    assert checked_file(tmp_path, "field", tool.digest(b"fixture")) == path
    with pytest.raises(AssertionError):
        checked_file(tmp_path, "field", "a" * 64)
    assert allowed_artifact("notebook/executed.ipynb") and allowed_artifact("sequential/m-fine.tif")
    assert not allowed_artifact("scientist.json") and not allowed_artifact("notebook/private.cert")


def test_notebook_source_uses_real_shared_workflows():
    from record_notebook_demos import read_cells
    cells = read_cells(tool.PACKAGE / "examples/notebooks/08_discover_compare_explain.py")
    code = "\n".join(c.source for c in cells if c.kind == "code")
    assert "run_case_batch" in code and "launch_restore" in code and "open_raster" in code
    assert "source_sha256=" not in code and "allow_owned_work=True" in code


def acceptance_evidence(directory, identity):
    """Synthetic validator input only; never published as executed scientific evidence."""
    from build_milestone_deliverable import REQUIRED_TESTS
    directory.mkdir()
    files = {
        "sequential/run.json": dict(identity=identity, fixture_sha256="a" * 64, matrix_verified=True,
            run_id="sequential", batch=dict(outcomes=[dict(result_handles=[dict(id=i)]) for i in range(4)])),
        "parallel/run.json": dict(identity=identity, fixture_sha256="a" * 64, matrix_verified=True,
            batch=dict(outcomes=[{}] * 4)),
        "restored/restoration.json": dict(identity=identity, fixture_sha256="a" * 64,
            results=[dict(observation_handle=dict(id=i)) for i in range(4)]),
        "notebook/notebook-run.json": dict(identity=identity, fixture_sha256="a" * 64, matrix_verified=True, run_id="notebook"),
        "notebook/executed.ipynb": dict(cells=[dict(cell_type="code", execution_count=1, outputs=[])]),
    }
    hashes = {}
    for name, value in files.items():
        path = directory / name
        path.parent.mkdir(parents=True, exist_ok=True)
        tool.write_json(path, value)
        hashes[name] = tool.digest(path.read_bytes())
    (directory / "notebook/notebook.html").write_text("<html>synthetic validator input</html>")
    hashes["notebook/notebook.html"] = tool.digest((directory / "notebook/notebook.html").read_bytes())
    report = dict(version=1, kind="milestone-acceptance", identity=identity, fixture_sha256="a" * 64,
                  run_id="acceptance", passed=True, deployment_verified=True,
                  tests={name: "passed" for name in REQUIRED_TESTS}, artifacts=hashes)
    tool.write_json(directory / "acceptance.json", report)
    return report


def test_builder_acceptance_matches_candidate_and_each_original_artifact(tmp_path, monkeypatch):
    from build_milestone_deliverable import validate_acceptance
    identity = dict(candidate_sha="a" * 40, dirty=False)
    monkeypatch.setattr(tool, "source_identity", lambda: identity)
    report = acceptance_evidence(tmp_path / "run", identity)
    assert validate_acceptance(tmp_path / "run/acceptance.json", require_clean=True) == report
    (tmp_path / "run/notebook/notebook.html").write_text("tampered")
    with pytest.raises(AssertionError, match="hash"):
        validate_acceptance(tmp_path / "run/acceptance.json")


@pytest.mark.parametrize("fault", ["identity", "dirty", "tests", "passed", "artifacts", "unrecognized", "batch",
                                  "restore", "notebook", "unexecuted", "version"])
def test_fail_closed_acceptance_gates(tmp_path, monkeypatch, fault):
    from build_milestone_deliverable import validate_acceptance
    identity = dict(candidate_sha="a" * 40, dirty=False)
    monkeypatch.setattr(tool, "source_identity", lambda: identity)
    root = tmp_path / "run"
    report = acceptance_evidence(root, identity)
    if fault == "identity":
        report["identity"] = {"candidate_sha": "different"}
    elif fault == "dirty":
        identity["dirty"] = True
        report["identity"] = identity
    elif fault in ("tests", "artifacts"):
        report[fault] = {}
    elif fault == "passed":
        report["passed"] = False
    elif fault == "version":
        report["version"] = 2
    elif fault == "unrecognized":
        report["artifacts"]["scientist.json"] = "a" * 64
    else:
        name = {"batch": "parallel/run.json", "restore": "restored/restoration.json",
                "notebook": "notebook/notebook-run.json", "unexecuted": "notebook/executed.ipynb"}[fault]
        value, _ = tool.read_json(root / name)
        if fault == "batch":
            value["matrix_verified"] = False
        elif fault == "restore":
            value["results"][0]["observation_handle"] = {"id": 999}
        elif fault == "notebook":
            value["run_id"] = "sequential"
        else:
            value["cells"][0]["execution_count"] = None
        (root / name).write_text(json.dumps(value))
        report["artifacts"][name] = tool.digest((root / name).read_bytes())
    (root / "acceptance.json").write_text(json.dumps(report))
    with pytest.raises(AssertionError):
        validate_acceptance(root / "acceptance.json", require_clean=True)


def test_builder_requires_verified_deployment_and_all_throughput_configurations(tmp_path, monkeypatch):
    from build_milestone_deliverable import build
    identity = dict(candidate_sha="a" * 40, dirty=False)
    monkeypatch.setattr(tool, "source_identity", lambda: identity)
    root = tmp_path / "run"
    acceptance_evidence(root, identity)
    configurations = {f"{size}-workers-{workers}": dict(passed=True, attempted=20, completed=20,
        failed_or_unknown=0, wall_seconds=10, successful_experiments_per_minute=120)
        for size in ("correctness-grid", "larger-grid") for workers in (1, 4)}
    throughput = dict(kind="milestone-throughput", passed=True, identity=identity, acceptance_run_id="acceptance",
                      fixture_sha256="a" * 64, results=configurations)
    tool.write_json(tmp_path / "throughput.json", throughput)
    assert build(root / "acceptance.json", tmp_path / "throughput.json", tmp_path / "package") == 0
    assert tool.read_json(tmp_path / "package/delivery.json")[0]["status"] == "ready-for-review"
    import build_milestone_deliverable as builder
    original_copy = builder.shutil.copyfile
    def corrupt_copy(source, destination):
        original_copy(source, destination)
        Path(destination).write_bytes(b"changed during copying")
    with monkeypatch.context() as copying:
        copying.setattr(builder.shutil, "copyfile", corrupt_copy)
        assert build(root / "acceptance.json", tmp_path / "throughput.json", tmp_path / "corrupt-package") == 1
    assert tool.read_json(tmp_path / "corrupt-package/delivery.json")[0]["status"] == "blocked"
    report, _ = tool.read_json(root / "acceptance.json")
    report["deployment_verified"] = False
    (root / "acceptance.json").write_text(json.dumps(report))
    assert build(root / "acceptance.json", tmp_path / "throughput.json", tmp_path / "blocked") == 1


def test_restore_helpers_never_resubmit_and_bind_child_identity(tmp_path, monkeypatch):
    from workflows import discover_compare_explain as flow
    fixture_path, batch_path = tmp_path / "fixture.json", tmp_path / "batch.json"
    fixture = write_fixture(fixture_path)
    batch_path.write_text("{}")
    monkeypatch.setattr(tool, "source_identity", lambda: {"candidate_sha": "fixture"})
    monkeypatch.setattr(tool, "factory_from_paths", lambda *a, **k: Owner)
    def restore(factory, cases, encoded, directory, **kwargs):
        Path(directory).mkdir()
        return ({"original_identity": 1},)
    monkeypatch.setattr(flow, "restore_matrix", restore)
    report = tool.restore_in_process(fixture_path, batch_path, tmp_path / "restored", "profile", "certificate")
    assert report["results"] == ({"original_identity": 1},) and report["fixture_sha256"] == fixture.sha256
    def child(arguments, **kwargs):
        directory = Path(arguments[-1])
        directory.mkdir()
        tool.write_json(directory / "restoration.json", {**report, "pid": os.getpid() + 1})
        assert arguments[1] == "-I" and "KLAB_MILESTONE_CERTIFICATE" in kwargs["env"]
        return NS(returncode=0)
    monkeypatch.setattr(tool.subprocess, "run", child)
    assert tool.launch_restore(fixture_path, batch_path, tmp_path / "child", "profile", "certificate")["pid"] != os.getpid()


def test_acceptance_adapter_builds_actual_case_status_manifest(tmp_path, monkeypatch):
    import run_milestone_acceptance as adapter
    from build_milestone_deliverable import REQUIRED_TESTS
    fixture_path = tmp_path / "fixture.json"
    write_fixture(fixture_path)
    output, report = tmp_path / "output", tmp_path / "tests.xml"
    monkeypatch.setattr(tool, "source_identity", lambda: {"candidate_sha": "fixture"})
    def run():
        output.mkdir()
        report.write_text("<testsuite>" + "".join(f'<testcase name="{name}"/>' for name in REQUIRED_TESTS) + "</testsuite>")
        tool.write_json(output / "test.json", {"test": True})
        (output / "private.cert").write_text("must never be included")
        return 0
    monkeypatch.setattr(adapter, "run", run)
    monkeypatch.setattr(sys, "argv", ["adapter", "--report", str(report), "--output-dir", str(output),
                                     "--milestone-fixture", str(fixture_path)])
    assert adapter.main() == 0
    manifest, _ = tool.read_json(output / "acceptance.json")
    assert manifest["passed"] and manifest["deployment_verified"] is False
    assert "private.cert" not in manifest["artifacts"]


def test_throughput_adapter_requires_explicit_work_authorization(monkeypatch):
    import run_milestone_throughput as runner
    monkeypatch.setattr(sys, "argv", ["throughput", *[arg for flag in ("--acceptance", "--milestone-fixture", "--large-fixture",
        "--profile", "--certificate", "--output") for arg in (flag, "unused")]])
    with pytest.raises(SystemExit) as error:
        runner.main()
    assert error.value.code == 2


def test_throughput_adapter_measures_both_sizes_and_recovers(tmp_path, monkeypatch):
    from dataclasses import replace
    import run_milestone_throughput as runner
    fixture = write_fixture(tmp_path / "fixture.json")
    large = replace(fixture, sha256="d" * 64, cases=tuple(replace(c, oracle=replace(c.oracle,
        width=c.oracle.width * 2, height=c.oracle.height * 2,
        transform=(c.oracle.transform[0] / 2, 0, c.oracle.transform[2], 0, c.oracle.transform[4] / 2, c.oracle.transform[5]),
        values=("0",) * (c.oracle.width * c.oracle.height * 4))) for c in fixture.cases))
    monkeypatch.setattr(runner, "validate_acceptance", lambda path: dict(fixture_sha256=fixture.sha256, run_id="accepted"))
    monkeypatch.setattr(tool, "load_fixture", lambda path: large if str(path) == "large" else fixture)
    monkeypatch.setattr(tool, "source_identity", lambda: {"candidate_sha": "fixture"})
    monkeypatch.setattr(tool, "factory_from_paths", lambda *a, **k: Owner)
    monkeypatch.setattr(tool, "launch_restore", lambda *a: {"run_id": "recovered"})
    monkeypatch.setattr(runner, "measure", lambda *a, **k: dict(passed=True, wall_seconds=1))
    arguments = ["runner", "--acceptance", "acceptance", "--milestone-fixture", "small", "--large-fixture", "large",
        "--profile", "profile", "--certificate", "private", "--output", str(tmp_path / "throughput"),
        "--allow-owned-work", "--allow-throughput"]
    monkeypatch.setattr(sys, "argv", arguments)
    assert runner.main() == 0
    result, _ = tool.read_json(tmp_path / "throughput/throughput.json")
    assert len(result["results"]) == 4 and result["passed"]
    def fail(*a):
        raise AssertionError("recovery failed")
    monkeypatch.setattr(tool, "launch_restore", fail)
    arguments[arguments.index("--output") + 1] = str(tmp_path / "recovery-failed")
    monkeypatch.setattr(sys, "argv", arguments)
    assert runner.main() == 1
    result, _ = tool.read_json(tmp_path / "recovery-failed/throughput.json")
    assert all(r["successful_experiments_per_minute"] is None for r in result["results"].values())
    monkeypatch.setattr(runner, "measure", lambda *a, **k: dict(passed=False, wall_seconds=1))
    arguments[arguments.index("--output") + 1] = str(tmp_path / "compute-failed")
    monkeypatch.setattr(sys, "argv", arguments)
    assert runner.main() == 1


@pytest.mark.parametrize("fault", ["failed", "missing", "duplicate", "doctype"])
def test_acceptance_adapter_keeps_failures_and_rejects_unsafe_xml(tmp_path, monkeypatch, fault):
    import run_milestone_acceptance as adapter
    fixture_path = tmp_path / "fixture.json"
    write_fixture(fixture_path)
    output, report = tmp_path / "output", tmp_path / "tests.xml"
    monkeypatch.setattr(tool, "source_identity", lambda: {"candidate_sha": "fixture"})
    def run():
        output.mkdir()
        if fault != "missing":
            report.write_text({"failed": '<testsuite><testcase name="failed"><failure/></testcase></testsuite>',
                "duplicate": '<testsuite><testcase name="x"/><testcase name="x"/></testsuite>',
                "doctype": '<!DOCTYPE x><testsuite/>'}[fault])
        return 1
    monkeypatch.setattr(adapter, "run", run)
    monkeypatch.setattr(sys, "argv", ["adapter", "--report", str(report), "--output-dir", str(output),
                                     "--milestone-fixture", str(fixture_path)])
    if fault in ("duplicate", "doctype"):
        with pytest.raises(ValueError):
            adapter.main()
    else:
        assert adapter.main() == 1


def test_cli_main_guards_execute_without_network(tmp_path, monkeypatch):
    import runpy
    import run_functional_acceptance
    import build_milestone_deliverable as builder
    monkeypatch.setattr(sys, "argv", ["builder", "--acceptance", "missing", "--throughput", "missing",
                                     "--output", str(tmp_path / "blocked")])
    with pytest.raises(SystemExit) as status:
        runpy.run_path(str(tool.PACKAGE / "tools/build_milestone_deliverable.py"), run_name="__main__")
    assert status.value.code == 1


def test_memory_sampler_optional_dependency_is_honest(monkeypatch):
    import builtins
    from run_milestone_throughput import PythonMonitor
    original = builtins.__import__
    def no_psutil(name, *args, **kwargs):
        if name == "psutil":
            raise ImportError("not installed")
        return original(name, *args, **kwargs)
    monkeypatch.setattr(builtins, "__import__", no_psutil)
    with PythonMonitor() as monitor:
        pass
    assert monitor.report()["collected"] is False and monitor.report()["peak_rss_bytes"] is None


def test_restore_worker_entry_points_do_not_expose_failure_details(tmp_path, monkeypatch):
    import restore_milestone_results as worker
    import run_functional_acceptance
    import runpy
    monkeypatch.setattr(sys, "argv", ["worker", "--fixture", "fixture", "--batch", "batch", "--output", "output"])
    monkeypatch.setenv("KLAB_MILESTONE_PROFILE", "profile")
    monkeypatch.setenv("KLAB_MILESTONE_CERTIFICATE", "private")
    monkeypatch.setattr(worker, "restore_in_process", lambda *a: None)
    assert worker.main() == 0
    monkeypatch.setattr(tool, "restore_in_process", lambda *a: None)
    with pytest.raises(SystemExit) as error:
        runpy.run_path(str(tool.PACKAGE / "tools/restore_milestone_results.py"), run_name="__main__")
    assert error.value.code == 0
    def fail(*args):
        raise ValueError("private failure text")
    monkeypatch.setattr(worker, "restore_in_process", fail)
    assert worker.main() == 1
    monkeypatch.setattr(sys, "argv", ["throughput", *[arg for flag in ("--acceptance", "--milestone-fixture", "--large-fixture",
        "--profile", "--certificate", "--output") for arg in (flag, "unused")]])
    with pytest.raises(SystemExit) as status:
        runpy.run_path(str(tool.PACKAGE / "tools/run_milestone_throughput.py"), run_name="__main__")
    assert status.value.code == 2
    fixture_path = tmp_path / "fixture.json"
    write_fixture(fixture_path)
    monkeypatch.setattr(tool, "source_identity", lambda: {"candidate_sha": "fixture"})
    monkeypatch.setattr(run_functional_acceptance, "main", lambda: 1)
    monkeypatch.setattr(sys, "argv", ["adapter", "--report", str(tmp_path / "no-report"), "--output-dir", str(tmp_path / "acceptance"),
                                     "--milestone-fixture", str(fixture_path)])
    with pytest.raises(SystemExit) as status:
        runpy.run_path(str(tool.PACKAGE / "tools/run_milestone_acceptance.py"), run_name="__main__")
    assert status.value.code == 1
