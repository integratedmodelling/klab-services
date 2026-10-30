# Python targets the existing Java services

The client must use the existing Java HTTP contract. Updating Java, adding a
parser enum value or upgrading Java dependencies is **not** a client requirement.

## What was checked

The client's semantic enum exactly matches the existing Java API: **81 flags**.
The inventory was independently checked against the milestone's Java source and
the installed October16 Runtime API JAR, not generated from the Python enum.
The existing Java API also supplies all **13 non-VOID activity names** accepted
by the typed observation request code.

There are two different enums involved in the earlier investigation:

- Java's existing public API `SemanticType` already contains `RELATIONSHIP`.
  Python must retain it when decoding a relationship returned by that service.
- The language parser's `SemanticSyntax.Type` is a separate Java-internal contract.
  The Python client does not construct it, import it or require its newer value.

The compiler reproducer intentionally compares two parser JAR versions. Neither
JAR is a Python package dependency or a required client upgrade. Its compiler
failure does not demonstrate that a Python request introduced an unsupported type.
The shared Maven CI failure remains separate from runtime client compatibility.

## Python behavior and regressions

- Requests preserve server-resolved observable data and symbolic API flags;
  they do not invent parser enum values or convert them to numeric ordinals.
- Every existing API semantic flag is checked through the decoder.
- Existing activity names round-trip unchanged through typed request encoding.
- `VOID` and unknown activities are rejected rather than silently replaced with
  a supposedly supported operation.
- The bounded elevation pilot uses existing `earth:Terrestrial earth:Region`,
  not an unqualified Region. That was the demonstrated Python workflow correction;
  it made the numerical/model-association check succeed with unchanged services.

The new audit found no additional enum/wire mismatch requiring a production
library change. It adds compatibility regressions instead of changing working
serialization or deleting a flag that the existing Java API actually supports.

Contract inventory: `tests/fixtures/existing_java_wire_enums.json`.
Regression suite: `tests/unit/test_existing_java_compatibility.py`.

## Validation, 30 October 2026

Environment: Windows, Python3.12.7, isolated `pr99-inspector-312` interpreter;
commands run from `python-client/`. No Java/POM/workflow/dependency changes.

| Check | Result |
| --- | --- |
| `python -m pytest tests/unit/test_existing_java_compatibility.py tests/unit/test_resource_inspection_workflow.py -q --tb=short` | Passed, exit0;141 tests |
| `python -m pytest tests/unit tests/integration -q --tb=short` | Passed, exit0;1342 tests, one existing Rasterio warning |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82` | Passed, exit0 |
| Existing restored October16 services: status GETs, model/resource listing through `ResourceInspector` | Passed; empty listings, not a numerical computation claim |

The read-only service check used the existing local-owner authentication, bounded
to exact loopback service paths. It did not resolve semantics, submit observations,
create sessions/contexts, restart services or alter configuration. It consumes
none of the one remaining authorized scientific attempts.

Earlier numerical success used the separately recorded unchanged October27
service distribution and pinned knowledge/components. Do not relabel that as a
new numerical result on the restored October16 deployment. Exact deployment/source
attestation and the nonowner-permission fixture remain separate evidence limits.
