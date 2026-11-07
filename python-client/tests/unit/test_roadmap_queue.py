"""All42original IDs/history retained; no saved queue implies autonomous execution."""
import importlib.util
import json
from pathlib import Path
from copy import deepcopy

import pytest

PATH = Path(__file__).resolve().parents[3] / "notes/python-roadmap-execution/roadmap_queue.py"
spec = importlib.util.spec_from_file_location("roadmap_queue", PATH)
queue = importlib.util.module_from_spec(spec); spec.loader.exec_module(queue)


def test_seed_preserves_all_original_rows_and_explicit_actions_without_promoting_history():
    value = queue.seed()
    assert len(queue.validate(value)) == 42
    assert len({row["id"] for row in value["requirements"]}) == 42
    assert all(row["disposition"] is None for row in value["requirements"])
    assert value["next_actionable_requirement"] == "CAP-04"
    assert not value["continuation"]["automatic_restart_configured"]
    worker = next(row for row in value["requirements"] if row["id"] == "CAP-17")
    assert "klab_worker" in worker["required_public_python_functionality"]
    assert "historical_audit" in worker and worker["real_java_acceptance_status"] == "NOT_RUN"


@pytest.mark.parametrize("defect", ["missing", "history", "falsecomplete", "noaction"])
def test_queue_rejects_dropped_history_unattested_completion_and_missing_action(defect):
    value = deepcopy(queue.seed())
    if defect == "missing": value["requirements"].pop()
    if defect == "history": value["requirements"][0]["historical_audit"]["implementation_status"] = "COMPLETE"
    if defect == "falsecomplete": value["requirements"][0]["disposition"] = "PYTHON_COMPLETE_SIMULATOR_VERIFIED"
    if defect == "noaction": value["requirements"][0]["next_executable_action"] = ""
    with pytest.raises(ValueError): queue.validate(value)


def test_check_next_and_seed_never_reset_existing_queue(tmp_path, monkeypatch, capsys):
    target = tmp_path / "queue.json"; monkeypatch.setattr(queue, "QUEUE", target)
    assert queue.main(["seed"]) == 0
    before = target.read_bytes()
    with pytest.raises(ValueError, match="reset"): queue.main(["seed"])
    assert target.read_bytes() == before
    assert queue.main(["check"]) == 0
    assert queue.main(["next"]) == 0
    assert '"id": "CAP-04"' in capsys.readouterr().out
    value = json.loads(target.read_text()); value["remaining_runnable_work"] = []
    target.write_text(json.dumps(value))
    assert queue.main(["next"]) == 0 and "no_runnable_work" in capsys.readouterr().out


def test_checkpoint_refuses_unmerged_or_wrong_target(tmp_path, monkeypatch):
    target = tmp_path / "queue.json"; monkeypatch.setattr(queue, "QUEUE", target)
    target.write_text(json.dumps(queue.seed()))
    with pytest.raises(ValueError): queue.main(["checkpoint"])
    monkeypatch.setattr(queue.subprocess, "check_output", lambda *a, **k: json.dumps({"state": "OPEN", "baseRefName": "feature/python-experiment-runner"}))
    with pytest.raises(ValueError): queue.main(["checkpoint", "--pr", "290"])


def test_checkpoint_requires_ancestry_and_preserves_acceptance(tmp_path, monkeypatch):
    target = tmp_path / "queue.json"; monkeypatch.setattr(queue, "QUEUE", target)
    value = queue.seed(); target.write_text(json.dumps(value))
    pr = {"state": "MERGED", "baseRefName": "feature/python-experiment-runner", "mergeCommit": {"oid": "abc"}, "headRefOid": "head", "mergedAt": "2026-11-07T00:00:00Z"}
    monkeypatch.setattr(queue.subprocess, "check_output", lambda command, **kwargs: json.dumps(pr) if command[0] == "gh" else "test/child\n")
    calls = []; monkeypatch.setattr(queue.subprocess, "run", lambda command, **kwargs: calls.append(command))
    assert queue.main(["checkpoint", "--pr", "290"]) == 0
    saved = json.loads(target.read_text())
    assert saved["requirements"] == value["requirements"] and saved["active"]["branch"] == "test/child"
    assert calls == [["git", "merge-base", "--is-ancestor", "abc", "HEAD"]]
