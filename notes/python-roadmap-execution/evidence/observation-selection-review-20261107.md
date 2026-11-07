# Persisted selection development review

Baseline: PR289 merge `48ef20741`. This is a local implementation review,
not maintainer approval, native execution, final PR acceptance or whole-row completion.

- Scope: existing Java headers and query routes, pinned source receipt; no native
  endpoint invented. SDK admits only persisted positive distinct IDs, checks bound
  origins/root/known Runtime and preserves server authority over bare integer IDs.
- Selection: immutable identity-qualified pair, ambient focus preserved, non-null
  within clears pair as inspected Java source does. Submission clone, observer
  option clone and sync/nativeasync service transport retain it. Job binding detects
  pair reassignment; current credentials, not saved credentials, are used.
- Persistence: ordinary v1 fields/serialization unchanged; v2 requires two explicit
  distinct persisted endpoint IDs. Both are authenticated by lookup before restored
  selection/Job use; no resubmission or acquired ownership. Unknown versions refuse.
- Collections: direct-child source-shaped query only, explicit page/byte bounds,
  sentinel, positive unique root-validated bound observations, no automatic crawling
  or claimed complete/revision-leased inventory. Mid-request identity/scope/pair
  changes refuse instead of silently rebinding returned observations.
- Simulator independence: no SDK import in new fixture; exactly two explicit
  synthetic observables. Current-owner context/endpoint lookup, finite metadata
  receipts, direct-child pagination, no scientific numeric storage. Supplemental
  fixture controls do not substitute for the six public HTTP workflows.
- Packaging: simulator remains a separate developer package, no production SDK or
  worker dependency added. Installed artifact/parity acceptance recorded separately.
- History/continuation: all42original row records embedded unchanged, no historical
  native or C-exclusion rewrite. Queue checkpoint requires actual remote merge and
  local ancestry; it does not auto-complete requirements or restart a dead process.
- Open boundaries: deployed native semantic applicability/auth/lifecycle requires
  authorized maintained fixtures. Source ScopeManager logging is not proof of native
  endpoint denial. Read-only native compatibility test remains NOT_RUN. Provisional
  receipt fields are not native scientific commits or a general Reasoner contract.

No source-level blocker found in the inspected bounded implementation. Fresh installed
regressions, exact final-head CI, ordinary PR workflow and queue checkpoint remain
delivery gates. Material-selection-effects is the next independent requirement.
