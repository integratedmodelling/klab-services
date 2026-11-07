"""Coverage append must not merge against a fixture's reset coverage database."""
from pathlib import Path
import re
import pytest


def assert_ordered(text,label):
    # Jobs use separate runners/coverage databases. Never compare an append in
    # one job against an unrelated fixture reset in another job.
    jobs=re.split(r'(?m)^  [A-Za-z0-9_-]+:\s*$',text.split('jobs:',1)[-1])
    for job in jobs:
        lines=job.splitlines()
        appends=[i for i,line in enumerate(lines) if 'python -m pytest' in line and '--cov=klab_client ' in line and '--cov-append' in line]
        resets=[i for i,line in enumerate(lines) if 'python -m pytest' in line and '--cov=klab_simulator' in line and '--cov-append' not in line]
        if appends and resets:
            assert max(appends)<min(resets),f'{label}: SDK append follows fixture database reset in same job'


def test_sdk_coverage_appends_precede_any_simulator_coverage_reset():
    root=Path(__file__).resolve().parents[3]
    for workflow in (root/'.github/workflows').glob('*.yml'):
        assert_ordered(workflow.read_text(encoding='utf-8'),workflow.name)
    reset='          python -m pytest fixture --cov=klab_simulator.example'
    append='          python -m pytest controls --cov=klab_client --cov-append'
    with pytest.raises(AssertionError):assert_ordered('jobs:\n  same:\n'+reset+'\n'+append,'hostile multiline ordering')
    assert_ordered('jobs:\n  separate1:\n'+reset+'\n  separate2:\n'+append,'independent job databases')
    assert_ordered('jobs:\n  same:\n'+append+'\n'+reset,'correct multiline ordering')
