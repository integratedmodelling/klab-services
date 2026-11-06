"""Trusted local-script process isolation, finite wall time and file byte budgets.

Not an adversarial OS sandbox or a hard resident-memory guarantee. No server-supplied
code/command is executed; no credentials are passed to the child. Native deployment
requires an operator-approved isolation boundary (JAVA-GAP-0017).
"""
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time

from .protocol import Field, Limits, WorkerError, decoded, encoded


def execute(script, field, *, limits=Limits(), cancel=None):
    if not isinstance(field, Field) or not isinstance(limits, Limits) or len(field.values) > limits.cells:
        raise WorkerError("Invalid worker input or cell budget exceeded")
    script = Path(script).resolve(strict=True)
    if script.suffix != ".py" or not script.is_file() or script.stat().st_size > 1024 * 1024:
        raise WorkerError("Choose a bounded trusted local Python script")
    source = script.read_bytes()
    if len(source) > 1024 * 1024:
        raise WorkerError("Trusted worker script exceeded byte budget")
    digest = hashlib.sha256(source).hexdigest()
    if cancel is not None and cancel.is_set():
        raise WorkerError("Worker execution cancelled before start")
    # Finite pipes are implemented with files, avoiding unbounded communicate() buffers.
    # Child sees minimal environment, not host credentials/PYTHONPATH/certificates.
    env = {key: value for key, value in os.environ.items() if key.upper() in ("SYSTEMROOT", "WINDIR", "TEMP", "TMP")}
    with tempfile.TemporaryDirectory(prefix="klab-worker-") as directory:
        # Execute exactly the bytes identified by provenance, not a mutable source path.
        snapshot = Path(directory) / "trusted.py"
        snapshot.write_bytes(source)
        with tempfile.TemporaryFile() as stdin, tempfile.TemporaryFile() as stdout, tempfile.TemporaryFile() as stderr:
            stdin.write(encoded(field.to_dict())); stdin.seek(0)
            process = subprocess.Popen([sys.executable, "-I", str(snapshot)], stdin=stdin, stdout=stdout, stderr=stderr,
                cwd=directory, env=env, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            start = time.monotonic()
            try:
                while True:
                    if cancel is not None and cancel.is_set():
                        raise WorkerError("Worker execution cancelled")
                    if time.monotonic() - start >= limits.seconds:
                        raise WorkerError("Worker execution exceeded deadline")
                    if max(os.fstat(stdout.fileno()).st_size, os.fstat(stderr.fileno()).st_size) > limits.output_bytes:
                        raise WorkerError("Worker output exceeded byte budget")
                    try:
                        status = process.wait(timeout=min(0.05, limits.seconds))
                        break
                    except subprocess.TimeoutExpired:
                        continue
                if status != 0:
                    raise WorkerError("Worker process failed; output is not publishable")
                if os.fstat(stdout.fileno()).st_size > limits.output_bytes or os.fstat(stderr.fileno()).st_size > limits.output_bytes:
                    raise WorkerError("Worker output exceeded byte budget")
                stdout.seek(0)
                result = Field.from_dict(decoded(stdout.read(limits.output_bytes + 1)))
                if not result.same_layout(field):
                    raise WorkerError("Worker result changed declared geometry, units or validity")
                return result, digest
            finally:
                if process.poll() is None:
                    process.kill()
                process.wait(timeout=5)
