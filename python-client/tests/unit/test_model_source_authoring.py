from types import SimpleNamespace
import json

import pytest

from workflows.model_source_authoring import MODEL_CLASS, model_statement, save_report, verify_model


def test_model_identity_comes_from_java_statement_not_name_reconstruction():
    model = {"@CLASS": MODEL_CLASS, "urn": "owned.namespace.actual canonical model"}
    assert model_statement({"urn": "owned.namespace", "statements": [model]}, "owned.namespace") is model


@pytest.mark.parametrize("payload", [{}, {"urn": "other"}, {"urn": "owned.namespace", "statements": []},
    {"urn": "owned.namespace", "statements": [{"@CLASS": MODEL_CLASS, "urn": "foreign.model"}]},
    {"urn": "owned.namespace", "statements": [{"@CLASS": MODEL_CLASS, "urn": "owned.namespace.m"}] * 2}])
def test_missing_ambiguous_or_foreign_model_statements_do_not_pass(payload):
    with pytest.raises(AssertionError):
        model_statement(payload, "owned.namespace")


def spec(**changes):
    return SimpleNamespace(**(dict(urn="owned.namespace.m", namespace="owned.namespace", project_name="owned-project",
        selection="advertised candidate; not evidence of Resolver selection or execution",
        dependency_completeness="not a dependency closure") | changes))


def test_advertised_model_is_not_mistaken_for_selection_or_dependency_closure():
    verify_model(spec(), "owned.namespace.m", "owned.namespace", "owned-project")


@pytest.mark.parametrize("changes", [{"urn": "other"}, {"namespace": "other"}, {"project_name": "other"},
    {"selection": "executed"}, {"dependency_completeness": "complete"}])
def test_foreign_or_overclaimed_model_inspection_is_not_accepted(changes):
    with pytest.raises(AssertionError):
        verify_model(spec(**changes), "owned.namespace.m", "owned.namespace", "owned-project")


def test_primary_evidence_failure_is_retained_without_private_secondary_diagnostics(tmp_path, monkeypatch):
    def fail(*args, **kwargs):
        raise OSError("private diagnostics")
    monkeypatch.setattr(type(tmp_path), "write_text", fail)
    original = AssertionError("primary")
    save_report(tmp_path / "report", {}, original)
    assert original.__notes__ == ["Model authoring evidence save failed: OSError"]
    with pytest.raises(OSError):
        save_report(tmp_path / "report", {})


def test_successful_report_is_actually_persisted(tmp_path):
    save_report(tmp_path / "report", {"passed": True})
    assert json.loads((tmp_path / "report").read_text()) == {"passed": True}
