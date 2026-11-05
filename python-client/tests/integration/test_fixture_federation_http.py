"""Real loopback Hub HTTP prerequisite, NOT broker/managed Runtime acceptance."""
import json
import threading
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

import fixture_hub


@pytest.mark.parametrize("enabled", [False, True])
def test_owned_group_lookup_uses_java_authentication_header_and_cleans_server(tmp_path, monkeypatch, enabled):
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    (tmp_path / "fixture-authority.pem").write_bytes(key.private_bytes(serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    (tmp_path / "fixture-authority.json").write_text(json.dumps({"enrollment_secret": "private-enrollment-test"}))
    monkeypatch.setattr(fixture_hub, "issue", lambda *args: None)
    broker = "amqp://owned:private-broker-test@127.0.0.1:38123/owned-test"
    if enabled:
        (tmp_path / "owned-federation.json").write_text(json.dumps({"allow_owned_federation": True,
            "name": "python-test-agent", "broker_uri": broker, "users": ["python-scientist"]}))
    server = fixture_hub.authority_server(tmp_path, port=0)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    url = "http://127.0.0.1:" + str(server.server_address[1]) + "/api/v2/users/services/"
    service = fixture_hub.token(key, "python-test-service", ("ROLE_USER", "ROLE_ENGINE"))
    def get(name, headers):
        return urlopen(Request(url + name, headers=headers), timeout=3)
    try:
        if enabled:
            with get("python-scientist", {"Authentication": service}) as response:
                assert response.headers["Cache-Control"] == "no-store"
                groups = json.load(response)
                assert groups[0]["customProperties"][1]["value"] == broker
        else:
            with pytest.raises(HTTPError) as error:
                get("python-scientist", {"Authentication": service})
            assert error.value.code == 404
        for headers in ({}, {"Authorization": "Bearer " + service},
                {"Authentication": fixture_hub.token(key, "python-scientist")}, {"Authentication": "invalid"}):
            with pytest.raises(HTTPError) as error:
                get("python-scientist", headers)
            assert error.value.code == (403 if enabled else 404)
            assert broker.encode() not in error.value.read()
        with pytest.raises(HTTPError) as error:
            get("other-scientist", {"Authentication": service})
        assert error.value.code == 404
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=3)
    assert not thread.is_alive()
