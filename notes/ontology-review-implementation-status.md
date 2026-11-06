# Ontology review pilot: bootstrap checkpoint

Historical bootstrap checkpoint at `7de6c188c`. The newer integrated round/UI result
and verification are in `ontology-review-combined-status.md` alongside this note.

6 November 2026. Authoritative working copy: `task-7/services-pilot`, branch
`ontology-review-pilot`, descended from published Services develop
`212548f761ada2b5eecaac013191be0d5ce4152d`. This is a real-history clone made
through a local Git bundle; the earlier synthetic archive copy is superseded.

## Delivered behavior

- New `ontology-review-pilot@0.1` workflow opens an editorial preparation stage without
  mandatory initial uploads. Editors must have workflow permission for this id (or `*`).
  The earlier `ontology-expert-review@1.1` definition and existing flows are unchanged.
- Explicit **Prepare review draft** phase action runs the bundled
  `klab.workflow.ontology_review` k.Actors behavior. Inputs are proposal/revision ids,
  domain, purpose, requested tier and target community. These editorial choices are
  not inferred from informative annotations.
- The action uses the current participant's permission-checked ontology/project handles
  to read the target and optional project-root `BOOTSTRAP.md`. It never follows an
  annotation's review-record path or URL. Invalid UTF-8 and over-limit material fail.
- Preparation retains direct ontology-level and nested declaration `@review` parameters,
  source locations and source hashes. It generates a schema-valid draft seed, comments
  if missing, candidate source, input manifest, and the original BOOTSTRAP document if
  present. Existing comments are preserved. Existing proposals/candidates block reruns;
  no editor attachment is silently replaced.
- All generated attachments, generator metadata and behavior checkpoint use the existing
  aggregate transaction. Revision/ownership checks precede execution; blob or aggregate
  failure removes this operation's payloads and leaves the saved stage unchanged.
- Resources registers authenticated page `ui/review`. A stage can be entered at
  `<Resources-base>/ui/review?flow=<flow-id>&stage=<stage-id>`. The existing shell protects
  the component; its login preserves the complete current URL. Existing REST methods
  enforce server-side workflow/stage rights. This checkpoint does not add public access.
- The stage page lists authorized flows/stages, renders action inputs, and offers a
  face-to-face draft/context view. It selects proposal/context attachments, displays
  attribution, and lets editors save a new draft attachment while retaining originals.
  It prevents in-page navigation/reruns from discarding unsaved edits. This is a JSON
  proposal editor scaffold; natural proposal/comment forms and contributor filters
  remain part of round/contribution UI integration.

Draft seeds contain authored evidence, not invented ontology assets or change actions.
Validation/acceptance is not inferred from annotations. Schema boolean validation claims
remain false with explicit not-recorded review status. Tier-1 context is conservatively
missing until reviewed by the editor; import-context articulation and scientific review
remain required before any eventual acceptance.

## Dependency recovery

The stale user-cache language snapshots caused the initial Core Services compile failure.
The five JARs downloaded during the already completed language publication were checked
against its recorded SHA-256 hashes and copied with their published POMs/metadata into
`task-7/maven-cache`. Versions are timestamp `20261106.143015`, builds observable 86,
worldview 73, observation 75, kim 71 and kactors 70, from language commit `582d4b5712`.
Full inputs/URLs/hashes: `task-7/published-language-inputs.json`.

Maven uses that writable cache with `C:/Users/Ferd/.m2/repository` as a read-only tail.
No credentials, global settings, original checkout, user project or user Maven cache
were changed. UI dependencies were copied into the isolated clone for local checks.

## Verification

- Normal Resources reactor: **BUILD SUCCESS**, 72 Resources workflow/proposal tests
  plus 2 common proposal tests, all passing with no skips. Includes 4 pure bootstrap
  tests and 4 integration tests using the real bundled k.Actors parser/compiler.
- Integration cases cover exact action inputs/revision, schema-valid output and manifest,
  ownership and stale-request denial, actual project-read revocation, preserved comments,
  duplicate/rerun refusal, aggregate-write failure and partial blob-write rollback.
- Normal Resources server reactor: **BUILD SUCCESS** for server compilation and the
  review-page configuration plus existing web UI route/configuration tests.
- Two Playwright tests exercise stage-link selection, editor inputs/revision, draft
  editing without losing the original attachment, read-only access and visible conflicts.
  These use a disposable mocked-API browser fixture, not a live authenticated deployment.
- Vue typecheck and standalone/embedded production UI builds pass. Existing bundle-size
  and Quasar import warnings are unchanged and do not fail the build.

Logs are outside the repository in task-7: `pilot-reactor-action-final.log`,
`pilot-server-review.log`, `review-ui-tests.log`, `review-ui-typecheck.log` and
`review-ui-build.log`. Initial failed diagnostic logs are retained separately.

The workflow command is `mvn -o -pl klab.services.resources -am test` with
`-Dtest=OntologyReviewBootstrapTest,OntologyReviewBootstrapActionTest,WorkflowSchemaTest,WorkflowManagerAuthorizationTest,WorkflowBehaviorBridgeTest,WorkflowAttachmentSourcesTest,ProposalReviewProtocolTest,ProposalSchemaValidatorTest,ProposalDocumentTest`.
The server command uses `-pl klab.services.resources.server -am` and
`-Dtest=ResourcesReviewConfigurationTest,KlabWebUiControllerTest,WebUiConfigurationTest`.
Both use `-Dsurefire.failIfNoSpecifiedTests=false`, task-local Maven head/test home,
and an explicit Byte Buddy javaagent. On this Windows sandbox the normal JVM pipe and
loopback facilities needed by these tests required an escalated test process; no
running user service was involved. Browser reproduction is `npm run test:review`,
with `KLAB_TEST_BROWSER_CHANNEL=msedge` in this environment only.

## Integration boundaries and next work

The separate round/contribution work should bind its candidate revisions and contribution
records to the same immutable attachments, add stage-authorized feedback and the real
contributor/proposal filters, then expose round actions in this page. The pilot schema
currently has editing only; it does not claim repeatable reviewer/community rounds yet.
Do not use synthesized REVIEWER or PUBLIC admission as the new authorization contract.

Durable notices/email and automatic project-material Git history export remain planned.
Project actor material read/write scaffolding is confirmed; those project writes have
external effects and are not rolled back by the workflow attachment transaction. Use
the planned idempotent post-persistence export/commit receipts, not project writes inside
the bootstrap action. No email, push, deployment, restart or external account creation
has been performed. See the companion implementation plan for the full pilot gates.
