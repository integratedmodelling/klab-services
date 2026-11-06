"""Governance invariants, not Java compatibility/science acceptance."""
import copy
import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace

import pytest
from jsonschema import ValidationError

FILE = Path(__file__).resolve().parents[1] / "govern.py"
SPEC = importlib.util.spec_from_file_location("gap_governance", FILE)
govern = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(govern)


def test_complete_registry_and_exact_original_acceptance_crosswalk():
    registry, audit, issues = govern.validate()
    assert len(registry["gaps"]) == 16
    assert len(audit["records"]) == 42
    assert len(issues["issues"]) == 17
    assert all(g["current_real_result"] == "REAL_NOT_RUN" for g in registry["gaps"])
    assert not any(i["closure_candidate"] for i in issues["issues"])
    assert {r["acceptance_id"] for r in audit["records"] if r["implementation_status"] == "OUT_OF_SCOPE"} == {"RC-P5", "CAP-17"}


@pytest.mark.parametrize("change", ["duplicate-id", "duplicate-case", "missing-reserved", "unknown-row", "unknown-scenario", "missing-case-coverage", "missing-required", "invalid-status"])
def test_registry_rejects_traceability_and_schema_corruption(change):
    registry = govern.expanded()
    gap = registry["gaps"][0]
    if change == "duplicate-id":
        registry["gaps"][1]["id"] = gap["id"]
    elif change == "duplicate-case":
        registry["gaps"][1]["contract_case"] = gap["contract_case"]
    elif change == "missing-reserved":
        registry["gaps"].pop()
    elif change == "unknown-row":
        gap["requirement_ids"] = ["RC-NONEXISTENT"]
    elif change == "unknown-scenario":
        gap["simulator_scenario_refs"] = ["SIM-FABRICATED-001"]
    elif change == "missing-case-coverage":
        for item in registry["gaps"]:
            item["simulator_scenario_refs"] = []
    elif change == "missing-required":
        del gap["resolution_criteria"]
    else:
        gap["java_status"] = "FIXED_BY_SIMULATOR"
    with pytest.raises((ValueError, ValidationError)):
        govern.validate(registry)


@pytest.mark.parametrize("field,value", [("java_status", "JAVA_FIXED_VERIFIED"), ("lifecycle", "REAL_VERIFIED"), ("lifecycle", "CLOSED"), ("current_real_result", "REAL_PASS")])
def test_source_or_simulation_cannot_close_or_verify_java(field, value):
    registry = govern.expanded()
    gap = registry["gaps"][2]
    gap[field] = value
    assert not gap["real_java_test_refs"] and gap["source_build_or_commit"] is None
    with pytest.raises(ValueError):
        govern.validate(registry)


def test_unknown_route_version_and_first_observation_stay_explicit_null():
    registry, _, _ = govern.validate()
    gap = next(g for g in registry["gaps"] if g["id"] == "JAVA-GAP-0007")
    assert gap["api_contract"]["path"] is None
    assert gap["source_build_or_commit"] is None and gap["first_observed"] is None


@pytest.mark.parametrize("change", ["missing-row", "duplicate-row", "changed-requirement", "invented-gap", "wrong-commit"])
def test_acceptance_audit_never_drops_or_rewrites_original_requirement(change):
    audit = govern.load(govern.DATA / "acceptance-audit.json")
    if change == "missing-row":
        audit["records"].pop()
    elif change == "duplicate-row":
        audit["records"][-1] = copy.deepcopy(audit["records"][0])
    elif change == "changed-requirement":
        audit["records"][0]["original_requirement"] = "A weakened substitute"
    elif change == "invented-gap":
        audit["records"][0]["java_gap_ids"] = ["JAVA-GAP-9999"]
    else:
        audit["records"][0]["commits"] = ["0" * 40]
    with pytest.raises(ValueError):
        govern.validate(audit=audit)


def test_original_product_issues_are_not_closed_by_register_migration():
    issues = govern.load(govern.DATA / "issues.json")
    issues["issues"][0]["closure_candidate"] = True
    with pytest.raises(ValueError, match="No fully justified closure"):
        govern.validate(issues=issues)


def test_no_arbitrary_real_command_execution_from_registry():
    registry = govern.expanded()
    registry["gaps"][0]["real_command"] = ["python", "unreviewed-real-call.py"]
    with pytest.raises(ValueError, match="arbitrary commands prohibited"):
        govern.validate(registry)


def test_real_mode_reports_not_run_and_never_calls_subprocess_or_simulator(tmp_path, monkeypatch):
    monkeypatch.setattr(govern.subprocess, "run", lambda *a, **k: pytest.fail("Real mode must not execute unreviewed operations"))
    output = tmp_path / "real.json"
    assert govern.contracts("real", output) == 3
    result = json.loads(output.read_text())
    assert len(result["cases"]) == 16 and result["fallbacks"] == 0
    assert {c["tag"] for c in result["cases"]} == {"REAL_NOT_RUN"}


@pytest.mark.parametrize("returncode,skipped,expected", [(0, 0, 0), (0, 1, 1), (1, 0, 1)])
def test_substitute_failure_or_skip_cannot_turn_into_real_pass(tmp_path, monkeypatch, returncode, skipped, expected):
    def run(command, **kwargs):
        xml = Path(next(x.split("=", 1)[1] for x in command if x.startswith("--junitxml=")))
        xml.write_text(f'<testsuites><testsuite tests="1" failures="0" errors="0" skipped="{skipped}"/></testsuites>')
        return SimpleNamespace(returncode=returncode)
    monkeypatch.setattr(govern.subprocess, "run", run)
    output = tmp_path / "sim.json"
    assert govern.contracts("simulated", output, ["JAVA-GAP-0004"]) == expected
    result = json.loads(output.read_text())
    assert all(c["current_real"] == "REAL_NOT_RUN" for c in result["cases"])
    assert result["cases"][0]["tag"] == (None if expected else "SIMULATED_PASS")


def test_json_duplicate_keys_paths_and_test_symbols_fail_closed():
    with pytest.raises(ValueError):
        json.loads('{"id":1,"id":2}', object_pairs_hook=govern.unique)
    with pytest.raises(ValueError):
        govern.local_ref("../../outside-checkout")
    with pytest.raises(ValueError):
        govern.test_ref("developer-tools/java-gap-governance/tests/test_governance.py::invented_case")


def test_sdk_and_http_simulator_cases_have_separate_pytest_import_namespaces(tmp_path, monkeypatch):
    calls = []
    def run(command, **kwargs):
        calls.append(command)
        refs = [arg for arg in command if arg.endswith(".py") or ".py::" in arg]
        assert all(ref.startswith("python-client/") for ref in refs) or all(ref.startswith("developer-tools/klab-service-simulator/") for ref in refs)
        xml = Path(next(x.split("=", 1)[1] for x in command if x.startswith("--junitxml=")))
        xml.write_text('<testsuites><testsuite tests="1" failures="0" errors="0" skipped="0"/></testsuites>')
        return SimpleNamespace(returncode=0, stdout="explicit pipe", stderr="")
    monkeypatch.setattr(govern.subprocess, "run", run)
    output = tmp_path / "sim.json"
    assert govern.contracts("simulated", output, ["JAVA-GAP-0005"]) == 0
    assert len(calls) == 2
    assert set(json.loads(output.read_text())["groups"]) == {"sdk", "simulator"}


def test_generated_reports_do_not_diverge():
    govern.generate(check=True)
