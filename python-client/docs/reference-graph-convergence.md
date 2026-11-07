# Finite reference graph convergence

`ReferenceCommits` / `AsyncReferenceCommits` requires explicit root Context,
`allow_experimental=True` and exact Runtime advertisement
`graphCommitProtocol="klab.experimental.graph-commits/1"`. No native fallback exists.
This **separate proposed protocol** never reinterprets native cached commit reports,
partial `GraphProjection` pages or the older observation-ID-only reference checkpoints.

```python
commits = ReferenceCommits(context, allow_experimental=True)
projection = ReferenceGraphProjection(commits)
initial = projection.resync().snapshot
checkpoint = commits.checkpoint(expected_revision=initial.revision)
current = projection.refresh().snapshot
assert current.digest == checkpoint.digest
```

Await the async counterparts directly; no thread-offloaded synchronous I/O. Two
separately attached clients can reconcile to identical immutable canonical views.

## Explicit contract

- `snapshot(revision=None)` reads current head or one retained exact revision.
  Snapshots pin root, Runtime, epoch, revision, canonical SHA256 and finite complete
  membership. Nodes are CONTEXT/OBSERVATION/RECORDED_ACTIVITY, edges CONTAINS/PRODUCED.
  Endpoints, kind pairing, unique identity, reachability and digest must validate.
- `checkpoint(expected_revision=N)` explicitly captures completed finite graph state
  with CAS. Pending or completed Jobs **do not advance reference head**. HTTP409 is a
  conflict, not automatic retry. Incomplete acknowledgments or post-write binding
  changes raise `SubmissionOutcomeUnknown`: inspect head, never blindly replay.
- `reconcile(base)` reads only a chain pinned to the exact base epoch/revision/digest.
  Each contiguous delta has previous/current digests, node/edge removals and upserts.
  The whole result validates before acceptance. Invalid/gapped/partial/forked responses
  never overwrite a prior accepted projection; no partially applied graph is returned.
- `ReferenceGraphProjection.resync()` explicitly reads a full snapshot;
  `.refresh()` incrementally reconciles the retained snapshot. HTTP409/410 or changed
  epoch produces `ReferenceResyncRequired`, retains historical data marked invalid,
  and requires `.resync()` before another incremental refresh. No hidden full read.
- `.hint(job)` only invalidates freshness locally. Duplicate/reordered same-root Job
  hints cannot create commits, revisions, graph nodes or ordering authority. A hint
  received during a read keeps the resulting view invalidated. The caller must
  supply a Job bound to that projection's exact client/root.
- `.clear()` forgets local data only. A read started before clear cannot repopulate
  it. Slow or same-revision forked responses cannot roll back an accepted graph;
  concurrent old-epoch reads cannot overwrite a newer accepted epoch. Cancellation
  preserves prior historical data, marks it invalid, and propagates cancellation.

Finite bounds:256nodes/1024edges,2MiB canonical aggregate graph and2MiB wire responses,
at most8deltas per response, positive bounded request budgets and safe JSON values.
All groups and exact protocol selectors validate; no arbitrary class loading/execution.
Configured credential material is refused without echo. Local historical views are
**not current permission leases**; services decide current principal/root authorization.

## Independent fixture and acceptance boundary

The separate `graph-convergence` fixture derives snapshots from completed synthetic
observations under the request lock, with declared reference activities—not native
activity IDs, scientific execution, or native historical lineage. Revision0 contains
only the explicit context; an explicit checkpoint captures observations. Eight
snapshots/root,127completed observations/root and128reference roots are retained
ephemerally. Missing history is410, changed epoch/base digest409. Durable storage
refuses. Runtime root release removes owned reference history.

Authenticated test-only rename/drop/epoch controls exercise modification/removal and
history reset. They are **not native SDK update/delete/restart APIs**. Public sync/
async two-client workflows cover actual submission/completion/CAS/snapshot/history/
delta/projection, duplicate hints, modification/removal, gap/epoch/resync, principal
denial/root refusal, one concurrent CAS winner and cleanup.

Every snapshot labels completeness
`REFERENCE_COMPLETE_FINITE_GRAPH_NOT_NATIVE_SCIENTIFIC_COMMIT`. This means complete
only for the declared finite graph, not the full native scientific knowledge graph,
source/permission closure or durable engine lineage. Native `GET /api/v1/commit?id`
remains a distinct temporary-cache read; IDs/timestamps are not contiguous revisions.
Maintained native revision/incarnation/retention/notification contracts and approved
real two-client/scientific evidence remain JAVA-GAP-0016 obligations. Simulation
never substitutes silently for unavailable or failed native services. No IDE repair,
native mutation, release promotion or scientific acceptance follows from this API.
