"""Execute and record the actual PR walkthrough notebooks on an owned TEST stack.

Optional tools only: JupyterLab, nbclient, nbconvert, Matplotlib, pandas and
Playwright/Chromium. Never serves private stack state or embeds credentials.
"""
import argparse
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import time
import urllib.request


@dataclass(frozen=True)
class Cell:
    kind: str
    source: str
    restart: bool = False


def read_cells(path):
    """Read normal percent-format Python examples; no invented execution output."""
    cells = []
    for part in re.split(r"^# %%", Path(path).read_text(encoding="utf-8"), flags=re.M)[1:]:
        header, _, text = part.partition("\n")
        if "[markdown]" in header:
            text = "\n".join(line[2:] if line.startswith("# ") else line[1:] if line.startswith("#") else line
                             for line in text.strip().splitlines())
            cells.append(Cell("markdown", text))
        else:
            compile(text.strip(), str(path), "exec")
            cells.append(Cell("code", text.strip(), "restart" in header))
    if not cells or not any(cell.kind == "code" for cell in cells):
        raise ValueError("A demo must contain executable notebook cells")
    return cells


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def make_notebook(cells):
    import nbformat
    nodes = []
    for cell in cells:
        if cell.kind == "markdown":
            nodes.append(nbformat.v4.new_markdown_cell(cell.source))
        else:
            nodes.append(nbformat.v4.new_code_cell(cell.source, metadata={"restart_before": cell.restart}))
    return nbformat.v4.new_notebook(cells=nodes, metadata={
        "kernelspec": {"name": "klab-demo", "display_name": "Python (k.LAB demo)", "language": "python"},
        "language_info": {"name": "python"}})


def execute_notebook(notebook, directory):
    """A fresh kernel for each restart segment, including all numerical assertions."""
    import nbformat
    from nbclient import NotebookClient
    segments, segment = [], []
    for cell in notebook.cells:
        if cell.metadata.get("restart_before") and segment:
            segments.append(segment)
            segment = []
        segment.append(cell)
    if segment:
        segments.append(segment)
    executed = []
    for segment in segments:
        chunk = nbformat.v4.new_notebook(cells=segment, metadata=notebook.metadata)
        try:
            NotebookClient(chunk, timeout=180, kernel_name="klab-demo",
                           resources={"metadata": {"path": str(directory)}}).execute()
        except BaseException:
            nbformat.write(chunk, directory / "failed-segment.ipynb")
            raise
        executed.extend(chunk.cells)
    notebook.cells = executed
    return notebook


def cleanup_sessions(directory, state):
    """Only sessions recorded by this notebook; never broadly enumerate/delete work."""
    sys.path.insert(0, str(Path(__file__).parent))
    from workflows.connection import connected_client
    from klab_client import Session
    from klab_client.errors import MissingAssetError
    path = directory / "owned-sessions.json"
    if not path.exists():
        return
    for item in json.loads(path.read_text()):
        id = item["id"] if isinstance(item, dict) else item
        collaborator = isinstance(item, dict) and item["principal"] == "other-scientist"
        with connected_client(state, "token", collaborator=collaborator) as client:
            try:
                # Explicit release in the notebook may already have removed it.
                if Session(client, id).release() is not True:
                    raise RuntimeError("Owned notebook session release was not acknowledged")
            except MissingAssetError:
                pass


def collect_secrets(state):
    config = json.loads((state / "scientist.json").read_text())
    authority = json.loads((state / "fixture-authority.json").read_text())
    return [config["token"].encode(), config["other_token"].encode(), authority["enrollment_secret"].encode()]


def assert_public(files, secrets):
    for path in files:
        contents = path.read_bytes()
        if any(secret in contents for secret in secrets):
            raise ValueError("Private credential detected in selected public output")
        if path.suffix == ".ipynb":
            notebook = json.loads(contents)
            if any(output.get("output_type") == "error" for cell in notebook.get("cells", [])
                   for output in cell.get("outputs", [])):
                raise ValueError("Notebook execution error cannot be published as a demo")


def record_browser(browser, *, base_url, private_token, directory, cells, output, ffmpeg):
    """Record real Jupyter UI execution, with a real kernel restart when specified."""
    context = browser.new_context(viewport={"width": 1440, "height": 1000},
        record_video_dir=str(directory / "capture"), record_video_size={"width": 1440, "height": 1000})
    page = context.new_page()
    begun = time.monotonic()
    video_path = None
    try:
        page.goto(base_url + "/doc/tree/" + directory.name + "/demo.ipynb?token=" + private_token)
        page.locator(".jp-MarkdownOutput h1").wait_for(timeout=90000)
        page.wait_for_function("document.body.innerText.includes(' | Idle')", timeout=90000)
        no = page.get_by_text("No", exact=True)
        if no.count():
            no.first.click()
        sidebar = page.locator(".jp-SideBar.jp-mod-left .lm-TabBar-tab.lm-mod-current")
        if sidebar.count():
            sidebar.first.click()
        page.add_style_tag(content=".cm-editor,.cm-content,.cm-line{font-size:18px!important;line-height:1.5!important}"
                           ".jp-MarkdownOutput{font-size:18px!important}.jp-Notebook{padding-bottom:200px}")
        ready = time.monotonic() - begun
        page.wait_for_timeout(2200)  # Deliberate reading time, not fabricated service activity.
        code_index = 0
        for cell in cells:
            if cell.kind != "code":
                continue
            if cell.restart:
                # The UI action visibly shuts down the old Python process.
                page.get_by_text("Kernel", exact=True).first.click()
                page.get_by_text(re.compile(r"^Restart Kernel[….]*$")).first.click()
                dialog = page.locator(".jp-Dialog")
                dialog.locator("button.jp-mod-accept").click()
                page.wait_for_function("document.body.innerText.includes(' | Idle')", timeout=90000)
            node = page.locator(".jp-CodeCell").nth(code_index)
            node.locator(".jp-InputArea-editor").click()
            node.scroll_into_view_if_needed()
            page.wait_for_timeout(min(5500, 1500 + 100 * len(cell.source.splitlines())))
            page.keyboard.press("Shift+Enter")
            # Wait for an execution count, not a canned pause or old preflight output.
            page.wait_for_function("i => {const c=document.querySelectorAll('.jp-CodeCell')[i];"
                "return /\\[\\d+\\]/.test(c.querySelector('.jp-InputPrompt').textContent)}", arg=code_index, timeout=180000)
            page.wait_for_function("document.body.innerText.includes(' | Idle')", timeout=180000)
            if node.locator(".jp-OutputArea-error").count():
                raise RuntimeError("Recorded notebook cell failed; inspect private workspace")
            outputs = node.locator(".jp-OutputArea-output")
            if outputs.count():
                outputs.first.scroll_into_view_if_needed()
                page.wait_for_timeout(3800)
                if outputs.count() > 1:
                    outputs.last.scroll_into_view_if_needed()
                    page.wait_for_timeout(2600)
            code_index += 1
        page.keyboard.press("Control+s")
        page.wait_for_timeout(1000)
        page.screenshot(path=str(output.with_suffix(".png")))
        video_path = page.video.path()
    finally:
        context.close()
    subprocess.run([ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-ss", str(ready),
        "-i", str(video_path), "-an", "-c:v", "libx264", "-crf", "21", "-pix_fmt", "yuv420p",
        "-movflags", "+faststart", str(output)], check=True, timeout=120)
    subprocess.run([ffmpeg, "-hide_banner", "-loglevel", "error", "-i", str(output), "-f", "null", "-"],
                   check=True, timeout=120)
    return {"file": output.name, "sha256": digest(output), "bytes": output.stat().st_size,
            "code_cells_executed": code_index, "kernel_restarts": sum(cell.restart for cell in cells),
            "decode_verified": True}


def run(args):
    import nbformat
    from nbconvert import HTMLExporter
    from playwright.sync_api import sync_playwright
    import imageio_ffmpeg
    import secrets

    root = Path(__file__).resolve().parents[1]
    state, output = args.state_dir.resolve(), args.output.resolve()
    if output.exists() or not (state / "scientist.json").is_file():
        raise ValueError("Requires running owned TEST state and a fresh output directory")
    output.mkdir(parents=True)
    work = state / "notebook-workspaces"
    work.mkdir()
    env = dict(os.environ, KLAB_NOTEBOOK_STATE=str(state))
    os.environ["KLAB_NOTEBOOK_STATE"] = str(state)
    subprocess.run([sys.executable, "-m", "ipykernel", "install", "--prefix", sys.prefix,
                    "--name", "klab-demo", "--display-name", "Python (k.LAB demo)"], check=True, timeout=30)
    report = {"passed": False, "presentation": "Actual Jupyter recordings; automated execution of visible code",
              "environment": "Owned local TEST authority and actual services; synthetic known-answer models",
              "editing": "Notebook loading trimmed; code execution and deliberate reading pauses retained",
              "notebooks": []}
    sources = sorted((root / "examples/notebooks").glob("[0-9][0-9]_*.py"))
    private = collect_secrets(state)
    server = None
    try:
        # First, execute every notebook and its assertions before recording anything.
        for source in sources:
            directory = work / (source.stem + "-check")
            directory.mkdir()
            shutil.copyfile(root / "tools/notebook_demos/notebook_setup.py", directory / "notebook_setup.py")
            notebook = make_notebook(read_cells(source))
            try:
                execute_notebook(notebook, directory)
                nbformat.write(notebook, directory / "executed.ipynb")
            except Exception as error:
                report["notebooks"].append({"name": source.stem, "source_sha256": digest(source),
                    "functional": "Failed", "error_type": type(error).__name__})
                print(source.stem, "functional notebook FAILED; inspect its private failed-segment.ipynb", flush=True)
                continue
            finally:
                cleanup_sessions(directory, state)
            print(source.stem, "functional notebook passed", flush=True)
            report["notebooks"].append({"name": source.stem, "source_sha256": digest(source),
                "functional": "Passed", "preflight_code_cells": sum(c.cell_type == "code" for c in notebook.cells),
                "preflight_plots": sum("image/png" in o.get("data", {}) for c in notebook.cells for o in c.get("outputs", []))})
        if len(report["notebooks"]) != 7 or any(row["functional"] != "Passed" for row in report["notebooks"]):
            raise RuntimeError("Notebook preflight incomplete/failed; no recordings published")
        if args.check_only:
            report["passed"] = True
            return report
        token = secrets.token_urlsafe(32)
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        base_url = f"http://127.0.0.1:{port}"
        with (state / "notebook-jupyter.log").open("wb") as log:
            server = subprocess.Popen([sys.executable, "-m", "jupyterlab", "--no-browser", "--ServerApp.ip=127.0.0.1",
                f"--ServerApp.port={port}", "--ServerApp.port_retries=0", "--ServerApp.root_dir=" + str(work),
                "--IdentityProvider.token=" + token, "--ServerApp.allow_remote_access=False"],
                env=env, stdout=log, stderr=subprocess.STDOUT)
        until = time.monotonic() + 60
        while True:
            try:
                with urllib.request.urlopen(base_url + "/api/status?token=" + token, timeout=2):
                    break
            except OSError:
                if time.monotonic() >= until or server.poll() is not None:
                    raise RuntimeError("Owned Jupyter server did not start")
                time.sleep(.2)
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch()
            try:
                for source, record in zip(sources, report["notebooks"]):
                    directory = work / source.stem
                    directory.mkdir()
                    shutil.copyfile(root / "tools/notebook_demos/notebook_setup.py", directory / "notebook_setup.py")
                    cells = read_cells(source)
                    nbformat.write(make_notebook(cells), directory / "demo.ipynb")
                    try:
                        record.update(record_browser(browser, base_url=base_url, private_token=token,
                            directory=directory, cells=cells, output=output / (source.stem + ".mp4"),
                            ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()))
                        notebook = nbformat.read(directory / "demo.ipynb", as_version=4)
                        # Shift+Enter on the last cell creates a new blank input in
                        # Jupyter. It is not an unexecuted step in the walkthrough.
                        code = [c for c in notebook.cells if c.cell_type == "code" and c.source.strip()]
                        expected = [c.source for c in cells if c.kind == "code"]
                        if ([c.source for c in code] != expected or not all(c.execution_count is not None
                                and not any(o.output_type == "error" for o in c.outputs) for c in code)):
                            raise RuntimeError("Recorded notebook did not save complete successful execution")
                        html, _ = HTMLExporter().from_notebook_node(notebook)
                        (output / (source.stem + ".html")).write_text(html, encoding="utf-8")
                        nbformat.write(notebook, output / (source.stem + ".ipynb"))
                        shutil.copyfile(source, output / source.name)
                        record["recording"] = "Passed"
                    finally:
                        cleanup_sessions(directory, state)
                    print(source.stem, "actual notebook recording passed", flush=True)
            finally:
                browser.close()
        # Public setup helper reads credentials privately; it contains no credential values.
        shutil.copyfile(root / "tools/notebook_demos/notebook_setup.py", output / "notebook_setup.py")
        assert_public(list(output.iterdir()), private)
        report["passed"] = True
        return report
    finally:
        if server is not None:
            try:
                # Graceful shutdown stops Jupyter-owned kernels, unlike killing the parent alone.
                request = urllib.request.Request(base_url + "/api/shutdown?token=" + token, data=b"{}", method="POST",
                    headers={"Authorization": "token " + token, "Content-Type": "application/json"})
                with urllib.request.urlopen(request, timeout=10):
                    pass
                server.wait(timeout=30)
            except (OSError, subprocess.TimeoutExpired):
                subprocess.run(["taskkill.exe", "/PID", str(server.pid), "/T", "/F"],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)
                server.wait(timeout=15)
        (output / "manifest.json").write_text(json.dumps(report, indent=2), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    result = run(args)
    print(json.dumps(result, indent=2))
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
