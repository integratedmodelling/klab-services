# Contribution review: indexed point-offset long bounds

## Recommendation

Ready for maintainer review of the bounded wire-domain correction, subject to
combined-integration checks before delivery. Contributor/CLI review under
KLAB_CLI_Contribution_Review.md, not human approval. A reproduced Python validation
gap permits impossible native-wire integers into requests. The shared preflight
upperbound is the smallest correction, without Java repair or geometry inference.

## Reviewed state

Repository klab-services; integration597b2fed15f3b25960667a7d47322a55ef96d796
(PR224). Branch fix/python-indexed-offset-long-bounds, contract654a09ac7 before
implementation. Shared-reader PR225 independently pendingCI, unchanged pushed
candidate preserved. Reused owned worktree, no unrelated changes.

## Capability and boundaries

Sync/nativeasync fetch_data reject exact integer offsets outside0..2**63-1 before
any HTTP. Existing1..256 bounded iterable, order, duplicates and bool/noninteger/
negative validation retained. Native StorageScan.Point offset is Java long; maximum
domain-valid offset is not a claim of any actual observation's cell existence.
No scientific calculation, Java/dependency/API/policy changes; no time/revision lease.

## Findings, ordered by consequence

Important Python defect: Context._read_spec checked only type/nonnegative offset,
allowing2**63 and larger into JSON. Mocked reproducer and six red regression cases
demonstrate the SDK path; not a Java server defect or a real-network scientific
result. Added one shared signed-long upperbound with unchanged count rules, no
clamping/casting or premature partial requests. No other important/blocking defect
demonstrated in the bounded correction; full package evidence remains required.

## Validation actually performed

| Check / command | Revision/environment | Status | Establishes / limits |
|---|---|---|---|
| New offset tests beforefix, pytest test_indexed_offset_long_bounds.py | source597b2fed, Windows Python3.12.7 | Failed6,Passed2 | Sync/async oversized offset reached mockedHTTP; existing valid domain controls |
| NativePointOffsetContractProbe.java via existing accepted Java classpath/Temurin21 | unchanged Java StorageScan.Point | Passed | Actual constructor/JSON0/maxlong exact, negative rejected, out-of-long Java parse fails; not servercell existence |
| Focused and fresh installed regression/gates | e9a4b6e37 | Passed:154focused/3,249installed/all17gates | Build/install/parity/lint; source/install/artifact identity bound; pre-PR225 baseline |

## Scientific and lifecycle evidence

New claim is local impossible-input refusal, so no model/service restart or live
scientific rerun required. Prior science is not reused as validation of changed SDK
code; tests prove unchanged valid wire values on mocked transport, not actual huge
cells. No owned live services launched for this slice. Existing scientific gates
stay open and original study execution remains NOT_RUN.
Exact source/installed/artifact/rawharness identity verified in existing private
indexed-offset-long-bounds-final-validation.json; native probe report/hash retained.
No important/blocking defect demonstrated after correction within this scope;
combined-integration checks and hosted review/acceptance remain separate gates.

## Contributor coordination

No Java/component changes, principal expansion, NiFi overlap, custodian contact or
release decision. PR225 independent CI/merge must be reconciled normally before
submission of this feature, preserving its candidate/evidence and all unrelated work.

## Smallest next actions

Finish installed/focused gates, exact SDK/artifact identity and final documentation;
integrate accepted upstream normally when PR225 delivered, then final-head tests/
normalPR/CI/eligiblemerge/ancestry/postmerge. Continue independent queue afterward.

## Suggested PR description

Reject indexed point offsets outside native signed-long domain before any sync/
async HTTP. Preserve exact valid values/order/duplicates/bounded iterable semantics.
Include red/green/native-constructor/installed evidence, without claiming geometry
cell availability, scientific execution or remote revision guarantees.
