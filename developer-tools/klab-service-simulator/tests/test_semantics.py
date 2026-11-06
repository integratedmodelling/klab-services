"""Finite public sync/async semantic workflows, not local ontology inference."""
import asyncio

import httpx
import pytest

from klab_client import (AsyncClient, AsyncResourceInspector, AsyncSemanticExplorer,
                         Client, ResourceInspector, SemanticExplorer)
from klab_client.errors import InvalidRequestError
from klab_client.semantics import StaleKnowledgeError
from conftest import launch
from test_vertical import client_args, control


def change_worldview(ready):
    response = httpx.post(ready["endpoints"]["runtime"].rsplit("/", 1)[0] + "/__sim__/changeWorldview",
        headers={"Authorization": "sim-admin-control"}, trust_env=False, timeout=3)
    response.raise_for_status()


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_explicit_authority_selection_hierarchy_predicates_and_passive_documentation(simulator, mode):
    if mode == "async":
        async def run():
            async with AsyncClient(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
                explorer = AsyncSemanticExplorer(client)
                meaning = await explorer.resolve_observable("geography:Elevation in m")
                first = await explorer.search_authority("SIM", "elevation", limit=1)
                second = await explorer.search_authority("SIM", "elevation", offset=1, limit=1)
                candidate = first.select("ELEVATION")
                with pytest.raises(InvalidRequestError):
                    first.select("Elevation")  # Display label is not a canonical code.
                docs = await explorer.documentation(candidate)
                selected = await explorer.resolve_candidate(candidate)
                parents = await explorer.parents("SIM:ELEVATION")
                children = await explorer.children("geography:Elevation")
                assert await explorer.is_a("SIM:ELEVATION", "geography:Elevation")
                assert not await explorer.is_a("geography:Elevation", "SIM:ELEVATION")
                assert await explorer.satisfiable("SIM:ELEVATION")
                with pytest.raises(InvalidRequestError):
                    await AsyncSemanticExplorer(client).resolve_candidate(candidate)
                return meaning, first, second, docs, selected, parents, children
        result = asyncio.run(run())
    else:
        with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
            explorer = SemanticExplorer(client)
            meaning = explorer.resolve_observable("geography:Elevation in m")
            first = explorer.search_authority("SIM", "elevation", limit=1)
            second = explorer.search_authority("SIM", "elevation", offset=1, limit=1)
            candidate = first.select("ELEVATION")
            with pytest.raises(InvalidRequestError):
                first.select("Elevation")
            docs = explorer.documentation(candidate)
            selected = explorer.resolve_candidate(candidate)
            parents = explorer.parents("SIM:ELEVATION")
            children = explorer.children("geography:Elevation")
            assert explorer.is_a("SIM:ELEVATION", "geography:Elevation")
            assert not explorer.is_a("geography:Elevation", "SIM:ELEVATION")
            assert explorer.satisfiable("SIM:ELEVATION")
            with pytest.raises(InvalidRequestError):
                SemanticExplorer(client).resolve_candidate(candidate)
            result = meaning, first, second, docs, selected, parents, children
    meaning, first, second, docs, selected, parents, children = result
    assert meaning.urn == "geography:Elevation in m" and meaning.knowledge.revision is None
    assert "unknown" in meaning.knowledge.freshness
    assert first.status == "OK" and first.total == 2 and first.next_offset == 1
    assert second.matches[0].code == "ALTITUDE" and second.next_offset is None
    assert first.matches[0].label == second.matches[0].label == "Elevation"
    assert selected.urn == "SIM:ELEVATION" and docs["text/html"].startswith("https://docs.invalid/")
    assert [item.urn for item in parents] == ["geography:Elevation"]
    assert {item.urn for item in children} == {"SIM:ELEVATION", "SIM:ALTITUDE"}
    assert not any(control(simulator)["counts"].values())
    # External documentation hosts are disallowed by the test socket guard; only
    # passive descriptors were returned. No Python hierarchy/predicate inference.


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_changed_worldview_refuses_stored_candidate_and_requires_fresh_search(simulator, mode):
    if mode == "async":
        async def run():
            async with AsyncClient(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
                explorer = AsyncSemanticExplorer(client)
                candidate = (await explorer.search_authority("SIM", "elevation")).select("ELEVATION")
                change_worldview(simulator)
                with pytest.raises(StaleKnowledgeError):
                    await explorer.resolve_candidate(candidate)
                fresh = (await explorer.search_authority("SIM", "elevation")).select("ELEVATION")
                assert (await explorer.resolve_candidate(fresh)).urn == "SIM:ELEVATION"
                return candidate, fresh
        old, fresh = asyncio.run(run())
    else:
        with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
            explorer = SemanticExplorer(client)
            old = explorer.search_authority("SIM", "elevation").select("ELEVATION")
            change_worldview(simulator)
            with pytest.raises(StaleKnowledgeError):
                explorer.resolve_candidate(old)
            fresh = explorer.search_authority("SIM", "elevation").select("ELEVATION")
            assert explorer.resolve_candidate(fresh).urn == "SIM:ELEVATION"
    assert old.knowledge.worldview_id == "synthetic" and fresh.knowledge.worldview_id == "synthetic-next"
    assert old.knowledge.revision is fresh.knowledge.revision is None  # No invented revision lease.


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_unavailable_and_unsupported_authorities_are_not_empty_successes(mode):
    with launch("semantic-unavailable") as ready:
        if mode == "async":
            async def run():
                async with AsyncClient(ready["endpoints"]["runtime"], **client_args(ready)) as client:
                    explorer = AsyncSemanticExplorer(client)
                    return await explorer.search_authority("SIM", "elevation"), await explorer.search_authority("OTHER", "elevation")
            unavailable, unsupported = asyncio.run(run())
        else:
            with Client(ready["endpoints"]["runtime"], **client_args(ready)) as client:
                explorer = SemanticExplorer(client)
                unavailable, unsupported = explorer.search_authority("SIM", "elevation"), explorer.search_authority("OTHER", "elevation")
        assert unavailable.status == "UNAVAILABLE" and unsupported.status == "UNSUPPORTED"
        assert not unavailable.matches and not unsupported.matches
        with pytest.raises(InvalidRequestError):
            unavailable.select("ELEVATION")


@pytest.mark.parametrize("mode", ["sync", "async"])
def test_meaning_based_catalog_inspection_remains_advertisement_not_selection(simulator, mode):
    if mode == "async":
        async def run():
            async with AsyncClient(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
                meaning = await AsyncSemanticExplorer(client).resolve_observable("geography:Elevation in m")
                inspector = AsyncResourceInspector(client)
                listed = await inspector.list("MODEL")
                model = await inspector.retrieve(listed[0].urn, "MODEL")
                resolved = await inspector.resolve(model.resource_urns[0], "RESOURCE")
                resource = await inspector.retrieve(resolved.results[0].urn, "RESOURCE")
                assert client.transport.endpoint("resources").url == simulator["endpoints"]["resources"]
                return meaning, model, resolved, resource
        meaning, model, resolved, resource = asyncio.run(run())
    else:
        with Client(simulator["endpoints"]["runtime"], **client_args(simulator)) as client:
            meaning = SemanticExplorer(client).resolve_observable("geography:Elevation in m")
            inspector = ResourceInspector(client)
            listed = inspector.list("MODEL")
            model = inspector.retrieve(listed[0].urn, "MODEL")
            resolved = inspector.resolve(model.resource_urns[0], "RESOURCE")
            resource = inspector.retrieve(resolved.results[0].urn, "RESOURCE")
            assert client.transport.endpoint("resources").url == simulator["endpoints"]["resources"]
    assert model.advertised_observables[0].urn == meaning.urn
    assert model.advertised_observables[0].unit == "m"
    assert "not evidence of Resolver selection" in model.selection
    assert "unknown" in model.dependency_completeness
    assert resource.provider_metadata["adapterType"] == "raster"
    assert resolved.partial and resolved.services[simulator["service_ids"]["resources"]] == "https://catalog.invalid/resources"
    assert not any(control(simulator)["counts"].values())  # No compute, routing or remote state creation.
