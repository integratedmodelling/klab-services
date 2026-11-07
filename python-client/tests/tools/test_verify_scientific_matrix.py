"""Reference/recorded evidence must remain distinct from real scientific acceptance."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools'))
import verify_scientific_matrix as matrix


def test_scientific_recipe_requires_offline_http_and_public_multishard_groups(monkeypatch):
    calls = []
    def verify(argv, **kwargs):
        calls.append(kwargs)
        return kwargs['matrix_name']
    monkeypatch.setattr(matrix, 'verify', verify)
    assert matrix.main(['--capture-current-source']) == 'scientific-snapshot-reference'
    recipe = calls[0]
    assert recipe['requirements'] == ['RC-P2.1'] and len(recipe['groups']) == 3
    paths = [path for group in recipe['groups'].values() for path in group]
    assert any('test_read_plans.py' in path for path in paths)
    assert any('test_offline_snapshots.py' in path for path in paths)
    assert any('test_exports.py' in path for path in paths)
    assert 'not real native provider science' in recipe['scope']
    assert any('tolerance' in gate for gate in recipe['external_gates'])
