"""Index stable run receipts and private evidence identities without publishing their contents."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--final', action='store_true', help='Final reconciliation, not a claim of milestone completion')
    args = parser.parse_args()
    notes = Path(__file__).resolve().parent
    evidence = notes.parents[2] / '.consolidation-evidence'
    index_file = notes / 'evidence-index.json'
    old = json.loads(index_file.read_text())
    timestamp = dt.datetime.now(dt.UTC).isoformat()
    entries = {row['path']: row for row in old['entries']}
    for directory in sorted(evidence.iterdir()):
        if not directory.is_dir():
            continue
        if directory.name.startswith(('managed-agent-', 'isolated-science-stack-')) and not (directory / 'result.json').exists():
            continue
        # Snapshot top-level receipts/logs only: do not traverse databases, homes,
        # signing material, caches or mutable service state. Nested focused test
        # receipts get separate entries below. Raw files stay private.
        targets = [directory]
        targets += sorted({p.parent for p in directory.glob('**/run.json')
                           if not any(name in p.parts for name in ('home', 'broker-work', 'projects-v4', 'projects-v5'))})
        for target in dict.fromkeys(targets):
            files = []
            for file in sorted(target.iterdir()):
                if (not file.is_file() or file.is_symlink()
                        or file.name.startswith(('private-', 'scientist', 'fixture-authority'))
                        or file.suffix not in ('.json', '.log', '.txt', '.patch', '.xml', '.stdout', '.stderr', '.py')):
                    continue
                with file.open('rb') as stream:
                    digest = hashlib.file_digest(stream, 'sha256').hexdigest()
                files.append({'path': file.name, 'sha256': digest, 'size': file.stat().st_size})
            if files:
                entries[str(target)] = {'path': str(target), 'kind': 'private_archive', 'files': files,
                                        'indexed_at_utc': timestamp, 'final_closeout': args.final}
    old.update(recorded_at_utc=timestamp, entries=list(entries.values()), shared_raw_logs=False,
               reconciliation=('FINAL_INCOMPLETE_EXTERNAL_COMPONENT_MERGE' if args.final else 'IN_PROGRESS')
               + '; original entries retained, no original failure waived')
    index_file.write_text(json.dumps(old, indent=2) + '\n')
    text = ['# Evidence index', '', f'Reconciled {timestamp}. **Milestone incomplete**.', '',
            'Raw service logs, private arguments, signed identities and recovery archives remain local; no raw credentials are published.',
            'Machine-readable SHA256/size records are in `evidence-index.json`. Earlier failures and baseline entries remain retained.', '',
            '## Current navigation', '',
            '- `PR_LEDGER.md` / `pr-ledger.json`: live verified GitHub state and preserved original baseline.',
            '- `SCIENTIFIC_ACCEPTANCE.md`: installed-client real Java numerical/provenance/restoration/cleanup results and limits.',
            '- `DEPENDENCY_MANIFEST.md`: flattened component identity, packaged HM/host probes and unresolved upstream adoption.',
            '- `PR_CONTRIBUTOR_REVIEWS.md` / `RECEIPT_REPAIR_REVIEW.md`: author-agent scoped reviews, not maintainer approvals.',
            '- `WORKTREE_INVENTORY.md`: original42 plus subsequent6 retirement evidence, retained purposes and current count.',
            '- `progress-ledger.json` / `CLOSEOUT_SUMMARY.md`: verified reconciliation and exact remaining external action.', '',
            '## Newly consolidated private evidence directories', '']
    text += [f'- `{row["path"]}` — {len(row["files"])} indexed files' for row in entries.values() if str(evidence) in row['path']]
    (notes / 'EVIDENCE_INDEX.md').write_text('\n'.join(text) + '\n', encoding='utf-8')
    print(json.dumps({'entries': len(entries), 'index_sha256': hashlib.sha256(index_file.read_bytes()).hexdigest()}))


if __name__ == '__main__':
    main()
