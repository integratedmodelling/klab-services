"""Matrix-runner custody/refusal controls without launching services."""
import hashlib
import json
from pathlib import Path
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools'))
import verify_onboarding_matrix as matrix


@pytest.mark.parametrize('xml', [
    '<testsuites/>', '<testsuite tests="0"/>', '<testsuite tests="1" failures="1"/>',
    '<testsuite tests="1" errors="1"/>', '<testsuite tests="1" skipped="1"/>',
    '<testsuite tests="-1"/>'])
def test_missing_failed_skipped_or_negative_evidence_refuses(tmp_path, xml):
    path = tmp_path / 'result.xml'
    path.write_text(xml)
    with pytest.raises(ValueError):
        matrix.suite_counts(path)


def test_actual_test_count_is_taken_from_junit_not_expected_constants(tmp_path):
    path = tmp_path / 'result.xml'
    path.write_text('<testsuites><testsuite tests="3"/><testsuite tests="4"/></testsuites>')
    assert matrix.suite_counts(path) == {'tests': 7, 'failures': 0, 'errors': 0, 'skipped': 0}


def test_foreign_or_source_imports_cannot_gain_installed_custody(tmp_path, monkeypatch):
    receipt = {'source_raw_sha256': {}}
    for name, module in [('sdk', 'klab_client'), ('simulator', 'klab_simulator'), ('worker', 'klab_worker')]:
        directory = tmp_path / name / module
        directory.mkdir(parents=True)
        path = directory / '__init__.py'
        path.write_text('# explicit package bytes\n')
        monkeypatch.setitem(sys.modules, module, SimpleNamespace(__file__=str(path)))
        receipt['source_raw_sha256'][name] = {module + '/__init__.py': hashlib.sha256(path.read_bytes()).hexdigest()}
    assert matrix.verify_installed(receipt)['sdk']['module_count'] == 1
    receipt['source_raw_sha256']['sdk'] = {'klab_client/__init__.py': '0' * 64}
    with pytest.raises(ValueError):
        matrix.verify_installed(receipt)
    monkeypatch.setattr(matrix, 'ROOT', tmp_path)
    with pytest.raises(ValueError):
        matrix.verify_installed(receipt)


@pytest.mark.parametrize('failure', [None, 'subprocess', 'test-drift', 'support-drift', 'timeout'])
def test_runner_preserves_failed_evidence_and_never_overwrites_prior_run(tmp_path, monkeypatch, failure):
    root = tmp_path / 'repo'
    root.mkdir()
    test = root / 'test_explicit.py'
    test.write_text('def test_explicit():\n    assert True\n')
    helper = root / 'python-client/tests/fixtures/owned_helper.py'
    helper.parent.mkdir(parents=True)
    helper.write_text('# original support bytes\n')
    monkeypatch.setattr(matrix, 'ROOT', root)
    monkeypatch.setattr(matrix, 'GROUPS', {'explicit': ['test_explicit.py']})
    monkeypatch.setattr(matrix, 'verify_installed', lambda receipt: {
        name: {'path': str(tmp_path / name), 'module_count': 1, 'source_sha256': {}}
        for name in ('sdk', 'simulator', 'worker')})
    prior = tmp_path / 'artifact.json'
    prior.write_text('{}')
    output = tmp_path / 'output'
    def run(args, **kwargs):
        assert kwargs['cwd'] == root and 'KLAB_PRIVATE' not in kwargs['env']
        assert kwargs['timeout'] == 480
        if failure == 'timeout':
            raise matrix.subprocess.TimeoutExpired(args, 480, output=b'retained diagnostics')
        xml = next(arg.split('=', 1)[1] for arg in args if arg.startswith('--junitxml='))
        Path(xml).write_text('<testsuite tests="2"/>')
        if failure == 'test-drift':
            test.write_text('def test_changed(): pass\n')
        if failure == 'support-drift':
            helper.write_text('# changed support bytes\n')
        return SimpleNamespace(returncode=1 if failure == 'subprocess' else 0, stdout='retained diagnostics', stderr='')
    monkeypatch.setenv('KLAB_PRIVATE', 'not inherited')
    monkeypatch.setattr(matrix.subprocess, 'run', run)
    options = ['--artifact-receipt', str(prior), '--output-directory', str(output)]
    if failure:
        with pytest.raises(ValueError):
            matrix.main(options)
        assert not (output / 'receipt.json').exists()
    else:
        receipt = matrix.main(options)
        assert receipt['groups']['explicit']['tests'] == 2
        assert receipt['final_original_row_acceptance'] is False
        assert json.loads((output / 'receipt.json').read_text()) == receipt
    assert (output / 'explicit.log').read_text() == 'retained diagnostics'
    with pytest.raises(FileExistsError):
        matrix.main(options)
