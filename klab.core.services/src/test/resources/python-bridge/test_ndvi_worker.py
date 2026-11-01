import json
from pathlib import Path
import subprocess
import sys

import pytest

from ndvi_worker import ndvi


def test_synthetic_reflectance_grid():
    values = ndvi([0.2, 0.3, 0.4, None, 0, 0.1], [0.8, 0.3, 0.1, 0.5, 0, 0.9])
    assert values[:3] == pytest.approx([0.6, 0, -0.6])
    assert values[3:5] == [None, None]
    assert values[5] == pytest.approx(0.8)


def test_boundaries_and_missing():
    assert ndvi([0, 1, None, 0.5], [1, 0, 0.5, None]) == [1, -1, None, None]
    assert ndvi([], []) == []


@pytest.mark.parametrize("value", [True, "0.2", -0.1, 1.1, float("nan"), float("inf"), {}])
def test_invalid_reflectance_even_when_other_band_is_missing(value):
    with pytest.raises(ValueError, match="reflectance"):
        ndvi([value], [None])
    with pytest.raises(ValueError, match="reflectance"):
        ndvi([None], [value])


@pytest.mark.parametrize("red,nir", [(None, []), ([], {}), ([0.2], [])])
def test_invalid_shapes(red, nir):
    with pytest.raises(ValueError, match="equal-length"):
        ndvi(red, nir)


@pytest.mark.parametrize("payload,success", [
    ('{"protocol_version":1,"operation":"ndvi","red":[0.2,null],"nir":[0.8,0.5]}', True),
    ('{"protocol_version":1,"operation":"ndvi","red":[NaN],"nir":[0.5]}', False),
    ('{"protocol_version":true,"operation":"ndvi","red":[],"nir":[]}', False),
    ('{"protocol_version":1,"operation":"double","red":[],"nir":[]}', False),
    ('{"protocol_version":1,"operation":"ndvi","red":[],"nir":[],"extra":0}', False),
    ('{"protocol_version":1,"operation":"ndvi","red":[],"red":[],"nir":[]}', False),
    ('{} {}', False),
])
def test_protocol(payload, success):
    result = subprocess.run([sys.executable, str(Path(__file__).with_name("ndvi_worker.py"))],
                            input=payload, text=True, encoding="utf-8", capture_output=True, timeout=10)
    if success:
        assert result.returncode == 0
        response = json.loads(result.stdout)
        assert response["protocol_version"] == 1
        assert response["values"][0] == pytest.approx(0.6)
        assert response["values"][1] is None
        assert result.stderr == ""
    else:
        assert result.returncode != 0
        assert result.stdout == ""
        assert "ndvi:" in result.stderr
