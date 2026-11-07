# Staged ontology review verification

7 November 2026. Services base: `120f60e2528bf94c165403a97f9467cdb95c402c`.
Local Services branch: `feature/ontology-review-pilot`. Companion IDE branch:
`feature/ontology-review-stages`, head `38db3e1e4014cae505851986faaf213a62615102`,
including creation/routing commit `5cc36d6dde93e6297b3a05883311bf7af47ae4cb`.
Both were developed in isolated worktrees. Original develop checkouts remain unchanged.
No push, merge, deployment, service restart, real mail or live-account test was performed.

## Delivered contract

Pilot 0.3 has creator-exclusive bootstrap, separate peer/community stage instances, an
editor coalescing stage and explicit termination. Round/stage state changes share the same
conditional aggregate write. Review history and submitted contributions remain immutable;
new review passes create new records. Coalescing can lead to another saved-candidate review
or termination without acceptance/publication. Private bootstrap is also omitted from the
shared asset catalog until handoff. The terminal catalog flow status is CLOSED; its existing
REVIEWING summary is historical, not an acceptance result.

Web UI has stage-specific routes/views, server-derived capabilities, natural creation,
current-account recovery for uncertain creation, editable coalescing seed/save/return controls,
read-only history and explicit conflict recovery. The IDE persists behavior-backed bootstrap
without needing a transition, routes review stages to the Resources UI, and suppresses generic
mutation controls and the misleading static schema diagram for this staged protocol.
The candidate/proposal editor remains JSON-oriented; this is not a rich semantic merge UI.

## Final checks

- Services applicable eight-module reactor: **287 tests passed**, zero failures, errors or skips.
  This is the selected review/workflow/serialization/authentication-boundary regression suite,
  not every test in the whole repository. Final log: `../stages-catalog-final-reactor.log`.
- Web: **21 Playwright tests passed**, Vue typecheck passed, standalone and embedded Vite
  production builds passed. Existing Quasar/bundle-size warnings remain.
- IDE final commit: **11 focused tests passed**. Full suite: **364 tests, 363 passed, one error,
  none skipped**. The sole SemanticComposer overlay-size assertion (451.2 expected, 454.4
  actual) also fails on untouched baseline `07cfcbf6`; no new full-suite failure was observed.
  Logs are in the IDE worktree `target/full-final-38db3e1.log`, with baseline evidence in the
  isolated baseline worktree. The full suite is not reported as green.
- `git diff --check` passed. Source worktrees were checked for unintended changes.

The native scenario runs four synthetic authenticated scopes against the real review component,
WorkflowManager and Nitrite/RocksDB on loopback. It verifies bootstrap behavior, optional
peer/community overlap, private drafts, immutable submissions, conflicts, integrated revision,
two closed cycles, native store/manager restart, termination, historical read-only views and
9 replayed notice events with 15 fake acceptances. Duplicate replay adds/sends nothing.
Its evidence is `../pilot-e2e-browser.log` and `../pilot-e2e-scenario.json`.

The first compile and first combined logs retain corrected iteration failures. Do not treat them
as final evidence. The final source includes the catalog privacy guard and its regression.

## Reproduce

Use JDK 21, an isolated writable Maven cache/test home, the prior verified published Languages
artifacts, and existing UI dependencies. The user Maven cache was only a read-only tail.
From the isolated Services root:

```powershell
mvn -o -pl klab.services.resources.server -am test `
  '-Dmaven.repo.local=<isolated-cache>' `
  '-Dmaven.repo.local.tail=<verified-cache>,<read-only-user-cache>' `
  '-Dtest=*SerializationTest,WorkflowRequestBindingTest,ReflectionMetadataTransportTest,LookupTableTransportTest,*Review*,Workflow*,*Bootstrap*,ProjectMaterialIOTest,WebUiConfigurationTest,ProposalSchemaValidatorTest,IsolatedProposalCandidateValidatorTest,ProjectMaterialAuthorizationTest,HubWebAuthenticationTest,TokenAuthorizationFilterTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-DargLine=-Duser.home=<isolated-home> -javaagent:<byte-buddy-agent-1.17.5.jar>'
```

From `klab.core.services/src/main/webui`, set `KLAB_TEST_BROWSER_CHANNEL=msedge`, run
`vue-tsc --noEmit`, Playwright with `playwright.review.config.ts`, then Vite's normal and
`vite.embedded.config.ts` builds. The native browser scenario is included by the `*Review*`
selector and uses a separate loopback fixture.

Real IdP/JWT login, colleague roles, deployed-session revocation and live service operation
remain **NOT RUN**. Follow the [real-account worksheet](ontology-review-colleague-checks.md).
Synthetic scopes, controller objects and browser fixtures do not certify those gates.
Publication/acceptance and real email delivery remain separate actions and are not enabled here.
