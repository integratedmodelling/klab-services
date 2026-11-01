import json
from pathlib import Path
import subprocess
import sys

import pytest

from worker import double, respond


def test_double():
    assert double([1, 2, 3, 0, -2, 0.25, None]) == [2, 4, 6, 0, -4, 0.5, None]
    assert double([]) == []


@pytest.mark.parametrize("values", [None, {}, "1", [True], [False], ["2"], [[]],
                                   [float("nan")], [float("inf")], [-float("inf")],
                                   [1e308], [10**400]])
def test_invalid_values(values):
    with pytest.raises(ValueError):
        double(values)


@pytest.mark.parametrize("payload", [None, [], {},
    {"protocol_version": True, "operation": "double", "values": []},
    {"protocol_version": 1.0, "operation": "double", "values": []},
    {"protocol_version": 2, "operation": "double", "values": []},
    {"protocol_version": 1, "operation": "other", "values": []},
    {"protocol_version": 1, "operation": "double", "values": [], "extra": 0}])
def test_invalid_request(payload):
    with pytest.raises(ValueError):
        respond(payload)


@pytest.mark.parametrize("payload", ['{', '{} {}',
    '{"protocol_version":1,"operation":"double","values":[NaN]}',
    '{"protocol_version":1,"operation":"double","values":[Infinity]}',
    '{"protocol_version":1,"operation":"double","values":[1e400]}',
    '{"protocol_version":1,"protocol_version":1,"operation":"double","values":[]}'])
def test_cli_rejects_invalid_json(payload):
    result = invoke(payload)
    assert result.returncode != 0
    assert result.stdout == ""
    assert "worker:" in result.stderr


def invoke(payload):
    return subprocess.run([sys.executable, str(Path(__file__).with_name("worker.py"))],
                          input=payload, capture_output=True, text=True, encoding="utf-8", timeout=10)


def test_cli_success():
    result = invoke('{"protocol_version":1,"operation":"double","values":[1,null,-0.5]}')
    assert result.returncode == 0
    assert result.stderr == ""
    assert json.loads(result.stdout) == {"protocol_version": 1, "values": [2, None, -1]}
