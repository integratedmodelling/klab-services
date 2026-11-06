"""Exercise actual multipart sockets in isolated subprocesses; offline guard retained."""
import json
import os
from pathlib import Path
import subprocess
import sys

import pytest


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_resource_import_upload_job_and_cleanup_over_http(mode):
    fixture = Path(__file__).parents[1] / "fixtures/resource_import_http.py"
    process = subprocess.run([sys.executable, str(fixture), mode], env=os.environ.copy(),
        capture_output=True, text=True, timeout=30,
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    assert process.returncode == 0, process.stderr
    receipt = json.loads(process.stdout)
    assert receipt == {"passed": True, "mode": mode, "upload_bytes": 1024 * 1024,
                       "requests": 4, "java_admission": "NOT_RUN"}
