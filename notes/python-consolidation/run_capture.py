"""Capture one command, revision, dirty diff digest and immutable local evidence."""
import argparse
import datetime as dt
import hashlib
import json
import os
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--cwd', type=Path, default=Path.cwd())
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ['--'] else args.command
    if not command:
        parser.error('Command required')
    args.output.mkdir(parents=True, exist_ok=False)
    env = os.environ.copy()
    if args.java_home:
        env['JAVA_HOME'] = str(args.java_home)
        env['PATH'] = str(args.java_home / 'bin') + os.pathsep + env['PATH']
    def git(*cmd):
        result = subprocess.run(['git', *cmd], cwd=args.cwd, capture_output=True, check=False)
        if result.returncode:
            raise RuntimeError(result.stderr.decode(errors='replace'))
        return result.stdout
    status = git('status', '--porcelain=v1', '--untracked-files=all')
    diff = git('diff', 'HEAD', '--binary')
    (args.output / 'source-status.txt').write_bytes(status)
    (args.output / 'source-diff.patch').write_bytes(diff)
    untracked = []
    for name in git('ls-files', '--others', '--exclude-standard', '-z').split(b'\0'):
        if name:
            path = args.cwd / os.fsdecode(name)
            untracked.append({'path': os.fsdecode(name), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    record = {'command': command, 'cwd': str(args.cwd.resolve()),
              'candidate_sha': git('rev-parse', 'HEAD').decode().strip(),
              'status_sha256': hashlib.sha256(status).hexdigest(),
              'diff_sha256': hashlib.sha256(diff).hexdigest(), 'untracked': untracked,
              'java_home': str(args.java_home) if args.java_home else None,
              'started_at_utc': dt.datetime.now(dt.UTC).isoformat(), 'status': 'IN_PROGRESS'}
    with (args.output / 'stdout.log').open('wb') as stdout, (args.output / 'stderr.log').open('wb') as stderr:
        result = subprocess.run(command, cwd=args.cwd, env=env, stdout=stdout, stderr=stderr, check=False)
    record.update(exit_code=result.returncode, status='PASSED' if result.returncode == 0 else 'FAILED',
                  finished_at_utc=dt.datetime.now(dt.UTC).isoformat())
    record['files'] = [{'path': p.name, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in args.output.iterdir() if p.is_file()]
    (args.output / 'run.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
    print(json.dumps(record, indent=2))
    raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
