"""Live signed-credential and lifecycle checks on the isolated deterministic fixture."""
import argparse
import json
from pathlib import Path
import time
from decimal import Decimal

from cryptography.hazmat.primitives import serialization
from fixture_hub import token
from workflow_benchmark import make_client, initialize, sample
from klab_client import Context, Session, ObservationImpl
from klab_client.experiment import rectangle_geometry, verify_grid
from klab_client.errors import (KlabError, WaitTimeout, JobFailedError, JobCancelledError,
                                AuthorizationError, AuthenticationError, MissingAssetError)


def require_access_denied(operation):
    """Only explicit ACL denial/nonvisibility counts; failures are not authorization."""
    try:
        operation()
    except (AuthorizationError, MissingAssetError) as error:
        return type(error).__name__
    raise AssertionError("Unauthorized operation succeeded")


def wait_for_marker(path, job, timeout=20):
    until = time.monotonic() + timeout
    while not path.exists():
        if job.status().status in {"FINISHED", "ABORTED", "INTERRUPTED", "EMPTY"}:
            raise AssertionError("Controlled computation ended before its in-flight marker")
        if time.monotonic() >= until:
            raise AssertionError("Controlled computation did not enter within deadline")
        time.sleep(.02)


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
        controls = state / "controls"
        if not controls.is_dir():
            raise AssertionError("Controlled test fixture missing; prepare a fresh state with current fixtures")
        (controls / "reset-field").write_text("reset")
        variable = context.submit(ObservationImpl(urn="", observable=client.reasoner.resolve_observable("geography:Aspect in degree_angle")))
        aspect = variable.result(120)
        verify_grid(aspect)
        for curve in ("D2_XY", "D2_YX", "D2_XInvY"):
            values = aspect.fetch_data(range(20), curve=curve).values
            expected = []
            for offset in range(20):
                x, y = (offset // 4, offset % 4) if curve != "D2_YX" else (offset % 5, offset // 5)
                if curve == "D2_XInvY":
                    y = 3 - y
                expected.append(None if (x, y) == (2, 1) else Decimal(x * 10 + y))
            if tuple(expected) != values:
                raise AssertionError(f"Nonuniform/missing/zero field differs at traversal {curve}")
        evidence["checks"].append("actual nonuniform field, missing cell and valid zero match three coordinate traversals")
        # Explicit entry/release handshake, not a workload-size timing race.
        entered, release = controls / "entered", controls / "release"
        entered.unlink(missing_ok=True)
        release.unlink(missing_ok=True)
        pending = context.submit(ObservationImpl(urn="", observable=client.reasoner.resolve_observable("geography:BathymetricDepth in m")))
        try:
            wait_for_marker(entered, pending)
            with_timeout = False
            try:
                pending.result(.1)
            except WaitTimeout as stopped:
                assert stopped.job is pending
                with_timeout = True
            if not with_timeout or pending.status().status != "WAITING":
                raise AssertionError("In-flight timeout did not preserve the running job")
            before = pending.status().status
            if not pending.cancel():
                raise AssertionError("Controlled running job did not accept cancellation")
            try:
                pending.result(5)
                raise AssertionError("Accepted cancellation did not reach interruption")
            except JobCancelledError:
                evidence["cancellation"] = {"id": pending.id, "before": before, "accepted": True,
                                             "confirmed": pending.status().status == "INTERRUPTED"}
        finally:
            release.write_text("release owned fixture computation")
        evidence["checks"].append("controlled in-flight timeout/handle retention and confirmed cancellation")
    key = serialization.load_pem_private_key((state / "fixture-authority.pem").read_bytes(), password=None)
    expired = token(key, "python-scientist", lifetime=-60)
    invalid = configuration["token"].rsplit(".", 1)[0] + "." + "A" * 342
    for name, credential in (("expired", expired), ("invalid_signature", invalid)):
        bad = {**configuration, "token": credential}
        with make_client(bad) as client:
            try:
                client.runtime.get_context_info()
                raise AssertionError(f"{name} credential was accepted")
            except (AuthenticationError, AuthorizationError):
                pass
        evidence["checks"].append(name + " credential denied by service JWT verification")
    other = {**configuration, "token": configuration["other_token"], "username": "other-scientist"}
    initialize(other)
    with make_client(other) as client:
        accessible = client.runtime.get_context_info()
        if any(seed["context_id"] in info.context_ids for info in accessible):
            raise AssertionError("Private context leaked into another user's allowed catalog")
        evidence["checks"].append("second valid user authenticated and listed permitted contexts successfully")
        attach_denial = require_access_denied(lambda: client.attach_context(seed["context_id"]))
        foreign = Context(client, {"id": seed["context_id"]})
        job_denial = require_access_denied(lambda: foreign.job(seed["job_id"]).result(2))
        evidence["access_denials"] = {"attach": attach_denial, "job": job_denial}
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
