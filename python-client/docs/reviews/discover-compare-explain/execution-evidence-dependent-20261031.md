# Contribution review: PR98 on the merged F1/F2 base

## Recommendation

**Needs contract/coordination evidence; not yet ready to merge.** Python fixes,
offline regression tests and coverage/lint pass. The actual shipped two-user
functional suite ran: owner/private passed, but nonowner/shared failed with
Runtime query HTTP500. Private denial was not reached. The integrated candidate
therefore does not satisfy the user-authorized conditional merge gate.

## Reviewed state

- Target: `feature/python-discover-compare-explain-integration`.
- Base: `0d17032ca5083cb4a354a1509af12d5d36a52dd9`, containing merged PR101/99.
- Integrated source: `8322773efbc57752a4c17490767de158750d2d1b`.
- Fixed source: `e77c26456ce3d6bd67aa8a339a8b86f913baaafd`.
- Included changes: the complete F3 delta from that base, plus the two fixes below.
  There were no unresolved merge conflicts, Java changes or scientific source edits.
- Authority: the user explicitly authorized sequential review/fix/conditional merge
  of101,99,98,100, deliberate retargeting to this integration branch, and one bounded
  PR98 fixture with two signed TEST users. PR102 is excluded.
- Applies the contributor review guide, feature scope and current Java query,
  persistence, diagram, ownership and context-authorization contracts. This is the
  implementing agent's assessment, not independent maintainer approval.

## Capability and boundaries

Read a computed observation's recorded, scoped Activity diagrams without replanning
or replaying it. Decode chart containment/connectivity and Dataflow outcomes into
bounded immutable views. Preserve missing/partial/plan distinctions; do not infer
complete lineage or durable root commit from successful activity execution.

The production change remains one direct decoder/fetch module and public exports.
The shipped functional suite reads retained results only; external authorized
fixture preparation and cleanup are separate from its assertions.

## Findings, ordered by consequence

1. **Important credential-display defect, fixed.** In `evidence.py`, chart node,
   port and link IDs were copied from unredacted payloads into typed fields even
   though raw metadata was redacted. Echoing a configured credential in a node ID
   exposed it through text/HTML output. Three regression cases failed before the
   fix. All graph-wide canonical IDs now reject credential-bearing material with
   a generic ProtocolError, rather than silently changing graph connectivity.
2. **Important numeric-boundary defect, fixed.** `decode_dataflow` called
   `math.isfinite` before fraction bounds, allowing a JSON integer such as10**400
   to leak OverflowError. Positive and negative regressions failed before the fix.
   Checking the supported0..1 range first rejects both with ProtocolError while
   retaining existing finite-number/type checks. No numeric clamping was added.
3. **Required live permission evidence outstanding.** Both valid-user shared
   access and owner/private positive controls must succeed before a nonowner's
   private denial counts. The existing test also rechecks owner access after the
   denial. Invalid tokens, deleted contexts, mocked HTTP responses and test
   collection cannot establish this gate.
4. **Test authority preparation mismatch, not a demonstrated SDK defect.** The
   first setup lacked Java's user-group metadata lookup. The second supplied the
   wrong authentication-header name. Both stopped at readiness before observation
   submission and restored services. Source tracing identified
   `ScopeManager.createUserIdentity` and `Utils.Http.Client.withAuthentication` as
   the contracts. The external issuer now checks signed requests using Java's
   `Authentication` header and returns the fixture users' empty group membership.
   It neither fabricates scientific responses nor bypasses Java JWT validation.
5. **Actual nonowner/shared acceptance failed.** The fresh shipped suite ran after
   preparing two finite20-cell results with the explicitly renewed four-Job budget.
   Owner/private data and recorded model/function diagrams passed. The second user
   authenticated, reconnected to the shared context, then its observation query
   returned HTTP500 `EXECUTION_FAILED`. The test correctly failed; no denial or
   successful isolation is inferred. This does not establish a decoder defect or
   a production Java defect by itself.
6. **Source-traced fixture prerequisite; root cause still unverified.** Only the
   owner's Java user scope was initialized with service advertisements. In
   `ServiceContextScope.forRequest`/`ServiceUserScope.useRequestAuthority`, Java
   intentionally copies the requesting user's service map rather than the owner's.
   `KnowledgeGraphNeo4j` resolves queried observation observables through that map's
   Reasoner. The nonowner had no such advertisement; the test state was therefore
   incomplete for this contract. The source also requires identity-compatible
   semantic peers, not an owner-key substitute for the second user. Verify both
   users' service bindings before any renewed fixture computation. The caught
   backend cause was not independently reproduced, so this remains a source-traced
   diagnosis rather than a claim that fixing one prerequisite guarantees success.

## Validation actually performed

Windows Python3.12.7, `pr99-inspector-312/Scripts/python.exe`; explicit
`PYTHONPATH=pr98-execution-evidence/python-client/src`. Commands from `python-client`:

| Check / command | Status | Meaning / limits |
| --- | --- | --- |
| `python -m pytest tests/unit/test_execution_evidence.py -k 'chart_identities or unrepresentable' -q --tb=short` before fixes | Failed, exit1;5 regressions | Reproduces both production defects before correction |
| `python -m pytest tests/unit tests/integration -q --tb=short --cov=klab_client --cov=workflows.resource_inspection --cov=run_resource_inspector_acceptance --cov-branch --cov-report=json:<external>/pr98-dependent-full-coverage.json` | Passed, exit0;1,428 tests in36.31s | Exact fixed source; one existing Rasterio warning; no live claims |
| `python tools/check_feature_coverage.py <external>/pr98-dependent-full-coverage.json` with the exact workflow's base/new-module arguments and additional `--new-module src/klab_client/evidence.py` | Passed, exit0 | Existing100% new-module line/branch and changed-code gates |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`; `git diff --check` | Passed, exit0 each | Fatal lint and whitespace |
| External TEST fixture preparation, first two state directories | Blocked at readiness;restored | No observation test invoked;16 files byte-exact and no contexts/projects afterward |
| External `python pr98-two-user-live-20261031.py prepare`, then `test` in the `verified-owner` state | Setup passed; functional exit1:1 passed,1 failed in1.52s | Real unchanged Java and shipped assertions; ordinary signed Runtime users; no response mocking |
| Post-test restoration verification | Passed, exit0 | All owned releases confirmed;16 files byte-exact;original component/plugin inventories;zero contexts/projects;five PID/listener matches;no visible service consoles;test issuer stopped |

## Scientific and lifecycle evidence

New authorization is limited to four observation Jobs, one owned session with two
contexts,20 cells per context, sequential, no automatic recomputation. Signed TEST
users have distinct identities; the nonowner receives no owner/admin key. Cleanup
must affect only test-created scopes, preserve original component/plugin inventories
and restore the October30 distribution headlessly. Restoration evidence is required
even when setup fails. No earlier F2 live result is reused as this candidate's pass.

The initial region submission used one Job and failed before numerical output.
After explicit renewed authorization, four fresh Jobs produced both finite results;
five Jobs overall are consumed. No automatic recomputation was performed or is
authorized. Intermediate readiness failures also exposed missing public authority
metadata under isolated discovery. Four unchanged public provider archives were
downloaded from Maven without production credentials; their MD5 hashes matched the
deployed provider descriptors and SHA256 pins were retained externally. They were
temporarily imported through Java and removed during verified restoration.

The successful preparation used supported local signed-owner bootstrap for the
three semantic services and certificate-backed ordinary-user JWT validation on
Runtime. Owner fixture preparation used local keys only for semantic peers; all
scientist Runtime requests and both shipped test clients had no owner/admin key.
The bootstrap timestamp mismatch in an earlier attempt was detected in service
logs, corrected, and explicitly guarded against before the renewed attempt.

See [sanitized actual results](../../results/execution-evidence-two-user-20261031.json).
Raw JUnit, logs, private test credentials and byte-exact backups remain outside Git.

The scientific claim is finite synthetic elevation in m with recorded model and
contextualizer association, not measured-elevation accuracy. Independent accuracy,
full scientific matrix and throughput are outside this bounded F3 review.

## Contributor coordination

Current open related PRs are98,100,102 and the older federation draft90. F3 adds no
new server routes, shared Java API or NiFi processor. No reviewer is assigned by
inference. Remote exact-head checks remain required after batched publication; no
approving self-review or check bypass is permitted.

## Smallest next actions

1. Establish both users' ordinary Java service advertisements and compatible peer
   authentication before numerical work. Do not change Java to mask this setup gap.
2. Obtain an authorized retained fixture or explicit fresh computation budget, run
   the shipped suite, and verify cleanup/restoration again. The previous handles
   were released and must not be presented as still-live evidence.
3. Publish fixes/evidence as one reviewed batch and verify exact-head CI; leave98
   draft/unmerged until the failed live gate passes.100 remains dependent;102 is
   excluded from this pass.

## Suggested PR description

Bounded read-only recorded execution evidence over existing Java APIs, with honest
partial and plan labels. Fixes credential-bearing chart IDs and oversized coverage
validation;1,428 offline tests plus coverage/lint pass. Actual owner/private Java
acceptance passes, but the nonowner/shared query returns500 and private denial is
not reached. Setup/service-binding evidence and a passing rerun remain required.
