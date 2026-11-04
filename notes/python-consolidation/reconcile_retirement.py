"""Reconcile normal Git retirement and individually verify every leftover file.

Windows Git can unregister a worktree but leave long-path ignored build outputs.
Never recurse-delete an arbitrary directory; require the original archived manifest
for every ignored file or the exact committed Git blob for tracked files, no
links, no active process and no Git registration.
"""
import hashlib
import json
import subprocess
from pathlib import Path

from retire_worktrees import active, digest


def main():
    root = Path(__file__).resolve().parents[2]
    archive = root.parent / '.consolidation-evidence/worktree-retirement-20261104'
    results = json.loads((archive / 'retirement-results.json').read_text())
    plan = json.loads((archive / 'retirement-plan.json').read_text())
    inventory = subprocess.check_output(['git', 'worktree', 'list', '--porcelain'], cwd=root, text=True)
    registered = {line.removeprefix('worktree ') for line in inventory.splitlines() if line.startswith('worktree ')}
    fixes = []
    for record in results:
        if record['status'] != 'BLOCKED' or 'Filename too long' not in record.get('reason', ''):
            continue
        path = Path(record['path'])
        if path.as_posix() in registered or active(path):
            raise RuntimeError(f'Registered or active path requires separate review: {path}')
        index = next(i for i, row in enumerate(plan['worktrees']) if Path(row['worktree']) == path)
        manifest = json.loads((archive / f'worktree-{index:02}-files.json').read_text())
        expected = {row['path']: row for row in manifest}
        tracked = {}
        tree = subprocess.check_output(['git', 'ls-tree', '-r', '-z', record['head']], cwd=root)
        for row in tree.split(b'\0'):
            if row:
                header, name = row.split(b'\t', 1)
                mode, kind, oid = header.split()
                if kind == b'blob' and mode in (b'100644', b'100755'):
                    tracked[name.decode('utf-8')] = oid.decode('ascii')
        def unchanged(entry, path=path, expected=expected, tracked=tracked):
            name = entry.relative_to(path).as_posix()
            if name in expected:
                return digest(entry) == expected[name]['sha256']
            if name not in tracked:
                return False
            body = entry.read_bytes()
            # The pre-removal worktree was clean. Accept exact committed bytes or
            # Git's normal Windows CRLF checkout representation; no arbitrary edits.
            for candidate in (body, body.replace(b'\r\n', b'\n')):
                blob = b'blob ' + str(len(candidate)).encode() + b'\0' + candidate
                if hashlib.sha1(blob).hexdigest() == tracked[name]:
                    return True
            return False
        if not record['archive_verified'] or digest(Path(record['archive'])) != record['archive_sha256']:
            raise RuntimeError('Recovery archive changed or was not verified')
        files, directories = [], []
        for entry in path.rglob('*'):
            if entry.is_symlink() or getattr(entry, 'is_junction', lambda: False)():
                raise RuntimeError(f'Link requires manual review: {entry}')
            if entry.is_dir():
                directories.append(entry)
            elif entry.is_file():
                name = entry.relative_to(path).as_posix()
                if not unchanged(entry):
                    raise RuntimeError(f'Unexpected or changed leftover file: {entry}')
                files.append(entry)
            else:
                raise RuntimeError(f'Nonregular leftover: {entry}')
        if active(path):
            raise RuntimeError('Path became active')
        for entry in files:
            # Recheck each individual archived byte immediately before unlinking.
            if not unchanged(entry):
                raise RuntimeError('Leftover changed after verification')
            entry.unlink()
        for entry in sorted(directories, key=lambda p: len(p.parts), reverse=True):
            entry.rmdir()  # Empty-only; fail closed if anything appeared meanwhile.
        path.rmdir()
        fixes.append({'path': str(path), 'git_unregistered_by_normal_remove': True,
                      'verified_archived_or_committed_files_removed': len(files),
                      'directory_absent': not path.exists(), 'branch_preserved': True})
        (archive / 'long-path-reconciliation.json').write_text(json.dumps(fixes, indent=2))
        print(json.dumps(fixes[-1]), flush=True)
    (archive / 'reconciled-worktrees.txt').write_text(inventory)


if __name__ == '__main__':
    main()
