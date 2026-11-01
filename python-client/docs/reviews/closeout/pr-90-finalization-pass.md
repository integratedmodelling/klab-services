# PR #90 finalization pass — 2026-10-31

## Final disposition — merged 2026-11-01

PR90 merged normally at `d86f9927d32aba983060760fdc9953feaf532631` into
`feature/python-client`. Parents are tested target `1e20fc478…` and accepted head
`a0a013a188fb752907f2d143a61b86f4a9cb0a0b`; merge tree
`d577ea0a3421eb70907ff1b63622619b549dd97a` exactly matches the validated candidate.
History and the source branch were preserved. Fresh remote head/target, successful
Java/Jenkins checks and repository rules were verified; no bypass/admin override,
retargeting, force-push or #102 merge was used.

Final accepted evidence: **321 selected tests/57 suites/13 modules**, zero
failures/errors/skips; all eighteen dashboard-enabled packaging modules;
thirty-three offline fixture/safety checks; fresh Java/Jenkins CI; and exact-head
native batch8 PASS with eight contexts, two creation orders, ten ordinary-role
native clients, two owned Runtime restarts and five credential-boundary controls.
All releases/session cleanup were response-confirmed, ledgers empty, seventeen
tracked PIDs plus helper JVMs stopped, fixture ports clear and original listener
mappings unchanged. Independent result-aware review gives bounded ACCEPT.

Additional published corrections after `d687856…`: `7027fe1…` fails closed on
invalid Runtime configuration; `1f541836…` validates lifecycle/absent-broker
handling and borrowed transports; `a0a013a…` enforces issuer/audience/claims and
invalid-token filter behavior. See [attempt history](pr-90-native-attempts.md).

Acceptance is metadata identity/ACL/lifecycle and the reviewed credential contract,
not scientific execution, broker transport, separate deployment certification or
simultaneous identical-ID multi-origin support. Peer caches stayed warm across
Runtime-only restarts. Cleanup confirmation is not a graph-absence audit. Earlier
failures and sealed UNKNOWN states remain unchanged. Earlier 259-test/fresh-cache
results below are historical, not relabeled as current-head reproducibility.

#98 is next, then #100, each still requiring its own review/acceptance. Numerical
execution limits must be separately agreed; #102 remains draft/unmerged and excluded.
The remaining sections preserve the development-pass history.

## Authorization and order

The user now requests review/fixes and conditional merges in this order:
**#90, then #98, then #100**. Each merge is conditional on passing review and
working acceptance. #102 remains explicitly excluded from merge authorization.
No failed or unrun criterion is treated as passed. The user subsequently
explicitly approved the [bounded isolated #90 native batch](pr-90-native-acceptance-plan.md).
That approval does not supply an unbounded computation/restart/import budget or
renew historical #98/#100 numerical allowances.

## Additional local corrections

The previous [fix pass](pr-90-fix-pass.md) remains historical evidence for the
origin correction and its ten passing regressions. Two additional files now
address the remaining test/build problems:

1. `WorldviewValidationTest.java`: deliberately invalid forward/deleted-reference
   fixtures no longer abort in the generic positive-fixture parser callback.
   They assert the exact expected `Unknown concept: test:Later` error, retain it
   as a notification, and independently invoke `KimOntologyVisitor`. The
   production validation entry point intentionally skips assets with parser
   errors, so it cannot establish this test's separate semantic-visitor claim.
   The existing declaration-order, lexical-offset, undefined-reference, imported
   namespace, valid backward-reference, and warm-state assertions remain intact.
   Positive fixtures retain fail-on-parser-error behavior. No production semantic
   validation, ontology, or scientific algorithm was changed.
2. `klab.services.resources/pom.xml`: the five language dependencies now name the
   matched dated October 16 artifacts explicitly, rather than resolving a newer
   incompatible observable-language enum through floating snapshots. No generic
   relationship semantics or catch-all switch was invented. The LSP module is
   independent and still has its own dependency graph; full default packaging
   must demonstrate that graph works, rather than assuming resources-only tests
   prove it.

The independent reviewer caught the production error short-circuit in the first
fixture-helper attempt. That first attempt failed at the still-unreached
declaration-order assertion; the corrected test invokes the semantic visitor
directly while preserving the parser diagnostic. The subsequent focused run
passed **12 worldview tests**, plus the ten origin and five federation identity
tests, with no failures/errors/skips. Evidence:
`negative-fixture-correction-focused.log` (failed first attempt) and
`negative-fixture-independent-validator-focused.log` (corrected focused PASS).

## Completed validation and publication

- **PASS: 259 tests, zero failures/errors/skips; all 13 selected reactor modules.**
  Full strict selected Java reactor against an **unmodified copy** of the
  ordinary Maven cache, not the earlier metadata-adjusted cache. It runs in
  `../pr90-final-validation-20261031` with byte-identical copies of all six
  proposed files, preventing concurrent builds from sharing output directories.
  Log: `final-ordinary-cache-strict.log`.
- **PASS: all 18 reactor modules.** The unchanged hosted packaging command
  (`-B -U clean package -DskipTests`),
  with progress output suppressed and a new **initially empty** public-resolution
  Maven repository at `../pr90-maven-fresh-public-20261031`. Log:
  `default-package-fresh-public.log`. This is a packaging/provenance check, not
  executed-test acceptance.
- Independent source re-review of the six-file proposal found no blocking source
  defect, including the negative fixture assertions and dated dependency pins.

The exact six tested files match `final-six-source-sha256.json`. Dated snapshots still
have retention and floating-parent risks; source compatibility is not a claim
of permanently immutable dependency publication. Exact resolved graphs and
artifact hashes accompany the result. An offline classpath-preparation attempt
failed because the fresh repository did not yet contain the dependency plugin;
the explicit online plugin invocation then rebuilt and packaged all 18 modules
successfully at the committed candidate. Logs preserve both outcomes.

Local commits:

- `1a4f2ee172fe1bc1ced255c0fbfed58070538728`: cached-origin correction/regressions.
- `d687856ed46f7b3de3c6d6a6edd55061bb3a99c7`: negative fixture and dependency pins.

The latter was fast-forward pushed to #90's existing
`feature/python-client-federation-integration` branch after rechecking unchanged
remote head/target identities. No force push, retargeting, history rewrite,
ready-for-review promotion, merge or closure occurred. The source branch retains
the current-target test-merge ancestry. Fresh hosted CI and native acceptance are
pending; older green checks are not attributed to this new head.

## Remaining real acceptance

See [native fixture attempts](pr-90-native-attempts.md) for the two stopped
preparations, their demonstrated fixture corrections, and the explicitly renewed
third attempt. Earlier “not yet executed” statements in preparation notes are
historical; no stateful acceptance PASS is currently recorded.

Two ordinary principals in a nonlocal identity-sharing group, separate native
Java client processes, both creation orders, warm/cold restoration, explicit
shared/private controls, caller attribution and release boundaries remain NOT
RUN for this corrected candidate. Existing unrelated services must not be
restarted or repurposed. The explicitly approved isolated fixture is being
prepared within its bounded setup/workload/cleanup plan. No PR has been merged or
closed by this pass.
