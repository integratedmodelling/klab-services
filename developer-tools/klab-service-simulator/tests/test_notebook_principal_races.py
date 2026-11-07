"""SIMULATED_PROVISIONAL/JAVA-GAP-0009/0015; no native sharing/admission claim."""
import json
import os
from pathlib import Path
import sys
import textwrap

import pytest

nbformat = pytest.importorskip("nbformat", reason="Notebook acceptance extra required")
pytest.importorskip("nbclient", reason="Notebook acceptance extra required")
pytest.importorskip("ipykernel", reason="Notebook acceptance extra required")
psutil = pytest.importorskip("psutil", reason="Owned kernel cleanup inspection required")

import klab_client
from conftest import REPO
from test_vertical import control


def test_owned_kernel_separate_clients_cas_race_foreign_denial_and_reader_close(simulator, tmp_path):
    sys.path.insert(0, str(REPO / "python-client/tools"))
    try:
        from workflows.async_notebook import check_outputs, execute_private_kernel
    finally:
        sys.path.pop(0)
    source = textwrap.dedent('''
        import asyncio, json, os
        from klab_client import (AsyncClient, AsyncContext, Endpoint, ObservationRequest,
            RectangularGeometry, async_write_draft, async_read_draft, DraftConflict)
        from klab_client.errors import AuthorizationError
        ready = json.loads(os.environ['KLAB_SIM_NOTEBOOK'])
        runtime = ready['endpoints']['runtime']
        def client(token):
            return AsyncClient(runtime, token, agent_name='alice',
                reasoner=Endpoint(ready['endpoints']['reasoner'], token),
                runtime_service_id=ready['service_ids']['runtime'], poll_interval=.01, timeout=3)
        async def scenario():
            async with client(ready['credentials']['owner']) as owner:
                session = await owner.create_session()
                context = await session.create_context()
                try:
                    job = await context.observe(ObservationRequest('geography:Elevation in m',
                        geometry=RectangularGeometry(0, 2000, 0, 2000, 20, 20)))
                    observation = await job.result(timeout=2)
                    first = await async_write_draft(context, 'source', 'initial',
                        expected_revision=0, allow_experimental=True)
                    async with client(ready['credentials']['owner']) as left, client(ready['credentials']['same_owner']) as right:
                        left_context = await left.attach_context(context.id)
                        right_context = await right.attach_context(context.id)
                        assert (await async_read_draft(left_context, 'source', allow_experimental=True)) == first
                        async def writer(bound, value):
                            try:
                                return await async_write_draft(bound, 'source', value,
                                    expected_revision=1, allow_experimental=True)
                            except DraftConflict:
                                return None
                        results = await asyncio.gather(writer(left_context, 'left'), writer(right_context, 'right'))
                        assert sum(item is not None for item in results) == 1
                        current = await async_read_draft(context, 'source', allow_experimental=True)
                        assert current.revision == 2 and current.validation == 'UNVALIDATED_PRIVATE_DRAFT'
                        assert current.source in ('left', 'right')
                        assert await async_read_draft(context, 'source', revision=1, allow_experimental=True) == first
                    async with client(ready['credentials']['foreign']) as denied:
                        foreign = AsyncContext(denied, {'id': context.id, 'serviceId': ready['service_ids']['runtime']})
                        for operation in (
                            lambda: async_read_draft(foreign, 'source', allow_experimental=True),
                            lambda: denied.runtime.query_knowledge_graph(
                                {'type': 'QUERY', 'resultType': 'OBSERVATION', 'id': observation.id, 'limit': 2}, foreign),
                            lambda: async_write_draft(foreign, 'source', 'foreign', expected_revision=2, allow_experimental=True)):
                            try:
                                await operation()
                            except AuthorizationError:
                                pass
                            else:
                                raise AssertionError('A handle must never grant another principal access')
                    assert (await observation.fetch_data((0, 399))).values == (100, 195)
                    assert (await async_read_draft(context, 'source', allow_experimental=True)) == current
                    return dict(backend='SIMULATED', java_science='NOT_RUN', writers=2,
                        winners=1, denied_operations=3, validation=current.validation,
                        reader_close_preserved_owner=True)
                finally:
                    await context.release()
                    await session.release()
        report = await scenario()
        print(json.dumps(report))
    ''')
    notebook = nbformat.v4.new_notebook(cells=[
        nbformat.v4.new_code_cell("import json,sys,os,psutil,klab_client\n"
            "print(json.dumps(dict(interpreter=sys.executable,sdk_file=klab_client.__file__,"
            "pid=os.getpid(),created=psutil.Process().create_time())))"),
        nbformat.v4.new_code_cell(source),
        nbformat.v4.new_code_cell("assert report['winners'] == 1 and report['denied_operations'] == 3\n"
            "assert report['backend'] == 'SIMULATED' and report['java_science'] == 'NOT_RUN'")])
    notebook.metadata.kernelspec = {"name": "klab-owned-async", "display_name": "owned", "language": "python"}
    env = {key: value for key, value in os.environ.items() if not key.startswith("KLAB_") and key != "PYTHONPATH"}
    env["PYTHONPATH"] = str(Path(klab_client.__file__).resolve().parents[1])
    env["KLAB_SIM_NOTEBOOK"] = json.dumps(simulator | {"credentials": {
        "owner": "sim-token-alice", "same_owner": "sim-token-admin-alice", "foreign": "sim-token-bob"}})
    execute_private_kernel(notebook, tmp_path, env)
    check_outputs(notebook, ["sim-token-alice", "sim-token-admin-alice", "sim-token-bob", "sim-admin-control"])
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
    assert report["writers"] == 2 and report["winners"] == 1 and report["denied_operations"] == 3
    assert report["validation"] == "UNVALIDATED_PRIVATE_DRAFT" and report["reader_close_preserved_owner"]
    state = control(simulator)
    assert not any(state["counts"].values()) and state["draft_contexts"] == 0
    assert state["authoring_assets"] == 0
    assert sum(row["path"].endswith("/submit") for row in state["trace"]) == 1
    writes = [row for row in state["trace"] if row["method"] == "PUT" and "/experimental/drafts/v1/" in row["path"]]
    assert sorted(row["status"] for row in writes) == [200, 200, 403, 409]
