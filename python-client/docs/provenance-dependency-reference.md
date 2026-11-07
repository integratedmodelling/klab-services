# Declared dependency reference workflow

The separate simulator scenario `provenance-dependencies` exercises ordinary
`fetch_provenance` and `async_fetch_provenance` calls over the existing Runtime graph
query route. No production SDK implementation or native Java service is changed.

Completed observations have synthetic `MEASURED` activity edges. Consecutive
completed observations in one owner context additionally have a declared
`CONTRIBUTED_TO` edge and a `RESOLVED` actuator plan edge. These are fixture
declarations, not computed dependencies or proof that a Resolver selected a model.
Planned resolution remains distinct from recorded relationships. An unfinished Job
does not add the fixture's dependency edge. Other scenarios preserve their existing
measurement-only behavior and observation insertion order.

Two independent same-owner clients test paginated reads, explicit fresh inspection
after another observation completes, immutable old local evidence, and owner state
surviving reader closure. Empty pages remain partial evidence, not complete lineage.
Different contexts never compose their graphs; a foreign principal is denied.
Owner context/session cleanup removes the underlying state with no resubmission.
Malformed, empty or over-budget query children and invalid typed endpoint identities
return explicit422 errors when sent as JSON. This finite parser is not a general
knowledge-graph query engine.

Evidence: **SIMULATED_PROVISIONAL / JAVA-GAP-0016**. Source hashes, fixture ordering
and successful refreshes establish no native scientific commit, atomic revision,
durable history, complete lineage, invalidation or notification convergence.
Those acceptance obligations remain open pending a pinned maintained Java contract
and same-workflow real execution, without simulator fallback.

Run `pytest developer-tools/klab-service-simulator/tests/test_provenance_dependencies.py`
with the ordinary installed SDK and separate simulator. The full refresh-failure
controls remain in `test_partial_refresh.py`. In-process fixture coverage is distinct
from isolated HTTP workflows: child-process execution alone does not attest parent
coverage.
