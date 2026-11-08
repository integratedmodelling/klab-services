"""Bounded read-only capability capture; never a scientific or per-gap native pass.

Without --connect this checks repository custody only. Connection configuration is
external JSON: approved_read_only=true, base_url, expected_service_id, native_build,
and optionally token_env (the NAME of an environment variable, never a token).
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
LIMIT = 65536


def strict_json(text):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('Duplicate JSON key')
            result[key] = value
        return result
    def nonfinite(value):
        raise ValueError('Nonfinite JSON number')
    return json.loads(text, object_pairs_hook=pairs, parse_constant=nonfinite)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def preflight(gap_id):
    document = json.loads((ROOT / 'docs/java-service-contract-gaps/gaps.yaml').read_text(encoding='utf-8'))
    matches = [row for row in document['gaps'] if row['id'] == gap_id]
    if len(matches) != 1:
        raise ValueError('Unknown or duplicate gap ID')
    row = {**document['defaults'], **matches[0]}
    refs = row['python_test_refs'] + row['real_java_test_refs']
    files = {}
    for ref in refs:
        if ref.startswith('https://'):
            continue
        path = (ROOT / ref.split('::')[0].split('#')[0]).resolve()
        path.relative_to(ROOT)
        files[path.relative_to(ROOT).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    return {'gap_id': gap_id, 'native_execution_status': 'REAL_NOT_RUN',
            'preflight': 'PASS', 'files_sha256': files,
            'reason': row['native_prerequisite'],
            'required_native_inputs': row['native_prerequisite'],
            'classification': row['handoff_classification'],
            'executed': 'Repository test-file custody/hash checks only; no Java request attempted',
            'not_executed': 'Public SDK-to-Java behavior, scientific oracle, mutation or cleanup',
            'next_action': 'Supply the named build/fixture/permissions/contract; see the canonical workflow',
            'fallbacks': 0}


def capability_capture(config, opener=None):
    if not isinstance(config, dict):
        raise ValueError('Connection configuration must be a JSON object')
    if set(config) - {'approved_read_only', 'base_url', 'expected_service_id', 'native_build', 'token_env'}:
        raise ValueError('Unknown connection configuration key')
    if config.get('approved_read_only') is not True:
        raise ValueError('Explicit read-only approval required')
    for key in ('base_url', 'expected_service_id', 'native_build'):
        if not isinstance(config.get(key), str) or not config[key].strip():
            raise ValueError('Missing pinned connection field')
    url = urllib.parse.urlsplit(config['base_url'])
    if (url.scheme not in ('http', 'https') or not url.hostname or url.username
            or url.password or url.query or url.fragment):
        raise ValueError('Use a credential-free HTTP(S) service base URL')
    if url.scheme == 'http' and url.hostname not in ('localhost', '127.0.0.1', '::1'):
        raise ValueError('Non-loopback connection requires HTTPS')
    headers = {'Accept': 'application/json'}
    if config.get('token_env'):
        if not isinstance(config['token_env'], str):
            raise ValueError('Token environment name must be text')
        token = os.environ.get(config['token_env'])
        if not token or '\r' in token or '\n' in token:
            raise ValueError('Missing or malformed external token')
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(config['base_url'].rstrip('/') + '/public/capabilities', headers=headers)
    opener = opener or urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    with opener.open(request, timeout=10) as response:
        raw = response.read(LIMIT + 1)
        if len(raw) > LIMIT:
            raise ValueError('Capability response exceeds byte budget')
        payload = strict_json(raw)
        if not isinstance(payload, dict) or payload.get('serviceId') != config['expected_service_id']:
            raise ValueError('Native service identity mismatch')
    # Do not serialize raw payloads, URLs, headers, token values or exception text.
    return {'capability_capture': 'IDENTITY_MATCH', 'native_build': config['native_build'],
            'response_sha256': hashlib.sha256(raw).hexdigest(),
            'native_execution_status': 'REAL_NOT_RUN',
            'reason': 'Capability request succeeded; the selected gap contract was NOT executed'}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gap', required=True)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--connect', type=Path)
    args = parser.parse_args(argv)
    if args.output.exists():
        raise FileExistsError('Choose an unused receipt path before any connection attempt')
    result = preflight(args.gap)
    result['timestamp_utc'] = datetime.now(timezone.utc).isoformat()
    result['repository_commit'] = subprocess.check_output(
        ['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True,
        creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0)).strip()
    code = 3
    if args.connect:
        result['executed'] = 'Repository custody and explicitly requested capability-gate check; selected native workflow not executed'
        try:
            if args.connect.stat().st_size > 16384:
                raise ValueError('Connection configuration exceeds byte budget')
            result.update(capability_capture(strict_json(args.connect.read_text(encoding='utf-8'))))
        except (ValueError, OSError, urllib.error.URLError):
            result.update(capability_capture='BLOCKED_WITH_REASON',
                          reason='Configuration, connection, response or identity gate failed; no fallback')
            code = 4
    with args.output.open('x', encoding='utf-8') as stream:
        stream.write(json.dumps(result, indent=2) + '\n')
    print(json.dumps({key: result[key] for key in ('gap_id', 'native_execution_status', 'reason',
        'required_native_inputs', 'executed', 'next_action')}))
    return code


if __name__ == '__main__':
    raise SystemExit(main())
