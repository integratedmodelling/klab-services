"""Actual native async model metadata reads, not model selection or computation."""
import asyncio
import json
from pathlib import Path
import uuid

from klab_client import AsyncResourceInspector
from klab_client.errors import MissingAssetError
from .async_handle_restoration import client
from .model_source_authoring import save_report
from .scientific_checks import require


async def scenario(state):
    config = json.loads((state / "scientist.json").read_text())
    report = {"passed": False, "scientific_submissions": 0, "remote_mutations": 0,
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
            require(retrieved == candidate, "Actual Java list/retrieve immutable model views differ")
            resolved = await inspector.resolve(candidate.urn, "MODEL")
            require(any(row.urn == candidate.urn for row in resolved.results), "Model resolution omitted listed model identity")
            try:
                await inspector.retrieve("consolidation.absent_" + uuid.uuid4().hex, "MODEL")
            except MissingAssetError:
                report["missing_model"] = "MissingAssetError after positive list/retrieve/resolve"
            else:
                raise AssertionError("Unknown model did not produce a typed missing-asset result")
            report.update(passed=True, model_urn=candidate.urn, listed_models=len(listed),
                service_id=retrieved.origin.service_id, namespace=retrieved.namespace,
                project=retrieved.project_name, version_available=retrieved.version is not None,
                advertised_observables=len(retrieved.advertised_observables or ()),
                listed_dependencies_available=retrieved.listed_dependencies is not None,
                immutable_view_parity=True, partial_resolution=resolved.partial, calls=calls)
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
