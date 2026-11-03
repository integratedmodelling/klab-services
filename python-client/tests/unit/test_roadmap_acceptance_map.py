from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "docs/PYTHON_CLIENT_ROADMAP.md"
MAP = ROOT / "docs/PYTHON_CLIENT_ACCEPTANCE_MAP.md"


def test_every_structured_acceptance_item_has_exactly_one_map_row():
    text = SOURCE.read_text(encoding="utf-8")
    expected = {"P0", "P5", "R1", "R2"}
    phases = re.findall(r"### Phase ([0-5])  [^\n]+\n(.*?)(?=\n### |\n## |\Z)", text, re.DOTALL)
    assert len(phases) == 6
    for number, body in phases:
        if number in ("0", "5"):
            assert "**Acceptance:**" in body
        else:
            scenarios = re.findall(r"^(\d+)\. ", body, re.MULTILINE)
            assert scenarios == ["1", "2", "3", "4", "5"]
            expected.update(f"P{number}.{scenario}" for scenario in scenarios)
    release = text.split("### Continuous release gate\n", 1)[1].split("\n## ", 1)[0]
    assert len([paragraph for paragraph in release.strip().split("\n\n") if paragraph.strip()]) == 2
    actual = re.findall(r"^\| ((?:P[0-5](?:\.[1-5])?)|R[12]) \|", MAP.read_text(encoding="utf-8"), re.MULTILINE)
    assert len(actual) == len(set(actual)) == 24
    assert set(actual) == expected


def test_rows_keep_full_criteria_open_and_classify_internal_vs_other_work():
    text = MAP.read_text(encoding="utf-8")
    rows = re.findall(r"^\| (?:P[0-5](?:\.[1-5])?|R[12]) \|.*$", text, re.MULTILINE)
    for row in rows:
        assert "OPEN:" in row
        assert re.search(r"\[(?:[ITBDS],?)+\]", row)
        assert "#" in row or "GAP" in row
    assert "GAP: retained-job" in text
    assert "GAP: full authoring" in text
    assert "not external-only" in text
    assert "No new GitHub issues" in text
    assert "not automatically" in text


def test_all_relative_evidence_links_exist_and_original_map_pointer_resolves():
    for path in (MAP, SOURCE):
        for target in re.findall(r"\]\(([^)]+)\)", path.read_text(encoding="utf-8")):
            if not target.startswith(("https://", "http://", "#")):
                assert (path.parent / target.split("#", 1)[0]).resolve().is_file(), target
    assert "PYTHON_CLIENT_ACCEPTANCE_MAP.md" in SOURCE.read_text(encoding="utf-8")
