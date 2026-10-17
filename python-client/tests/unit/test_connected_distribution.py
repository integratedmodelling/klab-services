"""The reproducible public connection profile must ship, not private profiles."""
from pathlib import Path

from klab_client import DeploymentProfile


def test_source_distribution_explicitly_includes_public_hosted_profile():
    root = Path(__file__).parents[2]
    directives = (root / "MANIFEST.in").read_text().splitlines()
    assert "include examples/hosted-deployment.json" in directives
    # Avoid broadening example JSON inclusion to user-created credential profiles.
    assert "recursive-include examples *.json" not in directives
    profile = DeploymentProfile.load(root / "examples/hosted-deployment.json")
    assert {service.service_type for service in profile.services} == {"runtime", "resolver", "reasoner", "resources"}
