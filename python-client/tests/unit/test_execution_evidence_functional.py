"""Offline checks of functional-test guardrails; not live permission evidence."""
from contextlib import nullcontext
import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace

import pytest

from klab_client.errors import AuthenticationError, AuthorizationError, ConfigurationError


@pytest.fixture
def functional_module():
    path = Path(__file__).parents[1] / "functional/test_execution_evidence.py"
    spec = importlib.util.spec_from_file_location("functional_execution_evidence", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_missing_retained_fixture_is_blocked_not_skipped(functional_module, tmp_path):
    with pytest.raises(ConfigurationError, match="Prepare execution-evidence.json"):
        functional_module.recorded_fixture.__wrapped__(tmp_path)


def test_same_credentials_cannot_count_as_nonowner_evidence(functional_module, tmp_path):
    (tmp_path / "execution-evidence.json").write_text(json.dumps({
        "units": "m", "model_urn": "fixture.model", "contextualizer": "fixture.call"}))
    (tmp_path / "scientist.json").write_text(json.dumps({"token": "offline-canary", "other_token": "offline-canary"}))
    with pytest.raises(ConfigurationError, match="distinct valid fixture credentials"):
        functional_module.recorded_fixture.__wrapped__(tmp_path)


def prepare_clients(module, monkeypatch, *, private_allowed=False, invalid_nonowner=False):
    calls = []
    private, shared = object(), object()
    owner = SimpleNamespace()
    def restore(handle):
        calls.append(("private_attempt", handle))
        if private_allowed:
            return object()
        raise AuthorizationError("offline denied")
    nonowner_client = SimpleNamespace(restore_handle=restore)
    monkeypatch.setattr(module, "fixture_client", lambda config, nonowner=False:
                        nullcontext(nonowner_client if nonowner else owner))
    def verify(client, handle, fixture):
        calls.append(("positive", client, handle))
        if client is nonowner_client and invalid_nonowner:
            raise AuthenticationError("offline invalid credential")
    monkeypatch.setattr(module, "verify_recorded_result", verify)
    monkeypatch.setattr(module, "fetch_execution_evidence", lambda observation: object())
    prepared = ({}, {}, {"private_observation": private, "shared_observation": shared})
    return prepared, calls, owner, nonowner_client


def test_nonowner_denial_needs_shared_positive_and_owner_recheck(functional_module, monkeypatch):
    prepared, calls, owner, nonowner = prepare_clients(functional_module, monkeypatch)
    functional_module.test_valid_nonowner_reads_shared_work_but_cannot_read_private_evidence(prepared)
    private, shared = prepared[2]["private_observation"], prepared[2]["shared_observation"]
    assert calls == [("positive", owner, private), ("positive", nonowner, shared),
                     ("private_attempt", private), ("positive", owner, private)]


def test_invalid_credential_is_not_a_successful_isolation_check(functional_module, monkeypatch):
    prepared, calls, _, _ = prepare_clients(functional_module, monkeypatch, invalid_nonowner=True)
    with pytest.raises(AuthenticationError):
        functional_module.test_valid_nonowner_reads_shared_work_but_cannot_read_private_evidence(prepared)
    assert all(call[0] != "private_attempt" for call in calls)


def test_unexpected_private_access_fails_the_live_test(functional_module, monkeypatch):
    prepared, _, _, _ = prepare_clients(functional_module, monkeypatch, private_allowed=True)
    with pytest.raises(pytest.fail.Exception, match="DID NOT RAISE"):
        functional_module.test_valid_nonowner_reads_shared_work_but_cannot_read_private_evidence(prepared)
