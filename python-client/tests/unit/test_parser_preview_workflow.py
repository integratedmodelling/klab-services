"""Harness validation controls only; no fake claims of real parser acceptance."""
from hashlib import sha256
from types import SimpleNamespace

import pytest

from workflows.parser_preview import DOCUMENTS, validate_receipt


def receipt(source, kind):
    return SimpleNamespace(kind=kind, urn="python.preview", service_id="resources",
        raw={"serviceId": "resources"}, edge_whitespace_trimmed=True,
        source_sha256=sha256(source.encode()).hexdigest(),
        parser_source_sha256=sha256(source.strip(" \t\r\n").encode()).hexdigest())


@pytest.mark.parametrize("kind", DOCUMENTS)
def test_four_real_document_families_have_explicit_source_pins(kind):
    source = " \n" + DOCUMENTS[kind] + "\n "
    validate_receipt(receipt(source, kind), source, kind)


@pytest.mark.parametrize("field,value", [("kind", "MODEL"), ("urn", "different"),
    ("source_sha256", "wrong"), ("parser_source_sha256", "wrong"), ("service_id", "foreign")])
def test_receipt_identity_or_source_drift_is_rejected(field, value):
    source = DOCUMENTS["NAMESPACE"]
    result = receipt(source, "NAMESPACE")
    setattr(result, field, value)
    with pytest.raises(AssertionError):
        validate_receipt(result, source, "NAMESPACE")


def test_no_trimming_is_also_an_explicit_valid_source_policy():
    source = " \n" + DOCUMENTS["ONTOLOGY"] + "\n "
    result = receipt(source, "ONTOLOGY")
    result.edge_whitespace_trimmed = False
    result.parser_source_sha256 = result.source_sha256
    validate_receipt(result, source, "ONTOLOGY")


def test_misreported_trimming_cannot_hide_source_changes():
    source = " \n" + DOCUMENTS["ONTOLOGY"] + "\n "
    result = receipt(source, "ONTOLOGY")
    result.edge_whitespace_trimmed = False
    with pytest.raises(AssertionError):
        validate_receipt(result, source, "ONTOLOGY")
