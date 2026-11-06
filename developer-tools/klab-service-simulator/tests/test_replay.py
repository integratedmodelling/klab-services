"""Replay is visibly replay: absent fixtures and genuine failures never become success."""
import json

import pytest

from klab_client import Client, Endpoint
from klab_client.client import Job
from klab_client.errors import JobFailedError, ServerError, SubmissionOutcomeUnknown
from conftest import launch
from test_vertical import control


def test_pinned_semantic_model_and_historical_empty_result():
    with launch(mode="replay") as ready:
        urls = ready["endpoints"]
        with Client(urls["runtime"], "sim-token-alice", reasoner=Endpoint(urls["reasoner"], "sim-token-alice"),
                    resources=Endpoint(urls["resources"], "sim-token-alice"), poll_interval=.001) as client:
            semantic = client.reasoner.resolve_observable("geography:Elevation in m")
            assert semantic.semantics.urn == "geography:Elevation"
            model = client.resources.retrieve("scientific.examples.usgs_virginia.elevation-quantifier", "MODEL")
            assert model.urn == "scientific.examples.usgs_virginia.elevation-quantifier"
            with pytest.raises(JobFailedError, match="Contextualization"):
                Job(client, 9, "replay.scope").result(timeout=1)
            with pytest.raises(SubmissionOutcomeUnknown):
                client.create_session()
            with pytest.raises(ServerError) as failure:
                client.resources.list("MODEL")
            assert failure.value.status_code == 501
        report = control(ready)
        assert report["counts"] == {"sessions": 0, "contexts": 0, "jobs": 0, "observations": 0}
        assert all(row["backend"] == "REPLAY" for row in report["trace"])
        assert {row["contract_category"] for row in report["trace"]} == {"VERIFIED_JAVA", "SIMULATED_PROVISIONAL"}
        assert "sim-token-" not in json.dumps(report)
