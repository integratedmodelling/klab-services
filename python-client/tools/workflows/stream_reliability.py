"""Actual-socket synthetic export acceptance, not a Java/provider capacity claim."""
import argparse
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import platform
import socket
import sys
from threading import Event, Thread
import time
import tracemalloc

import httpx
import psutil

from klab_client import Client, Context
from klab_client.dto import observation_from_wire
from klab_client.errors import AuthorizationError, ProtocolError, TransportError

BLOCK = bytes(range(256)) * 256
MIME = "application/octet-stream"
FIXTURE = Path(__file__).resolve().parents[2] / "tests/fixtures/elevation.json"


def case(directory, mode, size):
    release, sent, done = Event(), Event(), Event()
    requests = []
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            requests.append(self.path)
            if self.headers.get("Authorization") != "loopback-test-token":
                self.send_error(403); return
            if self.path == "/runtime/public/capabilities":
                data = json.dumps({"serviceId": "transport-fixture", "exportSchemata": {"fixture": [
                    {"schemaId": "fixture.bytes", "knowledgeClass": "OBSERVATION", "type": "STREAM",
                     "mediaTypes": [MIME], "extensions": ["bin"]}]}}).encode()
                self.send_response(200); self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)
                return
            if (self.path != "/runtime/export/OBSERVATION/s.c.42"
                    or self.headers.get("klab-scope") != "s.c" or self.headers.get("klab-service") != "transport-fixture"):
                self.send_error(400); return
            try:
                self.send_response(403 if mode == "denied" else 200)
                self.send_header("Content-Type", "text/html" if mode == "invalid" else MIME)
                self.send_header("Content-Length", str(size)); self.end_headers()
                self.wfile.write(BLOCK); self.wfile.flush(); sent.set()
                if mode == "disconnect":
                    self.connection.shutdown(socket.SHUT_RDWR); return
                if mode in ("timeout", "cancel"):
                    release.wait(5)
                for _ in range(size // len(BLOCK) - 1):
                    self.wfile.write(BLOCK)
            except (BrokenPipeError, ConnectionResetError, OSError):
                pass  # The fixture expects clients to close rejected/interrupted streams.
            finally:
                done.set()

        def log_message(self, *args):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = Thread(target=server.serve_forever, daemon=True); thread.start()
    url = f"http://127.0.0.1:{server.server_port}/runtime"
    destination = directory / (mode + ".bin")
    destination.write_bytes(b"previous-valid-result")
    stop, peak_rss = Event(), [psutil.Process().memory_info().rss]
    baseline = peak_rss[0]
    def sample():
        while not stop.wait(.002):
            peak_rss[0] = max(peak_rss[0], psutil.Process().memory_info().rss)
    sampler = Thread(target=sample, daemon=True)
    canceller = None
    try:
        with Client(url, "loopback-test-token", runtime_service_id="transport-fixture", timeout=.5 if mode == "timeout" else 10) as client:
            raw = json.loads(FIXTURE.read_text())
            observation = observation_from_wire(raw, Context(client, {"id": "s.c", "serviceId": "transport-fixture"}))
            client.runtime.capabilities()  # Warm metadata/HTTP machinery before measuring incremental transfer.
            baseline = psutil.Process().memory_info().rss; peak_rss[0] = baseline
            if mode == "cancel":
                def cancel():
                    import _thread
                    if not sent.wait(5):
                        return
                    deadline = time.monotonic() + 5
                    while time.monotonic() < deadline:
                        if any(p.stat().st_size for p in directory.glob(".klab-export-*")):
                            _thread.interrupt_main(); release.set(); return
                        time.sleep(.002)
                    release.set()
                canceller = Thread(target=cancel, daemon=True); canceller.start()
            sampler.start(); tracemalloc.start(); started = time.perf_counter()
            outcome = "SUCCESS"
            try:
                if mode == "buffered-control":
                    with httpx.Client(trust_env=False) as control:
                        response = control.get(url + "/export/OBSERVATION/s.c.42",
                            headers={"Authorization": "loopback-test-token", "klab-scope": "s.c", "klab-service": "transport-fixture"})
                        response.raise_for_status(); body = response.content
                        if len(body) != size:
                            raise AssertionError("Buffered control length differs")
                else:
                    result = observation.export_to_file(destination, media_type=MIME, overwrite=True,
                        max_bytes=len(BLOCK) if mode == "budget" else size)
                    if result.bytes_written != size:
                        raise AssertionError("SDK export length differs")
            except (TransportError, ProtocolError, AuthorizationError, KeyboardInterrupt) as error:
                expected = {"disconnect": TransportError, "timeout": TransportError, "cancel": KeyboardInterrupt,
                            "invalid": ProtocolError, "budget": ProtocolError, "denied": AuthorizationError}.get(mode)
                if expected is None or not isinstance(error, expected):
                    raise
                if "loopback-test-token" in str(error):
                    raise AssertionError("Diagnostic exposed credential") from None
                outcome = type(error).__name__
            finally:
                elapsed = time.perf_counter() - started
                peak_python = tracemalloc.get_traced_memory()[1]; tracemalloc.stop()
                peak_rss[0] = max(peak_rss[0], psutil.Process().memory_info().rss)
                release.set(); stop.set(); sampler.join(timeout=5)
            if mode not in ("success", "buffered-control") and outcome == "SUCCESS":
                raise AssertionError("Fault scenario falsely completed")
            if mode == "success":
                expected_digest = hashlib.sha256()
                for _ in range(size // len(BLOCK)):
                    expected_digest.update(BLOCK)
                with destination.open("rb") as stream:
                    if hashlib.file_digest(stream, "sha256").hexdigest() != expected_digest.hexdigest():
                        raise AssertionError("Published bytes differ")
                if peak_python >= 4 * 1024**2:
                    raise AssertionError("Synthetic streamed transfer exceeded its 4MiB Python allocation budget")
            elif destination.read_bytes() != b"previous-valid-result":
                raise AssertionError("Interrupted/control operation overwrote original data")
            if list(directory.glob(".klab-export-*")):
                raise AssertionError("Temporary export artifact remains")
            if requests.count("/runtime/export/OBSERVATION/s.c.42") != 1:
                raise AssertionError("Export was retried or not attempted exactly once")
            if not done.wait(5):
                raise AssertionError("Owned HTTP response handler did not finish")
            return {"mode": mode, "outcome": outcome, "declared_body_bytes": size,
                    "published_bytes": size if mode == "success" else 0, "peak_python_bytes": peak_python,
                    "rss_before": baseline, "sampled_peak_rss": peak_rss[0], "rss_growth": peak_rss[0] - baseline,
                    "seconds": elapsed, "original_preserved": mode != "success", "temporary_files": 0,
                    "export_requests": 1, "response_body_handler_finished": True, "scientific_or_java_acceptance": False}
    finally:
        release.set(); stop.set()
        if sampler.is_alive():
            sampler.join(timeout=5)
        if canceller is not None:
            canceller.join(timeout=5)
        server.shutdown(); server.server_close(); thread.join(timeout=5)
        if thread.is_alive() or sampler.is_alive() or canceller is not None and canceller.is_alive():
            raise RuntimeError("Owned transport test thread did not close")


def run(directory, *, size=64 * 1024**2):
    if type(size) is not int or not 2 * len(BLOCK) <= size <= 64 * 1024**2 or size % len(BLOCK):
        raise ValueError("Use an explicit multiple of64KiB in128KiB..64MiB")
    directory.mkdir(exist_ok=False)
    records = [case(directory, mode, size) for mode in
        ("success", "buffered-control", "disconnect", "timeout", "cancel", "invalid", "budget", "denied")]
    if records[1]["peak_python_bytes"] < size:
        raise AssertionError("Buffered positive control did not measure a response-sized allocation")
    report = {"passed": True, "workload": "deterministic synthetic binary HTTP body, not a scientific format/provider result",
        "python": sys.version, "platform": platform.platform(), "records": records,
        "memory_method": "Incremental after imports/client initialization and metadata warmup; tracemalloc peak plus process RSS sampled every2ms; loopback producer retains one64KiB block. Not total RSS/SLA/soak or native-Java capacity."}
    (directory / "result.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-dir", type=Path, required=True)
    parser.add_argument("--bytes", type=int, default=64 * 1024**2)
    args = parser.parse_args()
    print(json.dumps(run(args.work_dir, size=args.bytes), indent=2))
