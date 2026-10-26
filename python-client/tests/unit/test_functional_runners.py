"""Prevent suite routing and compatibility commands drifting from shared scenarios."""
import ast
from pathlib import Path

import pytest


def test_functional_runner_uses_short_tracebacks_to_omit_credential_arguments():
    from run_functional_acceptance import command
    assert "--tb=short" in command(["connected-semantic-readiness"])

from run_functional_acceptance import SUITES, command


@pytest.mark.parametrize("suite", tuple(SUITES))
def test_named_suite_selects_existing_functional_test(suite):
    location, *node = SUITES[suite].split("::")
    file = Path(__file__).parents[2] / location
    tree = ast.parse(file.read_text())
    assert file.parent.name == "functional"
    if node:
        assert any(isinstance(item, ast.FunctionDef) and item.name == node[0] for item in tree.body)
    assert location in command([suite]) or SUITES[suite] in command([suite])


def test_old_commands_use_the_same_scenarios_as_functional_acceptance():
    import geotiff_throughput
    import workflow_benchmark
    from workflows import compute_and_analyze, export_deliverables
    assert workflow_benchmark.sample is compute_and_analyze.sample
    assert workflow_benchmark.read_sample is compute_and_analyze.read_sample
    assert geotiff_throughput.run is export_deliverables.run
    assert geotiff_throughput.validate_dataset is export_deliverables.validate_dataset


def test_connected_suites_need_explicit_paths_not_an_owned_test_state(tmp_path, monkeypatch):
    import sys
    from types import SimpleNamespace
    import run_functional_acceptance as runner
    import run_local_acceptance
    profile, certificate = tmp_path / "profile.json", tmp_path / "private.cert"
    profile.write_text("{}")
    certificate.write_text("never sent by this offline routing test")
    calls = []
    def run(command, **kwargs):
        calls.append((command, kwargs["env"]))
        return 0
    monkeypatch.setattr(run_local_acceptance, "run_command", run)
    monkeypatch.setattr(runner.subprocess, "run", lambda command, **kwargs:
                        SimpleNamespace(returncode=run(command, **kwargs)))
    monkeypatch.setattr(sys, "argv", ["runner", "--suite", "connected-semantic-readiness",
        "--suite", "connected-unit-contract", "--profile", str(profile), "--certificate", str(certificate)])
    assert runner.main() == 0
    assert calls[0][1]["KLAB_CONNECTED_PROFILE"] == str(profile.resolve())
    assert calls[0][1]["KLAB_CONNECTED_CERTIFICATE"] == str(certificate.resolve())
    assert all("develop_compatibility.py" not in part for part in calls[0][0])


def test_connected_suites_do_not_silently_use_ambient_credentials(monkeypatch):
    import sys
    import run_functional_acceptance as runner
    monkeypatch.setenv("KLAB_CONNECTED_PROFILE", "ambient-profile.json")
    monkeypatch.setenv("KLAB_CONNECTED_CERTIFICATE", "ambient-cert")
    monkeypatch.setattr(sys, "argv", ["runner", "--suite", "connected-unit-contract"])
    with pytest.raises(SystemExit) as error:
        runner.main()
    assert error.value.code == 2


@pytest.mark.parametrize("suite", ["connected-context-recovery", "connected-tiny-compute"])
def test_connected_work_needs_explicit_authorization(suite, monkeypatch):
    import sys
    import run_functional_acceptance as runner
    monkeypatch.setenv("KLAB_CONNECTED_ALLOW_OWNED_WORK", "1")
    monkeypatch.setattr(sys, "argv", ["runner", "--suite", suite])
    with pytest.raises(SystemExit) as error:
        runner.main()
    assert error.value.code == 2


def test_connected_work_authorization_is_forwarded(tmp_path, monkeypatch):
    import sys
    from types import SimpleNamespace
    import run_functional_acceptance as runner
    import run_local_acceptance
    profile, certificate = tmp_path / "profile.json", tmp_path / "private.cert"
    profile.write_text("{}")
    certificate.write_text("offline stub")
    captured = []
    def run(command, **kwargs):
        captured.append(kwargs["env"])
        return 0
    monkeypatch.setattr(run_local_acceptance, "run_command", run)
    monkeypatch.setattr(runner.subprocess, "run", lambda command, **kwargs:
                        SimpleNamespace(returncode=run(command, **kwargs)))
    monkeypatch.setattr(sys, "argv", ["runner", "--suite", "connected-context-recovery",
        "--profile", str(profile), "--certificate", str(certificate), "--allow-owned-work"])
    assert runner.main() == 0
    assert captured[0]["KLAB_CONNECTED_ALLOW_OWNED_WORK"] == "1"


def test_read_only_connected_run_clears_ambient_mutation_authorization(tmp_path, monkeypatch):
    import sys
    from types import SimpleNamespace
    import run_functional_acceptance as runner
    import run_local_acceptance
    profile, certificate = tmp_path / "profile.json", tmp_path / "private.cert"
    profile.write_text("{}")
    certificate.write_text("offline stub")
    captured = []
    def run(command, **kwargs):
        captured.append(kwargs["env"])
        return 0
    monkeypatch.setattr(run_local_acceptance, "run_command", run)
    monkeypatch.setattr(runner.subprocess, "run", lambda command, **kwargs:
                        SimpleNamespace(returncode=run(command, **kwargs)))
    monkeypatch.setenv("KLAB_CONNECTED_ALLOW_OWNED_WORK", "1")
    monkeypatch.setattr(sys, "argv", ["runner", "--suite", "connected-onboarding",
        "--profile", str(profile), "--certificate", str(certificate)])
    assert runner.main() == 0
    assert captured[0]["KLAB_CONNECTED_ALLOW_OWNED_WORK"] == "0"


def test_do_not_mix_shared_deployment_and_owned_test_stack_suites(tmp_path, monkeypatch):
    import sys
    import run_functional_acceptance as runner
    (tmp_path / "scientist.json").write_text("{}")
    monkeypatch.setattr(sys, "argv", ["runner", "--suite", "connected-onboarding",
        "--suite", "develop-compatibility", "--state-dir", str(tmp_path)])
    with pytest.raises(SystemExit) as error:
        runner.main()
    assert error.value.code == 2
