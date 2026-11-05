import json

import pytest

from workflows.async_handle_restoration import save_report


def test_async_restoration_evidence_save_retains_primary_on_secondary_io_failure(tmp_path, monkeypatch):
    def fail(*args, **kwargs):
        raise OSError("private diagnostics must not enter notes")
    monkeypatch.setattr(type(tmp_path), "write_text", fail)
    original = AssertionError("Original restoration failure")
    save_report(tmp_path, {"passed": False}, original)
    assert original.__notes__ == ["Async restoration evidence save failed: OSError"]
    with pytest.raises(OSError):
        save_report(tmp_path, {"passed": True})


def test_successful_async_restoration_evidence_still_requires_persisted_report(tmp_path):
    report = {"passed": True, "context_released": True}
    save_report(tmp_path, report)
    assert json.loads((tmp_path / "async-handle-restoration.json").read_text()) == report
