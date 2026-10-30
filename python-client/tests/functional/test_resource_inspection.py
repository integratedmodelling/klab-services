"""F2 live model/accepted-resolution/result association; requires explicit pilot authorization."""
import json
import os

import pytest

from workflows.resource_inspection import run
from workflows.scientific_checks import require

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_existing_executable_model_links_inspection_to_recorded_computation(record_property):
    require(os.environ.get("KLAB_CONNECTED_ALLOW_OWNED_WORK") == "1", "Explicit owned work authorization is required")
    profile, certificate = os.environ.get("KLAB_CONNECTED_PROFILE"), os.environ.get("KLAB_CONNECTED_CERTIFICATE")
    require(profile and certificate, "Explicit profile/certificate paths are required")
    report = run(profile, certificate, "staging.vxii.basic.elevation-quantifier", allow_owned_work=True)
    record_property("resource_inspector_pilot", json.dumps(report, sort_keys=True))
    require(report["passed"], "F2 pilot failed/blocked at " + report["stage"] + "; inspect sanitized evidence")
