"""Demo packaging cannot turn a failed/skipped/incomplete test report into a success clip."""
import json
from xml.sax.saxutils import quoteattr

import pytest

from render_release_demos import PROPERTIES, build_clips, load_evidence, validate_run


def records():
    identity = {"passed": True, "observation_id": 42, "job_id": 7, "pending_id": 8}
    return {
        "connection_scientific_workflow": [{"provider": p, "scientific_check": {"verified_cells": 40}} for p in ("certificate", "token")],
        "typed_observe": [{"passed": True, "verified_options": ["geometry", "scenarios", "namespace", "project", "observer", "context grid"]}],
        "saved_handles": [[{**identity, "mode": "create"}, {**identity, "mode": "restore"},
                           {**identity, "mode": "collaborator", "private_denied": True},
                           {**identity, "mode": "release", "explicit_cleanup": True}]],
        "native_geotiff": [{"summary": {"acceptance_passed": True, "verified_samples": 1}, "session_cleanup": True}],
        "release_notebook_workflow": [{"verified_cells": 20, "valid_sum": 409, "recorded_links": 6,
            "recorded_relationships": [{"source": n + 1, "target": 42, "relationship": "MEASURED", "evidence": "recorded relationship"} for n in range(6)],
            "partial": True, "consistency": "unknown", "connection_path": "certificate to issued-token fresh-process recovery",
            "valid_nonowner_positive_control": True, "unauthorized_export": "AuthorizationError", "unauthorized_provenance": "AuthorizationError"}],
        "local_scientific_lifecycle": [{"passed": True, "cancellation": {"confirmed": True}, "failed_job": {"scientific_outcome": "confirmed failure"}}],
        "connection_access": [{"valid_collaborator_positive_control": True, "private_context_denial": "AuthorizationError"}],
    }


def test_demo_feature_selection_is_bounded_and_honest():
    clips, analysis = build_clips(records())
    assert [clip.name for clip in clips] == ["connect-and-compute", "save-and-resume", "native-exports", "analyze-and-trace", "jobs-and-access"]
    assert all(len(clip.facts) == 3 for clip in clips)
    assert analysis["partial"] and analysis["consistency"] == "unknown"


@pytest.mark.parametrize("break_record", [
    lambda r: r["connection_scientific_workflow"].pop(),
    lambda r: r["typed_observe"][0].update(passed=False),
    lambda r: r["saved_handles"][0][1].update(observation_id=99),
    lambda r: r["native_geotiff"][0]["summary"].update(acceptance_passed=False),
    lambda r: r["release_notebook_workflow"][0].update(partial=False),
    lambda r: r["release_notebook_workflow"][0].update(unauthorized_export="ServerError"),
    lambda r: r["local_scientific_lifecycle"][0]["cancellation"].update(confirmed=False),
    lambda r: r["connection_access"][0].update(private_context_denial="AuthenticationError"),
])
def test_unverified_or_failed_features_cannot_generate_success_clips(break_record):
    value = records()
    break_record(value)
    with pytest.raises(ValueError):
        build_clips(value)


def test_junit_properties_are_whitelisted_and_report_hash_is_retained(tmp_path):
    properties = "".join(f'<property name={quoteattr(key)} value={quoteattr(json.dumps(value[0]))}/>' for key, value in records().items())
    path = tmp_path / "functional.xml"
    path.write_text(f'<testsuites><testsuite tests="1"><testcase><properties>{properties}<property name="private_config" value="do not copy"/></properties></testcase></testsuite></testsuites>')
    loaded, sources = load_evidence([path])
    assert set(loaded) == PROPERTIES and len(sources["functional.xml"]) == 64
    assert "private_config" not in loaded


@pytest.mark.parametrize("xml", ["<testsuites/>", '<testsuite tests="1"><testcase><skipped/></testcase></testsuite>',
    '<testsuite tests="1"><testcase><failure/></testcase></testsuite>', '<testsuite tests="2"><testcase/></testsuite>',
    '<testsuite tests="1" failures="1"><testcase/></testsuite>', '<!DOCTYPE x><testsuite/>',
    '<testsuite tests="1"><testcase/></testsuite>'])
def test_empty_failed_skipped_incomplete_or_missing_evidence_is_rejected(tmp_path, xml):
    path = tmp_path / "bad.xml"
    path.write_text(xml)
    with pytest.raises(ValueError):
        load_evidence([path])


@pytest.mark.parametrize("payload", [{"passed": False}, {"passed": True, "cleanup_exit_code": 1},
    {"passed": True, "cleanup_exit_code": 0, "client_production_sha256_lf": {}}])
def test_missing_cleanup_or_changed_sdk_cannot_be_presented_as_verified_video(tmp_path, payload):
    path = tmp_path / "run-report.json"
    path.write_text(json.dumps(payload))
    with pytest.raises(ValueError):
        validate_run(path)
