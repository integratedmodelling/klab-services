"""One actual async notebook journey in a private exact-interpreter kernel."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

from .scientific_checks import require

PACKAGE = Path(__file__).resolve().parents[2]


def source_hashes(directory):
    return {path.relative_to(directory).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in directory.rglob("*.py")}


def check_outputs(notebook, credentials):
    encoded = json.dumps(notebook, ensure_ascii=False, allow_nan=False)
    require(len(encoded.encode("utf-8")) <= 1024 * 1024, "Notebook output exceeded the byte budget")
    require(all(secret not in encoded for secret in credentials if secret), "Notebook contains credential material")
    require(not re.search(r"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", encoded),
        "Notebook contains a JWT-shaped token")
    cells = [cell for cell in notebook["cells"] if cell["cell_type"] == "code"]
    require(len(cells) == 3 and [cell.get("execution_count") for cell in cells] == [1, 2, 3],
        "Notebook cells were not executed once in expected order")
    require(not any(output.get("output_type") == "error" for cell in cells for output in cell.get("outputs", [])),
        "Notebook execution retained error output")


def execute_private_kernel(notebook, directory, env):
    """Use the established exact-interpreter kernel/finally-shutdown pattern."""
    from nbclient import NotebookClient
    from jupyter_client import KernelManager
    from jupyter_client.kernelspec import KernelSpecManager
    with tempfile.TemporaryDirectory(prefix="private-kernel-", dir=directory) as temporary:
        kernel = Path(temporary) / "klab-owned-async"
        kernel.mkdir()
        (kernel / "kernel.json").write_text(json.dumps({"argv": [sys.executable, "-m", "ipykernel_launcher", "-f", "{connection_file}"],
            "display_name": "k.LAB owned async", "language": "python"}))
        manager = KernelManager(kernel_name="klab-owned-async", kernel_spec_manager=KernelSpecManager(kernel_dirs=[temporary]))
        try:
            options = {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}
            NotebookClient(notebook, km=manager, timeout=180, resources={"metadata": {"path": str(directory)}}).execute(env=env, **options)
        finally:
            if manager.has_kernel:
                manager.shutdown_kernel(now=True)
        require(not manager.has_kernel, "Owned notebook kernel shutdown unconfirmed")


def run(state):
    # Optional packages are loaded only for this explicit notebook scenario.
    import klab_client
    import nbformat
    import psutil

    state = Path(state).resolve()
    config = json.loads((state / "scientist.json").read_text())
    directory = state / "async-notebook"
    directory.mkdir()
    sdk = Path(klab_client.__file__).resolve()
    require(not sdk.is_relative_to(PACKAGE), "Notebook acceptance requires installed noneditable SDK")
    sdk_hashes = source_hashes(sdk.parent)
    workflow = PACKAGE / "tools/workflows/async_own_wait.py"
    workflow_hash = hashlib.sha256(workflow.read_bytes()).hexdigest()
    notebook = nbformat.v4.new_notebook(cells=[
        nbformat.v4.new_markdown_cell("# Owned native async Java notebook acceptance\nSynthetic234.5m, not historical replication."),
        nbformat.v4.new_code_cell(
            "import os,sys,json,hashlib\nfrom pathlib import Path\nimport klab_client,psutil\n"
            "state=Path(os.environ['KLAB_ASYNC_NOTEBOOK_STATE'])\n"
            "sys.path.insert(0,os.environ['KLAB_ASYNC_NOTEBOOK_TOOLS'])\n"
            "from workflows.async_own_wait import scenario\n"
            "sdk=Path(klab_client.__file__).resolve()\n"
            "identity={'interpreter':sys.executable,'sdk_file':str(sdk),'pid':os.getpid(),"
            "'created':psutil.Process().create_time(),'sdk_sha256':{p.relative_to(sdk.parent).as_posix():"
            "hashlib.sha256(p.read_bytes()).hexdigest() for p in sdk.parent.rglob('*.py')}}\n"
            "(state/'async-notebook/kernel-identity.json').write_text(json.dumps(identity))\n"
            "print('Exact installed kernel identity recorded')"),
        nbformat.v4.new_code_cell(
            "report=await scenario(state)\n"
            "assert report['passed'] and report['context_released'] and report['session_released']\n"
            "assert report['verified_cells']==20 and report['expected_value']==234.5 and report['units']=='m'\n"
            "assert report['local_wait_cancelled'] and report['other_wait_completed']\n"
            "assert report['remote_job_cancellations']==0 and report['scientific_submissions']==2\n"
            "print('Actual Java20cells and native async lifecycle verified')"),
        nbformat.v4.new_code_cell(
            "assert report['reader_close_preserved_work']\n"
            "assert report['foreign_context_attach_denial'] in ('AuthorizationError','MissingAssetError')\n"
            "assert report['async_saved_handle_restoration'].startswith('UNSUPPORTED')\n"
            "print('Owned scope cleanup confirmed; unsupported boundaries preserved')")])
    notebook.metadata.kernelspec = {"name": "klab-owned-async", "display_name": "k.LAB owned async", "language": "python"}
    env = {key: value for key, value in os.environ.items() if not key.startswith("KLAB_") and key != "PYTHONPATH"}
    env.update(KLAB_ASYNC_NOTEBOOK_STATE=str(state), KLAB_ASYNC_NOTEBOOK_TOOLS=str(PACKAGE / "tools"))
    execute_private_kernel(notebook, directory, env)
    identity = json.loads((directory / "kernel-identity.json").read_text())
    require(Path(identity["interpreter"]).resolve() == Path(sys.executable).resolve() and
        Path(identity["sdk_file"]).resolve() == sdk and identity["sdk_sha256"] == sdk_hashes,
        "Kernel interpreter/installed source identity differs")
    require(identity["pid"] != os.getpid(), "Notebook did not execute in a separate process")
    try:
        process = psutil.Process(identity["pid"])
        require(process.create_time() != identity["created"], "Owned notebook kernel process remains")
    except psutil.NoSuchProcess:
        pass
    require(source_hashes(sdk.parent) == sdk_hashes and hashlib.sha256(workflow.read_bytes()).hexdigest() == workflow_hash,
        "Installed SDK or workflow changed during notebook execution")
    check_outputs(notebook, [config["token"], config["other_token"]])
    nbformat.write(notebook, directory / "executed.ipynb")
    science = json.loads((state / "async-own-wait.json").read_text())
    report = {"passed": True, "kernel_identity": identity, "kernel_closed": True, "top_level_await": True,
        "executed_code_cells": 3, "output_credentials_checked": True, "workflow_sha256": workflow_hash,
        "notebook_sha256": hashlib.sha256((directory / "executed.ipynb").read_bytes()).hexdigest(),
        "science": science, "historical_replication": "NOT_CLAIMED", "browser_gui_support": "NOT_TESTED"}
    (state / "async-notebook.json").write_text(json.dumps(report, indent=2))
    return report
