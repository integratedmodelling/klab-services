"""Thin named-suite adapter; all safety/credential gates remain in the existing runner."""
import sys
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET
from uuid import uuid4
from run_functional_acceptance import main as run
from workflows import milestone


def main():
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--milestone-fixture", required=True, type=Path)
    args, _ = parser.parse_known_args()
    identity = milestone.source_identity()
    fixture = milestone.load_fixture(args.milestone_fixture)
    # Selecting individual suites remains possible through the existing runner.
    sys.argv = [sys.argv[0], "--suite", "milestone-compatibility-units", "--suite", "milestone-compatibility-compute",
        "--suite", "milestone-discovery", "--suite", "milestone-evidence",
        "--suite", "milestone-science", "--suite", "milestone-parallel", "--suite", "milestone-failure",
        "--suite", "milestone-restore", "--suite", "milestone-permissions", "--suite", "milestone-notebook", *sys.argv[1:]]
    status = run()
    tests = {}
    if args.report.is_file():
        # Bounded XML without entity declarations; raw stdout is never packaged.
        raw = args.report.read_bytes()
        if len(raw) > 16 * 1024 * 1024 or b"<!DOCTYPE" in raw.upper():
            raise ValueError("Invalid acceptance XML")
        root = ET.fromstring(raw)
        for case in root.iter("testcase"):
            name = case.get("name")
            if name in tests:
                raise ValueError("Duplicate acceptance case")
            tests[name] = "failed" if list(case) and any(case.find(v) is not None for v in ("failure", "error", "skipped")) else "passed"
    from build_milestone_deliverable import REQUIRED_TESTS
    artifacts = {}
    for path in args.output_dir.rglob("*"):
        if path.is_file() and path.suffix in (".json", ".tif", ".html", ".ipynb") and not path.is_symlink():
            artifacts[path.relative_to(args.output_dir).as_posix()] = milestone.digest(path.read_bytes())
    passed = status == 0 and set(tests) == REQUIRED_TESTS and all(v == "passed" for v in tests.values())
    require_identity = milestone.source_identity() == identity
    passed = passed and require_identity
    args.output_dir.mkdir(exist_ok=True)
    milestone.write_json(args.output_dir / "acceptance.json", {"version": 1, "kind": "milestone-acceptance",
        "run_id": uuid4().hex, "identity": identity, "fixture_sha256": fixture.sha256,
        "deployment": fixture.deployment, "reference": fixture.reference, "passed": passed,
        "deployment_verified": False,
        "deployment_note": "Supplied identities are declarations; running binary/worldview/component attestation remains required",
        "tests": tests, "artifacts": artifacts})
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
