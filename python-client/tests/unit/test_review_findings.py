"""Reproductions from the read-only branch review; no real services are used."""
from pathlib import Path

import httpx
import pytest

from klab_client import (Client, ConnectionIdentity, Context, CredentialGrant, DeploymentProfile,
                         Handle, IssuedTokenProvider, ServiceReference, Session, connect, save_handle)
from klab_client.connection_models import ProfileIOError
from klab_client.errors import InvalidRequestError, ProtocolError
from klab_client.exports import ExportIOError
from klab_client.handles import HandleFormatError, HandleIOError
from test_exports import MIME, Stream, asset, client as export_client


def set_identity(client, identity, kind):
    if kind == "configured":
        client.runtime_service_id = identity
    else:
        client._service_id_cache["runtime"] = identity


@pytest.mark.parametrize("kind", ["context", "session"])
@pytest.mark.parametrize("source_kind", ["configured", "cached"])
@pytest.mark.parametrize("destination_kind", ["configured", "cached"])
def test_release_refuses_known_foreign_identity_at_same_url(kind, source_kind, destination_kind):
    with Client("https://same.invalid/runtime", http_transport=httpx.MockTransport(lambda r: pytest.fail("Source HTTP"))) as source:
        with Client("https://same.invalid/runtime", http_transport=httpx.MockTransport(lambda r: pytest.fail("Foreign release reached HTTP"))) as destination:
            set_identity(source, "old-runtime", source_kind)
            set_identity(destination, "new-runtime", destination_kind)
            scope = Context(source, {"id": "s.c"}) if kind == "context" else Session(source, "s")
            operation = destination.runtime.release_context if kind == "context" else destination.runtime.release_session
            with pytest.raises(InvalidRequestError, match="Runtime"):
                operation(scope)


def test_release_checks_context_reported_identity_before_http():
    with Client("https://same.invalid/runtime", http_transport=httpx.MockTransport(lambda r: pytest.fail("Source HTTP"))) as source:
        with Client("https://same.invalid/runtime", runtime_service_id="new-runtime",
                    http_transport=httpx.MockTransport(lambda r: pytest.fail("Foreign release reached HTTP"))) as destination:
            with pytest.raises(InvalidRequestError, match="Runtime"):
                destination.runtime.release_context(Context(source, {"id": "s.c", "serviceId": "old-runtime"}))


@pytest.mark.parametrize("kind", ["context", "session"])
@pytest.mark.parametrize("source_id,destination_id", [("home", "home"), (None, "home"), ("home", None), (None, None)])
def test_release_preserves_same_identity_canonical_urls_and_legacy_unknowns(kind, source_id, destination_id):
    calls = []
    with Client("HTTPS://SAME.invalid:443/runtime/", runtime_service_id=source_id) as source:
        with Client("https://same.invalid/runtime", runtime_service_id=destination_id,
                    http_transport=httpx.MockTransport(lambda r: calls.append(r) or httpx.Response(200, json=True))) as destination:
            scope = Context(source, {"id": "s.c"}) if kind == "context" else Session(source, "s")
            operation = destination.runtime.release_context if kind == "context" else destination.runtime.release_session
            assert operation(scope)
    assert len(calls) == 1


@pytest.mark.parametrize("kind", ["context", "session"])
def test_release_refuses_contradictory_local_identity_before_http(kind):
    with Client("https://same.invalid/runtime", runtime_service_id="home",
                http_transport=httpx.MockTransport(lambda r: pytest.fail("Contradictory release reached HTTP"))) as current:
        current._service_id_cache["runtime"] = "other"
        scope = Context(current, {"id": "s.c"}) if kind == "context" else Session(current, "s")
        operation = current.runtime.release_context if kind == "context" else current.runtime.release_session
        with pytest.raises(InvalidRequestError, match="Runtime"):
            operation(scope)


@pytest.mark.parametrize("material", ["renewed-token", "certificate-secret"])
@pytest.mark.parametrize("encoding_depth", [0, 1, 2])
def test_handles_reject_all_known_provider_secrets_after_renewal(tmp_path, material, encoding_depth):
    url = "https://review.invalid/runtime"
    deployment = DeploymentProfile("review", (ServiceReference("runtime", url),), required_services=("runtime",))
    state = {"token": "original-issued-credential"}
    secret = "renewed-issued-credential" if material == "renewed-token" else "private-certificate-material"
    identity = secret
    for _ in range(encoding_depth):
        identity = "".join(f"%{byte:02X}" for byte in identity.encode())
    def handler(request):
        if request.url.path.endswith("/public/capabilities"):
            return httpx.Response(200, json={"serviceId": "home", "type": "RUNTIME"})
        if request.url.path.endswith("/public/status"):
            return httpx.Response(200, json={"available": True, "operational": True})
        if request.url.path.endswith("/createSession"):
            return httpx.Response(200, text="s")
        assert request.url.path.endswith("/createContext")
        assert request.headers["Authorization"] == "renewed-issued-credential"
        return httpx.Response(200, json={"id": "s." + identity, "serviceId": "home"})
    provider = IssuedTokenProvider(ConnectionIdentity("reviewer"), lambda: CredentialGrant(
        ConnectionIdentity("reviewer"), deployment.services, {url: state["token"]},
        sensitive_values=("private-certificate-material",)))
    with connect(deployment, credentials=provider, http_transport=httpx.MockTransport(handler), initialize=False) as connection:
        state["token"] = "renewed-issued-credential"
        connection.renew()
        context = connection.client.create_session().create_context()
        with pytest.raises(HandleFormatError, match="credential"):
            save_handle(context, tmp_path / "reference.json")
    assert not (tmp_path / "reference.json").exists()


def test_empty_optional_sensitive_value_does_not_reject_a_safe_handle():
    with Client("https://runtime.invalid", runtime_service_id="home") as current:
        current.transport.sensitive_values = ("",)
        assert Context(current, {"id": "s.c"}).to_handle().context_id == "s.c"


def save(kind, destination):
    if kind == "handle":
        return Handle("context", "home", "https://runtime.invalid", "s.c", "s.c").save(destination)
    if kind == "profile":
        return DeploymentProfile.from_urls("review", {"runtime": "https://runtime.invalid"}).save(destination)
    with export_client(Stream([b"complete"])) as current:
        return asset(current).export_to_file(destination, media_type=MIME)


ERRORS = {"handle": HandleIOError, "profile": ProfileIOError, "export": ExportIOError}


@pytest.mark.parametrize("kind", ERRORS)
def test_successful_save_inside_caller_except_still_reports_its_own_unlink_failure(kind, tmp_path, monkeypatch):
    destination = tmp_path / "result"
    with monkeypatch.context() as patch:
        patch.setattr(Path, "unlink", lambda *a, **kw: (_ for _ in ()).throw(OSError("private cleanup text")))
        try:
            raise RuntimeError("caller already handling this")
        except RuntimeError as caller:
            with pytest.raises(ERRORS[kind], match="cleanup") as caught:
                save(kind, destination)
            assert not getattr(caller, "__notes__", [])
            assert "private" not in str(caught.value)
    assert destination.is_file()  # Publication completed; only local cleanup failed.


def fail_closing(kind, patch):
    """Actually close the test-owned descriptor, then simulate a close failure."""
    import os
    if kind == "profile":
        original = os.fdopen
        class ClosingStream:
            def __init__(self, stream):
                self.stream = stream

            def __enter__(self):
                return self.stream

            def __exit__(self, *args):
                self.stream.close()
                raise OSError("private close diagnostics")
        patch.setattr(os, "fdopen", lambda *a, **kw: ClosingStream(original(*a, **kw)))
    else:
        original = os.close
        def close(fd):
            original(fd)
            raise OSError("private close diagnostics")
        patch.setattr(os, "close", close)


@pytest.mark.parametrize("kind", ERRORS)
@pytest.mark.parametrize("failure_type", [KeyboardInterrupt, ProtocolError, OSError])
def test_descriptor_close_preserves_the_operation_failure(kind, failure_type, tmp_path, monkeypatch):
    import json
    import os
    primary = failure_type("primary operation failed")
    def fail(*args, **kwargs):
        raise primary
    with monkeypatch.context() as patch:
        fail_closing(kind, patch)
        patch.setattr(json if kind == "profile" else os, "dump" if kind == "profile" else "write", fail)
        expected = ERRORS[kind] if failure_type is OSError else failure_type
        with pytest.raises(expected) as caught:
            save(kind, tmp_path / "result")
        if failure_type is not OSError:
            assert caught.value is primary
        assert any("cleanup failed" in note for note in caught.value.__notes__)
        assert "private" not in str(caught.value.__notes__)
    assert not (tmp_path / "result").exists()


@pytest.mark.parametrize("kind", ERRORS)
def test_descriptor_close_failure_without_operation_error_is_reported(kind, tmp_path, monkeypatch):
    with monkeypatch.context() as patch:
        fail_closing(kind, patch)
        with pytest.raises(ERRORS[kind]) as caught:
            save(kind, tmp_path / "result")
        assert "private" not in str(caught.value)
    assert not (tmp_path / "result").exists()
