"""A genuine installed GeoJSON exporter, checked against independent geographic corners."""
import json
import math
import os
import subprocess
import sys
import uuid
from pathlib import Path

from klab_client import Client, Endpoint, ObservationRequest, RectangularGeometry
from .scientific_checks import release_owned, require


def validate_reference(payload, observation):
    """Check the complete known rectangle and its source, not just plausible points."""
    require(isinstance(payload, dict) and payload.get("type") == "FeatureCollection",
        "GeoJSON reference must be a FeatureCollection")
    features = payload.get("features")
    require(isinstance(features, list) and len(features) == 1,
        "GeoJSON must contain the single reference feature")
    feature = features[0]
    require(isinstance(feature, dict) and feature.get("type") == "Feature",
        "GeoJSON reference must contain a Feature")
    properties = feature.get("properties")
    require(isinstance(properties, dict) and feature.get("id") == observation.id
        and properties.get("urn") == observation.urn,
        "GeoJSON observation identity differs from the source")
    geometry = feature.get("geometry")
    require(isinstance(geometry, dict) and geometry.get("type") == "Polygon",
        "GeoJSON reference must be a polygon")
    rings = geometry.get("coordinates")
    require(isinstance(rings, list) and len(rings) == 1,
        "GeoJSON reference must have one exterior ring without holes")
    ring = rings[0]
    require(isinstance(ring, list) and len(ring) == 5 and ring[0] == ring[-1],
        "GeoJSON reference must have a closed four-corner ring")
    require(all(isinstance(point, list) and len(point) == 2
        and all(type(value) in (int, float) and math.isfinite(value) for value in point) for point in ring),
        "GeoJSON reference must have finite two-dimensional coordinates")
    expected = [(math.degrees(x / 6378137), math.degrees(math.atan(math.sinh(y / 6378137))))
        for x, y in ((200000, 6000000), (200000, 6000400), (200500, 6000400), (200500, 6000000))]
    # Each of the four positions binds to a different reference corner in boundary
    # order. Permit all starting vertices and both windings, preserving the pinned
    # exporter's six-decimal precision without admitting clustered/crossed rings.
    orders = (order[start:] + order[:start] for order in (expected, expected[::-1]) for start in range(4))
    require(any(all(abs(lon-x) < 1e-6 and abs(lat-y) < 1e-6
        for (lon, lat), (x, y) in zip(ring[:-1], order)) for order in orders),
        "GeoJSON rectangle topology/corners differ from the independent geographic reference")
    return feature


def run(state):
    """Export actual GeoJSON and verify a fresh-process export of the same result."""
    state = Path(state)
    config = json.loads((state / "scientist.json").read_text())
    output = state / ("geojson-export-" + uuid.uuid4().hex)
    output.mkdir()
    with Client(config["urls"]["runtime"], config["token"], agent_name=config["username"],
            **{name: Endpoint(config["urls"][name], config["token"]) for name in ("reasoner", "resources", "resolver")}) as client:
        client.initialize_user_scope(local_federation=True)
        session = client.create_session(name="native-export-acceptance")
        context = None
        failure = None
        try:
            context = session.create_context()
            observation = context.observe(ObservationRequest("earth:Region", geometry=RectangularGeometry(
                200000, 200500, 6000000, 6000400, 5, 4))).result(120)
            require(any(item.media_type == "application/geo+json" for item in client.export_formats(observation)),
                "No advertised GeoJSON exporter")
            result = observation.export_to_file(output / "native-region.geojson", media_type="application/geo+json")
            payload = json.loads(result.path.read_bytes())  # Only this small independent reference fixture is materialized.
            feature = validate_reference(payload, observation)
            require(result.bytes_written == result.path.stat().st_size, "GeoJSON exported byte count differs")
            # Exercise the generic caller-facing example in a fresh process with separate credentials.
            reference = output / "native-observation.json"
            observation.to_handle().save(reference)
            env = {key: val for key, val in os.environ.items() if not key.startswith("KLAB_")}
            env["KLAB_AGENT_NAME"] = config["username"]
            env["KLAB_RUNTIME_SERVICE_ID"] = client.runtime_service_id
            for name, url in config["urls"].items():
                env["KLAB_" + name.upper() + "_URL"] = url
                env["KLAB_" + name.upper() + "_TOKEN"] = config["token"]
            script = Path(__file__).parents[2] / "examples/stream_export.py"
            example_path = output / "example-region.geojson"
            process = subprocess.run([sys.executable, str(script), str(reference), str(example_path),
                "--media-type", "application/geo+json"], env=env, capture_output=True, text=True, timeout=120)
            require(process.returncode == 0, client.transport.redact(process.stderr))
            example = json.loads(example_path.read_bytes())
            validate_reference(example, observation)
            return {"schema_id": result.schema_id,
                "media_type": result.media_type, "bytes": result.bytes_written,
                "observation_id": observation.id, "geometry": feature["geometry"],
                "tolerance_degrees": 1e-6, "units_raster_values_validity": "not supplied by this substantial GeoJSON export"}
        except BaseException as error:
            failure = error
            raise
        finally:
            release_owned(context, session, failure=failure)
