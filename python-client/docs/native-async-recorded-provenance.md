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

## Initial package checkpoint

`dcb513069cf77d26fb57a378da86fd35bb9d6925`:149 focused/3,419 installed/all17gates/
build/lint/source parity PASS; exact source/install/raw-harness/artifact identity
verified. Initial109PASS/1test expectation failure corrected to existing specific
HandleIdentityError for wrong membership ID; no production error-type remap.
This package excludes pending semantic #231 additions; accepted-parent integration
and combined validation must precede actual Java provenance acceptance.

- Wheel: `fa043e910264b0ea74564854e1679042c2a9834eb49db0837ea54b822cb86ca5`
- Sdist: `02d24cc4e4ce8a9e7e2979a33a953a821d498ec3d0ec8be806f001c96f1b02a3`

No original-study run, full graph or revision-pinned lineage established by these
offline tests; actual engineering observation/current recorded links still required.

#231 delivered`937c758b62743a0ab68e48792870a9261cf7cc87`,allhostedchecks/ancestry/
tree/209exact-sourcepostmergePASS; branch retired. Parent integrated normally
`12bcec7cd`; only adjacentMarkdown/runner-entry conflicts, both retained. AST verified
all parent suites/reports/scenario choices/readiness, ruff/149focusedPASS. Combined
package active before actual Java provenance run; no older sync/live result reused.

Combined`12bcec7cd392a6c54d23ff444e33f949237b147a`:3,454 installed/all17gates/
build/lint/source parity PASS; exact SDK/install/raw-harness/artifact identity
verified. One fresh owned`async-recorded-provenance-live` run active, no live result
predeclared. Two numeric engineering submissions before query audit are explicit;
they do not execute or recover historical study provenance.

- Wheel: `a3bf8f72b62beb847794ae25ebe458483f427836008dbd93c3f47664f9b4733a`
- Sdist: `9deba64392e081ff1e0434a7d1440c96532a44cc8d9ac44778b1a7117c9c7fbe`

## Exact-candidate Java outcome

`12bcec7cd392a6c54d23ff444e33f949237b147a` actualJavaPASS: original observation15
and retained credential-free handle15 matched after current membership checks.
Six links returned (CREATED×3, CONTEXTUALIZED_BY, RESOLVED, MEASURED); planned
resolution labels kept distinct from recorded relationships. Limit1 first page and
explicit continuation checked. Partial=true and unknown shared-revision consistency
preserved; no full/revision-pinned/historical lineage inferred.

Numeric engineering control234.5m at offsets0/19 passed. Two declared engineering
submissions before provenance audit; provenance phase six Runtime queryPOSTs,
zero submissions/mutations. Context/session/process/listener cleanup passed,
unrelated Java unchanged. Exact SDK/install/raw-harness/live identity, private
report hash and credential absence verified. Final focused/normalPR/CI/eligiblemerge/
postmerge remain required. Historical-study provenance NOT_RECOVERED; A+B incomplete.
