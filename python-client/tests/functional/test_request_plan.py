"""Typed option-to-actual-Java evidence; serializer equality is not scientific proof."""
from decimal import Decimal
import json
import math

import pytest

from klab_client import (Client, Endpoint, ContextOptions, GridOptions, ObservationOptions,
    ObservationRequest, RectangularGeometry, ResolutionConstraint, fetch_execution_evidence)
from klab_client.errors import InvalidRequestError, UnsupportedOperationError
from klab_client.experiment import verify_grid
from workflows.request_plan import model_urns

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_typed_options_match_real_served_plan_and_execution(functional_state):
    state = functional_state
    config = json.loads((state / "scientist.json").read_text())
    records = []
    session = None
    contexts = []
    primary = None
    with Client(config["urls"]["runtime"], config["token"], agent_name=config["username"],
        **{n: Endpoint(config["urls"][n], config["token"]) for n in ("resources", "reasoner", "resolver")}) as api:
        try:
            api.initialize_user_scope(local_federation=True)
            session = api.create_session(name="owned served request-plan acceptance")
            for name, options, expected in (
                ("baseline", ObservationOptions(), None),
                ("namespace", ObservationOptions(namespace="consolidation.lexical"), Decimal("66.5")),
                ("scenarios", ObservationOptions(scenarios=["consolidation.scenario"]), Decimal("234.5"))):
                context = session.create_context(options=ContextOptions(name=name, persistence="EXPLICIT_ACTION"))
                contexts.append(context)
                region = context.observe(ObservationRequest("consolidation:Region",
                    geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4))).result(120)
                focused = context.within(region)
                observation = focused.observe("consolidation:Elevation of consolidation:Region in m", options=options).result(120)
                verify_grid(region)
                verify_grid(observation)
                if name == "baseline":
                    assert observation.fetch_data([0, 4, 6, 8, 12, 15, 16, 17, 19, 0], curve="D2_YX").values == (
                        Decimal("0"), Decimal("5"), None, Decimal("-0.125"), Decimal("12.5"),
                        Decimal("16777217.125"), Decimal("11"), None, Decimal("41"), Decimal("0"))
                else:
                    assert observation.fetch_data(range(20), curve="D2_YX").values == (expected,) * 20
                evidence = fetch_execution_evidence(observation)
                urns = model_urns(evidence)
                expected_namespace = {"baseline": "consolidation.fixture", "namespace": "consolidation.lexical",
                    "scenarios": "consolidation.scenario"}[name]
                assert any(urn.startswith(expected_namespace + ".") or urn.startswith(expected_namespace + ":") for urn in urns), urns
                assert evidence.partial and evidence.durable_root_commit.startswith("unknown")
                assert observation._context.get_context_id() == focused.get_context_id()
                records.append({"option": name, "requested": True, "accepted": True,
                    "reflected_in_plan": True, "executed": True, "model_urns": urns,
                    "expected_constant": str(expected) if expected is not None else "independent nonuniform offsets",
                    "verified_cells": 10 if name == "baseline" else 20,
                    "geometry_verified": "5x4 EPSG:3857 bbox200000,200500,6000000,6000400",
                    "focus_preserved": observation._context.get_context_id() == focused.get_context_id()})
            grid_context = session.create_context(options=ContextOptions(name="served grid",
                grid=GridOptions(200000, 6000000, 100, strict=True, snap=False)))
            contexts.append(grid_context)
            alignment = grid_context.configuration.get("gridAlignment", {})
            assert alignment.get("projection") == "EPSG:3857"
            circumference = 2 * math.pi * 6378137  # EPSG:3857's independently known spherical radius.
            normalized = circumference / round(circumference / 100)
            assert math.isclose(alignment.get("stepX"), normalized, rel_tol=0, abs_tol=1e-9)
            assert math.isclose(alignment.get("stepY"), normalized, rel_tol=0, abs_tol=1e-9)
            assert "Global grid spacing normalized to whole cells over the world extent" in alignment.get("warnings", [])
            records.append({"option": "grid", "requested": True, "accepted": True,
                "reflected_in_configuration": True, "reflected_in_plan": "NOT_ASSERTED", "executed": False,
                "requested_span": 100, "served_stepX": alignment["stepX"], "served_stepY": alignment["stepY"],
                "normalization_warning_verified": True})
            calls = []
            api.transport._http.event_hooks["request"].append(lambda request: calls.append(request.url.path))
            with pytest.raises(UnsupportedOperationError):
                ObservationOptions(selected_model="consolidation.lexical.forced")
            with pytest.raises(InvalidRequestError):
                ObservationOptions(namespace="one", constraints=[ResolutionConstraint("ResolutionNamespace", ("two",))])
            with pytest.raises(UnsupportedOperationError):
                ResolutionConstraint("UsingModel", ("model",))
            assert calls == []
            records.append({"option": "forced-model/conflicting-lexical/unknown-constraint", "rejected": True, "http_requests": 0})
        except BaseException as error:
            primary = error
            raise
        finally:
            failures = []
            for owned in [*reversed(contexts), *([session] if session is not None else [])]:
                try:
                    if owned.release() is not True:
                        failures.append("release")
                except Exception:
                    failures.append("release")
            (state / "request-plan.json").write_text(json.dumps({"records": records,
                "owned_releases_confirmed": not failures, "full_matrix": "PARTIAL",
                "limits": "Observer/project material-plan controls not yet established; no forced-model consumer or complete lineage"}, indent=2))
            if failures:
                if primary is not None:
                    primary.add_note("Owned cleanup unconfirmed")
                else:
                    raise AssertionError("Owned cleanup unconfirmed")
