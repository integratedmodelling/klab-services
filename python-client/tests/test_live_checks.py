from decimal import Decimal
import json
from copy import deepcopy
from pathlib import Path

import httpx

import pytest

from klab_client.errors import ConfigurationError
from klab_client.experiment import verify_elevation
from live_checks import load_reference, verify_reference, verify_traversals


def reference_payload():
    # Deliberately synthetic oracle, used only to test the acceptance checker.
    return {"schema_version": 1, "semantic_definition": "geography:Elevation in m", "units": "m",
            "crs": "EPSG:3857", "bounds": [200000, 200500, 6000000, 6000400], "shape": [5, 4],
            "curve": "D2_YX", "slice": {"type": "INITIALIZATION", "key": "0-0", "start": 0, "end": 0},
            "source": "synthetic offline checker test, not scientific evidence", "dataset_id": "offline-only",
            "dataset_revision": "1", "sampling": "synthetic", "vertical_datum": "synthetic",
            "method": "independent-provider", "values_yx": ["100"] * 20,
            "absolute_tolerance_m": "0.01", "relative_tolerance": "0.000001"}


def test_rectangular_traversal_oracle_detects_axis_swap_and_missingness():
    # Coordinates (x,y) labelled 10*y+x, with a deliberate missing cell at (2,1).
    yx = (0, 1, 2, 3, 4, 10, 11, None, 13, 14, 20, 21, 22, 23, 24, 30, 31, 32, 33, 34)
    xy = (0, 10, 20, 30, 1, 11, 21, 31, 2, None, 22, 32, 3, 13, 23, 33, 4, 14, 24, 34)
    inverted = (30, 20, 10, 0, 31, 21, 11, 1, 32, 22, None, 2, 33, 23, 13, 3, 34, 24, 14, 4)
    verify_traversals(yx, xy, inverted)
    with pytest.raises(AssertionError, match="grid cell"):
        verify_traversals(yx, yx, inverted)
    changed = list(xy)
    changed[9] = 12
    with pytest.raises(AssertionError, match="missingness"):
        verify_traversals(yx, changed, inverted)


def test_independent_reference_detects_consistently_wrong_but_plausible_data(tmp_path):
    file = tmp_path / "reference.json"
    file.write_text(json.dumps(reference_payload()))
    reference = load_reference(file)
    assert len(reference["sha256"]) == 64
    assert verify_reference([Decimal("100.005")] * 20, reference) == Decimal("0.005")
    wrong_metres = [101] * 20
    verify_elevation(wrong_metres, [101000] * 20)  # former checks alone would accept this
    with pytest.raises(AssertionError, match="Reference mismatch"):
        verify_reference(wrong_metres, reference)
    with pytest.raises(AssertionError, match="missingness"):
        verify_reference([None] + [100] * 19, reference)


@pytest.mark.parametrize("field,value", [("crs", "EPSG:4326"), ("shape", [4, 5]),
    ("values_yx", [None] * 20), ("values_yx", [True] * 20), ("dataset_revision", ""),
    ("absolute_tolerance_m", "NaN"), ("relative_tolerance", "-1"), ("slice", {})])
def test_reference_requires_matching_geometry_provenance_and_finite_tolerances(tmp_path, field, value):
    payload = reference_payload()
    payload[field] = value
    file = tmp_path / "reference.json"
    file.write_text(json.dumps(payload))
    with pytest.raises(ConfigurationError):
        load_reference(file)


def test_missing_reference_is_failure_not_skip():
    with pytest.raises(ConfigurationError, match="KLAB_ELEVATION_REFERENCE"):
        load_reference(None)


@pytest.mark.parametrize("corrupt", [False, True])
def test_live_harness_reconnect_and_failed_fixture_retention(monkeypatch, corrupt):
    """Check harness control flow with synthetic HTTP; this is not live evidence."""
    from klab_client import Client
    from klab_client.experiment import rectangle_geometry
    from test_client import make_client
    from test_live import exercise_live

    base = json.loads((Path(__file__).parent / "fixtures" / "elevation.json").read_text())
    base["geometry"] = rectangle_geometry().to_wire()
    calls, properties = [], {}
    def handler(request):
        path = request.url.path
        calls.append(path)
        if path == "/public/capabilities":
            return httpx.Response(200, json={"serviceId": "runtime-id" if request.url.host == "runtime.invalid" else "reasoner-id"})
        if path == "/createSession":
            return httpx.Response(200, text="s")
        if path in {"/createContext", "/api/v1/connect"}:
            return httpx.Response(200, json={"id": "s.c", "notifications": []})
        if path == "/api/v1/resolve/observable":
            observable = deepcopy(base["observable"])
            observable["urn"] = request.content.decode()
            if observable["urn"].endswith("mm"):
                observable["unit"]["definition"] = "mm"
            return httpx.Response(200, json=observable)
        if path == "/api/v1/submit":
            definition = json.loads(request.content)["observation"]["observable"]["urn"]
            return httpx.Response(200, json=1 if definition == "geography:Region" else 2)
        if path.startswith("/jobs/status/"):
            return httpx.Response(200, json={"status": "FINISHED"})
        if path.startswith("/jobs/retrieve/"):
            result = deepcopy(base)
            result["id"] = 41 if path.endswith("/1") else 42
            return httpx.Response(200, json=result)
        if path.startswith("/jobs/cancel/"):
            return httpx.Response(200, json=False)
        if path == "/api/v1/observation/value":
            point = json.loads(request.content)
            offset, curve = point["offset"], point["curve"]
            if curve == "D2_YX":
                native = offset
            elif curve == "D2_XY":
                native = (offset % 4) * 5 + offset // 4
            else:
                native = (3 - offset % 4) * 5 + offset // 4
            value = 100 + native
            if corrupt and curve == "D2_XY":
                value += 1
            if point["semantics"]:
                value *= 1000
            return httpx.Response(200, text=str(value))
        if path == "/api/v1/contexts":
            return httpx.Response(200, json=[{"configuration": {"id": "s.c"}}])
        if path in {"/releaseContext", "/releaseSession"}:
            return httpx.Response(200, json=True)
        pytest.fail(path)

    for name in ("KLAB_RUNTIME_URL", "KLAB_RUNTIME_TOKEN", "KLAB_REASONER_URL", "KLAB_REASONER_TOKEN", "KLAB_AGENT_NAME"):
        monkeypatch.setenv(name, "synthetic-offline-only")
    monkeypatch.delenv("KLAB_CONTEXT_ID", raising=False)
    monkeypatch.setattr(Client, "from_env", classmethod(lambda cls: make_client(handler)))
    if corrupt:
        with pytest.raises(AssertionError, match="Traversal"):
            exercise_live(lambda key, value: properties.update({key: value}))
        assert "/releaseContext" not in calls and "/releaseSession" not in calls
        assert "elevation_handle" in properties
    else:
        exercise_live(lambda key, value: properties.update({key: value}))
        assert calls.count("/api/v1/connect") == 1
        assert calls[-2:] == ["/releaseContext", "/releaseSession"]
        evidence = json.loads(properties["scientific_acceptance_evidence"])
        assert evidence["valid_cells"] == 20
        assert "identical reread" in evidence["checks"][-1]
