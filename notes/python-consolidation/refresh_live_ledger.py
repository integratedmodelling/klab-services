"""Reconcile authoritative PR ledger with GitHub; preserve prior evidence and UNKNOWNs."""
import concurrent.futures
import datetime as dt
import json
import subprocess
from pathlib import Path


def main():
    notes = Path(__file__).resolve().parent
    root = notes.parents[1]
    timestamp = dt.datetime.now(dt.UTC)
    archive = root.parent / '.consolidation-evidence' / ('live-ledger-' + timestamp.strftime('%Y%m%dT%H%M%SZ'))
    archive.mkdir(exist_ok=False)
    files = ('pr-ledger.json', 'PR_LEDGER.md', 'progress-ledger.json')
    for name in files:
        (archive / name).write_bytes((notes / name).read_bytes())
    ledger = json.loads((notes / 'pr-ledger.json').read_text())
    fields = 'number,url,state,title,headRefName,headRefOid,baseRefName,baseRefOid,isDraft,mergedAt,mergeCommit,reviewDecision,reviews,statusCheckRollup'
    targets = [('integratedmodelling/klab-services', n) for n in (*range(133, 142), 191, 192, 194, 195)]
    targets.append(('integratedmodelling/klab.component.geospatial', 11))
    def fetch(target):
        repo, number = target
        result = json.loads(subprocess.check_output(['gh', 'pr', 'view', str(number), '--repo', repo, '--json', fields], text=True))
        result['repository'] = repo
        return result
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        records = list(pool.map(fetch, targets))
    (archive / 'github-current.json').write_text(json.dumps(records, indent=2))
    by_number = {p['number']: p for p in records if p['repository'].endswith('/klab-services')}
    for row in ledger['in_scope']:
        current = by_number[row['number']]
        row.setdefault('historical_baseline', {k: row[k] for k in ('head_sha', 'base_sha', 'base', 'head', 'status', 'checks')})
        row.update(status=current['state'], head_sha=current['headRefOid'], base_sha=current['baseRefOid'],
                   base=current['baseRefName'], head=current['headRefName'], draft=current['isDraft'],
                   checks=current['statusCheckRollup'], reviews=current['reviews'], merge_commit=current['mergeCommit'],
                   merged_at=current['mergedAt'], review_status='AUTHOR_AGENT_REVIEW; NOT_MAINTAINER_APPROVAL',
                   current_revision_tests='Source-matched installed-wheel focused gate PASS; see existing-pr-refresh-20261104',
                   blockers=[] if current['state'] == 'MERGED' else ['Verify exact-head hosted checks and normal merge requirements; genuine managed lifecycle passed in managed-agent-owned-v5-bounded-read-20261104'])
    ledger.update(recorded_at_utc=timestamp.isoformat(), current_remote_records=records,
                  current_raw_archive=str(archive), reconciliation='LIVE_VERIFIED; final milestone decision in CLOSEOUT_SUMMARY.md')
    (notes / 'pr-ledger.json').write_text(json.dumps(ledger, indent=2) + '\n')
    lines = ['# PR ledger', '', f'Live reconciliation {timestamp.isoformat()}; **milestone incomplete**.', '',
             'Original baseline/history remains in JSON and private immutable evidence. Normal merges only; no approval/protection bypass.', '',
             '| PR | State | Exact head | Merge commit |', '|---|---|---|---|']
    for p in records:
        label = f'component #{p["number"]}' if 'component' in p['repository'] else f'#{p["number"]}'
        lines.append(f'| [{label}]({p["url"]}) | {p["state"]}{" / DRAFT" if p["isDraft"] else ""} | `{p["headRefOid"]}` | `{(p["mergeCommit"] or {}).get("oid", "-")}` |')
    lines += ['', '## Remaining work', '',
              '- #138: genuine managed Runtime lifecycle passed (READY/RUNNING/STOPPED); see current GitHub state above. Unconfirmed publication and UNKNOWN remote cleanup retained.',
              '- #195: explicit operator-owned broker configuration; see live state above. Local224-test reactor and hosted checks pass.',
              '- Component #11: compatibility/science gates pass; account has no upstream push/merge permission. An upstream writer must merge or grant permission.',
              '- Issue #193 preserves original frozen ODO assertion and large-worldview incompatibility, not relabelled scientific success.',
              '- Final source/package/test reconciliation and cleanup are recorded in CLOSEOUT_SUMMARY.md and their referenced evidence; completion still requires the component upstream merge.']
    (notes / 'PR_LEDGER.md').write_text('\n'.join(lines) + '\n')
    progress = json.loads((notes / 'progress-ledger.json').read_text())
    progress.update(recorded_at_utc=timestamp.isoformat(), prs_merged=[p['url'] for p in records if p['state'] == 'MERGED'],
                    prs_unresolved=[p['url'] for p in records if p['state'] != 'MERGED'],
                    pr_in_scope_count=len(records), current_github_snapshot=str(archive / 'github-current.json'))
    progress['resume_pass']['broker_configuration'] = {'pr': 195, 'head': by_number[195]['headRefOid'],
        'local_reactor': '224 tests PASS, no failures/errors/skips',
        'managed_acceptance': 'PASS: genuine Runtime READY/RUNNING/STOPPED; start/stop publication UNCONFIRMED; remote cleanup UNKNOWN'}
    (notes / 'progress-ledger.json').write_text(json.dumps(progress, indent=2) + '\n')
    print(json.dumps({'archive': str(archive), 'merged': len(progress['prs_merged']), 'unresolved': progress['prs_unresolved']}))


if __name__ == '__main__':
    main()
