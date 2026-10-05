import os
import json
from datetime import datetime, timezone

import pytest

from klab_client import Client, ObservationImpl, Session
from klab_client.dto import storage_semantics
from klab_client.errors import ConfigurationError, WaitTimeout
from klab_client.experiment import rectangle_geometry, verify_elevation
from live_checks import load_reference, verify_reference, verify_traversals


def exercise_live(record_property, *, reference=None):
    required = ["KLAB_RUNTIME_URL", "KLAB_RUNTIME_TOKEN", "KLAB_REASONER_URL",
                "KLAB_REASONER_TOKEN", "KLAB_AGENT_NAME"]
    missing = [name for name in required if not os.environ.get(name)]
    if missing:
        raise ConfigurationError("Live acceptance requires: " + ", ".join(missing))
    evidence = {"started_utc": datetime.now(timezone.utc).isoformat(), "checks": []}
    session_id = None
    with Client.from_env() as client:
        evidence["runtime_service_id"] = client.runtime.capabilities().service_id
        evidence["reasoner_service_id"] = client.reasoner.capabilities().service_id
        if client.runtime_service_id:
            assert evidence["runtime_service_id"] == client.runtime_service_id, "Configured home runtime ID differs from actual capabilities"
        id = os.environ.get("KLAB_CONTEXT_ID")
        if id:
            context = client.attach_context(id)
        else:
            session = client.create_session(name="python-thorough-acceptance")
            session_id = session.id
            print(f"Created disposable session {session_id}", flush=True)
            context = session.create_context(configuration={"name": "Thorough elevation acceptance", "persistence": "ONE_OFF"})
        context_id = context.id
        evidence.update(context_id=context_id, created_session_id=session_id)
        print(f"Context {context_id}; retained on any failed assertion/interruption", flush=True)
        region_semantics = client.reasoner.resolve_observable("geography:Region")
        region_job = context.submit(ObservationImpl(urn="staging.storage:rectangle", name="rectangle",
                                                    observable=region_semantics, geometry=rectangle_geometry()))
        print(f"Region job {region_job.id}; scope {context.get_context_id()}", flush=True)
        record_property("region_handle", json.dumps({"context_id": context_id, "session_id": session_id,
                                                     "job_id": region_job.id, "scope": context.get_context_id()}))
        region = region_job.result(120)
        assert region.id > 0 and region.raw.get("resolvedCoverage") == 1
        focused = context.within(region)
        elevation_semantics = client.reasoner.resolve_observable("geography:Elevation in m")
        job = focused.submit(ObservationImpl(urn="", observable=elevation_semantics))
        print(f"Elevation job {job.id}; resume in scope {focused.get_context_id()}", flush=True)
        record_property("elevation_handle", json.dumps({"context_id": context_id, "session_id": session_id,
                                                        "job_id": job.id, "scope": focused.get_context_id()}))
        evidence.update(region_job_id=region_job.id, region_id=region.id, elevation_job_id=job.id,
                        submission_scope=focused.get_context_id(), initial_job_status=job.status().status)
        # A zero waiting budget tests preservation/resume without relying on a slow model.
        # It does not assert that the remote computation was still running.
        with pytest.raises(WaitTimeout) as stopped:
            job.result(timeout=0)
        assert stopped.value.job is job
        elevation = job.result(120)
        assert job.status().status == "FINISHED"
        assert elevation.id > 0 and elevation.raw.get("resolvedCoverage") == 1
        assert elevation.units == "m" and elevation.observable.raw["urn"] == elevation_semantics.raw["urn"]
        assert elevation.geometry is not None
        space = [d for d in elevation.geometry.raw["dimensions"] if d.get("type") == "SPACE"]
        assert len(space) == 1 and space[0].get("shape") == [5, 4], "Returned scientific grid must be 5 x 4"
        evidence.update(elevation_id=elevation.id, semantic_definition=elevation.observable.raw["urn"],
                        coverage=elevation.raw["resolvedCoverage"], spatial_shape=space[0]["shape"], units=elevation.units)
        parameters = space[0].get("parameters", {})
        parameters = parameters.get("delegate", parameters)
        evidence["returned_grid_parameters"] = {key: parameters[key] for key in
            ("proj", "bbox", "shape", "sgrid", "gridurn") if key in parameters}
        yx = elevation.fetch_data(range(20), curve="D2_YX")
        xy = elevation.fetch_data(range(20), curve="D2_XY")
        inverted = elevation.fetch_data(range(20), curve="D2_XInvY")
        verify_traversals(yx.values, xy.values, inverted.values)
        mm_semantics = storage_semantics(client.reasoner.resolve_observable("geography:Elevation in mm"))
        mm = elevation.fetch_data(range(20), curve="D2_YX", semantics=mm_semantics)
        assert yx.units == "m" and mm.units == "mm"
        verify_elevation(yx.values, mm.values)
        evidence.update(curve="D2_YX", slice=yx.slice, metres=yx.text, millimetres=mm.text,
                        converted_units=mm.units, valid_cells=sum(value is not None for value in yx.values))
        evidence["checks"].extend(["server completion and committed IDs", "full coverage/units/grid",
                                   "timeout handle preserved", "three traversal coordinate/missingness agreement",
                                   "all-cell metres-to-millimetres invariant"])
        if reference is not None:
            error = verify_reference(yx.values, reference)
            evidence["reference"] = {key: reference[key] for key in (
                "sha256", "method", "source", "dataset_id", "dataset_revision", "sampling", "vertical_datum")}
            evidence["reference"].update(max_absolute_error_m=str(error),
                absolute_tolerance_m=str(reference["absolute_tolerance_m"]),
                relative_tolerance=str(reference["relative_tolerance"]))
            evidence["checks"].append("all cells matched separately obtained reference")
        assert job.cancel() is False, "Completed-job cancellation should report no running job cancelled"
        assert job.result(120).id == elevation.id
        evidence["checks"].append("completed cancellation preserved result")
        # Persist enough identifiers before closing the original local transport.
        record_property("scientific_evidence_before_reconnect", client.transport.redact(json.dumps(evidence)))

    with Client.from_env() as reopened:
        attached = reopened.attach_context(context_id)
        assert attached.id == context_id and attached.owned is False
        resumed = attached.within(region.id).job(job.id)
        assert resumed.status().status == "FINISHED"
        retrieved = resumed.result(120)
        assert retrieved.id == elevation.id and retrieved.units == elevation.units
        assert retrieved.observable.raw["urn"] == elevation.observable.raw["urn"]
        repeated = retrieved.fetch_data(range(20), curve="D2_YX")
        assert repeated.values == yx.values, "Local close/reconnect changed scientific values or missingness"
        assert context_id in [id for info in reopened.runtime.get_context_info(attached) for id in info.context_ids]
        evidence["checks"].append("new client attachment, focused job resume, identical reread and context visibility")
        if session_id is not None:
            assert attached.release(), "Disposable context release was not confirmed"
            assert Session(reopened, session_id).release(), "Disposable session release was not confirmed"
            evidence["cleanup"] = "explicit disposable context and session release confirmed"
        else:
            evidence["cleanup"] = "user-owned context retained; no remote release requested"
        evidence["finished_utc"] = datetime.now(timezone.utc).isoformat()
        sanitized = reopened.transport.redact(json.dumps(evidence))
        record_property("scientific_acceptance_evidence", sanitized)
        print(sanitized, flush=True)


@pytest.mark.live
def test_live_elevation(record_property):
    exercise_live(record_property)


@pytest.mark.live
def test_live_elevation_reference(record_property):
    reference = load_reference(os.environ.get("KLAB_ELEVATION_REFERENCE"))
    exercise_live(record_property, reference=reference)
