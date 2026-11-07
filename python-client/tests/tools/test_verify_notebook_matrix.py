"""Local kernel smoke must never replace actual public notebook workflow acceptance."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools'))
import verify_notebook_matrix as matrix


def test_notebook_recipe_keeps_guards_smoke_and_public_workflows_distinct(monkeypatch):
    calls = []
    def verify(argv, **kwargs):
        calls.append(kwargs)
        return kwargs['matrix_name']
    monkeypatch.setattr(matrix, 'verify', verify)
    assert matrix.main(['--capture-current-source']) == 'notebook-integrated'
    recipe = calls[0]
    assert recipe['requirements'] == ['RC-P3.5'] and len(recipe['groups']) == 3
    assert 'actual Bob denial' in recipe['scope'] and 'unmeasured' in recipe['scope']
    assert len(recipe['groups']['owned-public-sdk-notebook-workflows']) == 3
    assert any('principal_races' in path for path in recipe['groups']['owned-public-sdk-notebook-workflows'])
    assert any('permission' in gate for gate in recipe['external_gates'])
