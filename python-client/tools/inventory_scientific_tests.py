"""Source-linked per-module expansion of the reviewed family matrix; no tests run."""
import argparse
import ast
import fnmatch
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET


def inventory(package, timing_junit=None, timing_source=None):
    matrix = json.loads((package / "tests/acceptance/scenarios/test-evidence-map.json").read_text())
    source = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=package, text=True).strip()
    modules = []
    timings = {}
    if timing_junit is not None:
        if timing_source is None or len(timing_source) != 40:
            raise ValueError("Exact timing-source SHA required with JUnit")
        for case in ET.parse(timing_junit).getroot().iter("testcase"):
            module = case.get("classname", "").replace(".", "/") + ".py"
            row = timings.setdefault(module, {"seconds": 0.0, "tests": 0, "failures": 0, "skipped": 0})
            row["seconds"] += float(case.get("time", "0"))
            row["tests"] += 1
            row["failures"] += int(case.find("failure") is not None or case.find("error") is not None)
            row["skipped"] += int(case.find("skipped") is not None)
    for pattern, defaults in matrix["family_defaults"].items():
        for path in sorted(package.glob(pattern)):
            relative = path.relative_to(package).as_posix()
            tree = ast.parse(path.read_text(encoding="utf-8"))
            families = [f for f in matrix["families"] if f["id"] != "packaging-harness"
                        and any(fnmatch.fnmatch(path.name, p) for p in f["patterns"])]
            if not families:
                families = [matrix["families"][-1]]
            origins = [o for o in matrix["independent_oracles"] if relative in o["files"]]
            modules.append({**defaults, "path": relative, "selection": relative,
                "source_sha": source, "file_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                "declared_test_functions": sum(isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef))
                    and n.name.startswith("test_") for n in ast.walk(tree)),
                "markers": ["live", "functional"] if "/functional/" in relative else ["offline-network-guard"],
                "families": [f["id"] for f in families],
                "capabilities": sorted({c for f in families for c in f["capabilities"]}),
                "critical_negatives": [f["critical_negatives"] for f in families],
                "oracle_exceptions": origins,
                "historical_status": "See accepted baseline receipts; no current execution inferred from AST inventory"})
            if relative in timings:
                blob = subprocess.check_output(["git", "hash-object", str(path)], cwd=package, text=True).strip()
                old = subprocess.check_output(["git", "rev-parse", timing_source + ":python-client/" + relative],
                                              cwd=package, text=True).strip()
                if blob == old:
                    measured = timings[relative]
                    modules[-1].update(duration_seconds=measured["seconds"],
                        duration_basis="Measured sum of JUnit case durations, excludes process/collection overhead",
                        status="FAIL" if measured["failures"] else "SKIPPED" if measured["skipped"] else "PASS",
                        measured_at_source=timing_source, measured_tests=measured["tests"],
                        timing_junit_sha256=hashlib.sha256(Path(timing_junit).read_bytes()).hexdigest())
    return {"schema_version": 1, "source_sha": source, "coverage_ids": matrix["coverage_ids"],
        "module_count": len(modules), "execution": "NOT_RUN: source discovery only",
        "duration_basis": "Per-module UNKNOWN until actual timed execution; no estimated duration passed as measured",
        "modules": modules}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--timing-junit", type=Path)
    parser.add_argument("--timing-source")
    args = parser.parse_args()
    print(json.dumps(inventory(args.package, args.timing_junit, args.timing_source), sort_keys=True, indent=2))
