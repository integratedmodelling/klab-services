"""JAVA-GAP-0017 / SIMULATED_PROVISIONAL owned process-tree cleanup controls."""
import json
import threading
import time

import psutil
import pytest

from klab_worker import Limits, WorkerError, execute
from test_worker import sample


@pytest.mark.parametrize("outcome", ["success", "deadline", "cancel", "crash"])
def test_owned_descendant_does_not_outlive_execution(tmp_path, outcome):
    marker = tmp_path / "descendant.json"
    script = tmp_path / "trusted.py"
    script.write_text('import json,subprocess,sys,time\n'
        'p=subprocess.Popen([sys.executable,"-I","-c","import time;time.sleep(60)"])\n'
        f'open({str(marker)!r},"w").write(json.dumps({{"pid":p.pid}}))\n' +
        ('import os;os._exit(1)\n' if outcome == "crash" else
         'print(sys.stdin.read())\n' if outcome == "success" else 'time.sleep(60)\n'))
    cancellation = threading.Event()
    def cancel_started():
        deadline = time.monotonic() + 5
        while not marker.exists() and time.monotonic() < deadline:
            cancellation.wait(.01)
        cancellation.set()
    thread = threading.Thread(target=cancel_started) if outcome == "cancel" else None
    if thread:
        thread.start()
    start = time.monotonic()
    try:
        if outcome == "success":
            result, _ = execute(script, sample())
            assert result == sample()
        else:
            with pytest.raises(WorkerError):
                execute(script, sample(), limits=Limits(seconds=1 if outcome == "deadline" else 10), cancel=cancellation)
        assert time.monotonic() - start < 5
        pid = json.loads(marker.read_text())["pid"]
        deadline = time.monotonic() + 3
        while psutil.pid_exists(pid) and time.monotonic() < deadline:
            process = psutil.Process(pid)
            if process.status() == psutil.STATUS_ZOMBIE:
                break  # Dead group member awaits reaping by its OS parent.
            time.sleep(.01)
        assert not psutil.pid_exists(pid) or psutil.Process(pid).status() == psutil.STATUS_ZOMBIE
    finally:
        cancellation.set()
        if thread:
            thread.join(timeout=5)


def test_containment_refusal_never_executes_operator_code(tmp_path, monkeypatch):
    import klab_worker.execution as module
    marker = tmp_path / "must-not-exist"
    script = tmp_path / "trusted.py"
    script.write_text(f'open({str(marker)!r},"w").write("unsafe")\n')
    def refuse(*_):
        raise WorkerError("Synthetic containment unavailable")
    monkeypatch.setattr(module, "ProcessTree", refuse)
    with pytest.raises(WorkerError, match="containment"):
        execute(script, sample())
    assert not marker.exists()


@pytest.mark.parametrize("budget", [True, 0, -1, 64 * 1024**2 - 1, 16 * 1024**3 + 1, "96MiB"])
def test_invalid_memory_budget_cannot_disable_kernel_limit(budget):
    with pytest.raises(WorkerError, match="memory"):
        Limits(memory_bytes=budget)


def test_explicit_memory_budget_permits_bounded_computation(tmp_path):
    script = tmp_path / "bounded.py"
    script.write_text('import sys\nprint(sys.stdin.read())\n')
    result, _ = execute(script, sample(), limits=Limits(memory_bytes=128 * 1024**2))
    assert result == sample()


def test_actual_allocation_over_memory_budget_never_returns_output(tmp_path):
    marker = tmp_path / "allocated"
    script = tmp_path / "overallocate.py"
    script.write_text('import sys\nx=bytearray(256*1024*1024)\n' +
        f'open({str(marker)!r},"w").write("unlimited")\nprint(sys.stdin.read())\n')
    with pytest.raises(WorkerError, match="process failed"):
        execute(script, sample(), limits=Limits(memory_bytes=96 * 1024**2))
    assert not marker.exists()
