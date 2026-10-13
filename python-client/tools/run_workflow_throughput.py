"""Measure named functional scenarios; compatibility scripts remain supported."""
import argparse
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workflow", required=True, choices=("compute-and-analyze", "export-deliverables"))
    args, remaining = parser.parse_known_args()
    if args.workflow == "compute-and-analyze":
        from workflows.compute_and_analyze import main as run
    else:
        from workflows.export_deliverables import main as run
    sys.argv = [sys.argv[0], *remaining]
    return run()


if __name__ == "__main__":
    raise SystemExit(main())
