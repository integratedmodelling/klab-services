"""Offline guardrails for the shipped suite, not live Java semantic evidence."""
import ast
import importlib.util
import json
from pathlib import Path

import pytest

from klab_client.errors import ConfigurationError


PATH = Path(__file__).parents[1] / "functional/test_semantic_explorer.py"
SPEC = importlib.util.spec_from_file_location("semantic_functional_under_test", PATH)
SUITE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SUITE)


def test_semantic_suite_requires_an_explicit_fixture(tmp_path):
    with pytest.raises(ConfigurationError, match="semantic-explorer.json"):
        SUITE.semantic_fixture.__wrapped__(tmp_path)


@pytest.mark.parametrize("field", ["authority", "query", "code", "identity_urn",
                                 "documentation_media_type", "parent", "child"])
def test_semantic_suite_does_not_invent_missing_expectations(tmp_path, field):
    fixture = dict.fromkeys(["authority", "query", "code", "identity_urn",
                             "documentation_media_type", "parent", "child"], "explicit")
    del fixture[field]
    (tmp_path / "semantic-explorer.json").write_text(json.dumps(fixture))
    with pytest.raises(ConfigurationError, match="explicit"):
        SUITE.semantic_fixture.__wrapped__(tmp_path)


def test_semantic_suite_does_not_submit_compute_or_fetch_provider_content():
    tree = ast.parse(PATH.read_text())
    tests = [node for node in tree.body
             if isinstance(node, ast.FunctionDef) and node.name.startswith("test_")]
    calls = {node.func.attr for test in tests for node in ast.walk(test)
             if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute)}
    assert {"search_authority", "select", "documentation", "resolve_candidate",
            "parents", "children", "is_a", "satisfiable"} <= calls
    assert not calls & {"create_session", "create_context", "observe", "submit",
                        "release", "fetch_data", "get", "post", "request"}
