"""Opt-in ADD-only authoring on a fresh operator-owned Resources fixture."""
import json
import os
from uuid import uuid4

import pytest

from klab_client import Authoring, Client, Endpoint
from klab_client.dto import check_notifications
from klab_client.errors import ConfigurationError, MissingAssetError, ProtocolError, ServerError, SubmissionOutcomeUnknown

pytestmark = [pytest.mark.live, pytest.mark.functional]


def test_real_java_owned_add_only_source_round_trip(functional_state, record_property):
    if os.environ.get("KLAB_AUTHORING_ALLOW_OWNED_WORK") != "1":
        raise ConfigurationError("Authoring acceptance requires explicit owned-work authorization")
    config = json.loads((functional_state / "scientist.json").read_text())
    suffix = uuid4().hex[:12]
    source_suffix = suffix.translate(str.maketrans("0123456789", "ghijklmnop"))
    workspace_name, project_name = "python-owned-" + suffix, "python-owned-project-" + suffix
    namespace = "python.owned.n" + source_suffix
    source = "namespace " + namespace + ";\n"
    script_urn = "python.owned.s" + source_suffix
    script_source = 'script ' + script_urn + '\n    "Owned ADD-only authoring fixture"\n    version 1.0\n\naction main:\n    return true\n'
    report = {"scientific_reproduction": False, "workspace_deleted": False, "project_deleted": False}
    with Client(config["urls"]["runtime"], config["token"], agent_name=config["username"],
            resources=Endpoint(config["urls"]["resources"], config["token"]), timeout=15) as api:
        author = Authoring(api)
        workspace = project = None
        primary = None
        try:
            parsed = author.parse(source)
            assert parsed.urn == namespace and parsed.raw["sourceCode"] == source
            with pytest.raises((ProtocolError, ServerError)):
                author.parse("this is not a valid namespace declaration")
            workspace = author.create_workspace(workspace_name, allow_write=True)
            project = author.create_project(workspace, project_name, allow_write=True)
            receipt = author.create_document(parsed, project, allow_write=True)
            assert receipt.operation == "CREATE" and receipt.urn == namespace
            returned = api.resources.retrieve(namespace, "NAMESPACE")
            assert returned.raw["sourceCode"] == source
            assert returned.raw["projectName"] == project_name
            script = author.parse(script_source, kind="SCRIPT")
            script_receipt = author.create_document(script, project, allow_write=True)
            assert script_receipt.urn == script_urn
            returned_script = api.resources.retrieve(script_urn, "BEHAVIOR")
            assert returned_script.raw["sourceCode"] == script_source
            assert returned_script.raw["behaviorType"] == "SCRIPT"
            report.update(source_round_trip_verified=True, script_round_trip_verified=True, explicit_creations=4,
                          parser_rejection_verified=True, overwrite_supported=False,
                          lifecycle_limit="Only newly confirmed assets in this isolated owned fixture are deleted")
        except BaseException as error:
            primary = error
            raise
        finally:
            failed = []
            if isinstance(primary, SubmissionOutcomeUnknown):
                report["cleanup_limit"] = "Unknown write outcome: do not issue dependent deletes; preserve owned fixture for inspection"
            else:
                for key, kind, urn, receipt in (("project_deleted", "PROJECT", project_name, project),
                                                ("workspace_deleted", "WORKSPACE", workspace_name, workspace)):
                    if receipt is not None:
                        try:
                            changes = api.transport.request("resources", "DELETE", f"/api/v1/delete/{kind}",
                                params={"urn": urn}, ambiguous=True, max_response_bytes=2 * 1024 * 1024)
                            assert isinstance(changes, list) and changes
                            for change in changes:
                                check_notifications(change, api.transport.redact)
                            with pytest.raises(MissingAssetError):
                                api.resources.retrieve(urn, kind)
                            report[key] = True
                        except BaseException as error:
                            failed.append(type(error).__name__)
                            break  # Never cascade cleanup after an unconfirmed delete.
            record_property("owned_add_only_authoring", json.dumps(report))
            if failed:
                if primary is not None:
                    primary.add_note("Owned authoring cleanup failed: " + ",".join(failed))
                else:
                    raise AssertionError("Owned authoring cleanup failed: " + ",".join(failed))
