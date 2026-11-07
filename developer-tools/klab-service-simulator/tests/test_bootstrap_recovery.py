"""Existing SDK over owned HTTP: local credential expiry, no replay/failover/identity swap.

JAVA-GAP-0015/0007: synthetic credentials/readiness, not real issuer approval or
composed twins. No native Modeler processes, endpoints or credentials are touched.
"""
from datetime import datetime, timedelta, timezone
from urllib.parse import urlsplit

import pytest

from klab_client import (Client, ConnectionIdentity, CredentialGrant, DeploymentProfile,
    IssuedTokenProvider, ObservationRequest, ServiceReference, connect)
from klab_client.connection_models import ConnectionIdentityError, ReadinessError, CredentialExpiredError
from klab_client.errors import AuthenticationError, TransportError
from conftest import launch
from test_vertical import client_args, control, geometry


def refs(ready):
    return tuple(ServiceReference(kind, url) for kind, url in ready["endpoints"].items())


def native_trace(ready):
    return [row for row in control(ready)["trace"] if not row["path"].startswith("/__sim__/")]


def clock(monkeypatch):
    import klab_client.connection as module
    now = [datetime.now(timezone.utc)]
    class Clock:
        @classmethod
        def now(cls, zone=None):
            return now[0] if zone is not None else now[0].replace(tzinfo=None)
    monkeypatch.setattr(module, "datetime", Clock)
    return now


def test_bootstrap_expiry_reacquires_same_identity_without_replaying_existing_job(simulator, monkeypatch):
    now = clock(monkeypatch)
    endpoints = refs(simulator)
    profile = DeploymentProfile("synthetic-renewal", endpoints, timeout=3, poll_interval=.001)
    identity = ConnectionIdentity("alice", "synthetic")
    calls = []
    def acquire():
        calls.append(len(calls) + 1)
        token = "sim-token-alice" if len(calls) == 1 else "sim-token-admin-alice"
        return CredentialGrant(identity, endpoints, {r.url: token for r in endpoints},
            expires_at=now[0] + timedelta(seconds=1 if len(calls) == 1 else 3600))
    with connect(profile, credentials=IssuedTokenProvider(identity, acquire)) as connection:
        session = connection.client.create_session(); context = session.create_context()
        try:
            job = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))
            assert job.status().status == "WAITING" and len(calls) == 1
            before_jobs = control(simulator)["counts"]["jobs"]
            now[0] += timedelta(seconds=2)  # Inject SDK expiry clock; no sleeping or issuer claim.
            observation = job.result(timeout=2)
            values = (*observation.fetch_data(range(256)).values, *observation.fetch_data(range(256, 400)).values)
            assert len(values) == 400 and values[0] == 100 and values[-1] == 195
            assert len(calls) == 2 and control(simulator)["counts"]["jobs"] == before_jobs
            submissions = [row for row in native_trace(simulator) if row["method"] == "POST" and row["path"].endswith("/api/v1/submit")]
            assert len(submissions) == 1  # Renewal occurs before transport, never resubmits Job.
        finally:
            context.release(); session.release()
    report = control(simulator)
    assert not any(report["counts"].values())
    assert "sim-token-" not in str(report)


@pytest.mark.parametrize("failure", ["principal-drift", "provider-error"])
def test_expired_reacquisition_denial_sends_no_scientific_request_or_replay(simulator, monkeypatch, failure):
    now = clock(monkeypatch)
    endpoints = refs(simulator)
    profile = DeploymentProfile("synthetic-denial", endpoints, timeout=3, poll_interval=.001)
    identity = ConnectionIdentity("alice", "synthetic")
    calls = []
    def acquire():
        calls.append(1)
        if len(calls) > 1 and failure == "provider-error": raise ValueError("Synthetic sim-token-alice issuer failure")
        selected = identity if len(calls) == 1 else ConnectionIdentity("bob", "synthetic")
        return CredentialGrant(selected, endpoints, {r.url: "sim-token-alice" for r in endpoints}, expires_at=now[0] + timedelta(seconds=1))
    with connect(profile, credentials=IssuedTokenProvider(identity, acquire)) as connection:
        session = connection.client.create_session(); context = session.create_context()
        try:
            before = native_trace(simulator)
            now[0] += timedelta(seconds=2)
            expected = ConnectionIdentityError if failure == "principal-drift" else AuthenticationError
            with pytest.raises(expected) as denied:
                context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))
            assert "sim-token-alice" not in str(denied.value)
            assert native_trace(simulator) == before and len(calls) == 2
            assert control(simulator)["counts"]["jobs"] == 0
        finally:
            # Explicit cleanup using the current owned test principal, not a hidden
            # fallback/recovery performed by the stale/denied connection.
            with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as cleanup:
                cleanup.attach_context(context.id).release(); cleanup.runtime.release_session(session.id)
    assert not any(control(simulator)["counts"].values())


def test_mixed_runtime_outage_recovery_preserves_explicit_selection_and_never_fails_over():
    with launch(seed=8) as standby:
        with launch(seed=7) as primary:
            port = urlsplit(primary["endpoints"]["runtime"]).port
            # Keep discovery peers reachable while only the selected Runtime goes
            # down; this is a mixed-provider outage, not four sequential dead probes.
            endpoints = (*tuple(r for r in refs(standby) if r.service_type != "runtime"),
                ServiceReference("runtime", primary["endpoints"]["runtime"]),
                ServiceReference("runtime", standby["endpoints"]["runtime"]))
            profile = DeploymentProfile("synthetic-mixed-runtime", endpoints,
                choices={"runtime": primary["endpoints"]["runtime"]}, timeout=3, poll_interval=.001)
            identity = ConnectionIdentity("alice", "synthetic")
            provider = IssuedTokenProvider(identity, {r.url: "sim-token-alice" for r in endpoints})
            connection = connect(profile, credentials=provider)
            assert connection.selected["runtime"].service_id == "sim-runtime-7"
        try:
            try:
                connection.refresh()  # Primary is down, standby still reachable.
            except TransportError:
                # OS refusal can consume the bounded global discovery deadline.
                # Incomplete refresh must invalidate readiness, not preserve an old
                # healthy label or switch endpoints. Do not lengthen production limits.
                assert connection.selected["runtime"].reachable is None
            assert connection.selected["runtime"].reference.url == primary["endpoints"]["runtime"]
            assert not connection.selected["runtime"].ready
            with pytest.raises(ReadinessError): connection.client.create_session()
            assert not any(control(standby)["counts"].values())
            with launch(port=port, seed=7) as recovered:
                connection.refresh()
                assert connection.selected["runtime"].service_id == "sim-runtime-7"
                session = connection.client.create_session(); context = session.create_context()
                try:
                    observation = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry())).result(timeout=2)
                    assert len(observation.fetch_data(range(256)).values) + len(observation.fetch_data(range(256, 400)).values) == 400
                finally:
                    context.release(); session.release()
                assert not any(control(recovered)["counts"].values())
            assert not any(control(standby)["counts"].values())
        finally:
            connection.close()


def test_restarted_endpoint_identity_drift_requires_explicit_reconnect_not_rebinding():
    with launch(seed=7) as original:
        port = urlsplit(original["endpoints"]["runtime"]).port
        endpoints = refs(original)
        profile = DeploymentProfile("synthetic-identity-drift", endpoints, timeout=3, poll_interval=.001)
        provider = IssuedTokenProvider(ConnectionIdentity("alice", "synthetic"), {r.url: "sim-token-alice" for r in endpoints})
        connection = connect(profile, credentials=provider)
    try:
        with launch(port=port, seed=9) as replaced:
            with pytest.raises(ConnectionIdentityError): connection.refresh()
            with pytest.raises(ConnectionIdentityError): connection.client.create_session()
            assert not any(control(replaced)["counts"].values())
            # Caller explicitly obtains a fresh connection/catalog, no old scopes
            # or work are transferred, no source composition/migration is implied.
            with connect(profile, credentials=provider) as fresh:
                assert fresh.selected["runtime"].service_id == "sim-runtime-9"
                session = fresh.client.create_session(); session.release()
            assert not any(control(replaced)["counts"].values())
    finally:
        connection.close()


def test_initial_expired_grant_is_refused_before_any_service_request(simulator, monkeypatch):
    now = clock(monkeypatch)
    endpoints = refs(simulator)
    profile = DeploymentProfile("synthetic-expired-intake", endpoints, timeout=3, poll_interval=.001)
    identity = ConnectionIdentity("alice", "synthetic")
    grant = CredentialGrant(identity, endpoints, {r.url: "sim-token-alice" for r in endpoints}, expires_at=now[0] - timedelta(seconds=1))
    before = native_trace(simulator)
    with pytest.raises(CredentialExpiredError): connect(profile, credentials=IssuedTokenProvider(identity, grant))
    assert native_trace(simulator) == before and not any(control(simulator)["counts"].values())


@pytest.mark.parametrize("seed", [True, -1, 2**31, "7"])
def test_backend_identity_seed_is_bounded_before_process_launch(seed):
    with pytest.raises(ValueError):
        with launch(seed=seed):
            pytest.fail("Invalid identity seed must never launch a process")
