"""Repository-wide changed-code checks need a repository-wide SDK test report."""
from pathlib import Path


def test_sdk_feature_coverage_gates_have_full_unit_inputs_before_checking():
    root = Path(__file__).resolve().parents[3]
    checked = 0
    for path in (root / '.github/workflows').glob('*.yml'):
        lines = path.read_text(encoding='utf-8').splitlines()
        gates = [i for i, line in enumerate(lines) if 'check_feature_coverage.py' in line
                 and '--package python-worker' not in line]
        if not gates:
            continue
        full = [i for i, line in enumerate(lines) if 'python -m pytest' in line
                and '--cov=klab_client' in line and (
                    'python-client/tests/unit --cov=' in line or 'tests/unit tests/integration' in line)]
        assert full and min(full) < min(gates), path.name + ': global diff gate lacks full SDK unit measurement'
        required = "python-client[dev,local,analysis,tabular,vector,notebook,events]"
        dependencies = [i for i, line in enumerate(lines) if 'pip install' in line and required in line]
        assert dependencies and min(dependencies) < min(full), path.name + ': full suite dependencies absent'
        checked += 1
    assert checked >= 20
