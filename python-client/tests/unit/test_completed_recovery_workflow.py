"""Recovery harness controls only; these fakes are never scientific acceptance."""
import json
from types import SimpleNamespace

import pytest

from workflows import completed_recovery as workflow


def fixture(tmp_path, monkeypatch, *, owned=False, values=None, close_mutation=False):
    (tmp_path / "scientist.json").write_text("{}")
    events = []
    context = SimpleNamespace(owned=owned, get_context_id=lambda: "session.context.1")
    observation = SimpleNamespace(id=2, _context=context,
        fetch_data=lambda *args, **kwargs: SimpleNamespace(
            values=workflow.expected_values() if values is None else values))
    job = SimpleNamespace(id=3, result=lambda timeout: observation)
    class Client:
        transport = SimpleNamespace(_http=SimpleNamespace(event_hooks={"request": events}))
        def __enter__(self):
            return self
        def __exit__(self, *args):
            if close_mutation:
                for event in events:
                    event(SimpleNamespace(method="GET", url=SimpleNamespace(path="/runtime/releaseContext")))
        def initialize_user_scope(self, **kwargs):
            assert kwargs == {"local_federation": True}
        def restore_handle(self, path):
            for event in events:
                event(SimpleNamespace(method="GET", url=SimpleNamespace(path="/runtime/attachContext")))
            return {"context.json": context, "observation.json": observation, "job.json": job}[path.name]
    monkeypatch.setattr(workflow, "client", lambda config: Client())
    monkeypatch.setattr(workflow, "load_handle", lambda path: path)
    return tmp_path


def test_independent_nonuniform_oracle():
    values = workflow.expected_values()
    assert len(values) == 20 and sum(v is None for v in values) == 2
    assert values[0] == 0 and values[15] == 16777217.125
    assert min(v for v in values if v is not None) == -100


def test_recovery_audits_local_close_and_preserves_identity(tmp_path, monkeypatch):
    state = fixture(tmp_path, monkeypatch)
    report = workflow.recover(state, state)
    assert report["passed"] and report["submissions"] == 0
    assert report["job_id"] == 3 and report["observation_id"] == 2 and not report["owned"]
    assert json.loads((state / "recovered.json").read_text()) == report


@pytest.mark.parametrize("options,match", [
    ({"owned": True}, "disposal ownership"),
    ({"values": (0,) * 20}, "independent oracle"),
    ({"close_mutation": True}, "remote mutation")])
def test_recovery_failure_never_writes_success(tmp_path, monkeypatch, options, match):
    state = fixture(tmp_path, monkeypatch, **options)
    with pytest.raises(AssertionError, match=match):
        workflow.recover(state, state)
    assert not (state / "recovered.json").exists()
