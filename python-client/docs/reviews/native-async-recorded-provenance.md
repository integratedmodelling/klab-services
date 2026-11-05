# Contribution review: native async recorded provenance

## Recommendation and reviewed state

Needs installed/actual Java evidence before submission. Contributor/CLI review under
`KLAB_CLI_Contribution_Review.md`, not human acceptance. Accepted base
`1af1e4935ee4c3c3cd9a260af3151a7837ab6bcb` (#230), branch
`feature/python-async-recorded-provenance`; contract`4d2be1993` preceded code.
Semantic #231 independent CI, candidate/evidence preserved. Same owned worktree,
no unrelated categorical/peer/research changes discarded.

## Capability, findings and contract limits

Existing fetch_provenance rejects AsyncClient-bound observations via sync-only
context gate; zero-HTTP refusal reproduced. New explicit async_fetch_provenance
awaits existing Runtime membership/query routes and returns same immutable nodes/
links/view. Shared pure limits/membership/query/422/link-decode helpers preserve sync
behavior; no thread wrapper, nested loop or Java change. Native AsyncClient and
committed bound observation required before network. Inert raw metadata redacted.

No complete graph/recursive traversal/atomic export-provenance revision/source
history inferred. Empty page remains partial; relationships marked planned resolution
stay distinct from recorded execution effects. Explicit limit+1 pagination only
stable in an unchanged graph. Credentials/Handle do not grant authority: current
membership must be checked before links. Errors are never empty provenance success.

Initial tests:109passed/1failed because wrong-ID membership raises the existing
specific HandleIdentityError, not ProtocolError. Test expectation corrected to
preserve that contract; no production identity-error remapping. No demonstrated
important/blocking defect after initial tests; full package/live evidence required.

## Validation actually performed

| Check | Revision/environment | Result | Establishes / limits |
|---|---|---|---|
| Existing sync-only refusal | acceptedSDK, mocktransport | Reproduced zeroHTTP | Missing nativeasync facade, noJava defect |
| Initial nativeasync+existing sync tests | candidate source, WindowsPython3.12 | 109passed/1expectation failure | Correct actual HandleIdentityError expectation before final validation |
| Corrected focused/package/17gates | dcb513069, Windows Python3.12 | Passed:149focused/3,419installed/all17gates | DTO/query parity/membership/errors/budgets/pagination/empty/plans/immutability/redaction/cancel; build/lint/parity and exact SDK/install/harness/artifacts verified, before #231 integration |
| Original engineering observation actualJava neighborhood | freshownedfixture | Notrun | Required original identity/current membership/nonempty recorded links/partial pagination/query-only audit and cleanup; not historical study lineage |

## Scientific fidelity, identity, lifecycle and coordination

Owned numeric engineering fixture234.5m/20cells may be created once to provide
actual recorded execution evidence, with explicit two submissions reported outside
provenance-query audit. Check original handle/current membership, recorded links,
partial flag/unknown consistency and explicit continuation. No Python science or
original-study inputs substituted. Source/provenance revisions, missing original
run/input/source lineage and all three study gates remain unavailable/incomplete.

Audit around provenance phase permits only Runtime query POST; no scientific/
authoring/lifecycle mutation in that phase. Owner performs explicit cleanup outside
that audit. Unknown mutation outcome is not replayed. Cancellation stops only local
waits. TEST/local federation not production Hub/license acceptance. Exact SDK/install/
harness/live/report privacy and owned scope/process/listener cleanup required.

No Java/component publication, authority/principal expansion, NiFi/compiler/grammar
or custodian outreach. Full history/revision/authoritative provenance/A+B incomplete.
Finish installed gates, normally integrate accepted #231/combined validation, one
actual Java acceptance, bind evidence, final focused/map/review/normal PR/CI/merge/
ancestry/postmerge; continue independent queue. No unchanged scientific retries.
