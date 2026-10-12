"""A genuine installed GeoJSON exporter, checked against independent geographic corners."""
import json
import math
import os
import subprocess
import sys
from pathlib import Path

import pytest

from klab_client import Client, Endpoint, ObservationRequest, RectangularGeometry
from klab_client.errors import ConfigurationError


@pytest.mark.live
def test_native_geojson_export_preserves_identity_and_known_geometry(record_property):
    value = os.environ.get("KLAB_FIXTURE_STATE_DIR")
    if not value:
        raise ConfigurationError("Export acceptance requires the explicit exporter-enabled TEST fixture")
    state = Path(value)
    config = json.loads((state / "scientist.json").read_text())
    with Client(config["urls"]["runtime"], config["token"], agent_name=config["username"],
            **{name: Endpoint(config["urls"][name], config["token"]) for name in ("reasoner", "resources", "resolver")}) as client:
        client.initialize_user_scope(local_federation=True)
        session = client.create_session(name="native-export-acceptance")
        context = session.create_context()
        try:
            observation = context.observe(ObservationRequest("earth:Region", geometry=RectangularGeometry(
                200000, 200500, 6000000, 6000400, 5, 4))).result(120)
            assert any(item.media_type == "application/geo+json" for item in client.export_formats(observation))
            result = observation.export_to_file(state / "native-region.geojson", media_type="application/geo+json")
            payload = json.loads(result.path.read_bytes())  # Only this small independent reference fixture is materialized.
            assert payload["type"] == "FeatureCollection" and len(payload["features"]) == 1
            feature = payload["features"][0]
            assert feature["id"] == observation.id and feature["properties"]["urn"] == observation.urn
            assert feature["geometry"]["type"] == "Polygon"
            coordinates = feature["geometry"]["coordinates"][0]
            expected = {(math.degrees(x / 6378137), math.degrees(math.atan(math.sinh(y / 6378137))))
                for x in (200000, 200500) for y in (6000000, 6000400)}
            assert len(coordinates) == 5 and coordinates[0] == coordinates[-1]
            assert len({tuple(point) for point in coordinates[:-1]}) == 4
            # The pinned published exporter rounds this fixture to six decimal degrees.
            # This declared tolerance allows that precision, not missing corners or swapped axes.
            assert all(any(abs(lon-x) < 1e-6 and abs(lat-y) < 1e-6 for x,y in expected) for lon,lat in coordinates)
            assert result.bytes_written == result.path.stat().st_size
            # Exercise the generic caller-facing example in a fresh process with separate credentials.
            reference = state / "native-observation.json"
            observation.to_handle().save(reference)
            env = {key: val for key, val in os.environ.items() if not key.startswith("KLAB_")}
            env["KLAB_AGENT_NAME"] = config["username"]
            env["KLAB_RUNTIME_SERVICE_ID"] = client.runtime_service_id
            for name, url in config["urls"].items():
                env["KLAB_" + name.upper() + "_URL"] = url
                env["KLAB_" + name.upper() + "_TOKEN"] = config["token"]
            script = Path(__file__).parents[1] / "examples/stream_export.py"
            example_path = state / "example-region.geojson"
            process = subprocess.run([sys.executable, str(script), str(reference), str(example_path),
                "--media-type", "application/geo+json"], env=env, capture_output=True, text=True, timeout=120)
            assert process.returncode == 0, process.stderr
            example = json.loads(example_path.read_bytes())
            assert example["features"][0]["id"] == observation.id
            assert example["features"][0]["geometry"] == feature["geometry"]
            record_property("native_export", json.dumps({"schema_id": result.schema_id,
                "media_type": result.media_type, "bytes": result.bytes_written,
                "observation_id": observation.id, "geometry": feature["geometry"],
                "tolerance_degrees": 1e-6, "units_raster_values_validity": "not supplied by this substantial GeoJSON export"}))
        finally:
            assert context.release() and session.release()
