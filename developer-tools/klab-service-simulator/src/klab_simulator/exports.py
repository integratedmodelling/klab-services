"""Exact immutable recorded bytes, not a local scientific exporter."""
from importlib.resources import files

from .fixtures import authentic_fixture
from .state import ContractError

MEDIA = "image/tiff;application=geotiff"
SCHEMA = "simulator.native.geotiff"


def export_schemas():
    # Native capability schema shape; identity/applicability are explicitly simulator-only.
    return {"simulator": [{"schemaId": SCHEMA, "knowledgeClass": "OBSERVATION", "type": "STREAM",
        "mediaTypes": [MEDIA], "extensions": ["tif"], "description": "Immutable recorded TIFF; simulated delivery"}]}


def export_bytes(state, headers):
    if state.origin != "AUTHENTIC_FIXTURE_SIMULATED_DELIVERY":
        raise ContractError(406, "No exporter is advertised for this synthetic observation")
    if headers.get("Accept") != MEDIA or headers.get("Accept-Encoding") != "identity":
        raise ContractError(406, "Choose the one advertised identity-encoded representation")
    if state.scenario == "export-denied":
        raise ContractError(403, "Deliberate export permission denial")
    authentic_fixture()  # Fail before serving if original scientific bytes drift.
    media = "application/octet-stream" if state.scenario == "export-media-mismatch" else MEDIA
    return files("klab_simulator").joinpath("fixtures/usgs-native-20x20.tif").read_bytes(), media
