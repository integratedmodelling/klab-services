"""Loopback-only TEST authority for the existing service certificate/JWT contract.

No production credentials are accepted or issued. Certificates and RSA material
are generated in an explicit disposable directory; service requests must match
their random enrollment secret. Scientific APIs still validate JWT signatures.
"""
import argparse
import base64
from datetime import datetime, timezone, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import secrets
import time

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

ISSUER = "python-local-test-hub"
SERVICES = {"reasoner": 8091, "resources": 8092, "resolver": 8093, "runtime": 8094}


def b64(value):
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode()


def token(key, user, roles=("ROLE_USER",)):
    now = int(time.time())
    header = b64(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    claims = b64(json.dumps({"iss": ISSUER, "sub": user, "aud": "engine", "iat": now,
                             "exp": now + 3600, "perms": [], "roles": list(roles)}).encode())
    message = (header + "." + claims).encode()
    return message.decode() + "." + b64(key.sign(message, padding.PKCS1v15(), hashes.SHA256()))


def prepare(state, worldview):
    if (state / "fixture-authority.json").exists():
        raise RuntimeError("Fixture already prepared; use a new state directory rather than overwrite credentials")
    state.mkdir(parents=True, exist_ok=True)
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    secret = secrets.token_urlsafe(32)
    (state / "fixture-authority.pem").write_bytes(key.private_bytes(serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    (state / "fixture-authority.json").write_text(json.dumps({"enrollment_secret": secret}))
    for service, port in SERVICES.items():
        directory = state / "home/.klab/services" / service
        directory.mkdir(parents=True, exist_ok=True)
        (directory / "service.cert").write_text("\n".join([
            f"klab.certificate.type={service.upper()}", "klab.certificate.level=TEST",
            f"klab.nodename=python-test-{service}", "klab.partner.hub=http://127.0.0.1:18090",
            "klab.partner.email=test@example.invalid", f"klab.url=http://127.0.0.1:{port}/{service}",
            f"klab.certificate={secret}", f"klab.signature={secret}", ""]))
    fixture = state / "assets/python.fixture"
    (fixture / "META-INF").mkdir(parents=True)
    (fixture / "src").mkdir()
    (fixture / "META-INF/manifest.json").write_text(json.dumps({
        "description": "Deterministic local verification fixture, not measured terrain",
        "version": {"major": 1, "minor": 0, "build": 0}, "worldview": "imod",
        "prerequisiteProjects": [], "privileges": {"public": True}, "metadata": {}}))
    (fixture / "src/python.fixture.kim").write_text(
        'namespace python.fixture;\n\n// Known constant field through ordinary model resolution.\n'
        'model 123.25 as geography:Elevation in m;\n')
    projects = {}
    for name, directory, is_worldview in (("imod", worldview.resolve(), True), ("python.fixture", fixture.resolve(), False)):
        projects[name] = {"sourceUrl": directory.as_uri(), "served": True, "worldview": is_worldview,
                          "privileges": {"public": True}, "locallyManaged": True, "authoritative": True,
                          "syncIntervalMinutes": 0, "localPath": str(directory),
                          "workspaceName": "test-fixtures", "storageType": "FILE"}
    import uuid
    config = {"serviceId": str(uuid.uuid4()), "servicePath": "resources",
              "workspaces": {"test-fixtures": list(projects)}, "projectConfiguration": projects}
    # JSON is valid YAML and uses the existing ResourcesConfiguration contract.
    (state / "home/.klab/services/resources/resources.yaml").write_text(json.dumps(config, indent=2))
    issue(state, key)
    print("Prepared isolated TEST certificates, signed user credentials and deterministic model; secrets not printed")


def issue(state, key):
    config = {"fixture": "constant-elevation-123.25", "username": "python-scientist",
              "token": token(key, "python-scientist"), "other_token": token(key, "other-scientist"),
              "urls": {name: f"http://127.0.0.1:{port}/{name}" for name, port in SERVICES.items()}}
    (state / "scientist.json").write_text(json.dumps(config))


def serve(state):
    key = serialization.load_pem_private_key((state / "fixture-authority.pem").read_bytes(), password=None)
    secret = json.loads((state / "fixture-authority.json").read_text())["enrollment_secret"]
    public = base64.b64encode(key.public_key().public_bytes(serialization.Encoding.DER,
                                serialization.PublicFormat.SubjectPublicKeyInfo)).decode()
    issue(state, key)
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            if self.path != "/api/v2/nodes/auth-cert":
                self.send_error(404)
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length < 16384:
                    raise ValueError()
                body = json.loads(self.rfile.read(length))
                if not secrets.compare_digest(body.get("certificate", ""), secret):
                    self.send_error(403)
                    return
                references = [{"id": f"python-test-{name}", "identityType": name.upper(),
                               "urls": [f"http://127.0.0.1:{port}/{name}"], "online": True,
                               "primary": True, "permissions": ["QUERY"],
                               "partner": {"id": "python-test-institution"}}
                              for name, port in SERVICES.items()]
                response = {"authenticatingHub": ISSUER, "publicKey": public, "groups": [], "services": references,
                    "userData": {"identity": {"id": "python-test-institution", "email": "test@example.invalid"},
                                 "token": token(key, "python-test-service", ("ROLE_USER", "ROLE_ENGINE")),
                                 "expiry": (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat(), "groups": []}}
                data = json.dumps(response).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
            except (ValueError, TypeError):
                self.send_error(400)
        def log_message(self, *args):
            pass
    server = ThreadingHTTPServer(("127.0.0.1", 18090), Handler)
    print("TEST authority listening on 127.0.0.1:18090; not a production login service", flush=True)
    try:
        server.serve_forever()
    finally:
        server.server_close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("prepare", "serve", "issue"))
    parser.add_argument("--state-dir", type=Path, required=True)
    parser.add_argument("--worldview", type=Path)
    args = parser.parse_args()
    if args.action == "prepare":
        if not args.worldview or not (args.worldview / "META-INF/manifest.json").is_file():
            parser.error("prepare requires an existing --worldview project")
        prepare(args.state_dir.resolve(), args.worldview)
    elif args.action == "issue":
        key = serialization.load_pem_private_key((args.state_dir / "fixture-authority.pem").read_bytes(), password=None)
        issue(args.state_dir, key)
        print("Refreshed short-lived TEST credentials; secrets not printed")
    else:
        serve(args.state_dir.resolve())
