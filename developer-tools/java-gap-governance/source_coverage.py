"""Conservative source-change gate, independent of the hand-maintained case index.

All simulator modules are explicitly classified. The snapshot includes every AST
string that looks path-like (including non-routes), every top-level scenario and
normalized source hashes. Hash changes force review; they do not prove semantics.
"""
import argparse
import ast
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'docs/java-service-contract-gaps'
SOURCE = ROOT / 'developer-tools/klab-service-simulator/src/klab_simulator'
# Explicit module families; adding a module requires a reviewed classification.
FAMILIES = {
    '0001,0013,0014': ['fixtures', 'provider_avro'],
    '0002,0009,0013': ['resource_imports', 'authoring'],
    '0003': ['model_candidates'],
    '0004,0017': ['persistence'],
    '0005': ['notifications'],
    '0006,0014': ['read_plans', 'reference_windows', 'reference_raster_labels'],
    '0007': ['composition'],
    '0008': ['managed_agents', 'managed_behaviors'],
    '0009': ['drafts', 'accepted_documents'],
    '0010,0014': ['exports'],
    '0011,0013': ['semantics', 'source_validation'],
    '0013': ['__init__', '__main__', 'contracts', 'compare', 'dependencies',
             'dependency_graphs', 'observation_selections', 'material_selections', 'replay'],
    '0013,0015': ['shared_access'],
    '0013,0016': ['input_provenance', 'provenance_fixture'],
    '0016': ['commits', 'graph_commits', 'revision_snapshots', 'execution_explanations'],
    '0017': ['workers'],
    '0001,0002,0003,0004,0005,0006,0007,0008,0009,0010,0011,0012,0013,0014,0015,0016,0017': ['state', 'server'],
}
SCENARIO_FAMILIES = {
    '0013,0014': ['elevation', 'zero-negative-nodata', 'empty-result', 'malformed-empty',
                  'failure', 'http500', 'nonuniform-export', 'provider-avro'],
    '0001,0014': ['usgs-recorded'],
    '0010,0014': ['export-interrupted', 'export-media-mismatch', 'export-denied',
                  'declared-band-export', 'category-code-export'],
    '0002,0013': ['import-workflow'],
    '0011,0013': ['semantic-unavailable', 'source-validation'],
    '0003': ['candidate-sources'],
    '0016': ['provenance-dependencies', 'revision-convergence', 'commit-inspection',
             'graph-convergence', 'execution-explanation'],
    '0007': ['copied-composition'],
    '0013': ['observation-selections', 'material-selections', 'dependency-inspection',
             'dependency-graphs', 'annotated-input'],
    '0015': ['shared-read'],
    '0009': ['accepted-documents'],
    '0008': ['managed-behavior'],
    '0006,0014': ['selected-read-plans'],
}
# Current queue preserves historical omissions; supplemental native dependencies
# are explicit here rather than rewriting any original acceptance wording/history.
SUPPLEMENTAL_ROWS = {
    'RC-P0': ['JAVA-GAP-0013', 'JAVA-GAP-0018'],
    'RC-P3.5': ['JAVA-GAP-0004', 'JAVA-GAP-0015', 'JAVA-GAP-0016'],
    'RC-P2.4': ['JAVA-GAP-0014'],
    'CAP-08': ['JAVA-GAP-0013', 'JAVA-GAP-0014'],
    'CAP-18': ['JAVA-GAP-0013', 'JAVA-GAP-0015'],
    'RC-R2': ['JAVA-GAP-0015'],
}


def scenarios(tree):
    values = {}
    def evaluate(node):
        if isinstance(node, ast.Name):
            return values[node.id]
        if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
            return evaluate(node.left) + evaluate(node.right)
        value = ast.literal_eval(node)
        if not isinstance(value, tuple) or not all(isinstance(x, str) for x in value):
            raise ValueError('Scenario expression is not an explicit string tuple')
        return value
    for node in tree.body:
        target = None
        if isinstance(node, ast.AnnAssign):
            target = node.target
        elif isinstance(node, ast.Assign) and len(node.targets) == 1:
            target = node.targets[0]
        elif isinstance(node, ast.AugAssign):
            target = node.target
        if isinstance(target, ast.Name) and target.id in ('SCENARIOS', 'EXPORT_SCENARIOS'):
            value = evaluate(node.value)
            if isinstance(node, ast.AugAssign):
                if not isinstance(node.op, ast.Add):
                    raise ValueError('Unknown scenario mutation')
                value = values[target.id] + value
            values[target.id] = value
    result = values['SCENARIOS']
    if len(result) != len(set(result)):
        raise ValueError('Duplicate source scenario')
    return sorted(result)


def snapshot():
    classifications = {module: ['JAVA-GAP-' + n for n in family.split(',')]
                       for family, modules in FAMILIES.items() for module in modules}
    files = sorted(SOURCE.glob('*.py'))
    if set(classifications) != {file.stem for file in files}:
        raise ValueError('Unclassified/new/deleted simulator module; review canonical coverage')
    records = []
    for path in files:
        text = path.read_text(encoding='utf-8')
        tree = ast.parse(text)
        records.append({'path': path.relative_to(ROOT).as_posix(),
                        'sha256_lf_utf8': hashlib.sha256(text.encode('utf-8')).hexdigest(),
                        'java_gap_ids': classifications[path.stem],
                        'path_like_literals': sorted({node.value for node in ast.walk(tree)
                            if isinstance(node, ast.Constant) and isinstance(node.value, str)
                            and node.value.startswith('/') and len(node.value) > 1})})
    names = scenarios(ast.parse((SOURCE / 'state.py').read_text(encoding='utf-8')))
    mapped = {name: ['JAVA-GAP-' + n for n in family.split(',')]
              for family, members in SCENARIO_FAMILIES.items() for name in members}
    if set(mapped) != set(names):
        raise ValueError('Unclassified/new/deleted source scenario')
    queue = json.loads((ROOT / 'notes/python-roadmap-execution/roadmap-execution-queue.json').read_text(encoding='utf-8'))
    rows = [{'id': row['id'], 'java_gap_ids': row['java_gap_ids'] or SUPPLEMENTAL_ROWS.get(row['id'], []),
             'basis': 'current queue' if row['java_gap_ids'] else 'explicit supplemental native dependency'}
            for row in queue['requirements']]
    if len(rows) != 42 or len({row['id'] for row in rows}) != 42 or not all(row['java_gap_ids'] for row in rows):
        raise ValueError('Missing/duplicate/unclassified current roadmap native dependency')
    ids = {gap['id'] for gap in json.loads((DATA / 'gaps.yaml').read_text(encoding='utf-8'))['gaps']}
    if any(not set(row['java_gap_ids']) <= ids for row in [*records, *rows]):
        raise ValueError('Source/roadmap classification links an unknown canonical ID')
    return {'version': 1, 'meaning': 'Conservative source custody, not exhaustive semantic proof or native parity',
            'scenarios': [{'name': name, 'java_gap_ids': mapped[name]} for name in names],
            'modules': records, 'current_roadmap_rows': rows}


def same_pr(base, changed=None):
    if changed is None:
        changed = subprocess.check_output(['git', 'diff', '--name-only', base + '...HEAD'],
                                          cwd=ROOT, text=True,
                                          creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0)).splitlines()
    changed = set(changed)
    prefixes = ('python-client/src/', 'python-worker/src/',
                'developer-tools/klab-service-simulator/src/')
    if any(path.startswith(prefixes) for path in changed):
        required = {'docs/java-service-contract-gaps/JAVA_GAPS_FOR_FERDINANDO.md',
                    'docs/java-service-contract-gaps/gaps.yaml'}
        if not required <= changed or not any('/tests/' in path and path.endswith('.py') for path in changed):
            raise ValueError('Source contract changes require canonical Markdown, register and runnable tests in the same PR')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true')
    parser.add_argument('--base')
    args = parser.parse_args(argv)
    value = snapshot()
    path = DATA / 'simulator-source-inventory.json'
    if args.write:
        path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    elif not path.is_file() or json.loads(path.read_text(encoding='utf-8')) != value:
        raise ValueError('Simulator source inventory drift: review source classification and canonical records before --write')
    if args.base:
        same_pr(args.base)
    print(json.dumps({'modules': len(value['modules']), 'source_scenarios': len(value['scenarios'])}))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
