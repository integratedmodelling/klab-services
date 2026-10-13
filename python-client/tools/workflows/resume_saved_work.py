import json
from pathlib import Path
import subprocess
import sys
import uuid



def run(state):
    """Create, restore and share original work in separate real Python processes."""
    state = Path(state)
    directory = state / ("saved-handles-" + uuid.uuid4().hex)
    directory.mkdir()
    script = Path(__file__).parents[2] / "examples/saved_handles.py"
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
    return reports
