"""Actual code-entry evidence is separate from assertions and native acceptance."""
import asyncio
import importlib.util
import json
from pathlib import Path
import sys
import threading

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools'))
from operation_trace import OperationTrace
from operation_attribution import reconcile


@pytest.fixture
def measured(tmp_path):
    path = tmp_path / 'public.py'
    path.write_text('''from abc import abstractmethod
def public():
    return "not retained"
async def awaited():
    import asyncio
    await asyncio.sleep(0)
    return 1
class Surface:
    @abstractmethod
    def abstract(self): pass
    def unsupported(self):
        raise UnsupportedOperationError()
''')
    spec = importlib.util.spec_from_file_location('trace_fixture', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return tmp_path, module


def test_calls_resumes_and_safe_references_are_not_counts_or_functional_acceptance(measured):
    source, module = measured
    trace = OperationTrace(source)
    trace.start()
    try:
        trace.begin('tests/test_public.py::test_workflow[private-parameter]')
        assert module.public() == 'not retained'
        assert asyncio.run(module.awaited()) == 1
        trace.finish('PASSED')
    finally:
        trace.stop()
    report = trace.report()
    assert {row['qualname'] for row in report['bindings']} == {'public', 'awaited'}
    assert len(report['bindings']) == 2  # Coroutine resume does not inflate presence.
    assert len(report['operations']) == 4  # Uncalled declarations remain in inventory.
    assert 'private-parameter' not in json.dumps(report) and 'not retained' not in json.dumps(report)
    report['tests'][0]['outcome'] = 'EDITED'
    assert trace.report()['tests'][0]['outcome'] == 'PASSED'
    attribution = reconcile(source, trace.report())
    assert len(attribution['operations']) == 4
    assert attribution['presence_counts']['NOT_OBSERVED_IN_THIS_TRACE'] == 2
    assert all(row['functional_assertion_evidence'] == 'NOT_ESTABLISHED_BY_TRACE'
               for row in attribution['operations'])
    altered = trace.report()
    altered['bindings'].append({'test': True, 'path': 'public.py', 'qualname': 'public'})
    with pytest.raises(ValueError):
        reconcile(source, altered)
    altered = trace.report()
    altered['profiler_ownership_lost'] = True
    with pytest.raises(ValueError):
        reconcile(source, altered)
    altered = trace.report()
    altered['source_sha256'] = {}
    with pytest.raises(ValueError):
        reconcile(source, altered)
    with pytest.raises(ValueError):
        trace.start()
    for field, bad in (('tests', [{'test_ref': 'unsafe[private]', 'outcome': 'PASSED'}]),
                       ('tests', [{'test_ref': 'safe', 'outcome': 'INVENTED'}]),
                       ('exhausted', 0), ('thread_scope', 'ALL_THREADS'),
                       ('bindings', [{'test': 0, 'path': 'foreign.py', 'qualname': 'other'}]),
                       ('bindings', [{'test': 0, 'path': 'public.py', 'qualname': 'public', 'extra': True}])):
        altered = trace.report()
        altered[field] = bad
        with pytest.raises(ValueError):
            reconcile(source, altered)


def test_profile_callback_decisions_directly_without_coverage_profiler_blind_spot(measured):
    from types import SimpleNamespace
    source, module = measured
    trace = OperationTrace(source, limit=1)
    trace.current = 0
    frame = SimpleNamespace(f_code=module.public.__code__)
    trace._profile(frame, 'return', None)
    trace._profile(SimpleNamespace(f_code=SimpleNamespace(co_filename=__file__)), 'call', None)
    trace._profile(SimpleNamespace(f_code=SimpleNamespace(co_filename=str(source / 'public.py'),
                                                       co_qualname='private_unlisted')), 'call', None)
    trace._profile(frame, 'call', None)
    trace._profile(SimpleNamespace(f_code=module.awaited.__code__), 'call', None)
    assert len(trace.events) == 1 and trace.exhausted
    trace.current = None
    trace._profile(frame, 'call', None)


def test_bounds_unknown_outcomes_and_source_drift_remain_explicit(measured):
    source, module = measured
    trace = OperationTrace(source, limit=1, test_limit=1)
    trace.start()
    try:
        trace.begin('test::one')
        module.public()
        asyncio.run(module.awaited())
        with pytest.raises(ValueError):
            trace.report()
        with pytest.raises(ValueError):
            trace.begin('overlap')
        trace.finish('FAILED')
        with pytest.raises(ValueError):
            trace.begin('test::two')
    finally:
        trace.stop()
    assert trace.report()['exhausted'] and len(trace.report()['bindings']) == 1
    (source / 'public.py').write_text('def changed(): pass\n')
    with pytest.raises(ValueError):
        trace.report()
    with pytest.raises(ValueError):
        trace.start()


def test_existing_and_replacement_profilers_preserved(measured):
    trace = OperationTrace(measured[0])
    hook = lambda *args: None
    sys.setprofile(hook)
    try:
        with pytest.raises(ValueError):
            trace.start()
        assert sys.getprofile() is hook
    finally:
        sys.setprofile(None)
    threading.setprofile(hook)
    try:
        with pytest.raises(ValueError):
            trace.start()
    finally:
        threading.setprofile(None)
    trace.start()
    sys.setprofile(hook)
    try:
        with pytest.raises(ValueError):
            trace.stop()
        assert sys.getprofile() is hook
        assert trace.report()['profiler_ownership_lost']
    finally:
        sys.setprofile(None)


def test_unknown_incomplete_and_foreign_thread_scopes_are_not_passes(measured):
    trace = OperationTrace(measured[0])
    with pytest.raises(ValueError):
        trace.begin('not-active')
    with pytest.raises(ValueError):
        trace.stop()
    trace.start()
    errors = []
    def foreign():
        for action in (lambda: trace.begin('foreign'), trace.stop, lambda: trace.finish('PASSED')):
            try:
                action()
            except ValueError:
                errors.append(True)
    try:
        worker = threading.Thread(target=foreign)
        worker.start()
        worker.join()
        assert len(errors) == 3
        for bad in ('', '\nunsafe', '[only-parameter]', None):
            with pytest.raises(ValueError):
                trace.begin(bad)
        trace.begin('test::incomplete')
        with pytest.raises(ValueError):
            trace.finish('INVENTED')
        measured[1].public()
    finally:
        trace.stop()
    assert trace.report()['tests'][0]['outcome'] == 'UNKNOWN'


@pytest.mark.parametrize('options', [{'limit': True}, {'limit': 0}, {'test_limit': 0}, {'test_limit': True}])
def test_invalid_bounds_refuse(measured, options):
    with pytest.raises(ValueError):
        OperationTrace(measured[0], **options)


def test_reconciler_cli_combines_run_ordinals_without_losing_failure_or_incompleteness(measured, tmp_path, monkeypatch):
    from operation_attribution import main
    source, module = measured
    trace = OperationTrace(source)
    trace.start()
    try:
        trace.begin('test::public[not-retained]')
        module.public()
        trace.finish('PASSED')
    finally:
        trace.stop()
    first = tmp_path / 'trace1.json'
    second = tmp_path / 'trace2.json'
    output = tmp_path / 'combined.json'
    first.write_text(json.dumps(trace.report()))
    report = trace.report()
    report['tests'][0]['outcome'] = 'FAILED'
    report['exhausted'] = True
    second.write_text(json.dumps(report))
    monkeypatch.setattr(sys, 'argv', ['operation_attribution', '--source', str(source), '--trace',
                                     str(first), str(second), '--output', str(output)])
    main()
    combined = json.loads(output.read_text())
    assert len(combined['runs']) == 2 and len(combined['tests']) == 2 and combined['trace_exhausted']
    public = next(row for row in combined['operations'] if row['operation'] == 'public')
    assert public['test_ordinals'] == [0, 1] and public['passed_test_presence']
    with pytest.raises(FileExistsError):
        main()


def test_explicit_assertion_mapping_requires_passed_exact_test_source_and_callable(measured):
    import hashlib
    from operation_attribution import attach_workflows
    source, module = measured
    test = source / 'test_mapping.py'
    test.write_text('def test_positive():\n    assert True\n')
    trace = OperationTrace(source)
    trace.start()
    try:
        trace.begin('test_mapping.py::test_positive')
        module.public()
        trace.finish('PASSED')
    finally:
        trace.stop()
    data = trace.report()
    data['test_source_sha256'] = {'test_mapping.py': hashlib.sha256(test.read_bytes()).hexdigest()}
    mapping = {'version': 1, 'workflows': [{'authority': 'LOCAL_DTO_ONLY',
                'test_ref': 'test_mapping.py::test_positive', 'operations': [['public.py', 'public']],
                'assertions': 'Explicit test-only mapping control, not a scientific assertion.'}]}
    report = attach_workflows(reconcile(source, data), mapping, source)
    assert report['workflow_mapping_counts']['MAPPED_PASSED_TEST_PRESENCE'] == 1
    data['test_source_sha256'] = {}
    report = attach_workflows(reconcile(source, data), mapping, source)
    assert report['workflow_mapping_counts'].get('MAPPED_PASSED_TEST_PRESENCE', 0) == 0
    for field, bad in (('authority', 'NATIVE_SCIENCE'), ('test_ref', 'test_mapping.py::absent'),
                       ('operations', [['foreign.py', 'unknown']]), ('assertions', '')):
        broken = {'version': 1, 'workflows': [{**mapping['workflows'][0], field: bad}]}
        with pytest.raises(ValueError):
            attach_workflows(reconcile(source, data), broken, source)
