"""SIMULATED_PROVISIONAL raw provider workflow, not native scientific execution."""
import asyncio
import json
from types import SimpleNamespace as NS

import pytest

from klab_client import Client, AsyncClient, ProviderData
from klab_client.api.primitives import KlabAsset
from klab_client.api.runtime import GeometryImpl
from klab_client.errors import (AuthorizationError, InvalidRequestError, JobCancelledError,
    JobFailedError, ProtocolError, WaitTimeout)
from conftest import launch, REPO
from test_vertical import client_args, control


def cases():
    return json.loads((REPO / "developer-tools/klab-service-simulator/src/klab_simulator/fixtures/provider-avro.json").read_text())["cases"]


def args(context, mode):
    return (KlabAsset("urn:synthetic:provider:" + mode),
        NS(get_observable=lambda: NS(urn="synthetic:Value")), GeometryImpl(), context)


@pytest.mark.parametrize("native_async", [False, True])
def test_independent_binary_provider_job_request_input_event_empty_and_owned_cleanup(native_async):
    with launch("provider-avro") as ready:
        input_fixture = next(case for case in cases() if case["case"] == "number")
        data_input = ProviderData(bytes.fromhex(input_fixture["response_hex"]))
        async def run():
            async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready)) as client:
                session = await client.create_session(); context = await session.create_context()
                try:
                    for mode, expected in (("number", (0.,)), ("input", (-2.5,)), ("event", (1.25,)), ("empty", None)):
                        kwargs = {"input_data": data_input} if mode == "input" else {"start_time": 1, "end_time": 2} if mode == "event" else {}
                        job = await client.resources.contextualize(*args(context, mode), **kwargs)
                        with pytest.raises(WaitTimeout): await job.result(timeout=0)
                        result = await job.result(timeout=2)
                        assert result.fields["doubleData"] == expected
                        assert result.fields["empty"] == (mode == "empty")
                        assert result.fields["metadata"]["source"] == "SIMULATED_PROVISIONAL"
                finally:
                    await context.release(); await session.release()
        if native_async: asyncio.run(run())
        else:
            with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
                session = client.create_session(); context = session.create_context()
                try:
                    for mode, expected in (("number", (0.,)), ("input", (-2.5,)), ("event", (1.25,)), ("empty", None)):
                        kwargs = {"input_data": data_input} if mode == "input" else {"start_time": 1, "end_time": 2} if mode == "event" else {}
                        job = client.resources.contextualize(*args(context, mode), **kwargs)
                        with pytest.raises(WaitTimeout): job.result(timeout=0)
                        result = job.result(timeout=2)
                        assert result.fields["doubleData"] == expected
                        assert result.fields["empty"] == (mode == "empty")
                        assert result.fields["metadata"]["source"] == "SIMULATED_PROVISIONAL"
                finally:
                    context.release(); session.release()
        state = control(ready)
        assert not any(state["counts"].values())
        assert sum(row["path"].endswith("/contextualize") for row in state["trace"]) == 4
        assert not any("/jobs/retrieve/" in row["path"] or row["path"].endswith("/submit") for row in state["trace"])


@pytest.mark.parametrize("mode,error", [("error", JobFailedError), ("malformed", ProtocolError), ("media", ProtocolError)])
def test_provider_error_payload_or_media_never_falls_back_to_json_or_synthetic_success(mode, error):
    with launch("provider-avro") as ready:
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            session = client.create_session(); context = session.create_context()
            try:
                job = client.resources.contextualize(*args(context, mode))
                with pytest.raises(error): job.result(timeout=2)
            finally:
                context.release(); session.release()
        assert not any(control(ready)["counts"].values())


def test_foreign_principal_cannot_compute_read_or_cancel_and_owner_can_cancel():
    with launch("provider-avro") as ready:
        with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
            session = client.create_session(); context = session.create_context()
            try:
                job = client.resources.contextualize(*args(context, "number"))
                with Client(ready["endpoints"]["runtime"], **client_args(ready, "sim-token-bob")) as denied:
                    for route in (f"/jobs/retrieveData/{job.id}", f"/jobs/cancel/{job.id}"):
                        with pytest.raises(AuthorizationError):
                            denied.transport.request("resources", "GET", route, scope=context.id,
                                service_id=ready["service_ids"]["runtime"], response="avro")
                    with pytest.raises(AuthorizationError):
                        denied.resources.contextualize(*args(context.id, "number"))
                assert job.cancel()
                with pytest.raises(JobCancelledError): job.result(timeout=2)
                with pytest.raises(InvalidRequestError): client.resources.contextualize(*args(context, "undeclared"))
            finally:
                context.release(); session.release()
        assert not any(control(ready)["counts"].values())


def test_independent_oracle_golden_bytes_decode_in_ordinary_sdk_without_oracle_runtime_dependency():
    from klab_client import _provider_avro as codec
    for case in cases():
        assert codec._decode(codec._REQUEST, bytes.fromhex(case["request_hex"])) == case["request"]
        if case["case"] != "malformed":
            assert codec._decode(codec._INSTANCE, bytes.fromhex(case["response_hex"])) == case["expected_instance"]


@pytest.mark.parametrize("scenario,owner,media,origin,body,status", [
    ("elevation", "alice", "application/octet-stream", True, "number", 404),
    ("provider-avro", "bob", "application/octet-stream", True, "number", 403),
    ("provider-avro", "alice", "application/octet-stream", False, "number", 403),
    ("provider-avro", "alice", "application/json", True, "number", 415),
    ("provider-avro", "alice", "application/octet-stream", True, "oversized", 413),
    ("provider-avro", "alice", "application/octet-stream", True, "unknown", 422)])
def test_finite_fixture_setup_bad_intake_and_denial_are_explicit(scenario, owner, media, origin, body, status):
    from klab_simulator.state import State
    state = State(scenario); state.contexts["s.c"] = {"owner": owner}
    headers = {"Authorization": "sim-token-" + owner, "klab-scope": "s.c", "Content-Type": media,
        "klab-service": state.service_ids["runtime"] if origin else "foreign"}
    raw = bytes.fromhex(next(case for case in cases() if case["case"] == "number")["request_hex"])
    if body == "oversized": raw = b"x" * (1024 * 1024 + 1)
    if body == "unknown": raw = b"unknown"
    assert state.request("POST", "/resources/contextualize", headers, raw)[0] == status
    assert not state.jobs


def test_in_process_fixture_data_route_waiting_refusal_and_no_json_result_alias():
    from klab_simulator.state import State
    state = State("provider-avro"); state.contexts["s.c"] = {"owner": "alice"}
    headers = {"Authorization": "sim-token-alice", "klab-scope": "s.c", "Content-Type": "application/octet-stream",
        "klab-service": state.service_ids["runtime"]}
    fixture = next(case for case in cases() if case["case"] == "number")
    status, identity, _ = state.request("POST", "/resources/contextualize", headers, bytes.fromhex(fixture["request_hex"]))
    assert status == 200
    assert state.request("GET", f"/resources/jobs/retrieveData/{identity}", headers)[0] == 409
    for _ in range(3): state.request("GET", f"/resources/jobs/status/{identity}", headers)
    status, raw, media = state.request("GET", f"/resources/jobs/retrieveData/{identity}", headers)
    assert status == 200 and raw == bytes.fromhex(fixture["response_hex"]) and media == "application/octet-stream"
    assert state.request("GET", f"/resources/jobs/retrieve/{identity}", headers)[0] == 422
