"""Real loopback Hub HTTP prerequisite, NOT broker/managed Runtime acceptance."""
import json
import os
from pathlib import Path
import subprocess
import sys
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
        with urlopen(url.removesuffix("/api/v2/users/services/") + "/health", timeout=3) as health:
            assert health.status == 200
            assert json.load(health) == {"status": "TEST_ONLY"}
        # Exercise the existing runner's real HTTP readiness logic unchanged;
        # this authority is owned by this test thread, not a Java process.
        # Run outside pytest's deliberately unchanged offline httpx guard, as
        # with other local process integration tests. Only this owned server
        # address is supplied, no live Java/broker or external endpoint.
        code = ("import sys;from types import SimpleNamespace;"
            "sys.path.insert(0,sys.argv[1]);from run_reliability_acceptance import ready;"
            "ready(int(sys.argv[2]),'/health',SimpleNamespace(poll=lambda:None))")
        subprocess.run([sys.executable, "-c", code, str(Path(fixture_hub.__file__).parent),
            str(server.server_address[1])], check=True, timeout=10, capture_output=True,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
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
