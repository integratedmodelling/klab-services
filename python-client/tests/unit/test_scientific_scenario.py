"""L1 validation/reporting only; no real-Java or L4 acceptance implied."""
import copy
import hashlib

import pytest

from scientific_scenario import AXES, ScenarioError, load, record, validate


@pytest.fixture
def contract(tmp_path):
    oracle = tmp_path / "oracle.json"
    oracle.write_text('{"value": 1}')
    return {"schema_version": 1, "id": "fixture-test", "name": "Independent fixture question",
        "claim_level": "L4", "capabilities": ["CAP-09", "RC-P2.1"],
        "source": {"publication_or_contract": "Reviewed test contract", "source_revision": "NOT_VERIFIED"},
        "fixture": {"input_files": [{"path": "oracle.json", "sha256": hashlib.sha256(oracle.read_bytes()).hexdigest()}],
            "oracle_file": "oracle.json", "oracle_origin": "independently-authored-reference",
            "expected_units": "m", "crs": "EPSG:3857", "temporal_domain": "initialization"},
        "stack": dict.fromkeys(("java_revision", "worldview_component_ids", "python_wheel_sha256"), "runtime-recorded"),
        "execution": {"operation": "nonuniform-export", "side_effect_policy": "owned-resources-only",
            "max_seconds": 900, "max_contexts": 1, "max_jobs": 2, "max_bytes": 1048576},
        "checks": [{"expectation_id": "values", "type": "numerical", "tolerance_basis": "Exact binary fraction"}]}


def test_valid_contract_does_not_execute_or_claim_acceptance(contract, tmp_path):
    assert validate(contract, tmp_path) == contract
    assert "status" not in contract


@pytest.mark.parametrize("mutation", ["missing-file", "changed-hash", "missing-reference", "traversal", "absolute",
    "oracle-not-hashed", "fake-runtime", "unsupported-operation", "unbounded", "bool-version", "l5", "duplicate-check",
    "no-tolerance", "bad-units", "bad-capability", "duplicate-input"])
def test_invalid_contract_fails_before_execution(contract, tmp_path, mutation):
    c = copy.deepcopy(contract)
    if mutation == "missing-file":
        (tmp_path / "oracle.json").unlink()
    elif mutation == "changed-hash":
        (tmp_path / "oracle.json").write_text("changed")
    elif mutation == "missing-reference":
        c["source"]["publication_or_contract"] = "NOT_VERIFIED"
    elif mutation == "traversal":
        c["fixture"]["input_files"][0]["path"] = "../outside"
    elif mutation == "absolute":
        c["fixture"]["input_files"][0]["path"] = "C:/secret"
    elif mutation == "oracle-not-hashed":
        c["fixture"]["oracle_file"] = "other.json"
    elif mutation == "fake-runtime":
        c["stack"]["java_revision"] = "fixed-static-identity"
    elif mutation == "unsupported-operation":
        c["execution"]["operation"] = "invented-union-reasoner"
    elif mutation == "unbounded":
        c["execution"]["max_seconds"] = 999999
    elif mutation == "bool-version":
        c["schema_version"] = True
    elif mutation == "l5":
        c["claim_level"] = "L5"
    elif mutation == "duplicate-check":
        c["checks"] *= 2
    elif mutation == "no-tolerance":
        c["checks"][0]["tolerance_basis"] = ""
    elif mutation == "bad-units":
        c["fixture"]["expected_units"] = None
    elif mutation == "bad-capability":
        c["capabilities"] = ["anything"]
    else:
        c["fixture"]["input_files"] *= 2
    with pytest.raises(ScenarioError):
        validate(c, tmp_path)


@pytest.mark.parametrize("text", ['{"a":1,"a":2}', '{"a":NaN}'])
def test_ambiguous_or_nonfinite_json_rejected(tmp_path, text):
    p = tmp_path / "bad.json"
    p.write_text(text)
    with pytest.raises(ScenarioError):
        load(p)


def receipt_args():
    passed = {"status": "PASS", "reason": "Explicit fixture assertion executed"}
    return dict(run_id="unit-record", identities={"source_sha": "a" * 40, "build_hashes": {"class": "b" * 64},
        "wheel_sha256": "c" * 64, "services": {"runtime": "test-id"}, "auth_principal": "test-user",
        "java_version": "21", "components": ["test-component"]}, command=["python", "owned-runner.py"],
        started="2026-11-05T00:00:00Z", ended="2026-11-05T00:01:00Z", checks={"values": passed.copy()},
        axes={axis: passed.copy() for axis in AXES}, evidence=[{"path": "sanitized.json", "sha256": "d" * 64,
        "sanitized": True}], cleanup=passed.copy())


@pytest.mark.parametrize("status", ["FAIL", "BLOCKED", "NOT_RUN", "SKIPPED"])
def test_lower_passes_never_promote_unknown_or_failed_science(contract, status):
    args = receipt_args()
    args["axes"]["scientific"] = {"status": status, "reason": "Independent result not established"}
    assert record(contract, **args)["status"] == status


def test_not_applicable_science_cannot_become_l4_pass(contract):
    args = receipt_args()
    args["axes"]["scientific"] = {"status": "NOT_APPLICABLE", "reason": "No scientific computation evaluated"}
    assert record(contract, **args)["status"] == "NOT_RUN"
    args = receipt_args()
    args["checks"]["values"] = {"status": "NOT_APPLICABLE", "reason": "No actual check evaluated"}
    assert record(contract, **args)["status"] == "NOT_RUN"


@pytest.mark.parametrize("mutation", ["no-reason", "no-identity", "no-axis", "no-check", "no-sanitization"])
def test_incomplete_receipt_refused(contract, mutation):
    args = receipt_args()
    if mutation == "no-reason":
        args["checks"]["values"] = {"status": "SKIPPED", "reason": ""}
    elif mutation == "no-identity":
        del args["identities"]["wheel_sha256"]
    elif mutation == "no-axis":
        del args["axes"]["java_contract"]
    elif mutation == "no-check":
        args["checks"] = {}
    else:
        args["evidence"][0]["sanitized"] = False
    with pytest.raises(ScenarioError):
        record(contract, **args)


@pytest.mark.parametrize("section", ["source", "fixture", "stack", "execution"])
def test_malformed_section_is_a_local_contract_error(contract, tmp_path, section):
    contract[section] = []
    with pytest.raises(ScenarioError):
        validate(contract, tmp_path)


@pytest.mark.parametrize("content", ["Bearer private-secret", "eyJabc.def.ghi"])
def test_credential_shaped_output_refused(contract, content):
    args = receipt_args()
    args["axes"]["python"]["reason"] = content
    with pytest.raises(ScenarioError):
        record(contract, **args)


def test_all_42_historical_ids_retained_in_family_map():
    import json
    from pathlib import Path
    package = Path(__file__).resolve().parents[2]
    matrix = json.loads((package / "tests/acceptance/scenarios/test-evidence-map.json").read_text())
    ids = matrix["coverage_ids"]
    assert len(ids) == len(set(ids)) == 42
    assert sum(c.startswith("RC-") for c in ids) == 24
    assert sum(c.startswith("CAP-") for c in ids) == 18
    for oracle in matrix["independent_oracles"]:
        assert all((package / p).is_file() for p in oracle["files"])
