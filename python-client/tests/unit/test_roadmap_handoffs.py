"""Keep issue handoffs executable and honest about failed current Java verification."""
import ast
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[3]
HANDOFFS = [ROOT / "notes" / f"issue-{number}-agent-handoff.md" for number in (89, 125)]


def test_handoffs_pin_source_and_disclose_the_actual_repeat_failure():
    for path in HANDOFFS:
        text = path.read_text(encoding="utf-8")
        assert "e09a7de7cac6b48526fd8b7ac72879abb36a77a3" in text
        assert "EXTENT" in text and "ATTRIBUTE" in text
        assert "db1a58cc9a3ab8f3f56db1855b9cc3812edc352154d377883031558c23db88af" in text
        assert "not" in text.lower() and "external" in text.lower()
        assert "temporary folder" in text
        assert "LanguageAdapter.java" in text


def test_every_selected_java_test_in_a_command_exists_in_the_checkout():
    selections = []
    for path in HANDOFFS:
        text = path.read_text(encoding="utf-8")
        selections.extend(re.findall(r"-Dtest=([A-Za-z0-9_,]+)", text))
    assert len(selections) >= 3
    for selection in selections:
        for name in selection.split(","):
            assert list(ROOT.glob(f"*/src/test/java/**/{name}.java")), name


def test_python_reproduction_blocks_compile_without_running_mutations():
    blocks = []
    for path in HANDOFFS:
        blocks.extend(re.findall(r"```python\n(.*?)```", path.read_text(encoding="utf-8"), re.DOTALL))
    assert blocks
    for block in blocks:
        tree = ast.parse(block)
        assert "assert not scratch.exists()" in block
        assert "write_bytes" in block  # Do not use platform text redirection for Java source.
        for node in ast.walk(tree):
            if isinstance(node, ast.List):
                arguments = [item.value for item in node.elts if isinstance(item, ast.Constant)]
                assert not any(value in arguments for value in ("reset", "--hard", "clean"))


def test_verification_never_promotes_old_java_or_mock_broker_evidence():
    report = json.loads((ROOT / "notes/roadmap-handoff-verification-20261103.json").read_text())
    assert report["issue89_selected_java_command"] == "FAIL_CORE_SERVICES_COMPILE_BEFORE_ITS_TESTS"
    assert report["issue125_selected_java_command"] == "FIVE_COMMON_TESTS_PASS_THEN_CORE_SERVICES_COMPILE_FAIL"
    assert report["issue125_python_preview_tests"] == "45_PASS"
    assert report["requires_type_actual_constants"] == ["IDENTITY", "REALM", "AUTHORITY"]
    assert len(report["language_artifacts"]) == 5
    assert all(row["historical_sha256"] != row["current_sha256"] for row in report["language_artifacts"])
    for key in ("artifact_replacement_cause_proven", "correct_supported_pair_proven",
                "maven_cache_overwritten_or_historical_closures_combined",
                "scientific_semantics_changed", "services_or_persistent_agents_launched"):
        assert report[key] is False
    assert report["scientific_jobs"] == 0


def test_current_documents_link_to_accurate_handoffs():
    for relative in ("docs/PYTHON_CLIENT_ROADMAP.md", "docs/PYTHON_CLIENT_FEDERATION_COMPATIBILITY.md",
                     "python-client/docs/agent-lifecycle-contract.md"):
        path = ROOT / relative
        text = path.read_text(encoding="utf-8")
        links = re.findall(r"\]\(([^)]+issue-(?:89|125)-agent-handoff\.md)\)", text)
        assert links, relative
        for link in links:
            assert (path.parent / link).resolve().is_file()


def test_agent_gap_is_explicitly_client_work_not_a_fabricated_java_defect():
    text = HANDOFFS[1].read_text(encoding="utf-8")
    assert "#111 is **MERGED**" in text
    assert "not a reproduced java defect" in text.lower()
    assert "## Work the client agent can do now" in text
    assert "not yet implemented/accepted" in text
    assert "mocked AMQP" in text
