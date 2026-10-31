"""Offline guards for the shipped live suite; never Java acceptance evidence."""
import importlib.util
from pathlib import Path

import pytest

from klab_client.errors import ConfigurationError


@pytest.fixture
def functional_module():
    path = Path(__file__).parents[1] / "functional/test_bounded_experiments.py"
    spec = importlib.util.spec_from_file_location("functional_bounded_experiments", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_live_runner_needs_explicit_work_permission(functional_module, monkeypatch):
    monkeypatch.delenv("KLAB_CONNECTED_ALLOW_OWNED_WORK", raising=False)
    with pytest.raises(ConfigurationError, match="authorization"):
        functional_module.configuration()


@pytest.mark.parametrize("missing", ["KLAB_EXPERIMENT_MODEL_URN", "KLAB_CONNECTED_PROFILE", "KLAB_CONNECTED_CERTIFICATE"])
def test_live_runner_needs_explicit_fixture_before_connecting(functional_module, monkeypatch, missing):
    monkeypatch.setenv("KLAB_CONNECTED_ALLOW_OWNED_WORK", "1")
    for key in ("KLAB_EXPERIMENT_MODEL_URN", "KLAB_CONNECTED_PROFILE", "KLAB_CONNECTED_CERTIFICATE"):
        monkeypatch.setenv(key, "not-used-offline")
    monkeypatch.delenv(missing)
    with pytest.raises(ConfigurationError, match="explicit model URN"):
        functional_module.configuration()
