"""Actual local kernel/HTML smoke; NOT a scientific notebook acceptance run."""
import json
import sys

import pytest


@pytest.mark.parametrize("client_stops_kernel", [False, True])
def test_exact_interpreter_notebook_execution_and_html(tmp_path, monkeypatch, client_stops_kernel):
    pytest.importorskip("nbclient")
    from workflows import milestone
    from build_milestone_deliverable import execute_notebook
    package = tmp_path / "smoke-package"
    source = package / "examples/notebooks/08_discover_compare_explain.py"
    source.parent.mkdir(parents=True)
    identity = {"smoke_only": True}
    if client_stops_kernel:
        from nbclient import NotebookClient
        original_execute = NotebookClient.execute
        def execute(self, **kwargs):
            result = original_execute(self, **kwargs)
            self.km.shutdown_kernel(now=True)
            return result
        monkeypatch.setattr(NotebookClient, "execute", execute)
    source.write_text('# %%\nimport json,sys\nfrom pathlib import Path\n'
        'print("local execution smoke, not scientific output")\n'
        'Path("interpreter.txt").write_text(sys.executable)\n'
        'Path("notebook-run.json").write_text(json.dumps({"identity":{"smoke_only":True},"matrix_verified":True}))\n')
    monkeypatch.setattr(milestone, "PACKAGE", package)
    monkeypatch.setattr(milestone, "source_identity", lambda: identity)
    output = tmp_path / "output"
    result = execute_notebook(output, fixture_path=tmp_path / "no-fixture", profile_path=tmp_path / "no-profile",
                              certificate_path=tmp_path / "no-certificate")
    assert result["identity"] == identity
    assert (output / "interpreter.txt").read_text().lower() == sys.executable.lower()
    assert (output / "notebook.html").is_file()
    notebook = json.loads((output / "executed.ipynb").read_text())
    assert notebook["cells"][0]["execution_count"] == 1
