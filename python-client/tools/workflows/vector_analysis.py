"""Actual Java native geometry export with independently specified geographic corners."""
import json
from pathlib import Path

from klab_client import Client, Endpoint, ObservationRequest, RectangularGeometry, export_for_analysis, open_vector
from .geojson_export import validate_reference
from .scientific_checks import release_owned, require


def run(state):
    state = Path(state)
    config = json.loads((state / "scientist.json").read_text())
    with Client(config["urls"]["runtime"], config["token"], agent_name=config["username"],
            **{name: Endpoint(config["urls"][name], config["token"])
               for name in ("reasoner", "resources", "resolver")}) as client:
        client.initialize_user_scope(local_federation=True)
        session = client.create_session(name="owned native vector analysis")
        context = None
        primary = None
        try:
            context = session.create_context()
            observation = context.observe(ObservationRequest("consolidation:Region",
                geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4))).result(120)
            require(any(item.media_type == "application/geo+json" for item in client.export_formats(observation)),
                "No advertised native GeoJSON exporter")
            artifact = export_for_analysis(observation, state / "native-vector.geojson",
                media_type="application/geo+json", max_bytes=1024 * 1024)
            payload = json.loads(artifact.export.path.read_bytes())
            feature = validate_reference(payload, observation)
            analysis = open_vector(artifact, max_bytes=1024 * 1024, max_features=1, max_coordinates=5, max_cells=256)
            require(len(analysis.features) == 1 and len(analysis.dataframe) == 1,
                "Vector adapter changed the feature count")
            require(analysis.features[0]["id"] == observation.id and
                analysis.dataframe.iloc[0]["urn"] == observation.urn,
                "Vector adapter changed original observation identity")
            require(list(map(list, analysis.dataframe.geometry.iloc[0].exterior.coords)) ==
                feature["geometry"]["coordinates"][0], "Vector adapter changed native geographic positions")
            require(analysis.dataframe.crs.to_string() == "OGC:CRS84", "Vector CRS is not explicit lon/lat")
            require(analysis.sha256 == artifact.sha256, "Vector analysis is not bound to exact native bytes")
            return {"passed": True, "observation_id": observation.id, "sha256": analysis.sha256,
                "schema_id": artifact.export.schema_id, "features": 1, "coordinates": analysis.coordinate_count,
                "crs": "OGC:CRS84", "geographic_oracle_tolerance_degrees": 1e-6,
                "geometry": feature["geometry"], "historical_replication": "NOT_CLAIMED",
                "server_revision": "NOT_ESTABLISHED", "reprojection_or_repair": "NOT_PERFORMED"}
        except BaseException as error:
            primary = error
            raise
        finally:
            release_owned(context, session, failure=primary)
