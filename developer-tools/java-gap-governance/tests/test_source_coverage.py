"""Fail-closed source and same-PR coverage checks, not native acceptance."""
import ast
import importlib.util
from pathlib import Path

import pytest

SPEC = importlib.util.spec_from_file_location('source_coverage', Path(__file__).resolve().parents[1] / 'source_coverage.py')
coverage = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(coverage)


def test_snapshot_covers_source_modules_and_scenarios():
    result = coverage.snapshot()
    assert result['modules'] and result['scenarios']
    assert all(row['java_gap_ids'] for row in result['modules'])
    assert coverage.main([]) == 0


def test_new_module_cannot_silently_inherit_umbrella(tmp_path, monkeypatch):
    (tmp_path / 'new_protocol.py').write_text('new = 1\n')
    monkeypatch.setattr(coverage, 'SOURCE', tmp_path)
    with pytest.raises(ValueError, match='Unclassified'):
        coverage.snapshot()


def test_scenarios_are_ast_only_and_refuse_computed_values():
    assert coverage.scenarios(ast.parse('SCENARIOS = ("a",)\nSCENARIOS += ("b",)')) == ['a', 'b']
    with pytest.raises((ValueError, KeyError)):
        coverage.scenarios(ast.parse('SCENARIOS = dangerous_call()'))


@pytest.mark.parametrize('missing', ['canonical', 'register', 'test'])
def test_same_pr_requires_each_companion(missing):
    files = {'source': 'python-client/src/klab_client/example.py',
             'canonical': 'docs/java-service-contract-gaps/JAVA_GAPS_FOR_FERDINANDO.md',
             'register': 'docs/java-service-contract-gaps/gaps.yaml',
             'test': 'python-client/tests/unit/test_example.py'}
    with pytest.raises(ValueError, match='same PR'):
        coverage.same_pr('unused', [path for kind, path in files.items() if kind != missing])
    coverage.same_pr('unused', list(files.values()))
