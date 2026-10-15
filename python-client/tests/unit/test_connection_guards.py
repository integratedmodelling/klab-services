"""Malformed configuration and native packages must fail without gaining authority."""
from dataclasses import replace
from datetime import datetime, timezone
import time

import httpx
import pytest

from klab_client import (CertificateError, ConnectionIdentity, CredentialGrant, DeploymentProfile,
                        HubCertificateProvider, IssuedTokenProvider, ServiceReference, connect)
from klab_client._bootstrap import MAX_BOOTSTRAP_BYTES, decode_bootstrap, public_fields, wire_request
from klab_client.connection_models import checked_url, loopback, parse_expiry
from klab_client.errors import AuthenticationError, AuthorizationError, ConfigurationError, ProtocolError, TransportError
from test_connection import HUB, TOKEN, URL, Wire, bootstrap, certificate, profile


@pytest.mark.parametrize("change", [
    {"name": ""}, {"services": ("untyped",)}, {"required_services": ()}, {"required_services": ("engine",)},
    {"required_services": ("runtime", "runtime")}, {"max_services": 0}, {"max_services": True}, {"max_services": 257},
    {"timeout": 0}, {"timeout": float("inf")}, {"poll_interval": False}, {"test_loopback": "yes"},
    {"local_federation": 1}, {"verify": False}, {"verify": 1}, {"test_loopback": True},
    {"credential_refs": {URL: TOKEN}}, {"credential_refs": {"https://foreign.example": "env:TOKEN"}},
    {"choices": {"engine": URL}}, {"choices": {"runtime": ""}},
    {"worldview": ""}, {"worldview": 123},
])
def test_invalid_profile_values(change):
    with pytest.raises(ConfigurationError):
        replace(profile(), **change)


@pytest.mark.parametrize("url", ["https://user:password@example.com", "ftp://example.com", "https://example.com/?token=secret",
                                "https://example.com/#secret", None, 123])
def test_profile_url_validation(url):
    with pytest.raises(ConfigurationError):
        checked_url(url)


def test_url_and_expiry_normalization():
    assert checked_url("HTTPS://Runtime.Example:443/runtime/") == URL
    assert loopback("http://localhost/runtime") and loopback("http://[::1]/runtime")
    assert not loopback("https://runtime.example/runtime")
    assert parse_expiry("2099-01-01T01:00:00+01:00") == datetime(2099, 1, 1, tzinfo=timezone.utc)
    assert parse_expiry(None) is None
    for value in (True, "", "impossible-date"):
        with pytest.raises(ProtocolError):
            parse_expiry(value)


@pytest.mark.parametrize("kwargs", [{"principal": ""}, {"principal": "scientist", "issuer": ""},
    {"principal": "scientist", "issuer": 123}, {"principal": "scientist", "email": []}])
def test_identity_validation(kwargs):
    with pytest.raises(ProtocolError):
        ConnectionIdentity(**kwargs)


@pytest.mark.parametrize("args", [("", URL), ("runtime", URL, ""), ("runtime", URL, None, "")])
def test_reference_identity_validation(args):
    with pytest.raises(ProtocolError):
        ServiceReference(*args)


@pytest.mark.parametrize("kwargs", [{"primary": "true"}, {"online": 1}, {"permissions": ("READ_ALL",)}])
def test_unknown_reference_authority_states(kwargs):
    with pytest.raises(ProtocolError):
        ServiceReference("runtime", URL, **kwargs)


@pytest.mark.parametrize("kwargs,error", [
    ({"identity": "anonymous"}, ProtocolError), ({"credentials": {URL: ""}}, AuthenticationError),
    ({"credentials": {URL: "bad\nheader"}}, AuthenticationError), ({"references": ("untyped",)}, ProtocolError),
    ({"sensitive_values": (123,)}, ProtocolError), ({"expires_at": datetime(2099, 1, 1)}, ProtocolError),
])
def test_invalid_grants(kwargs, error):
    values = {"identity": ConnectionIdentity("scientist"), "references": profile().services, "credentials": {URL: TOKEN}}
    with pytest.raises(error):
        CredentialGrant(**{**values, **kwargs})


def test_env_sources_missing_credentials_and_callback_exception_redaction(monkeypatch):
    deployment = profile(credential_refs={URL: "env:UNIT_PRIVATE_TOKEN"})
    provider = IssuedTokenProvider.from_env(deployment, ConnectionIdentity("scientist"))
    monkeypatch.delenv("UNIT_PRIVATE_TOKEN", raising=False)
    with pytest.raises(AuthenticationError):
        provider.acquire(deployment)
    monkeypatch.setenv("UNIT_PRIVATE_TOKEN", TOKEN)
    grant = provider.acquire(deployment)
    assert grant.for_url(URL) == TOKEN
    with pytest.raises(AuthenticationError):
        grant.for_url("https://foreign.example")
    def broken():
        raise AuthenticationError(TOKEN)
    with pytest.raises(AuthenticationError) as error:
        IssuedTokenProvider(grant.identity, broken).acquire(deployment)
    assert TOKEN not in str(error.value)
    with pytest.raises(AuthenticationError):
        IssuedTokenProvider(grant.identity, 123).acquire(deployment)
    with pytest.raises(AuthenticationError):
        IssuedTokenProvider(grant.identity, {URL: "\n"}).acquire(deployment)


@pytest.mark.parametrize("mutation", [
    lambda response: response.update(hub={"id": TOKEN}),
    lambda response: response["userData"].update(identity={"id": "another-scientist"}),
    lambda response: response.update(services={}),
    lambda response: response.update(services=[{}]*65),
    lambda response: response["services"][0].update(urls="https://bad.example"),
    lambda response: response["services"][0].update(permissions=["OWNER"]),
    lambda response: response["services"][0].update(urls=[123]),
    lambda response: response["services"][0].update(urls=["https://" + TOKEN + ".example"]),
    lambda response: response["services"][0].update(id=TOKEN),
    lambda response: response["services"][0].update(identityType=TOKEN),
    lambda response: response["services"][0].update(identityType=123),
    lambda response: response["services"][0].update(urls=[URL]*65),
])
def test_malformed_bootstrap_metadata(mutation):
    response = bootstrap()
    mutation(response)
    with pytest.raises((AuthenticationError, ProtocolError)):
        decode_bootstrap(response, profile(), "scientist", ())


def test_hub_missing_services_retained_unknown_and_secret_removal():
    response = bootstrap()
    response["userData"]["identity"]["metadata"] = {"password": TOKEN, "harmless_extension": "safe"}
    response["services"][0].update(id=None)
    identity, expiry, token, refs = decode_bootstrap(response, profile(), "scientist", ())
    assert identity.raw["metadata"] == {"harmless_extension": "safe"}
    assert token == TOKEN and expiry is not None and refs[0].reference_id is None
    response["services"][0].pop("identityType")
    assert decode_bootstrap(response, profile(), "scientist", ())[3][0].service_type == "legacy_node"
    assert public_fields({"safe": [TOKEN, {"signature": "not retained"}, 1]}, ("", TOKEN)) == {
        "safe": ["[REDACTED]", {}, 1]}


@pytest.mark.parametrize("value", [b"invalid-json", b"[]", b"x"*(MAX_BOOTSTRAP_BYTES+1)], ids=["malformed", "nonobject", "oversized"])
def test_invalid_or_unbounded_connection_response(value):
    with pytest.raises(ProtocolError):
        wire_request(URL, "GET", transport=httpx.MockTransport(lambda request: httpx.Response(200, content=value)))


def test_connection_wire_timeouts_and_transport_errors(monkeypatch):
    from types import SimpleNamespace
    import klab_client._bootstrap as bootstrap_module
    with pytest.raises(TransportError, match="deadline"):
        wire_request(URL, "GET", deadline=time.monotonic()-1)
    def failed(request):
        raise httpx.ReadTimeout(TOKEN)
    with pytest.raises(TransportError) as error:
        wire_request(URL, "GET", transport=httpx.MockTransport(failed))
    assert TOKEN not in str(error.value)
    class Slow(httpx.SyncByteStream):
        def __iter__(self):
            clock.now = 10
            yield b"{}"
    clock = SimpleNamespace(now=0)
    monkeypatch.setattr(bootstrap_module, "time", SimpleNamespace(monotonic=lambda: clock.now))
    with pytest.raises(TransportError):
        wire_request(URL, "GET", deadline=10,
            transport=httpx.MockTransport(lambda request: httpx.Response(200, stream=Slow())))


def test_certificate_read_bounds_and_TLS_policy(tmp_path):
    missing = HubCertificateProvider(tmp_path / "missing.cert")
    with pytest.raises(CertificateError):
        missing.acquire(profile(hub_url=HUB))
    oversized = tmp_path / "large.cert"
    oversized.write_bytes(b"x"*(MAX_BOOTSTRAP_BYTES+1))
    with pytest.raises(CertificateError, match="budget"):
        HubCertificateProvider(oversized).acquire(profile(hub_url=HUB))
    for level, hub in (("USER", "http://127.0.0.1:18090"), ("TEST", HUB)):
        with pytest.raises(CertificateError):
            HubCertificateProvider(certificate(tmp_path, **{"klab.certificate.level": level, "klab.partner.hub": hub})).acquire(profile(hub_url=hub))


def test_profiles_malformed_files_and_no_overwrite(tmp_path):
    from klab_client import ProfileIOError
    from klab_client.errors import InvalidRequestError
    with pytest.raises(ProfileIOError):
        DeploymentProfile.load(tmp_path / "missing")
    path = tmp_path / "malformed.json"
    path.write_text("[]")
    with pytest.raises(ConfigurationError):
        DeploymentProfile.load(path)
    path.write_text("{invalid")
    with pytest.raises(ProfileIOError):
        DeploymentProfile.load(path)
    with pytest.raises(ConfigurationError):
        DeploymentProfile.from_dict({"schema_version": 1, "services": [{}]})
    with pytest.raises(InvalidRequestError):
        profile().save(tmp_path / "profile.json", overwrite="yes")
    with pytest.raises(ProfileIOError):
        profile().save(tmp_path / "absent/profile.json")
    with pytest.raises(ProfileIOError):
        profile().save("nul\x00path")


def test_custom_selection_and_metadata_only_connection():
    from klab_client import ServiceSelectionError
    class Policy:
        def select(self, kind, entries, preference):
            return entries[0]
    with connect(profile(), credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN}),
        selection_policy=Policy(), http_transport=httpx.MockTransport(Wire()), initialize=False) as connection:
        assert connection.client.runtime_service_id == "runtime-uuid-1"
        with pytest.raises(ConfigurationError):
            connection.wait_ready(timeout=True)
    deployment = DeploymentProfile.from_urls("metadata", {"reasoner": "https://reasoner.example"}, required_services=("reasoner",))
    def reasoner(request):
        return httpx.Response(200, json={"serviceId": "reasoner-id", "type" if request.url.path.endswith("capabilities") else "serviceType": "REASONER", "available": True, "operational": True})
    with connect(deployment, credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {"https://reasoner.example": TOKEN}),
                 http_transport=httpx.MockTransport(reasoner)) as connection:
        assert connection.wait_ready(timeout=0).entries[0].ready
        with pytest.raises(ServiceSelectionError, match="Runtime"):
            _ = connection.client


def test_injected_provider_still_bound_by_catalog_budget_and_public_identity():
    from klab_client import CredentialProvider
    class Bad(CredentialProvider):
        def acquire(self, *args, **kwargs):
            return None
    with pytest.raises(ProtocolError):
        connect(profile(), credentials=Bad())
    grants = [CredentialGrant(ConnectionIdentity("scientist"), profile().services*65, {URL: TOKEN}),
              CredentialGrant(ConnectionIdentity(TOKEN), profile().services, {URL: TOKEN})]
    for grant in grants:
        with pytest.raises(ProtocolError):
            connect(profile(), credentials=IssuedTokenProvider(grant.identity, lambda: grant))
    with pytest.raises(ConfigurationError):
        connect({}, credentials=Bad())
    with pytest.raises(ConfigurationError):
        connect(profile(), credentials=Bad(), initialize="yes")


@pytest.mark.parametrize("change", [
    {"serviceId": None}, {"serviceId": TOKEN}, {"serviceType": "ENGINE"},
    {"type": "FUTURE_RUNTIME"},
    {"available": "true"}, {"worldviewId": 123}, {"worldviewId": "different-worldview"}, {"worldviewId": TOKEN},
])
def test_mixed_capabilities_remain_diagnosable_without_selection(change):
    secondary = "https://secondary.example/runtime"
    wire = Wire()
    def response(request):
        result = wire(request)
        route = "capabilities" if "serviceId" in change or "type" in change else "status"
        if request.url.host == "secondary.example" and request.url.path.endswith(route):
            payload = result.json()
            payload.update(change)
            return httpx.Response(200, json=payload)
        return result
    # Worldview is a capabilities field rather than a status scheduling hint.
    if "worldviewId" in change:
        def response(request):
            result = wire(request)
            if request.url.path.endswith("capabilities"):
                payload = result.json()
                payload["worldviewId"] = change["worldviewId"] if request.url.host == "secondary.example" else "imod"
                return httpx.Response(200, json=payload)
            return result
    deployment = replace(profile(), services=(*profile().services, ServiceReference("runtime", secondary)),
        trusted_urls=(URL, secondary), worldview="imod" if "worldviewId" in change else None)
    with connect(deployment, credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN, secondary: TOKEN}),
                 http_transport=httpx.MockTransport(response)) as connection:
        assert connection.selected["runtime"].reference.url == URL
        assert connection.catalog.entries[1].supported is False
        assert connection.catalog.entries[1].diagnostic
        import json
        assert TOKEN not in json.dumps(connection.catalog.summary())


def test_configured_runtime_pin_applies_to_certificate_discovery(tmp_path):
    from klab_client import ServiceSelectionError
    transport = httpx.MockTransport(Wire())
    deployment = replace(profile(hub_url=HUB), services=(ServiceReference("runtime", URL, expected_service_id="wrong-pinned-id"),))
    with pytest.raises(ServiceSelectionError):
        connect(deployment, credentials=HubCertificateProvider(certificate(tmp_path), http_transport=transport), http_transport=transport)


def test_conflicting_references_fail_and_unsupported_types_are_retained():
    deployment = replace(profile(), services=(*profile().services, ServiceReference("runtime", URL, expected_service_id="other-id")))
    with pytest.raises(ProtocolError, match="Conflicting"):
        connect(deployment, credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN}),
                http_transport=httpx.MockTransport(Wire()))
    deployment = replace(profile(), services=(*profile().services, ServiceReference("engine", URL)))
    with connect(deployment, credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN}),
                 http_transport=httpx.MockTransport(Wire())) as connection:
        assert connection.catalog.entries[1].supported is False


def test_reacquisition_before_send_keeps_existing_client_identity(monkeypatch):
    from datetime import timedelta
    import klab_client.connection as module
    wire = Wire()
    identity = ConnectionIdentity("scientist", "issuer")
    current = CredentialGrant(identity, profile().services, {URL: TOKEN}, datetime.now(timezone.utc)+timedelta(days=1))
    provider = IssuedTokenProvider(identity, lambda: current)
    with connect(profile(), credentials=provider, http_transport=httpx.MockTransport(wire)) as connection:
        client = connection.client
        current = replace(current, credentials={URL: "new-expiry-reacquired-credential-STU901"}, expires_at=datetime(2099, 1, 1, tzinfo=timezone.utc))
        class Future(datetime):
            @classmethod
            def now(cls, tz=None):
                return datetime(2050, 1, 1, tzinfo=timezone.utc)
        monkeypatch.setattr(module, "datetime", Future)
        client.create_session()
        assert wire.calls[-1].headers["Authorization"] == current.for_url(URL)
        connection._grant = replace(current, expires_at=datetime(2040, 1, 1, tzinfo=timezone.utc))
        connection.refresh()
        assert connection.client is client
        current = replace(current, references=())
        connection.renew()
        assert connection.selected["runtime"].diagnostic == "Selected service no longer advertised"


def test_peer_denial_protocol_errors_and_bounded_wait(monkeypatch):
    from types import SimpleNamespace
    import klab_client.connection as connection_module
    import klab_client._bootstrap as bootstrap_module
    from klab_client import ReadinessError
    from klab_client.errors import AuthorizationError
    wire = Wire()
    provider = IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN})
    wire.ack = "true"
    with pytest.raises(ProtocolError, match="boolean"):
        connect(profile(), credentials=provider, http_transport=httpx.MockTransport(wire))
    wire.ack = False
    transport = httpx.MockTransport(wire)
    with connect(profile(), credentials=provider, http_transport=transport) as connection:
        clock = SimpleNamespace(now=0)
        def sleep(duration):
            clock.now += duration
        def expire_probe(request):
            assert request.url.path.endswith("/public/capabilities")
            clock.now = 10
            raise httpx.ReadTimeout(TOKEN)
        with monkeypatch.context() as patch:
            patch.setattr(connection_module, "time", SimpleNamespace(monotonic=lambda: clock.now, sleep=sleep))
            patch.setattr(bootstrap_module, "time", SimpleNamespace(monotonic=lambda: clock.now))
            patch.setattr(transport, "handler", expire_probe)
            with pytest.raises(ReadinessError):
                connection.wait_ready(timeout=10)
            assert clock.now == 10
            def failed(**kwargs):
                raise TransportError("read-only observation failed")
            patch.setattr(connection, "refresh", failed)
            with pytest.raises(TransportError):
                connection.wait_ready(timeout=1)
    def denied(request):
        if request.url.path.endswith("notifyUserScope"):
            return httpx.Response(403, text=TOKEN)
        return wire(request)
    with pytest.raises(AuthorizationError):
        connect(profile(), credentials=provider, http_transport=httpx.MockTransport(denied))


def test_local_peers_not_advertised_to_remote_destinations():
    local = "http://127.0.0.1:8199/reasoner"
    wire = Wire()
    def handler(request):
        if request.url.host == "127.0.0.1":
            if request.url.path.endswith("notifyUserScope"):
                return httpx.Response(200, json=True)
            return httpx.Response(200, json={"serviceId": "local-reasoner-id", "type" if request.url.path.endswith("capabilities") else "serviceType": "REASONER", "available": True, "operational": True})
        return wire(request)
    deployment = replace(profile(), services=(*profile().services, ServiceReference("reasoner", local)),
        trusted_urls=(URL, local), required_services=("runtime", "reasoner"))
    provider = IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN, local: TOKEN})
    with connect(deployment, credentials=provider, http_transport=httpx.MockTransport(handler)):
        notification = next(request for request in wire.calls if request.url.path.endswith("notifyUserScope"))
        import json
        assert len(json.loads(notification.content)["services"]) == 1
    with pytest.raises(ConfigurationError, match="remote"):
        connect(replace(deployment, local_federation=True), credentials=provider, http_transport=httpx.MockTransport(handler))


def test_properties_EOF_escaped_separators_and_invalid_surrogate():
    from klab_client.credentials import parse_properties
    assert parse_properties(b"a\\:b value\nonlykey\nend=continued\\") == {
        "a:b": "value", "onlykey": "", "end": "continued"}
    with pytest.raises(CertificateError, match="Unicode"):
        parse_properties(b"broken=\\uD800")


def test_certificate_http_service_origin_and_nonTEST_TLS_disable_reject(tmp_path):
    local = "http://127.0.0.1:8199/runtime"
    response = bootstrap()
    response["services"][0]["urls"] = [local, "https://untrusted.example/runtime"]
    provider = HubCertificateProvider(certificate(tmp_path), http_transport=httpx.MockTransport(lambda request: httpx.Response(200, json=response)))
    with pytest.raises(AuthenticationError, match="HTTPS"):
        provider.acquire(DeploymentProfile.from_urls("user", {"runtime": local}, hub_url=HUB, required_services=("runtime",)))
    cert = certificate(tmp_path, **{"klab.partner.hub": "https://127.0.0.1:18090"})
    deployment = DeploymentProfile.from_urls("loopback", {"runtime": local}, hub_url="https://127.0.0.1:18090",
        required_services=("runtime",), test_loopback=True, verify=False)
    with pytest.raises(CertificateError, match="verified"):
        HubCertificateProvider(cert).acquire(deployment)
    cert = certificate(tmp_path, **{"klab.partner.hub": "http://127.0.0.1:18090", "klab.certificate.level": "TEST"})
    provider = HubCertificateProvider(cert, http_transport=httpx.MockTransport(lambda request: httpx.Response(200, json=response)))
    grant = provider.acquire(replace(deployment, hub_url="http://127.0.0.1:18090"))
    assert grant.for_url(local) == TOKEN
    assert "https://untrusted.example/runtime" not in grant.credentials


def test_profile_publication_and_cleanup_failures_preserve_original(tmp_path, monkeypatch):
    from pathlib import Path
    import klab_client.connection_models as module
    from klab_client import ProfileIOError
    path = tmp_path / "deployment.json"
    profile().save(path)
    newer = replace(profile(), name="updated")
    newer.save(path, overwrite=True)
    assert DeploymentProfile.load(path).name == "updated"
    original = path.read_bytes()
    def failed(*args, **kwargs):
        raise OSError("sensitive path must not be echoed")
    with monkeypatch.context() as patch:
        patch.setattr(module.os, "replace", failed)
        with pytest.raises(ProfileIOError, match="publication"):
            profile().save(path, overwrite=True)
    assert path.read_bytes() == original and not list(tmp_path.glob(".klab-profile-*"))
    with monkeypatch.context() as patch:
        patch.setattr(module.os, "link", failed)
        patch.setattr(Path, "unlink", failed)
        with pytest.raises(ProfileIOError, match="publication") as caught:
            profile().save(tmp_path / "failed.json")
        assert caught.value.__notes__ == ["Profile temporary cleanup failed"]
    for temporary in tmp_path.glob(".klab-profile-*"):
        temporary.unlink()
    with monkeypatch.context() as patch:
        patch.setattr(Path, "unlink", failed)
        with pytest.raises(ProfileIOError, match="temporary cleanup"):
            profile().save(tmp_path / "published.json")
    for temporary in tmp_path.glob(".klab-profile-*"):
        temporary.unlink()


def test_deadline_checked_on_empty_response_completion(monkeypatch):
    from types import SimpleNamespace
    import klab_client._bootstrap as bootstrap_module
    clock = SimpleNamespace(now=0)
    monkeypatch.setattr(bootstrap_module, "time", SimpleNamespace(monotonic=lambda: clock.now))
    class EmptyDelayed(httpx.SyncByteStream):
        def __iter__(self):
            clock.now = 10
            yield from ()
    with pytest.raises(TransportError, match="deadline"):
        wire_request(URL, "GET", deadline=10,
            transport=httpx.MockTransport(lambda request: httpx.Response(200, stream=EmptyDelayed())))


def test_metadata_only_renewal_and_ready_diagnostics():
    deployment = DeploymentProfile.from_urls("metadata", {"reasoner": URL}, required_services=("reasoner",))
    def handler(request):
        return httpx.Response(200, json={"serviceId": "reasoner-id", "type" if request.url.path.endswith("capabilities") else "serviceType": "REASONER", "available": True, "operational": True})
    with connect(deployment, credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN}),
                 http_transport=httpx.MockTransport(handler)) as connection:
        connection.renew()
    wire = Wire()
    wire.operational = False
    with connect(profile(), credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN}),
                 http_transport=httpx.MockTransport(wire)) as connection:
        assert connection.explain_readiness() == "Connection dependencies not ready: runtime"
        connection.initialize_peers()
        with pytest.raises(TypeError):
            connection.selected["runtime"] = "relabel"


def test_peer_transient_failures_latest_snapshot_and_wait_deadline(monkeypatch):
    from types import SimpleNamespace
    import klab_client.connection as connection_module
    import klab_client._bootstrap as bootstrap_module
    import klab_client.transport as transport_module
    from klab_client import ReadinessError
    wire = Wire()
    clock = SimpleNamespace(now=0)
    status = 503
    def handler(request):
        if request.url.path.endswith("notifyUserScope"):
            if status == 504:
                clock.now = 10
            return httpx.Response(status, text="temporary") if status != 200 else httpx.Response(200, json=True)
        return wire(request)
    with connect(profile(), credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN}),
                 http_transport=httpx.MockTransport(handler)) as connection:
        assert connection._pending
        status = 200
        connection.wait_ready(timeout=1)
        assert not connection._pending
        wire.operational = False
        status = 504
        with monkeypatch.context() as patch:
            for module in (connection_module, bootstrap_module, transport_module):
                patch.setattr(module, "time", SimpleNamespace(monotonic=lambda: clock.now))
            with pytest.raises(TransportError):
                connection.refresh(_deadline=10)
        with pytest.raises(ReadinessError):
            _ = connection.client


def test_missing_capability_type_is_not_verified():
    from klab_client import ServiceSelectionError
    wire = Wire()
    def handler(request):
        result = wire(request)
        payload = result.json()
        payload.pop("type", None)
        return httpx.Response(200, json=payload)
    with pytest.raises(ServiceSelectionError):
        connect(profile(), credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN}),
                http_transport=httpx.MockTransport(handler))


def test_older_acknowledgement_cannot_discard_newer_pending_snapshot():
    wire = Wire()
    count = 0
    def handler(request):
        nonlocal count
        if request.url.path.endswith("notifyUserScope"):
            count += 1
            if count == 1:
                wire.operational = False
                connection.refresh()
                connection.initialize_peers()
                return httpx.Response(200, json=True)
            return httpx.Response(200, json=False if count == 2 else True)
        return wire(request)
    with connect(profile(), credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN}),
                 http_transport=httpx.MockTransport(handler), initialize=False) as connection:
        connection.initialize_peers()
        assert connection._pending[URL][2]["services"][0]["status"]["operational"] is False
        wire.operational = True
        connection.refresh()
        connection.initialize_peers()
        assert not connection._pending


def test_concurrent_expiry_reacquires_one_complete_grant(monkeypatch):
    from concurrent.futures import ThreadPoolExecutor
    import klab_client.connection as module
    calls = 0
    identity = ConnectionIdentity("scientist", "issuer")
    def source():
        nonlocal calls
        calls += 1
        return CredentialGrant(identity, profile().services, {URL: TOKEN if calls == 1 else "concurrent-new-private-token-VWX234"},
            datetime(2030 if calls == 1 else 2099, 1, 1, tzinfo=timezone.utc))
    wire = Wire()
    with connect(profile(), credentials=IssuedTokenProvider(identity, source), http_transport=httpx.MockTransport(wire)) as connection:
        client = connection.client
        class Future(datetime):
            @classmethod
            def now(cls, tz=None):
                return datetime(2050, 1, 1, tzinfo=timezone.utc)
        monkeypatch.setattr(module, "datetime", Future)
        before = len(wire.calls)
        with ThreadPoolExecutor(max_workers=4) as workers:
            results = list(workers.map(lambda number: client.transport.request("runtime", "GET", "/public/capabilities"), range(8)))
        assert len(results) == 8 and calls == 2
        assert {request.headers["Authorization"] for request in wire.calls[before:]} == {"concurrent-new-private-token-VWX234"}


def test_pending_snapshot_not_delivered_to_withdrawn_reference():
    identity = ConnectionIdentity("scientist", "issuer")
    current = CredentialGrant(identity, profile().services, {URL: TOKEN})
    wire = Wire()
    wire.ack = False
    with connect(profile(), credentials=IssuedTokenProvider(identity, lambda: current), http_transport=httpx.MockTransport(wire)) as connection:
        before = len(wire.calls)
        current = replace(current, references=())
        connection.renew()
        assert len(wire.calls) == before
        assert connection.selected["runtime"].available is None


def test_rotated_credentials_cover_binary_stream_and_error_redaction():
    wire = Wire()
    current = CredentialGrant(ConnectionIdentity("scientist"), profile().services, {URL: TOKEN}, sensitive_values=("",))
    def handler(request):
        if request.url.path.endswith("native-export"):
            wire.calls.append(request)
            return httpx.Response(200, stream=httpx.ByteStream(b"\x00\xffnative-export-bytes"))
        return wire(request)
    with connect(profile(), credentials=IssuedTokenProvider(current.identity, lambda: current),
                 http_transport=httpx.MockTransport(handler)) as connection:
        newer = "private-rotated-binary-credential-YZA567"
        current = replace(current, credentials={URL: newer})
        connection.renew()
        with connection.client.transport.stream("runtime", "/native-export", media_type="application/octet-stream") as response:
            assert b"".join(response.iter_bytes()) == b"\x00\xffnative-export-bytes"
        assert wire.calls[-1].headers["Authorization"] == newer
        text = connection.client.transport.redact(TOKEN + " " + newer)
        assert TOKEN not in text and newer not in text


def test_bootstrap_principal_cannot_be_credential_material():
    response = bootstrap()
    response["userData"]["identity"]["id"] = TOKEN
    with pytest.raises(ProtocolError, match="principal identity"):
        decode_bootstrap(response, profile(), TOKEN, ())


def test_endpoint_mutation_cannot_expand_credential_trust():
    from klab_client import ConnectionIdentityError, Endpoint
    forbidden = "https://forbidden.example/runtime"
    wire = Wire()
    with connect(profile(), credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN, forbidden: TOKEN}),
                 http_transport=httpx.MockTransport(wire)) as connection:
        connection.client.transport.endpoints["runtime"] = Endpoint(forbidden)
        before = len(wire.calls)
        with pytest.raises(ConnectionIdentityError, match="approved selected"):
            connection.client.create_session()
        assert len(wire.calls) == before
        with pytest.raises(ConnectionIdentityError):
            connection._credential("engine", URL)


def test_same_identity_renewal_updates_descriptive_fields_and_peer_snapshot():
    wire = Wire()
    current = CredentialGrant(ConnectionIdentity("scientist", "issuer", "old@example.invalid"), profile().services, {URL: TOKEN})
    with connect(profile(), credentials=IssuedTokenProvider(current.identity, lambda: current),
                 http_transport=httpx.MockTransport(wire)) as connection:
        import json
        current = replace(current, identity=ConnectionIdentity("scientist", "issuer", "new@example.invalid", raw={"extension": TOKEN}),
                          credentials={URL: "renewed-private-credential-BCD890"})
        connection.renew()
        assert connection.identity.email == "new@example.invalid"
        assert connection.identity.raw["extension"] == "[REDACTED]"
        notification = [request for request in wire.calls if request.url.path.endswith("notifyUserScope")][-1]
        assert json.loads(notification.content)["emailAddress"] == "new@example.invalid"


@pytest.mark.parametrize("status,error", [(401, AuthenticationError), (403, AuthorizationError)])
def test_single_required_probe_denial_retains_error_distinction(status, error):
    with pytest.raises(error):
        connect(profile(), credentials=IssuedTokenProvider(ConnectionIdentity("scientist"), {URL: TOKEN}),
                http_transport=httpx.MockTransport(lambda request: httpx.Response(status, text=TOKEN)))


def test_empty_catalog_has_actionable_selection_diagnostic():
    from klab_client import ServiceSelectionError
    current = CredentialGrant(ConnectionIdentity("scientist"), (), {URL: TOKEN})
    with pytest.raises(ServiceSelectionError, match="no advertised references"):
        connect(profile(), credentials=IssuedTokenProvider(current.identity, lambda: current))
