# Contribution review: point-read source ID long bounds

## Recommendation and state

Ready for maintainer review of the bounded correction, subject to combined accepted
integration validation before delivery. Contributor/CLI review under
`KLAB_CLI_Contribution_Review.md`, not human approval. Repository klab-services;
accepted integration `13b723aa3c361e011031e76e5a7730ae0caf28c1` (#228), branch
`fix/python-indexed-source-id-long-bounds`, contract `fc9a254b2` before correction.
CODELIST #229 independent CI; candidate/evidence preserved in same owned worktree.

## Demonstrated defect and smallest correction

`StorageScan.Point.sourceId` is a positive Java long. Shared Python `_read_spec`
checked only exact integer and positivity, allowing `2**63` or larger to reach
HTTP in both facades. This is a Python wire-domain validation defect, not a
demonstrated Java defect. A one-condition upperbound plus accurate error text
rejects impossible observation IDs as ProtocolError before reads. Caller offset
validation, source-zero/consumer binding refusal and general DTO/handle/job IDs
are deliberately unchanged. No cast/clamp, signature/dependency/Java/policy change.

## Validation actually performed

| Check | Revision/environment | Result | Establishes / limits |
|---|---|---|---|
| Oversized source-ID tests before fix | accepted SDK, Windows Python 3.12 | 4 failed / 12 passed | Both facades emitted impossible sourceId; valid/control cases passed |
| Initial harness run | same SDK | 6 failed / 10 passed | Four genuine bound failures, two malformed detached-control setup failures; corrected setup mutates decoded observation metadata as existing foundation tests do |
| NativePointSourceIdContractProbe.java, existing classpath/Temurin 21 | unchanged Java | Passed | Constructor/JSON preserve1/maxlong; reject0/negative; out-of-long string parse fails; no source existence/read claim |
| Focused plus fresh installed/17 gates | 6f782b4a4, Windows Python 3.12 | Passed:176 focused/3,362 installed/all17gates | Build/lint/source parity and exact source/install/harness/artifact/probe identity; before #229 integration |

## Findings, scientific and lifecycle limits

Important Python validation defect demonstrated above; bounded correction only.
Mocked positive maxlong identity does not establish a real source or authorization.
Native probe exercises actual constructor/serialization, not runtime computation.
No scientific model/service rerun needed for local impossible-integer refusal and
no prior scientific outcome substituted as changed-SDK evidence. No live services
or credentials used in the new tests/probe; no Java production changes.

No additional important/blocking defect demonstrated after correction in bounded
scope; full installed tests passed, combined accepted integration tests remain
required. Not exhaustive proof or human acceptance.
Full A+B/identity-incarnation/revision/scientific study gates stay open. No component
publication, principal expansion, NiFi/grammar work or custodian outreach.

## Next actions

Finish installed tests, bind exact candidate/artifacts/probe; reconcile #229 normal
eligible merge and test combined candidate before separate PR. Final focused/map
checks, normal PR/CI/merge/ancestry/tree equality and exact-source postmerge. Preserve
red/harness failures and unrelated research/categorical/peer work; continue queue.
