"""Actual Resources parser receipt checks, never authoring or scientific execution."""
import hashlib
import json
from pathlib import Path

from klab_client import Client, Endpoint, SourcePreviews
from klab_client.errors import InvalidRequestError, ProtocolError, ServerError
from .scientific_checks import require

DOCUMENTS = {
    "NAMESPACE": "namespace python.preview;\nmodel consolidation:Elevation of consolidation:Region in m set to [3];",
    "ONTOLOGY": "ontology preview version 1.0 in domain root;",
    "OBSERVATION_STRATEGY_DOCUMENT": "strategies python.preview version 1.0;",
    "BEHAVIOR": 'behavior python.preview\n "Read-only parser fixture."\n version 1.0\naction main: return 0',
}


def validate_receipt(receipt, source, kind):
    require(receipt.kind == kind and receipt.urn in {"python.preview", "preview"},
        "Parser returned a different kind/URN")
    require(receipt.source_sha256 == hashlib.sha256(source.encode()).hexdigest(),
        "Parser receipt lost caller source identity")
    parser_source = source.strip(" \t\r\n") if receipt.edge_whitespace_trimmed else source
    require(receipt.parser_source_sha256 == hashlib.sha256(parser_source.encode()).hexdigest(),
        "Parser changed source beyond explicit edge trimming")
    require(receipt.raw.get("serviceId") == receipt.service_id, "Parser service identity differs")


def run(state):
    state = Path(state)
    config = json.loads((state / "scientist.json").read_text())
    report = {"passed": False, "records": [], "publication": "NOT_RUN",
        "knowledge_revision": "NOT_ESTABLISHED", "scientific_execution": "NOT_RUN"}
    calls = []
    try:
        with Client(config["urls"]["runtime"], config["token"], agent_name=config["username"],
            resources=Endpoint(config["urls"]["resources"], config["token"])) as api:
            def capture(request):
                call = {"method": request.method, "path": request.url.path}
                calls.append(call)
                require(call["method"] == "GET" and call["path"].endswith("/public/capabilities")
                    or call["method"] == "POST" and any(call["path"].endswith("/parseAsset/" + kind)
                        for kind in DOCUMENTS), "Preview attempted a non-parser operation")
            api.transport._http.event_hooks["request"].append(capture)
            previews = SourcePreviews(api)
            failures = []
            for kind, body in DOCUMENTS.items():
                row = {"kind": kind, "passed": False}
                report["records"].append(row)
                try:
                    source = " \n" + body + "\n "
                    receipt = previews.parse(source, kind=kind, allow_edge_trimming=True)
                    validate_receipt(receipt, source, kind)
                    row.update(valid_source=True, urn=receipt.urn, service_id=receipt.service_id,
                        source_sha256=receipt.source_sha256, parser_sha256=receipt.parser_source_sha256,
                        edge_trimmed=receipt.edge_whitespace_trimmed)
                    # This is a syntax failure, not a different valid empty model.
                    try:
                        previews.parse("%%% deliberately invalid parser source {{{", kind=kind)
                    except (ServerError, ProtocolError) as error:
                        row["invalid_rejected"] = type(error).__name__
                    else:
                        raise AssertionError("Invalid source became a successful parser receipt")
                    row["passed"] = True
                except Exception as error:
                    row["error_type"] = type(error).__name__
                    failures.append(kind)
            # Invalid local policy must fail before any HTTP request.
            before = len(calls)
            try:
                previews.parse(" ", kind="NAMESPACE")
            except InvalidRequestError:
                report["blank_source_rejected_before_http"] = len(calls) == before
            else:
                raise AssertionError("Blank source was accepted")
            require(not failures, "Actual parser receipt checks failed: " + ", ".join(failures))
            require(report["blank_source_rejected_before_http"], "Invalid local source reached HTTP")
        report["passed"] = True
    finally:
        report["calls"] = calls
        (state / "parser-preview.json").write_text(json.dumps(report, indent=2))
    return report
