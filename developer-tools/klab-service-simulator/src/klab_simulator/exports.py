"""Exact immutable recorded bytes, not a local scientific exporter."""
from importlib.resources import files
from urllib.parse import parse_qs, urlsplit

from .fixtures import authentic_fixture, nonuniform_fixture, declared_band_fixture
from .state import ContractError

MEDIA = "image/tiff;application=geotiff"
SCHEMA = "simulator.native.geotiff"
# RC-P2.2 finite assignment to existing bytes, NOT genuine native temporal history.
TEMPORAL_QUERY = {"storageEvent": ["fixture:transition"], "storageStart": ["0"], "storageEnd": ["1"]}


def export_schemas():
    # Native capability schema shape; identity/applicability are explicitly simulator-only.
    return {"simulator": [{"schemaId": SCHEMA, "knowledgeClass": "OBSERVATION", "type": "STREAM",
        "mediaTypes": [MEDIA], "extensions": ["tif"], "description": "Frozen native TIFF; fixture delivery only"}]}


def export_bytes(state, headers, original_path=""):
    query = parse_qs(urlsplit(original_path).query, keep_blank_values=True)
    if query and (state.scenario != "nonuniform-export" or query != TEMPORAL_QUERY):
        raise ContractError(422, "Selected state is unavailable; no unselected fallback")
    if state.origin != "AUTHENTIC_FIXTURE_SIMULATED_DELIVERY" and state.scenario not in ("nonuniform-export", "declared-band-export"):
        raise ContractError(406, "No exporter is advertised for this synthetic observation")
    if headers.get("Accept") != MEDIA or headers.get("Accept-Encoding") != "identity":
        raise ContractError(406, "Choose the one advertised identity-encoded representation")
    if state.scenario == "export-denied":
        raise ContractError(403, "Deliberate export permission denial")
    if state.scenario == "nonuniform-export":
        _, native = nonuniform_fixture()
        return native, MEDIA
    if state.scenario == "declared-band-export":
        _, native = declared_band_fixture()
        return native, MEDIA
    authentic_fixture()  # Fail before serving if original scientific bytes drift.
    media = "application/octet-stream" if state.scenario == "export-media-mismatch" else MEDIA
    return files("klab_simulator").joinpath("fixtures/usgs-native-20x20.tif").read_bytes(), media
