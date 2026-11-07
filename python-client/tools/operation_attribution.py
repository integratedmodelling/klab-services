"""Reconcile every source callable with bounded test presence, never promote acceptance."""
import argparse
import ast
from collections import Counter
import json
from pathlib import Path

from operation_inventory import inventory


def attach_workflows(report, mapping, repository):
    """Validate explicit descriptions and link only actual passed test presence."""
    import hashlib
    if mapping.get('version') != 1 or not isinstance(mapping.get('workflows'), list):
        raise ValueError('Versioned explicit workflow mapping required')
    repository = Path(repository).resolve()
    operations = {(row['path'], (row['owner'] + '.' if row['owner'] else '') + row['operation']): row
                  for row in report['operations']}
    for row in operations.values():
        row['workflow_assertion_mappings'] = []
    for workflow in mapping['workflows']:
        if (set(workflow) != {'authority', 'test_ref', 'operations', 'assertions'}
                or workflow['authority'] not in ('LOCAL_DTO_ONLY', 'MOCK_HTTP_ONLY', 'SIMULATED_HTTP_ONLY',
                                                'ORCHESTRATION_DOUBLES_ONLY')
                or not isinstance(workflow['assertions'], str) or not 1 <= len(workflow['assertions']) <= 4096):
            raise ValueError('Explicit bounded assertion and nonnative authority description required')
        reference = workflow['test_ref']
        if not isinstance(reference, str) or reference.count('::') != 1 or '[' in reference:
            raise ValueError('Exact function-only executable workflow reference required')
        filename, function = reference.split('::')
        path = (repository / filename).resolve()
        if not path.is_relative_to(repository) or path.suffix != '.py' or path.stat().st_size > 2 * 1024 * 1024:
            raise ValueError('Bounded repository test source required')
        raw = path.read_bytes()
        tree = ast.parse(raw)
        nodes = [node for node in tree.body if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
                 and node.name == function]
        if len(nodes) != 1 or not any(isinstance(node, ast.Assert) or (
                isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and node.func.attr == 'raises'
                and isinstance(node.func.value, ast.Name) and node.func.value.id == 'pytest')
                for node in ast.walk(nodes[0])):
            raise ValueError('Mapping must identify an executable test with explicit assertions')
        for key in workflow['operations']:
            if not isinstance(key, list) or len(key) != 2 or tuple(key) not in operations:
                raise ValueError('Workflow maps an unknown source callable')
            row = operations[tuple(key)]
            passed = [index for index in row['test_ordinals'] if report['tests'][index]['test_ref'] == reference
                      and report['tests'][index]['outcome'] == 'PASSED'
                      and report.get('test_source_by_ordinal', {}).get(str(index)) == hashlib.sha256(raw).hexdigest()]
            row['workflow_assertion_mappings'].append({**workflow, 'test_source_sha256': hashlib.sha256(raw).hexdigest(),
                                                      'passed_test_ordinals': passed})
            if passed:
                row['functional_assertion_evidence'] = 'EXPLICIT_ASSERTION_DESCRIPTION_LINKED_TO_PASSED_TEST_PRESENCE_NOT_NATIVE_ACCEPTANCE'
    report['workflow_mapping_counts'] = dict(Counter('MAPPED_PASSED_TEST_PRESENCE' if any(
        entry['passed_test_ordinals'] for entry in row['workflow_assertion_mappings']) else 'MISSING'
        for row in report['operations']))
    return report


def reconcile(source, trace):
    import hashlib
    source = Path(source).resolve()
    expected = {path.relative_to(source).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
                for path in sorted(source.rglob('*.py'))}
    current = inventory(source)
    if (trace.get('evidence') != 'TEST_SCOPE_CALL_OR_RESUME_PRESENCE_NOT_FUNCTIONAL_OR_NATIVE_ACCEPTANCE'
            or trace.get('source_sha256') != expected or trace.get('operations') != current['operations']):
        raise ValueError('Exact trace source/inventory custody required')
    if trace.get('profiler_ownership_lost'):
        raise ValueError('Profiler custody lost; attribution cannot be published')
    tests = trace['tests']
    if (not isinstance(tests, list) or len(tests) > 100000
            or type(trace.get('exhausted')) is not bool
            or trace.get('thread_scope') != 'OWNING_THREAD_ONLY_OTHER_THREADS_AND_PROCESSES_UNMEASURED'
            or not isinstance(trace.get('bindings'), list) or len(trace['bindings']) > 1000000):
        raise ValueError('Bounded explicit trace scope required')
    for test in tests:
        if (not isinstance(test, dict) or set(test) != {'test_ref', 'outcome'}
                or test['outcome'] not in ('UNKNOWN', 'PASSED', 'FAILED', 'SKIPPED', 'ERROR')
                or not isinstance(test['test_ref'], str) or not 1 <= len(test['test_ref']) <= 4096
                or '[' in test['test_ref'] or any(ord(char) < 32 for char in test['test_ref'])):
            raise ValueError('Safe function-only test references and explicit outcomes required')
    bindings: dict[tuple[str, str], set[int]] = {}
    known = {(row['path'], (row['owner'] + '.' if row['owner'] else '') + row['operation'])
             for row in current['operations']}
    for row in trace['bindings']:
        if not isinstance(row, dict) or set(row) != {'path', 'qualname', 'test'}:
            raise ValueError('Exact operation association required')
        key = (row['path'], row['qualname'])
        index = row['test']
        if key not in known or type(index) is not int or not 0 <= index < len(tests):
            raise ValueError('Unknown operation or test association')
        bindings.setdefault(key, set()).add(index)
    records = []
    for row in current['operations']:
        name = (row['owner'] + '.' if row['owner'] else '') + row['operation']
        indices = sorted(bindings.get((row['path'], name), set()))
        records.append({**row, 'test_ordinals': indices,
                        'presence': 'CALL_OR_RESUME_OBSERVED' if indices else 'NOT_OBSERVED_IN_THIS_TRACE',
                        'passed_test_presence': any(tests[index]['outcome'] == 'PASSED' for index in indices),
                        'functional_assertion_evidence': 'NOT_ESTABLISHED_BY_TRACE',
                        'native_acceptance': 'NOT_ESTABLISHED'})
    return {'version': 1, 'evidence': 'PUBLIC_CALLABLE_TEST_PRESENCE_NOT_FUNCTIONAL_OR_NATIVE_ACCEPTANCE',
            'classifications': current['counts'], 'source_sha256': expected,
            'test_source_sha256': dict(trace.get('test_source_sha256', {})),
            'test_source_by_ordinal': dict(trace.get('test_source_by_ordinal', {
                str(index): trace.get('test_source_sha256', {}).get(test['test_ref'].split('::', 1)[0])
                for index, test in enumerate(tests)})),
            'trace_exhausted': trace['exhausted'], 'thread_scope': trace['thread_scope'],
            'pytest_exitstatus': trace.get('pytest_exitstatus'), 'tests': [dict(test) for test in tests],
            'presence_counts': dict(Counter(row['presence'] for row in records)), 'operations': records}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--trace', required=True, type=Path, nargs='+')
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--workflows', type=Path)
    parser.add_argument('--repository', type=Path)
    args = parser.parse_args()
    traces = [json.loads(path.read_text(encoding='utf-8')) for path in args.trace]
    # Verify each run independently before combining anonymous ordinals. Repeated
    # test function references remain distinct run/case scopes, never call counts.
    reports = [reconcile(args.source, trace) for trace in traces]
    merged = dict(traces[0])
    merged['tests'], merged['bindings'] = [], []
    merged['test_source_sha256'] = {}
    merged['test_source_by_ordinal'] = {}
    for trace in traces:
        for path, digest in trace.get('test_source_sha256', {}).items():
            if path in merged['test_source_sha256'] and merged['test_source_sha256'][path] != digest:
                raise ValueError('Cannot merge different executed test source bytes')
            merged['test_source_sha256'][path] = digest
        offset = len(merged['tests'])
        for index, test in enumerate(trace['tests']):
            merged['test_source_by_ordinal'][str(offset + index)] = trace.get('test_source_sha256', {}).get(
                test['test_ref'].split('::', 1)[0])
        merged['tests'].extend(trace['tests'])
        merged['bindings'].extend({**row, 'test': row['test'] + offset} for row in trace['bindings'])
    merged['exhausted'] = any(trace['exhausted'] for trace in traces)
    merged['pytest_exitstatus'] = None  # Separate run statuses retained below.
    result = reconcile(args.source, merged)
    result['runs'] = [{'tests': len(trace['tests']), 'pytest_exitstatus': report['pytest_exitstatus'],
                       'exhausted': trace['exhausted']} for trace, report in zip(traces, reports)]
    if args.workflows:
        if not args.repository:
            raise ValueError('Explicit repository required for reviewed test mappings')
        attach_workflows(result, json.loads(args.workflows.read_text(encoding='utf-8')), args.repository)
    with args.output.open('x', encoding='utf-8') as stream:
        json.dump(result, stream, indent=2)
        stream.write('\n')
    print(json.dumps(result['presence_counts'], sort_keys=True))


if __name__ == '__main__':
    main()
