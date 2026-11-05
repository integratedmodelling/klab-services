"""Native concept codebook metadata only; no mappings invented or computed."""
import asyncio
import json
from pathlib import Path
import uuid

from klab_client.errors import MissingAssetError, UnsupportedOperationError
from .async_handle_restoration import client
from .model_source_authoring import save_report
from .scientific_checks import require


def validate_codebook(asset, root, service_id):
    raw = asset.raw
    require(asset.urn == raw.get("rootConceptId") == root, "Codebook root identity changed")
    require(raw.get("type") == "CONCEPT" and raw.get("serviceId") == service_id, "Codebook type/service changed")
    require(isinstance(raw.get("entries"), list), "Native codebook entries unavailable")
    return len(raw["entries"])


async def scenario(state):
    config = json.loads((state / "scientist.json").read_text())
    report = {"passed": False, "scientific_submissions": 0, "inspection_mutations": 0,
        "categorical_computation": "NOT_RUN", "historical_dictionary": "NOT_RECOVERED"}
    primary = None
    try:
        async with client(config) as reader:
            await reader.initialize_user_scope(local_federation=True)
            calls = []
            async def audit(request):
                require(request.method == "GET" and (request.url.path.endswith("/public/capabilities")
                    or "/api/v1/retrieve/CODELIST/" in request.url.path), "Codebook inspection attempted unrelated operation")
                calls.append({"method": request.method, "path": request.url.path})
            reader.transport._http.event_hooks["request"].append(audit)
            capabilities = await reader.resources.capabilities()
            root = "consolidation:Region"
            book = await reader.resources.retrieve(root, "CODELIST")
            count = validate_codebook(book, root, capabilities.service_id)
            report.update(root=root, service_id=capabilities.service_id, entry_count=count,
                evidence_scope="empty-metadata retrieval only" if count == 0 else "served metadata, uninterpreted")
            before = len(calls)
            try:
                await reader.resources.list("CODELIST")
            except UnsupportedOperationError:
                pass
            else:
                raise AssertionError("CODELIST list unexpectedly admitted")
            try:
                await reader.resources.resolve(root, knowledge_class="CODELIST")
            except UnsupportedOperationError:
                pass
            else:
                raise AssertionError("CODELIST resolve unexpectedly admitted")
            require(len(calls) == before, "Unsupported operations reached HTTP")
            try:
                await reader.resources.retrieve("consolidation:Absent_" + uuid.uuid4().hex, "CODELIST")
            except MissingAssetError as error:
                report["missing_root_http_status"] = getattr(error, "status_code", None)
            else:
                raise AssertionError("Unknown root produced a codebook")
            report.update(passed=True, unsupported_list_resolve_zero_http=True, calls=calls)
        report["reader_closed_locally"] = True
    except BaseException as error:
        primary = error
        report.update(passed=False, error_type=type(error).__name__)
        raise
    finally:
        save_report(state / "native-codelist-retrieval.json", report, primary)
    return report


def run(state):
    return asyncio.run(scenario(Path(state)))
