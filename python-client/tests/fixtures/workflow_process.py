"""Isolated fault probe of real scenario code; no live services or credentials."""
import json
from copy import deepcopy
from pathlib import Path
import sys
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

from workflows import geojson_export, provider_reference, resume_saved_work


# Geographic coordinates copied from the genuine published GeoJSON encoding.
# Scenario validation uses a separately calculated inverse-Mercator reference.
REFERENCE = {"type": "FeatureCollection", "features": [{"type": "Feature", "id": 42,
    "properties": {"urn": "s.c.42"}, "geometry": {"type": "Polygon", "coordinates": [[
        [1.796631, 47.353705], [1.796631, 47.356139], [1.801122, 47.356139],
        [1.801122, 47.353705], [1.796631, 47.353705]]]}}]}


def change_feature(payload, corruption):
    feature = payload["features"][0]
    rings = feature["geometry"]["coordinates"]
    if corruption in ("identity", "identity-and-cleanup"):
        feature["id"] = 999
    elif corruption == "urn":
        feature["properties"]["urn"] = "different.context.42"
    elif corruption == "coordinates":
        rings[0][1] = [0, 0]
    elif corruption == "clustered":
        rings[0] = [[1.796630, 47.356138], [1.796630, 47.356139], [1.796631, 47.356139],
            [1.796631, 47.356138], [1.796630, 47.356138]]
    elif corruption == "crossed":
        rings[0] = [rings[0][index] for index in (0, 2, 1, 3, 0)]
    elif corruption == "hole":
        rings.append([[1.7980, 47.3545], [1.7990, 47.3545], [1.7985, 47.3550], [1.7980, 47.3545]])
    elif corruption in ("rotated", "reversed"):
        vertices = rings[0][:-1]
        vertices = vertices[1:] + vertices[:1] if corruption == "rotated" else vertices[::-1]
        rings[0] = [*vertices, vertices[0]]


def probe(suite, corruption, state):
    config = {"urls": {name: "https://" + name + ".invalid" for name in
        ("runtime", "reasoner", "resources", "resolver")}, "token": "test-only-token", "username": "test"}
    (state / "scientist.json").write_text(json.dumps(config))
    current = MagicMock()
    current.__enter__.return_value = current
    current.runtime_service_id = "home"
    session = current.create_session.return_value
    context = session.create_context.return_value
    observation = context.observe.return_value.result.return_value
    observation.id, observation.urn = 42, "s.c.42"
    current.export_formats.return_value = [SimpleNamespace(media_type="application/geo+json")]
    current.transport.redact.side_effect = lambda text: text.replace(config["token"], "[REDACTED]")
    calls = []
    def release(name):
        calls.append(name)
        if corruption in ("context-false", "identity-and-cleanup") and name == "context":
            return False
        if corruption == name + "-raises":
            raise OSError("cleanup unavailable")
        return True
    context.release.side_effect = lambda: release("context")
    session.release.side_effect = lambda: release("session")
    if corruption == "create-context":
        session.create_context.side_effect = ValueError("context creation failed")

    payload = deepcopy(REFERENCE)
    if not corruption.startswith("fresh-"):
        change_feature(payload, corruption)
    fresh = deepcopy(payload)
    if corruption.startswith("fresh-"):
        change_feature(fresh, corruption.removeprefix("fresh-"))
    def export(path, **kwargs):
        contents = json.dumps(payload).encode()
        path.write_bytes(contents)
        return SimpleNamespace(path=path, bytes_written=len(contents), schema_id="geojson",
            media_type="application/geo+json")
    observation.export_to_file.side_effect = export
    def child(arguments, **kwargs):
        Path(arguments[3]).write_text(json.dumps(fresh))
        return SimpleNamespace(returncode=0, stderr="")

    result = {"optimization": sys.flags.optimize, "release_calls": calls, "passed": False}
    try:
        if suite == "geojson":
            with patch.object(geojson_export, "Client", return_value=current), patch.object(geojson_export.subprocess, "run", child):
                geojson_export.run(state)
        elif suite == "resume":
            reports = {name: {"passed": True, "job_id": 1, "observation_id": 2, "pending_id": 3}
                for name in ("create", "restore", "collaborator", "release")}
            reports["restore"]["pending_id"] = 999
            if corruption == "failed-report":
                reports["restore"]["pending_id"] = 3
                reports["restore"]["passed"] = False
            def saved_child(arguments, **kwargs):
                mode = arguments[2]
                (state / ("saved-" + mode + ".json")).write_text(json.dumps(reports[mode]))
                return SimpleNamespace(returncode=0, stderr="")
            with patch.object(resume_saved_work.subprocess, "run", saved_child):
                resume_saved_work.run(state)
        else:
            current.runtime.capabilities.return_value.service_id = "foreign"
            environment = {"KLAB_" + name: "test" for name in
                ("RUNTIME_URL", "RUNTIME_TOKEN", "REASONER_URL", "REASONER_TOKEN", "AGENT_NAME")}
            with patch.dict(provider_reference.os.environ, environment, clear=True), patch.object(provider_reference.Client,
                    "from_env", return_value=current):
                provider_reference.exercise_live(lambda *args: None)
        result["passed"] = True
    except BaseException as error:
        result.update(error_type=type(error).__name__, message=str(error), notes=getattr(error, "__notes__", []))
    print(json.dumps(result))
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(probe(sys.argv[1], sys.argv[2], Path(sys.argv[3])))
