"""Notebook demo inputs are actual Python examples; failed/private outputs stay private."""
import json
from pathlib import Path
import sys
from types import SimpleNamespace
from contextlib import contextmanager

import pytest

from record_notebook_demos import assert_public, cleanup_sessions, execute_notebook, read_cells


def test_percent_notebook_preserves_code_markdown_and_real_restart(tmp_path):
    path = tmp_path / "example.py"
    path.write_text("# %% [markdown]\n# # Begin\n# Run Python.\n# %%\nvalue = 7\n# %% restart\nassert 'value' not in globals()\n")
    cells = read_cells(path)
    assert cells[0].kind == "markdown" and cells[0].source == "# Begin\nRun Python."
    assert cells[1].source == "value = 7" and not cells[1].restart
    assert cells[2].restart


@pytest.mark.parametrize("source", ["# Just commentary", "# %% [markdown]\n# No code"])
def test_empty_or_nonexecutable_notebook_is_not_a_demo(tmp_path, source):
    path = tmp_path / "empty.py"
    path.write_text(source)
    with pytest.raises(ValueError, match="executable"):
        read_cells(path)


def test_invalid_python_rejected_before_service_work(tmp_path):
    path = tmp_path / "broken.py"
    path.write_text("# %%\nif broken syntax\n")
    with pytest.raises(SyntaxError):
        read_cells(path)


def test_all_seven_walkthroughs_have_executable_code_and_no_placeholders():
    root = Path(__file__).parents[2] / "examples/notebooks"
    # The original release walkthrough uses its owned TEST-state setup. Milestone
    # notebook 08 has a separate explicit fixture/deployment runner and tests.
    paths = sorted(root.glob("0[1-7]_*.py"))
    assert len(paths) == 7
    for path in paths:
        cells = read_cells(path)
        assert cells[0].kind == "markdown" and cells[0].source.startswith("# ")
        code = "\n".join(c.source for c in cells if c.kind == "code")
        assert "..." not in code and "assert " in code
    assert sum(c.restart for p in paths for c in read_cells(p)) == 1


@pytest.mark.parametrize("suffix", [".html", ".ipynb", ".mp4"])
def test_private_credential_cannot_be_published(tmp_path, suffix):
    path = tmp_path / ("output" + suffix)
    path.write_bytes(b"prefix privately-issued-token suffix")
    with pytest.raises(ValueError, match="credential"):
        assert_public([path], [b"privately-issued-token"])


def test_failed_notebook_output_cannot_be_published(tmp_path):
    path = tmp_path / "failed.ipynb"
    path.write_text(json.dumps({"cells": [{"outputs": [{"output_type": "error", "ename": "AssertionError"}]}]}, separators=(",", ":")))
    with pytest.raises(ValueError, match="execution error"):
        assert_public([path], [])


def test_successful_notebook_and_plot_are_allowed(tmp_path):
    path = tmp_path / "okay.ipynb"
    path.write_text(json.dumps({"cells": [{"outputs": [{"output_type": "display_data", "data": {"text/plain": "42"}}]}]}))
    assert_public([path], [b"never-published-secret"])


def test_restart_segments_are_executed_in_separate_kernel_clients(monkeypatch, tmp_path):
    clients = []
    class Client:
        def __init__(self, notebook, **kwargs):
            self.notebook = notebook
            clients.append(self)

        def execute(self):
            for cell in self.notebook.cells:
                cell.executed_by = len(clients)

    fake_format = SimpleNamespace(v4=SimpleNamespace(new_notebook=lambda **kwargs: SimpleNamespace(**kwargs)))
    monkeypatch.setitem(sys.modules, "nbformat", fake_format)
    monkeypatch.setitem(sys.modules, "nbclient", SimpleNamespace(NotebookClient=Client))
    cells = [SimpleNamespace(metadata={}), SimpleNamespace(metadata={"restart_before": True}),
             SimpleNamespace(metadata={})]
    notebook = SimpleNamespace(cells=cells, metadata={})
    result = execute_notebook(notebook, tmp_path)
    assert len(clients) == 2
    assert [cell.executed_by for cell in result.cells] == [1, 2, 2]


def test_cell_failure_is_preserved_for_private_diagnosis_not_converted_to_success(monkeypatch, tmp_path):
    saved = []
    class Client:
        def __init__(self, *args, **kwargs):
            pass

        def execute(self):
            raise AssertionError("A real cell assertion failed")

    fake_format = SimpleNamespace(v4=SimpleNamespace(new_notebook=lambda **kwargs: SimpleNamespace(**kwargs)),
                                  write=lambda notebook, path: saved.append(path))
    monkeypatch.setitem(sys.modules, "nbformat", fake_format)
    monkeypatch.setitem(sys.modules, "nbclient", SimpleNamespace(NotebookClient=Client))
    notebook = SimpleNamespace(cells=[SimpleNamespace(metadata={})], metadata={})
    with pytest.raises(AssertionError, match="real cell"):
        execute_notebook(notebook, tmp_path)
    assert saved == [tmp_path / "failed-segment.ipynb"]


@pytest.mark.parametrize("acknowledgement", [True, False, "absent", "denied"])
def test_owned_session_cleanup_requires_confirmation(tmp_path, monkeypatch, acknowledgement):
    import klab_client
    import workflows.connection
    from klab_client.errors import AuthorizationError, MissingAssetError
    sessions = [{"id": "owner-session", "principal": "python-scientist"},
                {"id": "guest-session", "principal": "other-scientist"}, "legacy-session"]
    (tmp_path / "owned-sessions.json").write_text(json.dumps(sessions))
    connections, releases = [], []
    @contextmanager
    def connected(state, provider, *, collaborator):
        connections.append(collaborator)
        yield SimpleNamespace()
    class Session:
        def __init__(self, client, id):
            self.id = id

        def release(self):
            releases.append(self.id)
            if acknowledgement == "absent":
                raise MissingAssetError("Already removed")
            if acknowledgement == "denied":
                raise AuthorizationError("Not permitted")
            return acknowledgement
    monkeypatch.setattr(workflows.connection, "connected_client", connected)
    monkeypatch.setattr(klab_client, "Session", Session)
    if acknowledgement is False:
        with pytest.raises(RuntimeError, match="release"):
            cleanup_sessions(tmp_path, tmp_path)
    elif acknowledgement == "denied":
        with pytest.raises(AuthorizationError):
            cleanup_sessions(tmp_path, tmp_path)
    else:
        cleanup_sessions(tmp_path, tmp_path)
        assert connections == [False, True, False]
        assert releases == ["owner-session", "guest-session", "legacy-session"]


def test_no_owned_session_registry_needs_no_remote_cleanup(tmp_path):
    cleanup_sessions(tmp_path, tmp_path)
