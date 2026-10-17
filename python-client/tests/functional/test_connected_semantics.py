"""Opt-in, read-only contracts against explicitly trusted existing deployments."""
import json
import os
from pathlib import Path

import pytest

from klab_client import DeploymentProfile, HubCertificateProvider, connect
from klab_client.errors import ConfigurationError
from run_connected_semantic_probe import check_observable
from workflows.develop_compatibility import require_semantic_readiness

pytestmark = [pytest.mark.live, pytest.mark.functional]


@pytest.fixture(scope="module")
def connected():
    profile_path = os.environ.get("KLAB_CONNECTED_PROFILE")
    certificate_path = os.environ.get("KLAB_CONNECTED_CERTIFICATE")
    if not profile_path or not certificate_path:
        raise ConfigurationError("Connected tests require explicit KLAB_CONNECTED_PROFILE and KLAB_CONNECTED_CERTIFICATE paths")
    profile = DeploymentProfile.load(profile_path)
    with connect(profile, credentials=HubCertificateProvider(Path(certificate_path)), initialize=False) as connection:
        yield connection


def test_connected_reasoner_publishes_accepted_semantic_knowledge(connected, tmp_path, record_property):
    candidate = require_semantic_readiness(tmp_path, client=connected.client)
    assert candidate.get("acceptedGeneration", 0) > 0, "Connected tests require explicit accepted-generation evidence"
    record_property("accepted_generation", candidate["acceptedGeneration"])
    record_property("logical_status", candidate.get("logicalStatus"))
    record_property("diagnostic_count", len(candidate.get("diagnostics", [])))


@pytest.mark.parametrize("unit", ["m", "mm"])
def test_connected_resolved_observable_preserves_requested_unit(connected, record_property, unit):
    client = connected.client
    definition = "geography:Elevation in " + unit
    # Independently inspect the actual wire shape before invoking DTO conversion.
    payload = client.transport.request("reasoner", "POST", "/api/v1/resolve/observable", text=definition)
    observable = client.reasoner.resolve_observable(definition)
    wire_unit = payload.get("unit")
    record_property("request_definition", definition)
    record_property("wire_unit_present", "unit" in payload)
    record_property("wire_observable_urn", client.transport.redact(str(payload.get("urn"))))
    record_property("wire_unit", client.transport.redact(json.dumps(wire_unit)))
    record_property("sdk_unit_present", "unit" in observable.raw)
    if not isinstance(wire_unit, dict) or wire_unit.get("definition") != unit:
        raise AssertionError("Reasoner wire response does not preserve requested unit "
                             + unit + "; Python must not invent mediation")
    check_observable(observable, "geography:Elevation", unit)
