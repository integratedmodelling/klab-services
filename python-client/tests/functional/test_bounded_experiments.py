"""Real Java runner checks: 20 Jobs/200 cells total; never run without new consent."""
import math
import os
from pathlib import Path
from threading import Barrier, Lock

import pytest

from klab_client import (ContextOptions, DeploymentProfile, Experiment, ExperimentBatch,
                         ExperimentRunner, HubCertificateProvider, ObservationOptions,
                         ObservationRequest, OwnedContextPolicy, RectangularGeometry,
                         ResourceInspector, connect, fetch_execution_evidence)
from klab_client.errors import ConfigurationError
from klab_client.experiment import verify_grid

pytestmark = [pytest.mark.live, pytest.mark.functional]


def configuration():
    if os.environ.get("KLAB_CONNECTED_ALLOW_OWNED_WORK") != "1":
        raise ConfigurationError("Runner acceptance requires explicit owned-work authorization")
    model = os.environ.get("KLAB_EXPERIMENT_MODEL_URN")
    profile = os.environ.get("KLAB_CONNECTED_PROFILE")
    certificate = os.environ.get("KLAB_CONNECTED_CERTIFICATE")
    if not model or not model.strip() or not profile or not certificate:
        raise ConfigurationError("Runner acceptance needs profile, certificate and explicit model URN")
    return DeploymentProfile.load(profile), Path(certificate), model


def run_batch(tmp_path, *, workers, count, fail_processing=False):
    profile, certificate, model_urn = configuration()
    with connect(profile, credentials=HubCertificateProvider(certificate)) as connection:
        model = ResourceInspector(connection.client).retrieve(model_urn)
        assert model.namespace and model.urn == model_urn
    calls = 0
    lock = Lock()
    barrier = Barrier(workers, timeout=60)

    def factory():
        nonlocal calls
        with lock:
            calls += 1
            first_wave = calls <= workers
        if first_wave:
            # Real workers must overlap before any scientific submission.
            barrier.wait()
        connection = connect(profile, credentials=HubCertificateProvider(certificate))
        counts = {"/createSession": 0, "/createContext": 0, "/api/v1/submit": 0}

        def bound_submissions(request):
            if request.method == "POST":
                for route in counts:
                    if request.url.path.endswith(route):
                        counts[route] += 1
                        assert counts[route] <= (2 if route == "/api/v1/submit" else 1), "Fixture work budget exceeded before transmission"
        connection.client.transport._http.event_hooks["request"].append(bound_submissions)
        return connection

    def process(experiment, observation):
        verify_grid(observation)
        values = observation.fetch_data(range(20), curve="D2_YX").values
        assert observation.units == "m" and len(values) == 20
        assert all(value is not None and math.isfinite(value) for value in values)
        evidence = fetch_execution_evidence(observation)
        assert any(node.raw.get("metadata", {}).get("urn") == model_urn
                   for activity in evidence.activities if activity.outcome == "SUCCESS"
                   for chart in activity.charts if chart.phase == "resolution plan" for node in chart.nodes)
        if fail_processing and experiment.name == "run-0":
            raise ValueError("Intentional test processor failure after real result validation")
        return {"finite_cells": len(values), "units": observation.units}

    experiments = [Experiment(f"run-{index}",
        ObservationRequest("geography:Elevation in m", options=ObservationOptions(namespace=model.namespace)),
        OwnedContextPolicy(ContextOptions(persistence="EXPLICIT_ACTION"), release="terminal"),
        context_request=ObservationRequest("earth:Terrestrial earth:Region",
            geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4))) for index in range(count)]
    with ExperimentRunner(factory, workers=workers, max_experiments=count, processor=process) as runner:
        batch = runner.run(experiments, timeout=120)
    # Save recoverable IDs even if a result/cleanup assertion fails. No replay on load.
    manifest = batch.dumps()
    (tmp_path / "batch.json").write_text(manifest, encoding="utf-8")
    assert ExperimentBatch.loads(manifest).dumps() == manifest
    assert calls == count
    assert [outcome.name for outcome in batch.outcomes] == [item.name for item in experiments]
    assert len({outcome.session_id for outcome in batch.outcomes}) == count
    assert all(outcome.context_handle is not None for outcome in batch.outcomes)
    assert len({outcome.context_handle.context_id for outcome in batch.outcomes}) == count
    assert all(len(outcome.job_handles) == 2 and len(outcome.result_handles) == 2 for outcome in batch.outcomes)
    assert all(outcome.remote_release == "confirmed" and not outcome.cleanup_errors for outcome in batch.outcomes)
    return batch


@pytest.mark.parametrize("workers", [1, 2])
def test_four_independent_experiments_on_existing_java(tmp_path, workers):
    batch = run_batch(tmp_path, workers=workers, count=4)
    assert batch.counts == {"succeeded": 4}
    assert all(outcome.processed == {"finite_cells": 20, "units": "m"} for outcome in batch.outcomes)


def test_real_results_survive_a_sibling_processor_failure(tmp_path):
    batch = run_batch(tmp_path, workers=2, count=2, fail_processing=True)
    assert [outcome.state for outcome in batch.outcomes] == ["processing_failed", "succeeded"]
    assert batch.outcomes[0].error_type == "ValueError"
    assert batch.outcomes[1].processed == {"finite_cells": 20, "units": "m"}
