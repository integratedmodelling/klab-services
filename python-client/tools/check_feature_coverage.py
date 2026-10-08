"""Fail on uncovered new-module code or uncovered decisions touching changed code."""
import argparse
import json
from pathlib import Path
import re
import subprocess


def changed_lines(diff):
    changed, path, line = {}, None, 0
    for text in diff.splitlines():
        if text.startswith("+++ b/python-client/src/"):
            path = text[len("+++ b/python-client/"):]
            changed.setdefault(path, set())
        elif text.startswith("+++ "):
            path = None
        elif text.startswith("@@"):
            line = int(re.search(r"\+(\d+)", text)[1])
        elif path and text.startswith("+"):
            changed[path].add(line)
            line += 1
        elif path and not text.startswith("-"):
            line += 1
    return changed


def evaluate(report, diff, new_modules):
    files = {path.replace("\\", "/"): data for path, data in report["files"].items()}
    errors = []
    for path in new_modules:
        data = files.get(path)
        if data is None or not data["summary"]["num_statements"]:
            errors.append(f"{path}: not measured")
        elif data["missing_lines"] or data["missing_branches"] or data["excluded_lines"]:
            errors.append(f"{path}: new modules require unexcluded 100% lines and branches")
    for path, lines in changed_lines(diff).items():
        data = files.get(path)
        if data is None:
            errors.append(f"{path}: changed production module not measured")
            continue
        missing = sorted(lines.intersection(data["missing_lines"]))
        arcs = [arc for arc in data["missing_branches"] if lines.intersection(arc)]
        excluded = sorted(lines.intersection(data["excluded_lines"]))
        if missing or arcs or excluded:
            errors.append(f"{path}: missing changed lines={missing}, arcs={arcs}, excluded={excluded}")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--base", required=True)
    parser.add_argument("--new-module", action="append", required=True)
    args = parser.parse_args()
    diff = subprocess.run(["git", "diff", "--no-ext-diff", "--unified=0", args.base, "--", "python-client/src"],
        cwd=Path(__file__).resolve().parents[2], capture_output=True, text=True, check=True).stdout
    errors = evaluate(json.loads(args.report.read_text()), diff, args.new_module)
    if errors:
        print("\n".join(errors))
        return 1
    print("New-module 100% line/branch and changed-code coverage gates passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
