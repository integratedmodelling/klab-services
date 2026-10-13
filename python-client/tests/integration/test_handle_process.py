"""Local HTTP and real subprocess recovery; does not require a k.LAB deployment."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import subprocess
import sys
import threading

import pytest

pytestmark = pytest.mark.integration


def test_actual_fresh_subprocess_round_trip_and_authorized_principal_change(tmp_path):
    wire = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    wire["urn"] = "s.c.42"
    wire["observable"]["contextualization"] = "MEASURE"
    calls = []
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
    script = Path(__file__).parents[1] / "fixtures/handle_process.py"
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
