"""Isolated owned simulator process. Never discover or modify a live Java service."""
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import socket

import pytest

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
if importlib.util.find_spec("klab_simulator") is None:
    sys.path.insert(0, str(ROOT / "src"))
if importlib.util.find_spec("klab_client") is None:
    sys.path.insert(0, str(REPO / "python-client/src"))
SIM_IMPORT_ROOT = str(Path(importlib.util.find_spec("klab_simulator").origin).resolve().parents[1])


@contextmanager
def launch(scenario="elevation", mode="simulated", *, port=0, state_file=None, seed=7):
    if type(seed) is not int or not 0 <= seed < 2**31:
        raise ValueError("Choose an exact bounded synthetic backend identity seed")
    env = os.environ.copy()
    env["PYTHONPATH"] = os.pathsep.join((SIM_IMPORT_ROOT, env.get("PYTHONPATH", "")))
    options = ["--state-file", str(state_file)] if state_file is not None else []
    process = subprocess.Popen([sys.executable, "-m", "klab_simulator", "--port", str(port),
        "--scenario", scenario, "--seed", str(seed), "--mode", mode, "--test-mode", *options], env=env,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    try:
        with ThreadPoolExecutor(max_workers=1) as pool:
            try:
                line = pool.submit(process.stdout.readline).result(timeout=10)
            except BaseException:
                process.kill()
                raise
        assert line, process.stderr.read()
        yield json.loads(line)
    finally:
        if process.poll() is None:
            process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
        process.stdout.close()
        process.stderr.close()


@pytest.fixture
def simulator():
    with launch() as ready:
        yield ready


@pytest.fixture(autouse=True)
def isolated_loopback_only(monkeypatch):
    original = socket.getaddrinfo
    def restricted(host, port, *args, **kwargs):
        if host not in ("127.0.0.1", "localhost", "::1") or port in (8091, 8092, 8093, 8094, 8382, 7687):
            raise AssertionError("Default simulator tests may not contact production ports or external hosts")
        return original(host, port, *args, **kwargs)
    monkeypatch.setattr(socket, "getaddrinfo", restricted)
