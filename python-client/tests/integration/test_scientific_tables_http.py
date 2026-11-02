"""Own loopback HTTP subprocess, following the existing offline-network boundary."""
import json
import os
from pathlib import Path
import subprocess
import sys

import pytest

pytestmark = pytest.mark.integration


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_real_http_scientific_table_preserves_source_contract(mode):
    script = Path(__file__).parents[1] / "fixtures/scientific_tables_http.py"
    process = subprocess.run([sys.executable, str(script), mode], env=os.environ.copy(),
        capture_output=True, text=True, timeout=30,
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    assert process.returncode == 0, process.stderr
    assert json.loads(process.stdout) == {"passed": True, "mode": mode, "read_requests": 3}
