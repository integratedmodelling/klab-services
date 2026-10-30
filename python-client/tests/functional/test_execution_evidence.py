"""Read existing Java results only; never create, recompute or release fixture work."""
import json
import math

import pytest

from klab_client import Client, Endpoint, fetch_execution_evidence, load_handle
from klab_client.errors import AuthorizationError, ConfigurationError, MissingAssetError
from klab_client.experiment import verify_grid


pytestmark = [pytest.mark.live, pytest.mark.functional]


@pytest.fixture
def recorded_fixture(functional_state):
    path = functional_state / "execution-evidence.json"
    if not path.is_file():
        raise ConfigurationError("Prepare execution-evidence.json with live shared/private observation handles")
    fixture = json.loads(path.read_text())
    for name in ("units", "model_urn", "contextualizer"):
        if not isinstance(fixture.get(name), str) or not fixture[name].strip():
            raise ConfigurationError("Execution evidence fixture needs units, model_urn and contextualizer")
    config = json.loads((functional_state / "scientist.json").read_text())
    if not config.get("other_token") or config.get("other_token") == config.get("token"):
        raise ConfigurationError("Execution evidence requires distinct valid fixture credentials")
    handles = {
        name: load_handle(functional_state / fixture[name])
        for name in ("shared_observation", "private_observation")
    }
    if any(handle.kind != "observation" for handle in handles.values()):
        raise ConfigurationError("Execution evidence fixtures must reference existing observations")
    if handles["shared_observation"].context_id == handles["private_observation"].context_id:
        raise ConfigurationError("Shared and private controls require distinct live contexts")
    return config, fixture, handles


def fixture_client(config, *, nonowner=False):
    token = config["other_token"] if nonowner else config["token"]
    return Client(
        config["urls"]["runtime"], token,
        agent_name="other-scientist" if nonowner else config["username"],
        **{name: Endpoint(config["urls"][name], token) for name in ("reasoner", "resources", "resolver")},
    )


def verify_recorded_result(client, handle, fixture):
    observation = client.restore_handle(handle)
    verify_grid(observation)
    values = observation.fetch_data(range(20), curve="D2_YX").values
    assert observation.units == fixture["units"]
    assert len(values) == 20 and all(value is not None and math.isfinite(value) for value in values)
    evidence = fetch_execution_evidence(observation)
    model_found = False
    call_labels = set()
    for activity in evidence.activities:
        if activity.outcome != "SUCCESS":
            continue
        for chart in activity.charts:
            for node in chart.nodes:
                if chart.phase == "resolution plan":
                    metadata = node.raw.get("metadata", {})
                    if metadata.get("kind") == "model" and metadata.get("urn") == fixture["model_urn"]:
                        model_found = True
                elif chart.phase == "contextualization plan":
                    call_labels.update(node.labels)
    assert model_found
    assert fixture["contextualizer"] in call_labels
    assert evidence.partial and evidence.durable_root_commit.startswith("unknown")
    assert "Partial evidence" in evidence.summary()
    assert "Partial evidence" in evidence._repr_html_()
    return evidence


def test_owner_reads_its_actual_persisted_execution_diagrams(recorded_fixture):
    config, fixture, handles = recorded_fixture
    with fixture_client(config) as owner:
        verify_recorded_result(owner, handles["private_observation"], fixture)


def test_valid_nonowner_reads_shared_work_but_cannot_read_private_evidence(recorded_fixture):
    config, fixture, handles = recorded_fixture
    with fixture_client(config) as owner, fixture_client(config, nonowner=True) as nonowner:
        verify_recorded_result(owner, handles["private_observation"], fixture)
        # Successful shared data/evidence reads prove this is a valid credential
        # and supported endpoint, not an invalid-token or unavailable-service test.
        verify_recorded_result(nonowner, handles["shared_observation"], fixture)
        with pytest.raises((AuthorizationError, MissingAssetError)):
            private = nonowner.restore_handle(handles["private_observation"])
            fetch_execution_evidence(private)
        # The owner still sees the same live fixture after the denial.
        verify_recorded_result(owner, handles["private_observation"], fixture)
