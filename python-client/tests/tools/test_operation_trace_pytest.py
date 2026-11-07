"""Subprocess pytest custody, teardown failures, secret-safe refs and refusal policies."""
import json
import os
from pathlib import Path
import subprocess
import sys

import klab_client


TOOLS = Path(__file__).resolve().parents[2] / 'tools'
SOURCE = Path(klab_client.__file__).resolve().parent


def run(tmp_path, code, *options):
    test = tmp_path / 'test_public.py'
    test.write_text(code)
    env = dict(os.environ)
    env['PYTHONPATH'] = os.pathsep.join((str(TOOLS), str(SOURCE.parent)))
    return subprocess.run([sys.executable, '-m', 'pytest', '-q', '-p', 'operation_trace_pytest',
                           str(test), *options], env=env, capture_output=True, text=True, timeout=60)


def test_actual_sdk_calls_teardown_failure_and_parameter_privacy(tmp_path):
    output = tmp_path / 'trace.json'
    result = run(tmp_path, '''import pytest
from klab_client import ReferenceWindow
@pytest.fixture
def teardown_error():
    yield
    raise AssertionError("test teardown failed")
@pytest.mark.parametrize("private", ["private-case-value"])
def test_called(private, teardown_error):
    assert ReferenceWindow(0,1,0,1).to_wire()["rowStart"] == 0
''', '--operation-trace-source', str(SOURCE), '--operation-trace-output', str(output))
    assert result.returncode == 1, result.stdout + result.stderr
    report = json.loads(output.read_text())
    assert report['pytest_exitstatus'] == 1 and report['tests'][0]['outcome'] == 'FAILED'
    assert 'private-case-value' not in output.read_text()
    assert any(row['qualname'] == 'ReferenceWindow.to_wire' for row in report['bindings'])
    assert len(report['operations']) == 506 and not report['exhausted']


def test_explicit_source_output_and_import_custody_refusals(tmp_path):
    output = tmp_path / 'prior.json'
    output.write_text('preserve')
    code = 'def test_ok(): pass\n'
    for options in (('--operation-trace-output', str(tmp_path / 'missing.json')),
                    ('--operation-trace-output', str(output), '--operation-trace-source', str(SOURCE)),
                    ('--operation-trace-output', str(tmp_path / 'wrong.json'), '--operation-trace-source', str(tmp_path))):
        result = run(tmp_path, code, *options)
        assert result.returncode != 0
    assert output.read_text() == 'preserve'
