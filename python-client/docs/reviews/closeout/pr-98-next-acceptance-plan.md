# PR98 next acceptance gates — 2026-11-01

## Latest authorization override

The user now explicitly approves starting live tests until PR98, PR100 and PR102
are finished. This supersedes the per-attempt renewal requirements in the retained
history below, not review/acceptance requirements. Continue bounded diagnosed
attempts without repeated approval questions; never resend ambiguous mutations,
preserve failed state, and do not expand into unrelated research. Sleep is queued
only after all PR work, final webpage verification and cleanup finish.

This is a supporting closeout plan, not a replacement roadmap or live execution
authorization. PR90 is merged; PR98 remains draft at
`e5c4bb6fd94d71394c1db12c8a3eaa60e1ae13bf`. Its existing target and published
history must remain unchanged unless separately authorized.

## Established facts

- Independent review's three raw-privacy/test-boundary findings are corrected at
  this head. Final 1,467 tests, coverage/lint and wheel/sdist builds pass; exact
  source/package hashes were independently verified before guarded publication.
  This does not resolve the historical live failure.

- PR98's Python transport already sends `klab-service`; PR90's earlier private
  harness header defect is not demonstrated in this SDK.
- The shipped suite needs two existing, still-live computed observations in
  distinct shared/private contexts. Historical fixtures were released. Stopped
  databases or saved handles alone are not a usable acceptance fixture.
- Historical owner/private evidence passed; shared observation retrieval returned
  HTTP500. Per-user service bindings were incomplete. Fixing that prerequisite
  has not yet been shown to fix the query failure.
- The subsequent fixture attempt stopped with Resources semantic search unavailable.
  Retained Resources logs show an inoperative-Reasoner warning and authorization
  failures. Neither fact identifies the discarded shared-query exception.
- Accepted Java source sets semantic-search availability only after an operational
  Reasoner is found and namespace indexing succeeds
  (`ResourcesProvider.checkSemanticServices`/`indexKnowledge`). A running HTTP
  listener or `operational=true` alone does not establish that readiness.

## Gate 1: offline fixture preparation and review

Use the accepted PR90 Java artifacts, recording candidate `a0a013a…` and merge
tree equivalence, separately from the exact PR98 Python head. Do not silently
merge Java history into PR98, retarget it, or relabel old deployment evidence.

Prepare new isolated ports, homes, configurations and private graph data. Reuse
the reviewed configuration, protocol, spatial-plugin, artifact-integrity and
owned-process safeguards; do not execute the historical fixture runner, which
changes existing services and home-level run files. Preserve all failed states.

Inspect/hash the required worldview, namespace/model, geospatial component and
authority artifacts before copying or importing them. Record exactly which are
trusted dependencies versus fixture assets; do not edit ontology/scientific
algorithms, invent semantic mappings or weaken readiness assertions.

The offline inventory now verifies two clean pinned project repositories, both
generator/geospatial component archives and all four retained public authority
archives. It records tracked project-file hashes, commits and trees in private
`pr98-readiness-inventory-20261101.json`. Nothing was copied into a live fixture,
imported or started. Matching historical hashes proves input identity, **not**
compatibility with the accepted Java build; isolated readiness must establish that.

## Gate 2: separately authorized readiness-only fixture

The new private `pr98_isolated_readiness.py` harness is implemented and unrun.
Thirty-one offline safety tests pass and independent re-review clears the harness
for an authorization request only. It proposes
fifteen minutes total, two ordinary test users plus the internal test-service
principal, at most eight administrative component-import Jobs (six Resources,
two Runtime), and zero contexts/observation Jobs. Imports and advertisements are
explicit mutations, not read-only acceptance. All inputs/configurations/projects
and database state belong to new private fixture directories. A default-deny gate
precedes state creation. Ordinary request guards disallow service-key authority,
context creation, submission and nonfixture URLs; unknown imports are journaled
before sending and never resent. No execution is authorized by this document.

Readiness review found and corrected old-root probe incompatibility, optimized-
Python assertion loss, insufficient authority pinning, retry-on-startup-error
behavior, broad status capture, lost SDK failure statuses, cooperative-only
deadlines, and cancellation cleanup gaps. A separate compiled probe verifies the
exact new output contract offline. Authorities and returned component identities
are independently pinned. Startup permits polling only prelistener connection
refusal or valid not-ready metadata, not HTTP500/malformed replies. Event evidence
contains approved fields; raw private JVM logs are not claimed sanitized.

A parent supervises the worker's fifteen-minute execution window, then stops it
before fallible process discovery. Cleanup verifies recorded JVM PID birth and
private argument-file binding, including transient preflights. Discovery failures
do not skip ledger cleanup; interrupts remain failures and are preserved. Cleanup
and post-run listener/process/console audit have separate bounded waits. This is
not JVM/plugin egress confinement. Twenty-one current/frozen identities match
`pr98-readiness-reviewed-inputs-4-20261101/manifest.json`; the execution state
directory remains uncreated. Explicit user approval is still required.

### Subsequent explicit readiness authorization

The user approved exactly one readiness-only attempt under the bounds above.
It ran in new `pr98-isolated-readiness-1-20261101` state, supervised by
the reviewed parent watchdog. Before launch, all six original listener/PID
mappings matched and fixture ports were clear.
This approval supplies no contexts, observation Jobs, numerical work, automatic
retry or merge authorization. The preceding uncreated-state statement records
the reviewed preauthorization condition, not a post-launch status.

### Readiness attempt 1 — FAIL before component imports

The worker stopped with `ConnectTimeout` while probing GraphDB during startup.
Local logs end at Tomcat starting; they contain no demonstrated backend exception
before the owned process was stopped. The process exit code after termination is
not proof of a spontaneous GraphDB failure. No component imports or principal
initialization ran; the import journal is empty, with zero contexts and observation
Jobs. This does not establish compatibility or explain the historical shared500.

All seven recorded JVMs (six preflights plus GraphDB) are absent. No matching helper
JVMs or fixture listeners remain; the six original listener/PID mappings match the
prelaunch snapshot. Launches used `CREATE_NO_WINDOW`; no service console was
launched. Worker exit is1, parent supervision errors are empty, and the execution
watchdog did not time out. Failed-state and supervisor evidence hashes are retained.
Independent result review verified the failed outcome and retained cleanup evidence.
No automatic retry or new approval exists.

### Readiness-2 preparation — reviewed offline, not authorized

The corrected startup gate polls only the private log until the exact Spring
launcher-completion marker appears, checking process exit and fatal errors first.
It then verifies listener ownership before one initial HTTP request. Every HTTP
timeout, connection error, non-200 response and malformed status still stops the
attempt. Neither the marker nor fixing this timing gap proves future health.

New state-2 remains uncreated. Its exact spatial output contract was recompiled;
thirty-five offline safety tests and fatal lint pass. Twenty-one input identities
match snapshot-5. Independent review found no blocker to requesting one new
readiness-only batch under the same bounds; this is not execution authorization.
State-1, its compiled probe and failed evidence remain preserved separately.

The user subsequently explicitly approved one readiness-2 attempt under the same
bounds. It subsequently ran in fresh state-2 and failed as recorded below; original listener mappings were reverified
before launch. Results/import journals/cleanup and independent result review remain
pending. The current broader conditional merge order is98→100→102, with computer
sleep requested only after all work and final webpage verification are finished.
Neither instruction expands this readiness attempt into contexts or computation.

Fixture startup/import/indexing and principal service initialization are mutations,
even if no numerical observations are submitted. Obtain explicit authorization
after the harness is implemented and reviewed. Zero observation Jobs, contexts
and numerical work at this stage. No existing-service changes.

Before computation, prove identity-compatible service authentication and each
ordinary user's own advertisements, observable resolution and model retrieval.
Require Resources semantic-search readiness as well as operational peers. Capture
bounded sanitized failure metadata from original responses; do not replay failures.
Stop at the first unexpected failure. Cleanup must cover owned helper processes,
listeners and console windows without touching unrelated user services/terminals.

## Latest dependency/fixture diagnosis and readiness-3

### Superseding current result: readiness-5 passed; acceptance-9 running

Acceptance-8 computed four Jobs but its first evidence test failed on an incorrect
fixture contextualizer string. The pinned model source calls
`klab.generators.geospatial.terrain()`, matching the recorded chart label, not the
harness's invented `terrain.elevation()`. Corrected only the fixture expectation;
unchanged production/test assertions still require the specified call in evidence.
All known contexts/session were released;13 JVMs absent, helpers0 and original
listeners unchanged. Shared/private acceptance was not completed.

Acceptance-9 is running with unchanged budgets after55 offline safety tests. A
new source-contract preflight verifies the exact pinned model declaration before
service startup, rather than deriving expected output from a live chart.

Acceptance-7 completed four Jobs and verified both20-cell results. Its shipped
suite launch failed during unrelated anyio/asyncio plugin import while subprocess
Popen was tracked as a function. No evidence assertion ran. Both contexts/session
were released; all13 JVMs were rechecked absent, no helpers and original listeners
unchanged. This is failed acceptance with successful fixture computation, not a pass.

Acceptance-8 ran after54 offline tests and an actual collection-only preflight
under the same patched-Popen condition. It explicitly selects live tests, disables
unrelated plugin autoload and requires both exact shipped tests collected, two
passing call reports and no skips. Budgets remain unchanged;35 inputs are frozen.

Acceptance-6 stopped after one confirmed region result: the harness supplied options
inside a typed request and separately to Context.observe. The SDK correctly rejected
the elevation request before dispatch. All13 JVMs are absent and original listeners
unchanged; object release was not confirmed. This remains FAIL, not scientific
acceptance or an ambiguous elevation submission. The fixture is preserved.

Acceptance-7 ran after correction and52 passing offline safety tests. The
new regression executes both phases through actual SDK Context.observe and wire
codecs, rather than relying only on fake client methods. Budgets are unchanged;
future local failures release known scopes only when every submitted Job result
is confirmed and no mutation intent remains UNKNOWN. Unknown writes/results
still prohibit parent disposal and resend.

Readiness-5 passed with semantic search ready, both ordinary users verified,
six imports confirmed, zero contexts/observation Jobs. All13 recorded JVMs were
rechecked absent, no matching helpers remained, and original listeners unchanged.
This is fixture readiness, not scientific or PR merge acceptance.

Acceptance-6 used new isolated state: maximum one session/two contexts,
four sequential observation Jobs and20 cells per result, followed by the shipped
two-user shared/private execution-evidence suite. Fifty offline safety tests pass,
including exact budgets, pre-dispatch unknown-write journaling, full mocked flow,
no retry/parent disposal after an unknown result, and confirmed-fixture cleanup
after a read-only test failure. Thirty-three frozen tooling/input identities and819
dependency hashes are rechecked before launch. No numerical expansion or automatic
recomputation is enabled. The ordinary-client hook explicitly denies server keys.

Readiness-3 failed after confirming TAXA import but not UN import; its UN Job2
remains recorded as JOB_RETURNED, not replayed or retrospectively successful.
Resources separately reported a geography semantic inconsistency. All12 recorded
JVMs were absent after cleanup and original listener mappings were unchanged.

The corrected offline preflight mirrors production HashMap/interleaved dependency
ordering, notification propagation, source preservation and semantic visitors,
using actual FileProjectStorage enumeration without watchers. Unchanged published
worldview `d6c37c0b82afadd004d94bc5311983b66677532c` passes all26 ontologies,
two strategies and all12 staging namespaces. Its adapted authority requirements
are only TAXA and CHEM. Therefore UN/LAND are not imported in this fixture;
their compatibility limitations remain unresolved, not hidden by ontology edits.
Static PF4J checks find no component-owned linkage issues on Resources/Runtime;
bundled optional-library issues309/311 remain retained, not comprehensive passes.

Readiness-4 confirmed all six required component imports, completed one planned
owned Resources index restart, and reached ordinary semantic/model retrievals
with HTTP200. It then failed because the harness incorrectly used ObservableImpl
`.urn`; this SDK stores the transported observable identity in `.raw['urn']`.
It is FAIL, not a backend/scientific acceptance pass. All13 recorded JVMs were
rechecked absent; original six listener mappings remained unchanged.

Readiness-5 subsequently ran and passed after a regression using actual shipped DTO types and all42
offline safety tests passed. It retains fresh state, the same six-import/one-owned-
restart/zero-context/zero-observation bounds, and sanitized stack-frame diagnostics
without exception messages or response bodies. Earlier fixture states remain sealed.

Readiness-2 passed GraphDB health and the spatial procedure gate, then Resources
failed during ontology adaptation with `MatchException`. No imports, principal
initializations, contexts or observations ran; all nine recorded JVMs are absent,
fixture ports/helpers clear, and original listeners unchanged.

Offline probing reproduced a `RELATIONSHIP` mismatch with the actually loaded
October29 language bytes. Regenerating classpaths through normal Maven from the
accepted October16-pinned POMs changed those bytes. Cached sibling application JARs
were also stale, so thirteen exact accepted POM/JAR artifacts were installed through
normal Maven tooling, without hand-editing cache metadata or dependency overrides.
The new closure records seven ordered classpaths and819 hashed dependency files;
all twelve accepted reactor JARs and five timestamped language pins match.

The original October26 worldview still has three files using newer grammar. A
separate clean worktree at the unchanged published October16 worldview commit
`dc473ba4d01b504eb330fbcac400608f9fa4004d` parses all26 ontologies and the target
model without syntax errors. No ontology or scientific algorithm was edited.
This is fixture-version selection and syntax evidence, not semantic/indexing or
numerical acceptance. The monetary-value null-mapping limitation remains distinct
and uncorrected; no broader semantic coverage is inferred.

Readiness-3 ran and failed as recorded above under the user's ongoing live-test authority after independent
review verified24 tooling/evidence hashes,819 artifact hashes, the new exact spatial
output contract, clean source/project refs and36 offline safety tests. Bounds remain
fifteen-minute worker execution plus separate cleanup, at most eight administrative
imports, two ordinary users plus internal principal, zero contexts/observation Jobs,
no existing-service changes and no ambiguous-write replay. Earlier states are sealed.

## Gate 3: bounded numerical fixture and shipped tests

Prefer authorized existing retained results if available. Otherwise agree a new
bounded observation-Job budget, cell count, time limit, context/session ownership,
cleanup policy and stop/no-retry rules before any computation. Old allowances are
consumed; PR90 metadata approval does not authorize this gate.

Then run the unchanged shipped `execution-evidence` suite: owner/private positive,
valid nonowner/shared positive, private denial, owner recheck, finite 5x4 grids,
and actual recorded model/contextualizer evidence. A 500, timeout, mock, missing
fixture or invalid credential is not successful denial or scientific acceptance.

## Gate 4: closeout

Independently review exact Python/Java candidates, real results, artifact hashes,
release journals and owned-process cleanup. Recheck live refs, CI and repository
rules before conditional merge. Only then proceed to PR100 with separately agreed
limits. The user's latest instruction now includes conditional review/test/merge
of PR102 after PR98 and PR100, superseding the earlier prohibition. Update the
requested Technical webpage after outcomes
of the sequence are settled.
