# Native commit-cache inspection

`CommitInspector(context).inspect(commit_id)` and awaited
`AsyncCommitInspector(context).inspect(commit_id)` read the existing source-defined
`GET /api/v1/commit?id=<positive Java-long>` endpoint. This is a **read-only native-shaped
cache report**, not a proposed commit write API or a full graph synchronizer.

The explicit root/Runtime/client/transport/endpoint/credential binding is checked
around reads, with Runtime capability identity confirmed before and after. A current
server-side scope/owner denial propagates. IDs must come from explicit application
evidence, such as the observation's reported `im:commit-id`; a finished Job, numeric
ID order or timestamp does not by itself identify a commit or its predecessor.

The immutable report preserves:
- exact requested commit ID, originating root/Runtime, reported timestamp and owner;
- supplied added assets/observations/cohorts, modified and deleted asset groups;
- supplied added/deleted link triples and credential-filtered raw auxiliary records
  (including cessation/membership/consequence data without inventing their authority).

Missing/null groups remain `None` (unknown), not empty sets. Supplied empty groups
remain empty tuples. IDs are bounded positive signed Java-longs; duplicate entries
within a group refuse, and the item budget spans all seven membership/link arrays.
Groups need not be disjoint. Link endpoints may name assets from earlier transactions,
so the client does not reject them as missing or invent a complete graph from this
one report. Unknown auxiliary fields are retained as bounded immutable metadata,
not dynamically loaded classes or executable content.

Native `DigitalTwinImpl.getCommit` reads `commitCache.getIfPresent`. Inspected source
configures maximum200entries/expire-after-access10minutes; those source settings
are **not a promised deployed retention contract**. A null cache response raises
`MissingAssetError`, not an empty successful delta or permission grant. Local retained
receipts remain historical data after cache expiry; they do not authorize rereads.

## What this does not establish

`CommitInspection.evidence` explicitly says this is not full-graph, contiguous revision
or scientific acceptance. There is no inferred pagination/cursor, next-ID polling,
automatic replay, mutation, commit synthesis or persistent revision lease. Source
`KnowledgeGraph.Commit` includes richer change records but documents temporary cache
custody. No native full-history/complete graph/delta-gap-resync contract is invented.

The separate finite `commit-inspection` fixture adds a native-shaped cache entry only
when its synthetic observation completes and reports the ID in `im:commit-id`.
Pending Jobs do not materialize commits. Explicit test-only expiry and bounded cache
eviction produce null responses; fixture maximum128 is not the native200setting or
a scientific retention claim. Current-principal denial and owned root cleanup are
public sync/async workflows, not native compatibility or native scientific execution.
Durable fixture storage refuses rather than promising restart retention.

The original RC-P3.1/CAP-10/CAP-11 **full finite reference checkpoint/delta/gap-resync
reconciliation remains unfinished work** after this native read primitive. Existing
partial GraphProjection and observation-ID-only reference checkpoints retain their
separate meanings. Native commits/compatibility/scientific lineage remain distinct
acceptance gates; no Java/IDE/worldview repair is performed.
