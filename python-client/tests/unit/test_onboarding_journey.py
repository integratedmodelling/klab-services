from contextlib import contextmanager
import json
import os
from types import SimpleNamespace

import pytest

from workflows import onboarding_journey as workflow


@pytest.mark.parametrize("call,allowed", [
    ({"method": "GET", "path": "/runtime/export/OBSERVATION/original"}, True),
    ({"method": "GET", "path": "/runtime/public/capabilities"}, True),
    ({"method": "POST", "path": "/runtime/api/v1/connect", "context_id": "root"}, True),
    ({"method": "POST", "path": "/runtime/api/v1/query"}, True),
    ({"method": "POST", "path": "/runtime/api/v1/connect", "context_id": "foreign"}, False),
    ({"method": "POST", "path": "/runtime/api/v1/connect"}, False),
    ({"method": "POST", "path": "/runtime/export/OBSERVATION/original"}, False),
    ({"method": "POST", "path": "/runtime/api/v1/submit"}, False),
    ({"method": "DELETE", "path": "/runtime/api/v1/release"}, False),
    ({"method": "GET", "path": "/runtime/unexpected"}, False),
])
def test_fresh_connection_request_audit(call, allowed):
    assert workflow.allowed_recovery(call, "root") is allowed


def setup_recovery(monkeypatch, *, owned=False, close_release=False):
    hooks = []
    def request(method, path, body=None):
        wire = SimpleNamespace(method=method, url=SimpleNamespace(path=path), content=json.dumps(body).encode())
        for hook in hooks:
            hook(wire)
    def restore(reference):
        request("POST", "/runtime/api/v1/connect", {"configuration": {"id": reference.context_id}})
        def export(destination, **kwargs):
            request("GET", "/runtime/export/OBSERVATION/original")
            return SimpleNamespace(bytes_written=123)
        return SimpleNamespace(id=11, _context=SimpleNamespace(owned=owned), export_to_file=export)
    client = SimpleNamespace(initialize_user_scope=lambda **kwargs: None,
        transport=SimpleNamespace(_http=SimpleNamespace(event_hooks={"request": hooks})), restore_handle=restore)
    @contextmanager
    def connected(*args):
        try:
            yield client
        finally:
            if close_release:
                request("DELETE", "/runtime/api/v1/release")
    monkeypatch.setattr(workflow, "connected_client", connected)
    monkeypatch.setattr(workflow, "load_handle", lambda path: SimpleNamespace(kind="observation", context_id="root"))


def test_recovery_audits_through_local_close_and_preserves_metadata(tmp_path, monkeypatch):
    setup_recovery(monkeypatch)
    path = tmp_path / "report.json"
    result = workflow.recover(tmp_path, "certificate", "handle", "destination", path)
    assert result["pid"] == os.getpid() and result["observation_id"] == 11
    assert result["scientific_submissions"] == 0 and result["local_close_only"] and not result["owned"]
    assert json.loads(path.read_text())["calls"][0]["context_id"] == "root"


@pytest.mark.parametrize("owned,close_release", [(True, False), (False, True)])
def test_recovery_refuses_ownership_or_remote_close(tmp_path, monkeypatch, owned, close_release):
    setup_recovery(monkeypatch, owned=owned, close_release=close_release)
    path = tmp_path / "report.json"
    with pytest.raises(AssertionError):
        workflow.recover(tmp_path, "token", "handle", "destination", path)
    assert not path.exists()


def test_recovery_refuses_non_observation(tmp_path, monkeypatch):
    monkeypatch.setattr(workflow, "load_handle", lambda path: SimpleNamespace(kind="job"))
    with pytest.raises(AssertionError, match="original observation"):
        workflow.recover(tmp_path, "token", "handle", "destination", tmp_path / "report.json")


def test_journey_requires_fresh_worker_receipt(tmp_path, monkeypatch):
    monkeypatch.setattr(workflow, "native_export", lambda *args, **kwargs: {"observation_id": 11})
    with pytest.raises(AssertionError, match="two fresh-process"):
        workflow.journey(tmp_path)


@pytest.mark.parametrize("leak", [None, "profile", "handle", "report"])
def test_final_report_and_saved_artifacts_are_secret_free(tmp_path, monkeypatch, leak):
    config = {"token": "PRIVATE_TEST_OWNER", "other_token": "PRIVATE_TEST_OTHER"}
    (tmp_path / "scientist.json").write_text(json.dumps(config))
    (tmp_path / "connection-profile.json").write_text(config["token"] if leak == "profile" else "{}")
    (tmp_path / "onboarding-scalar.json").write_text("{}")
    directory = tmp_path / "geojson-export-test"
    directory.mkdir()
    (directory / "native-observation.json").write_text(config["other_token"] if leak == "handle" else "{}")
    calls = []
    def journey(state):
        calls.append(state)
        return [{"provider": provider, "public": config["token"] if leak == "report" else "metadata"}
                for provider in ("certificate", "token")]
    monkeypatch.setattr(workflow, "journey", journey)
    monkeypatch.setattr(workflow, "access", lambda state: {"owner_positive": True})
    if leak:
        with pytest.raises(AssertionError, match="credential material"):
            workflow.run(tmp_path)
        assert not (tmp_path / "onboarding-journey.json").exists()
    else:
        report = workflow.run(tmp_path)
        assert report["production_hub"] == "NOT_RUN" and report["secret_free_profiles_handles"]
        assert len(report["journeys"]) == 2
        assert calls == [tmp_path]
