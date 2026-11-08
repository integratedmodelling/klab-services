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


@pytest.fixture(autouse=True)
def isolate_receipt_provenance_from_mocked_test_runner(monkeypatch):
    # Mock runner tests are not real executions or provenance receipts.
    monkeypatch.setattr(govern, 'repository_commit', lambda: 'MOCK_TEST_ONLY')


def test_complete_registry_and_exact_original_acceptance_crosswalk():
    registry, audit, issues = govern.validate()
    assert {f"JAVA-GAP-{number:04}" for number in range(1, 17)} <= {gap["id"] for gap in registry["gaps"]}
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
@pytest.mark.parametrize("source_baseline", [None, "Pinned repository source only; NOT a native Java execution"])
def test_source_or_simulation_cannot_close_or_verify_java(field, value, source_baseline):
    registry = govern.expanded()
    gap = next(g for g in registry["gaps"] if g["id"] == "JAVA-GAP-0003")
    # Test both missing and supplied source authority. Adding a legitimate source
    # baseline to the live registry must never turn it into real-execution proof.
    gap["source_build_or_commit"] = source_baseline
    gap[field] = value
    assert not gap["real_java_test_refs"] and gap["real_command"] is None
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
    assert len(result["cases"]) == len(govern.expanded()["gaps"]) and result["fallbacks"] == 0
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


def test_individual_reproductions_cover_all_stable_gaps_and_native_boundaries():
    for gap in govern.expanded()["gaps"]:
        path = govern.DATA / "reproductions" / (gap["id"] + ".md")
        document = path.read_text(encoding="utf-8")
        assert document == govern.reproduction_document(gap)
        assert gap["observed_java_behavior"] in document
        assert gap["simulator_behavior"] in document
        assert f"--mode simulated --gap {gap['id']}" in document
        assert f"--mode real --gap {gap['id']}" in document
        assert "REAL_NOT_RUN" in document and "exits3 without network calls" in document
        assert "Required native startup prerequisites" in document
        assert "not** an actual native test" in document
        assert all(requirement in document for requirement in gap["requirement_ids"])


def test_reproduction_generation_never_invents_unknown_native_build_or_endpoint():
    gap = next(g for g in govern.expanded()["gaps"] if g["id"] == "JAVA-GAP-0007")
    document = govern.reproduction_document(gap)
    assert "UNKNOWN_PENDING_REFERENCE" in document
    assert "NOT_RUN / exact native build and environment not established" in document
    assert "no reviewed adapter/build/permission exists, retain NOT_RUN" in document


def test_maintainer_handoff_has_each_gap_once_and_current_status_triplet():
    registry = govern.expanded()
    path = govern.DATA / "JAVA_GAPS_FOR_FERDINANDO.md"
    document = path.read_text(encoding="utf-8")
    assert govern.canonical_check(registry)["canonical_records"] == len(registry["gaps"])
    for gap in registry["gaps"]:
        assert document.count("### " + gap["id"] + "\n") == 1
    for preserved in ("Executive summary", "EPSG:4269", "NAVD88", "RESOLVED_SETUP",
                       "NullScannerReproducer.java", "REAL_NOT_RUN"):
        assert preserved in document


@pytest.mark.parametrize("change", ["missing-id", "duplicate-id", "title", "native", "simulator", "execution", "link", "selector"])
def test_canonical_drift_is_rejected(change):
    text = (govern.DATA / 'JAVA_GAPS_FOR_FERDINANDO.md').read_text(encoding='utf-8')
    if change == 'missing-id':
        text = text.replace('### JAVA-GAP-0001\n', '### removed\n')
    elif change == 'duplicate-id':
        text += '\n### JAVA-GAP-0001\n'
    elif change == 'title':
        text = text.replace('**Title:** Resources remote numeric', '**Title:** Wrong numeric')
    elif change == 'native':
        text = text.replace('**Native status:** CONFIRMED_DEFECT', '**Native status:** NATIVE_WORKS_VERIFIED', 1)
    elif change == 'simulator':
        text = text.replace('**Simulator status:** SIMULATOR_PASS', '**Simulator status:** SIMULATOR_FAIL', 1)
    elif change == 'execution':
        text = text.replace('**Native execution status:** REAL_NOT_RUN', '**Native execution status:** REAL_PASS', 1)
    elif change == 'link':
        text += '\n[bad](missing-file.md)\n'
    else:
        text += '\n`python-client/tests/unit/test_connection.py::invented_test`\n'
    with pytest.raises(ValueError):
        govern.canonical_check(govern.expanded(), text)


def test_handoff_generation_preserves_narrative_and_refuses_missing_or_duplicate_markers():
    registry = govern.expanded()
    text = "Seed before\n" + govern.HANDOFF_BEGIN + "\nold counts\n" + govern.HANDOFF_END + "\nSeed after\n"
    output = govern.handoff_document(registry, text)
    assert output.startswith("Seed before\n") and output.endswith("\nSeed after\n")
    assert "old counts" not in output and "|JAVA_FIXED_VERIFIED|0|" in output
    for broken in ("no markers", text + govern.HANDOFF_BEGIN):
        with pytest.raises(ValueError, match="marker pair"):
            govern.handoff_document(registry, broken)


def test_handoff_counts_are_derived_from_registry_not_frozen_sixteen_snapshot():
    registry = govern.expanded()
    registry["gaps"] = registry["gaps"][:1]
    text = govern.handoff_inventory(registry)
    assert "Registry entries: **1**" in text
    assert "|CONFIRMED_DEFECT|1|" in text and "|MISSING_API|0|" in text


def test_current_worker_scope_does_not_rewrite_historical_c_exclusion():
    _, audit, issues = govern.validate()
    current = govern.load(govern.local_ref(issues["current_campaign_review_ref"]))["issues"]
    worker = next(row for row in current if row["number"] == 127)
    assert worker["disposition"] == "OPEN_OTHER" and worker["java_gap_ids"] == ["JAVA-GAP-0017"]
    assert "now in implementation scope" in worker["reason"]
    assert all(row["implementation_status"] == "OUT_OF_SCOPE" for row in audit["records"] if row["acceptance_id"] in ("RC-P5", "CAP-17"))


@pytest.mark.parametrize('change', ['missing-receipt', 'wrong-receipt', 'missing-time', 'native-with-source-only'])
def test_independent_execution_status_requires_real_receipt_authority(change):
    registry = govern.expanded()
    gap = registry['gaps'][0]
    if change == 'missing-receipt':
        del gap['simulator_receipt']
    elif change == 'wrong-receipt':
        gap['simulator_receipt'] = 'docs/java-service-contract-gaps/issues.json'
    elif change == 'missing-time':
        del gap['simulator_verified_utc']
    else:
        gap['native_execution_status'] = 'REAL_PASS'
        gap['current_real_result'] = 'REAL_PASS'
    with pytest.raises(ValueError):
        govern.validate(registry)


def test_canonical_summary_count_drift_is_rejected():
    text = (govern.DATA / 'JAVA_GAPS_FOR_FERDINANDO.md').read_text(encoding='utf-8')
    with pytest.raises(ValueError, match='summary counts'):
        govern.canonical_check(govern.expanded(), text.replace('|CONFIRMED_DEFECT|2|', '|CONFIRMED_DEFECT|99|'))
