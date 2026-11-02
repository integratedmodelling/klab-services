"""Real loopback HTTP-to-pandas representation, not a scientific provider test."""
import asyncio
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
from threading import Thread
import sys

from klab_client import AsyncClient, AsyncContext, Client, Context
from klab_client.dto import observation_from_wire


def run(mode):
    assert mode in {"sync", "async"}
    calls = []
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            assert self.path == "/runtime/api/v1/observation/value"
            assert self.headers["Authorization"] == "test-token"
            assert self.headers["klab-scope"] == "s.c"
            assert self.headers.get("server-key") is None
            request = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            calls.append(request)
            body = ("null" if request["offset"] == 1 else "0").encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    url = f"http://127.0.0.1:{server.server_port}/runtime"
    raw = json.loads((Path(__file__).parents[1] / "fixtures/elevation.json").read_text())
    try:
        def check(data):
            frame = data.to_dataframe()
            assert frame["offset"].tolist() == [0, 1, 0]
            assert frame["value"].tolist() == [0, None, 0]
            assert frame["wire_missing"].tolist() == [False, True, False]
            assert frame.attrs["klab"]["origin"] == {"runtime_url": url, "runtime_id": "test-runtime",
                                                     "context_id": "s.c", "scope": "s.c"}
            assert frame.attrs["klab"]["units"] == "m"
        async def asynchronous():
            async with AsyncClient(url, "test-token", runtime_service_id="test-runtime") as client:
                observation = observation_from_wire(raw, AsyncContext(client, {"id": "s.c"}))
                check(await observation.fetch_data([0, 1, 0], curve="D2_YX"))
        if mode == "async":
            asyncio.run(asynchronous())
        else:
            with Client(url, "test-token", runtime_service_id="test-runtime") as client:
                observation = observation_from_wire(raw, Context(client, {"id": "s.c"}))
                check(observation.fetch_data([0, 1, 0], curve="D2_YX"))
        assert [call["offset"] for call in calls] == [0, 1, 0]
        assert all(call["sourceId"] == 42 and call["curve"] == "D2_YX" for call in calls)
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)
        assert not thread.is_alive()


if __name__ == "__main__":
    run(sys.argv[1])
    print(json.dumps({"passed": True, "mode": sys.argv[1], "read_requests": 3}))
