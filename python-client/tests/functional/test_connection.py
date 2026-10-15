"""Requested controlled-exchange suites must reach genuine services or fail."""
import json
import pytest

from workflows.connection import access, readiness, run

pytestmark = [pytest.mark.functional, pytest.mark.live]


@pytest.mark.parametrize("provider", ["certificate", "token"])
def test_connection_onboarding_and_fresh_process_recovery(functional_state, provider, record_property):
    report = run(functional_state, provider)
    record_property("connection_scientific_workflow", json.dumps(report))


def test_connection_access(functional_state, record_property):
    record_property("connection_access", json.dumps(access(functional_state)))


def test_connection_readiness(functional_state, record_property):
    record_property("connection_readiness", json.dumps(readiness(functional_state)))
