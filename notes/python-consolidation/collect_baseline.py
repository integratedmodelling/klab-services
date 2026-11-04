"""Read-only repository/GitHub census; retain raw local evidence with SHA256.

No fetch, checkout, merge, cleanup, service mutation or credential collection.
Run with an explicit fresh, private output directory outside Git.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    output = args.output.resolve()
    if output == root or root in output.parents:
        parser.error('Raw evidence must be outside the checkout')
    output.mkdir(parents=True, exist_ok=False)
    manifest = []

    def capture(name, command, cwd=root):
        started = dt.datetime.now(dt.timezone.utc).isoformat()
        result = subprocess.run(command, cwd=cwd, capture_output=True)
        data = result.stdout
        (output / name).write_bytes(data)
        (output / (name + '.stderr')).write_bytes(result.stderr)
        manifest.append(dict(path=name, sha256=hashlib.sha256(data).hexdigest(),
                             stderr_sha256=hashlib.sha256(result.stderr).hexdigest(),
                             command=command, cwd=str(cwd), started_at_utc=started,
                             finished_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
                             exit_code=result.returncode))
        return data.decode('utf-8', errors='replace') if result.returncode == 0 else None

    repo = 'integratedmodelling/klab-services'
    for name, cmd in {
        'status.txt': ['git', 'status', '--porcelain=v1'],
        'remotes.txt': ['git', 'remote', '-v'],
        'branches.txt': ['git', 'branch', '-vv'],
        'worktrees.txt': ['git', 'worktree', 'list', '--porcelain'],
        'log.txt': ['git', 'log', '-20', '--format=%H %cI %s'],
        'refs.txt': ['git', 'show-ref'],
        'stashes.txt': ['git', 'stash', 'list', '--format=%H %gd %s'],
        'open-prs.json': ['gh', 'pr', 'list', '--repo', repo, '--state', 'open', '--limit', '100', '--json', 'number,title,url,baseRefName,baseRefOid,headRefName,headRefOid,isDraft,reviewDecision,mergeStateStatus,statusCheckRollup'],
        'all-prs.json': ['gh', 'pr', 'list', '--repo', repo, '--state', 'all', '--limit', '500', '--json', 'number,title,url,state,baseRefName,headRefName,headRefOid,mergedAt,mergeCommit,closedAt,updatedAt'],
        'open-issues.json': ['gh', 'issue', 'list', '--repo', repo, '--state', 'open', '--limit', '100', '--json', 'number,title,url,updatedAt'],
        'repository.json': ['gh', 'api', f'repos/{repo}', '--jq', '{default_branch,permissions}'],
        'rulesets.json': ['gh', 'api', f'repos/{repo}/rulesets'],
        'delivery-protection.json': ['gh', 'api', f'repos/{repo}/branches/feature%2Fpython-experiment-runner/protection'],
    }.items():
        capture(name, cmd)
    prs = json.loads((output / 'open-prs.json').read_bytes())
    for pr in prs:
        capture(f'pr-{pr["number"]}.json', ['gh', 'pr', 'view', str(pr['number']), '--repo', repo, '--json', 'number,title,url,body,baseRefName,baseRefOid,headRefName,headRefOid,isDraft,mergeable,mergeStateStatus,reviewDecision,reviews,comments,statusCheckRollup,files,commits'])
    for number in [89, 123, 124, 125, 126, 127, 128, 129, 130, 144, 147]:
        capture(f'issue-{number}.json', ['gh', 'issue', 'view', str(number), '--repo', repo, '--json', 'number,title,url,state,body,comments,closedAt'])
    inventory = []
    for block in (output / 'worktrees.txt').read_text(encoding='utf-8').strip().split('\n\n'):
        fields = dict(line.split(' ', 1) if ' ' in line else (line, True) for line in block.splitlines())
        path = Path(fields['worktree'])
        index = len(inventory)
        status = capture(f'wt-{index:02}-status.txt', ['git', 'status', '--porcelain=v1', '--untracked-files=all'], path)
        ignored = capture(f'wt-{index:02}-ignored.txt', ['git', 'ls-files', '--others', '--ignored', '--exclude-standard'], path)
        remotes = capture(f'wt-{index:02}-containing-remotes.txt', ['git', 'for-each-ref', '--contains', fields['HEAD'], '--format=%(refname)', 'refs/remotes/origin'], path)
        unique = capture(f'wt-{index:02}-unpublished.txt', ['git', 'log', fields['HEAD'], '--not', '--remotes=origin', '--format=%H %s'], path)
        fields.update(status_file=f'wt-{index:02}-status.txt', ignored_file=f'wt-{index:02}-ignored.txt',
                      origin_reachable=bool(remotes and remotes.strip()), unpublished_commits=unique.splitlines() if unique else [],
                      classification='UNKNOWN' if status is None or ignored is None or unique is None else 'UNPUBLISHED_UNIQUE_WORK' if unique.strip() else 'NEEDED_FOR_REVIEW' if status.strip() else 'UNKNOWN',
                      retirement_authorized=False)
        inventory.append(fields)
    (output / 'worktree-inventory.json').write_text(json.dumps(inventory, indent=2), encoding='utf-8')
    manifest.append(dict(path='worktree-inventory.json', sha256=hashlib.sha256((output / 'worktree-inventory.json').read_bytes()).hexdigest()))
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print(json.dumps(dict(output=str(output), worktrees=len(inventory), open_prs=len(prs), failed_captures=[item['path'] for item in manifest if item.get('exit_code', 0)]), indent=2))


if __name__ == '__main__':
    main()
