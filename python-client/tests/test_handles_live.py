import json
import os
from pathlib import Path
import subprocess
import sys

import pytest

from klab_client.errors import ConfigurationError


@pytest.mark.live
def test_real_saved_handles_between_fresh_python_processes(record_property):
    state = os.environ.get("KLAB_FIXTURE_STATE_DIR")
    if not state:
        raise ConfigurationError("Saved-handle live acceptance requires explicit prepared fixture state")
    state = Path(state)
    directory = state / "saved-handles"
    directory.mkdir()
    script = Path(__file__).parents[1] / "examples/saved_handles.py"
    reports = []
    for mode in ("create", "restore", "collaborator", "release"):
        process = subprocess.run([sys.executable, str(script), mode, "--state-dir", str(state),
            "--handles-dir", str(directory)], capture_output=True, text=True, timeout=180)
        if process.returncode:
            raise AssertionError(f"Saved-handle {mode} process failed: {process.stderr}")
        result = json.loads((state / ("saved-" + mode + ".json")).read_text())
        assert result["passed"]
        reports.append(result)
    assert reports[0]["job_id"] == reports[1]["job_id"]
    assert reports[0]["observation_id"] == reports[1]["observation_id"] == reports[2]["observation_id"]
    assert reports[0]["pending_id"] == reports[1]["pending_id"]
    record_property("saved_handles", json.dumps(reports))
