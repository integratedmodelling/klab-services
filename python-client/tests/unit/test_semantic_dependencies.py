"""Exact-source/hash and owned-state safety for the local semantic dependency capture."""
import hashlib
import json

import httpx
import pytest

import semantic_dependencies as dependencies


@pytest.fixture
def catalog(tmp_path, monkeypatch):
    content = b"owned test ontology bytes"
    digest = hashlib.sha256(content).hexdigest()
    properties = f"odo={dependencies.ODO_LOCATOR}\nodo.sha256={digest}\n"
    monkeypatch.setattr(dependencies, "ODO_SHA256", digest)
    monkeypatch.setattr(dependencies, "PROPERTIES", properties)
    path = tmp_path / "catalog"
    path.mkdir()
    (path / "odo.owl").write_bytes(content)
    (path / "source-catalog.properties").write_text(properties, encoding="utf-8")
    return path, content


def test_numbered_dependency_has_an_immutable_source_and_content_pin():
    assert f"/{dependencies.ODO_COMMIT}/" in dependencies.ODO_SOURCE
    assert "/master/" not in dependencies.ODO_SOURCE
    assert dependencies.ODO_SOURCE.endswith("/releases/0.1.0/odo.owl")
    assert dependencies.ODO_SHA256 == "eb7318d725a16bd6e68a4cc12955c7b94b885a48d9c3c4f7837d4cd24a8ec7e4"


@pytest.mark.parametrize("file,value", [("odo.owl", b"substituted bundled ontology"),
                                       ("source-catalog.properties", b"odo=https://other.invalid/replacement.owl")])
def test_modified_bytes_or_locator_cannot_be_staged(catalog, tmp_path, file, value):
    path, _ = catalog
    (path / file).write_bytes(value)
    with pytest.raises(ValueError):
        dependencies.stage(path, tmp_path / "state")
    assert not (tmp_path / "state").exists()


def test_staging_requires_prepared_test_state_and_never_overwrites_knowledge(catalog, tmp_path):
    path, content = catalog
    state = tmp_path / "state"
    state.mkdir()
    with pytest.raises(ValueError, match="TEST state"):
        dependencies.stage(path, state)
    (state / "fixture-authority.json").write_text("{}")
    dependencies.stage(path, state)
    target = state / "home/.klab/knowledge/odo.owl"
    assert target.read_bytes() == content
    assert json.loads((state / "semantic-dependencies.json").read_text())["odo_sha256"] == hashlib.sha256(content).hexdigest()
    target.write_bytes(b"existing owned bytes")
    with pytest.raises(FileExistsError):
        dependencies.stage(path, state)
    assert target.read_bytes() == b"existing owned bytes"


@pytest.mark.parametrize("mode", ["valid", "hash_mismatch", "redirect", "oversized"])
def test_capture_is_bounded_hash_checked_and_has_no_partial_output(catalog, tmp_path, monkeypatch, mode):
    _, content = catalog
    output = tmp_path / "captured"
    original = httpx.Client

    def respond(request):
        assert str(request.url) == dependencies.ODO_SOURCE
        if mode == "redirect":
            return httpx.Response(302, headers={"Location": "https://other.invalid/odo.owl"})
        body = content if mode == "valid" else b"other" if mode == "hash_mismatch" else b"x" * (2 * 1024 * 1024 + 1)
        return httpx.Response(200, content=body)

    monkeypatch.setattr(dependencies.httpx, "Client", lambda **kwargs:
        original(transport=httpx.MockTransport(respond), **kwargs))
    if mode == "valid":
        dependencies.capture(output)
        assert dependencies.validate_catalog(output) == content
    else:
        with pytest.raises((ValueError, httpx.HTTPStatusError)):
            dependencies.capture(output)
        assert not output.exists()
