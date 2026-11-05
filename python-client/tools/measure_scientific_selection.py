"""Bounded same-source offline comparison; never runs Java or claims L4 acceptance."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

from select_scientific_tests import select


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True, help="Fresh evidence directory, never overwrites")
    args = parser.parse_args()
    args.output.mkdir(parents=False, exist_ok=False)
    package = Path(__file__).resolve().parents[1]
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=package, text=True).strip()
    cases = {"baseline": select([], full=True),
        "documentation": select(["python-client/docs/typed-observe.md"]),
        "decoder": select(["python-client/src/klab_client/dto.py"]),
        "java-dto": select(["klab.core.api/src/main/java/org/integratedmodelling/klab/api/ServicesAPI.java"])}
    measurements = []
    for name, plan in cases.items():
        xml = args.output.resolve() / (name + ".xml")
        command = plan["python_command"] + ["--junitxml", str(xml)]
        started = time.perf_counter()
        with (args.output / (name + ".log")).open("xb") as log:
            completed = subprocess.run(command, cwd=package, stdout=log, stderr=log, timeout=300)
        seconds = time.perf_counter() - started
        suites = ET.parse(xml).getroot().findall(".//testsuite")
        count = {key: sum(int(s.get(key, "0")) for s in suites) for key in ("tests", "failures", "errors", "skipped")}
        measurements.append({"case": name, "source_sha": head, "python": sys.executable,
            "wall_seconds": seconds, "command": command, "exit_code": completed.returncode,
            "counts": count, "selection": plan, "java_families_executed": 0,
            "real_java_scenarios_executed": 0})
        (args.output / "measurements.json").write_text(json.dumps({"schema_version": 1,
            "comparison": "Same source/interpreter, serial runs, no coverage in either baseline or subset; mandatory coverage CI unaffected",
            "results": measurements}, sort_keys=True, indent=2), encoding="utf-8")
    print(json.dumps([{k: r[k] for k in ("case", "wall_seconds", "counts", "exit_code")} for r in measurements]))
    return int(any(r["exit_code"] for r in measurements))


if __name__ == "__main__":
    raise SystemExit(main())
