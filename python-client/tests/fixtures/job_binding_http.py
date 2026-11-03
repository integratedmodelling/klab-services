"""Real loopback HTTP, synthetic scoped jobs; NOT Java retention/restart proof."""
import asyncio
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import threading
import sys

import pytest

from klab_client import AsyncClient, Client, Context, Endpoint
from klab_client.async_client import AsyncContext, AsyncJob
from klab_client.client import Job
from klab_client.errors import ProtocolError


def run(asynchronous):
    calls, cancelled = [], set()
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_): pass
        def do_GET(self):
            base, _, action, number = self.path.strip("/").split("/")
            scope = self.headers.get("klab-scope")
            calls.append((base, scope, action, number))
            assert number == "5" and scope in ("s.c", "s.other")
            assert self.headers["authorization"] == base + "-credential"
            assert self.headers["klab-service"] == base + "-runtime"
            if action == "cancel":
                cancelled.add((base, scope)); payload = True
            elif action == "status": payload = {"status": "FINISHED"}
            else: payload = {"synthetic_owner": base + ":" + scope}
            body = json.dumps(payload).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers(); self.wfile.write(body)
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    url = "http://127.0.0.1:" + str(server.server_port)
    async def scenario():
        cls, ctx_cls, job_cls = (AsyncClient, AsyncContext, AsyncJob) if asynchronous else (Client, Context, Job)
        first, second = [cls(url + "/" + base, base + "-credential", runtime_service_id=base + "-runtime")
                         for base in ("one", "two")]
        try:
            one = ctx_cls(first, {"id": "s.c", "serviceId": "one-runtime"})
            two = ctx_cls(second, {"id": "s.c", "serviceId": "two-runtime"})
            jobs = [job_cls(first, 5, one), job_cls(second, 5, two)]
            for base, job in zip(("one", "two"), jobs):
                result = await job.result(2) if asynchronous else job.result(2)
                assert result == {"synthetic_owner": base + ":s.c"}
            before = len(calls)
            one.context_id = "s.other"
            with pytest.raises(ProtocolError):
                if asynchronous: await jobs[0].cancel()
                else: jobs[0].cancel()
            assert not cancelled and len(calls) == before
            one.context_id = "s.c"
            first.transport.endpoints["runtime"] = Endpoint(url + "/two", "two-credential")
            first.runtime_service_id = "two-runtime"
            with pytest.raises(ProtocolError):
                if asynchronous: await jobs[0].result(2)
                else: jobs[0].result(2)
            assert len(calls) == before
            assert (await jobs[1].cancel() if asynchronous else jobs[1].cancel()) is True
            assert cancelled == {("two", "s.c")}
        finally:
            if asynchronous:
                await first.close(); await second.close()
            else:
                first.close(); second.close()
    try:
        asyncio.run(scenario())
    finally:
        server.shutdown(); server.server_close(); thread.join(timeout=3)
        assert not thread.is_alive()


if __name__ == "__main__":
    assert sys.argv[1] in ("sync", "async")
    run(sys.argv[1] == "async")
    print(json.dumps({"passed": True, "mode": sys.argv[1], "synthetic_not_java_retention": True}))
