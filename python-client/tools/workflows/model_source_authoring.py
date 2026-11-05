"""Owned model-containing draft creation/inspection, not execution or publication."""
import hashlib
import json
from pathlib import Path
from uuid import uuid4

from klab_client import Authoring, Client, Endpoint, ResourceInspector
from klab_client.errors import ProtocolError, ServerError, SubmissionOutcomeUnknown
from klab_client.evidence import _plain
from .current_model_plan import encode_report
from .scientific_checks import require
from .strategy_authoring import cleanup, require_missing, require_write_denied

KIND = "NAMESPACE"
MODEL_CLASS = "org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl"


def model_statement(payload, namespace):
    require(isinstance(payload, dict) and payload.get("urn") == namespace,
        "Authored namespace identity changed")
    models = [row for row in payload.get("statements", []) if isinstance(row, dict)
        and row.get("@CLASS") == MODEL_CLASS]
    require(len(models) == 1, "Require exactly one authored model statement")
    urn = models[0].get("urn")
    require(isinstance(urn, str) and urn.startswith(namespace + "."), "Model has no namespace-qualified source identity")
    return models[0]


def verify_model(spec, urn, namespace, project):
    require(spec.urn == urn and spec.namespace == namespace and spec.project_name == project,
        "Advertised authored model identity/namespace/project differs")
    require(spec.selection == "advertised candidate; not evidence of Resolver selection or execution"
        and "not a dependency closure" in spec.dependency_completeness,
        "Model inspection overclaims scientific execution or dependency closure")


def save_report(path, report, primary=None):
    try:
        path.write_text(encode_report(report), encoding="utf-8")
    except Exception as error:
        if primary is None:
            raise
        primary.add_note("Model authoring evidence save failed: " + type(error).__name__)


def run(state):
    state = Path(state)
    config = json.loads((state / "scientist.json").read_text())
    operator = json.loads((state / "authoring-operator.json").read_text())
    require(operator.get("allow_owned_authoring") is True and operator["username"] != config["username"],
        "Requires distinct explicitly provisioned owned TEST operator")
    suffix = uuid4().hex[:12]
    lexical = suffix.translate(str.maketrans("0123456789", "ghijklmnop"))
    namespace = "python.owned.m" + lexical
    workspace_name, project_name = "python-model-" + suffix, "python-model-project-" + suffix
    source = ("// Owned synthetic source draft; not study data or an executed model.\nnamespace " + namespace
        + ";\nmodel consolidation:Elevation of consolidation:Region in m set to [91.75];\n")
    report = {"passed": False, "scientific_execution": "NOT_RUN", "publication": "NOT_CLAIMED",
        "project_deleted": False, "workspace_deleted": False, "model_removed": False,
        "fixture_principal_role": operator["fixture_principal_role"]}
    workspace = project = None
    primary = None
    def client(user):
        return Client(config["urls"]["runtime"], user["token"], agent_name=user["username"],
            resources=Endpoint(config["urls"]["resources"], user["token"]), timeout=30)
    with client(operator) as api:
        author = Authoring(api)
        inspector = ResourceInspector(api)
        positive_model = None
        try:
            before = inspector.list("MODEL")
            controls = [row.urn for row in before if row.urn.startswith("consolidation.scenario.")]
            require(len(controls) == 1, "Need an advertised same-family model positive control")
            positive_model = controls[0]
            parsed = author.parse(source, kind=KIND)
            statement = model_statement(_plain(parsed.raw), namespace)
            model_urn = statement["urn"]
            require(parsed.raw["sourceCode"] == source, "Parser altered authored source")
            report.update(namespace=namespace, model_urn=model_urn,
                source_sha256=hashlib.sha256(source.encode("utf-8")).hexdigest())
            save_report(state / "model-source-authoring.json", report)
            try:
                author.parse("namespace " + namespace + "; model ???", kind=KIND)
            except (ProtocolError, ServerError):
                report["invalid_source_refused"] = True
            else:
                raise AssertionError("Invalid model-containing namespace source was admitted")
            workspace = author.create_workspace(workspace_name, allow_write=True)
            project = author.create_project(workspace, project_name, allow_write=True)
            receipt = author.create_document(parsed, project, allow_write=True)
            returned = api.resources.retrieve(namespace, KIND)
            require(receipt.urn == namespace and receipt.operation == "CREATE" and returned.raw.get("sourceCode") == source
                and returned.raw.get("projectName") == project_name and returned.raw.get("@CLASS") == parsed.raw["@CLASS"],
                "Stored authored namespace source/class/project/acknowledgement differs")
            require(model_statement(_plain(returned.raw), namespace)["urn"] == model_urn,
                "Stored model statement changed canonical identity")
            advertised = inspector.list("MODEL")
            require(any(row.urn == model_urn for row in advertised), "Authored model not advertised by Resources")
            spec = inspector.retrieve(model_urn, "MODEL")
            verify_model(spec, model_urn, namespace, project_name)
            report["model_inspection"] = {"urn": spec.urn, "namespace": spec.namespace,
                "project_name": spec.project_name, "version": _plain(spec.version), "raw": _plain(spec.raw),
                "selection": spec.selection, "dependency_completeness": spec.dependency_completeness}
            with client(config) as ordinary:
                denied_urn = "python.denied.m" + lexical
                denied = Authoring(ordinary).parse(source.replace(namespace, denied_urn), kind=KIND)
                payload = _plain(denied.raw)
                payload["projectName"] = project_name
                require_write_denied(ordinary, KIND, denied_urn, payload)
                require_missing(api, denied_urn, KIND, positive=namespace)
                require_write_denied(ordinary, "PROJECT", workspace_name + "/" + project_name + "-denied",
                    {"urn": workspace_name + "/" + project_name + "-denied"})
                require_missing(api, project_name + "-denied", "PROJECT", positive=project_name)
            report.update(passed=True, exact_source_round_trip=True, advertised_model_verified=True,
                actual_document_project_denials=True, denied_assets_absent=True, service_id=receipt.service_id)
        except BaseException as error:
            primary = error
            report["error_type"] = type(error).__name__
            raise
        finally:
            if isinstance(primary, SubmissionOutcomeUnknown):
                report["cleanup_limit"] = "Unknown mutation outcome; no dependent deletes or replay"
            else:
                try:
                    if project is not None:
                        cleanup(api, "PROJECT", project_name)
                        report["project_deleted"] = True
                        require_missing(api, namespace, KIND, positive="consolidation.fixture")
                        require_missing(api, report["model_urn"], "MODEL", positive=positive_model)
                        report["model_removed"] = True
                    if workspace is not None:
                        cleanup(api, "WORKSPACE", workspace_name)
                        report["workspace_deleted"] = True
                except BaseException as error:
                    report.update(passed=False, cleanup_error_type=type(error).__name__)
                    if primary is None:
                        save_report(state / "model-source-authoring.json", report, error)
                        raise
                    primary.add_note("Owned model authoring cleanup failed: " + type(error).__name__)
            save_report(state / "model-source-authoring.json", report, primary)
    return report
