"""Actual remote semantic inspection; no local inference, model jobs or authority invention."""
import asyncio
import json
from pathlib import Path
import uuid

from klab_client import AsyncSemanticExplorer
from klab_client.errors import MissingAssetError
from .async_handle_restoration import client
from .model_source_authoring import save_report
from .scientific_checks import require


async def scenario(state):
    config = json.loads((state / "scientist.json").read_text())
    report = {"passed": False, "scientific_submissions": 0, "inspection_mutations": 0,
        "authority_positive_candidate": "NOT_TESTED; no provider/identity invented",
        "atomic_revision_lease": "NOT_CLAIMED", "whole_worldview_validity": "NOT_CLAIMED"}
    primary = None
    try:
        async with client(config) as reader:
            await reader.initialize_user_scope(local_federation=True)
            calls = []
            allowed = ("/resolve/concept", "/resolve/observable", "/parents", "/children", "/subsumes",
                "/satisfiable", "/authority/search")
            async def audit(request):
                require(request.method == "GET" and request.url.path.endswith("/public/capabilities")
                    or request.method == "POST" and any(request.url.path.endswith("/api/v1" + route) for route in allowed),
                    "Semantic exploration attempted an unrelated operation")
                calls.append({"method": request.method, "path": request.url.path})
            reader.transport._http.event_hooks["request"].append(audit)
            explorer = AsyncSemanticExplorer(reader, max_items=1000)
            child, parent = "consolidation:Region", "consolidation:Subject"
            resolved = await explorer.resolve_concept(child)
            canonical = await explorer.resolve_concept(parent)
            observable = await explorer.resolve_observable(child)
            require(resolved.urn == child and canonical.urn == "odo:Subject" and observable.urn == child,
                "Source-defined concept/observable/canonical parent identity changed")
            parents = await explorer.parents(child)
            children = await explorer.children(parent)
            require(canonical.urn in {item.urn for item in parents} and child in {item.urn for item in children},
                "Source-defined canonical hierarchy differs")
            require(await explorer.is_a(child, parent) is True and await explorer.is_a(parent, child) is False,
                "Directional specialization differs")
            require(await explorer.satisfiable(child) is True, "Known child unsatisfiable")
            try:
                await explorer.resolve_concept("consolidation:Absent_" + uuid.uuid4().hex)
            except MissingAssetError:
                report["missing_concept"] = "MissingAssetError after known-root positive"
            else:
                raise AssertionError("Unknown concept yielded a semantic view")
            # Explicitly unconfigured local authority is a negative control, not a real provider.
            page = await explorer.search_authority("UNCONFIGURED_ASYNC_TEST", "negative-control")
            require(page.status in ("UNSUPPORTED", "UNAVAILABLE") and not page.matches,
                "Unconfigured authority negative control unexpectedly served results or failed")
            report.update(passed=True, child=resolved.urn, canonical_parent=canonical.urn,
                parents=[item.urn for item in parents], children=[item.urn for item in children],
                forward_is_a=True, reverse_is_a=False, satisfiable=True,
                service_id=resolved.knowledge.service_id, worldview_id=resolved.knowledge.worldview_id,
                observed_revision=resolved.knowledge.revision, authority_negative_status=page.status, calls=calls)
        report["reader_closed_locally"] = True
    except BaseException as error:
        primary = error
        report.update(passed=False, error_type=type(error).__name__)
        raise
    finally:
        save_report(state / "async-semantic-exploration.json", report, primary)
    return report


def run(state):
    return asyncio.run(scenario(Path(state)))
