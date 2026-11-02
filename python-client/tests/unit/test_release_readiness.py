"""Evidence identity is not proof of the scientific truth asserted by its author."""
from copy import deepcopy
import hashlib
import json

import pytest

from check_release_readiness import GATES, ReadinessError, assess, main

CANDIDATE = "a" * 40


def setup(tmp_path):
    content = b"Illustrative test evidence only; not an actual release pass"
    (tmp_path / "log.txt").write_bytes(content)
    artifact = {"path": "log.txt", "sha256": hashlib.sha256(content).hexdigest()}
    return {"version": 1, "candidate": CANDIDATE, "checks": [
        {"gate": gate, "status": "PASS", "candidate": CANDIDATE, "artifact": dict(artifact)} for gate in GATES]}


def test_complete_manifest_is_only_eligible_for_human_review(tmp_path):
    result = assess(setup(tmp_path), tmp_path, CANDIDATE)
    assert result["eligible_for_review"] and result["publication_authorized"] is False
    assert all(row["evidence_verified"] for row in result["checks"])
    assert "human" in result["limit"]
    assert "log.txt" not in json.dumps(result)


@pytest.mark.parametrize("status", ["FAIL", "UNKNOWN", "BLOCKED", "NOT_RUN"])
def test_nonpass_is_not_relabeled_success(status, tmp_path):
    manifest = setup(tmp_path)
    manifest["checks"][0] = {"gate": "offline", "status": status, "candidate": CANDIDATE}
    result = assess(manifest, tmp_path, CANDIDATE)
    assert not result["eligible_for_review"] and result["checks"][0]["status"] == status


def test_missing_checks_are_unknown_not_skipped_success(tmp_path):
    result = assess({"version": 1, "candidate": CANDIDATE, "checks": []}, tmp_path, CANDIDATE)
    assert not result["eligible_for_review"]
    assert all(row["status"] == "UNKNOWN" for row in result["checks"])


@pytest.mark.parametrize("candidate", [None, "a", "A" * 40, "g" * 40, "a" * 41])
def test_exact_candidate_required(candidate, tmp_path):
    with pytest.raises(ReadinessError):
        assess(setup(tmp_path), tmp_path, candidate)


@pytest.mark.parametrize("change", [None, [], {"version": True}, {"version": 2}, {"candidate": "b" * 40},
    {"checks": {}}, {"checks": [None] * 9}, {"extra": "value"}])
def test_malformed_manifest_is_invalid(change, tmp_path):
    manifest = setup(tmp_path)
    if isinstance(change, dict):
        manifest.update(change)
    else:
        manifest = change
    with pytest.raises(ReadinessError):
        assess(manifest, tmp_path, CANDIDATE)


@pytest.mark.parametrize("change", [None, [], {"gate": []}, {"gate": "invented"}, {"status": "SKIP"},
    {"status": []}, {"candidate": "b" * 40}, {"extra": "value"}])
def test_malformed_or_wrong_candidate_check_rejected(change, tmp_path):
    manifest = setup(tmp_path)
    if isinstance(change, dict):
        manifest["checks"][0].update(change)
    else:
        manifest["checks"][0] = change
    with pytest.raises(ReadinessError):
        assess(manifest, tmp_path, CANDIDATE)


def test_duplicate_and_missing_pass_evidence_are_invalid(tmp_path):
    manifest = setup(tmp_path)
    manifest["checks"][1] = deepcopy(manifest["checks"][0])
    with pytest.raises(ReadinessError):
        assess(manifest, tmp_path, CANDIDATE)
    manifest = setup(tmp_path)
    del manifest["checks"][0]["artifact"]
    with pytest.raises(ReadinessError):
        assess(manifest, tmp_path, CANDIDATE)


@pytest.mark.parametrize("reference", [None, {}, {"path": 1, "sha256": "a" * 64},
    {"path": "log.txt", "sha256": 1}, {"path": "log.txt", "sha256": "wrong"}])
def test_bad_artifact_shape_rejected(reference, tmp_path):
    manifest = setup(tmp_path)
    manifest["checks"][0]["artifact"] = reference
    with pytest.raises(ReadinessError):
        assess(manifest, tmp_path, CANDIDATE)


@pytest.mark.parametrize("path", ["../private", "/private", "a/../private", "a//b", "./log.txt",
    "C:/private", "..\\private", "x" * 1025, "private key"])
def test_paths_cannot_escape_or_disguise_origin(path, tmp_path):
    manifest = setup(tmp_path)
    manifest["checks"][0]["artifact"]["path"] = path
    with pytest.raises(ReadinessError):
        assess(manifest, tmp_path, CANDIDATE)


def test_missing_modified_oversized_or_nonfile_artifacts_fail(tmp_path):
    manifest = setup(tmp_path)
    (tmp_path / "log.txt").write_bytes(b"changed")
    with pytest.raises(ReadinessError, match="digest"):
        assess(manifest, tmp_path, CANDIDATE)
    (tmp_path / "log.txt").unlink()
    with pytest.raises(ReadinessError, match="absent"):
        assess(manifest, tmp_path, CANDIDATE)
    (tmp_path / "log.txt").mkdir()
    with pytest.raises(ReadinessError, match="regular"):
        assess(manifest, tmp_path, CANDIDATE)


def test_large_file_is_rejected_before_hashing(tmp_path):
    manifest = setup(tmp_path)
    with (tmp_path / "log.txt").open("wb") as stream:
        stream.truncate(8 * 1024 * 1024 + 1)
    with pytest.raises(ReadinessError, match="8 MiB"):
        assess(manifest, tmp_path, CANDIDATE)


def test_file_growth_cannot_bypass_actual_read_budget(tmp_path, monkeypatch):
    import os
    from pathlib import Path
    manifest = setup(tmp_path)
    path = tmp_path / "log.txt"
    with path.open("wb") as stream:
        stream.truncate(8 * 1024 * 1024 + 1)
    original = Path.stat
    def stale_size(self, *args, **kwargs):
        value = original(self, *args, **kwargs)
        if self.name == "log.txt":
            values = list(value)
            values[6] = 0
            return os.stat_result(values)
        return value
    monkeypatch.setattr(Path, "stat", stale_size)
    with pytest.raises(ReadinessError, match="grew"):
        assess(manifest, tmp_path, CANDIDATE)


def test_missing_or_nondirectory_root_is_rejected(tmp_path):
    manifest = setup(tmp_path)
    for root in (tmp_path / "missing", tmp_path / "log.txt"):
        with pytest.raises(ReadinessError):
            assess(manifest, root, CANDIDATE)


def test_symlink_is_rejected_before_read(tmp_path, monkeypatch):
    from pathlib import Path
    manifest = setup(tmp_path)
    monkeypatch.setattr(Path, "is_symlink", lambda self: self.name == "log.txt")
    with pytest.raises(ReadinessError, match="symlink"):
        assess(manifest, tmp_path, CANDIDATE)


@pytest.mark.parametrize("mode,code", [("complete", 0), ("missing_checks", 3), ("bad_json", 2),
    ("absent", 2), ("oversized", 2), ("wrong_candidate", 2), ("duplicate_key", 2)])
def test_cli_exit_codes_and_output_never_publish_or_echo_private_input(mode, code, tmp_path, capsys):
    manifest = setup(tmp_path)
    path = tmp_path / "manifest.json"
    if mode == "missing_checks":
        manifest["checks"] = []
    elif mode == "wrong_candidate":
        manifest["candidate"] = "b" * 40
    if mode != "absent":
        path.write_text('{"version":1,"version":2}' if mode == "duplicate_key" else "private-secret invalid JSON" if mode == "bad_json" else
            "x" * (256 * 1024 + 1) if mode == "oversized" else json.dumps(manifest), encoding="utf-8")
    assert main(["--manifest", str(path), "--evidence-root", str(tmp_path), "--candidate", CANDIDATE]) == code
    output = capsys.readouterr().out
    assert "private-secret" not in output
    assert json.loads(output)["publication_authorized"] is False


def test_script_entry_point_is_review_gate_not_publication(tmp_path, monkeypatch, capsys):
    import runpy
    import sys
    from pathlib import Path
    manifest = setup(tmp_path)
    manifest["checks"] = []
    path = tmp_path / "manifest.json"
    path.write_text(json.dumps(manifest), encoding="utf-8")
    script = Path(__file__).parents[2] / "tools/check_release_readiness.py"
    monkeypatch.setattr(sys, "argv", [str(script), "--manifest", str(path), "--evidence-root", str(tmp_path),
                                   "--candidate", CANDIDATE])
    with pytest.raises(SystemExit) as caught:
        runpy.run_path(str(script), run_name="__main__")
    assert caught.value.code == 3
    assert json.loads(capsys.readouterr().out)["publication_authorized"] is False
