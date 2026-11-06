"""Owned real socket multipart protocol check; not a Java admission/science fixture."""
import asyncio
from email.parser import BytesParser
from email.policy import default
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import sys
import tempfile
import threading

from klab_client import AsyncClient, AsyncResourceImporter, Client, Endpoint, ResourceImporter


def main(mode):
    content = bytes(range(256)) * 4096
    calls = []
    media = "image/tiff;application=geotiff"
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def reply(self, body):
            data = json.dumps(body).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            calls.append(("GET", self.path))
            assert self.headers["Authorization"] == "test-resource-token"
            assert self.headers["klab-scope"] == "owned-scope"
            if self.path == "/resources/public/capabilities":
                self.reply({"serviceId": "resources-test-id", "importSchemata": {"raster": [{
                    "type": "STREAM", "knowledgeClass": "RESOURCE", "schemaId": "raster.geotiff.import",
                    "mediaTypes": [media]}]}})
            elif self.path == "/resources/jobs/status/17":
                self.reply({"status": "FINISHED"})
            else:
                assert self.path == "/resources/jobs/retrieve/17"
                self.reply({"empty": False, "results": [{"resourceUrn": "owned:result"}]})

        def do_POST(self):
            calls.append(("POST", self.path))
            assert self.path == "/resources/import/raster.geotiff.import/owned%3Aupload"
            assert self.headers["Authorization"] == "test-resource-token"
            assert self.headers["klab-scope"] == "owned-scope"
            length = int(self.headers["Content-Length"])
            assert len(content) < length < len(content) + 2048
            body = self.rfile.read(length)
            message = BytesParser(policy=default).parsebytes(
                ("Content-Type: " + self.headers["Content-Type"] + "\r\n\r\n").encode() + body)
            parts = list(message.iter_parts())
            assert len(parts) == 1
            assert parts[0].get_param("name", header="content-disposition") == "file"
            assert parts[0].get_filename() == "upload.tif"
            assert parts[0].get_payload(decode=True) == content
            self.reply(17)

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        with tempfile.TemporaryDirectory(prefix="resource-import-http-") as directory:
            path = Path(directory) / "private-original-name.tif"
            path.write_bytes(content)
            endpoint = Endpoint(f"http://127.0.0.1:{server.server_port}/resources", "test-resource-token")
            args = {"urn": "owned:upload", "schema_id": "raster.geotiff.import", "media_type": media,
                    "allow_write": True}
            if mode == "async":
                async def scenario():
                    async with AsyncClient("http://127.0.0.1:1/runtime", resources=endpoint) as client:
                        receipt = await AsyncResourceImporter(client, "owned-scope").upload(path, **args)
                        return receipt, await receipt.job.result()
                receipt, result = asyncio.run(scenario())
            else:
                with Client("http://127.0.0.1:1/runtime", resources=endpoint) as client:
                    receipt = ResourceImporter(client, "owned-scope").upload(path, **args)
                    result = receipt.job.result()
            assert receipt.source_sha256 == hashlib.sha256(content).hexdigest()
            assert receipt.source_bytes == len(content)
            assert result["results"][0]["resourceUrn"] == "owned:result"
            assert len(calls) == 4 and sum(method == "POST" for method, _ in calls) == 1
    finally:
        server.shutdown()
        server.server_close()
        thread.join(5)
    assert not thread.is_alive()
    print(json.dumps({"passed": True, "mode": mode, "upload_bytes": len(content),
                      "requests": len(calls), "java_admission": "NOT_RUN"}))


if __name__ == "__main__":
    main(sys.argv[1])
