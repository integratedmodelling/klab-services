# Ontology review editor journey verification

7 November 2026. Services changes are based on `25648342296c3fb004133d99a210e037ae903533`
on `feature/ontology-review-pilot`. Companion IDE changes are based on
`38db3e1e4014cae505851986faaf213a62615102` on `feature/ontology-review-stages`.
The original develop checkouts were left clean and unchanged. This revision was prepared
locally; no new push, merge, deployment, production-service restart, real email or live-account
mutation was performed.

## Confirmed causes and repairs

- Creation discovery exposed every stored definition, while the IDE omitted version labels.
  Discovery now returns only the latest definition, uses safe numeric/prerelease ordering, and
  rejects explicitly retired versions for new flows. Historical flows retain their own schemas.
  The pilot definition is now `0.4`, so an installed `0.3` definition is not silently reused.
- The native review shell deliberately hid attachments, saved-stage actions and the diagram.
  Native preparation fields, files, action controls and a behavior-aware structure/history view
  are restored. Review-cycle operations remain in the dedicated Resources review UI.
- Web creation contained only an ontology URN and remained visible after success. Both clients
  now submit details and initial files atomically. The web moves to the saved flow and requires
  an explicit Start another action. Known validation rejection and uncertain outcomes are kept
  distinct; interrupted requests are not automatically repeated.
- Empty bootstrap had neither an upload path nor an editable candidate. The web now supports
  preparation fields, proposal/reference files, title/description, editorial notes and candidate
  editing, with explicit next steps. It shows the workflow version and discovery/context errors.
  The real production behavior parser passes its regression. A specific resolver failure in the
  user's deployed session was not established; the previous client suppressed those errors.

Revision-bound detail edits avoid round-tripping protected protocol metadata. Upload guards,
size/count limits and atomic rollback preserve state on failures. Cross-tab details and native
upload/reload races retain conflicts rather than silently overwriting another edit. Selecting
private reference material for preview cannot replace the exact frozen ontology source copied
into a new review candidate. Creator exclusivity, immutable review history, overlapping rounds,
coalescing and termination without publication remain enforced.

## Services and web evidence

- Final applicable eight-module Maven reactor: **296 tests passed**, no failures, errors or skips;
  **BUILD SUCCESS**, including local packaging/install. This is the selected workflow/review,
  serialization, transport and authentication-boundary aggregate, not every repository test.
  Final log: `../editor-journey-final-reactor.log`.
- Web: **32 focused Edge/Playwright tests passed**. Final Vue typecheck and standalone/embedded
  production builds passed. Log: `../editor-journey-final-web-build.log`. Existing dependency and
  bundle warnings remain.
- Typed initialization transport tests verify known 400/422 rejection, uncertain 500/transport/
  decoding failures, no automatic resubmission and successful responses above the diagnostic
  response-size cap.
- The native browser scenario now begins with **no flow and no candidate**. It fills the actual
  editor form, uploads reference/proposal files, creates exactly once, cancels explicit second
  creation, saves details/notes, prepares the candidate, reviews with four synthetic principals,
  coalesces, returns for another review, restarts Nitrite/RocksDB and terminates. It verifies exact
  saved details and uploaded bytes, private draft isolation and immutable historical cycles.
  Receipt: `../editor-journey-e2e-scenario.json`; log: `../editor-journey-e2e-browser.log`.
- Screenshots of the actual local component are saved beside the checkout as
  `editor-journey-filled-bootstrap.png` and `editor-journey-saved-bootstrap.png` and were visually
  inspected. They show the isolated fixture, not a signed-in production deployment.
- `git diff --check` passed. The earlier native run exposed an ambiguous exact-label selector
  after successful preparation; explicit accessibility labeling fixed it, and the complete
  native scenario passed on the final source.

## Scope and remaining external checks

Browser principals and the native IDE service fixture are synthetic. Real IdP/JWT login,
colleague grants, deployed-session revocation and live service operation remain **NOT RUN**.
See the [real-account worksheet](ontology-review-colleague-checks.md). The proposal editor is
text-oriented (JSON/YAML), not a rich semantic composition tool. Peer/community stage names
remain protocol-defined; editable names/descriptions belong to bootstrap/coalescing.

The earlier pushed Services commit's [GitHub Actions run](https://github.com/integratedmodelling/klab-services/actions/runs/42353637952)
failed resolving `org.integratedmodelling:klab.product:1.0.1-SNAPSHOT` at the reasoner-server
packaging module. It had compiled/packaged the preceding modules and explicitly skipped tests.
This revision does not change that external plugin publication, so a fresh CI runner may still
hit that dependency-resolution blocker. No remote CI result is claimed for these unpushed changes.

## Reproduce

Use JDK 21 and the isolated cache/test home described in the prior staged verification note.
Run `npm run build` in `klab.core.services/src/main/webui` before packaging. Then run:

```powershell
mvn -o -pl klab.services.resources.server -am install -Pwebui `
  '-Dskip.installnodenpm=true' '-Dskip.npm=true' `
  '-Dmaven.repo.local=<isolated-cache>' `
  '-Dmaven.repo.local.tail=<verified-cache>,<read-only-user-cache>' `
  '-Dtest=*SerializationTest,ResourcesInitializationTransportTest,WorkflowRequestBindingTest,ReflectionMetadataTransportTest,LookupTableTransportTest,*Review*,Workflow*,*Bootstrap*,ProjectMaterialIOTest,WebUiConfigurationTest,ProposalSchemaValidatorTest,IsolatedProposalCandidateValidatorTest,ProjectMaterialAuthorizationTest,HubWebAuthenticationTest,TokenAuthorizationFilterTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-DargLine=-Duser.home=<isolated-home> -javaagent:<byte-buddy-agent-1.17.5.jar>'
```

The frontend skip flags reuse the explicitly rebuilt local assets and existing dependencies;
they are not a substitute for the preceding web build. Native browser tests use installed Edge
through `KLAB_TEST_BROWSER_CHANNEL=msedge`. Do not run the interactive native IDE fixture while
replacing its dependency jars in the Maven cache.
