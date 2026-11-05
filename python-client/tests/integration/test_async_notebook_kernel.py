"""Real local kernel/await/cleanup smoke, NOT scientific acceptance."""
import os
import sys

import pytest


def test_exact_private_kernel_supports_top_level_await_and_stops(tmp_path):
    nbformat = pytest.importorskip("nbformat")
    pytest.importorskip("nbclient")
    import psutil
    from workflows.async_notebook import check_outputs, execute_private_kernel
    notebook = nbformat.v4.new_notebook(cells=[
        nbformat.v4.new_code_cell("import sys,os\nprint(sys.executable)\nprint(os.getpid())"),
        nbformat.v4.new_code_cell("import asyncio\nawait asyncio.sleep(0)\nprint('top-level await smoke only')"),
        nbformat.v4.new_code_cell("print('not scientific output')")])
    notebook.metadata.kernelspec = {"name": "klab-owned-async", "display_name": "owned", "language": "python"}
    execute_private_kernel(notebook, tmp_path, dict(os.environ))
    check_outputs(notebook, [])
    lines = notebook.cells[0].outputs[0].text.strip().splitlines()
    assert lines[0].lower() == sys.executable.lower()
    assert int(lines[1]) != os.getpid() and not psutil.pid_exists(int(lines[1]))
    assert not list(tmp_path.glob("private-kernel-*"))
