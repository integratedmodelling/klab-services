"""Opt-in pytest adapter for bounded, single-process SDK code-entry attribution."""
import json
import hashlib
from pathlib import Path

import pytest

from operation_trace import OperationTrace


def pytest_addoption(parser):
    group = parser.getgroup('operation attribution')
    group.addoption('--operation-trace-output', default=None)
    group.addoption('--operation-trace-source', default=None)
    group.addoption('--operation-trace-limit', type=int, default=100000)


def pytest_configure(config):
    output = config.getoption('--operation-trace-output')
    if not output:
        return
    source = config.getoption('--operation-trace-source')
    if not source:
        raise pytest.UsageError('Explicit exact SDK source directory required')
    if getattr(config.option, 'numprocesses', None) or hasattr(config, 'workerinput'):
        raise pytest.UsageError('Parallel pytest requires separate explicit attribution; unsupported here')
    import klab_client
    if Path(klab_client.__file__).resolve().parent != Path(source).resolve():
        raise pytest.UsageError('Imported SDK differs from explicit trace source; no fallback')
    target = Path(output)
    if target.exists():
        raise pytest.UsageError('Refuse overwriting prior trace evidence')
    config.pluginmanager.register(TracePlugin(source, target, config.getoption('--operation-trace-limit')),
                                  'bounded-operation-attribution')


class TracePlugin:
    def __init__(self, source, output, limit):
        self.trace = OperationTrace(source, limit=limit)
        self.output = output
        self.reports = []
        self.test_sources = {}

    def pytest_sessionstart(self, session):
        self.trace.start()

    @pytest.hookimpl(hookwrapper=True)
    def pytest_runtest_protocol(self, item, nextitem):
        self.reports = []
        path = Path(item.path).resolve()
        repository = Path(__file__).resolve().parents[2]
        try:
            reference = path.relative_to(repository).as_posix()
        except ValueError:
            reference = path.as_posix()
        # Root-relative pytest nodeIDs can alias SDK and simulator tests. Use
        # exact executable file identity, with parameters removed by the tracer.
        suffix = item.nodeid.split('::', 1)[1] if '::' in item.nodeid else ''
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if reference in self.test_sources and self.test_sources[reference][1] != digest:
            raise ValueError('Executable test source changed during trace')
        self.test_sources[reference] = (path, digest)
        self.trace.begin(reference + ('::' + suffix if suffix else ''))
        try:
            yield
        finally:
            # Teardown failure overrides a passed call. Absent stages are not PASS.
            if any(report.failed for report in self.reports):
                outcome = 'FAILED'
            elif any(report.skipped for report in self.reports):
                outcome = 'SKIPPED'
            elif {report.when for report in self.reports} == {'setup', 'call', 'teardown'}:
                outcome = 'PASSED'
            else:
                outcome = 'ERROR'
            self.trace.finish(outcome)

    def pytest_runtest_logreport(self, report):
        self.reports.append(report)

    def pytest_sessionfinish(self, session, exitstatus):
        self.trace.stop()
        report = self.trace.report()
        for path, digest in self.test_sources.values():
            if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
                raise ValueError('Executable test source changed during trace')
        report['test_source_sha256'] = {reference: digest for reference, (path, digest) in self.test_sources.items()}
        report['pytest_exitstatus'] = int(exitstatus)
        self.output.parent.mkdir(parents=True, exist_ok=True)
        # Exclusive creation preserves failures/evidence from previous runs.
        with self.output.open('x', encoding='utf-8') as stream:
            json.dump(report, stream, indent=2)
            stream.write('\n')
