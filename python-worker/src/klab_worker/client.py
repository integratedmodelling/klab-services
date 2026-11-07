"""Explicit versioned experimental transport; no discovery guess or live fallback."""
import hashlib
import re
import time
from urllib.parse import urlsplit

import httpx

from .protocol import Field, PROTOCOL, WorkerError, decoded, encoded, MAX_BYTES
from . import _responses


def identity(value):
    if not isinstance(value, str) or value in (".", "..") or not re.fullmatch(r"[A-Za-z0-9_.-]{1,128}", value):
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
        self._scope = scope

    def close(self):
        self._http.close()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()  # Local transport only; never deletes/disposes remote work.

    def _request(self, method, path, body=None, *, poll=False):
        payload = encoded(body) if body is not None else None
        try:
            with self._http.stream(method, "experimental/worker/v1/" + path, content=payload,
                    headers={"Content-Type": "application/json"}, timeout=.2 if poll else self._http.timeout) as response:
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
        worker_id = identity(worker_id)
        return _responses.ack(self._request("POST", "register", {"worker_id": worker_id, "operation": identity(operation)}),
            "REGISTERED", worker_id=worker_id)

    def submit(self, field, *, operation):
        if not isinstance(field, Field):
            raise WorkerError("Submit requires a validated Field")
        return _responses.assigned(self._request("POST", "tasks", {"operation": identity(operation), "input": field.to_dict()}))

    def claim(self, worker_id):
        return _responses.claimed(self._request("POST", "claim", {"worker_id": identity(worker_id)}))

    def status(self, task_id):
        task_id = identity(task_id)
        return _responses.status(self._request("GET", "tasks/" + task_id), task_id, self._scope)

    def cancel(self, task_id):
        return _responses.ack(self._request("POST", "tasks/" + identity(task_id) + "/cancel", {}), "CANCELLED")

    def recover_task(self, task_id, *, allow_recompute=False):
        if allow_recompute is not True:
            raise WorkerError("Lost worker outcome requires explicit recomputation consent; no automatic replay")
        task_id = identity(task_id)
        return _responses.assigned(self._request("POST", "tasks/" + task_id + "/recover", {"confirm_recompute": True}), task_id)

    def release(self, worker_id):
        return _responses.ack(self._request("POST", "release", {"worker_id": identity(worker_id)}), "RELEASED")

    def fail(self, task):
        _responses.task_input(task)
        return _responses.ack(self._request("POST", "tasks/" + identity(task.get("task_id")) + "/fail", {"lease": task.get("lease")}), "FAILED")

    def run_once(self, worker_id, script, *, limits=None, cancel=None):
        from .execution import execute
        from .protocol import Limits
        task = self.claim(worker_id)
        if task.get("state") == "IDLE":
            return task
        client = self
        class Cancellation:
            next_check = 0.0

            def is_set(self):
                if cancel is not None and cancel.is_set():
                    return True
                now = time.monotonic()
                if now >= self.next_check:
                    self.next_check = now + .2
                    response = client._request("GET", "tasks/" + identity(task["task_id"]), poll=True)
                    if _responses.status(response, task["task_id"], client._scope)["state"] != "LEASED":
                        return True
                return False
        try:
            result, digest = execute(script, Field.from_dict(task.get("input")), limits=limits or Limits(), cancel=Cancellation())
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
        source = _responses.task_input(task)
        if not field.same_layout(source):
            raise WorkerError("Worker result cannot silently alter geometry, units or validity")
        output_sha256 = hashlib.sha256(encoded(field.to_dict())).hexdigest()
        return _responses.ack(self._request("POST", "tasks/" + identity(task.get("task_id")) + "/publish", {
            "lease": task.get("lease"), "output": field.to_dict(), "input_sha256": hashlib.sha256(encoded(source.to_dict())).hexdigest(),
            "implementation_sha256": implementation_sha256}), "PUBLISHED", output_sha256=output_sha256)
