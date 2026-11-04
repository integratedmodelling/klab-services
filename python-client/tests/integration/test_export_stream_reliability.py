"""Actual HTTP socket and file lifecycle; explicitly not real Java science."""
import json
from pathlib import Path
import subprocess
import sys


def test_stream_reliability_in_installed_subprocess(tmp_path):
    script = Path(__file__).resolve().parents[2] / "tools/workflows/stream_reliability.py"
    result = subprocess.run([sys.executable, "-I", str(script), "--work-dir", str(tmp_path / "fresh"),
                             "--bytes", str(8 * 1024**2)], capture_output=True, text=True, timeout=90)
    assert result.returncode == 0, result.stderr
    report = json.loads(result.stdout)
    assert report["passed"]
    records = {item["mode"]: item for item in report["records"]}
    assert records["success"]["peak_python_bytes"] < 4 * 1024**2
    assert records["buffered-control"]["peak_python_bytes"] >= 8 * 1024**2
    assert records["cancel"]["outcome"] == "KeyboardInterrupt"
    assert all(item["export_requests"] == 1 and item["temporary_files"] == 0 for item in records.values())
    assert all(item["original_preserved"] for name, item in records.items() if name != "success")
