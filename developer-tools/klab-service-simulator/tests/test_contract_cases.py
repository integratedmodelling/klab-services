from datetime import datetime, timedelta, timezone
import json
import re

from klab_simulator.compare import readiness_matches, run
from klab_simulator.contracts import CONTRACT_CATEGORIES
from conftest import ROOT, REPO, launch


def test_executable_case_index_has_sources_rows_and_real_java_boundaries():
    document = json.loads((ROOT / "contract-cases.json").read_text())
    assert (REPO / document["java_gap_registry"]).is_file()
    cases = document["cases"]
    assert len({case["id"] for case in cases}) == len(cases)
    for case in cases:
        assert case["rows"] and case["routes"] and case["source"] and case["confidence"] and case["java_status"]
        file, name = case["simulator_test"].split("::")
        assert "def " + name.split("[")[0] + "(" in (ROOT / "tests" / file).read_text()
    assert all("SCIENTIFICALLY_VERIFIED_ON_JAVA=TRUE" not in case["java_status"] for case in cases)
    classifications = document["contract_classifications"]
    assert {row["category"] for row in classifications} == set(CONTRACT_CATEGORIES)
    for row in classifications:
        assert row["evidence"] and row["properties"]
        assert row["cases"] == "ALL" or set(row["cases"]) <= {case["id"] for case in cases}
    receipt = json.loads((REPO / document["staging_reference"]["receipt"]).read_text())
    assert receipt["access"] == "LOCAL_READ_ONLY_SOURCE_AVAILABLE"
    assert receipt["observed_java_execution_here"] is False


def test_ledger_preserves_every_original42criterion_wording_without_waiver():
    source = (REPO / "docs/PYTHON_CLIENT_ACCEPTANCE_MAP.md").read_text(encoding="utf-8")
    expected = {}
    for line in source.splitlines():
        if line.startswith(("| RC-", "| CAP-")):
            fields = line.split("|")
            expected[fields[1].strip()] = fields[2].strip()
    ledger = (REPO / "notes/python-roadmap-execution/AB_COMPLETION_LEDGER.md").read_text(encoding="utf-8")
    # Only the authoritative requirement-wording table defines these criteria.
    # Later operation/status tables may reference the same IDs without redefining
    # them; still require every original definition exactly once in this section.
    ledger = ledger.split("Original criterion text below is the exact criterion", 1)[1]
    ledger = ledger.split("## Ordered runnable engineering queue", 1)[0]
    actual = {}
    for line in ledger.splitlines():
        if line.startswith(("|RC-", "|CAP-")):
            identity, wording = line.split("|")[1].split(" — ", 1)
            assert identity not in actual
            actual[identity] = wording
    assert len(actual) == len(expected) == 42
    assert actual == expected


def test_supported_operation_assessment_covers_original_ids_without_parent_promotion():
    source = (REPO / "docs/PYTHON_CLIENT_ACCEPTANCE_MAP.md").read_text(encoding="utf-8")
    expected = set(re.findall(r"^\| ((?:RC-|CAP-)[^|]+) \|", source, re.M))
    ledger = (REPO / "notes/python-roadmap-execution/AB_COMPLETION_LEDGER.md").read_text(encoding="utf-8")
    section = ledger.split("### Supported-operation reconciliation", 1)[1]
    section = section.split("Production async-analysis correction", 1)[0]
    records = re.findall(r"^\|((?:RC-|CAP-)[^|]+)\|([^\n]+)", section, re.M)
    assert len(records) == len(expected) == 42
    assert {identity for identity, _ in records} == expected
    assert {identity for identity, row in records if "EXCLUDED" in row} == {"RC-P5", "CAP-17"}
    assert sum("READY" in row for _, row in records) == 40
    assert "not 40 complete acceptance rows" in section
    for file in set(re.findall(r"`(test_[a-z0-9_]+\.py)`", section)):
        assert (REPO / "python-client/tests/unit" / file).is_file(), file


def test_same_public_read_cases_run_against_explicit_simulator_and_replay():
    for mode, model in (("simulated", "simulator.elevation"),
                        ("replay", "scientific.examples.usgs_virginia.elevation-quantifier")):
        with launch(mode=mode) as ready:
            report = run(mode, ready["endpoints"], "sim-token-alice", model)
            assert report["status"] == "PASS" and report["fallbacks"] == 0
            assert all(row["backend"] == report["backend"] for row in report["cases"])
            assert report["scientific_execution"] == "NOT_RUN"


def test_real_comparison_requires_current_matching_readonly_evidence_before_io():
    endpoints = {name: f"http://127.0.0.1:1/{name}" for name in ("reasoner", "resources", "resolver", "runtime")}
    assert run("real", endpoints, "not-logged", "any")["status"] == "NOT_RUN"
    receipt = {"collected_utc": (datetime.now(timezone.utc) - timedelta(days=2)).isoformat(),
               "services": [{"name": name, "endpoint": url, "check": "PASS"} for name, url in endpoints.items()],
               "gates": {key: "PASS" for key in ("G0", "G1", "G2")}}
    assert not readiness_matches(receipt, endpoints)
    receipt["collected_utc"] = datetime.now(timezone.utc).isoformat()
    assert readiness_matches(receipt, endpoints)
    receipt["services"][0]["endpoint"] = "http://wrong.invalid"
    assert not readiness_matches(receipt, endpoints)


def test_http500_is_traced_failed_once_and_never_becomes_a_synthetic_success():
    with launch("http500") as ready:
        report = run("simulated", ready["endpoints"], "sim-token-alice", "simulator.elevation")
        assert report["status"] == "FAIL" and report["fallbacks"] == 0
        assert any(row.get("http_status") == 500 for row in report["cases"])
        failures = [row for row in report["trace"] if row.get("status") == 500]
        assert len(failures) == 1 and failures[0]["backend"] == "SIMULATED"
        assert "sim-token-alice" not in json.dumps(report)
