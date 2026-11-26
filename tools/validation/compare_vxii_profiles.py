"""Compare Runtime testcase reports and optional logs from a dedicated Resolver."""
import argparse
from bisect import bisect_left, bisect_right
from datetime import datetime
import json
from pathlib import Path
import re
import sys


def report(path):
    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    suite = payload["delegate"]
    tests = [node["delegate"] for node in payload["children"] if node["delegate"].get("type") == "test"]
    if (len(tests) != suite["tests"] or len({t["urn"] for t in tests}) != len(tests)
            or suite["testsFinished"] != suite["tests"]):
        raise ValueError(f"Incomplete or ambiguous per-action results in {path}")
    return suite, {test["urn"]: test for test in tests}


def no_model_jobs(paths, suite):
    if not paths:
        return None
    jobs = []
    matched = 0
    pattern = re.compile(r"^\[([^\]]+)\].* completed with resolution outcome (\w+)\s*$")
    for path in paths:
        with path.open(encoding="utf-8", errors="replace") as log:
            for line in log:
                match = pattern.match(line)
                if not match:
                    continue
                # The service log uses local time. Use the timezone of the service when running this tool.
                timestamp = round(datetime.fromisoformat(match[1]).timestamp() * 1000)
                if suite["start"] <= timestamp <= suite["end"]:
                    matched += 1
                    if match[2] == "NO_MODEL":
                        jobs.append(timestamp)
    if not matched:
        print("No Resolver outcomes in the timed interval. Check the log date and local timezone.", file=sys.stderr)
        return None
    return sorted(jobs)


def count(jobs, test):
    if jobs is None:
        return "-"
    return bisect_right(jobs, test["end"]) - bisect_left(jobs, test["start"])


def duration(record):
    return (record["end"] - record["start"]) / 1000


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("--before-log", action="append", type=Path, default=[])
    parser.add_argument("--after-log", action="append", type=Path, default=[])
    args = parser.parse_args()
    before, tests = report(args.before)
    after, other = report(args.after)
    if tests.keys() != other.keys():
        raise ValueError("Action sets differ: " + ", ".join(sorted(tests.keys() ^ other.keys())))
    jobs_a = no_model_jobs(args.before_log, before)
    jobs_b = no_model_jobs(args.after_log, after)
    print("| Action | Before (s) | After (s) | NO_MODEL before | NO_MODEL after | Passed assertions before / after |")
    print("| --- | ---: | ---: | ---: | ---: | ---: |")
    for name, a, b in [(name, test, other[name]) for name, test in tests.items()] + [("All", before, after)]:
        print(f"| {name} | {duration(a):.3f} | {duration(b):.3f} | {count(jobs_a, a)} | "
              f"{count(jobs_b, b)} | {a['assertionsPassed']} / {b['assertionsPassed']} |")
    if before["testsPassed"] != before["tests"] or after["testsPassed"] != after["tests"]:
        raise SystemExit("A testcase failed. The reports contain the per-action outcomes.")


if __name__ == "__main__":
    main()
