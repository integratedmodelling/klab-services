# Recorded execution evidence and separate plans

```python
from klab_client import fetch_execution_evidence, decode_dataflow

evidence = fetch_execution_evidence(observation)
print(evidence.summary())
for activity in evidence.activities:
    print(activity.id, activity.activity_type, activity.outcome, activity.missing)
    for plan in activity.charts:
        print(plan.summary())

# Only for a separately obtained existing Resolver Job:
plan = decode_dataflow(resolver_job.result(timeout=30))
```

`fetch_execution_evidence` never asks the Resolver to re-plan earlier work. It
refreshes the existing bounded provenance neighborhood, which checks current
observation membership and Runtime/context identity. It then retrieves up to
`activity_limit` currently scoped ACTIVITY records using `/api/v1/query` and
validates the returned activity identities and any supplied observation URN.
Server authorization remains authoritative for every fetch. Missing records stay
partial/unavailable; malformed identities and failed/forbidden/unsupported requests
do not become empty successful evidence.

Supported diagrams are only actual `im:resolution-graph` and `im:dataflow-graph`
activity metadata, with plain or `MetadataImpl.delegate` map envelopes. They remain
**resolution plans** or **contextualization plans**, even on successful activities.
Their node/port/link identities, parallel links, multi-endpoint/reference edges,
labels and compatible raw fields are preserved without evaluating metadata.
Chart-local identifiers are not graph asset query IDs. Containment is checked as a
bounded tree; connectivity can contain cycles/self-links/disconnected nodes.
No HTML/script/provider link or computation is executed. Plain summaries and escaped
notebook tables are projections of the same decoded immutable data.

`decode_dataflow` projects a received Java Dataflow into `DataflowEvidence`, retaining
the explicit `RESOLVED`, `NO_MODEL` or `FAILED` outcome and supplied coverage,
requirements, actuator occurrences, call/strategy identities and diagnostics.
Repeated reference actuators remain separate occurrences with distinct containment
paths. `NO_MODEL` accepts existence without executable computation where Java
supports it; it is not a computed numerical field. Unknown/missing outcomes reject
instead of being guessed from `empty` or Job completion.

Job completion, resolution success, activity execution outcome and durable root
commit are separate facts. Failed activities remain visible. Missing diagram metadata
is **not** evidence of no dependencies. Every returned neighborhood remains partial,
and activity completion alone has an explicitly unknown durable-root-commit status.
There is no complete lineage/resource-version closure, portable replay,
`encode_dataflow`, graph reproduction, arbitrary activity lookup or new Java route.

Defaults: one 64-link page (plus the existing lookahead), 16 activity lookups,
1 MiB per HTTP response and per decoded diagram/Dataflow, 256 nodes+ports and
512 links per chart, or 256 actuator occurrences per Dataflow. Maxima are explicit:
64 activity lookups, 16 MiB bytes, 4,096 chart nodes+ports/actuators, 8,192 chart
links. Byte limits on decoded JSON include its canonical JSON encoding and can
therefore reject even a compact wire response. Budgets are not an RSS guarantee.
No implicit recursive activity traversal occurs; exhausting the activity budget
adds a diagnostic, not an invented complete graph. Pagination has no snapshot lease.

Live persisted diagram retrieval and representation were verified on 29 October
against an actual 20-cell synthetic terrain result: its recorded resolution named
the inspected model, and its contextualization chart named the terrain generator.
This is not independent scientific accuracy or exhaustive lineage. A valid
nonowner/private-context denial fixture remains required for permission acceptance.
See [live evidence](results/execution-evidence-local-acceptance-20261029.json),
[contracts](discover-compare-explain-contracts.md) and
[the feature review](reviews/discover-compare-explain/execution-evidence.md).
