"""Preserve inventoried unique commits and dirty files; never retire worktrees."""
import argparse
import hashlib
import json
import shutil
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    output = args.output.resolve()
    if output == root or root in output.parents:
        parser.error('Private preservation archive must be outside Git')
    output.mkdir(parents=True, exist_ok=False)
    inventory = json.loads(args.inventory.read_text(encoding='utf-8'))
    records = []
    def git(path, *command):
        result = subprocess.run(['git', *command], cwd=path, capture_output=True, check=True)
        return result.stdout
    for index, item in enumerate(inventory):
        path = Path(item['worktree'])
        head = git(path, 'rev-parse', 'HEAD').decode().strip()
        if head != item['HEAD']:
            raise RuntimeError(f'HEAD changed since inventory: {path}')
        record = {'path': str(path), 'head': head, 'archive_ref': None}
        if item['unpublished_commits']:
            ref = f'refs/archive/consolidation-20261104/worktree-{index:02}'
            git(root, 'update-ref', ref, head, '0' * 40)
            if git(root, 'rev-parse', ref).decode().strip() != head:
                raise RuntimeError('Archive ref verification failed')
            record['archive_ref'] = ref
        target = output / f'wt-{index:02}'
        target.mkdir()
        for name, command in [('status.txt', ['status', '--porcelain=v1', '--untracked-files=all']),
                              ('unstaged.patch', ['diff', '--binary']),
                              ('staged.patch', ['diff', '--cached', '--binary'])]:
            (target / name).write_bytes(git(path, *command))
        names = git(path, 'ls-files', '--others', '--exclude-standard', '-z').split(b'\0')
        for name in names:
            if not name:
                continue
            relative = Path(name.decode('utf-8'))
            source = path / relative
            if source.is_symlink() or not source.is_file():
                raise RuntimeError(f'Nonregular untracked file requires manual preservation: {source}')
            destination = target / 'untracked' / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
        records.append(record)
    bundle = output / 'repository.bundle'
    git(root, 'bundle', 'create', str(bundle), '--all')
    (output / 'bundle-verification.txt').write_bytes(git(root, 'bundle', 'verify', str(bundle)))
    (output / 'preserved-worktrees.json').write_text(json.dumps(records, indent=2), encoding='utf-8')
    files = []
    for path in output.rglob('*'):
        if path.is_file():
            with path.open('rb') as stream:
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
            files.append({'path': str(path.relative_to(output)), 'size': path.stat().st_size, 'sha256': digest})
    (output / 'manifest.json').write_text(json.dumps(files, indent=2), encoding='utf-8')
    print(json.dumps({'output': str(output), 'files': len(files), 'worktrees': len(records),
                      'archive_refs': [r['archive_ref'] for r in records if r['archive_ref']],
                      'bundle_bytes': bundle.stat().st_size}, indent=2))


if __name__ == '__main__':
    main()
