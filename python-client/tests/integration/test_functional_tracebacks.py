"""Actually run pytest: transport-error arguments must not leak into stdout or JUnit."""
import os
from pathlib import Path
import subprocess

from run_functional_acceptance import SUITES, command


def test_functional_failure_does_not_print_credential_arguments(tmp_path, monkeypatch):
    source = tmp_path / "test_transport_canary.py"
    source.write_text('''import os
import pytest

def credential_operation(credential):
    raise RuntimeError("Deliberate transport failure")

@pytest.mark.functional
def test_transport():
    credential_operation(os.environ["KLAB_TEST_CREDENTIAL_CANARY"])
''', encoding="utf-8")
    canary = "not-a-real-credential-c9e4482b"
    report = tmp_path / "result.xml"
    monkeypatch.setitem(SUITES, "traceback-canary", str(source))
    result = subprocess.run(command(["traceback-canary"], report), cwd=tmp_path,
        env={**os.environ, "KLAB_TEST_CREDENTIAL_CANARY": canary}, capture_output=True, text=True, timeout=30)
    assert result.returncode == 1
    assert "Deliberate transport failure" in result.stdout
    assert canary not in result.stdout + result.stderr + Path(report).read_text(encoding="utf-8")
