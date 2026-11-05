"""Link one advertised canonical scenario model to this result's recorded plan."""
from dataclasses import asdict
import json
from pathlib import Path

from klab_client import (Client, Endpoint, ObservationOptions, ObservationRequest,
    RectangularGeometry, ResourceInspector, fetch_execution_evidence)
from klab_client.errors import SubmissionOutcomeUnknown
from klab_client.evidence import _plain
from .request_plan import model_urns
from .scientific_checks import require


def select_candidate(inspector):
    listed = inspector.list("MODEL")
    matched = [item for item in listed if item.urn.startswith(("consolidation.scenario.", "consolidation.scenario:"))]
    require(len(matched) == 1, "Require one unique authored scenario candidate, never first result or label")
    candidate = inspector.retrieve(matched[0].urn, "MODEL")
    require(candidate.urn == matched[0].urn and candidate.origin == matched[0].origin,
        "Model retrieval changed canonical identity or Resources origin")
    require(candidate.selection == "advertised candidate; not evidence of Resolver selection or execution"
        and "not a dependency closure" in candidate.dependency_completeness, "Inspection overclaims selection/closure")
    require(candidate.namespace == candidate.raw.get("namespace") and
        candidate.project_name == candidate.raw.get("projectName") and
        _plain(candidate.version) == _plain(candidate.raw.get("version")), "Inspection invented or changed advertised metadata")
    return candidate, listed


def verify_result(candidate, evidence, units, values):
    require(units == "m" and values == (234.5,) * 20, "Original scenario cells/unit differ from independent source literal")
    models = model_urns(evidence)
    require(candidate.urn in models, "Inspected canonical model absent from this original result's successful recorded plan")
    return models


def run(state):
    state = Path(state)
    config = json.loads((state / "scientist.json").read_text())
    report = {"passed": False, "context_released": False, "session_released": False,
        "historical_admission": "NOT_CLAIMED", "full_dependency_access_revision_matrix": "NOT_TESTED"}
    session = context = None
    primary = None
    with Client(config["urls"]["runtime"], config["token"], agent_name=config["username"],
            **{name: Endpoint(config["urls"][name], config["token"]) for name in ("reasoner", "resources", "resolver")}) as api:
        def save():
            encoded = json.dumps(report, indent=2, allow_nan=False)
            require(len(encoded.encode("utf-8")) <= 2 * 1024 * 1024, "Inspection report exceeds byte budget")
            require(api.transport.redact(encoded) == encoded, "Inspection report contains credentials")
            (state / "current-model-plan.json").write_text(encoded, encoding="utf-8")
        try:
            api.initialize_user_scope(local_federation=True)
            status = api.transport.request("reasoner", "GET", "/public/status", max_response_bytes=512 * 1024)
            report["reasoner_advisories"] = status.get("advisories", [])
            candidate, listed = select_candidate(ResourceInspector(api))
            report["listed_model_urns"] = [item.urn for item in listed]
            report["inspected_model"] = {"urn": candidate.urn, "origin": asdict(candidate.origin),
                "namespace": candidate.namespace, "project_name": candidate.project_name,
                "version": _plain(candidate.version), "resource_urns": _plain(candidate.resource_urns),
                "listed_dependencies": None if candidate.listed_dependencies is None else
                    [_plain(item.raw) for item in candidate.listed_dependencies],
                "selection": candidate.selection, "dependency_completeness": candidate.dependency_completeness,
                "raw": _plain(candidate.raw)}
            save()
            session = api.create_session(name="owned current model-plan acceptance")
            context = session.create_context()
            region = context.observe(ObservationRequest("consolidation:Region",
                geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4))).result(120)
            job = context.within(region).observe("consolidation:Elevation of consolidation:Region in m",
                options=ObservationOptions(scenarios=["consolidation.scenario"]))
            observation = job.result(120)
            data = observation.fetch_data(range(20), curve="D2_YX")
            evidence = fetch_execution_evidence(observation)
            report.update(job_id=job.id, observation_id=observation.id, units=observation.units,
                values=list(data.values), served_model_urns=model_urns(evidence), recorded_evidence_partial=evidence.partial)
            save()
            require(data.units == observation.units, "Point read/observation units disagree")
            verify_result(candidate, evidence, observation.units, data.values)
            report.update(passed=True, verified_cells=20, expected_value=234.5)
        except BaseException as error:
            primary = error
            report["error_type"] = type(error).__name__
            raise
        finally:
            if isinstance(primary, SubmissionOutcomeUnknown):
                report["cleanup_limit"] = "Unknown mutation outcome: no dependent releases; preserve private fixture"
            else:
                failures = []
                for key, scope in (("context_released", context), ("session_released", session)):
                    if scope is not None:
                        try:
                            report[key] = scope.release() is True
                            require(report[key], "Owned release not confirmed")
                        except BaseException as error:
                            failures.append(type(error).__name__)
                if failures:
                    report.update(passed=False, cleanup_error_types=failures)
                    if primary is None:
                        save()
                        raise AssertionError("Owned model-plan cleanup failed")
                    primary.add_note("Owned model-plan cleanup failed: " + ",".join(failures))
            save()
    return report
