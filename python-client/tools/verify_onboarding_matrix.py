"""Installed owned onboarding/routing acceptance, never production issuer approval."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
GROUPS = {
    'sdk-onboarding-routing': [
        'python-client/tests/unit/test_connection.py', 'python-client/tests/unit/test_connection_guards.py',
        'python-client/tests/unit/test_onboarding_journey.py', 'python-client/tests/unit/test_source_credential_preflight.py'],
    'public-http-bootstrap-origin': ['python-client/tests/integration/test_connection_http.py'],
    'independent-public-expiry-routing': [
        'developer-tools/klab-service-simulator/tests/test_bootstrap_recovery.py',
        'developer-tools/klab-service-simulator/tests/test_vertical.py'],
}


def support_snapshot():
    paths = [ROOT / 'python-client/tools/verify_onboarding_matrix.py']
    for directory in ('python-client/tests', 'python-client/tools/workflows',
                      'developer-tools/klab-service-simulator/tests'):
        paths.extend((ROOT / directory).rglob('*.py'))
    return {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted(set(paths)) if path.is_file()}


def suite_counts(path):
    suites = list(ET.parse(path).getroot().iter('testsuite'))
    counts = {key: sum(int(suite.get(key, '0')) for suite in suites)
              for key in ('tests', 'failures', 'errors', 'skipped')}
    if (not suites or counts['tests'] <= 0 or any(value < 0 for value in counts.values())
            or counts['failures'] or counts['errors'] or counts['skipped']):
        raise ValueError('Positive executed test counts and zero failures/errors/skips required')
    return counts


def verify_installed(receipt):
    import klab_client
    import klab_simulator
    import klab_worker
    packages = {'sdk': klab_client, 'simulator': klab_simulator, 'worker': klab_worker}
    result = {}
    for name, package in packages.items():
        directory = Path(package.__file__).resolve().parent
        if directory.is_relative_to(ROOT):
            raise ValueError('Source-tree imports cannot establish installed acceptance')
        expected = receipt['source_raw_sha256'][name]
        actual = {directory.name + '/' + path.relative_to(directory).as_posix():
                  hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(directory.rglob('*.py'))}
        if not expected or actual != expected:
            raise ValueError('Installed production/reference bytes differ from explicit artifact receipt')
        result[name] = {'path': str(directory), 'module_count': len(actual), 'source_sha256': actual}
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    custody = parser.add_mutually_exclusive_group(required=True)
    custody.add_argument('--artifact-receipt', type=Path)
    custody.add_argument('--capture-current-source', action='store_true',
                         help='Verify fresh installed module bytes against current checkout, not wheel/sdist custody')
    parser.add_argument('--output-directory', required=True, type=Path)
    args = parser.parse_args(argv)
    if args.capture_current_source:
        packages = {'sdk': 'python-client', 'simulator': 'developer-tools/klab-service-simulator',
                    'worker': 'python-worker'}
        current = {'evidence': 'FRESH_INSTALLED_MODULE_SOURCE_BYTE_PARITY_NOT_WHEEL_SDIST_ISOLATION',
                   'source_raw_sha256': {name: {path.relative_to(ROOT / package / 'src').as_posix():
                       hashlib.sha256(path.read_bytes()).hexdigest()
                       for path in sorted((ROOT / package / 'src').rglob('*.py'))}
                       for name, package in packages.items()}}
        raw_receipt = json.dumps(current, sort_keys=True).encode()
    else:
        raw_receipt = args.artifact_receipt.read_bytes()
    installed = verify_installed(json.loads(raw_receipt))
    test_hashes = {test: hashlib.sha256((ROOT / test).read_bytes()).hexdigest()
                   for tests in GROUPS.values() for test in tests}
    support_hashes = support_snapshot()
    # Do not overwrite failed development evidence or reuse an earlier green run.
    args.output_directory.mkdir(parents=True, exist_ok=False)
    output = args.output_directory.resolve()
    env = {key: value for key, value in os.environ.items() if not key.startswith('KLAB_')}
    env['PYTHONPATH'] = os.pathsep.join([str(ROOT / 'python-client/tools'),
                                       *(str(Path(info['path']).parent) for info in installed.values())])
    results = {}
    for name, tests in GROUPS.items():
        try:
            result = subprocess.run([sys.executable, '-m', 'pytest', '-q', *tests,
                                     '--junitxml=' + str(output / (name + '.xml')), '--tb=short',
                                     '-o', 'faulthandler_timeout=90'], cwd=ROOT, env=env,
                                    capture_output=True, text=True, encoding='utf-8', errors='replace',
                                    timeout=480, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        except subprocess.TimeoutExpired as error:
            chunks = [value.decode('utf-8', 'replace') if isinstance(value, bytes) else value or ''
                      for value in (error.stdout, error.stderr)]
            (output / (name + '.log')).write_text(''.join(chunks), encoding='utf-8')
            raise ValueError('Onboarding group harness timeout; partial evidence retained: ' + name) from None
        (output / (name + '.log')).write_text(result.stdout + result.stderr, encoding='utf-8')
        if result.returncode != 0:
            raise ValueError('Onboarding group failed; local diagnostic evidence retained: ' + name)
        results[name] = suite_counts(output / (name + '.xml'))
        print(name, json.dumps(results[name], sort_keys=True), flush=True)
    # A child run must not mutate the installation being accepted.
    if verify_installed(json.loads(raw_receipt)) != installed:
        raise ValueError('Installed bytes changed during verification')
    if test_hashes != {test: hashlib.sha256((ROOT / test).read_bytes()).hexdigest() for test in test_hashes}:
        raise ValueError('Executable test bytes changed during verification')
    if support_snapshot() != support_hashes:
        raise ValueError('Test/support/runner source bytes changed during verification')
    receipt = {
        'version': 1, 'verified_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'requirements': ['RC-P1.1', 'RC-P1.4', 'CAP-01', 'CAP-02'], 'groups': results,
        'artifact_receipt_sha256': hashlib.sha256(raw_receipt).hexdigest(), 'installed': installed,
        'artifact_receipt_kind': json.loads(raw_receipt).get('evidence', 'EXPLICIT_PRIOR_ARTIFACT_RECEIPT'),
        'test_sha256': test_hashes,
        'support_source_sha256': support_hashes,
        'python_acceptance': 'INSTALLED_PUBLIC_ONBOARDING_EXPIRY_PROVIDER_ROUTING_CONTROLS_PASS',
        'native_issuer_license_deployment': 'REAL_NOT_RUN_EXTERNAL_APPROVAL_REQUIRED',
        'scientific_acceptance': False, 'final_original_row_acceptance': False,
        'dependency_custody': 'Explicit installed package bytes; existing dependencies, not fresh dependency isolation',
        'scope': 'Mock-wire certificate/token substitution, isolated local HTTP certificate exchange/origin boundaries, '
                 'independent owned HTTP expiry/principal/identity/outage/recovery and no-replay workflows. '
                 'No native Hub, IDE, genuine issuer/license approval or production deployment inferred.'}
    with (output / 'receipt.json').open('x', encoding='utf-8') as stream:
        json.dump(receipt, stream, indent=2)
        stream.write('\n')
    return receipt


if __name__ == '__main__':
    main()
