"""Devtool measurement custody, never executed-support certification."""
import importlib.util
import json
from pathlib import Path
import runpy
import sys

import pytest

PATH = Path(__file__).resolve().parents[2] / "tools/operation_coverage.py"


@pytest.fixture
def tool(monkeypatch):
    monkeypatch.syspath_prepend(str(PATH.parent))
    spec = importlib.util.spec_from_file_location("operation_coverage", PATH)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module


@pytest.fixture
def sample(tmp_path):
    source = tmp_path / "klab_client"; source.mkdir()
    path = source / "sample.py"
    path.write_text('''def public():
    return 1
def partial():
    if ready: return 1
    return 2
def uncalled():
    return 1
def refusal():
    raise UnsupportedOperationError("explicit")
class API:
    @abstractmethod
    def declared(self): ...
def callback_factory():
    def nested():
        return 8
    return nested
def excluded():
    return 9
def missing():
    return 10
''', encoding="utf-8")
    return source, path


def measured(path, executed=(), missing=(), excluded=()):
    return {str(path): {"executed_lines": list(executed), "missing_lines": list(missing), "excluded_lines": list(excluded)}}


def test_body_measurements_exclude_declarations_nested_callbacks_and_exclusions(tool, sample):
    source, path = sample
    value = tool.report(source, {"files": measured(path, (1, 2, 3, 4, 6, 8, 9, 13, 15, 17, 18), (5, 7, 14, 16, 20), (18,))})
    rows = {row["operation"]: row for row in value["operations"]}
    assert rows["public"]["measurement"] == "BODY_LINES_MEASURED_EXECUTED"
    assert rows["partial"]["measurement"] == "PARTIAL_BODY_LINE_MEASUREMENT"
    assert rows["uncalled"]["measurement"] == "NO_BODY_LINES_MEASURED_EXECUTED"
    assert rows["refusal"]["measurement"] == "REFUSAL_BODY_MEASURED_NOT_SUPPORTED"
    assert rows["declared"]["measurement"] == "ABSTRACT_NOT_IMPLEMENTATION"
    assert rows["callback_factory"]["executed_body_lines"] == []  # nested return15 is not outer execution.
    assert rows["callback_factory"]["missing_body_lines"] == [14, 16]
    assert rows["excluded"]["measurement"] == "NO_MEASURABLE_BODY"
    assert rows["excluded"]["excluded_body_lines"] == [18]
    assert "NOT_FUNCTIONAL_OR_NATIVE_ACCEPTANCE" in value["evidence"]
    assert len(value["source_module_sha256"]["sample.py"]) == 64


def test_missing_module_does_not_inherit_execution_from_definitions(tool, sample):
    source, _ = sample
    value = tool.report(source, {"files": {}})
    assert value["measurement_counts"]["REPORT_MODULE_MISSING"] == 7
    assert value["measurement_counts"]["ABSTRACT_NOT_IMPLEMENTATION"] == 1


def test_custody_refuses_mixed_report_duplicate_module_unsafe_path_and_byte_drift(tool, sample, tmp_path):
    source, path = sample
    for files in ({"klab_simulator/state.py": {}}, {str(path): {}, str(path.parent) + "/./" + path.name: {}},
            {"klab_client/../escape.py": {}}, {"klab_client/absent.py": {}}):
        with pytest.raises(ValueError): tool.report(source, {"files": files})
    installed = tmp_path / "installed/klab_client"; installed.mkdir(parents=True)
    mismatch = installed / "sample.py"; mismatch.write_text("def public(): return 2", encoding="utf-8")
    with pytest.raises(ValueError, match="bytes differ"): tool.report(source, {"files": measured(mismatch)})


def test_duplicate_source_callable_identity_refuses_instead_of_picking_first(tool, sample):
    source, path = sample
    path.write_text("def public(): return 1\ndef public(): return 2\n", encoding="utf-8")
    with pytest.raises(ValueError, match="Duplicate callable"):
        tool.report(source, {"files": measured(path)})


def test_single_line_definition_is_not_proof_its_body_was_invoked(tool, sample):
    source, path = sample
    path.write_text("def public(): return 1\n", encoding="utf-8")
    row = tool.report(source, {"files": measured(path, (1,))})["operations"][0]
    assert row["measurement"] == "SHARED_DECLARATION_LINE_NO_BODY_EXECUTION_INFERENCE"
    assert row["executed_body_lines"] == [] and row["shared_declaration_body_line"] == 1


def test_cli_receipt_pins_report_hash_and_works_without_sdk_import(tool, sample, tmp_path, monkeypatch, capsys):
    source, path = sample
    report = tmp_path / "coverage.json"; report.write_text(json.dumps({"files": measured(path, (2,))}), encoding="utf-8")
    output = tmp_path / "receipt.json"
    arguments = [str(report), "--source", str(source), "--output", str(output)]
    assert tool.main(arguments) == 0
    value = json.loads(output.read_text())
    assert len(value["coverage_report_sha256"]) == 64
    assert json.loads(capsys.readouterr().out)["BODY_LINES_MEASURED_EXECUTED"] == 1
    monkeypatch.setattr(sys, "argv", [str(PATH), *arguments])
    with pytest.raises(SystemExit) as status: runpy.run_path(str(PATH), run_name="__main__")
    assert status.value.code == 0
