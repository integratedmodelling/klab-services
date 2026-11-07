"""Trusted local-script process-tree cleanup, finite wall time and file byte budgets.

Not an adversarial OS sandbox or a hard resident-memory guarantee. No server-supplied
code/command is executed; no credentials are passed to the child. Native deployment
requires an operator-approved isolation boundary (JAVA-GAP-0017).
"""
import hashlib
from contextlib import contextmanager
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time

from .protocol import Field, Limits, WorkerError, decoded, encoded
from .containment import ProcessTree

# Trusted bootstrap cannot execute the operator script until the parent has
# assigned containment and provided EOF. Buffering is bounded by encoded(Field).
BOOTSTRAP = '''import io,os,runpy,sys
data=sys.stdin.buffer.read()
if not data:
    sys.exit(125)
budget=int(sys.argv[2])
if budget and os.name != "nt":
    import resource
    resource.setrlimit(resource.RLIMIT_AS,(budget,budget))
sys.stdin=io.TextIOWrapper(io.BytesIO(data),encoding="utf-8")
runpy.run_path(sys.argv[1],run_name="__main__")
'''


@contextmanager
def owned_directory():
    directory = tempfile.TemporaryDirectory(prefix="klab-worker-")
    try:
        yield directory.name
    finally:
        deadline = time.monotonic() + 2
        while True:
            try:
                directory.cleanup()
                break
            except PermissionError:
                if time.monotonic() >= deadline:
                    raise WorkerError("Owned worker temporary cleanup could not be confirmed") from None
                time.sleep(.02)  # Windows may release terminated members' CWD handles asynchronously.


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
    with owned_directory() as directory:
        # Execute exactly the bytes identified by provenance, not a mutable source path.
        snapshot = Path(directory) / "trusted.py"
        snapshot.write_bytes(source)
        with tempfile.TemporaryFile() as stdout, tempfile.TemporaryFile() as stderr:
            process = subprocess.Popen([sys.executable, "-I", "-c", BOOTSTRAP, str(snapshot), str(limits.memory_bytes or 0)], stdin=subprocess.PIPE, stdout=stdout, stderr=stderr,
                cwd=directory, env=env, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0), start_new_session=os.name != "nt")
            start = time.monotonic()
            tree = None
            try:
                tree = ProcessTree(process, limits.memory_bytes)
                assert process.stdin is not None  # PIPE is explicit above.
                process.stdin.write(encoded(field.to_dict()))
                process.stdin.close()
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
                if tree is not None:
                    tree.close()  # Also remove descendants after a successful direct child.
                if process.stdin is not None and not process.stdin.closed:
                    process.stdin.close()
                if process.poll() is None:
                    process.kill()
                process.wait(timeout=5)
