"""Fail-closed milestone evidence validation and actual notebook execution/export.

No prerecorded charts or synthetic scientific outputs can satisfy these gates.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile

from workflows import milestone
from workflows.scientific_checks import require

REQUIRED_TESTS = {
    "test_connected_reasoner_publishes_accepted_semantic_knowledge",
    "test_connected_resolved_observable_preserves_requested_unit[m]",
    "test_connected_resolved_observable_preserves_requested_unit[mm]",
    "test_connected_tiny_terrestrial_region_completes",
    "test_discovery_and_model_inspection_use_existing_java_contracts",
    "test_recorded_execution_evidence_matches_computed_observation",
    "test_four_experiments_preserve_scientific_meaning",
    "test_parallel_experiments_preserve_identity_and_results",
    "test_partial_failure_retains_successes_and_recoverable_handles",
    "test_fresh_process_restores_original_experiment_results",
    "test_private_experiment_evidence_and_exports_enforce_access",
    "test_milestone_notebook_executes_and_matches_evidence",
}


def checked_file(root, relative, expected_hash):
    require(isinstance(relative, str) and relative and not Path(relative).is_absolute()
            and "\\" not in relative and ":" not in relative, "Artifact path must be portable and relative")
    path = (Path(root) / relative).resolve()
    require(path.is_relative_to(Path(root).resolve()) and path.is_file(), "Artifact escapes the run or is missing")
    require(path.stat().st_size <= 128 * 1024 * 1024 and milestone.digest(path.read_bytes()) == expected_hash,
            "Artifact hash/size differs from the recorded execution")
    return path


def allowed_artifact(name):
    if name in ("notebook/executed.ipynb", "notebook/notebook.html", "notebook/notebook-run.json"):
        return True
    return bool(re.fullmatch(r"(?:notebook/)?(?:sequential|parallel|partial|restored)/(?:batch\.json|run\.json|restoration\.json|[A-Za-z][A-Za-z0-9_-]{0,79}\.tif)", name))


def validate_acceptance(path, *, require_clean=False):
    data, _ = milestone.read_json(path)
    require(data.get("version") == 1 and data.get("kind") == "milestone-acceptance", "Unsupported acceptance manifest")
    require(data.get("identity") == milestone.source_identity(), "Candidate/installed package/environment differs from acceptance")
    require(not require_clean or data["identity"]["dirty"] is False, "PR-ready evidence requires a clean identified candidate")
    tests = data.get("tests", {})
    require(set(tests) == REQUIRED_TESTS and all(v == "passed" for v in tests.values()),
            "Required acceptance cases are missing, failed or skipped")
    require(data.get("passed") is True and isinstance(data.get("run_id"), str), "Acceptance did not pass")
    files = data.get("artifacts", {})
    require(isinstance(files, dict) and {"sequential/run.json", "parallel/run.json", "restored/restoration.json",
        "notebook/executed.ipynb", "notebook/notebook.html", "notebook/notebook-run.json"} <= files.keys(),
        "Required executed artifacts are missing")
    root = Path(path).parent
    for name, value in files.items():
        require(allowed_artifact(name), "Unrecognized artifact; never package arbitrary state files")
        checked_file(root, name, value)
    for name in ("sequential/run.json", "parallel/run.json"):
        report, _ = milestone.read_json(root / name)
        require(report["identity"] == data["identity"] and report["fixture_sha256"] == data["fixture_sha256"],
                "Batch candidate/fixture differs")
        require(report["matrix_verified"] is True and len(report["batch"]["outcomes"]) == 4,
                "Scientific matrix did not pass")
    sequential, _ = milestone.read_json(root / "sequential/run.json")
    restored, _ = milestone.read_json(root / "restored/restoration.json")
    require(restored["identity"] == data["identity"] and restored["fixture_sha256"] == data["fixture_sha256"],
            "Restoration candidate/fixture differs")
    require([v["observation_handle"] for v in restored["results"]] ==
            [v["result_handles"][-1] for v in sequential["batch"]["outcomes"]], "Restoration used different observations")
    notebook, _ = milestone.read_json(root / "notebook/notebook-run.json")
    require(notebook["identity"] == data["identity"] and notebook["fixture_sha256"] == data["fixture_sha256"]
            and notebook["matrix_verified"] is True and notebook["run_id"] != sequential["run_id"],
            "Notebook did not run its own verified candidate/fixture journey")
    executed, _ = milestone.read_json(root / "notebook/executed.ipynb", budget=64 * 1024 * 1024)
    code = [c for c in executed["cells"] if c["cell_type"] == "code"]
    require(code and all(type(c.get("execution_count")) is int and c["execution_count"] > 0
            and not any(o.get("output_type") == "error" for o in c.get("outputs", [])) for c in code),
            "Notebook cells were not actually executed successfully")
    return data


def execute_notebook(directory, *, fixture_path, profile_path, certificate_path):
    """Execute percent-source cells in a kernel using this exact Python interpreter."""
    import nbformat
    from nbclient import NotebookClient
    from nbconvert import HTMLExporter
    from jupyter_client import KernelManager
    from jupyter_client.kernelspec import KernelSpecManager
    from record_notebook_demos import make_notebook, read_cells
    identity = milestone.source_identity()
    directory = Path(directory).resolve()
    directory.mkdir(exist_ok=False)
    source = milestone.PACKAGE / "examples/notebooks/08_discover_compare_explain.py"
    notebook = make_notebook(read_cells(source))
    notebook.metadata.kernelspec = {"name": "klab-milestone", "display_name": "k.LAB milestone", "language": "python"}
    env = dict(os.environ, KLAB_MILESTONE_TOOLS=str(milestone.PACKAGE / "tools"),
        KLAB_MILESTONE_FIXTURE=str(Path(fixture_path).resolve()), KLAB_MILESTONE_PROFILE=str(Path(profile_path).resolve()),
        KLAB_MILESTONE_CERTIFICATE=str(Path(certificate_path).resolve()), KLAB_CONNECTED_ALLOW_OWNED_WORK="1")
    # The kernel specification is private runtime metadata, not a deliverable.
    with tempfile.TemporaryDirectory(prefix="milestone-kernel-", dir=directory) as temporary:
        kernel = Path(temporary) / "klab-milestone"
        kernel.mkdir()
        (kernel / "kernel.json").write_text(json.dumps({"argv": [sys.executable, "-m", "ipykernel_launcher", "-f", "{connection_file}"],
            "display_name": "k.LAB milestone", "language": "python"}))
        manager = KernelManager(kernel_name="klab-milestone", kernel_spec_manager=KernelSpecManager(kernel_dirs=[temporary]))
        try:
            NotebookClient(notebook, km=manager, timeout=900, resources={"metadata": {"path": str(directory)}}).execute(env=env)
        finally:
            if manager.has_kernel:
                manager.shutdown_kernel(now=True)
    require(milestone.source_identity() == identity, "Candidate changed during notebook execution")
    # Scan serialized outputs before writing a potentially public artifact.
    payload = json.loads(nbformat.writes(notebook))
    milestone.safe_json(payload)
    nbformat.write(notebook, directory / "executed.ipynb")
    html, _ = HTMLExporter().from_notebook_node(notebook)
    (directory / "notebook.html").write_text(html, encoding="utf-8")
    report, _ = milestone.read_json(directory / "notebook-run.json")
    require(report["identity"] == identity and report["matrix_verified"] is True, "Notebook journey did not validate its own real outputs")
    return report


def build(acceptance_path, throughput_path, output):
    output = Path(output)
    output.mkdir(exist_ok=False)
    try:
        acceptance = validate_acceptance(acceptance_path, require_clean=True)
        require(acceptance.get("deployment_verified") is True,
                "Running Java/component/worldview provenance has not been independently verified")
        throughput, _ = milestone.read_json(throughput_path)
        require(throughput.get("kind") == "milestone-throughput" and throughput.get("passed") is True
                and throughput["identity"] == acceptance["identity"]
                and throughput["acceptance_run_id"] == acceptance["run_id"], "Missing/stale/unverified scientific throughput")
        require(throughput.get("fixture_sha256") == acceptance["fixture_sha256"], "Throughput baseline fixture differs")
        configurations = {f"{size}-workers-{workers}" for size in ("correctness-grid", "larger-grid") for workers in (1, 4)}
        require(set(throughput.get("results", {})) == configurations, "Missing throughput configuration")
        for result in throughput["results"].values():
            require(result.get("passed") is True and result.get("attempted") == result.get("completed") == 20
                    and result.get("failed_or_unknown") == 0 and result.get("wall_seconds", 0) > 0,
                    "Throughput did not validate every measured experiment")
            require(abs(result["successful_experiments_per_minute"] - 1200 / result["wall_seconds"]) < 1e-8,
                    "Throughput rate differs from measured wall-clock denominator")
        root = Path(acceptance_path).parent
        for name, value in acceptance["artifacts"].items():
            # Only whitelisted run artifacts are copied, never whole state trees.
            source = checked_file(root, name, value)
            destination = output / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
            checked_file(output, name, value)
        milestone.write_json(output / "acceptance.json", acceptance)
        milestone.write_json(output / "throughput.json", throughput)
        milestone.write_json(output / "delivery.json", {"version": 1, "status": "ready-for-review",
            "candidate_sha": acceptance["identity"]["candidate_sha"], "acceptance_run_id": acceptance["run_id"],
            "artifacts": acceptance["artifacts"], "maintainer_acceptance": False})
        return 0
    except Exception as error:
        milestone.write_json(output / "delivery.json", {"version": 1, "status": "blocked", "error_type": type(error).__name__})
        return 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--acceptance", required=True, type=Path)
    parser.add_argument("--throughput", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    return build(args.acceptance, args.throughput, args.output)


if __name__ == "__main__":
    raise SystemExit(main())
