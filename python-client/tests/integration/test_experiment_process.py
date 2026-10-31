"""Real loopback HTTP + fresh Python processes. Controlled wire fixture, not Java/scientific acceptance."""
from copy import deepcopy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import subprocess
import sys
from threading import Lock, Thread

import pytest


@pytest.mark.integration
def test_independent_experiments_restore_original_results_in_fresh_process(tmp_path):
    payload = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    payload["observable"]["contextualization"] = "MEASURE"
    contexts, observations, jobs, submissions = {}, {}, {}, []
    lock = Lock()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def send(self, value, status=200, *, text=False):
            body = (str(value) if text else json.dumps(value)).encode()
            self.send_response(status)
            self.send_header("Content-Type", "text/plain" if text else "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self):
            body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
            assert self.headers["Authorization"] == "local-fixture-not-a-real-credential"
            if self.path.endswith("/resolve/observable"):
                self.send(payload["observable"])
                return
            data = json.loads(body)
            scope = self.headers.get("klab-scope")
            if self.path == "/createSession":
                self.send(data["configuration"]["id"], text=True)
            elif self.path == "/createContext":
                configuration = {"id": scope + ".c", "serviceId": "home"}
                contexts[configuration["id"]] = configuration
                self.send(configuration)
            elif self.path == "/api/v1/submit":
                with lock:
                    id = len(submissions) + 1
                    submissions.append((scope, id))
                    result = deepcopy(payload)
                    result.update(id=id + 100, urn=scope + "." + str(id + 100))
                    observations[(scope, id + 100)] = result
                    jobs[(scope, id)] = result
                self.send(id)
            elif self.path == "/api/v1/connect":
                self.send(contexts[data["configuration"]["id"]])
            elif self.path == "/api/v1/query":
                value = observations.get((scope, data["id"]))
                self.send([value] if value is not None else [])
            elif self.path == "/api/v1/observation/value":
                assert (scope, data["sourceId"]) in observations
                self.send("0", text=True)
            else:
                self.send({}, 404)

        def do_GET(self):
            assert self.headers["Authorization"] == "local-fixture-not-a-real-credential"
            scope = self.headers.get("klab-scope")
            if self.path == "/public/capabilities":
                self.send({"serviceId": "home"})
            elif self.path.startswith("/jobs/status/"):
                assert (scope, int(self.path.rsplit("/", 1)[-1])) in jobs
                self.send({"status": "FINISHED"})
            elif self.path.startswith("/jobs/retrieve/"):
                self.send(jobs[(scope, int(self.path.rsplit("/", 1)[-1]))])
            else:
                self.send({}, 404)

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    worker = tmp_path / "experiment_worker.py"
    worker.write_text('''import json
from pathlib import Path
import sys
from klab_client import Client, Endpoint, Experiment, ExperimentRunner, ExperimentBatch, ObservationRequest, OwnedContextPolicy

mode, endpoint, path = sys.argv[1:]
def factory():
    return Client(endpoint, "local-fixture-not-a-real-credential", runtime_service_id="home", agent_name="scientist",
                  reasoner=Endpoint(endpoint, "local-fixture-not-a-real-credential"))
if mode == "create":
    def process(experiment, observation):
        return str(observation.fetch_data([0], curve="D1_LINEAR").values[0])
    with ExperimentRunner(factory, workers=4, processor=process) as runner:
        batch = runner.run([Experiment("case" + str(i), ObservationRequest("geography:Elevation in m"), OwnedContextPolicy()) for i in range(4)])
    if batch.counts != {"succeeded": 4}:
        raise RuntimeError(batch.summary())
    Path(path).write_text(json.dumps({"manifest": json.loads(batch.dumps()), "values": [item.processed for item in batch.outcomes]}))
else:
    source = json.loads(Path(path).read_text())
    batch = ExperimentBatch.loads(json.dumps(source["manifest"]))
    values, identities = [], []
    for outcome in batch.outcomes:
        with factory() as client:
            observation = client.restore_handle(outcome.result_handle)
            if observation._context.owned:
                raise RuntimeError("Restoration must not acquire disposal ownership")
            identities.append(observation.to_handle().to_dict())
            values.append(str(observation.fetch_data([0], curve="D1_LINEAR").values[0]))
    if values != source["values"]:
        raise RuntimeError("Restored values differ")
    print(json.dumps({"identities": identities, "values": values}))
''', encoding="utf-8")
    manifest = tmp_path / "batch.json"
    endpoint = "http://127.0.0.1:" + str(server.server_port)
    try:
        first = subprocess.run([sys.executable, "-I", str(worker), "create", endpoint, str(manifest)],
            capture_output=True, text=True, timeout=30, env=dict(os.environ))
        assert first.returncode == 0, first.stdout + first.stderr
        assert len(submissions) == 4 and len(contexts) == 4
        second = subprocess.run([sys.executable, "-I", str(worker), "restore", endpoint, str(manifest)],
            capture_output=True, text=True, timeout=30, env=dict(os.environ))
        assert second.returncode == 0, second.stdout + second.stderr
        source = json.loads(manifest.read_text())
        restored = json.loads(second.stdout)
        assert restored["identities"] == [item["result_handles"][-1] for item in source["manifest"]["outcomes"]]
        assert restored["values"] == ["0"] * 4 and len(submissions) == 4
        assert "local-fixture-not-a-real-credential" not in manifest.read_text() + first.stdout + second.stdout
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)
        assert not thread.is_alive()
