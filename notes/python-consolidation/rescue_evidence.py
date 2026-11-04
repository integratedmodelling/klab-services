"""Copy explicitly named historical Java reports, never rewrite their claims."""
import argparse
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    groups = [
        ('runtime-empty-change-fixed-evidence-20261103', 'runtime-empty-change-fixed-20261103.log'),
        ('runtime-server-inventory-evidence-20261103', 'runtime-server-inventory-20261103.log'),
        ('resources-integrated-evidence-20261103', 'resources-integrated-configured-20261103.log'),
        ('reasoner-final-evidence-20261103', 'reasoner-final-followup-20261103.log'),
    ]
    records = []
    for reports, log in groups:
        record = {'reports': reports, 'log': log, 'historical': True, 'final_candidate_evidence': False,
                  'source_sha': 'UNVERIFIED', 'command_environment': 'Recover from raw log; not inferred from directory date',
                  'totals': {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}, 'failing_tests': [], 'missing': []}
        for name in [reports, log]:
            source = args.source / name
            if source.is_dir():
                shutil.copytree(source, args.output / name)
            elif source.is_file():
                shutil.copy2(source, args.output / name)
            else:
                record['missing'].append(name)
        for report in (args.output / reports).rglob('TEST-*.xml'):
            suite = ET.parse(report).getroot()
            for key in record['totals']:
                record['totals'][key] += int(suite.get(key, 0))
            for case in suite.iter('testcase'):
                for failure in list(case):
                    if failure.tag in ['failure', 'error']:
                        record['failing_tests'].append({'classname': case.get('classname'), 'name': case.get('name'),
                                                       'kind': failure.tag, 'message': failure.get('message')})
        records.append(record)
    manifest = []
    for file in args.output.rglob('*'):
        if file.is_file():
            with file.open('rb') as stream:
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
            manifest.append({'path': str(file.relative_to(args.output)), 'sha256': digest, 'size': file.stat().st_size})
    (args.output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    (args.output / 'historical-report-summary.json').write_text(json.dumps(records, indent=2), encoding='utf-8')
    print(json.dumps(records, indent=2))


if __name__ == '__main__':
    main()
