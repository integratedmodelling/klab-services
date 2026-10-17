"""Run named user-workflow acceptance against an explicitly prepared deployment."""
import argparse
import os
from pathlib import Path
import subprocess
import sys

SUITES = {
    "connected-semantic-readiness": "tests/functional/test_connected_semantics.py::test_connected_reasoner_publishes_accepted_semantic_knowledge",
    "connected-unit-contract": "tests/functional/test_connected_semantics.py::test_connected_resolved_observable_preserves_requested_unit",
    "semantic-readiness": "tests/functional/test_develop_compatibility.py::test_reasoner_publishes_accepted_semantic_knowledge",
    "develop-compatibility": "tests/functional/test_develop_compatibility.py::test_python_and_java_services_work_before_and_after_runtime_restart",
    "release-notebook": "tests/functional/test_scientific_analysis.py::test_release_notebook_journey_uses_connections_and_recovers_the_original_field",
    "scientific-analysis-provenance": "tests/functional/test_scientific_analysis.py",
    "connection-onboarding": "tests/functional/test_connection.py::test_connection_onboarding_and_fresh_process_recovery",
    "connection-recovery": "tests/functional/test_connection.py::test_connection_onboarding_and_fresh_process_recovery",
    "connection-access": "tests/functional/test_connection.py::test_connection_access",
    "connection-readiness": "tests/functional/test_connection.py::test_connection_readiness",
    "compute-and-analyze": "tests/functional/test_compute_and_analyze.py",
    "resume-saved-work": "tests/functional/test_resume_saved_work.py",
    "typed-compute": "tests/functional/test_typed_compute.py",
    "geojson-export": "tests/functional/test_export_deliverables.py::test_native_geojson_export_preserves_identity_and_known_geometry",
    "geotiff-export": "tests/functional/test_export_deliverables.py::test_native_geotiff_export_preserves_values_units_and_mask",
    "provider-reference": "tests/functional/test_provider_reference.py",
}

CONNECTED_SUITES = {"connected-semantic-readiness", "connected-unit-contract"}


def command(suites, report=None):
    arguments = [sys.executable, "-m", "pytest", "-o", "addopts=", "-o", "junit_family=xunit1",
                 "-m", "functional", "-s", *[SUITES[name] for name in suites]]
    if report is not None:
        arguments.append("--junitxml=" + str(report))
    return arguments


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", choices=tuple(SUITES), action="append", required=True)
    parser.add_argument("--state-dir", type=Path, help="Running isolated TEST state; provider-reference uses its own environment contract")
    parser.add_argument("--report", type=Path, help="JUnit XML destination")
    parser.add_argument("--profile", type=Path, help="Explicit trusted deployment profile for connected read-only suites")
    parser.add_argument("--certificate", type=Path, help="Private ENGINE certificate path for connected suites; never report its contents")
    args = parser.parse_args()
    if args.state_dir is None and any(name not in CONNECTED_SUITES | {"provider-reference"} for name in args.suite):
        parser.error("Owned TEST suites require an explicit --state-dir")
    connected = any(name in CONNECTED_SUITES for name in args.suite)
    if connected and (not args.profile or not args.certificate
                      or not args.profile.is_file() or not args.certificate.is_file()):
        parser.error("Connected suites require existing --profile and --certificate paths")
    if not connected and (args.profile or args.certificate):
        parser.error("--profile/--certificate only apply to connected read-only suites")
    if args.state_dir is not None and not (args.state_dir / "scientist.json").is_file():
        parser.error("State must contain the prepared scientist.json")
    if args.report is not None and (not args.report.parent.is_dir() or args.report.exists()):
        parser.error("Report needs an existing parent and a fresh destination")
    env = dict(os.environ)
    if connected:
        env["KLAB_CONNECTED_PROFILE"] = str(args.profile.resolve())
        env["KLAB_CONNECTED_CERTIFICATE"] = str(args.certificate.resolve())
    if args.state_dir:
        env["KLAB_FIXTURE_STATE_DIR"] = str(args.state_dir.resolve())
    try:
        if os.name == "nt":
            from run_local_acceptance import run_command
            return run_command(command(args.suite, args.report.resolve() if args.report else None),
                cwd=Path(__file__).resolve().parents[1], env=env, log=None, timeout=900)
        return subprocess.run(command(args.suite, args.report.resolve() if args.report else None),
            cwd=Path(__file__).resolve().parents[1], env=env, timeout=900).returncode
    except subprocess.TimeoutExpired:
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
