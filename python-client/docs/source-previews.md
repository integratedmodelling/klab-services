# Read-only source-document previews

`SourcePreviews` sends source text only to the existing Resources parser. It
supports four whole-document knowledge classes: `NAMESPACE`, `ONTOLOGY`,
`OBSERVATION_STRATEGY_DOCUMENT` and `BEHAVIOR`. No Python grammar, semantic
algorithm, import endpoint, compilation or execution is implemented.

```python
from klab_client import SourcePreviews

# client already has an explicitly configured, authorized Resources endpoint.
reader = SourcePreviews(client)
preview = reader.parse(ontology_source, kind="ONTOLOGY")
print(preview.urn, preview.service_id, preview.source_sha256)
```

Only source strings are accepted, never arbitrary Java DTOs. Default fidelity is
exact unchanged source. If the server returns only edge-trimmed ASCII
space/tab/CR/LF and that is acceptable for a **preview**, opt in explicitly:

```python
preview = reader.parse(behavior_source, kind="BEHAVIOR", allow_edge_trimming=True)
assert preview.edge_whitespace_trimmed == (
    preview.source_sha256 != preview.parser_source_sha256)
```

Both SHA256 hashes are recorded; any other source change is rejected. This is
not an assertion that all maintained parser versions perform the same trimming.
Inner whitespace, CRLF normalization, token rewriting and Unicode edge-space
normalization are not silently accepted. Source byte budget defaults to1MiB,
HTTP/payload response budget2MiB, configurable only within bounded limits.

Resources capabilities are read before and after parsing; service ID, configured
origin/base path, credentials and transport stay pinned to that preview session.
Client identity fields grant no authorization. Endpoint/credential changes after
the session is bound reject before a new request to that changed endpoint.
Malformed class/URN/source/identity, server error notifications, nonfinite JSON,
invalid UTF-8 and credential material in decoded source/metadata are rejected.
JSON escaping cannot hide configured credential material. Credentials are not
copied from Runtime into Resources. Missing Resources configuration is an error.

`SourcePreview` is an immutable receipt with immutable redacted metadata
projection. The raw field follows the existing inspection safety policy (e.g.
credential-key removal), not byte-for-byte raw HTTP preservation. Source is not
printed in its repr. Nested data are not scientific interpretations; known
classes are checked as strings, never dynamically imported. Error notifications
raise the existing typed error; this is not yet a complete invalid-document
diagnostic editor or an ordered validation snapshot.

## Deliberate separation from authoring

`SourcePreview` is **not** `ParsedSource`. It cannot be supplied to
`Authoring.create_document`, even for a namespace. `Authoring.DOCUMENTS` remains
namespace-only for its existing exact-source ADD path. No new ontology,
strategy-document or behavior write is enabled by this feature. Broader
authoring/edit/import/version/conflict/lock workflows remain separate work.
No workspace/project/agent/session is created or disposed. Server parsing may
perform its normal validation/computation/logging; “read-only” means the client
does not call persistence or execution routes, not that server CPU is free.

## Reproduce the bounded contract tests

From `python-client/` with declared development extras:

```powershell
python -m pytest -q tests/unit/test_source_previews.py tests/unit/test_authoring.py
```

From repository root with Java21:

```powershell
& ./mvnw.cmd -B -pl klab.core.common -am '-Dtest=SourcePreviewWireTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The committed shared fixture is generated/compared against production Jackson
serialization of four native document DTOs, with an assertion that the actual
`KnowledgeClass.getAssetClass()` mapping accepts each DTO. Python tests consume
that same fixture. It uses explicitly DTO-only text: **this does not run or
establish acceptance by the Resources parser**. The dedicated Java wire CI job
is named accordingly. Request/negative/immutability/write-isolation tests are
deterministic HTTP mocks; no fixture workspace or scientific data is changed.

Live maintained-server parser and semantic acceptance for these four source
families remain unaccepted in this slice. The current full Resources/Runtime
build fails on the documented LanguageAdapter EXTENT/ATTRIBUTE dependency
mismatch (#89/#125). Restore a coherent maintained source/artifact pair, then
exercise original source, invalid-document/credential positive controls and
bounded source-fidelity fixtures. Do not port grammars, change scientific
meaning, or substitute the DTO serializer test for that live acceptance.
