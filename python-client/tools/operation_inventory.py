"""Reproducible source-callable inventory, not execution or backend acceptance.

Development-only AST inspection: never imports the production SDK, contacts a
service or treats a conditional unsupported guard as an absent whole operation.
"""
import argparse
import ast
from collections import Counter
import json
from pathlib import Path

PACKAGE = Path(__file__).resolve().parents[1]
OUTPUT = PACKAGE / "docs" / "operation-inventory.json"


def _unsupported(node):
    return (isinstance(node, ast.Raise) and isinstance(node.exc, ast.Call)
        and isinstance(node.exc.func, ast.Name) and node.exc.func.id == "UnsupportedOperationError")


def classify(node):
    decorators = [ast.unparse(value) for value in node.decorator_list]
    if "abstractmethod" in decorators:
        return "ABSTRACT_DECLARATION"
    # Strip only documentary/no-op/import statements. A branch or early return
    # means unsupported may be a guarded fallback, not an unconditional stub.
    body = [statement for statement in node.body if not isinstance(statement, (ast.Import, ast.ImportFrom, ast.Pass))
        and not (isinstance(statement, ast.Expr) and isinstance(statement.value, ast.Constant)
            and isinstance(statement.value.value, str))]
    if len(body) == 1 and _unsupported(body[0]):
        return "UNCONDITIONALLY_UNSUPPORTED"
    # Do not count nested functions' guards against the enclosing callable.
    def guarded(statements):
        for statement in statements:
            if isinstance(statement, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
                continue
            if _unsupported(statement):
                return True
            if guarded(list(ast.iter_child_nodes(statement))):
                return True
        return False
    return "IMPLEMENTED_WITH_UNSUPPORTED_GUARDS" if guarded(node.body) else "IMPLEMENTED_SOURCE_ONLY"


def inventory(source):
    records = []
    for path in sorted(source.rglob("*.py")):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        relative = path.relative_to(source).as_posix()
        for parent in [tree, *(node for node in tree.body if isinstance(node, ast.ClassDef))]:
            owner = None if parent is tree else parent.name
            for node in parent.body:
                if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and not node.name.startswith("_"):
                    records.append({"path": relative, "owner": owner, "operation": node.name,
                        "async": isinstance(node, ast.AsyncFunctionDef), "classification": classify(node)})
    return {"version": 1, "evidence": "READ_ONLY_PYTHON_SOURCE_NOT_EXECUTION_OR_NATIVE_ACCEPTANCE",
        "scope": "Public-named top-level functions and direct methods of top-level classes, including private facade classes. Inherited/generated methods, nested closures and aliases are not expanded; routes/permissions/semantic correctness require separate tests and authority.",
        "counts": dict(sorted(Counter(row["classification"] for row in records).items())), "operations": records}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Refuse stale committed source inventory")
    args = parser.parse_args(argv)
    rendered = json.dumps(inventory(PACKAGE / "src" / "klab_client"), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != rendered:
            raise ValueError("Operation inventory drift; regenerate from current SDK source")
    else:
        OUTPUT.write_text(rendered, encoding="utf-8")
    print(json.dumps(json.loads(rendered)["counts"], sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
