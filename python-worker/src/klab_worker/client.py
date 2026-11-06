"""Explicit versioned experimental transport; no discovery guess or live fallback."""
import hashlib
import re
from urllib.parse import urlsplit

import httpx

from .protocol import Field, PROTOCOL, WorkerError, decoded, encoded, MAX_BYTES


def identity(value):
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,128}", value):
        raise WorkerError("Invalid worker/task identity")
    return value


class WorkerClient:
    def __init__(self, endpoint, credential, *, scope, allow_experimental=False, timeout=10):
        if allow_experimental is not True:
            raise WorkerError("Experimental worker protocol requires explicit opt-in; native parity NOT_TESTED")
        url = urlsplit(endpoint)
        if (url.scheme not in ("http", "https") or not url.hostname or url.username or url.password or url.query or url.fragment
                or (url.scheme == "http" and url.hostname not in ("127.0.0.1", "localhost", "::1"))):
            raise WorkerError("Worker endpoint requires HTTPS or explicit loopback HTTP")
        if any(not isinstance(s, str) or not s or len(s) > 4096 or any(ord(c) < 32 or ord(c) == 127 for c in s) for s in (credential, scope)):
            raise WorkerError("Invalid worker credential or scope")
        if type(timeout) not in (int, float) or not 0 < timeout <= 300:
            raise WorkerError("Invalid worker timeout")
        self._http = httpx.Client(base_url=endpoint.rstrip("/") + "/", timeout=timeout, follow_redirects=False, trust_env=False,
            headers={"Authorization": credential, "klab-scope": scope, "X-Klab-Worker-Protocol": PROTOCOL})

    def close(self):
        self._http.close()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()  # Local transport only; never deletes/disposes remote work.

    def _request(self, method, path, body=None):
        payload = encoded(body) if body is not None else None
        try:
            with self._http.stream(method, "experimental/worker/v1/" + path, content=payload,
                    headers={"Content-Type": "application/json"}) as response:
                if response.status_code not in (200, 201):
                    raise WorkerError(f"Worker operation refused (HTTP {response.status_code}); no automatic replay")
                chunks, size = [], 0
                for chunk in response.iter_bytes():
                    size += len(chunk)
                    if size > MAX_BYTES:
                        raise WorkerError("Worker response exceeds byte budget")
                    chunks.append(chunk)
                value = decoded(b"".join(chunks))
                if not isinstance(value, dict) or value.get("protocol") != PROTOCOL:
                    raise WorkerError("Worker protocol negotiation failed")
                return value
        except httpx.HTTPError:
            raise WorkerError("Worker transport outcome unknown; no automatic replay") from None

    def register(self, worker_id, *, operation):
        return self._request("POST", "register", {"worker_id": identity(worker_id), "operation": identity(operation)})

    def submit(self, field, *, operation):
        if not isinstance(field, Field):
            raise WorkerError("Submit requires a validated Field")
        return self._request("POST", "tasks", {"operation": identity(operation), "input": field.to_dict()})

    def claim(self, worker_id):
        return self._request("POST", "claim", {"worker_id": identity(worker_id)})

    def status(self, task_id):
        return self._request("GET", "tasks/" + identity(task_id))

    def cancel(self, task_id):
        return self._request("POST", "tasks/" + identity(task_id) + "/cancel", {})

    def recover_task(self, task_id, *, allow_recompute=False):
        if allow_recompute is not True:
            raise WorkerError("Lost worker outcome requires explicit recomputation consent; no automatic replay")
        return self._request("POST", "tasks/" + identity(task_id) + "/recover", {"confirm_recompute": True})

    def release(self, worker_id):
        return self._request("POST", "release", {"worker_id": identity(worker_id)})

    def fail(self, task):
        if not isinstance(task, dict):
            raise WorkerError("Failure acknowledgement requires a claimed task")
        return self._request("POST", "tasks/" + identity(task.get("task_id")) + "/fail", {"lease": task.get("lease")})

    def run_once(self, worker_id, script, *, limits=None, cancel=None):
        from .execution import execute
        from .protocol import Limits
        task = self.claim(worker_id)
        if task.get("state") == "IDLE":
            return task
        try:
            result, digest = execute(script, Field.from_dict(task.get("input")), limits=limits or Limits(), cancel=cancel)
        except (WorkerError, OSError):
            try:
                self.fail(task)
            except WorkerError:
                pass  # Preserve original failure; lease outcome may be unknown/cancelled.
            raise
        return self.publish(task, result, implementation_sha256=digest)

    def publish(self, task, field, *, implementation_sha256):
        if (not isinstance(task, dict) or not isinstance(field, Field) or not isinstance(implementation_sha256, str)
                or not re.fullmatch(r"[0-9a-f]{64}", implementation_sha256)):
            raise WorkerError("Publication requires validated result and implementation digest")
        source = Field.from_dict(task.get("input"))
        if not field.same_layout(source):
            raise WorkerError("Worker result cannot silently alter geometry, units or validity")
        return self._request("POST", "tasks/" + identity(task.get("task_id")) + "/publish", {
            "lease": task.get("lease"), "output": field.to_dict(), "input_sha256": hashlib.sha256(encoded(source.to_dict())).hexdigest(),
            "implementation_sha256": implementation_sha256})
