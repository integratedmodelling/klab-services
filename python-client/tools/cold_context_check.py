"""Real server-registry restart checks; graph remains running between prepare/check."""
import argparse
import json
from pathlib import Path

from klab_client import Context, Session
from workflow_benchmark import make_client, initialize
from verify_fixture import require_access_denied


def prepare(state):
    config = json.loads((state / "scientist.json").read_text())
    initialize(config)
    with make_client(config) as client:
        session = client.create_session(name="persistent-acl-restart")
        private = session.create_context(configuration={"name": "Private persistent context", "persistence": "EXPLICIT_ACTION"})
        shared = session.create_context(configuration={"name": "Explicitly shared persistent context", "persistence": "EXPLICIT_ACTION",
            "accessRights": {"@CLASS": "org.integratedmodelling.klab.api.authentication.ResourcePrivileges",
                             "isPublic": False, "allowedUsers": ["python-scientist", "other-scientist"],
                             "allowedGroups": [], "excludedGroups": [], "excludedUsers": [], "allowedServices": []}})
        excluded = session.create_context(configuration={"name": "Public with explicit exclusion", "persistence": "EXPLICIT_ACTION",
            "accessRights": {"@CLASS": "org.integratedmodelling.klab.api.authentication.ResourcePrivileges",
                             "isPublic": True, "excludedUsers": ["other-scientist"],
                             "allowedUsers": [], "allowedGroups": [], "excludedGroups": [], "allowedServices": []}})
        ids = {"session": session.id, "private": private.id, "shared": shared.id, "excluded": excluded.id}
    other = {**config, "token": config["other_token"], "username": "other-scientist"}
    initialize(other)
    with make_client(other) as collaborator:
        collaborator.attach_context(ids["shared"])  # Positive control for the credential/stack.
        warm_exclusion = verify_exclusion(collaborator, ids["excluded"])
    (state / "cold-context-ids.json").write_text(json.dumps(ids))
    return {"prepared": True, "ids": ids, "warm_exclusion": warm_exclusion,
            "next": "restart only Runtime; keep graphdb alive"}


def verify_exclusion(client, id):
    visible = {id for item in client.runtime.get_context_info() for id in item.context_ids}
    if id in visible:
        raise AssertionError("Explicitly excluded public context leaked into listing")
    return {"attach": require_access_denied(lambda: client.attach_context(id)),
            "scoped_job": require_access_denied(lambda: Context(client, {"id": id}).job(1).status())}


def check(state):
    config = json.loads((state / "scientist.json").read_text())
    ids = json.loads((state / "cold-context-ids.json").read_text())
    initialize(config)
    other = {**config, "token": config["other_token"], "username": "other-scientist"}
    initialize(other)
    with make_client(other) as client:
        visible = {id for item in client.runtime.get_context_info() for id in item.context_ids}
        if ids["private"] in visible or ids["shared"] not in visible:
            raise AssertionError("Persisted context visibility does not enforce actual ACL")
        private_denial = require_access_denied(lambda: client.attach_context(ids["private"]))
        cold_exclusion = verify_exclusion(client, ids["excluded"])
    with make_client(other) as collaborator:
        shared = collaborator.attach_context(ids["shared"])
        if shared.configuration.get("owner") != "python-scientist" or set(shared.configuration.get("accessRights", {}).get("allowedUsers", [])) != {"python-scientist", "other-scientist"}:
            raise AssertionError("Shared persisted ACL was replaced by requester defaults")
        require_access_denied(lambda: Session(collaborator, ids["session"]).create_context(name="must not seize owner session"))
    with make_client(config) as owner:
        private = owner.attach_context(ids["private"])
        rights = private.configuration.get("accessRights", {})
        if private.configuration.get("owner") != config["username"] or rights.get("allowedUsers") != [config["username"]]:
            raise AssertionError("Private persisted owner/ACL changed after restart")
        extra = Session(owner, ids["session"]).create_context(name="owner session remains usable")
        if not extra.release():
            raise AssertionError("Owner's new disposable context did not release")
        excluded = owner.attach_context(ids["excluded"])
        if not excluded.configuration.get("accessRights", {}).get("public", excluded.configuration.get("accessRights", {}).get("isPublic")):
            raise AssertionError("Public rights were not preserved after restart")
        if excluded.configuration.get("accessRights", {}).get("excludedUsers") != ["other-scientist"]:
            raise AssertionError("Explicit exclusion was not preserved after restart")
        with make_client(other) as collaborator:
            warm_exclusion = verify_exclusion(collaborator, ids["excluded"])
        if not excluded.release() or not owner.attach_context(ids["private"]).release() or not owner.attach_context(ids["shared"]).release():
            raise AssertionError("Test-owned persisted context cleanup failed")
        if not Session(owner, ids["session"]).release():
            raise AssertionError("Test-owned session cleanup failed")
    return {"passed": True, "private_denial": private_denial,
            "cold_exclusion": cold_exclusion, "warm_exclusion": warm_exclusion,
            "checks": ["same-federation private denial with empty registry", "owner private reconnect",
                        "explicit cross-user sharing restored without ACL replacement",
                        "collaborator-first restoration preserves parent session authority",
                        "public exclusions enforced in listing/attach/scoped operations before and after restoration", "explicit cleanup"]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("prepare", "check"))
    parser.add_argument("--state-dir", type=Path, required=True)
    args = parser.parse_args()
    result = {"passed": False}
    try:
        result = prepare(args.state_dir) if args.action == "prepare" else check(args.state_dir)
    except Exception as error:
        result["error"] = type(error).__name__ + ": " + str(error)
    (args.state_dir / ("cold-context-" + args.action + ".json")).write_text(json.dumps(result, indent=2))
    print(json.dumps(result))
    raise SystemExit(0 if result.get("passed") or result.get("prepared") else 1)
