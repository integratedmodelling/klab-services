"""Simulated bounded restoration/deadline/concurrency; not durable Java guarantees."""
import asyncio
import json
import os
import subprocess
import sys

import pytest

from klab_client import AsyncClient, Client, ObservationRequest
from klab_client.errors import JobCancelledError, JobUnavailableError, MissingAssetError, WaitTimeout
from conftest import ROOT, SIM_IMPORT_ROOT
from test_vertical import client_args, control, geometry


def test_fresh_sdk_process_restore_does_not_create_or_dispose_remote_state(simulator, tmp_path):
    env = os.environ.copy()
    # Inherit the exact imported SDK, including packaged-wheel test runs.
    import klab_client
    from pathlib import Path
    env["PYTHONPATH"] = os.pathsep.join((str(Path(klab_client.__file__).resolve().parents[1]), SIM_IMPORT_ROOT))
    def worker(mode):
        result = subprocess.run([sys.executable, str(ROOT / "tests/restore_worker.py"), mode,
            json.dumps(simulator), str(tmp_path)], env=env, capture_output=True, text=True,
            timeout=15, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        assert result.returncode == 0, result.stderr
        return json.loads(result.stdout)
    created = worker("create")
    before = control(simulator)
    restored = worker("restore")
    assert {k: created[k] for k in ("context", "job", "observation")} == {k: restored[k] for k in ("context", "job", "observation")}
    after = control(simulator)
    assert before["counts"] == after["counts"] == {"sessions": 1, "contexts": 1, "jobs": 1, "observations": 1}
    trace = after["trace"][len(before["trace"]):]
    assert not any(row["path"].endswith(("/createSession", "/createContext", "/submit", "/releaseContext", "/releaseSession")) for row in trace)
    for path in tmp_path.glob("*.json"):
        assert "sim-token-" not in path.read_text() and "values" not in path.read_text()
    with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
        context = client.attach_context(created["context"])
        assert not context.owned
        context.release()
        client.runtime.release_session(created["context"].split(".")[0])
    assert not any(control(simulator)["counts"].values())


@pytest.mark.parametrize("cancel_before_finish", [False, True])
def test_zero_deadline_preserves_job_without_submission_replay(simulator, cancel_before_finish):
    with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
        session = client.create_session()
        context = session.create_context()
        try:
            job = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))
            before = control(simulator)
            with pytest.raises(WaitTimeout) as timeout:
                job.result(timeout=0)
            assert timeout.value.job is job
            assert control(simulator)["counts"] == before["counts"]
            if cancel_before_finish:
                assert job.cancel()
                with pytest.raises(JobCancelledError):
                    job.result(timeout=2)
            else:
                assert job.result(timeout=2).fetch_data((0,)).values == (100,)
            assert sum(row["path"].endswith("/submit") for row in control(simulator)["trace"]) == 1
        finally:
            context.release()
            session.release()


def test_async_concurrent_contexts_cancel_one_wait_and_resume_other(simulator):
    async def run():
        async with AsyncClient(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
            session = await client.create_session()
            contexts = await asyncio.gather(*(session.create_context() for _ in range(4)))
            try:
                request = ObservationRequest("geography:Elevation in m", geometry=geometry())
                jobs = await asyncio.gather(*(context.observe(request) for context in contexts))
                with pytest.raises(WaitTimeout):
                    await jobs[0].result(timeout=0)
                assert await jobs[0].cancel()
                with pytest.raises(JobCancelledError):
                    await jobs[0].result(timeout=2)
                observations = await asyncio.gather(*(job.result(timeout=2) for job in jobs[1:]))
                values = await asyncio.gather(*(observation.fetch_data((0, 399)) for observation in observations))
                assert all(value.values == (100, 195) for value in values)
                assert len({observation.id for observation in observations}) == 3
            finally:
                await asyncio.gather(*(context.release() for context in contexts))
                await session.release()
    asyncio.run(run())
    assert not any(control(simulator)["counts"].values())


def test_released_job_is_unavailable_not_cancelled_or_replayed(simulator):
    with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
        session = client.create_session()
        context = session.create_context()
        job = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))
        context.release()
        try:
            with pytest.raises(MissingAssetError):
                job.result(timeout=2)
            assert not job.cancellation_requested
        finally:
            session.release()


def test_expired_job_empty_status_is_not_confirmed_cancellation(simulator):
    import httpx
    with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
        session = client.create_session()
        context = session.create_context()
        try:
            job = context.observe(ObservationRequest("geography:Elevation in m", geometry=geometry()))
            base = simulator["endpoints"]["runtime"].rsplit("/", 1)[0]
            response = httpx.post(base + "/__sim__/expireJobs", headers={"Authorization": "sim-admin-control"},
                                  trust_env=False, timeout=3)
            response.raise_for_status()
            with pytest.raises(JobUnavailableError):
                job.result(timeout=2)
            assert not job.cancellation_requested
            assert control(simulator)["counts"]["observations"] == 0
        finally:
            context.release()
            session.release()
