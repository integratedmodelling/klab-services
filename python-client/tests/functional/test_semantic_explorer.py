"""Actual existing-Java authority and hierarchy checks on an authorized test state."""
import json

import pytest

from klab_client import Client, Endpoint, SemanticExplorer
from klab_client.errors import ConfigurationError


pytestmark = [pytest.mark.live, pytest.mark.functional]


@pytest.fixture
def semantic_fixture(functional_state):
    path = functional_state / "semantic-explorer.json"
    if not path.is_file():
        raise ConfigurationError("Prepare semantic-explorer.json with independently verified authority/hierarchy expectations")
    fixture = json.loads(path.read_text())
    for name in ("authority", "query", "code", "identity_urn", "documentation_media_type", "parent", "child"):
        if not isinstance(fixture.get(name), str) or not fixture[name].strip():
            raise ConfigurationError("Semantic fixture needs explicit authority, identity, documentation and hierarchy expectations")
    config = json.loads((functional_state / "scientist.json").read_text())
    return config, fixture


def fixture_client(config):
    token = config["token"]
    return Client(
        config["urls"]["runtime"], token, agent_name=config["username"],
        **{name: Endpoint(config["urls"][name], token) for name in ("reasoner", "resources", "resolver")},
    )


def test_existing_authority_search_selection_documentation_and_resolution(semantic_fixture):
    config, fixture = semantic_fixture
    with fixture_client(config) as client:
        explorer = SemanticExplorer(client)
        page = explorer.search_authority(fixture["authority"], fixture["query"])
        assert page.status == "OK"
        candidate = page.select(fixture["code"])
        assert candidate.authority == fixture["authority"]
        links = explorer.documentation(candidate)
        assert fixture["documentation_media_type"] in links
        result = explorer.resolve_candidate(candidate)
        assert result.urn == fixture["identity_urn"]
        assert candidate.locator in candidate.summary()
        assert result.urn in result.summary()


def test_existing_hierarchy_and_predicates_match_independent_expectations(semantic_fixture):
    config, fixture = semantic_fixture
    parent, child = fixture["parent"], fixture["child"]
    if parent == child:
        raise ConfigurationError("Hierarchy fixture must distinguish parent and child")
    with fixture_client(config) as client:
        explorer = SemanticExplorer(client)
        assert parent in {concept.urn for concept in explorer.parents(child)}
        assert child in {concept.urn for concept in explorer.children(parent)}
        assert explorer.is_a(child, parent) is True
        assert explorer.is_a(parent, child) is False
        assert explorer.satisfiable(parent) is True
        assert explorer.satisfiable(child) is True
