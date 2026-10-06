"""Opt-in actual signed-JWT/scientific/throughput fixture acceptance.

Run this file explicitly with -o addopts= -m live after provisioning the stack.
Missing state/assets/configuration fail, never skip.
"""
import json
import os
from pathlib import Path

import pytest

from klab_client.errors import ConfigurationError

pytestmark = pytest.mark.live


@pytest.fixture
def fixture_tools(monkeypatch):
    value = os.environ.get("KLAB_FIXTURE_STATE_DIR")
    if not value:
        raise ConfigurationError("Set KLAB_FIXTURE_STATE_DIR to the running isolated fixture state")
    state = Path(value)
    if not (state / "scientist.json").is_file():
        raise ConfigurationError("Fixture scientist.json missing; run fixture_hub.py prepare/serve")
    monkeypatch.syspath_prepend(str(Path(__file__).parents[1] / "tools"))
    return state


def test_signed_authentication_scientific_lifecycle(fixture_tools, record_property):
    from verify_fixture import verify
    report = verify(fixture_tools)
    assert report["passed"]
    assert report["seed"]["verified_cells"] == 40
    assert report["cancellation"]["confirmed"], "Fixture completed too early to validate active cancellation"
    assert report["failed_job"]["scientific_outcome"] == "confirmed failure"
    record_property("local_scientific_lifecycle", json.dumps(report))


def test_actual_concurrent_full_workflow(fixture_tools, record_property):
    from workflow_benchmark import initialize, make_client, run_workload, sample, summarize
    from klab_client import Session
    config = json.loads((fixture_tools / "scientist.json").read_text())
    initialize(config)
    with make_client(config) as client:
        session_id = client.create_session(name="pytest-full-workflow-throughput").id
    count = int(os.environ.get("KLAB_FIXTURE_SAMPLES", "4"))
    concurrency = int(os.environ.get("KLAB_FIXTURE_CONCURRENCY", "2"))
    if count < 1 or concurrency < 1:
        raise ConfigurationError("Fixture sample/concurrency counts must be positive")
    records, elapsed = run_workload(lambda number: sample(config, session_id, number),
        samples=count, concurrency=concurrency, duration=0, on_result=lambda result: print(json.dumps(result)))
    summary = summarize(records, elapsed)
    record_property("verified_workflow_throughput", json.dumps(summary))
    assert summary["acceptance_passed"], json.dumps(records)
    assert summary["verified_samples"] == count
    assert len({record["observation_id"] for record in records}) == count
    assert len({record["context_id"] for record in records}) == count
    with make_client(config) as client:
        assert Session(client, session_id).release()
