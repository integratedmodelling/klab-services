# Experimental bounded snapshot shards

**JAVA-GAP-0006/0014 — SIMULATED_PROVISIONAL, native contract UNKNOWN_PENDING_REFERENCE.**
`klab.experimental.read-plans/1` is a proposed reference protocol, not Java's native
Point/READ_VALUE API. The SDK never imports the simulator or falls back to it.

```python
from klab_client import open_read_plan, read_plan_shard, assemble_read_plan, release_read_plan

plan = open_read_plan(observation, allow_experimental=True,
    shard_cells=128, max_cells=4096, lease_seconds=30)
try:
    shards = tuple(read_plan_shard(plan, i, allow_experimental=True)
                   for i in range(len(plan.shards)))
    snapshot = assemble_read_plan(plan, shards)
finally:
    release_read_plan(plan, allow_experimental=True)
```

Native asynchronous counterparts are `async_open_read_plan`,
`async_read_plan_shard` and `async_release_read_plan`. Assembly is local and requires
the complete immutable shard tuple in manifest order; no partial success, mixed
revision, automatic replan or replay is returned. Root context, observation and
originating Runtime binding must be explicit, with current credentials on requests.

Proposed routes under the selected Runtime base URL:

- POST `/experimental/read-plans/v1/open`: protocol, observation_id, shard_cells,
  lease_seconds, max_cells. Exact integer budgets:1–128cells/shard,1–60seconds,
  1–4096total cells. Unknown open outcomes are never automatically retried.
- GET `/experimental/read-plans/v1/{plan_id}/shards/{index}` with explicit
  `protocol` and `epoch` query. Immutable contiguous shard manifest pins counts,
  offsets and SHA256; each response repeats plan/epoch/revision/index/offset.
- DELETE `/experimental/read-plans/v1/{plan_id}` with the same query. Unknown
  release outcomes remain unknown; exact boolean release confirmation is required.

Manifest includes float64dtype,2Dshape,CRS, north-to-south row-major affine transform,
units (or explicit absence), fixture origin and exact root/Runtime/observation.
Valid zero/negative cells remain valid; invalid cells are `None` with exact false
mask. SDK response JSON is bounded64KiB, strict UTF8, duplicate-free and finite.
Per-shard and assembled digests cover canonical JSON values+valid arrays. Revision
kind is **REFERENCE_CONTENT_SHA256_NOT_NATIVE_COMMIT**: neither a native committed
revision nor a scientific provenance/acceptance claim. Float64 reference coercion
does not certify native storage dtype or integer precision beyond exact2**53.

The separate simulator permits only elevation, zero-negative-nodata and recorded
USGS numerical fixture mappings. Other fixture mappings refuse422, not guess.
It pins copied cell buffers at open and limits grants to16per process, validates
request bytes within4KiB and manifest bytes within64KiB before allocating a grant.
Explicit synthetic buffer changes do not mutate a previously pinned snapshot.
TTL/release/context cleanup removes grants; expired/lost grants fail410. Other
owners/roots/Runtime IDs deny. Capacity429is not silent eviction.

Owned SQLite stores observations but deliberately **does not persist read grants**.
Restart changes process epoch; old grants fail410even when source observation
reattachment succeeds. A fresh plan requires an explicit caller operation. Failed
durable requests roll back ephemeral rows/epoch along with durable state, including
failed reset and release. This is not durable capability custody or native lease
revocation/convergence acceptance. Native block/layout/revision contracts, deployment
latency and scientific execution remain external gates; do not close issue123.
