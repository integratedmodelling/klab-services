"""Local HTTP subprocess exercises real redirect/credential destination boundaries."""
import json
import os
from pathlib import Path
import subprocess
import sys

import pytest

pytestmark = pytest.mark.integration


def test_native_exchange_forbidden_origin_and_unknown_mutation(tmp_path):
    script = Path(__file__).parents[1] / "fixtures/connection_http.py"
    process = subprocess.run([sys.executable, str(script), str(tmp_path)],
        env={**os.environ, "PYTHONOPTIMIZE": "1"}, capture_output=True, text=True, timeout=30)
    assert process.returncode == 0, process.stderr
    assert json.loads(process.stdout) == {"passed": True, "forbidden_requests": 0, "mutation_submissions": 1}
    assert "controlled-private" not in process.stdout + process.stderr
