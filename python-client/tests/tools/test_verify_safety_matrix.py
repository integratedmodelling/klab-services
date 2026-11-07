"""Safety recipe includes fresh process noextras rather than extras-only success."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools'))
import verify_safety_matrix as matrix


def test_safety_recipe_requires_nooptional_offline_and_partial_http(monkeypatch):
    calls = []
    def verify(argv, **kwargs):
        calls.append(kwargs)
        return kwargs['matrix_name']
    monkeypatch.setattr(matrix, 'verify', verify)
    assert matrix.main(['--capture-current-source']) == 'safety-regression'
    recipe = calls[0]
    assert recipe['requirements'] == ['RC-P3.2', 'CAP-08', 'CAP-12', 'CAP-18']
    assert len(recipe['groups']) == 3
    assert any('test_core_optional_isolation.py' in path for path in
               recipe['groups']['isolated-public-core-nooptional-offline'])
    assert 'no implicit replay/fallback' in recipe['scope']
    assert any('OS/container' in gate for gate in recipe['external_gates'])
