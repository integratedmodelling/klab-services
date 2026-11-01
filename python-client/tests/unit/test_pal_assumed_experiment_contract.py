"""Specification integrity only: no scientific computation, network or Java execution."""
import calendar
from fractions import Fraction
import json
from pathlib import Path


RESEARCH = Path(__file__).resolve().parents[2] / "docs" / "research" / "pal"


def contract():
    return json.loads((RESEARCH / "assumed-experiment.json").read_text(encoding="utf-8"))


def test_scope_cannot_be_read_as_an_executed_full_study():
    item = contract()
    assert item["status"] == "SPECIFIED_NOT_EXECUTED"
    assert item["scientific_acceptance"] is item["full_study_reproduction"] is False
    assert item["live_java_jobs"] == 0
    assert item["video_status"] == "PLANNED_NOT_RECORDED"
    assert item["originals"]["included_models"] == ["rgc_max", "rg2", "fr"]
    assert "P4 comparison" in item["originals"]["excluded_from_this_milestone"]


def test_operational_legend_retains_exact_original_labels_and_code_sets():
    item = contract()["legend"]
    expected = {4: "Calluna", 7: "FestucaRubra", 12: "Helictotrichon", 15: "Phragmites",
                21: "Polypodiopsida", 27: "Senecio", 28: "UlexGallii", 36: "Carex",
                37: "Gramineae", 41: "UlexEuropaeus"}
    assert {r["code"]: r["original_label"] for r in item["entries"]} == expected
    assert len(item["entries"]) == len(expected)
    assert set(item["overstory_codes"]) == set(expected) - {21}
    assert len(item["overstory_codes"]) == 9
    assert item["taxonomic_equivalences_applied"] is False
    assert next(r for r in item["entries"] if r["code"] == 7)["taxonomic_interpretation"].startswith("CONFLICT_RETAINED")


def test_legend_cross_product_preserves_nine_equal_and_eighty_one_different_pairs():
    item = contract()["legend"]
    pairs = [(a["code"], b) for a in item["entries"] for b in item["overstory_codes"]]
    assert len(pairs) == 90
    assert sum(a == b for a, b in pairs) == 9
    assert sum(a != b for a, b in pairs) == 81
    codes = {entry["original_label"]: entry["code"] for entry in item["entries"]}
    assert (codes["FestucaRubra"], codes["Gramineae"]) in pairs
    assert codes["FestucaRubra"] != codes["Gramineae"]


def test_original_equation_text_and_threshold_are_not_rewritten():
    item = contract()["calculation"]
    oracle = json.loads((RESEARCH / "radiation-adaptation-results.json").read_text())
    assert item["equations"] == oracle["adaptation"]["equations_unchanged"]
    assert item["threshold"]["value"] == 25
    assert item["original_equations_changed"] is False
    assert item["input_interpretation_is_additional_adaptation"] is True
    assert item["limiting_factor"] == {"stored_range": [0, 1], "unit": "dimensionless", "scale_to_percent": False}


def test_assumptions_are_explicit_not_reported_as_confirmed_source_metadata():
    item = contract()
    a, b = item["scenarios"]
    assert (a["id"], b["id"]) == ("assumed_daily_mean", "assumed_monthly_total")
    assert "ASSUMPTION A" in a["label"] and "ASSUMPTION B" in b["label"]
    assert all(s["interpretation_confirmed"] is False for s in (a, b))
    assert all(s["registered"] is False and s["request"] is None for s in (a, b))
    assert a["raw_unit"] == b["raw_unit"] == "MJ/m2"
    assert a["raw_support"] != b["raw_support"]
    factor = lambda s: Fraction(s["raw_to_daily_factor"]["numerator"], s["raw_to_daily_factor"]["denominator"])
    assert factor(a) == 1
    assert factor(b) == Fraction(1, calendar.monthrange(2018, 5)[1])
    assert a["threshold_daily_value"] == b["threshold_daily_value"] == 25
    assert item["scenario_comparison"]["not_a_pure_unit_conversion"] is True
    assert item["scenario_comparison"]["raw_data_must_not_be_rewritten"] is True


def test_actual_input_qa_does_not_masquerade_as_result_or_mask_acceptance():
    item = contract()["inputs"]
    equal, mixed = item["patches"]
    for patch in (equal, mixed):
        counts = patch["main_center_input_qa"]
        assert counts["joint_valid"] + counts["missing"] == counts["centers"]
        assert counts["equal"] + counts["different"] == counts["joint_valid"]
        assert "not approved resampling or computed output" in patch["scope"]
    assert equal["main_center_input_qa"]["different"] == 0
    assert mixed["main_center_input_qa"]["different"] == 3
    assert mixed["raw_radiation_zero_pixels"] == 100
    assert mixed["joint_valid_radiation_zero_centers"] == 0
    assert item["zero_is_not_nodata"] is True
    assert item["native_radiation_nodata"] < -1e30


def test_no_automatic_authoring_write_replay_or_premature_publication():
    item = contract()
    assert item["runtime_gates"]["project_local_operational_definitions"] == "NOT_AUTHORED_OR_ADMITTED"
    assert item["runtime_gates"]["shared_worldview_changes"] == "NOT_AUTHORIZED"
    assert item["runtime_gates"]["java_changes"] == "NOT_AUTHORIZED"
    assert item["runtime_gates"]["automatic_ambiguous_write_replay"] is False
    assert item["runtime_gates"]["maximum_workers_first_attempt"] == 1
    assert item["runtime_gates"]["maximum_contexts"] == 4
    assert item["runtime_gates"]["maximum_requested_output_jobs"] == 12
    assert item["runtime_gates"]["maximum_requested_output_jobs"] == 3 * item["runtime_gates"]["maximum_contexts"]
    assert item["runtime_gates"]["maximum_context_observation_jobs"] == item["runtime_gates"]["maximum_contexts"]
    assert item["runtime_gates"]["maximum_total_jobs"] == (
        item["runtime_gates"]["maximum_requested_output_jobs"]
        + item["runtime_gates"]["maximum_context_observation_jobs"]
    ) == 16
    assert item["presentation"]["publication_authorized_now"] is False


def test_demo_disclosure_and_original_study_acceptance_remain_separate():
    item = contract()
    assert item["claim_levels"]["assumed_scenario_execution"] == "NOT_RUN"
    assert item["claim_levels"]["real_input_field_numerical_fidelity"] == "NOT_VALIDATED"
    assert item["claim_levels"]["full_published_study_reproduction"] == "BLOCKED_NOT_DEMONSTRATED"
    assert "not full-study reproduction" in item["presentation"]["required_visible_label"]
    assert item["presentation"]["record_actual_notebook_execution"] is True
    assert item["presentation"]["reuse_synthetic_demo_footage_as_pal"] is False
    assert item["presentation"]["executed_notebook_requires_matching_run_evidence"] is True


def test_portable_receipts_match_frozen_input_identity_and_qa_scope():
    item = contract()["inputs"]
    receipt = json.loads((RESEARCH / "assumed-input-receipts.json").read_text(encoding="utf-8"))
    assert receipt["scientific_acceptance"] is False and receipt["raw_payloads_in_repository"] is False
    assert receipt["observed_date"] == "2026-11-01"
    assert receipt["coverage_id"] == item["radiation_coverage"]
    assert receipt["service_url"] == item["production_endpoint"]
    assert len(receipt["patches"]) == len(item["patches"]) == 2
    for actual, expected in zip(receipt["patches"], item["patches"]):
        assert actual["id"] == expected["id"]
        assert actual["sha256"] == expected["radiation_sha256"]
        assert actual["shape"] == expected["native_shape"]
        assert actual["main_center_input_qa"] == expected["main_center_input_qa"]
        assert actual["tiff_units"] is None
        assert actual["nodata"] == item["native_radiation_nodata"]
        assert actual["bytes"] > 0 and actual["dtype"] == "float32" and actual["crs"] == "EPSG:4326"


def test_future_site_publication_request_does_not_authorize_unfinished_claims():
    item = contract()["publication"]
    assert item["destination"] == "https://eric-resume.up.railway.app/python-client-progress/"
    assert item["authorization"] == "CONDITIONAL_ON_DEMONSTRABLE_FINISHED_WORK"
    assert item["ready_now"] is item["website_changed"] is False
    assert item["gate_evidence_status"] == "NOT_COMPLETE"
    assert item["unfinished_material_must_not_be_presented_as_finished"] is True
    assert item["raw_third_party_payload_publication_implied"] is False
    assert len(item["required_gates"]) == len(set(item["required_gates"])) == 8
    assert "actual_java_results_for_declared_scenarios" in item["required_gates"]
    assert "accurate_adapted_versus_original_full_study_claims" in item["required_gates"]
