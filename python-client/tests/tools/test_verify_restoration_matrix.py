"""Restoration recipe delegates exact independent public groups, not native approval."""
from pathlib import Path
import sys

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools'))
import verify_onboarding_matrix as shared
import verify_restoration_matrix as matrix


def test_explicit_restoration_recipe_preserves_separate_native_obligations(monkeypatch):
    calls = []
    def verify(argv, **kwargs):
        calls.append((argv, kwargs))
        return {'recipe': kwargs['matrix_name']}
    monkeypatch.setattr(matrix, 'verify', verify)
    assert matrix.main(['--capture-current-source']) == {'recipe': 'restoration-lifecycle'}
    assert calls[0][1]['requirements'] == ['RC-P1.2', 'RC-P3.3', 'CAP-07']
    assert len(calls[0][1]['groups']) == 3
    assert 'not native' in calls[0][1]['scope']
    assert any('incarnation' in gate for gate in calls[0][1]['external_gates'])
    assert any(path.endswith('test_integrated_restore.py') for paths in matrix.GROUPS.values() for path in paths)


@pytest.mark.parametrize('groups', [{}, {'empty': []}, {'../unsafe': ['test.py']},
                                    {'foreign': ['../../outside.py']}, {'bad': ['--help']}])
def test_empty_or_escaping_recipes_cannot_publish_success(tmp_path, groups):
    with pytest.raises(ValueError):
        shared.main(['--capture-current-source', '--output-directory', str(tmp_path / 'unused')], groups=groups)
    assert not (tmp_path / 'unused').exists()
