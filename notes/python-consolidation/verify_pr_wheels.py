"""Sequential source-matched installed-wheel focused gates in a clean environment.

Each installation is noneditable; the installed package's entire Python source
inventory must equal the intended candidate before running its tests. No services.
"""
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--environment', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--baseline', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    args.output.mkdir(parents=True, exist_ok=False)
    python = args.environment / 'Scripts/python.exe'
    if not python.is_file():
        parser.error('Prepare clean environment with test/build dependencies first')
    paths = {133:'roadmap-handoffs-20261103', 134:'python-agent-status-20261103',
             135:'python-agent-control-20261103', 136:'python-graph-projection-20261103',
             137:'python-roadmap-map-20261103', 138:'python-agent-amqp-20261103',
             139:'python-job-binding-20261103', 140:'python-fixture-copy-order-20261103',
             141:'python-source-previews-20261103'}
    parent = args.baseline.parent
    identity_probe = '''import pathlib,json,hashlib,klab_client
p=pathlib.Path(klab_client.__file__).parent
print(json.dumps({'installed_path':str(p),'files':{str(f.relative_to(p)).replace('\\\\','/'):hashlib.sha256(f.read_bytes()).hexdigest() for f in p.rglob('*.py')}}))'''
    summary = []
    for number, name in paths.items():
        candidate = parent / name
        detail = json.loads((args.baseline / f'pr-{number}.json').read_text(encoding='utf-8'))
        actual = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=candidate, text=True).strip()
        if actual != detail['headRefOid']:
            raise RuntimeError(f'PR{number} changed since baseline')
        directory = args.output / f'pr-{number}'
        directory.mkdir()
        with (directory / 'install.log').open('wb') as log:
            result = subprocess.run([str(python), '-m', 'pip', 'install', '--disable-pip-version-check',
                                     '--no-deps', '--no-build-isolation', '--force-reinstall', str(candidate/'python-client')],
                                    cwd=directory, stdout=log, stderr=subprocess.STDOUT, check=False)
        if result.returncode:
            summary.append({'number': number, 'status': 'BLOCKED', 'reason': 'wheel installation failed'})
            continue
        installed = json.loads(subprocess.check_output([str(python), '-c', identity_probe], cwd=directory, text=True))
        source = candidate / 'python-client/src/klab_client'
        expected = {f.relative_to(source).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in source.rglob('*.py')}
        if installed['files'] != expected or candidate in Path(installed['installed_path']).parents:
            raise RuntimeError(f'PR{number} installed source identity mismatch')
        (directory / 'installed-identity.json').write_text(json.dumps(dict(candidate_sha=actual,**installed),indent=2),encoding='utf-8')
        tests = [f['path'] for f in detail['files'] if '/tests/' in f['path'] and Path(f['path']).name.startswith('test_') and f['path'].endswith('.py')]
        result = subprocess.run([sys.executable, str(root/'notes/python-consolidation/run_capture.py'),
                                 '--output', str(directory/'tests'), '--cwd', str(candidate), '--',
                                 str(python), '-m', 'pytest', '-q', *tests], capture_output=True, check=False)
        summary.append({'number': number, 'candidate_sha': actual, 'status': 'PASSED' if result.returncode==0 else 'FAILED',
                        'installed_sources_verified': len(expected), 'evidence': str(directory)})
        print(json.dumps(summary[-1]), flush=True)
    (args.output/'summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
    raise SystemExit(0 if all(item['status']=='PASSED' for item in summary) else 1)


if __name__ == '__main__':
    main()
