"""Native async actual parser receipts for the existing four syntax fixtures."""
import asyncio
import json
from pathlib import Path

from klab_client import AsyncSourcePreviews
from klab_client.errors import InvalidRequestError, ProtocolError, ServerError
from .async_handle_restoration import client
from .model_source_authoring import save_report
from .parser_preview import DOCUMENTS, validate_receipt
from .scientific_checks import require


async def scenario(state):
    config = json.loads((state / "scientist.json").read_text())
    report = {"passed": False, "records": [], "publication": "NOT_RUN", "compilation": "NOT_RUN",
        "scientific_execution": "NOT_RUN", "knowledge_revision": "NOT_ESTABLISHED"}
    primary = None
    try:
        async with client(config) as reader:
            await reader.initialize_user_scope(local_federation=True)
            calls = []
            async def audit(request):
                path = request.url.path
                require(request.method == "GET" and path.endswith("/public/capabilities")
                    or request.method == "POST" and any(path.endswith("/parseAsset/" + kind) for kind in DOCUMENTS),
                    "Async preview attempted a non-parser operation")
                calls.append({"method": request.method, "path": path})
            reader.transport._http.event_hooks["request"].append(audit)
            previews = AsyncSourcePreviews(reader)
            for kind, body in DOCUMENTS.items():
                row = {"kind": kind, "passed": False}
                report["records"].append(row)
                source = " \n" + body + "\n "
                receipt = await previews.parse(source, kind=kind, allow_edge_trimming=True)
                validate_receipt(receipt, source, kind)
                row.update(urn=receipt.urn, service_id=receipt.service_id,
                    source_sha256=receipt.source_sha256, parser_sha256=receipt.parser_source_sha256,
                    edge_trimmed=receipt.edge_whitespace_trimmed)
                try:
                    await previews.parse("%%% deliberately invalid parser source {{{", kind=kind)
                except (ServerError, ProtocolError) as error:
                    row["invalid_rejected"] = type(error).__name__
                else:
                    raise AssertionError("Invalid async source produced a parser receipt")
                row["passed"] = True
            before = len(calls)
            try:
                await previews.parse(" ", kind="NAMESPACE")
            except InvalidRequestError:
                require(len(calls) == before, "Blank preview source reached HTTP")
                report["blank_source_rejected_before_http"] = True
            else:
                raise AssertionError("Blank async source was admitted")
            report.update(passed=True, calls=calls, scientific_submissions=0, authoring_writes=0)
        report["reader_closed_locally"] = True
    except BaseException as error:
        primary = error
        report.update(passed=False, error_type=type(error).__name__)
        raise
    finally:
        save_report(state / "async-source-previews.json", report, primary)
    return report


def run(state):
    return asyncio.run(scenario(Path(state)))
