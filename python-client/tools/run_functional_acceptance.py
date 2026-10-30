"""Run named user-workflow acceptance against an explicitly prepared deployment."""
import argparse
import os
from pathlib import Path
import subprocess
import sys

SUITES = {
    "milestone-discovery": "tests/functional/test_discover_compare_explain.py::test_discovery_and_model_inspection_use_existing_java_contracts",
    "milestone-evidence": "tests/functional/test_discover_compare_explain.py::test_recorded_execution_evidence_matches_computed_observation",
    "milestone-science": "tests/functional/test_discover_compare_explain.py::test_four_experiments_preserve_scientific_meaning",
    "milestone-parallel": "tests/functional/test_discover_compare_explain.py::test_parallel_experiments_preserve_identity_and_results",
    "milestone-failure": "tests/functional/test_discover_compare_explain.py::test_partial_failure_retains_successes_and_recoverable_handles",
    "milestone-restore": "tests/functional/test_discover_compare_explain.py::test_fresh_process_restores_original_experiment_results",
    "milestone-permissions": "tests/functional/test_discover_compare_explain.py::test_private_experiment_evidence_and_exports_enforce_access",
    "milestone-notebook": "tests/functional/test_discover_compare_explain.py::test_milestone_notebook_executes_and_matches_evidence",
    "connected-resource-inspection": "tests/functional/test_resource_inspection.py::test_existing_executable_model_links_inspection_to_recorded_computation",
    "connected-semantic-readiness": "tests/functional/test_connected_semantics.py::test_connected_reasoner_publishes_accepted_semantic_knowledge",
    "connected-unit-contract": "tests/functional/test_connected_semantics.py::test_connected_resolved_observable_preserves_requested_unit",
    "connected-onboarding": "tests/functional/test_connected_workflows.py::test_connected_onboarding_and_profile_roundtrip",
    "connected-credential-renewal": "tests/functional/test_connected_workflows.py::test_connected_certificate_renewal_preserves_identity",
    "connected-context-recovery": "tests/functional/test_connected_workflows.py::test_connected_context_recovers_in_a_fresh_process",
    "connected-tiny-compute": "tests/functional/test_connected_workflows.py::test_connected_tiny_terrestrial_region_completes",
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
    "execution-evidence": "tests/functional/test_execution_evidence.py",
    "semantic-explorer": "tests/functional/test_semantic_explorer.py",
}

MILESTONE_SUITES = {name for name in SUITES if name.startswith("milestone-")}
CONNECTED_WORK_SUITES = {"connected-context-recovery", "connected-tiny-compute", "connected-resource-inspection"} | (MILESTONE_SUITES - {"milestone-discovery"})
CONNECTED_SUITES = {"connected-semantic-readiness", "connected-unit-contract", "connected-onboarding",
                    "connected-credential-renewal", "milestone-discovery"} | CONNECTED_WORK_SUITES


def command(suites, report=None):
    # Long tracebacks include function arguments, including credentials during bootstrap.
    arguments = [sys.executable, "-m", "pytest", "--tb=short", "-o", "addopts=", "-o", "junit_family=xunit1",
                 "-m", "functional", "-s", *[SUITES[name] for name in suites]]
    if report is not None:
        arguments.append("--junitxml=" + str(report))
    return arguments


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", choices=tuple(SUITES), action="append", required=True)
    parser.add_argument("--state-dir", type=Path, help="Running isolated TEST state; provider-reference uses its own environment contract")
    parser.add_argument("--report", type=Path, help="JUnit XML destination")
    parser.add_argument("--profile", type=Path, help="Explicit trusted deployment profile for connected suites")
    parser.add_argument("--certificate", type=Path, help="Private ENGINE certificate path for connected suites; never report its contents")
    parser.add_argument("--milestone-fixture", type=Path, help="Independent four-cell fixture with deployment provenance")
    parser.add_argument("--output-dir", type=Path, help="Fresh external milestone output directory")
    parser.add_argument("--other-certificate", type=Path, help="Second real identity for milestone permission checks")
    parser.add_argument("--allow-owned-work", action="store_true",
                        help="Authorize connected suites to initialize peers and create/release their own session/context and tiny Job")
    args = parser.parse_args()
    milestone = any(name in MILESTONE_SUITES for name in args.suite)
    if milestone:
        if not args.milestone_fixture or not args.milestone_fixture.is_file() or not args.output_dir or args.output_dir.exists():
            parser.error("Milestone suites require an existing --milestone-fixture and fresh --output-dir")
    elif args.milestone_fixture or args.output_dir or args.other_certificate:
        parser.error("Milestone input options only apply to milestone suites")
    if "milestone-permissions" in args.suite and (not args.other_certificate or not args.other_certificate.is_file()):
        parser.error("Milestone permissions require an existing --other-certificate")
    if args.state_dir is None and any(name not in CONNECTED_SUITES | {"provider-reference"} for name in args.suite):
        parser.error("Owned TEST suites require an explicit --state-dir")
    connected = any(name in CONNECTED_SUITES for name in args.suite)
    owned = any(name not in CONNECTED_SUITES | {"provider-reference"} for name in args.suite)
    if connected and owned:
        parser.error("Run connected deployment suites separately from owned TEST-stack suites")
    work = any(name in CONNECTED_WORK_SUITES for name in args.suite)
    if work and not args.allow_owned_work:
        parser.error("Connected work suites require explicit --allow-owned-work")
    if args.allow_owned_work and not work:
        parser.error("--allow-owned-work only applies to connected work suites")
    if connected and (not args.profile or not args.certificate
                      or not args.profile.is_file() or not args.certificate.is_file()):
        parser.error("Connected suites require existing --profile and --certificate paths")
    if not connected and (args.profile or args.certificate):
        parser.error("--profile/--certificate only apply to connected suites")
    if args.state_dir is not None and not (args.state_dir / "scientist.json").is_file():
        parser.error("State must contain the prepared scientist.json")
    if args.report is not None and (not args.report.parent.is_dir() or args.report.exists()):
        parser.error("Report needs an existing parent and a fresh destination")
    env = dict(os.environ)
    for name in ("FIXTURE", "PROFILE", "CERTIFICATE", "OTHER_CERTIFICATE", "OUTPUT"):
        env.pop("KLAB_MILESTONE_" + name, None)
    if milestone:
        env.update(KLAB_MILESTONE_FIXTURE=str(args.milestone_fixture.resolve()),
                   KLAB_MILESTONE_OUTPUT=str(args.output_dir.resolve()),
                   KLAB_MILESTONE_PROFILE=str(args.profile.resolve()) if args.profile else "",
                   KLAB_MILESTONE_CERTIFICATE=str(args.certificate.resolve()) if args.certificate else "")
        if args.other_certificate:
            env["KLAB_MILESTONE_OTHER_CERTIFICATE"] = str(args.other_certificate.resolve())
    if connected:
        env["KLAB_CONNECTED_PROFILE"] = str(args.profile.resolve())
        env["KLAB_CONNECTED_CERTIFICATE"] = str(args.certificate.resolve())
        env["KLAB_CONNECTED_ALLOW_OWNED_WORK"] = "1" if work else "0"
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
