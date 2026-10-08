"""Explicit create/restore/collaborator/release modes on the isolated real fixture."""
import argparse
from decimal import Decimal
import json
from pathlib import Path
import time

from klab_client import Client, Context, Endpoint, ObservationImpl, Session, load_handle, save_handle
from klab_client.errors import AuthorizationError, MissingAssetError, WaitTimeout
from klab_client.experiment import rectangle_geometry, verify_grid


def make_client(config, collaborator=False):
    token = config["other_token"] if collaborator else config["token"]
    username = "other-scientist" if collaborator else config["username"]
    return Client(config["urls"]["runtime"], token, agent_name=username,
        **{name: Endpoint(config["urls"][name], token) for name in ("reasoner", "resources", "resolver")})


def expected_values():
    return tuple(None if (x, y) == (2, 1) else Decimal(x*10+y) for y in range(4) for x in range(5))


def create(client, state, directory):
    session = client.create_session(name="saved-handles-example")
    context = session.create_context(configuration={"persistence": "EXPLICIT_ACTION", "name": "Saved synthetic work",
        "accessRights": {"@CLASS": "org.integratedmodelling.klab.api.authentication.ResourcePrivileges",
            "isPublic": False, "allowedUsers": ["python-scientist", "other-scientist"],
            "allowedGroups": [], "excludedGroups": [], "excludedUsers": [], "allowedServices": []}})
    print(f"Created context {context.id}; retain on failure and release explicitly", flush=True)
    region = context.submit(ObservationImpl(urn="python.fixture:saved-region",
        observable=client.reasoner.resolve_observable("earth:Region"), geometry=rectangle_geometry())).result(120)
    verify_grid(region)
    focused = context.within(region)
    observer = context.configuration.get("observer")
    if not observer or type(observer.get("id")) is not int or observer["id"] <= 0:
        raise AssertionError("Fixture did not prepare a valid observer")
    focused = Context(client, context.configuration, token=focused.get_context_id() + f"#{observer['id']}")
    (state / "controls/reset-field").write_text("reset test-owned field")
    job = focused.submit(ObservationImpl(urn="", observable=client.reasoner.resolve_observable("geography:Aspect in degree_angle")))
    observation = job.result(120)
    verify_grid(observation)
    if observation.fetch_data(range(20), curve="D2_YX").values != expected_values():
        raise AssertionError("Source computation differs from independent nonuniform oracle")
    save_handle(focused, directory / "context.json")
    save_handle(job, directory / "job.json")
    save_handle(observation, directory / "observation.json")
    private = session.create_context(configuration={"persistence": "EXPLICIT_ACTION", "name": "Private negative control"})
    save_handle(private, directory / "private.json")
    resolver = client.resolver.resolve(ObservationImpl(urn="", observable=client.reasoner.resolve_observable("geography:Elevation in m")), focused)
    plan = resolver.result(120)
    if not isinstance(plan, dict):
        raise AssertionError("Resolver did not return its Dataflow DTO")
    save_handle(resolver, directory / "resolver.json")
    (state / "controls/entered").unlink(missing_ok=True)
    (state / "controls/release").unlink(missing_ok=True)
    pending = focused.submit(ObservationImpl(urn="", observable=client.reasoner.resolve_observable("geography:BathymetricDepth in m")))
    deadline = time.monotonic() + 20
    while not (state / "controls/entered").exists():
        if time.monotonic() >= deadline:
            raise AssertionError("Controlled computation did not enter")
        time.sleep(.02)
    try:
        pending.result(.05)
        raise AssertionError("Controlled in-flight wait did not time out")
    except WaitTimeout as error:
        if error.job is not pending:
            raise AssertionError("Timeout lost the original job")
    save_handle(pending, directory / "pending.json")
    return {"passed": True, "mode": "create", "context_id": context.id,
        "job_id": job.id, "observation_id": observation.id, "pending_id": pending.id,
        "scope": focused.get_context_id(), "local_close_only": True}


def restore(client, state, directory, collaborator=False):
    context_ref = load_handle(directory / "context.json")
    context = client.restore_handle(context_ref)
    observation = client.restore_handle(load_handle(directory / "observation.json"))
    verify_grid(observation)
    if context.owned or context.get_context_id() != context_ref.scope or observation._context.get_context_id() != context_ref.scope:
        raise AssertionError("Restoration changed selection or acquired disposal ownership")
    if observation.fetch_data(range(20), curve="D2_YX").values != expected_values():
        raise AssertionError("Fresh-process readback differs from actual source storage")
    if collaborator:
        try:
            client.restore_handle(load_handle(directory / "private.json"))
            raise AssertionError("Unshared context restoration unexpectedly succeeded")
        except (AuthorizationError, MissingAssetError):
            pass  # A valid shared read above proves the credential/stack positive control.
        return {"passed": True, "mode": "collaborator", "observation_id": observation.id,
            "private_denied": True, "verified_cells": 20, "scope": context.get_context_id()}
    job = client.restore_handle(load_handle(directory / "job.json"))
    if job.result(120).id != observation.id:
        raise AssertionError("Restored job and observation identities differ")
    resolver = client.restore_handle(load_handle(directory / "resolver.json"))
    if resolver.service != "resolver" or not isinstance(resolver.result(120), dict):
        raise AssertionError("Resolver job was reinterpreted as a Runtime observation")
    pending = client.restore_handle(load_handle(directory / "pending.json"))
    if pending.status().status not in {"WAITING", "STARTED", "CHANGED"}:
        raise AssertionError("Timed-out computation was not still running after Python-process restart")
    (state / "controls/release").write_text("release test-owned gate")
    completed = pending.result(120)
    if completed.fetch_data([0], curve="D2_YX").values != (Decimal("7.5"),):
        raise AssertionError("Restored timed-out job did not complete its original computation")
    return {"passed": True, "mode": "restore", "job_id": job.id, "observation_id": observation.id,
        "pending_id": pending.id, "resolver_id": resolver.id, "verified_cells": 20,
        "scope": context.get_context_id(), "local_close_only": True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("create", "restore", "collaborator", "release"))
    parser.add_argument("--state-dir", type=Path, required=True)
    parser.add_argument("--handles-dir", type=Path, required=True)
    args = parser.parse_args()
    if not args.handles_dir.is_dir():
        parser.error("The explicitly requested handles directory must already exist")
    config = json.loads((args.state_dir / "scientist.json").read_text())
    with make_client(config, args.mode == "collaborator") as client:
        client.initialize_user_scope(local_federation=True)  # Explicit caller setup, not a restoration side effect.
        if args.mode == "create":
            report = create(client, args.state_dir, args.handles_dir)
        elif args.mode in ("restore", "collaborator"):
            report = restore(client, args.state_dir, args.handles_dir, args.mode == "collaborator")
        else:
            context = client.restore_handle(load_handle(args.handles_dir / "context.json"))
            private = client.restore_handle(load_handle(args.handles_dir / "private.json"))
            if not private.release() or not context.release() or not Session(client, context.session_id).release():
                raise AssertionError("Explicit owner-directed test cleanup was not confirmed")
            report = {"passed": True, "mode": "release", "explicit_cleanup": True}
    (args.state_dir / ("saved-" + args.mode + ".json")).write_text(json.dumps(report, indent=2))
    print(json.dumps(report))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
