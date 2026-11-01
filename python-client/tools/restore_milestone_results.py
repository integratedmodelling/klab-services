"""Isolated original-result restoration worker. Never provisions or submits observations."""
import argparse
import os
from pathlib import Path
import sys

# -I intentionally removes the script directory; import only this checkout's tools.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from workflows.milestone import restore_in_process


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--batch", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        restore_in_process(args.fixture, args.batch, args.output,
            os.environ["KLAB_MILESTONE_PROFILE"], os.environ["KLAB_MILESTONE_CERTIFICATE"])
    except Exception as error:
        print("Restoration failed: " + type(error).__name__)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
