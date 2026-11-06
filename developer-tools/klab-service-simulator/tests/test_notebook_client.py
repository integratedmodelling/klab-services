"""RC-P3.5/CAP15: actual owned kernel + ordinary async SDK over existing HTTP routes."""
import json
import os
from pathlib import Path
import sys
import textwrap

import pytest

nbformat = pytest.importorskip("nbformat", reason="notebook acceptance extra required")
pytest.importorskip("nbclient", reason="notebook acceptance extra required")
pytest.importorskip("ipykernel", reason="notebook acceptance extra required")
psutil = pytest.importorskip("psutil", reason="owned kernel cleanup inspection required")

import klab_client
from conftest import REPO
from test_vertical import control


def test_owned_exact_sdk_kernel_await_shared_job_local_cancellation_reader_close_and_partial_graph(simulator, tmp_path):
    # Reuse the established exact-interpreter, hidden-kernel/finally-shutdown helper.
    sys.path.insert(0, str(REPO / "python-client/tools"))
    try:
        from workflows.async_notebook import check_outputs, execute_private_kernel
    finally:
        sys.path.pop(0)
    source = textwrap.dedent('''
        import asyncio, json, os
        from klab_client import (AsyncClient, AsyncGraphBrowser, AsyncGraphProjection,
                                 Endpoint, ObservationRequest, RectangularGeometry)
        ready = json.loads(os.environ["KLAB_SIM_NOTEBOOK"])
        urls = ready["endpoints"]
        def options():
            token = ready["credential"]
            return dict(credential=token, agent_name="alice",
                        runtime_service_id=ready["service_ids"]["runtime"],
                        reasoner=Endpoint(urls["reasoner"], token),
                        resources=Endpoint(urls["resources"], token),
                        resolver=Endpoint(urls["resolver"], token), poll_interval=.01, timeout=3)
        async def scenario():
            async with AsyncClient(urls["runtime"], **options()) as client:
                session = await client.create_session()
                contexts = []
                try:
                    for _ in range(2):
                        contexts.append(await session.create_context())
                    request = ObservationRequest("geography:Elevation in m",
                        geometry=RectangularGeometry(0, 2000, 0, 2000, 20, 20))
                    jobs = await asyncio.gather(*(ctx.observe(request) for ctx in contexts))
                    first_status = asyncio.Event()
                    original = jobs[0].status
                    async def tracked_status(**kwargs):
                        value = await original(**kwargs)
                        first_status.set()
                        return value
                    jobs[0].status = tracked_status  # test-only scheduling gate, actual native HTTP status read
                    own_wait = asyncio.create_task(jobs[0].result(timeout=5))
                    await asyncio.wait_for(first_status.wait(), timeout=2)
                    survivor = asyncio.create_task(jobs[0].result(timeout=5))
                    independent = asyncio.create_task(jobs[1].result(timeout=5))
                    own_wait.cancel()
                    try:
                        await own_wait
                    except asyncio.CancelledError:
                        cancelled = True
                    else:
                        raise AssertionError("Local wait must be cancelled, not a fabricated result")
                    observations = await asyncio.gather(survivor, independent)
                    blocks = await asyncio.gather(*(obs.fetch_data((0, 399)) for obs in observations))
                    assert all(tuple(block.values) == (100, 195) for block in blocks)
                    saved = observations[0].to_handle()
                    async with AsyncClient(urls["runtime"], **options()) as reader:
                        restored = await reader.restore_handle(saved)
                        assert (await restored.fetch_data((0,))).values == (100,)
                    assert (await observations[0].fetch_data((399,))).values == (195,)
                    projection = AsyncGraphProjection(AsyncGraphBrowser(contexts[0]))
                    page = await projection.load(observations[0], relationships=("MEASURED",))
                    assert len(page.page.links) == 1 and page.page.partial
                    projection.invalidate("NOTIFICATION_HINT")
                    assert projection.view.pages[0].invalidated
                    refreshed = await projection.load(observations[0], relationships=("MEASURED",))
                    assert not refreshed.invalidated and "UNKNOWN" in projection.view.consistency
                    return dict(own_wait_cancelled=cancelled, jobs_resumed_without_recreate=True,
                                other_wait_completed=True, same_principal_reader_only=True,
                                reader_close_preserved_work=True, independent_contexts=2,
                                verified_endpoint_cells=4, graph_partial=True,
                                backend="SIMULATED", java_science="NOT_RUN")
                finally:
                    for ctx in contexts:
                        await ctx.release()
                    await session.release()
        report = await scenario()
        print(json.dumps(report))
    ''')
    notebook = nbformat.v4.new_notebook(cells=[
        nbformat.v4.new_code_cell(
            "import json,sys,os,psutil,klab_client\n"
            "print(json.dumps(dict(interpreter=sys.executable,sdk_file=klab_client.__file__,"
            "pid=os.getpid(),created=psutil.Process().create_time())))"),
        nbformat.v4.new_code_cell(source),
        nbformat.v4.new_code_cell(
            "assert report['own_wait_cancelled'] and report['other_wait_completed'] and report['reader_close_preserved_work']\n"
            "assert report['graph_partial'] and report['backend']=='SIMULATED'\n"
            "print('SIMULATED notebook acceptance only; no Java scientific execution')")])
    notebook.metadata.kernelspec = {"name": "klab-owned-async", "display_name": "owned", "language": "python"}
    env = {key: value for key, value in os.environ.items() if not key.startswith("KLAB_") and key != "PYTHONPATH"}
    env["PYTHONPATH"] = str(Path(klab_client.__file__).resolve().parents[1])
    env["KLAB_SIM_NOTEBOOK"] = json.dumps(simulator | {"credential": "sim-token-alice"})
    execute_private_kernel(notebook, tmp_path, env)
    check_outputs(notebook, ["sim-token-alice", "sim-token-bob", "sim-admin-control"])
    identity = json.loads(notebook.cells[0].outputs[0].text)
    assert Path(identity["interpreter"]).resolve() == Path(sys.executable).resolve()
    assert Path(identity["sdk_file"]).resolve() == Path(klab_client.__file__).resolve()
    assert identity["pid"] != os.getpid()
    try:
        assert psutil.Process(identity["pid"]).create_time() != identity["created"]
    except psutil.NoSuchProcess:
        pass
    assert not list(tmp_path.glob("private-kernel-*"))
    report = json.loads(notebook.cells[1].outputs[0].text)
    assert report["independent_contexts"] == 2 and report["verified_endpoint_cells"] == 4
    state = control(simulator)
    assert not any(state["counts"].values())
    assert sum(row["path"].endswith("/submit") for row in state["trace"]) == 2
    assert not any("/jobs/cancel/" in row["path"] for row in state["trace"])
    assert sum(row["path"].endswith("/createSession") for row in state["trace"]) == 1
    assert sum(row["path"].endswith("/createContext") for row in state["trace"]) == 2
