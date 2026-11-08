"""Probe safety controls; fake responses here are NOT native evidence."""
import importlib.util
import io
import json
from pathlib import Path
from types import SimpleNamespace

import pytest

SPEC = importlib.util.spec_from_file_location('native_probe', Path(__file__).resolve().parents[1] / 'native_probe.py')
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


def config(**changes):
    return dict(approved_read_only=True, base_url='http://127.0.0.1:12345/resources',
                expected_service_id='native-id', native_build='test-build', **changes)


def test_preflight_all_registered_ids_is_repository_only():
    rows = json.loads((probe.ROOT / 'docs/java-service-contract-gaps/gaps.yaml').read_text())['gaps']
    for row in rows:
        result = probe.preflight(row['id'])
        assert result['native_execution_status'] == 'REAL_NOT_RUN'
        assert result['files_sha256'] and result['fallbacks'] == 0
        assert result['reason'] and result['next_action']
        assert 'custody/hash checks only' in result['executed']
        assert 'SDK-to-Java' in result['not_executed']


@pytest.mark.parametrize('url', ['http://example.org', 'https://user:secret@example.org',
                                'file:///etc/passwd', 'https://example.org/?token=secret'])
def test_reject_unsafe_urls_before_network(url):
    value = config()
    value['base_url'] = url
    with pytest.raises(ValueError):
        probe.capability_capture(value)


def test_capability_success_is_not_gap_pass_or_raw_secret_capture():
    def open_request(request, timeout):
        assert request.full_url.endswith('/resources/public/capabilities')
        assert request.get_method() == 'GET' and timeout == 10
        return io.BytesIO(b'{"serviceId":"native-id","private":"never-log-this"}')
    result = probe.capability_capture(config(), SimpleNamespace(open=open_request))
    assert result['native_execution_status'] == 'REAL_NOT_RUN'
    assert 'never-log-this' not in json.dumps(result)


@pytest.mark.parametrize('raw', [b'{"serviceId":"other"}', b'[]', b'x' * (probe.LIMIT + 1),
    b'{"serviceId":"other","serviceId":"native-id"}', b'{"serviceId":"native-id","value":NaN}'])
def test_wrong_identity_and_unbounded_body_refuse(raw):
    with pytest.raises(ValueError):
        probe.capability_capture(config(), SimpleNamespace(open=lambda *a, **k: io.BytesIO(raw)))


def test_redirect_is_never_followed():
    assert probe.NoRedirect().redirect_request(None, None, 302, '', {}, 'https://elsewhere') is None


def test_receipt_is_exclusive_and_not_run(tmp_path, capsys):
    path = tmp_path / 'receipt.json'
    assert probe.main(['--gap', 'JAVA-GAP-0001', '--output', str(path)]) == 3
    assert json.loads(path.read_text())['native_execution_status'] == 'REAL_NOT_RUN'
    displayed = json.loads(capsys.readouterr().out)
    assert displayed['required_native_inputs'] and displayed['reason']
    assert 'custody/hash checks only' in displayed['executed'] and displayed['next_action']
    with pytest.raises(FileExistsError):
        probe.main(['--gap', 'JAVA-GAP-0001', '--output', str(path)])
