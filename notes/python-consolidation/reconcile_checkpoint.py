"""Record an explicitly INCOMPLETE checkpoint from actual local evidence."""
import datetime as dt
import hashlib
import json
import subprocess
from pathlib import Path


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    destination = Path(__file__).resolve().parent
    root = destination.parents[1]
    evidence = Path.home()/'AppData/Local/Temp/opencode'
    timestamp = dt.datetime.now(dt.UTC).isoformat()
    def load(path):
        return json.loads(path.read_text(encoding='utf-8'))
    def save(name, value):
        (destination/name).write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
    def git(*args):
        return subprocess.check_output(['git',*args],cwd=root,text=True).strip()
    prs = load(destination/'pr-ledger.json')
    wheel_root = evidence/'klab-pr-installed-wheel-gates-20261104'
    for pr in prs['in_scope']:
        directory = wheel_root/f'pr-{pr["number"]}'
        run = load(directory/'tests/run.json')
        output = (directory/'tests/stdout.log').read_text(encoding='utf-8')
        identity = load(directory/'installed-identity.json')
        pr['focused_installed_wheel'] = {'status': run['status'], 'candidate_sha': run['candidate_sha'],
                                        'result': output.strip().splitlines()[-1],
                                        'source_files_verified': len(identity['files']),
                                        'evidence': str(directory), 'run_sha256': sha(directory/'tests/run.json')}
        pr['head_reachable_from_local_candidate'] = subprocess.run(['git','merge-base','--is-ancestor',pr['head_sha'],'HEAD'],cwd=root,check=False).returncode==0
        pr['current_revision_tests'] = 'PASSED' if run['exit_code']==0 else 'FAILED'
        pr['test_scope'] = 'Focused original-head installed-wheel tests only; combined acceptance not implied'
        pr['review_status'] = 'BLOCKED' if pr['number']==140 else 'NOT_STARTED'
    for pr in prs['historical_pr_census']:
        commit = (pr.get('mergeCommit') or {}).get('oid')
        if not commit:
            continue
        files = git('diff','--name-only',commit+'^1',commit).splitlines()
        production = [p for p in files if '/src/main/' in p]
        tests = [p for p in files if '/src/test/' in p or '/tests/' in p]
        docs = [p for p in files if p.endswith('.md') or p.startswith('notes/')]
        pr.update(classification='MIXED' if production and (tests or docs) else 'PRODUCTION_FIX' if production else 'FIXTURE_ONLY' if tests else 'DOCUMENTATION' if docs else 'CODE_ONLY',
                  classification_method='Changed-path classification; not a full semantic review',
                  changed_files=files,
                  merge_reachable_from_local_candidate=subprocess.run(['git','merge-base','--is-ancestor',commit,'HEAD'],cwd=root,check=False).returncode==0)
    prs['local_candidate_sha'] = git('rev-parse','HEAD')
    prs['updated_at_utc'] = timestamp
    save('pr-ledger.json',prs)
    index = []
    for directory in sorted(evidence.glob('klab-*20261104*')):
        if 'wheel-env' in directory.name or 'recovery-check' in directory.name:
            continue
        if directory.is_file():
            index.append({'path': str(directory), 'sha256': sha(directory), 'kind': 'artifact'})
        elif directory.is_dir():
            files = [{'path': p.relative_to(directory).as_posix(), 'sha256': sha(p), 'size': p.stat().st_size}
                     for p in directory.rglob('*') if p.is_file()]
            index.append({'path': str(directory), 'kind': 'private_archive', 'files': files})
    save('evidence-index.json', {'recorded_at_utc': timestamp, 'shared_raw_logs': False, 'entries': index})
    lines=['# Evidence index','',f'Checkpoint recorded {timestamp}. **MILESTONE_INCOMPLETE**.',
           '','Raw logs, settings, process details and recoverable bundles remain local. Do not publish them without redaction.',
           'All listed files have SHA256 entries in `evidence-index.json`; run records include exact commands, revisions, dirty digests and times.',
           '','## Navigation','',
           '- `PR_LEDGER.md` / `pr-ledger.json`: live baseline, PR DAG, merge ancestry, focused wheel checks and unresolved reviews.',
           '- `ISSUE_ACCEPTANCE_MAP.md`: current issues, historical oracles and acceptance boundaries.',
           '- `DEPENDENCY_MANIFEST.md`: compatibility findings; raw lock-like manifests listed below.',
           '- `WORKTREE_INVENTORY.md`: every services worktree and preserved unpublished tips.',
           '- `INTEGRATION_REVIEW.md`, `COMPOSITION_REVIEW.md`, `PR140_REVIEW.md`: review scope and limitations.',
           '- `INTEGRATED_ACCEPTANCE.md`: exact candidate checks, not a combined PASS.',
           '- `progress-ledger.json`: authoritative milestone state.',
           '','## Private local archives','']
    lines += [f'- `{item["path"]}`'+(f' — SHA256 `{item["sha256"]}`' if 'sha256' in item else f' — {len(item["files"])} indexed files') for item in index]
    (destination/'EVIDENCE_INDEX.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    statuses={'baseline':'PASSED','dependency_audit':'IN_PROGRESS','pr_issue_acceptance_audit':'IN_PROGRESS',
              'unique_commit_dirty_file_preservation':'PASSED','raw_scientific_evidence_rescue':'IN_PROGRESS',
              'worktree_retirement':'BLOCKED','per_pr_review_tests_merges':'BLOCKED',
              'combined_java_acceptance':'FAILED','installed_python_real_java_science':'BLOCKED',
              'final_remote_reconciliation':'IN_PROGRESS'}
    blockers=[{'owner': 'implementing CLI / backend reviewer', 'action': 'Resolve or explicitly classify the reproduced issued-query-receipt error without weakening rollback/security assertions; rerun affected and combined gates'},
              {'owner': 'implementing CLI / scientific reviewer', 'action': 'Establish a separately versioned semantically verified fixture for current ODO/language source; preserve original failed oracle'},
              {'owner': 'implementing CLI / dependency maintainer', 'action': 'Verify HM0.12/GT34.x/H2/Jackson compatibility and targeted dependency change; upstream geospatial push permission is unavailable'},
              {'owner': 'implementing CLI / repository maintainers', 'action': 'Complete all nine per-PR reviews/current-base tests, hosted/Jenkins checks and required approvals, then merge in verified dependency order'},
              {'owner': 'implementing CLI / operator', 'action': 'Run exact installed combined client against an authorized nonconflicting owned Java stack; do not reuse or stop Modeler-owned services'},
              {'owner': 'implementing CLI', 'action': 'Finish worktree purpose/ignored-evidence/lock classification, dry-run safe removals, final verification and complete live census'}]
    current=git('rev-parse','HEAD')
    record={'milestone': 'dependency-closeout-repository-consolidation', 'status': 'MILESTONE_INCOMPLETE',
            'recorded_at_utc': timestamp, 'repository': 'integratedmodelling/klab-services',
            'integration_base_sha': prs['delivery_sha'], 'final_candidate_sha': current, 'final_candidate_verified': False,
            'java_tested_candidate_sha': '52197c41b01213628e1fbf4e5290933eb9350f2b',
            'dependency_manifest': {'path': str(evidence/'klab-dependency-artifacts-before-20261104.json'), 'sha256': sha(evidence/'klab-dependency-artifacts-before-20261104.json'), 'final': False},
            'pr_baseline_count': 9, 'pr_in_scope_count': 9, 'prs_unresolved': [p['url'] for p in sorted(prs['in_scope'],key=lambda x:x['number'])],
            'prs_merged': [], 'prs_approved_superseded': [], 'scientific_acceptance': 'BLOCKED',
            'evidence_index': 'notes/python-consolidation/EVIDENCE_INDEX.md', 'worktrees_initial': 61, 'worktrees_final': 62,
            'worktree_count_scope': 'services repository; geospatial repository separately has one retained worktree',
            'unique_unpublished_work_preserved': True, 'remaining_blockers': blockers,
            'gates': [{'id': k, 'status': v, 'recorded_at_utc': timestamp} for k,v in statuses.items()],
            'next_milestone': 'python-roadmap-reassessment'}
    save('progress-ledger.json',record)
    print(json.dumps({'candidate': current, 'status': record['status'], 'evidence_entries': len(index), 'open_prs': 9},indent=2))


if __name__=='__main__':
    main()
