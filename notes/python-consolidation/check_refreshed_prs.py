"""Source-matched noneditable-wheel gates for already refreshed original PR branches."""
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--numbers', nargs='+', type=int, required=True)
    parser.add_argument('--refresh', type=Path, required=True)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--environment', type=Path, required=True)
    parser.add_argument('--label', required=True)
    parser.add_argument('--push', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    python = args.environment / 'Scripts/python.exe'
    probe = '''import pathlib,json,hashlib,klab_client
p=pathlib.Path(klab_client.__file__).parent
print(json.dumps({'path':str(p),'files':{f.relative_to(p).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in p.rglob('*.py')}}))'''
    for number in args.numbers:
        row = json.loads((args.refresh / f'pr-{number}-refresh.json').read_text())
        repo = Path(row['repo'])
        actual_head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip()
        if actual_head != row['head']:
            raise RuntimeError('Refreshed head changed before testing')
        detail = json.loads((args.baseline / f'pr-{number}.json').read_text())
        output = args.refresh / f'pr-{number}-wheel-{args.label}'
        output.mkdir(exist_ok=False)
        with (output / 'install.log').open('xb') as log:
            # Isolated build dependencies are intentional; do not assume setuptools
            # exists in a fresh Python3.12 runtime environment.
            subprocess.run([str(python), '-m', 'pip', 'install', '--disable-pip-version-check',
                            '--no-deps', '--force-reinstall', str(repo / 'python-client')],
                           stdout=log, stderr=log, check=True)
        installed = json.loads(subprocess.check_output([str(python), '-c', probe], cwd=output, text=True))
        source = repo / 'python-client/src/klab_client'
        expected = {p.relative_to(source).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                    for p in source.rglob('*.py')}
        if installed['files'] != expected or Path(installed['path']).is_relative_to(repo):
            raise RuntimeError('Noneditable installed source identity mismatch')
        (output / 'installed-identity.json').write_text(json.dumps(dict(head=actual_head, base=row['base'], **installed), indent=2))
        tests = [f['path'] for f in detail['files'] if '/tests/' in f['path']
                 and Path(f['path']).name.startswith('test_') and f['path'].endswith('.py')]
        if not tests:
            raise RuntimeError('No focused tests discovered; do not claim acceptance')
        subprocess.run([sys.executable, str(root / 'notes/python-consolidation/run_capture.py'),
                        '--cwd', str(repo), '--output', str(output / 'tests'), '--', str(python),
                        '-m', 'pytest', '-q', *tests], check=True)
        print(f'PR{number} focused installed-wheel gate PASS at {actual_head}', flush=True)
        if args.push:
            subprocess.run(['git', 'push', 'origin', 'HEAD:refs/heads/' + detail['headRefName']], cwd=repo, check=True)


if __name__ == '__main__':
    main()
