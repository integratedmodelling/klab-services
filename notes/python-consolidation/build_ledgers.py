"""Render baseline ledgers from captured evidence, without changing remote state."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', required=True, type=Path)
    parser.add_argument('--preservation', required=True, type=Path)
    args = parser.parse_args()
    destination = Path(__file__).resolve().parent
    root = destination.parents[1]
    timestamp = dt.datetime.now(dt.timezone.utc).isoformat()
    def load(name):
        return json.loads((args.baseline / name).read_text(encoding='utf-8'))
    def git(*cmd):
        return subprocess.run(['git', *cmd], cwd=root, capture_output=True, check=True).stdout.decode().strip()
    def contains(tip, commit):
        result = subprocess.run(['git', 'merge-base', '--is-ancestor', commit, tip], cwd=root, capture_output=True)
        if result.returncode not in [0, 1]:
            raise RuntimeError(result.stderr.decode())
        return result.returncode == 0
    def save(name, value):
        (destination / name).write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    delivery = git('rev-parse', 'origin/feature/python-experiment-runner')
    develop = git('rev-parse', 'origin/develop')
    prs = []
    for summary in load('open-prs.json'):
        record = load(f'pr-{summary["number"]}.json')
        paths = [item['path'] for item in record['files']]
        kind = 'DOCUMENTATION' if record['number'] in [133, 137] else 'FIXTURE_ONLY' if record['number'] == 140 else 'MIXED'
        prs.append(dict(number=record['number'], url=record['url'], title=record['title'],
                        classification=kind, status='BLOCKED', base=record['baseRefName'],
                        base_sha=record['baseRefOid'], head=record['headRefName'], head_sha=record['headRefOid'],
                        draft=record['isDraft'], reviews=record['reviews'], checks=record['statusCheckRollup'],
                        head_reachable_from_delivery=contains(delivery, record['headRefOid']),
                        head_reachable_from_develop=contains(develop, record['headRefOid']),
                        files=paths, evidence=f'pr-{record["number"]}.json',
                        review_status='NOT_STARTED', current_revision_tests='NOT_RUN',
                        blockers=['General build failed; inspect and reconcile source/language baseline',
                                  'Jenkins ERROR; underlying cause not yet verified',
                                  'Read-only contribution review on actual integration base not yet completed']))
    edges = []
    for child in prs:
        for parent in prs:
            if parent != child and parent['head'] == child['base']:
                edges.append(dict(parent=parent['number'], child=child['number'],
                                  current_parent_head_is_ancestor=contains(child['head_sha'], parent['head_sha']),
                                  captured_base_matches_parent_head=child['base_sha'] == parent['head_sha']))
    historical = []
    for pr in load('all-prs.json'):
        if pr['number'] < 133:
            continue
        commit = (pr.get('mergeCommit') or {}).get('oid')
        historical.append(dict(**pr, merge_reachable_from_delivery=contains(delivery, commit) if commit else None,
                               merge_reachable_from_develop=contains(develop, commit) if commit else None,
                               classification='NOT_YET_AUDITED'))
    save('pr-ledger.json', dict(recorded_at_utc=timestamp, delivery_sha=delivery, develop_sha=develop,
                                baseline_archive=str(args.baseline), in_scope=prs, dependency_edges=edges,
                                historical_pr_census=historical))
    lines = ['# PR ledger', '', f'Baseline captured 2026-11-04; rendered {timestamp}.', '',
             f'Delivery `{delivery}`; fetched develop `{develop}`.', '',
             'No remote merges, approvals, retargets or draft changes performed. All nine existing PRs remain mandatory.', '',
             '| PR | Kind | Base | Head | State | Current review/tests |', '|---|---|---|---|---|---|']
    lines += [f'| [#{p["number"]}]({p["url"]}) | {p["classification"]} | `{p["base"]}` | `{p["head_sha"]}` | BLOCKED / {"DRAFT" if p["draft"] else "OPEN"} | NOT_STARTED / NOT_RUN |' for p in sorted(prs, key=lambda p:p['number'])]
    lines += ['', '## Observed dependency edges', '']
    lines += [f'- #{e["parent"]} → #{e["child"]}: current parent head ancestry={e["current_parent_head_is_ancestor"]}; captured base matches={e["captured_base_matches_parent_head"]}.' for e in edges]
    lines += ['', 'The #135 API base SHA differs from the current #134 branch tip. Use actual ancestry, not API base SHA alone.',
              'Historical PR census, merge ancestry, exact checks, file lists and review decisions are in `pr-ledger.json`.',
              'No submitted reviews were present in the baseline for these nine PRs. This is not proof that approval is unnecessary.',
              'Delivery branch reports `protected=false`; protection endpoint returned 404 and repository rulesets returned [].',
              'Account has push/triage, not maintain/admin. Do not bypass failing checks or infer maintainer acceptance.']
    (destination / 'PR_LEDGER.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    inventory = load('worktree-inventory.json')
    preservation = json.loads((args.preservation / 'preserved-worktrees.json').read_text(encoding='utf-8'))
    lines = ['# Worktree inventory', '', f'Baseline: {len(inventory)} services worktrees (61 pre-existing plus this new consolidation checkout).',
             'A separate geospatial repository has one worktree and is not included in that count.', '',
             'All paths are retained. UNKNOWN is deliberate: ignored outputs, locks, evidence and purpose still require inspection.',
             'No path is yet SAFE_TO_RETIRE. A verified bundle does not by itself authorize removal.', '',
             '| Worktree | HEAD | Initial class | Unpublished count | Preservation ref |', '|---|---|---|---|---|']
    for item, saved in zip(inventory, preservation, strict=True):
        lines.append(f'| `{item["worktree"]}` | `{item["HEAD"]}` | {item["classification"]} | {len(item["unpublished_commits"])} | `{saved["archive_ref"] or "origin-reachable"}` |')
    lines += ['', f'Private archive: `{args.preservation}`.',
              'Archive contains per-worktree status, staged/unstaged binary patches and nonignored untracked regular files.',
              'Ignored artifacts are enumerated, not all copied. Unique raw scientific reports must still be rescued before retirement.',
              'Known uncommitted receipt repairs remain in the upstream-integration and selection-submission worktrees; not imported.',
              'Baseline processes showed no Java/Python workload. Recheck processes and locks immediately before any removal.']
    (destination / 'WORKTREE_INVENTORY.md').write_text('\n'.join(lines)+'\n', encoding='utf-8')
    statuses = {'baseline': 'PASSED', 'dependency_audit': 'IN_PROGRESS', 'pr_issue_acceptance_audit': 'IN_PROGRESS',
                'unique_commit_dirty_file_preservation': 'PASSED', 'raw_scientific_evidence_rescue': 'IN_PROGRESS',
                'worktree_retirement': 'NOT_STARTED', 'per_pr_review_tests_merges': 'BLOCKED',
                'combined_java_acceptance': 'NOT_RUN', 'installed_python_real_java_science': 'NOT_RUN',
                'final_remote_reconciliation': 'NOT_STARTED'}
    save('progress-ledger.json', dict(milestone='dependency-closeout-repository-consolidation',
                                     status='MILESTONE_INCOMPLETE', recorded_at_utc=timestamp,
                                     integration_base_sha=delivery, fetched_develop_sha=develop,
                                     final_candidate_sha=None, gates=[dict(id=key,status=value,recorded_at_utc=timestamp) for key,value in statuses.items()],
                                     evidence=[dict(path=str(path/'manifest.json'),sha256=hashlib.sha256((path/'manifest.json').read_bytes()).hexdigest()) for path in [args.baseline,args.preservation]]))
    print('Rendered PR, worktree and authoritative progress ledgers')


if __name__ == '__main__':
    main()
