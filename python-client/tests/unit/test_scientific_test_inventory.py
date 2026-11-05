from pathlib import Path

from inventory_scientific_tests import inventory


def test_every_existing_python_module_has_an_explicit_boundary_and_selection():
    package = Path(__file__).resolve().parents[2]
    report = inventory(package)
    discovered = {p.relative_to(package).as_posix() for kind in ("unit", "integration", "functional")
                  for p in (package / "tests" / kind).glob("test_*.py")}
    assert {m["path"] for m in report["modules"]} == discovered
    assert len(report["coverage_ids"]) == 42
    for module in report["modules"]:
        assert module["selection"] == module["path"] and module["families"] and module["capabilities"]
        assert module["duration_seconds"] is None and module["status"] == "NOT_RUN"
        assert len(module["file_sha256"]) == 64
        if module["path"].startswith("tests/unit/"):
            assert module["tier"] == "L1"
