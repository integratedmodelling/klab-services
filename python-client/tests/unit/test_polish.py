"""Branch-wide polish regressions: bounded work and original failure preservation."""
from itertools import count
import json
from pathlib import Path

import httpx
import pytest

from klab_client import Client, Context, DeploymentProfile, Handle
from klab_client.dto import observation_from_wire
from klab_client.errors import InvalidRequestError, ProtocolError, SubmissionOutcomeUnknown
from klab_client.handles import HandleIOError


def observation(client):
    wire = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    wire["urn"] = "s.c.42"
    return observation_from_wire(wire, Context(client, {"id": "s.c", "serviceId": "home"}))


def test_sparse_read_stops_after_257_offsets_instead_of_consuming_the_iterable():
    consumed = []
    def offsets():
        for n in count():
            consumed.append(n)
            if n == 257:
                pytest.fail("Read exhausted an unbounded input before validating its 256-cell budget")
            yield n
    with Client("https://runtime.invalid", "secret", runtime_service_id="home",
                http_transport=httpx.MockTransport(lambda r: pytest.fail("Invalid offsets reached HTTP"))) as client:
        with pytest.raises(InvalidRequestError, match="256"):
            observation(client).fetch_data(offsets())
    assert len(consumed) == 257


def test_sparse_read_requires_an_iterable_before_http():
    with Client("https://runtime.invalid", "secret", runtime_service_id="home",
                http_transport=httpx.MockTransport(lambda r: pytest.fail("Invalid offsets reached HTTP"))) as client:
        with pytest.raises(InvalidRequestError):
            observation(client).fetch_data(None)


@pytest.mark.parametrize("primary", [SubmissionOutcomeUnknown("submission uncertain"), ProtocolError("bad result"), KeyboardInterrupt()])
def test_client_exit_preserves_primary_operation_failure(primary, monkeypatch):
    client = Client("https://runtime.invalid", "secret")
    original_close = client.transport.close
    def close():
        original_close()
        raise OSError("private cleanup diagnostics")
    monkeypatch.setattr(client.transport, "close", close)
    with pytest.raises(type(primary)) as caught:
        with client:
            raise primary
    assert caught.value is primary
    assert any("Client cleanup failed: OSError" in note for note in primary.__notes__)
    assert "private" not in str(primary.__notes__)


def test_successful_client_context_does_not_hide_close_failure(monkeypatch):
    client = Client("https://runtime.invalid", "secret")
    original = client.transport.close
    failure = OSError("local close failed")
    def close():
        original()
        raise failure
    monkeypatch.setattr(client.transport, "close", close)
    with pytest.raises(OSError) as caught:
        with client:
            pass
    assert caught.value is failure


@pytest.mark.parametrize("primary", [OSError("private write failure"), KeyboardInterrupt()])
def test_handle_cleanup_never_masks_a_failed_or_interrupted_write(tmp_path, monkeypatch, primary):
    import klab_client.handles as module
    handle = Handle("observation", "home", "https://runtime.invalid", "s.c", "s.c", observation_id=42)
    def fail_write(*args):
        raise primary
    def fail_cleanup(*args, **kwargs):
        raise OSError("private unlink failure")
    with monkeypatch.context() as patch:
        patch.setattr(module.os, "write", fail_write)
        patch.setattr(module.Path, "unlink", fail_cleanup)
        expected = HandleIOError if isinstance(primary, OSError) else KeyboardInterrupt
        with pytest.raises(expected) as caught:
            handle.save(tmp_path / "handle.json")
        if isinstance(primary, OSError):
            assert "write/publication" in str(caught.value)
        else:
            assert caught.value is primary
        assert any("temporary" in note for note in caught.value.__notes__)
        assert "private" not in str(caught.value.__notes__)
    assert not (tmp_path / "handle.json").exists()


def test_profile_cleanup_preserves_keyboard_interrupt(tmp_path, monkeypatch):
    import klab_client.connection_models as module
    primary = KeyboardInterrupt()
    def interrupt(*args, **kwargs):
        raise primary
    def cleanup(*args, **kwargs):
        raise OSError("private unlink diagnostics")
    profile = DeploymentProfile.from_urls("demo", {"runtime": "https://runtime.invalid"})
    with monkeypatch.context() as patch:
        patch.setattr(module.json, "dump", interrupt)
        patch.setattr(module.Path, "unlink", cleanup)
        with pytest.raises(KeyboardInterrupt) as caught:
            profile.save(tmp_path / "profile.json")
        assert caught.value is primary and any("cleanup" in note for note in primary.__notes__)
        assert "private" not in str(primary.__notes__)
