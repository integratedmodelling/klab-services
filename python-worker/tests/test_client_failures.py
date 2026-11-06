"""SIMULATED_PROVISIONAL transport guards; not native acceptance."""
import httpx
import pytest

from klab_worker import WorkerClient, WorkerError
from klab_worker.protocol import MAX_BYTES, PROTOCOL


def client(monkeypatch, handler):
    factory = httpx.Client
    monkeypatch.setattr(httpx, "Client", lambda **options: factory(**options, transport=httpx.MockTransport(handler)))
    return WorkerClient("https://worker.invalid/runtime", "synthetic-secret", scope="s.c", allow_experimental=True)


@pytest.mark.parametrize("case", ["redirect", "denied", "oversized", "unknown-protocol", "invalid-json", "non-object", "transport"])
def test_failed_wire_operation_never_replays_or_exposes_credentials(monkeypatch, case):
    calls = []
    def handler(request):
        calls.append(request)
        if case == "redirect": return httpx.Response(302, headers={"Location": "https://other.invalid"})
        if case == "denied": return httpx.Response(403, text="synthetic-secret")
        if case == "oversized": return httpx.Response(200, content=b"x" * (MAX_BYTES + 1))
        if case == "invalid-json": return httpx.Response(200, content=b"{")
        if case == "non-object": return httpx.Response(200, json=[])
        if case == "transport": raise httpx.ConnectError("synthetic-secret", request=request)
        return httpx.Response(200, json={"protocol": "unknown"})
    with client(monkeypatch, handler) as worker:
        with pytest.raises(WorkerError) as caught:
            worker.register("example", operation="double")
        assert "synthetic-secret" not in str(caught.value)
        assert len(calls) == 1


@pytest.mark.parametrize("kwargs", [{"credential": "bad\rheader"}, {"scope": ""}, {"timeout": float("nan")}, {"timeout": True}])
def test_bad_client_configuration_is_rejected_before_network(kwargs):
    options = {"credential": "synthetic", "scope": "s.c", "allow_experimental": True, **kwargs}
    with pytest.raises(WorkerError): WorkerClient("http://127.0.0.1:1/runtime", **options)


def test_unknown_task_and_invalid_publication_fail_before_network(monkeypatch):
    def handler(request):
        pytest.fail("Invalid local request must not reach transport")
    with client(monkeypatch, handler) as worker:
        for callback in (lambda: worker.status("../other"), lambda: worker.submit({}, operation="double"),
                         lambda: worker.fail(None), lambda: worker.publish({}, {}, implementation_sha256="wrong")):
            with pytest.raises(WorkerError): callback()


def test_idle_work_does_not_open_or_execute_operator_script(monkeypatch):
    with client(monkeypatch, lambda request: httpx.Response(200, json={"protocol": PROTOCOL, "state": "IDLE"})) as worker:
        assert worker.run_once("example", "absent.py")["state"] == "IDLE"
