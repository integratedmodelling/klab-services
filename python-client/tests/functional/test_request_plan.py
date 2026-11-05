"""Typed option-to-actual-Java evidence; serializer equality is not scientific proof."""
from decimal import Decimal
import json
import math
from pathlib import Path

import pytest

from klab_client import (Client, Endpoint, ContextOptions, GridOptions, ObservationOptions,
    ObservationRequest, RectangularGeometry, ResolutionConstraint, fetch_execution_evidence)
from klab_client.errors import (InvalidRequestError, UnsupportedOperationError, AuthorizationError,
    MissingAssetError, SubmissionOutcomeUnknown)
from klab_client.experiment import verify_grid
from workflows.request_plan import model_urns

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_typed_options_match_real_served_plan_and_execution(functional_state):
    state = functional_state
    config = json.loads((state / "scientist.json").read_text())
    oracle = json.loads((Path(__file__).parents[1] / "acceptance/scenarios/s-selection-oracle.json").read_text())
    assert [(row["expected_model_namespace"], row["literal_decimal"], row["units"], row["cells"])
        for row in oracle["cases"]] == [("consolidation.lexical", "66.5", "m", 20),
            ("consolidation.scenario", "234.5", "m", 20)]
    records = []
    session = None
    contexts = []
    primary = None
    expectations = {key: {"status": "NOT_RUN", "reason": "Not reached"}
        for key in ("s-plan-output", "s-private-denial", "s-local-negative", "s-cleanup")}
    phase = "s-plan-output"
    with Client(config["urls"]["runtime"], config["token"], agent_name=config["username"],
        **{n: Endpoint(config["urls"][n], config["token"]) for n in ("resources", "reasoner", "resolver")}) as api:
        try:
            api.initialize_user_scope(local_federation=True)
            session = api.create_session(name="owned served request-plan acceptance")
            for name, options, expected in (
                ("baseline", ObservationOptions(), None),
                ("namespace", ObservationOptions(namespace="consolidation.lexical"), Decimal("66.5")),
                ("scenarios", ObservationOptions(scenarios=["consolidation.scenario"]), Decimal("234.5"))):
                configuration = ContextOptions(name=name, persistence="EXPLICIT_ACTION").to_wire()
                configuration["accessRights"] = {"public": False, "allowedUsers": [config["username"]],
                    "excludedUsers": [], "allowedGroups": [], "excludedGroups": []}
                context = session.create_context(configuration=configuration)
                contexts.append(context)
                region = context.observe(ObservationRequest("consolidation:Region",
                    geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4))).result(120)
                focused = context.within(region)
                observation = focused.observe("consolidation:Elevation of consolidation:Region in m", options=options).result(120)
                verify_grid(region)
                verify_grid(observation)
                if name == "baseline":
                    values = observation.fetch_data([0, 4, 6, 8, 12, 15, 16, 17, 19, 0], curve="D2_YX")
                    assert values.values == (
                        Decimal("0"), Decimal("5"), None, Decimal("-0.125"), Decimal("12.5"),
                        Decimal("16777217.125"), Decimal("11"), None, Decimal("41"), Decimal("0"))
                else:
                    values = observation.fetch_data(range(20), curve="D2_YX")
                    assert values.values == (expected,) * 20
                assert values.units == "m" and values.observable == observation.observable.raw["urn"]
                evidence = fetch_execution_evidence(observation)
                urns = model_urns(evidence)
                expected_namespace = {"baseline": "consolidation.fixture", "namespace": "consolidation.lexical",
                    "scenarios": "consolidation.scenario"}[name]
                assert any(urn.startswith(expected_namespace + ".") or urn.startswith(expected_namespace + ":") for urn in urns), urns
                assert evidence.partial and evidence.durable_root_commit.startswith("unknown")
                assert evidence.provenance.observation == observation.to_handle()
                assert observation._context.get_context_id() == focused.get_context_id()
                records.append({"option": name, "requested": True, "accepted": True,
                    "reflected_in_plan": True, "executed": True, "model_urns": urns,
                    "expected_constant": str(expected) if expected is not None else "independent nonuniform offsets",
                    "verified_cells": 10 if name == "baseline" else 20,
                    "observation": observation.to_handle().to_dict(),
                    "activity_ids": [activity.id for activity in evidence.activities],
                    "plan_partial": evidence.partial, "durable_root_commit": evidence.durable_root_commit,
                    "geometry_verified": "5x4 EPSG:3857 bbox200000,200500,6000000,6000400",
                    "focus_preserved": observation._context.get_context_id() == focused.get_context_id()})
            expectations["s-plan-output"] = {"status": "PASS", "reason": "Three actual request-associated plans and independently sourced values; exact observation handles and focus"}
            phase = "s-private-denial"
            # Valid distinct signed principal, not expired authentication disguised as denial.
            negative_calls = []
            with Client(config["urls"]["runtime"], config["other_token"], agent_name="other-scientist",
                **{n: Endpoint(config["urls"][n], config["other_token"]) for n in ("resources", "reasoner", "resolver")}) as other:
                other.initialize_user_scope(local_federation=True)
                positive = other.create_session(name="owned valid negative-principal control")
                assert positive.release() is True
                def negative_audit(request):
                    assert not any(p in request.url.path for p in ("/submit", "/create", "/release", "/jobs/cancel/"))
                    assert request.method != "DELETE"
                    negative_calls.append({"method": request.method, "path": request.url.path})
                other.transport._http.event_hooks["request"].append(negative_audit)
                with pytest.raises((AuthorizationError, MissingAssetError)) as denial:
                    other.attach_context(context.id)
            assert negative_calls and any(c["path"].endswith("/api/v1/connect") for c in negative_calls)
            records.append({"negative": "non-admitted valid signed principal private context", "context_id": context.id,
                "error_type": type(denial.value).__name__, "calls": negative_calls,
                "scientific_submissions": 0, "positive_session_released": True})
            expectations["s-private-denial"] = {"status": "PASS", "reason": "Actual valid distinct principal refused private owner context; no submit or foreign mutation"}
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
            phase = "s-local-negative"
            api.transport._http.event_hooks["request"].append(lambda request: calls.append(request.url.path))
            with pytest.raises(UnsupportedOperationError):
                ObservationOptions(selected_model="consolidation.lexical.forced")
            with pytest.raises(InvalidRequestError):
                ObservationOptions(namespace="one", constraints=[ResolutionConstraint("ResolutionNamespace", ("two",))])
            with pytest.raises(UnsupportedOperationError):
                ResolutionConstraint("UsingModel", ("model",))
            assert calls == []
            records.append({"option": "forced-model/conflicting-lexical/unknown-constraint", "rejected": True, "http_requests": 0})
            expectations["s-local-negative"] = {"status": "PASS", "reason": "L1 unsupported forced model/conflicting options refused before HTTP"}
        except BaseException as error:
            primary = error
            expectations[phase] = {"status": "FAIL", "reason": type(error).__name__}
            raise
        finally:
            failures = []
            for owned in [*reversed(contexts), *([session] if session is not None else [])]:
                if isinstance(primary, SubmissionOutcomeUnknown):
                    failures.append("unknown submission: no dependent cleanup or replay")
                    break
                try:
                    if owned.release() is not True:
                        failures.append("release")
                except Exception:
                    failures.append("release")
            expectations["s-cleanup"] = {"status": "FAIL" if failures else "PASS",
                "reason": "Unknown outcome or cleanup unconfirmed" if failures else "Owned contexts/session and negative principal positive-control session released"}
            success = primary is None and not failures
            axes = {axis: {"status": "PASS" if success else "NOT_RUN",
                "reason": "Bounded source-justified model/plan/output and authorization control" if success else "Inspect explicit check failure; not certified"}
                for axis in ("python", "java_contract", "scientific", "security_lifecycle", "environment_artifacts")}
            (state / "request-plan.json").write_text(json.dumps({"records": records, "expectations": expectations, "axes": axes,
                "owned_releases_confirmed": not failures, "full_matrix": "PARTIAL",
                "limits": "Observer/project material-plan controls not yet established; no forced-model consumer or complete lineage"}, indent=2))
            if failures:
                if primary is not None:
                    primary.add_note("Owned cleanup unconfirmed")
                else:
                    raise AssertionError("Owned cleanup unconfirmed")
