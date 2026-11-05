"""Actual native async model metadata reads, not model selection or computation."""
import asyncio
from dataclasses import fields
import json
from pathlib import Path
import uuid

from klab_client import AsyncResourceInspector
from klab_client.errors import MissingAssetError
from .async_handle_restoration import client
from .model_source_authoring import save_report
from .scientific_checks import require


def verify_model_views(listed, retrieved):
    """Java retrieve may fill a missing KimAsset serviceId; no other drift allowed."""
    for field in fields(listed):
        if field.name not in ("raw", "provider_metadata"):
            require(getattr(listed, field.name) == getattr(retrieved, field.name),
                "Actual Java list/retrieve model identity or metadata changed")
    raw, provider = dict(listed.raw), dict(listed.provider_metadata)
    filled = raw.get("serviceId") is None and retrieved.raw.get("serviceId") is not None
    if filled:
        raw["serviceId"] = retrieved.origin.service_id
        provider["serviceId"] = retrieved.origin.service_id
    require(retrieved.raw == raw and retrieved.provider_metadata == provider,
        "Model views differ beyond Java's missing-serviceId enrichment")
    return filled


async def scenario(state):
    config = json.loads((state / "scientist.json").read_text())
    report = {"passed": False, "scientific_submissions": 0, "inspection_mutations": 0,
        "model_selection_execution": "NOT_CLAIMED", "dependency_closure": "NOT_CLAIMED"}
    primary = None
    try:
        async with client(config) as reader:
            await reader.initialize_user_scope(local_federation=True)
            calls = []
            async def audit(request):
                require(request.method == "GET" and (request.url.path.endswith("/public/capabilities")
                    or "/api/v1/list/" in request.url.path or "/api/v1/retrieve/" in request.url.path
                    or "/api/v1/resolve/" in request.url.path), "Inspection attempted a non-read operation")
                calls.append({"method": request.method, "path": request.url.path})
            reader.transport._http.event_hooks["request"].append(audit)
            inspector = AsyncResourceInspector(reader, max_items=1000)
            listed = await inspector.list("MODEL")
            require(bool(listed), "Actual Java model advertisements unavailable")
            candidate = listed[0]
            retrieved = await inspector.retrieve(candidate.urn, "MODEL")
            report["model_view_differing_fields"] = [field.name for field in fields(candidate)
                if getattr(candidate, field.name) != getattr(retrieved, field.name)]
            report["model_view_differing_raw_keys"] = sorted(key for key in candidate.raw.keys() | retrieved.raw.keys()
                if candidate.raw.get(key) != retrieved.raw.get(key))
            report["retrieval_filled_missing_service_id"] = verify_model_views(candidate, retrieved)
            resolved = await inspector.resolve(candidate.urn, "MODEL")
            require(any(row.urn == candidate.urn for row in resolved.results), "Model resolution omitted listed model identity")
            try:
                await inspector.retrieve("consolidation.absent_" + uuid.uuid4().hex, "MODEL")
            except MissingAssetError as error:
                report["missing_model"] = "MissingAssetError after positive list/retrieve/resolve"
                report["missing_model_http_status"] = getattr(error, "status_code", None)
            else:
                raise AssertionError("Unknown model did not produce a typed missing-asset result")
            report.update(passed=True, model_urn=candidate.urn, listed_models=len(listed),
                service_id=retrieved.origin.service_id, namespace=retrieved.namespace,
                project=retrieved.project_name, version_available=retrieved.version is not None,
                advertised_observables=len(retrieved.advertised_observables or ()),
                listed_dependencies_available=retrieved.listed_dependencies is not None,
                checked_native_model_views=True, partial_resolution=resolved.partial, calls=calls)
        report["reader_closed_locally"] = True
    except BaseException as error:
        primary = error
        report["passed"] = False
        report["error_type"] = type(error).__name__
        raise
    finally:
        save_report(state / "async-resource-inspection.json", report, primary)
    return report


def run(state):
    return asyncio.run(scenario(Path(state)))
