import importlib.util
from pathlib import Path

import pytest


def tool():
    spec = importlib.util.spec_from_file_location("feature_coverage", Path(__file__).parents[2] / "tools/check_feature_coverage.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@pytest.mark.parametrize("gap", ["missing_lines", "missing_branches", "excluded_lines"])
def test_gate_rejects_new_module_gaps(gap):
    data = {"summary": {"num_statements": 1}, "missing_lines": [], "missing_branches": [], "excluded_lines": []}
    data[gap] = [1] if gap != "missing_branches" else [[1, 2]]
    assert tool().evaluate({"files": {"src/new.py": data}}, "", ["src/new.py"])


def test_changed_decisions_are_checked_without_claiming_old_gaps_are_new():
    diff = "+++ b/python-client/src/existing.py\n@@ -1 +1,2 @@\n+new\n+decision\n"
    data = {"summary": {"num_statements": 3}, "missing_lines": [9], "missing_branches": [[9, 10]], "excluded_lines": []}
    assert not tool().evaluate({"files": {"src/existing.py": data}}, diff, [])
    data["missing_branches"].append([2, 3])
    assert tool().evaluate({"files": {"src/existing.py": data}}, diff, [])
    assert tool().evaluate({"files": {}}, diff, ["src/new.py"])


def test_gate_maps_installed_wheel_source_paths():
    data = {"summary": {"num_statements": 1}, "missing_lines": [], "missing_branches": [], "excluded_lines": []}
    report = {"files": {"/env/site-packages/klab_client/new.py": data}}
    assert not tool().evaluate(report, "", ["src/klab_client/new.py"])


def test_worker_gate_maps_installed_module_and_does_not_ignore_changed_worker_decisions():
    diff = "+++ b/python-worker/src/klab_worker/client.py\n@@ -1 +1,2 @@\n+new\n+decision\n"
    data = {"summary": {"num_statements": 3}, "missing_lines": [], "missing_branches": [], "excluded_lines": []}
    report = {"files": {"C:\\installed\\klab_worker\\client.py": data}}
    assert not tool().evaluate(report, diff, ["src/klab_worker/client.py"], "python-worker")
    data["missing_branches"] = [[2, 3]]
    assert tool().evaluate(report, diff, [], "python-worker")
    assert tool().evaluate(report, diff, ["src/klab_worker/client.py"], "python-worker")


def test_default_sdk_gate_does_not_treat_worker_paths_as_sdk_changes():
    diff = "+++ b/python-worker/src/klab_worker/client.py\n@@ -1 +1 @@\n+new\n"
    assert tool().changed_lines(diff) == {}
    assert tool().changed_lines(diff, "python-worker") == {"src/klab_worker/client.py": {1}}
