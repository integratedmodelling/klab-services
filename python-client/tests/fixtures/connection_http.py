"""Isolated loopback contract/forbidden-origin probes; never genuine Hub acceptance."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import json
import sys
import threading

from klab_client import DeploymentProfile, HubCertificateProvider, connect
from klab_client.errors import ServerError, SubmissionOutcomeUnknown


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main(directory):
    secret = "controlled-private-engine-secret-MNO345"
    issued = "controlled-private-token-PQR678"
    hits, submits = [], []
    redirect = False
    class Forbidden(BaseHTTPRequestHandler):
        def do_GET(self):
            hits.append(self.path)
            self.send_error(500)
        do_POST = do_GET
        def log_message(self, *args):
            pass
    forbidden = ThreadingHTTPServer(("127.0.0.1", 0), Forbidden)
    class Authority(BaseHTTPRequestHandler):
        def response(self, value, status=200):
            data = json.dumps(value).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        def do_GET(self):
            require(self.headers.get("Authorization") == issued, "Missing origin-bound service credential")
            payload = {"serviceId": "http-runtime-id", "type" if self.path.endswith("capabilities") else "serviceType": "RUNTIME"}
            if self.path.endswith("status"):
                payload.update(available=True, operational=True)
            self.response(payload)
        def do_POST(self):
            payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            if self.path == "/api/v2/engines/auth-cert":
                require("Authorization" not in self.headers, "Bootstrap received a service credential")
                require(payload["key"] == payload["certificate"] == secret, "Incorrect certificate exchange fields")
                if redirect:
                    self.send_response(302)
                    self.send_header("Location", bad_url)
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
                self.response({"hub": {"id": "loopback-contract-hub"}, "userData": {
                    "identity": {"id": "scientist"}, "expiry": "2099-01-01T00:00:00", "token": issued, "groups": []},
                    "services": [{"identityType": "RUNTIME", "id": "hub-runtime-reference", "urls": [url, bad_url]}]})
            elif self.path.endswith("notifyUserScope"):
                require(self.headers["Authorization"] == issued, "Peer initialization missing credential")
                self.response(True)
            else:
                submits.append(self.path)
                self.response({"failure": secret + issued}, 503)
        def log_message(self, *args):
            pass
    authority = ThreadingHTTPServer(("127.0.0.1", 0), Authority)
    hub = "http://127.0.0.1:" + str(authority.server_port)
    url = hub + "/runtime"
    bad_url = "http://127.0.0.1:" + str(forbidden.server_port) + "/runtime"
    threads = [threading.Thread(target=server.serve_forever) for server in (authority, forbidden)]
    for thread in threads:
        thread.start()
    try:
        path = Path(directory) / "contract.cert"
        path.write_text("\n".join(["klab.certificate.type=ENGINE", "klab.certificate.level=TEST",
            "klab.username=scientist", "klab.partner.hub=" + hub,
            "klab.signature=" + secret, "klab.certificate=" + secret]))
        deployment = DeploymentProfile("loopback", trusted_urls=(url,), hub_url=hub,
            required_services=("runtime",), test_loopback=True)
        provider = HubCertificateProvider(path)
        with connect(deployment, credentials=provider) as connection:
            require(len(connection.catalog.entries) == 2, "Advertised catalog not retained")
            require(connection.catalog.entries[1].trusted is False, "Forbidden endpoint gained trust")
            try:
                connection.client.create_session()
            except SubmissionOutcomeUnknown as error:
                require(secret not in str(error) and issued not in str(error), "Secret leaked in failure text")
            else:
                raise AssertionError("Mutation failure was hidden")
        require(len(submits) == 1 and not hits, "Mutation replay or untrusted forwarding")
        redirect = True
        try:
            provider.acquire(deployment)
        except ServerError:
            pass
        else:
            raise AssertionError("Credential-bearing redirect was followed/accepted")
        require(not hits, "Forbidden origin received a redirected credential")
        print(json.dumps({"passed": True, "forbidden_requests": len(hits), "mutation_submissions": len(submits)}))
    finally:
        for server in (authority, forbidden):
            server.shutdown()
            server.server_close()
        for thread in threads:
            thread.join(timeout=5)


if __name__ == "__main__":
    main(sys.argv[1])
