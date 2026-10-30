"""Explicitly authorized single hosted F2 pilot; no retries, provisioning or shared administration."""
import argparse
import json
from pathlib import Path

from workflows.resource_inspection import run


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", type=Path, required=True)
    parser.add_argument("--certificate", type=Path, required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--allow-owned-work", action="store_true")
    args = parser.parse_args()
    if not args.allow_owned_work:
        parser.error("Explicit --allow-owned-work is required for this computation pilot")
    if not args.profile.is_file() or not args.certificate.is_file() or args.report.exists() or not args.report.parent.is_dir():
        parser.error("Use existing profile/certificate paths and a fresh report under an existing parent")
    # Reserve the output before touching services; never overwrite another report.
    with args.report.open("x", encoding="utf-8") as output:
        report = run(args.profile, args.certificate, args.model, allow_owned_work=True)
        json.dump(report, output, indent=2, sort_keys=True, allow_nan=False)
    print("F2 pilot: " + ("passed" if report["passed"] else "failed at " + report["stage"]))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
