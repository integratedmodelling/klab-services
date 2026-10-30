# Contribution review: F1 semantic explorer

## Recommendation

**30 October follow-up:** the [current review](semantic-explorer-final-20261030.md)
records the local malformed-score fix, 1,167 passing offline tests and the new
shipped authority/hierarchy functional suite. The suite has not run on Java;
changes are local and unpublished. The recommendation remains blocked on evidence.

**Needs contract/coordination evidence.** The bounded Python capability is
implemented and locally tested, including the established 100% line/branch gate.
It is suitable for continued local milestone integration, not final acceptance:
real authority discovery/selection/resolution and independent Java hierarchy
checks have not passed at this candidate. This is an internal read-only review
using Eric's guide, not independent reviewer approval or maintainer acceptance.

## Reviewed state

- Repository: `integratedmodelling/klab-services`; worktree
  `klab-python-discover-compare-explain`.
- Final integration target: `feature/python-client`; milestone base
  `1e20fc478f645b5fb5c2fd00ffd223a040ca2878`.
- Feature checkpoint/base: `6bb7a8224` (specification/reconnaissance only).
- Candidate: `f10279b8a4d658c37dbef356ab5213a22cb8833c`; clean at review entry.
- Reviewed all nine feature paths and inherited transport/DTO/credential boundaries.
  No uncommitted code was included. The review phase did not edit or publish code.
  This report is recorded after exiting that phase.
- Guide unchanged at `e7659b03193448c6bd1aacc76f257ba0af0842d2`; applicable
  repository guide and current/historical Python planning references were read.
- Authorized checks: isolated offline/mock HTTP/subprocess tests; normal bounded
  authenticated reads on the existing explicitly trusted hosted profile. No shared
  admin/import/restart/fixture changes or remote scientific objects were created.

## Capability and boundaries

Before, scientists manually assembled authority/hierarchy requests. Now a
`SemanticExplorer` searches an explicitly configured authority, preserves canonical
codes/labels/diagnostics, requires explicit selection, inspects documentation links,
resolves selected identities, navigates direct hierarchy and asks two implemented
semantic predicates. Plain text and escaped notebook summaries use the same views.
No semantic inference, permanent cache, global search, automatic candidate choice,
provider-content fetch or credential propagation to advertised destinations.

## Findings, ordered by consequence

1. **Contract/evidence gap (required acceptance):**
   `semantics.py:125–282`; source-derived fixtures establish routing, DTO bounds,
   statuses and Java predicate argument order, but not live worldview meaning.
   The hosted prerequisite invocation encountered `TransportError` during
   Resources capabilities retrieval before all three test bodies. The unit and
   readiness assertions were not reached. This is **Blocked**, not reproduction
   or repair of the historical missing-unit failures. Establish exact deployed
   binaries and an accessible configured authority, then run the named journey and
   independent asserted-hierarchy relationships.
2. **Demonstrated credential-reporting defect corrected:** default long pytest
   tracebacks displayed a bootstrap credential argument on the failed prerequisite
   run. `tools/run_functional_acceptance.py:36–41` now selects `--tb=short`.
   `tests/integration/test_functional_tracebacks.py` actually launches failing
   pytest with a non-secret canary and checks stdout/stderr/JUnit. The newly
   generated external XML was sanitized; no credential value is committed here.
   This does not claim arbitrary third-party debugging/explicit locals are safe.
3. **Explicit freshness limitation:** `KnowledgeState.freshness`,
   `_state/_unchanged` detect observed service/worldview/scope/revision changes.
   Revision absent/zero gives unknown guarantees; before/after checks are not an
   atomic snapshot. There is no local semantic cache to remain silently valid.
   This is a disclosed server-contract boundary, not a demonstrated remaining defect.

No further demonstrated blocking/important Python defect was found in the scoped
review. That is not exhaustive correctness proof.

## Validation actually performed

`python` means the isolated Windows Python 3.12.7 interpreter under
`%LOCALAPPDATA%/Temp/opencode/discover-compare-explain-312/Scripts/`.
Commands ran from `python-client/` unless indicated.

| Check / command | Revision and environment | Status | What it establishes / limits |
| --- | --- | --- | --- |
| `python -m pytest tests/unit tests/integration -q --cov=klab_client --cov-branch --cov-report=json:<external>/discover-f1-full-coverage.json --junitxml=<external>/discover-f1-offline.xml` | Pre-checkpoint F1 working code, before traceback hardening | Passed, exit 0; 1,153 tests | Full offline regression; one existing Rasterio warning; not live scientific evidence |
| `python tools/check_feature_coverage.py <external>/discover-f1-full-coverage.json --base 1e20fc478f645b5fb5c2fd00ffd223a040ca2878 --new-module src/klab_client/semantics.py --new-module src/klab_client/_inspection.py` | Same working code | Passed, exit 0 | Existing unexcluded new-module and changed-code gates, no reduced threshold |
| `python -m pytest tests/unit/test_semantic_explorer.py tests/unit/test_functional_runners.py tests/integration/test_functional_tracebacks.py -q --cov=klab_client.semantics --cov=klab_client._inspection --cov-branch --cov-report=term-missing --cov-report=json:<external>/discover-f1-review-coverage.json` | Exact executable content of `f10279b8a` | Passed, exit 0; 110 tests; both new modules 100% lines/branches | Includes actual canary subprocess and post-fix semantic contracts |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`; `git diff --check <milestone-base> HEAD` | Exact candidate | Passed, exit 0 each | Existing static gates |
| `python tools/run_functional_acceptance.py --suite connected-semantic-readiness --suite connected-unit-contract --profile examples/hosted-deployment.json --certificate <private-certificate-path> --report <external>/discover-hosted-contracts-20261026.xml` | Pre-checkpoint F1 working code; existing hosted profile, TLS verified | Blocked; process exit 1; three setup errors | Resources connection prerequisite failed; no assertion, discovery or scientific success claimed |
| `python -m pytest tests/unit tests/integration -q --cov=klab_client --cov-branch --cov-report=json:<external>/discover-f1-candidate-coverage.json --junitxml=<external>/discover-f1-candidate.xml` | Exact executable candidate `f10279b8a`; report-only local file not imported | Passed, exit 0; 1,155 tests | Full offline candidate regression, no failures/errors/skips; same existing Rasterio warning |
| Same coverage evaluator and base/new-module arguments on `discover-f1-candidate-coverage.json` | Exact candidate | Passed, exit 0 | Unexcluded 100% line/branch and changed-code gates |
| Installed wheel / Python matrix | Exact candidate | Not run | Later package/matrix gates remain outstanding |
| F1 live authority and independent hierarchy acceptance | Exact candidate / pinned unchanged Java | Blocked | Accessible compatible stack and exact deployed binary identities not established |

## Scientific and lifecycle evidence

No numerical/scientific-output claim is made. Source-inspection confirmed that
`/subsumes` calls `is(first,second)` (first specializes second), despite reversed
Swagger prose. Offline checks retain explicit candidate identity, unknown states,
raw extensions, redaction, frozen views, observed knowledge drift, input/response
budgets, auth failures and unknown freshness. Documentation URLs are returned as
data only; the mock verifies that every request stays at the configured origin.
Remote disposal, cancellation and continuation are N/A to F1.

## Contributor coordination

Python-only work over existing Reasoner operations. No Java, authority plugin,
worldview or component source was changed. Runtime/federation/authority deployment
ownership remains external. No contributors were contacted or assigned; current
remote PR overlap beyond the fetched milestone target has not been reverified.

## Smallest next actions

1. Finish installed-package and supported-interpreter matrix gates.
2. Establish a compatible unchanged Java stack and configured authority; exercise
   real search → explicit code selection → documentation → resolution and the
   independently specified direct parent/child and predicate relationships.
3. Keep F1 live acceptance blocked until those checks pass; continue independent
   Python features without claiming the combined milestone is complete.

## Suggested PR description

Add bounded semantic exploration using current authenticated Java routes. Scientists
can explicitly select canonical authority identities, inspect safe documentation
descriptors and navigate/assert semantic relationships without raw HTTP assembly.
Immutable redacted views carry observed knowledge identity and explicit freshness
limits. Offline checks and the original coverage gates pass; live authority,
scientific matrix and deployed-binary acceptance remain blocked. Includes a tested
functional-runner safeguard against credential arguments in pytest tracebacks.
