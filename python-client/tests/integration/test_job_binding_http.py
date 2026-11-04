"""Owned loopback subprocesses; retain the existing offline-network guard."""
import json
import os
from pathlib import Path
import subprocess
import sys

import pytest

pytestmark = pytest.mark.integration


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_real_http_job_binding_does_not_cancel_same_number_in_another_scope(mode):
    script = Path(__file__).parents[1] / "fixtures/job_binding_http.py"
    process = subprocess.run([sys.executable, str(script), mode], env=os.environ.copy(),
        capture_output=True, text=True, timeout=30,
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    assert process.returncode == 0, process.stderr
    assert json.loads(process.stdout) == {"passed": True, "mode": mode, "synthetic_not_java_retention": True}
