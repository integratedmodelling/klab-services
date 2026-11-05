import json

import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

import fixture_hub
from fixture_federation import load_groups, service_authorized


def config():
    return {"allow_owned_federation": True, "name": "python-test-agent",
        "broker_uri": "amqp://owned:private-test-password@127.0.0.1:38123/owned-test",
        "users": ["python-scientist"]}


def test_explicit_groups_preserve_exact_existing_java_custom_property_contract(tmp_path):
    assert load_groups(tmp_path) is None
    (tmp_path / "owned-federation.json").write_text(json.dumps(config()))
    groups = load_groups(tmp_path)
    assert set(groups) == {"python-scientist"}
    assert groups["python-scientist"] == [{"name": "python-test-agent", "customProperties": [
        {"key": "federation.id", "value": "true"},
        {"key": "federation.broker.url", "value": config()["broker_uri"]}]}]


@pytest.mark.parametrize("change", [
    {"allow_owned_federation": 1}, {"allow_owned_federation": False}, {"name": "production"},
    {"users": []}, {"users": ["unrelated-user"]}, {"users": ["python-scientist"] * 2},
    {"users": "python-scientist"}, {"users": ["python-user-" + str(i) for i in range(9)]},
    {"broker_uri": "amqp://user:password@external.invalid:38123/host"},
    {"broker_uri": "amqp://127.0.0.1:38123/host"},
    {"broker_uri": "amqp://user:password@127.0.0.1:567/host"},
    {"broker_uri": "amqp://user:password@127.0.0.1:38123/"},
    {"broker_uri": "amqp://user:password@127.0.0.1:38123/host?extra=value"},
    {"broker_uri": "amqp://user:password@127.0.0.1:38123/host#fragment"},
    {"broker_uri": "amqp://user:password@127.0.0.1:bad/host"}, {"extra": True}])
def test_unsafe_or_implicit_federation_never_allocates_broker_or_server(tmp_path, change):
    (tmp_path / "owned-federation.json").write_text(json.dumps(config() | change))
    with pytest.raises(ValueError):
        load_groups(tmp_path)


def test_oversized_private_manifest_refused(tmp_path):
    (tmp_path / "owned-federation.json").write_text(" " * 8193)
    with pytest.raises(ValueError, match="byte budget"):
        load_groups(tmp_path)


@pytest.fixture(scope="module")
def key():
    return rsa.generate_private_key(public_exponent=65537, key_size=2048)


@pytest.mark.parametrize("case", ["service", "scientist", "wrongkey", "expired", "issuer", "bearer", "malformed", "missing"])
def test_only_actual_signed_fixture_service_credentials_authorize_group_lookup(key, case):
    token = fixture_hub.token(key, "python-test-service", ("ROLE_USER", "ROLE_ENGINE"))
    verify_key, issuer = key.public_key(), fixture_hub.ISSUER
    if case == "scientist":
        token = fixture_hub.token(key, "python-scientist")
    if case == "wrongkey":
        verify_key = rsa.generate_private_key(public_exponent=65537, key_size=2048).public_key()
    if case == "expired":
        token = fixture_hub.token(key, "python-test-service", ("ROLE_USER", "ROLE_ENGINE"), lifetime=-1)
    if case == "issuer":
        issuer = "other"
    if case == "bearer":
        token = "Bearer " + token
    if case == "malformed":
        token = "not.a.valid.token"
    if case == "missing":
        token = None
    assert service_authorized(token, verify_key, issuer) is (case == "service")
