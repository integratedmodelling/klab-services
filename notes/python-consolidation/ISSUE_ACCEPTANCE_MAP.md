# Issue and acceptance audit — baseline

## Current milestone reconciliation

- #147's retained receipt failure was reproduced, repaired with original rollback
  and strengthened tamper/port/root guards, verified, and merged via #191.
- All nine original PRs merged after scoped contributor review, refreshed wheel
  and hosted gates. This is not closure of the entire Python roadmap.
- The installed client passes genuine scientific Java execution, partial
  provenance, independent-client restoration and owned cleanup. Managed Runtime
  registration/READY/RUNNING/STOPPED also passes; publication UNCONFIRMED and
  remote-agent cleanup UNKNOWN remain explicit. Broader #89/#125/#144 goals
  must not be marked universally complete from these bounded workflows.
- New issue[193](https://github.com/integratedmodelling/klab-services/issues/193)
  preserves the frozen ODO assertion/large-worldview semantic incompatibility.
  No original assertion was changed or historical failure hidden.
- Component PR11 requires an upstream writer's merge or granted permission.
  This is the only remaining external milestone blocker after internal cleanup
  and evidence reconciliation, not a request to expand the Python/PaL roadmap.

The tables below preserve baseline states and historical evidence, not a claim
that later accepted source still fails those repaired gates.

This is not a roadmap-completion claim. Live issue bodies/comments were captured
on 2026-11-04 in the private baseline archive; future issues remain future scope.

| Issue | Live state | Requirement retained | Milestone relevance / next action |
|---|---|---|---|
| [89](https://github.com/integratedmodelling/klab-services/issues/89) | OPEN | Exact client/server scientific identity, native/federation ACL and lifecycle acceptance | Mandatory applicable workflow gates; current integration and installed-package acceptance pending |
| [123](https://github.com/integratedmodelling/klab-services/issues/123) | OPEN | Versioned remote scan/block/slice contract | Future roadmap unless selected PR/workflow promises it; no new chunk protocol here |
| [124](https://github.com/integratedmodelling/klab-services/issues/124) | OPEN | Twin composition/migration | Future roadmap; do not claim global federation from bounded identity fixes |
| [125](https://github.com/integratedmodelling/klab-services/issues/125) | OPEN | Genuine managed-agent execution/control/terminal cleanup | Relevant to #134/#135/#138 scope; native AMQP peer is not managed Runtime acceptance |
| [126](https://github.com/integratedmodelling/klab-services/issues/126) | OPEN | Durable event replay/resynchronization | Broader roadmap; #136 must retain explicit partial/stale behavior, not invent replay |
| [127](https://github.com/integratedmodelling/klab-services/issues/127) | OPEN | Provider/worker SDK contracts | Explicitly out of scope |
| [129](https://github.com/integratedmodelling/klab-services/issues/129) | OPEN | Release/support and publication decisions | Record compatibility limits; no package/release publication authorized |
| [130](https://github.com/integratedmodelling/klab-services/issues/130) | OPEN | PaL operational inputs and original-study oracles | Explicitly out of scope; synthetic demonstration does not satisfy it |
| [144](https://github.com/integratedmodelling/klab-services/issues/144) | OPEN | Paired remote currentness and deployed lifecycle | #148 is merged, but deployed/current combined acceptance still requires verification |
| [147](https://github.com/integratedmodelling/klab-services/issues/147) | CLOSED | Runtime query-receipt association, rollback and integrated acceptance | **Closed without full acceptance.** Latest comment explicitly retains one error; reproduce on current candidate, not silently dropped |

Issue #128 is a merged PR rather than an omitted open issue. Full PR/issue states
are separate facts. No issue was reopened or closed by this audit.

## Recovered scientific evidence, not current-candidate results

Raw XML and logs were copied before any rerun; local archive:
`C:/Users/lumsd/AppData/Local/Temp/opencode/klab-consolidation-scientific-evidence-20261104`.

| Report set | XML tests | Failures | Errors | Skips | Interpretation |
|---|---:|---:|---:|---:|---|
| Runtime empty-change-fixed | 1279 | 0 | 1 | 11 | Receipt-association case below; not full dependency-reactor acceptance |
| Runtime-server inventory | 19 | 0 | 0 | 0 | Explicit six-class selection only |
| Resources integrated | 472 | 1 | 0 | 8 | Pinned ODO expected/actual hash mismatch |
| Reasoner final archive | 816 | 0 | 0 | 11 | Raw archive total differs from historical narrative's 794; do not silently reconcile or mix SHAs |

These archives lack a verified immutable candidate identity in their summaries.
Keep `source_sha=UNVERIFIED` until recovered from sufficient evidence. The recount
establishes report contents, not a newly executed pass or final scientific result.

### Failure classification

- `CollectiveSelectionAtomicExecutionTest.actualConsumerFailureReceivesSelectedAliasAndRootRollbackDiscardsClassification`:
  error `Generic multi-contributor query inputs require an issued query receipt association`.
  **SCIENTIFIC/SECURITY_BLOCKER for the selected-input contract**. Whether it blocks
  the bounded constant-model demonstration must be determined, not assumed.
  Reproduce with that class on the combined Runtime dependency reactor. Retain
  original alias and root-rollback assertions. Existing dirty repairs are preserved,
  not treated as tested/merged production fixes.
- `RuntimeFixtureCompatibilitySourceTest.fixtureCoreImportNamesTheActualCapturedVersionWithoutReplacingOwlBytes`:
  expected ODO `496bc6634392d7e2eb2a6749bca6a1570333ea607b9b8e52ffaf5b619bcf2a57`,
  actual `5c020dac9bd9d1cd4f9673b8075cf08f75ecc7194c74378bdb8f0498110ddba3`.
  **PYTHON_INTEGRATION_BLOCKER for the original frozen fixture**. Do not change its
  oracle or reinterpret it as accepted. A separately versioned compatible synthetic
  fixture requires explicit semantic-impact evidence and independent expected values.

## Acceptance map reconciliation

The historical 24-item map corresponds to 22 structured phase statements/scenarios
and two continuous-release paragraphs in the preserved planning guide. Draft #137
contains their detailed mapping in `docs/PYTHON_CLIENT_ACCEPTANCE_MAP.md` at
`ca1a096251eb8499ffa82f53ece8da9fc41c8005`. It remains an unmerged contribution.
Its source/text coverage tests are to be rerun on its exact head and later on the
actual combined candidate. Do not equate implemented, merged, live verified and
scientifically verified statuses. The other broad capability proposals/public API
inventory remain a distinct later-roadmap audit, not silently certified here.
