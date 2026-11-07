# Persisted relationship selection and observation collections

`Context.between(source, target)` selects two distinct existing observations without
creating a relationship or contacting a service. Each endpoint is a positive Java
long ID, or an observation bound to the same Runtime/root. A bound DTO from another
root/origin/known Runtime is refused locally; raw IDs still require authoritative
server access checks. A configured home Runtime identity is required. Negative,
unassigned, query-result or detached mediated views are not prospective endpoints.

```python
selected = context.between(source_observation, target_observation)
job = selected.observe("myworldview:Connection")
relationship = job.result(timeout=30)
page = context.observations(limit=64)
children = context.within(source_observation).observations(limit=64)
```

The definition above is illustrative: use a definition actually supplied by your
Reasoner, not the simulator's synthetic vocabulary on real Java. Native header names
are `klab-source-observation` and `klab-target-observation`, with the original
Runtime and focus/observer scope. The existing submit/resolve endpoints are reused.
`within(observation)` clears the pair, matching the inspected Java scope contract;
`between` retains ambient focus. Endpoint selection is not an access grant or
scientific applicability decision. Resolver plan evidence is not execution proof.

`AsyncContext` has the same non-I/O `between` and awaited `observe`/`observations`:

```python
selected = context.between(source, target)
job = await selected.observe(definition)
relationship = await job.result(timeout=30)
page = await context.observations(limit=64)
```

Collections use source-defined direct `HAS_CHILD` observation queries. `ObservationPage`
contains `items`, `offset`, `next_offset`, selected `scope`, and explicit `partial=True`.
Limits are1..128items and1..16MiB response bytes, with one sentinel row to detect a
further page. Fetch the next page explicitly; no automatic recursive crawl, semantic
grouping, complete inventory, revision lease or stable cross-page snapshot is implied.
Invalid arrays, duplicate/unpersisted identities, foreign-root URNs and mutation of
scope/endpoint selection during a request refuse. Returned observations are bound
to the current client so later operations use its current credentials.

## Saved references and lifecycle

Ordinary references remain byte-compatible version1. Saving a relationship-selected
context, observation or Job emits version2 with exactly two additional positive
endpoint fields. No payload, credentials or upstream authority is stored. Old clients
refuse version2 rather than silently losing selection. New clients explicitly attach
the authorized root, look up focus/observer/endpoints using current credentials and
restore the pair before any Job inspection; no creation or computation is replayed.
Restored contexts remain unowned. Reader/client close never releases the owner's
scope; explicit context/session release retains existing remote-disposal semantics.
Version2 requires both distinct endpoints, and version1 cannot carry these fields.

## Independent fixture and native evidence

`observation-selections` is a separately runnable finite simulator scenario. It admits
only `synthetic:Entity` and `synthetic:Connection`, checks endpoint existence/current
owner, records declared parent/source/target receipts and supports direct-child
pagination. No values, scientific model, ontology inference or compiler is fabricated.
Sync/nativeasync public SDK tests cover plan versus result, root/focused collections,
three saved-reference kinds, foreign denial, missing endpoints and owned cleanup.
Evidence is **SIMULATED_PROVISIONAL / JAVA-GAP-0013**, not native acceptance.

Source custody:
`notes/python-roadmap-execution/evidence/observation-selection-source-authority-20261107.json`.
Native source defines headers/scope/query shapes; deployed compatibility is **NOT_RUN**.
The read-only opt-in native test is
`tests/functional/test_observation_selections.py`, requiring prepared authorized
`relationship-selection.json` fixture state and `Client.from_env()` endpoints.
It contains context/source/target handle objects plus `expected_direct_child_ids`;
the test neither creates nor releases remote state or submits Jobs. Native relationship
semantic execution still requires a separately approved compatible scientific fixture.
