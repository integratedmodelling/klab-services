"""Keep common-code gate90%; report OS-specific containment separately, no exclusions."""
import json
from pathlib import Path
import sys


def main():
    coverage = json.loads(Path(sys.argv[1]).read_text())
    common = [row["summary"] for path, row in coverage["files"].items() if not path.replace("\\", "/").endswith("/containment.py")]
    total = sum(row["num_statements"] + row["num_branches"] for row in common)
    covered = sum(row["covered_lines"] + row["covered_branches"] for row in common)
    assert total and 100 * covered / total >= 90, "Common worker coverage below90%"
    print(f"Common worker coverage: {100 * covered / total:.2f}%; OS-specific containment remains in full report/80% aggregate gate")


if __name__ == "__main__":
    main()
