"""Local-only service process; never probes, proxies or modifies a real deployment."""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import threading
from pathlib import Path
from urllib.parse import urlsplit

from .state import State, SCENARIOS, SERVICES


def make_server(port=0, *, scenario="elevation", seed=1, test_mode=False, mode="simulated"):
    if type(port) is not int or not 0 <= port <= 65535 or port in (8091, 8092, 8093, 8094, 8382, 7687):
        raise ValueError("Simulator must use a non-production local port")
    if mode not in ("simulated", "replay"):
        raise ValueError("A simulator server cannot proxy real services or perform hybrid fallback")
    if mode == "replay":
        from .replay import ReplayState
        state = ReplayState(seed, test_mode)
    else:
        state = State(scenario, seed, test_mode)
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass  # Trace never records request bodies or Authorization headers.

        def handle_route(self):
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 <= length <= 1024 * 1024 or self.headers.get("Transfer-Encoding"):
                    self.send_error(413, "Request framing exceeds simulator budget")
                    return
                body = self.rfile.read(length)
                status, payload, media = state.request(self.command, self.path, self.headers, body)
                encoded = (payload if isinstance(payload, bytes) else payload.encode() if media == "text/plain" else
                           json.dumps(payload, allow_nan=False, separators=(",", ":")).encode())
            except (ValueError, TypeError, KeyError, UnicodeError, IndexError, AttributeError):
                status, media, encoded = 400, "application/json", b'{"error":"Simulator contract mismatch"}'
            self.send_response(status)
            self.send_header("Content-Type", media)
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            if state.scenario == "export-interrupted" and status == 200 and isinstance(payload, bytes):
                self.wfile.write(encoded[:len(encoded) // 2])
                self.close_connection = True  # Deterministic short stream, not a timing race.
            else:
                self.wfile.write(encoded)

        do_GET = do_POST = do_PUT = do_DELETE = handle_route

    class BoundedServer(ThreadingHTTPServer):
        daemon_threads = True

        def __init__(self, *args):
            self.slots = threading.BoundedSemaphore(16)
            super().__init__(*args)

        def get_request(self):
            connection, address = super().get_request()
            connection.settimeout(3)
            return connection, address

        def process_request(self, request, address):
            if not self.slots.acquire(blocking=False):
                self.shutdown_request(request)
                return
            try:
                super().process_request(request, address)
            except BaseException:
                self.slots.release()
                raise

        def process_request_thread(self, request, address):
            try:
                super().process_request_thread(request, address)
            finally:
                self.slots.release()

    server = BoundedServer(("127.0.0.1", port), Handler)
    server.daemon_threads = True
    server.state = state
    return server


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=38000)
    parser.add_argument("--scenario", choices=SCENARIOS, default="elevation")
    parser.add_argument("--seed", type=int, default=1)
    parser.add_argument("--test-mode", action="store_true")
    parser.add_argument("--mode", choices=("simulated", "replay", "real"), default="simulated")
    parser.add_argument("--real-endpoints", type=Path, help="Explicit four-service JSON configuration; never a proxy")
    args = parser.parse_args()
    if args.mode == "real":
        if args.real_endpoints is None or args.real_endpoints.stat().st_size > 16384:
            parser.error("real mode requires a bounded explicit endpoint configuration")
        endpoints = json.loads(args.real_endpoints.read_text())
        if not isinstance(endpoints, dict) or set(endpoints) != set(SERVICES):
            parser.error("real mode requires all four named service endpoints")
        for url in endpoints.values():
            parsed = urlsplit(url)
            if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
                parser.error("real endpoints must be explicit credential-free service URLs")
        print(json.dumps({"backend": "REAL", "endpoints": endpoints, "proxy": False,
                          "instruction": "Configure the ordinary SDK directly; failures are not substituted"}), flush=True)
        return
    server = make_server(args.port, scenario=args.scenario, seed=args.seed, test_mode=args.test_mode, mode=args.mode)
    print(json.dumps({"backend": server.state.backend, "scenario": server.state.scenario, "seed": args.seed,
        "endpoints": {name: f"http://127.0.0.1:{server.server_port}/{name}" for name in SERVICES},
        "service_ids": server.state.service_ids}), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
