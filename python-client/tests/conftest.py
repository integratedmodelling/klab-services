from pathlib import Path
import sys

import httpx
import pytest

# Test-only helpers and development scenarios; never added to the SDK wheel.
sys.path.insert(0, str(Path(__file__).parent))
sys.path.insert(0, str(Path(__file__).parents[1] / "tools"))


@pytest.fixture(autouse=True)
def offline_network_guard(request, monkeypatch):
    if request.node.get_closest_marker("live"):
        return
    def prohibited(*args, **kwargs):
        pytest.fail("Offline test attempted real HTTP; use source-derived MockTransport fixtures")
    monkeypatch.setattr(httpx.HTTPTransport, "handle_request", prohibited)
