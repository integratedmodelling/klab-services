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

## Functional acceptance against existing Java

Run the shipped suite against an explicitly prepared, authorized test state:

```sh
python tools/run_functional_acceptance.py --suite execution-evidence --state-dir /approved/test-state --report /approved/results/execution-evidence.xml
```

The existing `scientist.json` supplies the trusted service URLs and the owner's
and second user's valid test credentials. Do not commit that private file or
substitute an administrator credential, invented token or another user's account.

Place this additional manifest in the same state directory:

```json
{
  "shared_observation": "shared-observation.handle.json",
  "private_observation": "private-observation.handle.json",
  "units": "m",
  "model_urn": "staging.vxii.basic.elevation-quantifier",
  "contextualizer": "klab.generators.geospatial.terrain"
}
```

Both handle files must refer to **already computed, still-live** 5x4 EPSG:3857
results with 20 finite cells and the expected model/function represented in their
recorded diagrams. They must belong to distinct contexts on the selected Runtime:

- The owner can read the private result and its evidence.
- The second authenticated user can read the shared result and its evidence, but
  cannot read the private result/evidence.
- The owner must still read the private result after the denial. A deleted fixture
  or invalid credential is not accepted as a successful isolation check.

The tests only restore/read those results. They never submit an observation,
request a new plan, create a session/context or release the operator's work.
Fixture preparation and eventual cleanup remain explicit operator operations.
Missing state fails with a configuration error rather than skipping or claiming
functional acceptance. Functional-test guardrail unit tests use mocks and are
reported only as unit tests, never as live Java/permission evidence.

**Current status (31October):** the shipped two-user suite ran on the integrated
candidate: owner/private passed, but the nonowner/shared observation query returned
Runtime HTTP500. The private-denial assertion was not reached. Both finite20-cell
fixtures were released and the original services/configuration restored. This is
not a permission acceptance pass; PR98 remains unmerged.

Both users also need their own authorized Java service advertisements and
identity-compatible semantic peers. A signed credential and trusted endpoint URLs
alone do not initialize the server-side user's service map. The suite deliberately
does not borrow the owner's service authority. See the
[current review](reviews/discover-compare-explain/execution-evidence-dependent-20261031.md)
and [actual results](results/execution-evidence-two-user-20261031.json).
