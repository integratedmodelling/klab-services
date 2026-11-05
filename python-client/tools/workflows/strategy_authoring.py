"""One owned strategy draft round-trip; no semantic admission or publication."""
import hashlib
import json
from pathlib import Path
from uuid import uuid4

from klab_client import Authoring, Client, Endpoint
from klab_client.dto import check_notifications
from klab_client.evidence import _plain
from klab_client.errors import AuthorizationError, MissingAssetError, ProtocolError, ServerError, SubmissionOutcomeUnknown, TransportError
from .scientific_checks import require

KIND = "OBSERVATION_STRATEGY_DOCUMENT"


def cleanup(api, kind, urn):
    rows = api.transport.request("resources", "DELETE", f"/api/v1/delete/{kind}",
        params={"urn": urn}, ambiguous=True, max_response_bytes=2 * 1024 * 1024)
    require(isinstance(rows, list) and rows, "Owned delete has no acknowledgement")
    for row in rows:
        check_notifications(row, api.transport.redact)
    descriptors = [item for row in rows for key in ("results", "projects") for item in row.get(key, [])]
    require(any(item.get("resourceUrn") == urn and item.get("knowledgeClass") == kind and
        item.get("operation") == "DELETE" for item in descriptors), "Owned delete was not confirmed")
    require(all(asset.urn != urn for asset in api.resources.list(kind, max_response_bytes=2 * 1024 * 1024)),
        "Deleted owned asset remains listed")


def require_missing(api, urn, kind):
    try:
        api.resources.retrieve(urn, kind)
    except MissingAssetError:
        return
    raise AssertionError("Denied asset unexpectedly exists")


def require_write_denied(api, kind, name, body):
    try:
        api.transport.request("resources", "PUT", f"/api/v1/submit/{kind}/ADD",
            params={"urn": name}, text=json.dumps(body), ambiguous=True, max_response_bytes=2 * 1024 * 1024)
    except AuthorizationError:
        return
    except (ProtocolError, ServerError, TransportError):
        raise SubmissionOutcomeUnknown("Denied-write control outcome unknown; do not replay or issue dependent deletes") from None
    raise AssertionError("Ordinary READ-only project/document write was not explicitly denied")


def run(state):
    state = Path(state)
    config = json.loads((state / "scientist.json").read_text())
    operator = json.loads((state / "authoring-operator.json").read_text())
    require(operator.get("allow_owned_authoring") is True and operator["username"] != config["username"],
        "Requires distinct explicitly provisioned owned TEST operator")
    suffix = uuid4().hex[:12]
    lexical = suffix.translate(str.maketrans("0123456789", "ghijklmnop"))
    workspace_name, project_name = "python-strategy-" + suffix, "python-strategy-project-" + suffix
    urn = "python.owned.s" + lexical
    source = "// Owned synthetic draft, not a scientific strategy.\nstrategies " + urn + " version 1.0;\n\n"
    report = {"passed": False, "scientific_execution": "NOT_RUN", "semantic_admission": "NOT_CLAIMED",
        "fixture_principal_role": operator["fixture_principal_role"], "project_deleted": False, "workspace_deleted": False}
    workspace = project = None
    primary = None
    def client(user):
        return Client(config["urls"]["runtime"], user["token"], agent_name=user["username"],
            resources=Endpoint(config["urls"]["resources"], user["token"]), timeout=30)
    with client(operator) as api:
        author = Authoring(api)
        try:
            parsed = author.parse(source, kind=KIND)
            require(parsed.urn == urn and parsed.raw["sourceCode"] == source, "Strategy parser identity/source drift")
            try:
                author.parse("this is not a strategy declaration", kind=KIND)
            except (ProtocolError, ServerError):
                report["invalid_source_refused"] = True
            else:
                raise AssertionError("Invalid strategy source was admitted")
            workspace = author.create_workspace(workspace_name, allow_write=True)
            project = author.create_project(workspace, project_name, allow_write=True)
            receipt = author.create_document(parsed, project, allow_write=True)
            require(receipt.kind == KIND and receipt.urn == urn and receipt.operation == "CREATE",
                "Strategy CREATE acknowledgement differs")
            returned = api.resources.retrieve(urn, KIND)
            require(returned.raw.get("sourceCode") == source and returned.raw.get("projectName") == project_name and
                returned.raw.get("@CLASS") == parsed.raw["@CLASS"], "Stored strategy source/class/project differs")
            with client(config) as ordinary:
                denied_project = project_name + "-denied"
                denied_urn = "python.denied.s" + lexical
                draft = Authoring(ordinary).parse("strategies " + denied_urn + " version 1.0;\n", kind=KIND)
                payload = _plain(draft.raw)
                payload["projectName"] = project_name
                for kind, name, body in (("PROJECT", workspace_name + "/" + denied_project,
                        {"urn": workspace_name + "/" + denied_project}), (KIND, denied_urn, payload)):
                    require_write_denied(ordinary, kind, name, body)
                require_missing(api, denied_project, "PROJECT")
                require_missing(api, denied_urn, KIND)
            report.update(passed=True, kind=KIND, urn=urn, service_id=receipt.service_id,
                source_sha256=hashlib.sha256(source.encode()).hexdigest(), exact_source_round_trip=True,
                actual_project_document_denial=True, denied_assets_absent=True, write_replay=False)
        except BaseException as error:
            primary = error
            raise
        finally:
            if isinstance(primary, SubmissionOutcomeUnknown):
                report["cleanup_limit"] = "Unknown write outcome: preserve for read-only inspection; no dependent deletes"
            else:
                try:
                    if project is not None:
                        cleanup(api, "PROJECT", project_name)
                        report["project_deleted"] = True
                    if workspace is not None:
                        cleanup(api, "WORKSPACE", workspace_name)
                        report["workspace_deleted"] = True
                except BaseException as error:
                    report["cleanup_error_type"] = type(error).__name__
                    if primary is None:
                        raise
                    primary.add_note("Owned authoring cleanup failed: " + type(error).__name__)
            (state / "strategy-authoring.json").write_text(json.dumps(report, indent=2))
    return report
