"""Source inventory guard does not confuse supported guarded operations with stubs."""
import ast
import importlib.util
import json
from pathlib import Path
import runpy
import sys

import pytest

PATH = Path(__file__).resolve().parents[2] / "tools" / "operation_inventory.py"
spec = importlib.util.spec_from_file_location("operation_inventory", PATH)
tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tool)


@pytest.mark.parametrize("body,expected", [
    ('raise UnsupportedOperationError("unknown")', "UNCONDITIONALLY_UNSUPPORTED"),
    ('"doc"\nfrom example import UnsupportedOperationError\nraise UnsupportedOperationError("unknown")', "UNCONDITIONALLY_UNSUPPORTED"),
    ('if supported: return 1\nraise UnsupportedOperationError("unknown")', "IMPLEMENTED_WITH_UNSUPPORTED_GUARDS"),
    ('if not supported: raise UnsupportedOperationError("unknown")\nreturn 1', "IMPLEMENTED_WITH_UNSUPPORTED_GUARDS"),
    ('def child():\n    raise UnsupportedOperationError("nested")\nreturn child', "IMPLEMENTED_SOURCE_ONLY"),
    ('return 1', "IMPLEMENTED_SOURCE_ONLY"),
    ('raise ValueError("not unsupported")', "IMPLEMENTED_SOURCE_ONLY")])
def test_classification_is_source_shape_only(body, expected):
    node = ast.parse("def public():\n" + "\n".join("    " + line for line in body.splitlines())).body[0]
    assert tool.classify(node) == expected


def test_abstract_and_async_declarations_are_explicit(tmp_path):
    (tmp_path / "sample.py").write_text('''
class Facade:
    @abstractmethod
    def declaration(self): ...
    async def public(self): return 1
    def _private(self): return 1
def public_helper(): return 1
def _private_helper(): return 1
''', encoding="utf-8")
    value = tool.inventory(tmp_path)
    assert len(value["operations"]) == 3
    assert value["counts"]["ABSTRACT_DECLARATION"] == 1
    assert next(row for row in value["operations"] if row["operation"] == "public")["async"]
    assert value["evidence"] == "READ_ONLY_PYTHON_SOURCE_NOT_EXECUTION_OR_NATIVE_ACCEPTANCE"


def test_live_inventory_remaining_encoder_wrappers_are_one_unsupported_operation():
    value = tool.inventory(tool.PACKAGE / "src" / "klab_client")
    unsupported = [row for row in value["operations"] if row["classification"] == "UNCONDITIONALLY_UNSUPPORTED"]
    assert len(unsupported) == 2
    assert {row["operation"] for row in unsupported} == {"encode_dataflow"}
    assert {row["async"] for row in unsupported} == {True, False}
    for name in ("decode_cell", "encode"):
        assert any(row["operation"] == name and row["classification"] == "IMPLEMENTED_WITH_UNSUPPORTED_GUARDS" for row in value["operations"])


def test_committed_inventory_and_drift_refusal(tmp_path, monkeypatch, capsys):
    assert tool.main(["--check"]) == 0
    assert json.loads(capsys.readouterr().out)["UNCONDITIONALLY_UNSUPPORTED"] == 2
    output = tmp_path / "inventory.json"
    monkeypatch.setattr(tool, "OUTPUT", output)
    with pytest.raises(ValueError, match="drift"): tool.main(["--check"])
    assert tool.main([]) == 0
    assert tool.main(["--check"]) == 0
    output.write_text("{}", encoding="utf-8")
    with pytest.raises(ValueError, match="drift"): tool.main(["--check"])


def test_inventory_cli_runs_without_sdk_import(monkeypatch):
    monkeypatch.setattr(sys, "argv", [str(PATH), "--check"])
    with pytest.raises(SystemExit) as status:
        runpy.run_path(str(PATH), run_name="__main__")
    assert status.value.code == 0
