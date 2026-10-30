"""Keep the client on the existing Java wire contract, without a Java upgrade.

The frozen inventory was checked against the existing source and installed Java
binary, independently of this Python enum. No JDK or running service is needed
to run these codec/request regressions. These are not scientific admission tests.
"""
import json
from pathlib import Path

import pytest

from klab_client import ObservationRequest
from klab_client.api.primitives import SemanticType
from klab_client.dto import concept_from_wire, observable_from_wire, observable_to_wire
from klab_client.errors import UnsupportedOperationError


CONTRACT = json.loads((Path(__file__).parents[1] / "fixtures/existing_java_wire_enums.json").read_text())


def observable(contextualization="CONNECTION"):
    # A Java API relationship is represented by existing API flags. The client
    # does not construct or send the language parser's SemanticSyntax.Type.
    return {
        "@CLASS": "org.integratedmodelling.common.knowledge.ObservableImpl",
        "urn": "fixture:Connection",
        "contextualization": contextualization,
        "semantics": {
            "@CLASS": "org.integratedmodelling.common.knowledge.ConceptImpl",
            "urn": "fixture:Connection",
            "type": ["OBSERVABLE", "COUNTABLE", "RELATIONSHIP", "BIDIRECTIONAL"],
        },
    }


def test_client_semantic_flags_match_existing_java_api_not_new_parser_types():
    assert set(SemanticType.__members__) == set(CONTRACT["semantic_types"])
    assert len(CONTRACT["semantic_types"]) == 81


@pytest.mark.parametrize("name", CONTRACT["semantic_types"])
def test_existing_java_semantic_flags_are_not_dropped_by_the_decoder(name):
    payload = {"urn": "fixture:ExistingConcept", "type": [name]}
    concept = concept_from_wire(payload)
    assert concept.get_type() == {SemanticType[name]}
    assert concept.raw == payload


@pytest.mark.parametrize("activity", [v for v in CONTRACT["contextualizations"] if v != "VOID"])
def test_requests_preserve_existing_java_contextualization_and_server_wire_data(activity):
    wire = observable(activity)
    resolved = observable_from_wire(wire)
    assert observable_to_wire(resolved) == wire
    request = ObservationRequest(resolved).to_wire()
    assert request["observable"] == wire
    # No translation to parser enum names, ordinals or locally invented types.
    assert request["observable"]["semantics"]["type"] == wire["semantics"]["type"]


def test_void_or_unknown_activity_cannot_be_invented_as_a_supported_submission():
    for activity in ("VOID", "NOT_IN_EXISTING_JAVA"):
        with pytest.raises(UnsupportedOperationError):
            ObservationRequest(observable_from_wire(observable(activity)))
