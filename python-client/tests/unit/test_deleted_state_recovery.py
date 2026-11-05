from types import SimpleNamespace

import pytest

from workflows.deleted_state_recovery import allowed_call, save_report, verify_listing


@pytest.mark.parametrize("deleted", [False, True])
def test_absence_requires_successful_same_family_survivor_visibility(deleted):
    ids = ["s.survivor"] if deleted else ["s.deleted", "s.survivor"]
    witness = verify_listing([SimpleNamespace(context_ids=ids)], "s.deleted", "s.survivor", deleted=deleted)
    assert witness["survivor_visible"] and witness["released_context_absent"] is deleted
    assert "not restore error" in witness["absence_basis"]


@pytest.mark.parametrize("ids,deleted", [([], True), (["s.deleted"], True),
    (["s.deleted", "s.survivor"], True), (["s.survivor"], False), (["s.survivor"] * 2, True)])
def test_error_or_uncontrolled_empty_listing_is_not_absence_proof(ids, deleted):
    with pytest.raises(AssertionError):
        verify_listing([SimpleNamespace(context_ids=ids)], "s.deleted", "s.survivor", deleted=deleted)


@pytest.mark.parametrize("method,path,expected", [("GET", "/runtime/public/capabilities", True),
    ("POST", "/runtime/api/v1/connect", True), ("POST", "/runtime/api/v1/query", True),
    ("GET", "/runtime/jobs/status/2", True), ("POST", "/runtime/api/v1/observation/value", True),
    ("POST", "/runtime/api/v1/submit", False), ("GET", "/runtime/releaseContext", False),
    ("POST", "/runtime/createContext", False), ("GET", "/runtime/jobs/cancel/2", False)])
def test_strict_recovery_allowlist_no_scientific_replay_or_disposal(method, path, expected):
    assert allowed_call(method, path) is expected


def test_evidence_failure_cannot_mask_primary_or_echo_private_diagnostics(tmp_path, monkeypatch):
    def fail(*args, **kwargs):
        raise OSError("private details")
    monkeypatch.setattr(type(tmp_path), "write_text", fail)
    original = AssertionError("original failure")
    save_report(tmp_path, {"passed": False}, original)
    assert original.__notes__ == ["Deleted-state evidence save failed: OSError"]
    with pytest.raises(OSError):
        save_report(tmp_path, {"passed": True})
