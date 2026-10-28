"""Explicit opt-in unchanged-Java milestone acceptance; missing prerequisites fail, never skip."""
import json
import os
from pathlib import Path

import pytest

from klab_client import Context, Experiment, ExperimentRunner, ObservationRequest, export_for_analysis, fetch_execution_evidence
from klab_client.dto import observation_from_wire
from klab_client.errors import AuthorizationError, ConfigurationError, MissingAssetError
from workflows import milestone
from workflows.discover_compare_explain import inspect_result
from workflows.scientific_checks import require

pytestmark = [pytest.mark.live, pytest.mark.functional]


@pytest.fixture(scope="module")
def inputs():
    paths = {name: Path(os.environ["KLAB_MILESTONE_" + name]) for name in ("FIXTURE", "PROFILE", "CERTIFICATE", "OUTPUT")}
    fixture = milestone.load_fixture(paths["FIXTURE"])
    paths["OUTPUT"].mkdir(exist_ok=False)
    return paths, fixture


@pytest.fixture(scope="module")
def read_factory(inputs):
    paths, _ = inputs
    return milestone.factory_from_paths(paths["PROFILE"], paths["CERTIFICATE"])


@pytest.fixture(scope="module")
def factory(inputs):
    if os.environ.get("KLAB_CONNECTED_ALLOW_OWNED_WORK") != "1":
        raise ConfigurationError("Milestone computation requires explicit --allow-owned-work")
    paths, _ = inputs
    return milestone.factory_from_paths(paths["PROFILE"], paths["CERTIFICATE"], allow_owned_work=True)


@pytest.fixture(scope="module")
def sequential(inputs, factory):
    paths, fixture = inputs
    return milestone.run_case_batch(factory, fixture, paths["OUTPUT"] / "sequential", workers=1)


def test_discovery_and_model_inspection_use_existing_java_contracts(inputs, read_factory, record_property):
    _, fixture = inputs
    record_property("milestone_discovery", json.dumps(milestone.discover(read_factory, fixture)))


def test_recorded_execution_evidence_matches_computed_observation(sequential):
    require(sequential["matrix_verified"], "Real computation/export/evidence failed; inspect retained batch")
    for record in sequential["processed"].values():
        require(record["evidence"]["successful_activity_ids"] and record["evidence"]["matched_node_ids"],
                "Recorded numerical execution evidence missing")


def test_four_experiments_preserve_scientific_meaning(sequential, record_property):
    require(sequential["matrix_verified"], "All four independent scientific oracle checks must pass")
    record_property("milestone_sequential", json.dumps(sequential))


def test_parallel_experiments_preserve_identity_and_results(inputs, factory, sequential, record_property):
    paths, fixture = inputs
    parallel = milestone.run_case_batch(factory, fixture, paths["OUTPUT"] / "parallel", workers=4)
    require(sequential["matrix_verified"] and parallel["matrix_verified"], "Sequential and parallel matrices must both pass")
    require(len({row["context_handle"]["context_id"] for row in parallel["batch"]["outcomes"]}) == 4,
            "Parallel experiments shared context identities")
    for name in parallel["processed"]:
        require(parallel["processed"][name]["raster"] == sequential["processed"][name]["raster"],
                "Parallel scientific output differs from sequential reference checks")
    record_property("milestone_parallel", json.dumps(parallel))


def test_fresh_process_restores_original_experiment_results(inputs, sequential, record_property):
    paths, _ = inputs
    require(sequential["matrix_verified"], "Cannot establish restoration from an unsuccessful matrix")
    result = milestone.launch_restore(paths["FIXTURE"], paths["OUTPUT"] / "sequential/batch.json",
        paths["OUTPUT"] / "restored", paths["PROFILE"], paths["CERTIFICATE"])
    require([r["observation_handle"] for r in result["results"]] ==
            [r["result_handles"][-1] for r in sequential["batch"]["outcomes"]], "Original identities were not restored")
    record_property("milestone_restoration", json.dumps(result))


def test_partial_failure_retains_successes_and_recoverable_handles(inputs, factory):
    # Local failure injection needs no special Java route and cannot cause ambiguous
    # mutation replay: one worker's factory fails before it can submit anything.
    from threading import Lock
    lock, calls = Lock(), [0]
    def failing_factory():
        with lock:
            calls[0] += 1
            fail = calls[0] == 2
        if fail:
            raise ConfigurationError("Deliberate local worker setup failure")
        return factory()
    paths, fixture = inputs
    by_name = {c.experiment.name: c for c in fixture.cases}
    directory = paths["OUTPUT"] / "partial"
    directory.mkdir()
    def process(experiment, observation):
        return inspect_result(observation, by_name[experiment.name].oracle, directory / (experiment.name + ".tif"),
                              fixture.expected_node_ids)
    with ExperimentRunner(failing_factory, workers=1, processor=process) as runner:
        batch = runner.run([c.experiment for c in fixture.cases])
    milestone.write_json(directory / "batch.json", json.loads(batch.dumps()))
    require(batch.counts == {"succeeded": 3, "setup_failed": 1} and calls[0] == 4,
            "Injected setup failure discarded successful siblings or retried work")
    require(all(o.result_handle for o in batch.outcomes if o.state == "succeeded"), "Sibling result handles lost")


def test_private_experiment_evidence_and_exports_enforce_access(inputs, factory, sequential, record_property):
    from klab_client import Handle
    paths, _ = inputs
    other_path = os.environ.get("KLAB_MILESTONE_OTHER_CERTIFICATE")
    if not other_path:
        raise ConfigurationError("Permissions acceptance requires a separately authenticated second certificate")
    other_factory = milestone.factory_from_paths(paths["PROFILE"], Path(other_path), allow_owned_work=True)
    require(sequential["matrix_verified"], "Owner positive scientific control must pass")
    with factory() as owner, other_factory() as guest:
        require(owner.identity.principal != guest.identity.principal, "Permission test requires two distinct principals")
        original = owner.client.restore_handle(Handle.loads(json.dumps(sequential["batch"]["outcomes"][0]["result_handles"][-1])))
        positive = guest.client.create_session(name="milestone-valid-nonowner")
        try:
            foreign = Context(guest.client, original._context.configuration, token=original._context.get_context_id())
            inaccessible = observation_from_wire(original.raw, foreign)
            denied = paths["OUTPUT"] / "nonowner-denied.tif"
            operations = [lambda: fetch_execution_evidence(inaccessible), lambda: export_for_analysis(inaccessible, denied)]
            for operation in operations:
                try:
                    operation()
                except (AuthorizationError, MissingAssetError):
                    pass
                else:
                    raise AssertionError("Private evidence/export was accessible to a valid nonowner")
            require(not denied.exists(), "Denied export published a local file")
        finally:
            require(positive.release(), "Nonowner positive-control cleanup failed")
    record_property("milestone_permissions", json.dumps({"distinct_principals": True, "evidence_denied": True, "export_denied": True}))


def test_milestone_notebook_executes_and_matches_evidence(inputs, factory):
    from build_milestone_deliverable import execute_notebook
    paths, _ = inputs
    execute_notebook(paths["OUTPUT"] / "notebook", fixture_path=paths["FIXTURE"], profile_path=paths["PROFILE"],
                     certificate_path=paths["CERTIFICATE"])
