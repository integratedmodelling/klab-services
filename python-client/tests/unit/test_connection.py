"""Connection policy/lifecycle negative controls over native wire-shaped responses."""
from dataclasses import replace
from datetime import datetime, timedelta, timezone
import json
import traceback

import httpx
import pytest

from klab_client import (CertificateError, Client,
    ConnectionIdentity, ConnectionIdentityError, CredentialExpiredError,
    CredentialGrant, DeploymentProfile, HubCertificateProvider, IssuedTokenProvider,
    ReadinessError, ServiceReference, ServiceSelectionError, connect)
from klab_client.errors import (AuthenticationError, AuthorizationError, ConfigurationError,
    ProtocolError, SubmissionOutcomeUnknown)
from klab_client.credentials import parse_properties

TOKEN = "private-issued-test-credential-ABC123"
URL = "https://runtime.example/runtime"
HUB = "https://hub.example"


def profile(**kwargs):
    return DeploymentProfile("unit", (ServiceReference("runtime", URL),), required_services=("runtime",),
                             poll_interval=.001, **kwargs)


def certificate(tmp_path, **properties):
    values = {"klab.certificate.type": "ENGINE", "klab.certificate.level": "USER",
        "klab.username": "scientist", "klab.signature": "private-signature-DEF456",
        "klab.certificate": "private-certificate-GHI789", "klab.partner.hub": HUB,
        "klab.agreement": "agreement-1", "klab.user.email": "scientist@example.invalid"}
    values.update(properties)
    path = tmp_path / "engine.cert"
    path.write_bytes(("\n".join(key + "=" + value for key, value in values.items())).encode("latin-1"))
    return path


def bootstrap(**changes):
    result = {"hub": {"id": "test-issuer"}, "userData": {"identity": {"id": "scientist"},
        "token": TOKEN, "expiry": "2099-01-01T00:00:00", "groups": []},
        "services": [{"id": "hub-reference-not-runtime-uuid", "identityType": "RUNTIME", "urls": [URL],
                      "permissions": ["QUERY"], "primary": True, "online": True}]}
    result.update(changes)
    return result


class Wire:
    def __init__(self):
        self.calls = []
        self.operational = True
        self.service_id = "runtime-uuid-1"
        self.ack = True
        self.submit_code = 200
        self.auth = bootstrap()

    def __call__(self, request):
        self.calls.append(request)
        if request.url.host == "hub.example":
            assert request.url.path == "/api/v2/engines/auth-cert"
            assert "Authorization" not in request.headers
            assert json.loads(request.content) == {"name": "scientist", "key": "private-signature-DEF456",
                "userType": "ENGINE", "certificate": "private-certificate-GHI789", "level": "USER",
                "idAgreement": "agreement-1", "email": "scientist@example.invalid"}
            return httpx.Response(200, json=self.auth)
        if request.url.path.endswith("/public/capabilities"):
            return httpx.Response(200, json={"serviceId": self.service_id, "type": "RUNTIME"})
        if request.url.path.endswith("/public/status"):
            return httpx.Response(200, json={"serviceId": self.service_id, "serviceType": "RUNTIME",
                "@CLASS": "org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl",
                "available": True, "operational": self.operational})
        if request.url.path.endswith("/notifyUserScope"):
            for service in json.loads(request.content)["services"]:
                assert service["status"]["@CLASS"] == "org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl"
            return httpx.Response(200, json=self.ack)
        return httpx.Response(self.submit_code, text="session1" if self.submit_code == 200 else TOKEN)


@pytest.mark.parametrize("provider_kind", ["token", "certificate"])
def test_provider_substitution_uses_same_client_and_origin_headers(tmp_path, provider_kind):
    wire = Wire()
    transport = httpx.MockTransport(wire)
    provider = (IssuedTokenProvider(ConnectionIdentity("scientist", "test-issuer"), {URL: TOKEN})
        if provider_kind == "token" else HubCertificateProvider(certificate(tmp_path), http_transport=transport))
    with connect(profile(hub_url=HUB), credentials=provider, http_transport=transport) as connection:
        client = connection.client
        assert isinstance(client, Client)
        assert client.create_session().id == "session1"
        assert connection.selected["runtime"].service_id == "runtime-uuid-1"
        assert TOKEN not in repr(connection) + repr(provider) + repr(connection.catalog.summary())
        for request in wire.calls:
            if request.url.host != "hub.example":
                assert request.headers["Authorization"] == TOKEN
        notification = next(request for request in wire.calls if request.url.path.endswith("notifyUserScope"))
        assert json.loads(notification.content)["services"][0]["id"] == "runtime-uuid-1"
        calls = len(wire.calls)
    assert len(wire.calls) == calls  # local close never releases caller-owned work
    with pytest.raises(ConfigurationError, match="closed"):
        client.create_session()


def test_ambiguity_explicit_choice_and_policy_cannot_expand_trust():
    other = "https://second.example/runtime"
    deployment = replace(profile(), services=(ServiceReference("runtime", URL), ServiceReference("runtime", other)),
                         trusted_urls=(URL, other))
    provider = IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN, other: TOKEN})
    with pytest.raises(ServiceSelectionError, match="unique"):
        connect(deployment, credentials=provider, http_transport=httpx.MockTransport(Wire()))
    with connect(replace(deployment, choices={"runtime": other}), credentials=provider,
                 http_transport=httpx.MockTransport(Wire())) as connection:
        assert connection.client.transport.endpoint("runtime").url == other
    class BadPolicy:
        def select(self, *args):
            return "unverified"
    with pytest.raises(ServiceSelectionError, match="trust"):
        connect(profile(), credentials=provider, selection_policy=BadPolicy(), http_transport=httpx.MockTransport(Wire()))


def test_untrusted_reference_never_contacted():
    wire = Wire()
    bad = "https://forbidden.example/runtime"
    deployment = replace(profile(), services=(*profile().services, ServiceReference("runtime", bad)))
    with connect(deployment, credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN, bad: TOKEN}),
                 http_transport=httpx.MockTransport(wire)) as connection:
        assert connection.catalog.entries[1].trusted is False
        assert all(request.url.host != "forbidden.example" for request in wire.calls)


def test_readiness_and_latest_pending_advertisement():
    wire = Wire()
    wire.operational = wire.ack = False
    with connect(profile(), credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN}),
                 http_transport=httpx.MockTransport(wire)) as connection:
        with pytest.raises(ReadinessError, match="runtime"):
            _ = connection.client
        with pytest.raises(ReadinessError):
            connection.wait_ready(timeout=0)
        wire.operational = wire.ack = True
        connection.wait_ready(timeout=1)
        notifications = [json.loads(request.content) for request in wire.calls if request.url.path.endswith("notifyUserScope")]
        assert notifications[0]["services"][0]["status"]["operational"] is False
        assert notifications[-1]["services"][0]["status"]["operational"] is True
        assert connection.client.runtime_service_id == "runtime-uuid-1"
        wire.service_id = "different-runtime-uuid"
        with pytest.raises(ConnectionIdentityError, match="Pinned"):
            connection.refresh()
        with pytest.raises(ConnectionIdentityError):
            connection.client.create_session()


def test_renewal_is_atomic_identity_bound_and_never_replays_mutation():
    current = CredentialGrant(ConnectionIdentity("scientist", "issuer"), profile().services, {URL: TOKEN})
    provider = IssuedTokenProvider(current.identity, lambda: current)
    wire = Wire()
    with connect(profile(), credentials=provider, http_transport=httpx.MockTransport(wire)) as connection:
        newer = "private-rotated-token-JKL012"
        current = replace(current, credentials={URL: newer})
        connection.renew()
        wire.submit_code = 503
        with pytest.raises(SubmissionOutcomeUnknown):
            connection.client.create_session()
        submitted = [request for request in wire.calls if request.url.path.endswith("/createSession")]
        assert len(submitted) == 1 and submitted[0].headers["Authorization"] == newer
        current = replace(current, identity=ConnectionIdentity("another-scientist", "issuer"))
        with pytest.raises(ConnectionIdentityError, match="principal/issuer"):
            connection.renew()
        with pytest.raises(ConnectionIdentityError):
            connection.refresh()


def test_expired_provider_does_not_send_any_request():
    grant = CredentialGrant(ConnectionIdentity("scientist"), profile().services, {URL: TOKEN},
        datetime.now(timezone.utc)-timedelta(seconds=1))
    wire = Wire()
    with pytest.raises(CredentialExpiredError):
        connect(profile(), credentials=IssuedTokenProvider(grant.identity, lambda: grant), http_transport=httpx.MockTransport(wire))
    assert wire.calls == []


@pytest.mark.parametrize("code,error", [(401, AuthenticationError), (403, AuthorizationError), (302, Exception)])
def test_hub_denials_redirects_and_secrets_not_echoed(tmp_path, code, error):
    requests = []
    def handler(request):
        requests.append(request)
        return httpx.Response(code, text=TOKEN, headers={"Location": "https://forbidden.example/"})
    provider = HubCertificateProvider(certificate(tmp_path), http_transport=httpx.MockTransport(handler))
    with pytest.raises(error) as caught:
        provider.acquire(profile(hub_url=HUB))
    assert len(requests) == 1 and TOKEN not in str(caught.value)


@pytest.mark.parametrize("change", [{"klab.certificate.type": "RUNTIME"}, {"klab.partner.hub": "https://forbidden.example"},
    {"klab.certificate.level": "ADMIN"}, {"klab.signature": ""}, {"klab.validuntil": "2000-01-01"}])
def test_invalid_certificate_fails_before_exchange(tmp_path, change):
    wire = Wire()
    provider = HubCertificateProvider(certificate(tmp_path, **change), http_transport=httpx.MockTransport(wire))
    with pytest.raises(AuthenticationError):
        provider.acquire(profile(hub_url=HUB))
    assert wire.calls == []


@pytest.mark.parametrize("field,value", [("expiry", "2000-01-01"), ("token", "bad\r\ntoken"), ("groups", {}), ("identity", {})])
def test_malformed_hub_identity_is_not_anonymous(tmp_path, field, value):
    wire = Wire()
    wire.auth["userData"][field] = value
    provider = HubCertificateProvider(certificate(tmp_path), http_transport=httpx.MockTransport(wire))
    with pytest.raises((ProtocolError, AuthenticationError)):
        provider.acquire(profile(hub_url=HUB))


def test_profile_schema_atomic_save_and_secret_field_rejection(tmp_path):
    deployment = profile(credential_refs={URL: "env:KLAB_RUNTIME_TOKEN"})
    path = tmp_path / "deployment.json"
    deployment.save(path)
    assert DeploymentProfile.load(path) == deployment
    with pytest.raises(Exception):
        deployment.save(path)
    assert json.loads(path.read_text())["credential_refs"] == {URL: "env:KLAB_RUNTIME_TOKEN"}
    for invalid in ({"token": TOKEN}, {"schema_version": 2}, {"schema_version": True},
                    {"services": [{"type": "runtime", "url": URL, "credential": TOKEN}]}):
        with pytest.raises(ConfigurationError):
            DeploymentProfile.from_dict({**deployment.to_dict(), **invalid})


def test_java_properties_independent_escaping_continuation_and_encoding():
    contents = b"# ignored\r\na\\ key : caf\xe9\r\npath=C:\\\\private\\\\cert\nvalue=ab\\\n  cd\nvalue=last\\u0021\n\\\n#continued=literal\n"
    assert parse_properties(contents) == {"a key": "caf\u00e9", "path": "C:\\private\\cert",
                                          "value": "last!", "#continued": "literal"}
    assert parse_properties(b"emoji=\\uD83D\\uDE00") == {"emoji": "\U0001f600"}
    with pytest.raises(CertificateError):
        parse_properties(b"bad=\\uXYZ1")


class FailingCloseTransport(httpx.MockTransport):
    """Allow two metadata probes, then fail closing the scientific transport."""
    def __init__(self, wire):
        super().__init__(wire)
        self.close_calls = 0

    def close(self):
        self.close_calls += 1
        if self.close_calls >= 3:
            raise OSError(TOKEN)


def test_initialization_failure_preserved_when_cleanup_also_fails():
    wire = Wire()
    wire.ack = "invalid-acknowledgement"
    transport = FailingCloseTransport(wire)
    with pytest.raises(ProtocolError, match="boolean acknowledgement") as caught:
        connect(profile(), credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN}),
                http_transport=transport)
    assert transport.close_calls == 3
    assert caught.value.__notes__ == ["Connection cleanup failed: OSError"]
    assert TOKEN not in "".join(traceback.format_exception(caught.value))
    assert [request.method for request in wire.calls] == ["GET", "GET", "POST"]
    assert wire.calls[-1].url.path.endswith("/notifyUserScope")


@pytest.mark.parametrize("error_type", [SubmissionOutcomeUnknown, ProtocolError, KeyboardInterrupt])
def test_context_exit_preserves_primary_identity_traceback_and_safe_cleanup_note(error_type):
    wire = Wire()
    transport = FailingCloseTransport(wire)
    connection = connect(profile(), credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN}),
                         http_transport=transport)
    client = connection.client
    requests_before_exit = len(wire.calls)
    primary = error_type("primary operation failure")
    def raise_primary():
        raise primary
    with pytest.raises(error_type) as caught:
        with connection:
            raise_primary()
    assert caught.value is primary
    assert traceback.extract_tb(primary.__traceback__)[-1].name == "raise_primary"
    assert primary.__notes__ == ["Connection cleanup failed: OSError"]
    assert TOKEN not in "".join(traceback.format_exception(primary))
    assert transport.close_calls == 3
    assert len(wire.calls) == requests_before_exit  # No release, cancellation or retry.
    with pytest.raises(ConfigurationError, match="closed"):
        client.create_session()


def test_context_exit_without_primary_reports_cleanup_failure():
    wire = Wire()
    transport = FailingCloseTransport(wire)
    connection = connect(profile(), credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN}),
                         http_transport=transport)
    with pytest.raises(OSError):
        with connection:
            pass
    assert transport.close_calls == 3
    with pytest.raises(ConfigurationError, match="closed"):
        _ = connection.client


def test_explicit_close_reports_cleanup_failure():
    transport = FailingCloseTransport(Wire())
    connection = connect(profile(), credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN}),
                         http_transport=transport)
    with pytest.raises(OSError):
        connection.close()
    assert transport.close_calls == 3
    with pytest.raises(ConfigurationError, match="closed"):
        connection.refresh()
