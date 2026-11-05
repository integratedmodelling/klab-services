from select_scientific_tests import NEGATIVE, SMOKE, select

import pytest


def test_docs_feedback_keeps_required_smoke_and_negative_controls():
    report = select(["python-client/docs/typed-observe.md"])
    assert report["mode"] == "focused"
    assert set(SMOKE + NEGATIVE) <= set(report["included_python"])
    assert report["mandatory_ci_coverage_unchanged"] and not report["required_l4_scenarios"]


@pytest.mark.parametrize("path", ["python-client/src/klab_client/client.py", "python-client/src/klab_client/dto.py",
    "python-client/src/klab_client/_async_transport.py", "python-client/src/klab_client/analysis.py",
    "python-client/tools/select_scientific_tests.py", ".github/workflows/python-client.yml",
    "unknown/file", "../unsafe", "C:/absolute", "python-client\\ambiguous"])
def test_high_risk_or_unknown_always_broadens(path):
    report = select([path])
    assert report["mode"] == "broad" and report["included_python"] == ["tests/unit", "tests/integration"]
    assert report["required_l4_scenarios"] == ["N", "S"]
    assert not report["java_full_suite_triggered"]


def test_java_dto_selects_contract_validation_and_broad_python_not_full_java():
    report = select(["klab.core.api/src/main/java/org/integratedmodelling/klab/api/data/Observation.java"])
    assert report["mode"] == "broad" and report["java_contract_validation_required"]
    assert not report["java_full_suite_triggered"]


def test_declared_capabilities_cannot_narrow_high_risk_paths():
    assert select(["unknown"], ["CAP-10"])["mode"] == "broad"
    assert select([], ["CAP-99"])["mode"] == "broad"
    assert select([], ["CAP-10"])["mode"] == "focused"


def test_selector_is_deterministic_and_explicit_full_cannot_narrow():
    paths = ["python-client/src/klab_client/semantics.py", "python-client/docs/typed-observe.md"]
    assert select(paths) == select(list(reversed(paths)) + paths)
    assert select(paths, full=True)["mode"] == select([])["mode"] == "broad"
    assert select(paths)["required_l4_scenarios"] == ["S"]
