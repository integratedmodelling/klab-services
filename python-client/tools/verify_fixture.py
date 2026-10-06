"""Live signed-credential and lifecycle checks on the isolated deterministic fixture."""
import argparse
import json
from pathlib import Path
import time

from cryptography.hazmat.primitives import serialization
from fixture_hub import token
from workflow_benchmark import make_client, initialize, sample
from klab_client import Context, Session, ObservationImpl
from klab_client.experiment import rectangle_geometry
from klab_client.errors import KlabError, WaitTimeout, JobFailedError, JobCancelledError


def verify(state):
    configuration = json.loads((state / "scientist.json").read_text())
    evidence = {"mode": "isolated TEST authority and real deterministic runtime", "checks": []}
    initialize(configuration)
    with make_client(configuration) as client:
        session_id = client.create_session(name="live-lifecycle-security").id
    seed = sample(configuration, session_id, 900000, retain=True)
    evidence["seed"] = seed
    if seed["outcome"] != "verified":
        raise AssertionError("Seed computation failed")
    with make_client(configuration) as client:
        context = client.attach_context(seed["context_id"]).within(seed["region_id"])
        job = context.job(seed["job_id"])
        try:
            job.result(0)
            raise AssertionError("Zero-budget wait did not time out")
        except WaitTimeout as error:
            assert error.job is job
        assert job.result(120).id == seed["observation_id"]
        assert job.cancel() is False and job.result(120).id == seed["observation_id"]
        evidence["checks"].append("completed-job timeout/resume/cancel preserved actual result")
        slope = client.reasoner.resolve_observable("geography:Slope in degree_angle")
        failed = context.submit(ObservationImpl(urn="", observable=slope))
        try:
            failed.result(120)
            raise AssertionError("Missing-model computation unexpectedly succeeded")
        except JobFailedError:
            evidence["failed_job"] = {"id": failed.id, "server_status": failed.status().status,
                                      "scientific_outcome": "confirmed failure"}
        evidence["checks"].append("real missing-model job reported scientific failure")
        # Large real scalar workload gives the cancellation API a real in-flight job.
        # Completion races are reported separately, never labelled confirmed cancellation.
        geometry = rectangle_geometry()
        dimension = geometry.raw["dimensions"][0]
        dimension["shape"] = [1000, 1000]
        dimension["parameters"]["delegate"]["shape"] = (
            "EPSG:3857 POLYGON ((200000 6000000,200000 6100000,300000 6100000,300000 6000000,200000 6000000))")
        large_region_job = context.client.attach_context(seed["context_id"]).submit(ObservationImpl(
            urn="python.fixture:cancellation", name="cancellation", observable=client.reasoner.resolve_observable("earth:Region"),
            geometry=geometry))
        large_region = large_region_job.result(120)
        large = client.attach_context(seed["context_id"]).within(large_region)
        pending = large.submit(ObservationImpl(urn="", observable=client.reasoner.resolve_observable("geography:Elevation in m")))
        before = pending.status().status
        accepted = pending.cancel()
        if accepted:
            try:
                pending.result(120)
                raise AssertionError("Accepted cancellation did not reach interruption")
            except JobCancelledError:
                evidence["cancellation"] = {"id": pending.id, "before": before, "accepted": True,
                                             "confirmed": pending.status().status == "INTERRUPTED"}
        else:
            completed = pending.result(120)
            evidence["cancellation"] = {"id": pending.id, "before": before, "accepted": False,
                                         "confirmed": False, "race": "completed", "observation_id": completed.id}
        evidence["checks"].append("real cancellation lifecycle recorded without confusing completion races")
    key = serialization.load_pem_private_key((state / "fixture-authority.pem").read_bytes(), password=None)
    expired = token(key, "python-scientist", lifetime=-60)
    invalid = configuration["token"].rsplit(".", 1)[0] + "." + "A" * 342
    for name, credential in (("expired", expired), ("invalid_signature", invalid)):
        bad = {**configuration, "token": credential}
        with make_client(bad) as client:
            try:
                client.runtime.get_context_info()
                raise AssertionError(f"{name} credential was accepted")
            except KlabError as error:
                if not ("HTTP 401" in str(error) or "HTTP 403" in str(error)):
                    raise
        evidence["checks"].append(name + " credential denied by service JWT verification")
    other = {**configuration, "token": configuration["other_token"], "username": "other-scientist"}
    with make_client(other) as client:
        try:
            client.attach_context(seed["context_id"])
            raise AssertionError("Other signed-in scientist attached to private context")
        except KlabError:
            pass
        foreign = Context(client, {"id": seed["context_id"]})
        try:
            foreign.job(seed["job_id"]).result(2)
            raise AssertionError("Other scientist retrieved private job result")
        except KlabError:
            pass
    evidence["checks"].append("different valid scientist could not attach/retrieve private context/job")
    with make_client(configuration) as client:
        root = client.attach_context(seed["context_id"])
        if not root.release() or not Session(client, session_id).release():
            raise AssertionError("Explicit test-owned releases failed")
    evidence["checks"].append("local close retained scope; explicit context/session release confirmed")
    evidence["passed"] = True
    return evidence


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state-dir", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    result = {"passed": False}
    try:
        result = verify(args.state_dir)
    except Exception as error:
        # Credential values never form assertion diagnostics in this tool.
        result["error"] = type(error).__name__ + ": " + str(error)
    args.report.write_text(json.dumps(result, indent=2))
    print(json.dumps(result, indent=2))
    raise SystemExit(0 if result["passed"] else 1)
