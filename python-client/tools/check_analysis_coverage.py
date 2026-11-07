"""Scientific adapter changed-code gate; other feature modules keep their own CI gates."""
import argparse
import json
from pathlib import Path
import subprocess

from check_feature_coverage import evaluate


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--base", required=True)
    args = parser.parse_args()
    base = "HEAD^" if not args.base or set(args.base) == {"0"} else args.base
    root = Path(__file__).resolve().parents[2]
    diff = subprocess.run(["git", "diff", "--no-ext-diff", "--unified=0", base, "--",
        "python-client/src/klab_client/analysis.py", "python-client/src/klab_client/__init__.py"],
        cwd=root, capture_output=True, text=True, check=True).stdout
    errors = evaluate(json.loads(args.report.read_text()), diff, [])
    if errors:
        print("\n".join(errors))
        return 1
    print("Scientific adapter changed lines/arcs unexcluded coverage PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
