"""Two-phase safe retirement: archive every ignored file, verify it, normal Git removal.

Never force, delete branches, clean files, prune metadata or retire dirty/active work.
Only clean worktrees whose HEAD is both origin-reachable and in the integration
candidate may qualify. Every ignored/untracked byte is archived and re-read first.
"""
import argparse
import datetime as dt
import hashlib
import json
import subprocess
import tarfile
from pathlib import Path

import psutil


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def git(root, *args):
    return subprocess.check_output(['git', *args], cwd=root).decode('utf-8').strip()


def active(path):
    target = str(path.resolve()).replace('\\', '/').lower().rstrip('/')
    matches = []
    for process in psutil.process_iter(['pid', 'cwd', 'cmdline']):
        try:
            info = process.info
            cwd = (info.get('cwd') or '').replace('\\', '/').lower().rstrip('/')
            command = ' '.join(info.get('cmdline') or []).replace('\\', '/').lower()
            if cwd == target or cwd.startswith(target + '/') or target in command:
                matches.append(info['pid'])
        except (psutil.AccessDenied, psutil.NoSuchProcess):
            continue
    return matches


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, required=True)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--merged-prs-only', action='store_true', help='Restrict new plan to verified merged original PR branches')
    parser.add_argument('--include-branch', action='append', default=[], help='Explicit additional published merged prerequisite branch; ancestry/clean/archive gates still apply')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    archive = args.archive.resolve()
    plan_file = archive / 'retirement-plan.json'
    if not args.execute:
        archive.mkdir(parents=True, exist_ok=False)
        # Archive may contain private ignored test material. Restrict before copying.
        user = subprocess.check_output(['whoami', '/user', '/fo', 'csv', '/nh'], text=True).strip().split(',')[-1].strip('"')
        subprocess.run(['icacls', str(archive), '/grant:r', f'*{user}:(OI)(CI)F',
                        '*S-1-5-18:(OI)(CI)F', '*S-1-5-32-544:(OI)(CI)F', '/inheritance:r'], check=True, capture_output=True)
        branches = json.loads((root / 'notes/python-consolidation/pr-ledger.json').read_text(encoding='utf-8'))
        keep = {pr['head'] for pr in branches['in_scope'] if pr['status'] != 'MERGED'}
        merged = {pr['head'] for pr in branches['in_scope'] if pr['status'] == 'MERGED'} | set(args.include_branch)
        candidate = git(root, 'rev-parse', 'origin/feature/python-experiment-runner' if args.merged_prs_only else 'HEAD')
        rows = []
        for block in git(root, 'worktree', 'list', '--porcelain').split('\n\n'):
            row = dict(line.split(' ', 1) if ' ' in line else (line, True) for line in block.splitlines())
            path = Path(row['worktree'])
            status = git(path, 'status', '--porcelain=v1', '--untracked-files=all')
            refs = git(root, 'for-each-ref', '--contains', row['HEAD'], '--format=%(refname)', 'refs/remotes/origin')
            ancestor = subprocess.run(['git', 'merge-base', '--is-ancestor', row['HEAD'], candidate], cwd=root, check=False).returncode == 0
            pids = active(path)
            branch = str(row.get('branch', '')).removeprefix('refs/heads/')
            reason = ('main repository' if path.resolve() == Path(git(root, 'rev-parse', '--git-common-dir')).resolve().parent
                      else 'active consolidation' if path.resolve() == root.resolve()
                      else 'pending focused PR review' if branch in keep
                      else 'outside explicit merged-PR retirement scope' if args.merged_prs_only and branch not in merged
                      else 'dirty files preserved for review' if status
                      else 'active process' if pids
                      else 'HEAD not published on origin' if not refs
                      else 'HEAD not included in current candidate' if not ancestor
                      else None)
            row.update(status=status, origin_refs=refs.splitlines(), active_pids=pids,
                       classification='SAFE_TO_RETIRE' if reason is None else 'RETAINED',
                       reason=reason or 'Clean, origin-reachable, candidate-ancestor; ignored bytes require archival before removal')
            rows.append(row)
        plan_file.write_text(json.dumps({'created_at_utc': dt.datetime.now(dt.UTC).isoformat(),
                                         'candidate_sha': candidate, 'worktrees': rows}, indent=2), encoding='utf-8')
        print(json.dumps([{'path': row['worktree'], 'class': row['classification'], 'reason': row['reason']}
                          for row in rows], indent=2))
        return
    plan = json.loads(plan_file.read_text(encoding='utf-8'))
    results = []
    for index, row in enumerate(plan['worktrees']):
        if row['classification'] != 'SAFE_TO_RETIRE':
            continue
        path = Path(row['worktree'])
        record = {'path': str(path), 'head': row['HEAD'], 'status': 'BLOCKED'}
        try:
            if git(path, 'rev-parse', 'HEAD') != row['HEAD'] or git(path, 'status', '--porcelain=v1', '--untracked-files=all'):
                raise RuntimeError('HEAD or dirty state changed since dry run')
            if active(path):
                raise RuntimeError('Active process uses this path')
            names = subprocess.check_output(['git', 'ls-files', '--others', '--ignored', '--exclude-standard', '-z'], cwd=path).decode('utf-8').split('\0')
            entries = []
            target = archive / f'worktree-{index:02}.tar.gz'
            with tarfile.open(target, 'x:gz') as output:
                for name in names:
                    if not name:
                        continue
                    file = path / name
                    if file.is_symlink() or getattr(file, 'is_junction', lambda: False)() or not file.is_file():
                        raise RuntimeError(f'Nonregular ignored path requires review: {name}')
                    if file.resolve() != file.absolute():
                        raise RuntimeError(f'Ignored path traverses a link: {name}')
                    entry = {'path': name, 'size': file.stat().st_size, 'sha256': digest(file)}
                    output.add(file, arcname=name, recursive=False)
                    entries.append(entry)
            with tarfile.open(target, 'r:gz') as source:
                for member, expected in zip(source.getmembers(), entries, strict=True):
                    with source.extractfile(member) as stream:
                        actual = hashlib.file_digest(stream, 'sha256').hexdigest()
                    if member.name != expected['path'] or actual != expected['sha256']:
                        raise RuntimeError('Archive recovery verification failed')
            # Recheck all bytes immediately before normal Git retirement.
            for entry in entries:
                if digest(path / entry['path']) != entry['sha256']:
                    raise RuntimeError('Ignored evidence changed while archiving')
            if active(path) or git(path, 'status', '--porcelain=v1', '--untracked-files=all'):
                raise RuntimeError('Worktree became active or dirty')
            (archive / f'worktree-{index:02}-files.json').write_text(json.dumps(entries, indent=2), encoding='utf-8')
            record.update(archive=str(target), archive_sha256=digest(target), archived_files=len(entries),
                          archive_verified=True, branch_preserved=True)
            result = subprocess.run(['git', 'worktree', 'remove', str(path)], cwd=root, capture_output=True, check=False)
            if result.returncode:
                raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
            if path.exists():
                raise RuntimeError('Removed worktree path still exists')
            record['status'] = 'REMOVED'
        except (OSError, RuntimeError, subprocess.SubprocessError, tarfile.TarError) as error:
            record['reason'] = str(error)
        results.append(record)
        (archive / 'retirement-results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
        print(json.dumps(record), flush=True)
    (archive / 'post-worktrees.txt').write_text(git(root, 'worktree', 'list', '--porcelain'), encoding='utf-8')


if __name__ == '__main__':
    main()
