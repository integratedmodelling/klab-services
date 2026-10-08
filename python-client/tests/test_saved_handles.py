"""Strict local references and explicit authenticated restoration through real codecs."""
from copy import deepcopy
from dataclasses import FrozenInstanceError
import json
import os
from pathlib import Path
import subprocess
import sys

import httpx
import pytest

from klab_client import (Client, Context, Endpoint, Handle, Job, HandleFormatError,
    HandleIdentityError, HandleIOError, load_handle, restore_handle, save_handle, to_handle)
from klab_client.dto import observation_from_wire
from klab_client.errors import (InvalidRequestError, ConfigurationError, UnsupportedOperationError,
    AuthenticationError, AuthorizationError, MissingAssetError, ProtocolError, ServerError,
    TransportError, SubmissionOutcomeUnknown, JobUnavailableError, JobFailedError, JobCancelledError)


@pytest.fixture
def wire():
    payload = json.loads((Path(__file__).parent / "fixtures/elevation.json").read_text())
    payload["urn"] = "s.c.42"
    return payload


def client(handler, **kwargs):
    return Client("https://runtime.invalid/runtime", "issued-secret", agent_name="scientist",
        runtime_service_id="runtime-id", resolver=Endpoint("https://resolver.invalid/resolver", "peer-secret"),
        http_transport=httpx.MockTransport(handler), **kwargs)


def test_local_context_handle_preserves_selection_and_contains_no_authority(tmp_path):
    with client(lambda r: pytest.fail("Saving/loading must remain offline")) as current:
        context = Context(current, {"id": "s.c", "owner": "scientist", "privateToken": "issued-secret"},
            owned=True, token="s.c.8.9#3")
        handle = context.to_handle()
        destination = tmp_path / "context.json"
        save_handle(context, destination)
        again = load_handle(destination)
        assert again == handle
        assert again.scope == "s.c.8.9#3" and again.context_id == "s.c"
        assert not again.inspect()["remote_verified"]
        assert "issued-secret" not in destination.read_text() and "owner" not in destination.read_text()
        assert "privateToken" not in repr(handle)


def test_restore_observation_queries_current_authorized_graph_and_not_cached_dto(wire):
    calls = []
    def handler(request):
        calls.append(request)
        if request.url.path.endswith("/capabilities"):
            return httpx.Response(200, json={"serviceId": "runtime-id"})
        if request.url.path.endswith("/connect"):
            return httpx.Response(200, json={"id": "s.c", "serviceId": "runtime-id"})
        if request.url.path.endswith("/query"):
            query = json.loads(request.content)
            assert query["resultType"] == "OBSERVATION" and query["id"] == 42
            assert request.headers["klab-scope"] == "s.c"
            return httpx.Response(200, json=[{**wire, "name": "fresh from server"}])
        pytest.fail(str(request.url))
    with client(handler) as current:
        observation = observation_from_wire(wire, Context(current, {"id": "s.c"}))
        handle = observation.to_handle()
        restored = handle.restore(current)
        assert restored.name == "fresh from server"
        assert restored._context.owned is False
    assert [r.url.path for r in calls] == ["/runtime/public/capabilities", "/runtime/api/v1/connect", "/runtime/api/v1/query"]


def test_runtime_and_resolver_job_references_do_not_cross_route(wire):
    calls = []
    def handler(request):
        calls.append(request)
        if request.url.path.endswith("/capabilities"):
            return httpx.Response(200, json={"serviceId": "peer-id" if request.url.host == "resolver.invalid" else "runtime-id"})
        if request.url.path.endswith("/connect"):
            return httpx.Response(200, json={"id": "s.c"})
        if "/status/" in request.url.path:
            return httpx.Response(200, json={"status": "FINISHED"})
        if "/retrieve/" in request.url.path:
            return httpx.Response(200, json={"resolutionOutcome": "RESOLVED"})
        if request.url.path.endswith("/resolve"):
            return httpx.Response(200, json=8)
        pytest.fail(str(request.url))
    with client(handler) as current:
        current.resolver.capabilities()
        from klab_client import ObservationImpl
        context = Context(current, {"id": "s.c"})
        job = current.resolver.resolve(ObservationImpl(urn="", observable=observation_from_wire(wire).observable), context)
        reference = to_handle(job)
        assert reference.service == "resolver" and reference.result_kind == "dataflow"
        restored = current.restore_handle(Handle.loads(reference.dumps()))
        assert restored.service == "resolver" and restored.result(1)["resolutionOutcome"] == "RESOLVED"
        assert all(r.headers["klab-service"] == "runtime-id" for r in calls if "/jobs/" in r.url.path)


def test_mismatch_never_contacts_a_file_provided_origin():
    reference = Handle(kind="context", runtime_id="home", runtime_url="https://evil.invalid/runtime",
        context_id="s.c", scope="s.c")
    with client(lambda r: pytest.fail("Mismatch must fail before HTTP")) as current:
        with pytest.raises(InvalidRequestError):
            current.restore_handle(reference)


def test_atomic_save_refuses_overwrite_and_preserves_existing_file(tmp_path):
    destination = tmp_path / "reference.json"
    first = Handle(kind="context", runtime_id="home", runtime_url="https://host.invalid/runtime", context_id="s.c", scope="s.c")
    second = Handle(kind="context", runtime_id="home", runtime_url="https://host.invalid/runtime", context_id="s.d", scope="s.d")
    first.save(destination)
    before = destination.read_bytes()
    with pytest.raises(HandleIOError):
        second.save(destination)
    assert destination.read_bytes() == before
    second.save(destination, overwrite=True)
    assert Handle.load(destination) == second
    assert sorted(p.name for p in tmp_path.iterdir()) == ["reference.json"]


def reference(**changes):
    values = dict(kind="context", runtime_id="runtime-id", runtime_url="https://runtime.invalid/runtime", context_id="s.c", scope="s.c")
    values.update(changes)
    return Handle(**values)


def job_reference(service="runtime", result_kind=None, **changes):
    values = dict(kind="job", job_id=5, service=service,
        service_id="runtime-id" if service == "runtime" else "peer-id",
        service_url="https://runtime.invalid/runtime" if service == "runtime" else "https://resolver.invalid/resolver",
        result_kind=("observation" if service == "runtime" else "dataflow") if result_kind is None else result_kind)
    values.update(changes)
    return reference(**values)


def transport(wire, calls, *, status="FINISHED", query_mutate=None, attach=None, runtime_id="runtime-id", peer_id="peer-id"):
    def handler(request):
        calls.append(request)
        if request.url.path.endswith("/capabilities"):
            return httpx.Response(200, json={"serviceId": peer_id if request.url.host == "resolver.invalid" else runtime_id})
        if request.url.path.endswith("/connect"):
            return httpx.Response(200, json=attach or {"id": "s.c", "serviceId": "runtime-id", "owner": "original-owner"})
        if request.url.path.endswith("/query"):
            point = json.loads(request.content)["id"]
            row = deepcopy(wire)
            row.update(id=point, urn=f"s.c.{point}")
            if point == 3:
                row["observable"]["semantics"]["type"] = ["AGENT"]
            return httpx.Response(200, json=query_mutate(row) if query_mutate else [row])
        if "/status/" in request.url.path:
            return httpx.Response(200, json={"status": status})
        if "/retrieve/" in request.url.path:
            return httpx.Response(200, json={"resolutionOutcome": "RESOLVED"} if request.url.host == "resolver.invalid" else wire)
        pytest.fail(f"Undocumented restoration side effect: {request.url.path}")
    return handler


@pytest.mark.parametrize("handle", [reference(), reference(kind="observation", observation_id=42),
    job_reference(), job_reference("runtime", "mapping"), job_reference("resolver"), job_reference("resolver", "mapping")])
def test_all_reference_kinds_and_decoder_variants_round_trip(handle):
    assert Handle.loads(handle.dumps().encode()) == handle
    assert to_handle(handle) is handle
    assert json.dumps(handle.to_dict(), sort_keys=True) == json.dumps(Handle.loads(handle.dumps()).to_dict(), sort_keys=True)
    with pytest.raises(FrozenInstanceError):
        handle.scope = "other.context"


@pytest.mark.parametrize("changes", [
    {"version": True}, {"version": "1"}, {"kind": "unknown"}, {"kind": []},
    {"runtime_id": None}, {"runtime_id": " "}, {"runtime_id": "line\nsecret"},
    {"runtime_id": "Bearer credential"}, {"runtime_id": "eyJheader.credential.signature"},
    {"runtime_id": "\ud800"}, {"runtime_id": "x" * 4097},
    {"scope": "s.other"}, {"scope": "s"}, {"context_id": "s.c.extra"},
    {"scope": "s.c.bad"}, {"scope": "s.c.0"}, {"scope": "s.c#0"},
    {"scope": "s.c." + str(2**63)}, {"scope": "s.c#" + str(2**63)},
    {"observation_id": 42}, {"job_id": 5}, {"service": "runtime"},
    {"runtime_url": "https://user:secret@runtime.invalid/runtime"},
    {"runtime_url": "https://@runtime.invalid/runtime"}, {"runtime_url": "https://runtime.invalid/runtime?secret=1"},
    {"runtime_url": "https://runtime.invalid/runtime#secret"}, {"runtime_url": "https://runtime.invalid/runtime?"},
    {"runtime_url": "file:///secret"}, {"runtime_url": "http://"}, {"runtime_url": "https://[invalid/runtime"},
    {"runtime_url": "https://runtime.invalid:invalid/runtime"}, {"runtime_url": "https://runtime.invalid/has space"},
    {"runtime_url": "https://runtime.invalid/" + "é"*1000}])
def test_invalid_schema_is_local_and_diagnostics_do_not_echo_input(changes):
    with pytest.raises(HandleFormatError) as error:
        reference(**changes)
    assert "secret" not in str(error.value)


@pytest.mark.parametrize("version", [0, 2, -1])
def test_unknown_version_is_explicit(version):
    with pytest.raises(UnsupportedOperationError):
        reference(version=version)


@pytest.mark.parametrize("identity", [True, 0, -1, 2**63, 1.0, "42", None])
def test_observation_and_job_ids_are_exact_integers(identity):
    with pytest.raises(HandleFormatError):
        reference(kind="observation", observation_id=identity)
    with pytest.raises(HandleFormatError):
        job_reference(job_id=identity)


@pytest.mark.parametrize("changes", [{"service": "other"}, {"observation_id": 42}, {"result_kind": "os.system"},
    {"result_kind": {}}, {"service_id": "other"}, {"service_url": "https://runtime.invalid/other"},
    {"service_id": None}, {"service_url": "https://user:secret@peer.invalid"}])
def test_job_cross_field_invariants(changes):
    with pytest.raises(HandleFormatError):
        job_reference(**changes)


@pytest.mark.parametrize("contents", [b"\xff", "{", "[]", "null", 42,
    "\ud800", " " * 65537, b" " * 65537, '{"version":1,"version":1}',
    '{"version":NaN}', "[" * 2000 + "]" * 2000],
    ids=["utf8", "truncated", "array", "null", "type", "surrogate", "large-text", "large-bytes", "duplicate", "nonfinite", "deep"])
def test_malformed_duplicate_oversized_or_nonfinite_json_is_rejected(contents):
    with pytest.raises(HandleFormatError):
        Handle.loads(contents)


@pytest.mark.parametrize("field,value", [("token", "hidden-credential-value"), ("owner", True), ("cached_observation", {}),
    ("Authorization", "hidden-credential-value"), ("profile", {"credential": "hidden-credential-value"})])
def test_unknown_or_secret_fields_are_rejected(field, value):
    values = reference().to_dict()
    values[field] = value
    with pytest.raises(HandleFormatError) as error:
        Handle.loads(json.dumps(values))
    assert "hidden-credential-value" not in str(error.value)


def test_nested_duplicate_keys_and_missing_fields():
    with pytest.raises(HandleFormatError, match="Duplicate"):
        Handle.loads('{"extra":{"token":"one","token":"two"}}')
    values = reference().to_dict()
    del values["runtime_id"]
    with pytest.raises(HandleFormatError, match="missing"):
        Handle.loads(json.dumps(values))


def test_offline_capture_identity_requirements_and_custom_jobs(wire):
    with client(lambda r: pytest.fail("Capture is offline")) as current:
        context = Context(current, {"id": "s.c"})
        assert context.job(5).to_handle().result_kind == "observation"
        assert to_handle(Job(current, 5, "s.c")).result_kind == "mapping"
        with pytest.raises(UnsupportedOperationError, match="Custom"):
            to_handle(Job(current, 5, context, decoder=lambda value: value))
        with pytest.raises(UnsupportedOperationError):
            to_handle(Job(current, 5, context, service="other"))
        with pytest.raises(HandleFormatError):
            to_handle(Job(current, 5, "session"))
        with pytest.raises(HandleFormatError):
            to_handle(Job(current, 5, None))
        with pytest.raises(ConfigurationError, match="peer"):
            to_handle(Job(current, 5, context, service="resolver"))
        with pytest.raises(HandleFormatError):
            to_handle(observation_from_wire(wire))
        with pytest.raises(HandleFormatError):
            to_handle(object())
        with pytest.raises(HandleIdentityError):
            to_handle(Context(current, {"id": "s.c", "serviceId": "different"}))
        with Client("https://different.invalid") as foreign:
            with pytest.raises(HandleIdentityError):
                to_handle(Job(current, 5, Context(foreign, {"id": "s.c"})))
        current.runtime_service_id = None
        with pytest.raises(ConfigurationError, match="home"):
            context.to_handle()
        current._service_id_cache["runtime"] = "runtime-id"
        assert context.to_handle().runtime_id == "runtime-id"
        current.runtime_service_id = "different"
        with pytest.raises(HandleIdentityError, match="Cached"):
            context.to_handle()


def test_configured_credential_cannot_be_smuggled_as_reference_metadata():
    with client(lambda r: pytest.fail("No HTTP")) as current:
        current.runtime_service_id = "issued-secret"
        with pytest.raises(HandleFormatError, match="credential"):
            Context(current, {"id": "s.c"}).to_handle()
        unsafe = reference(runtime_id="issued-secret")
        with pytest.raises(HandleFormatError, match="credential"):
            unsafe.restore(current)
    with Client("https://runtime.invalid/runtime", runtime_service_id="runtime-id") as uncredentialed:
        assert Context(uncredentialed, {"id": "s.c"}).to_handle().runtime_id == "runtime-id"


@pytest.mark.parametrize("scope", ["s.c", "s.c.8", "s.c.8.9", "s.c#3", "s.c.8.9#3"])
def test_restored_selection_revalidates_assets_and_never_takes_ownership(wire, scope):
    calls = []
    with client(transport(wire, calls)) as current:
        restored = reference(scope=scope).restore(current)
        assert restored.get_context_id() == scope and restored.owned is False
    assert not any(r.url.path.endswith(("submit", "createContext", "createSession", "releaseContext", "cancel")) for r in calls)


@pytest.mark.parametrize("service,result_kind", [("runtime", "observation"), ("runtime", "mapping"),
    ("resolver", "dataflow"), ("resolver", "mapping")])
def test_restored_job_decoder_allowlist_and_actual_result_routes(wire, service, result_kind):
    calls = []
    with client(transport(wire, calls)) as current:
        restored = job_reference(service, result_kind).restore(current)
        result = restored.result(1)
        if result_kind == "observation":
            assert result.id == 42 and result._context.client is current
        else:
            assert isinstance(result, dict)
    job_calls = [r for r in calls if "/jobs/" in r.url.path]
    assert all(r.url.host == ("runtime.invalid" if service == "runtime" else "resolver.invalid") for r in job_calls)


@pytest.mark.parametrize("state", ["WAITING", "STARTED", "CHANGED", "FINISHED", "ABORTED", "INTERRUPTED"])
def test_restore_does_not_wait_cancel_or_invent_job_state(wire, state):
    calls = []
    with client(transport(wire, calls, status=state)) as current:
        restored = job_reference().restore(current)
        assert restored.cancellation_requested is False and restored.status().status == state
    assert not any("/retrieve/" in r.url.path or "/cancel/" in r.url.path for r in calls)


@pytest.mark.parametrize("state,error", [("EMPTY", JobUnavailableError), ("UNKNOWN", ProtocolError)])
def test_unavailable_or_unknown_job_state(wire, state, error):
    with client(transport(wire, [], status=state)) as current:
        with pytest.raises(error):
            job_reference().restore(current)


@pytest.mark.parametrize("state,error", [("ABORTED", JobFailedError), ("INTERRUPTED", JobCancelledError)])
def test_restored_terminal_results_keep_existing_semantics(wire, state, error):
    with client(transport(wire, [], status=state)) as current:
        restored = job_reference().restore(current)
        with pytest.raises(error):
            restored.result(1)


@pytest.mark.parametrize("mutation,error", [
    (lambda row: [], MissingAssetError), (lambda row: [row, row], ProtocolError),
    (lambda row: [{**row, "@CLASS": "unexpected"}], ProtocolError),
    (lambda row: [{**row, "id": 99}], HandleIdentityError),
    (lambda row: [{**row, "id": True}], HandleIdentityError),
    (lambda row: [{**row, "urn": "other.context.42"}], HandleIdentityError),
    (lambda row: [{**row, "urn": None}], HandleIdentityError),
    (lambda row: [{**row, "observable": None}], ProtocolError)])
def test_lookup_must_establish_fresh_context_identity(wire, mutation, error):
    with client(transport(wire, [], query_mutate=mutation)) as current:
        with pytest.raises(error):
            reference(kind="observation", observation_id=42).restore(current)


def test_observer_requires_agent_and_service_identity_checks_are_explicit(wire):
    def nonagent(row):
        row["observable"]["semantics"]["type"] = ["QUALITY"]
        return [row]
    with client(transport(wire, [], query_mutate=nonagent)) as current:
        with pytest.raises(HandleIdentityError, match="agent"):
            reference(scope="s.c#3").restore(current)
    for configuration, error in (({"id": "s.other"}, ProtocolError), ({"id": "s.c", "serviceId": "other"}, HandleIdentityError)):
        with client(transport(wire, [], attach=configuration)) as current:
            with pytest.raises(error):
                reference().restore(current)
    with client(transport(wire, [], runtime_id="different")) as current:
        with pytest.raises(HandleIdentityError):
            reference().restore(current)
    with client(transport(wire, [], peer_id="different")) as current:
        with pytest.raises(HandleIdentityError, match="peer"):
            job_reference("resolver").restore(current)


def test_url_canonicalization_base_paths_and_explicit_verified_relocation(wire):
    canonical = reference(runtime_url="HTTPS://RUNTIME.INVALID:443/runtime/")
    assert canonical.runtime_url == "https://runtime.invalid/runtime"
    calls = []
    moved = reference(runtime_url="https://old.invalid/runtime")
    with client(transport(wire, calls)) as current:
        with pytest.raises(HandleIdentityError):
            moved.restore(current)
        assert not calls
        assert moved.restore(current, allow_relocation=True).id == "s.c"
        assert all(r.url.host != "old.invalid" for r in calls)
        with pytest.raises(HandleIdentityError):
            reference(runtime_url="https://runtime.invalid/another-service").restore(current)
        peer = job_reference("resolver", service_url="https://resolver.invalid/another-service")
        with pytest.raises(HandleIdentityError, match="job-service"):
            peer.restore(current)
        assert peer.restore(current, allow_relocation=True).service == "resolver"
    with Client("https://runtime.invalid/runtime", http_transport=httpx.MockTransport(transport(wire, []))) as missing:
        assert reference().restore(missing).id == "s.c"
        assert missing.runtime_service_id == "runtime-id"
        with pytest.raises(ConfigurationError):
            job_reference("resolver").restore(missing)


@pytest.mark.parametrize("args", [(None, reference(), False), ("bad", reference(), False),
    (None, "bad", False), (None, reference(), "yes")])
def test_restore_argument_validation(args):
    with pytest.raises(HandleFormatError):
        restore_handle(args[0], args[1], allow_relocation=args[2])


@pytest.mark.parametrize("code,error", [(401, AuthenticationError), (403, AuthorizationError),
    (404, MissingAssetError), (503, ServerError)])
def test_capability_failure_is_not_proof_of_absence(code, error):
    with client(lambda r: httpx.Response(code, text="issued-secret peer-secret")) as current:
        with pytest.raises(error) as caught:
            reference().restore(current)
        assert "secret" not in str(caught.value)


@pytest.mark.parametrize("code,error", [(401, AuthenticationError), (403, AuthorizationError),
    (404, MissingAssetError), (500, SubmissionOutcomeUnknown)])
def test_attach_failure_never_creates_replacement_work(wire, code, error):
    calls = []
    def handler(request):
        calls.append(request)
        return httpx.Response(200, json={"serviceId": "runtime-id"}) if request.url.path.endswith("/capabilities") else httpx.Response(code, text="issued-secret")
    with client(handler) as current:
        with pytest.raises(error):
            reference().restore(current)
    assert [r.url.path for r in calls] == ["/runtime/public/capabilities", "/runtime/api/v1/connect"]


def test_transport_failure_and_attach_ambiguity(wire):
    def offline(request):
        raise httpx.ConnectError("issued-secret", request=request)
    with client(offline) as current:
        with pytest.raises(TransportError):
            reference().restore(current)
    def attach_lost(request):
        if request.url.path.endswith("/capabilities"):
            return httpx.Response(200, json={"serviceId": "runtime-id"})
        raise httpx.ReadTimeout("issued-secret", request=request)
    with client(attach_lost) as current:
        with pytest.raises(SubmissionOutcomeUnknown):
            reference().restore(current)


def test_failed_and_partial_writes_preserve_existing_valid_file(tmp_path, monkeypatch):
    import klab_client.handles as implementation
    destination = tmp_path / "handle.json"
    old = reference()
    old.save(destination)
    before = destination.read_bytes()
    replacement = reference(context_id="s.other", scope="s.other")
    with monkeypatch.context() as patch:
        patch.setattr(implementation.os, "write", lambda fd, data: 0)
        with pytest.raises(HandleIOError):
            replacement.save(destination, overwrite=True)
    assert destination.read_bytes() == before
    with monkeypatch.context() as patch:
        patch.setattr(implementation.os, "replace", lambda *a: (_ for _ in ()).throw(PermissionError("secret")))
        with pytest.raises(HandleIOError):
            replacement.save(destination, overwrite=True)
    assert destination.read_bytes() == before
    assert len(list(tmp_path.iterdir())) == 1
    original = os.write
    calls = []
    def partial(fd, data):
        calls.append(len(data))
        return original(fd, data[:5] if len(calls) == 1 else data)
    with monkeypatch.context() as patch:
        patch.setattr(implementation.os, "write", partial)
        replacement.save(destination, overwrite=True)
    assert len(calls) == 2 and load_handle(destination) == replacement


def test_interrupted_write_cleans_temporary_and_does_not_publish(tmp_path, monkeypatch):
    import klab_client.handles as implementation
    destination = tmp_path / "handle.json"
    monkeypatch.setattr(implementation.os, "write", lambda *a: (_ for _ in ()).throw(KeyboardInterrupt()))
    with pytest.raises(KeyboardInterrupt):
        reference().save(destination)
    assert not destination.exists() and not list(tmp_path.iterdir())


def test_local_io_policy_limits_and_cleanup_errors_are_explicit(tmp_path, monkeypatch):
    with pytest.raises(HandleFormatError):
        reference().save(tmp_path / "file", overwrite="yes")
    with pytest.raises(HandleIOError):
        reference().save(tmp_path / "missing/file")
    assert not (tmp_path / "missing").exists()
    with pytest.raises(HandleIOError):
        load_handle(tmp_path / "missing")
    with pytest.raises(HandleIOError):
        load_handle(None)
    oversized = tmp_path / "big"
    oversized.write_bytes(b" " * 65537)
    with pytest.raises(HandleFormatError):
        load_handle(oversized)
    import klab_client.handles as implementation
    with monkeypatch.context() as patch:
        patch.setattr(implementation.Path, "unlink", lambda *a, **k: (_ for _ in ()).throw(PermissionError("secret")))
        with pytest.raises(HandleIOError, match="cleanup") as caught:
            reference().save(tmp_path / "file")
        assert "secret" not in str(caught.value)


@pytest.mark.parametrize("changes,error", [({"urn": "other.context.42"}, HandleIdentityError),
    ({"id": True}, HandleIdentityError), ({"id": -1}, HandleIdentityError),
    ({"@CLASS": "other"}, ProtocolError)])
def test_restored_observation_job_cannot_bind_a_foreign_result(wire, changes, error):
    calls = []
    changed = {**wire, **changes}
    handler = transport(changed, calls)
    with client(handler) as current:
        job = job_reference().restore(current)
        with pytest.raises(error):
            job.result(1)


@pytest.mark.integration
def test_actual_fresh_subprocess_round_trip_and_authorized_principal_change(wire, tmp_path):
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
    import threading
    calls = []
    wire["observable"]["contextualization"] = "MEASURE"
    class Handler(BaseHTTPRequestHandler):
        def respond(self, payload, code=200, text=False):
            data = str(payload).encode() if text else json.dumps(payload).encode()
            self.send_response(code)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        def do_GET(self):
            calls.append((self.command, self.path, self.headers.get("Authorization")))
            if self.headers.get("Authorization") not in ("creator-secret", "collaborator-secret"):
                return self.respond("forbidden", 403)
            if self.path.endswith("/capabilities"):
                return self.respond({"serviceId": "runtime-id"})
            if "/status/" in self.path:
                return self.respond({"status": "FINISHED"})
            if "/retrieve/" in self.path:
                return self.respond(wire)
            self.respond("unexpected", 500)
        def do_POST(self):
            calls.append((self.command, self.path, self.headers.get("Authorization")))
            self.rfile.read(int(self.headers.get("Content-Length", "0")))
            if self.headers.get("Authorization") not in ("creator-secret", "collaborator-secret"):
                return self.respond("forbidden", 403)
            if self.path.endswith("/createSession"):
                return self.respond("s", text=True)
            if self.path.endswith(("/createContext", "/connect")):
                return self.respond({"id": "s.c", "serviceId": "runtime-id"})
            if self.path.endswith("/resolve/observable"):
                return self.respond(wire["observable"])
            if self.path.endswith("/submit"):
                return self.respond(5)
            if self.path.endswith("/query"):
                return self.respond([wire])
            if self.path.endswith("/value"):
                return self.respond("123.25", text=True)
            self.respond("unexpected", 500)
        def log_message(self, *args):
            pass
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    env = {**os.environ, "KLAB_RUNTIME_URL": f"http://127.0.0.1:{server.server_port}/runtime",
        "KLAB_REASONER_URL": f"http://127.0.0.1:{server.server_port}/reasoner", "KLAB_RUNTIME_SERVICE_ID": "runtime-id",
        "KLAB_RUNTIME_TOKEN": "creator-secret", "KLAB_REASONER_TOKEN": "creator-secret", "KLAB_AGENT_NAME": "creator"}
    script = Path(__file__).parent / "fixtures/handle_process.py"
    try:
        created = subprocess.run([sys.executable, str(script), "create", str(tmp_path)], env=env,
            capture_output=True, text=True, timeout=30)
        assert created.returncode == 0, created.stderr
        assert json.loads(created.stdout)["created"]
        env.update(KLAB_RUNTIME_TOKEN="collaborator-secret", KLAB_AGENT_NAME="collaborator")
        restored = subprocess.run([sys.executable, str(script), "restore", str(tmp_path)], env=env,
            capture_output=True, text=True, timeout=30)
        assert restored.returncode == 0, restored.stderr
        assert json.loads(restored.stdout)["restored"]
        env.update(KLAB_RUNTIME_TOKEN="revoked-secret", KLAB_AGENT_NAME="revoked")
        denied = subprocess.run([sys.executable, str(script), "restore", str(tmp_path)], env=env,
            capture_output=True, text=True, timeout=30)
        assert denied.returncode != 0 and "AuthorizationError" in denied.stderr
        assert "revoked-secret" not in denied.stderr
    finally:
        server.shutdown()
        server.server_close()
        thread.join(10)
    assert sum(path.endswith("/submit") for _, path, _ in calls) == 1
    assert sum(path.endswith("/createSession") for _, path, _ in calls) == 1
    assert sum(path.endswith("/createContext") for _, path, _ in calls) == 1
    assert not any("/release" in path or "/cancel/" in path for _, path, _ in calls)
    assert any(token == "collaborator-secret" and path.endswith("/query") for _, path, token in calls)
    assert all("secret" not in path.read_text() for path in tmp_path.glob("*.json"))


def test_atomic_no_clobber_publication_under_concurrent_saves(tmp_path):
    from concurrent.futures import ThreadPoolExecutor
    destination = tmp_path / "handle.json"
    handles = [reference(context_id=f"s.c{i}", scope=f"s.c{i}") for i in range(2)]
    def write(handle):
        try:
            handle.save(destination)
            return True
        except HandleIOError:
            return False
    with ThreadPoolExecutor(2) as pool:
        results = list(pool.map(write, handles))
    assert results.count(True) == 1 and results.count(False) == 1
    assert load_handle(destination) in handles and len(list(tmp_path.iterdir())) == 1


@pytest.mark.parametrize("payload", [[], {}, {"serviceId": None}, {"serviceId": 4}])
def test_malformed_capabilities_do_not_become_missing_work(payload):
    calls = []
    def handler(request):
        calls.append(request)
        return httpx.Response(200, json=payload)
    with client(handler) as current:
        with pytest.raises(ProtocolError):
            reference().restore(current)
    assert len(calls) == 1


def test_configured_home_identity_mismatch_and_no_implicit_service_discovery(wire):
    calls = []
    with Client("https://runtime.invalid/runtime", "issued-secret", runtime_service_id="other-id",
                http_transport=httpx.MockTransport(transport(wire, calls))) as current:
        with pytest.raises(HandleIdentityError):
            reference().restore(current)
    assert len(calls) == 1 and calls[0].url.path.endswith("/capabilities")


def test_storage_read_after_restore_preserves_source_identity_and_valid_zero(wire):
    calls = []
    original = transport(wire, calls)
    def handler(request):
        if request.url.path.endswith("/value"):
            calls.append(request)
            assert json.loads(request.content)["sourceId"] == 42
            assert request.headers["klab-scope"] == "s.c.8#3"
            return httpx.Response(200, text="0")
        return original(request)
    with client(handler) as current:
        handle = reference(kind="observation", observation_id=42, scope="s.c.8#3")
        restored = handle.restore(current)
        assert restored.fetch_data([0]).values == (0,)


def test_os_write_and_fsync_errors_never_publish_a_partial_destination(tmp_path, monkeypatch):
    import klab_client.handles as implementation
    for operation in ("write", "fsync", "link"):
        with monkeypatch.context() as patch:
            patch.setattr(implementation.os, operation, lambda *a: (_ for _ in ()).throw(PermissionError("sensitive")))
            with pytest.raises(HandleIOError) as error:
                reference().save(tmp_path / "new")
            assert "sensitive" not in str(error.value)
        assert not list(tmp_path.iterdir())
