"""Deterministic advisory feedback selection; mandatory CI/coverage never reduced."""
import argparse
import json
from pathlib import PurePosixPath
import sys
import subprocess

SMOKE = ["tests/unit/test_foundation_contracts.py", "tests/unit/test_roadmap_acceptance_map.py",
         "tests/unit/test_roadmap_handoffs.py"]
NEGATIVE = ["tests/unit/test_connection_guards.py", "tests/unit/test_job_binding.py",
            "tests/unit/test_read_semantics_isolation.py"]
FAMILIES = {
    "documentation": ["tests/unit/test_roadmap_acceptance_map.py", "tests/unit/test_roadmap_handoffs.py"],
    "graph": ["tests/unit/test_graph.py", "tests/unit/test_graph_projection.py", "tests/unit/test_provenance.py"],
    "semantics": ["tests/unit/test_semantic_explorer.py", "tests/unit/test_async_semantic_explorer.py",
                  "tests/unit/test_request_plan_acceptance.py"],
}
CAPABILITIES = {"CAP-05": "semantics", "CAP-06": "semantics", "CAP-10": "graph", "CAP-11": "graph"}


def select(paths, capabilities=(), full=False):
    paths = sorted(set(paths))
    capabilities = sorted(set(capabilities))
    reasons, matches, broad = [], set(), full or not (paths or capabilities)
    java = False
    for path in paths:
        pure = PurePosixPath(path)
        if pure.is_absolute() or ".." in pure.parts or "\\" in path or ":" in path:
            broad = True
            reasons.append({"path": path, "rule": "unsafe/ambiguous path: broaden"})
        elif path.startswith("python-client/docs/") and path.endswith(".md"):
            matches.add("documentation")
            reasons.append({"path": path, "rule": "documentation-only feedback"})
        elif path in ("python-client/src/klab_client/graph.py", "python-client/src/klab_client/graph_projection.py"):
            matches.add("graph")
            reasons.append({"path": path, "rule": "bounded graph with lifecycle/security controls"})
        elif path == "python-client/src/klab_client/semantics.py":
            matches.add("semantics")
            reasons.append({"path": path, "rule": "semantic options/plans plus security controls"})
        else:
            broad = True
            java |= path.endswith(".java") or path.endswith("pom.xml") or path.startswith("klab.")
            reasons.append({"path": path, "rule": "shared/high-risk/unknown/selector/CI: broaden"})
    for capability in capabilities:
        if capability in CAPABILITIES:
            matches.add(CAPABILITIES[capability])
        else:
            broad = True
            reasons.append({"capability": capability, "rule": "unknown or cross-cutting capability: broaden"})
    if full:
        reasons.append({"rule": "explicit full validation"})
    if not paths and not capabilities:
        reasons.append({"rule": "no impact information: broaden"})
    suites = ["tests/unit", "tests/integration"] if broad else sorted(set(SMOKE + NEGATIVE +
        [test for family in matches for test in FAMILIES[family]]))
    scenarios = ["N", "S"] if broad else (["S"] if "semantics" in matches else [])
    return {"schema_version": 1, "changed_paths": paths, "declared_capabilities": capabilities,
        "mode": "broad" if broad else "focused", "rationale": reasons,
        "matched_families": sorted(matches), "included_python": suites,
        "excluded_families": [] if broad else [f for f in FAMILIES if f not in matches],
        "exclusion_reason": "Feedback only; mandatory complete CI/coverage still runs",
        "python_command": [sys.executable, "-m", "pytest", "-q", *suites],
        "python_status": "NOT_RUN", "required_l4_scenarios": scenarios,
        "l4_status": "NOT_RUN", "l4_reason": "Requires explicit isolated pinned Java stack, wheel, manifests and owned fresh states",
        "java_contract_validation_required": java,
        "java_full_suite_triggered": False,
        "java_reason": "Affected API/modules require existing Java gates and source-contract review, not automatic full Java campaign",
        "mandatory_ci_coverage_unchanged": True, "release_acceptance": False}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--changed", action="append", default=[])
    parser.add_argument("--capability", action="append", default=[])
    parser.add_argument("--full", action="store_true")
    parser.add_argument("--git-base", help="Explicit comparison commit; diff failure conservatively broadens")
    args = parser.parse_args(argv)
    if args.git_base:
        try:
            args.changed.extend(subprocess.check_output(["git", "diff", "--name-only", args.git_base, "HEAD"],
                                                       text=True, stderr=subprocess.PIPE).splitlines())
        except subprocess.CalledProcessError:
            args.full = True
    print(json.dumps(select(args.changed, args.capability, args.full), sort_keys=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
