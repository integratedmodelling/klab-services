import httpx
import pytest


@pytest.fixture(autouse=True)
def offline_network_guard(request, monkeypatch):
    if request.node.get_closest_marker("live"):
        return
    def prohibited(*args, **kwargs):
        pytest.fail("Offline test attempted real HTTP; use source-derived MockTransport fixtures")
    monkeypatch.setattr(httpx.HTTPTransport, "handle_request", prohibited)
