"""Offline lifecycle safety for the opt-in real-deployment tests."""
import importlib.util
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock

import pytest

from klab_client.errors import ConfigurationError


@pytest.fixture
def live_module():
    location = Path(__file__).parents[1] / "functional/test_connected_workflows.py"
    spec = importlib.util.spec_from_file_location("connected_workflow_safety_target", location)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_direct_work_requires_explicit_authorization_before_connecting(live_module, monkeypatch):
    monkeypatch.delenv("KLAB_CONNECTED_ALLOW_OWNED_WORK", raising=False)
    connector = Mock()
    monkeypatch.setattr(live_module, "connect", connector)
    fixture = live_module.owned_context.__wrapped__()
    with pytest.raises(ConfigurationError, match="ALLOW_OWNED_WORK"):
        next(fixture)
    connector.assert_not_called()


@pytest.mark.parametrize("context_created", [False, True])
def test_owned_cleanup_releases_session_even_if_context_fails(live_module, monkeypatch, context_created):
    monkeypatch.setenv("KLAB_CONNECTED_ALLOW_OWNED_WORK", "1")
    monkeypatch.setattr(live_module, "settings", lambda: ("profile", "credentials"))
    session = Mock()
    context = Mock()
    session.release.return_value = True
    if context_created:
        session.create_context.return_value = context
        context.release.side_effect = RuntimeError("private backend details must not be printed")
    else:
        session.create_context.side_effect = RuntimeError("context creation failed")
    connection = SimpleNamespace(client=SimpleNamespace(create_session=lambda **kwargs: session))
    manager = Mock()
    manager.__enter__ = Mock(return_value=connection)
    manager.__exit__ = Mock(return_value=False)
    monkeypatch.setattr(live_module, "connect", lambda *args, **kwargs: manager)
    fixture = live_module.owned_context.__wrapped__()
    if context_created:
        next(fixture)
        with pytest.raises(AssertionError, match="RuntimeError") as error:
            next(fixture)
        assert "private backend" not in str(error.value)
        context.release.assert_called_once()
    else:
        with pytest.raises(RuntimeError, match="context creation failed"):
            next(fixture)
    session.release.assert_called_once()
