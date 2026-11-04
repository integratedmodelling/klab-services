"""An installed client inspects a local snapshot in an isolated new process."""
from pathlib import Path
import subprocess
import sys


def test_offline_snapshot_demo_in_fresh_process():
    example = Path(__file__).resolve().parents[2] / "examples/offline_scientific_read.py"
    result = subprocess.run([sys.executable, "-I", str(example)], capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, result.stderr
    assert "PASS: offline fresh process" in result.stdout
