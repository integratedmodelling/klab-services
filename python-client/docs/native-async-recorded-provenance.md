# Native async recorded provenance neighborhood

Base`1af1e4935ee4c3c3cd9a260af3151a7837ab6bcb` (#230). Separate read-only
notebook prerequisite while semantic #231 CI runs; same owned worktree, preserved
reviewed candidate/evidence. Existing fetch_provenance rejects an AsyncClient-bound
ObservationImpl locally via sync-only context validation (reproduced zero HTTP).

Add explicit `async_fetch_provenance(observation, ...)` returning existing immutable
ProvenanceView/Link/Node, using native await Runtime query requests. Share pure
limit/offset/byte preflight, current observation membership/identity checks, exact
relationship query construction and checked immutable link decoding with sync.
Use existing AsyncClient-bound observation validation and credential-free Handle;
never thread-wrap sync transport or run nested event loop. Sync helper still rejects
async inputs, async helper rejects sync/unbound inputs before network.

Preserve limit1..128/offset signedint/response byte budgets and lookahead pagination.
Query current membership before recorded links; missing/unauthorized/malformed/
unsupported422 distinct, never errors-as-empty provenance. Empty neighborhood is
still partial, not absence of lineage. Planned RESOLVED/CONTEXTUALIZED_BY links must
stay distinguished from recorded execution relationships. No recursive traversal,
graph completion, export/provenance atomic revisionlease, inferred producer/source
revision or historical-run reconstruction. Returned provider/metadata values stay
inert and credential-redacted. Native cancellation local only; no query replay.

Tests: initial sync-only async refusal reproduced; then native/sync DTO/query parity,
membership identity/missing/URNUrn mismatch, foreign context/unsupported consumer,
malformed link/class/sequence/relationship/anchor/properties, budgets/lookahead/
empty partial/422/auth/server/redaction/cancel/type controls; keep syncsuite green.
Installed17gates and one exact actualJava original observation handle+recorded link
query required, reusing existing declared numeric engineering fixture if computation
is necessary for provenance. It is not any original-study rerun or new scientific
accuracy benchmark. Exactidentity/privacy/cleanup+guide review/normalPR/CI/eligible
merge/postmerge required. NoJava/worker/provider registration, publication or rights
expansion; fullA/B/study/history/revision gates remain open.
