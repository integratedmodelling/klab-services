"""Coverage append must not merge against a fixture's reset coverage database."""
from pathlib import Path


def test_sdk_coverage_appends_precede_any_simulator_coverage_reset():
    root=Path(__file__).resolve().parents[3]
    for workflow in (root/'.github/workflows').glob('*.yml'):
        lines=workflow.read_text(encoding='utf-8').splitlines()
        appends=[i for i,line in enumerate(lines) if 'run: python -m pytest' in line and '--cov=klab_client ' in line and '--cov-append' in line]
        resets=[i for i,line in enumerate(lines) if 'run: python -m pytest' in line and '--cov=klab_simulator' in line and '--cov-append' not in line]
        if appends and resets:
            assert max(appends)<min(resets),f'{workflow.name}: SDK append follows fixture database reset'
