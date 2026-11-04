import copy
import json
from pathlib import Path
import runpy
import sys
from types import SimpleNamespace

import pytest

import prepare_runtime_fixture as module


@pytest.fixture
def fixture(tmp_path):
    source = tmp_path / "source"
    source.mkdir()
    files = {"imod/agency.kwv": b"process Decision within Agent;\r\n// within Agent;\r\n",
             "imod/life.kwv": b"structural relationship SocialConnection;\r\n",
             "imod/earth.kwv": b"functional relationship StreamConnection;\r\n",
             "python.fixture/model.kim": b"model Elevation in m set to [100 + 93/4];\r\n"}
    for name, body in files.items():
        target = source / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(body)
    changes = []
    for name, old, new in [("imod/agency.kwv", "process Decision within Agent;", "process Decision of Agent;"),
                           ("imod/life.kwv", "structural relationship", "structural bond"),
                           ("imod/earth.kwv", "functional relationship", "functional directed relationship")]:
        changes.append({"path": name, "input_sha256": module._sha(files[name]),
                        "output_sha256": module._sha(files[name].replace(old.encode(), new.encode())),
                        "replacements": [{"old": old, "new": new}]})
    contract = {"version": 2, "backend_core_ontology": {"version_iri": "fixture-core", "sha256": "fixture-hash"},
                "input_files": len(files), "input_bytes": sum(map(len, files.values())),
                "input_tree_sha256": module._tree_hash(files), "changes": changes,
                "oracle": {"expected_decimal": "123.25", "cells": 20,
                           "unchanged_files": {"python.fixture/model.kim": module._sha(files["python.fixture/model.kim"])}}}
    return source, tmp_path / "new", contract, files


def test_new_copy_preserves_sources_and_oracle_and_never_claims_admission(fixture):
    source, destination, contract, files = fixture
    receipt = module._prepare(source, destination, contract)
    assert receipt["status"] == "PREPARED_NOT_SEMANTICALLY_ADMITTED"
    assert receipt["semantic_admission"] == "UNKNOWN"
    assert receipt["services_started"] == receipt["scientific_jobs"] == 0
    assert not receipt["production_worldview_migration"]
    assert receipt["source_unchanged"]
    assert len(receipt["changed_files"]) == 3 and receipt["unchanged_files"] == 1
    assert (destination / "python.fixture/model.kim").read_bytes() == files["python.fixture/model.kim"]
    assert module._collect(source, len(files)) == files
    assert json.loads((destination / module.RECEIPT).read_text()) == receipt
    with pytest.raises(ValueError, match="must be new"):
        module._prepare(source, destination, contract)


def test_default_contract_has_only_four_files_five_edits_and_frozen_oracle():
    contract = json.loads(module.CONTRACT.read_text())
    assert contract["input_files"] == 243 and contract["input_bytes"] == 3832217
    assert contract["version"] == 2
    assert len(contract["changes"]) == 4
    assert sum(len(c["replacements"]) for c in contract["changes"]) == 5
    assert contract["backend_core_ontology"]["version_iri"] == "http://integratedmodelling.org/odo/0.2.0"
    assert contract["backend_core_ontology"]["sha256"] == "496bc6634392d7e2eb2a6749bca6a1570333ea607b9b8e52ffaf5b619bcf2a57"
    assert contract["oracle"]["calculation"] == "100 + 93/4 metres"
    assert contract["oracle"]["expected_decimal"] == "123.25"
    assert contract["oracle"]["cells"] == 20 and contract["oracle"]["missing_cells"] == 0
    assert len(contract["oracle"]["unchanged_files"]) == 4


@pytest.mark.parametrize("field,value", [("input_files", 5), ("input_bytes", 1), ("input_tree_sha256", "wrong")])
def test_unapproved_input_never_creates_output(fixture, field, value):
    source, destination, contract, _ = fixture
    contract[field] = value
    with pytest.raises(ValueError, match="approved frozen"):
        module._prepare(source, destination, contract)
    assert not destination.exists()


@pytest.mark.parametrize("kind", ["duplicate", "missing", "preimage", "empty", "zero", "multiple", "postimage"])
def test_bad_or_ambiguous_patch_plan_fails_before_writing(fixture, kind):
    source, destination, contract, _ = fixture
    change = contract["changes"][0]
    if kind == "duplicate":
        contract["changes"].append(copy.deepcopy(change))
    elif kind == "missing":
        change["path"] = "absent.kwv"
    elif kind == "preimage":
        change["input_sha256"] = "bad"
    elif kind == "postimage":
        change["output_sha256"] = "bad"
    else:
        change["replacements"][0]["old"] = {"empty": "", "zero": "absent token", "multiple": "\r\n"}[kind]
    with pytest.raises(ValueError):
        module._prepare(source, destination, contract)
    assert not destination.exists()


@pytest.mark.parametrize("changed", [True, False])
def test_oracle_cannot_be_patched_or_changed(fixture, changed):
    source, destination, contract, files = fixture
    name = "imod/agency.kwv" if changed else "python.fixture/model.kim"
    contract["oracle"]["unchanged_files"] = {name: module._sha(files[name]) if changed else "bad"}
    with pytest.raises(ValueError, match="oracle/model"):
        module._prepare(source, destination, contract)
    assert not destination.exists()


@pytest.mark.parametrize("name", ["", "../x", "/absolute", "a\\x", "C:x", "a//x", "a/./x", "a/../x", "a/"])
def test_unsafe_relative_paths_are_not_admitted(name):
    with pytest.raises(ValueError, match="Unsafe"):
        module._safe_name(name)


def test_existing_and_nonprivate_output_locations_are_refused(fixture):
    source, destination, contract, _ = fixture
    destination.write_text("user work")
    with pytest.raises(ValueError, match="must be new"):
        module._prepare(source, destination, contract)
    assert destination.read_text() == "user work"
    with pytest.raises(ValueError, match="existing direct"):
        module._prepare(source, destination.parent / "missing/new", contract)
    (destination.parent / ".git").write_text("gitdir: retained-user-worktree")
    with pytest.raises(ValueError, match="outside Git"):
        module._prepare(source, destination.parent / "another-new", contract)


def test_nested_or_overlapping_trees_are_refused(fixture):
    source, destination, contract, _ = fixture
    for first, second in [(destination, destination), (source, source / "new"), (destination / "child", destination)]:
        with pytest.raises(ValueError, match="separate"):
            module._prepare(first, second, contract)


def test_non_direct_paths_and_missing_inputs_are_refused(fixture, monkeypatch):
    source, destination, contract, _ = fixture
    with pytest.raises(ValueError, match="real, direct"):
        module._collect(source / "missing", 4)
    resolve = Path.resolve
    monkeypatch.setattr(Path, "resolve", lambda self: resolve(self).parent if self == destination.parent else resolve(self))
    with pytest.raises(ValueError, match="existing direct"):
        module._prepare(source, destination, contract)


def test_links_and_junctions_are_never_followed(fixture, monkeypatch):
    source, _, _, _ = fixture
    assert module._link(SimpleNamespace(is_symlink=lambda: True))
    assert module._link(SimpleNamespace(is_symlink=lambda: False, is_junction=lambda: True))
    assert not module._link(SimpleNamespace(is_symlink=lambda: False))
    monkeypatch.setattr(module, "_link", lambda path: path.name == "imod")
    with pytest.raises(ValueError, match="links and junctions"):
        module._collect(source, 4)


def test_file_and_byte_budgets_are_checked_before_copy(fixture, monkeypatch):
    source, _, _, _ = fixture
    with pytest.raises(ValueError, match="inventory budget"):
        module._collect(source, 0)
    monkeypatch.setattr(module, "MAX_BYTES", 1)
    with pytest.raises(ValueError, match="byte budget"):
        module._collect(source, 4)


@pytest.mark.parametrize("corrupt_output", [True, False])
def test_external_byte_changes_do_not_receive_success_receipt(fixture, monkeypatch, corrupt_output):
    source, destination, contract, files = fixture
    collect = module._collect
    calls = 0
    def changing(root, count):
        nonlocal calls
        calls += 1
        result = collect(root, count)
        if calls == (2 if corrupt_output else 3):
            result["python.fixture/model.kim"] = b"external change"
        return result
    monkeypatch.setattr(module, "_collect", changing)
    with pytest.raises(ValueError, match="changed during"):
        module._prepare(source, destination, contract)
    assert destination.is_dir() and not (destination / module.RECEIPT).exists()
    assert collect(source, len(files)) == files


@pytest.mark.parametrize("reverse", [False, True])
def test_write_failure_preserves_partial_output_and_original_inputs(fixture, monkeypatch, reverse):
    source, destination, contract, files = fixture
    collect = module._collect
    def ordered(*args, **kwargs):
        return dict(sorted(collect(*args, **kwargs).items(), reverse=reverse))
    monkeypatch.setattr(module, "_collect", ordered)
    original = Path.open
    def failing(self, mode="r", *args, **kwargs):
        if self == destination / "imod/earth.kwv" and mode == "xb":
            raise OSError("controlled output failure")
        return original(self, mode, *args, **kwargs)
    monkeypatch.setattr(Path, "open", failing)
    with pytest.raises(OSError):
        module._prepare(source, destination, contract)
    assert destination.is_dir() and not (destination / module.RECEIPT).exists()
    assert (destination / "imod/agency.kwv").is_file()
    assert module._collect(source, len(files)) == files


def test_public_prepare_uses_checked_in_contract_and_cli_reports_only_byte_preparation(fixture, monkeypatch, capsys):
    source, destination, contract, _ = fixture
    path = source.parent / "contract.json"
    path.write_text(json.dumps(contract))
    monkeypatch.setattr(module, "CONTRACT", path)
    assert module.main([str(source), str(destination)]) == 0
    assert json.loads(capsys.readouterr().out)["semantic_admission"] == "UNKNOWN"
    assert module.main([str(source), str(destination)]) == 3
    assert "UNKNOWN" in capsys.readouterr().out


def test_script_entry_failure_is_unknown_not_acceptance(fixture, monkeypatch, capsys):
    source, destination, _, _ = fixture
    monkeypatch.setattr(sys, "argv", [module.__file__, str(source), str(destination)])
    with pytest.raises(SystemExit) as failure:
        runpy.run_path(module.__file__, run_name="__main__")
    assert failure.value.code == 3
    assert "UNKNOWN" in capsys.readouterr().out


def execution_plan(fixture, monkeypatch):
    source, destination, contract, files = fixture
    base = source.parent / "base-contract.json"
    base.write_text(json.dumps(contract))
    name = "python.fixture/model.kim"
    output = files[name].replace(b"model Elevation in m", b"model Elevation of Region in m")
    variant = {"version": 3, "base_contract_sha256": module._sha(base.read_bytes()),
               "request_definition": "Elevation of Region in m", "original_acceptance": "PRESERVED_FAILED_NOT_REPLACED",
               "model": {"path": name, "input_sha256": module._sha(files[name]), "output_sha256": module._sha(output),
                         "constant_expression": "[100 + 93/4]", "replacements": [
                             {"old": "model Elevation in m", "new": "model Elevation of Region in m"}]}}
    plan = source.parent / "execution-contract.json"
    plan.write_text(json.dumps(variant))
    monkeypatch.setattr(module, "CONTRACT", base)
    monkeypatch.setattr(module, "EXECUTION_CONTRACT", plan)
    return source, destination, contract, files, plan, variant, output


def test_execution_contract_is_opt_in_preserves_original_and_prior_v2(fixture, monkeypatch):
    source, destination, contract, files, _, variant, output = execution_plan(fixture, monkeypatch)
    previous = destination.with_name("previous-v2")
    old_receipt = module.prepare(source, previous)
    receipt = module.prepare_execution(source, destination)
    assert old_receipt["version"] == 2 and receipt["version"] == 3
    assert (previous / "python.fixture/model.kim").read_bytes() == files["python.fixture/model.kim"]
    assert (destination / "python.fixture/model.kim").read_bytes() == output
    assert module._collect(source, len(files)) == files
    assert receipt["execution_contract"] == variant
    assert receipt["oracle"]["expected_decimal"] == contract["oracle"]["expected_decimal"]
    assert receipt["oracle"]["derived_model"]["constant_expression"] == "[100 + 93/4]"
    assert receipt["status"] == "PREPARED_NOT_SEMANTICALLY_ADMITTED"
    assert receipt["semantic_admission"] == "UNKNOWN"
    assert not receipt["original_acceptance_replaced"]
    assert receipt["services_started"] == receipt["scientific_jobs"] == 0
    with pytest.raises(ValueError, match="must be new"):
        module.prepare_execution(source, destination)


@pytest.mark.parametrize("kind", ["base-hash", "base-version", "variant-version", "model-hash"])
def test_execution_contract_refuses_unpinned_derivation_before_output(fixture, monkeypatch, kind):
    source, destination, _, _, plan, variant, _ = execution_plan(fixture, monkeypatch)
    if kind == "base-hash":
        variant["base_contract_sha256"] = "bad"
    elif kind == "base-version":
        body = json.loads(module.CONTRACT.read_text()); body["version"] = 1
        module.CONTRACT.write_text(json.dumps(body))
        variant["base_contract_sha256"] = module._sha(module.CONTRACT.read_bytes())
    elif kind == "variant-version":
        variant["version"] = 2
    else:
        variant["model"]["input_sha256"] = "bad"
    plan.write_text(json.dumps(variant))
    with pytest.raises(ValueError):
        module.prepare_execution(source, destination)
    assert not destination.exists()


@pytest.mark.parametrize("kind", ["changed-expression", "missing-expression", "empty-expression", "derived-preimage", "derived-postimage", "not-derived"])
def test_derived_model_cannot_relabel_a_changed_numerical_oracle(fixture, kind):
    source, destination, contract, files = fixture
    name = "python.fixture/model.kim"
    output = files[name].replace(b"model Elevation in m", b"model Elevation of Region in m")
    if kind == "changed-expression":
        output = output.replace(b"100 + 93/4", b"100 + 20")
    contract["changes"].append({"path": name, "input_sha256": module._sha(files[name]), "output_sha256": module._sha(output),
                                "replacements": [{"old": files[name].decode(), "new": output.decode()}]})
    contract["oracle"]["unchanged_files"] = {}
    derived = {"path": name, "input_sha256": module._sha(files[name]), "output_sha256": module._sha(output),
               "constant_expression": "[100 + 93/4]"}
    if kind == "missing-expression":
        derived["constant_expression"] = "absent"
    elif kind == "empty-expression":
        derived["constant_expression"] = ""
    elif kind == "derived-preimage":
        derived["input_sha256"] = "bad"
    elif kind == "derived-postimage":
        derived["output_sha256"] = "bad"
    elif kind == "not-derived":
        contract["changes"].pop()
    contract["oracle"]["derived_model"] = derived
    with pytest.raises(ValueError, match="exact numerical expression"):
        module._prepare(source, destination, contract)
    assert not destination.exists()
    assert module._collect(source, len(files)) == files


def test_execution_cli_requires_explicit_flag(fixture, monkeypatch, capsys):
    source, destination, _, _, _, _, output = execution_plan(fixture, monkeypatch)
    assert module.main([str(source), str(destination), "--explicit-bearer"]) == 0
    receipt = json.loads(capsys.readouterr().out)
    assert receipt["version"] == 3 and not receipt["original_acceptance_replaced"]
    assert (destination / "python.fixture/model.kim").read_bytes() == output


def test_checked_in_execution_contract_is_a_separate_exact_v2_derivation():
    base = json.loads(module.CONTRACT.read_text())
    variant = json.loads(module.EXECUTION_CONTRACT.read_text())
    assert module._sha(module.CONTRACT.read_bytes()) == variant["base_contract_sha256"]
    assert base["version"] == 2 and variant["version"] == 3
    assert variant["original_acceptance"] == "PRESERVED_FAILED_NOT_REPLACED"
    assert base["oracle"]["unchanged_files"][variant["model"]["path"]] == variant["model"]["input_sha256"]
    assert variant["model"]["constant_expression"] == "[100 + 23.25]"
    assert len(variant["model"]["replacements"]) == 1
    edit = variant["model"]["replacements"][0]
    assert edit["old"].replace("geography:Elevation", "geography:Elevation of (earth:Terrestrial earth:Region)") == edit["new"]
    assert variant["request_definition"] == "geography:Elevation of (earth:Terrestrial earth:Region) in m"
