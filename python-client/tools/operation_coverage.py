"""Attribute measured Python callable-body lines to exact source bytes.

Development-only report, NOT invocation counting, route/workflow correctness or
native acceptance. Reject mixed package reports, duplicate modules and byte drift.
"""
import argparse
import ast
from collections import Counter
import hashlib
import json
from pathlib import Path
from typing import cast

from operation_inventory import inventory


def report(source, coverage):
    measured = {}
    for filename, data in coverage["files"].items():
        normalized = filename.replace("\\", "/")
        if "klab_client/" not in normalized:
            raise ValueError("Only SDK-only klab_client coverage can be attributed")
        relative = normalized.split("klab_client/", 1)[1]
        if ".." in Path(relative).parts:
            raise ValueError("Ambiguous or unsafe measured module identity")
        relative = Path(relative).as_posix()
        if relative in measured: raise ValueError("Ambiguous or unsafe measured module identity")
        original = source / relative
        measured_path = Path(filename)
        if not original.is_file() or not measured_path.is_file() or original.read_bytes() != measured_path.read_bytes():
            raise ValueError("Measured SDK bytes differ or are unavailable; no source-only fallback")
        measured[relative] = data
    result = inventory(source)
    trees = {relative: ast.parse((source / relative).read_text(encoding="utf-8")) for relative in {row["path"] for row in result["operations"]}}
    operations = []
    identities = set()
    for row in result["operations"]:
        identity = (row["path"], row["owner"], row["operation"])
        if identity in identities: raise ValueError("Duplicate callable identity cannot be attributed unambiguously")
        identities.add(identity)
        tree = trees[row["path"]]
        parent = tree if row["owner"] is None else next(node for node in tree.body if isinstance(node, ast.ClassDef) and node.name == row["owner"])
        node = next(node for node in parent.body if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == row["operation"])
        # ast.parse on supported Python supplies end positions; no caller AST.
        lines = set(range(node.body[0].lineno, cast(int, node.end_lineno) + 1))
        shared_declaration = node.lineno in lines
        lines.discard(node.lineno)  # One-line def coverage can be import-only.
        # A nested callback's executed body is not proof the outer body reached
        # every line; count its declaration, not its independently executed body.
        for child in ast.walk(node):
            if child is not node and isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
                lines.difference_update(range(child.lineno + 1, cast(int, child.end_lineno) + 1))
        data = measured.get(row["path"])
        executed = lines & set(data["executed_lines"]) if data else set()
        missing = lines & set(data["missing_lines"]) if data else set()
        excluded = lines & set(data["excluded_lines"]) if data else set()
        executed.difference_update(excluded)
        missing.difference_update(excluded)
        if row["classification"] == "ABSTRACT_DECLARATION": status = "ABSTRACT_NOT_IMPLEMENTATION"
        elif data is None: status = "REPORT_MODULE_MISSING"
        elif shared_declaration and not lines: status = "SHARED_DECLARATION_LINE_NO_BODY_EXECUTION_INFERENCE"
        elif not executed and not missing: status = "NO_MEASURABLE_BODY"
        elif not executed: status = "NO_BODY_LINES_MEASURED_EXECUTED"
        elif row["classification"] == "UNCONDITIONALLY_UNSUPPORTED": status = "REFUSAL_BODY_MEASURED_NOT_SUPPORTED"
        elif missing: status = "PARTIAL_BODY_LINE_MEASUREMENT"
        else: status = "BODY_LINES_MEASURED_EXECUTED"
        operations.append({**row, "measurement": status, "executed_body_lines": sorted(executed),
            "missing_body_lines": sorted(missing), "excluded_body_lines": sorted(excluded),
            "shared_declaration_body_line": node.lineno if shared_declaration else None})
    return {"version": 1, "evidence": "EXACT_BYTES_SDK_BODY_LINE_MEASUREMENT_NOT_FUNCTIONAL_OR_NATIVE_ACCEPTANCE",
        "scope": "Public source-callables from operation_inventory; excludes declaration-only coverage and nested bodies from outer ranges. Does not count invocations, branch coverage per callable, routes, permissions, scientific correctness or workflows. Exclusions remain explicit; missing reports never imply execution.",
        "source_module_sha256": {relative: hashlib.sha256((source / relative).read_bytes()).hexdigest() for relative in sorted(trees)},
        "measurement_counts": dict(sorted(Counter(row["measurement"] for row in operations).items())), "operations": operations}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("coverage", type=Path)
    parser.add_argument("--source", type=Path, default=Path(__file__).resolve().parents[1] / "src/klab_client")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    value = report(args.source, json.loads(args.coverage.read_text(encoding="utf-8")))
    value["coverage_report_sha256"] = hashlib.sha256(args.coverage.read_bytes()).hexdigest()
    args.output.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(value["measurement_counts"], sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
