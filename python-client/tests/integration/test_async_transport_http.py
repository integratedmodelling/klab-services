"""Actual loopback HTTP overlap/stream closure, not real k.LAB acceptance."""
import asyncio
import json

from klab_client._async_transport import AsyncTransport
from klab_client.transport import Endpoint


def test_real_http_requests_overlap_without_blocking_the_event_loop():
    async def scenario():
        both_entered, release = asyncio.Event(), asyncio.Event()
        calls, tasks = [], set()

        async def handler(reader, writer):
            task = asyncio.current_task()
            tasks.add(task)
            try:
                headers = await reader.readuntil(b"\r\n\r\n")
                assert b"Authorization: runtime-key\r\n" in headers
                assert b"klab-scope: s.c\r\n" in headers
                calls.append(headers.split(b"\r\n", 1)[0])
                if len(calls) == 2:
                    both_entered.set()
                await release.wait()
                body = json.dumps({"value": 0, "valid": False}).encode()
                writer.write(b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                             + str(len(body)).encode() + b"\r\nConnection: close\r\n\r\n" + body)
                await writer.drain()
            finally:
                writer.close()
                await writer.wait_closed()
                tasks.discard(task)

        server = await asyncio.start_server(handler, "127.0.0.1", 0)
        port = server.sockets[0].getsockname()[1]
        pending = []
        try:
            async with AsyncTransport({"runtime": Endpoint(f"http://127.0.0.1:{port}", "runtime-key")}) as client:
                pending = [asyncio.create_task(client.request("runtime", "GET", f"/read/{i}", scope="s.c")) for i in range(2)]
                # Both requests must reach the actual server before either response is
                # released. A sync request in the event-loop thread would deadlock.
                await asyncio.wait_for(both_entered.wait(), 3)
                assert not any(task.done() for task in pending)
                release.set()
                results = await asyncio.wait_for(asyncio.gather(*pending), 3)
                assert results == [{"value": 0, "valid": False}] * 2
                assert len(calls) == 2
        finally:
            release.set()
            for task in pending:
                if not task.done():
                    task.cancel()
            await asyncio.gather(*pending, return_exceptions=True)
            server.close()
            await server.wait_closed()
            if tasks:
                await asyncio.wait_for(asyncio.gather(*list(tasks)), 3)
    asyncio.run(scenario())
