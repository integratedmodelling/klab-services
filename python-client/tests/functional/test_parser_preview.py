"""SourcePreviews must exercise the real maintained Resources parsers."""
import pytest

from workflows.parser_preview import run

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_source_previews_use_actual_resources_parser_and_reject_invalid_input(functional_state):
    report = run(functional_state)
    assert report["passed"] and len(report["records"]) == 4
    assert all(row["valid_source"] and row["invalid_rejected"] for row in report["records"])
    assert report["scientific_execution"] == "NOT_RUN"
