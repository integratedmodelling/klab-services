"""Read retained owned TEST work in a fresh process using the installed SDK."""
import argparse
from pathlib import Path

from workflows.completed_recovery import recover


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state-dir", type=Path, required=True)
    parser.add_argument("--handles-dir", type=Path, required=True)
    args = parser.parse_args()
    recover(args.state_dir, args.handles_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
