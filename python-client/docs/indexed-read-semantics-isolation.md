# Indexed-read request semantics isolation

Base95b7db67f38e75b1dd537c2b25284a6ad7dfc546 (merged PR223). Python-only
RC-P2.2/CAP-08/09 reliability correction. Existing owned worktree reused.

Reproduced against installed ba7773abd: a caller-supplied native semantics dict
is reused by reference across per-offset requests and final decoding. Mutation
after the first request changes later requested units and the final reported unit.
Deterministic mocked transport recorded ['m','km'] for a single read and a final
km-labelled table containing234.5 and0.2345. This is a Python request/attribution
defect, not Java scientific evidence or a demonstrated Java conversion failure.
Exact receipt indexed-read-semantics-mutation-reproducer.json retained privately.

Smallest fix: copy the validated explicit semantics dict once in shared _read_spec,
then return/use that snapshot for all sync/async requests and final read metadata.
Native Semantics strings are unchanged; preserve opaque meaning/key/extensions.
No Python conversion, global locking, deep-freezing arbitrary caller extensions,
scientific mediation, Java code/dependency/authorization change, cross-fetch lease,
or observation/geometry immutable-snapshot guarantee. Existing default semantics
and typed ScanSlice behavior stay unchanged.

Tests must reproduce failure on old source, pass after the change, and cover sync/
nativeasync, originalunits/observable and request equality after caller mutation,
caller dictionary not rewritten by SDK, exact wire values/duplicates and default
semantics regression. Source-derived Java Semantics fields/DTO contract reused.
Installed gates and one actual owned Java read with a request-hook mutation provide
separate transport/scalar evidence without a new scientific fixture. Normal guide
review/PR/CI/eligiblemerge/ancestry/postmerge required. Continue queue after delivery.
