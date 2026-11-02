"""Deterministic research-tool checks; no network or third-party research execution."""
import hashlib
import io
import zipfile

import pytest

import pal_g0_artifacts
from pal_g0_artifacts import inventory, main


def fixture(name="source.kim", content=b"unexecuted source"):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr(name, content)
    raw = buffer.getvalue()
    # Windows' ZIP writer normalizes backslashes. Restore the malicious name in
    # both directory headers so this tests the reader, not writer normalization.
    if "\\" in name:
        raw = raw.replace(name.replace("\\", "/").encode(), name.encode())
    pin = {"record": "test", "size": len(raw), "sha256": hashlib.sha256(raw).hexdigest(),
           "md5": hashlib.md5(raw).hexdigest()}
    return raw, pin


def test_inventory_retains_bytes_identity_without_scientific_acceptance():
    raw, pin = fixture()
    result = inventory(raw, pin)
    assert result["members"] == [{"path": "source.kim", "size": 17,
        "sha256": hashlib.sha256(b"unexecuted source").hexdigest()}]
    assert result["research_code_executed"] is False
    assert result["scientific_acceptance"] is False


@pytest.mark.parametrize("name", ["../outside", "/absolute", "C:/absolute", "a\\b"])
def test_rejects_unsafe_members(name):
    raw, pin = fixture(name)
    with pytest.raises(ValueError, match="Unsafe"):
        inventory(raw, pin)


@pytest.mark.parametrize("field", ["size", "sha256", "md5"])
def test_rejects_changed_archive_identity(field):
    raw, pin = fixture()
    pin[field] = 0 if field == "size" else "0" * 64
    with pytest.raises(ValueError, match="immutable"):
        inventory(raw, pin)


def test_output_preflight_does_not_read_or_replace_existing_destination(tmp_path):
    with pytest.raises(SystemExit):
        main(["--archive", "pal", "--input", str(tmp_path / "absent.zip"),
              "--output", str(tmp_path)])
    assert not list(tmp_path.iterdir())


@pytest.mark.parametrize("budget", ["MAX_MEMBER", "MAX_EXPANDED"])
def test_rejects_uncompressed_budget_overflow(monkeypatch, budget):
    raw, pin = fixture()
    monkeypatch.setattr(pal_g0_artifacts, budget, 1)
    with pytest.raises(ValueError, match="budget"):
        inventory(raw, pin)
