"""Import read-only gh issue-list receipts as sanitized metadata, preserving history.

Inputs are private {exit, stdout, stderr} collection receipts; no GitHub writes.
Bodies/comments stay in original custody; hashes attest exactly what was reviewed.
"""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'docs/java-service-contract-gaps'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--snapshot-dir', required=True, type=Path)
    args = parser.parse_args(argv)
    provenance = json.loads((args.snapshot_dir / 'provenance.json').read_text(encoding='utf-8'))
    rows = []
    for repository in ('klab-services', 'klab.component.geospatial'):
        wrapper = json.loads((args.snapshot_dir / (repository + '-issues.json')).read_text(encoding='utf-8'))
        if wrapper['exit'] != 0:
            raise ValueError('Failed issue collection cannot be imported')
        for issue in json.loads(wrapper['stdout']):
            rows.append({key: issue[key] for key in ('number', 'url', 'title', 'state', 'assignees', 'labels', 'updatedAt', 'closedAt')}
                        | {'repository': 'integratedmodelling/' + repository,
                           'body_sha256_utf8': hashlib.sha256(issue['body'].encode('utf-8')).hexdigest(),
                           'comments': [{'url': c['url'], 'body_sha256_utf8': hashlib.sha256(c['body'].encode('utf-8')).hexdigest()}
                                        for c in issue['comments']]})
    old = json.loads((DATA / 'issues.json').read_text(encoding='utf-8'))
    if not {i['url'] for i in old['issues']} <= {i['url'] for i in rows}:
        raise ValueError('Historical issues missing from complete live audit')
    value = {'queried_utc': provenance['queried_utc'], 'audited_commit': provenance['repository_head'],
             'github_writes': False, 'scope': 'All issues in both repositories, state=all, limit=1000; titles/states/owners/labels preserved; no closure decision inferred',
             'issues': rows}
    path = DATA / 'live-issue-review.json'
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'issues': len(rows), 'open': sum(i['state'] == 'OPEN' for i in rows)}))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
