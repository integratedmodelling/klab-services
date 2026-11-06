# Bounded simulated pilot verification

Isolated branch `ontology-review-e2e`, based on preserved worker checkpoint `28b7976c10ab`.
No deployed service, original checkout, real account or ontology project was changed.

## Scenario and result

`ReviewPilotBrowserScenarioIT` launches the real ResourcesReview Vue component against a
loopback-only test HTTP adapter, the production WorkflowManager and actual Nitrite/RocksDB.
The real parsed k.Actors bootstrap behavior is bound to a mock read-only project/ontology source.
Four separate headless browser contexts use fixed synthetic authenticated scopes: editor,
reviewer-a, reviewer-b and community. No accounts, passwords, certificates or production tokens
are created or borrowed.

The scenario passes through:

1. Editor action generates a proposal, source, empty comments and provenance from fixture
   ontology/BOOTSTRAP context; the candidate is displayed in the real UI.
2. One cycle opens overlapping reviewer and community rounds. Community sees its admitted
   round and exact candidate without editing-stage or peer-feedback access.
3. Reviewer A saves a private draft and alternative proposal, then submits it. Reviewer B and
   the editor cannot read that private draft. Reviewer B's stale submission conflicts, preserves
   its text, then succeeds after an explicit refresh. Community submits independent feedback.
4. Editor sees attributed contributions and filters by contributor and alternative proposal,
   while the current candidate remains intact beside the feedback.
5. Editor closes the first cycle, saves a fresh integrated revision with supersedes_revision,
   opens a later peer cycle, receives another review and closes that cycle as well.
6. Unauthorized management and late feedback are rejected. The first closed cycle compares
   exactly before/after the second cycle and after native store/manager close/reopen. Candidate
   payloads remain readable. The underlying flow remains ACTIVE: closure does not accept,
   publish or apply an ontology.
7. After restart, committed notification replay produces 9 events and 15 fake transport
   acceptances. Reopening/replaying again produces zero new events and zero sends, retaining
   all 15 acceptances. No real mail transport is instantiated by this harness.

`pilot-e2e-scenario.json` records the final assertion-backed counts, revision/checksum references
and explicit simulated-authentication boundary. `pilot-e2e-browser.log` records the browser case.
The scenario passed in both the focused and combined reactor runs.

## Product issue found and corrected

The run initially reached integrated-proposal upload but the shared configured JSON mapper
returned a LinkedHashMap for the browser's plain Flow.AttachmentUpload body. The DTO's registered
polymorphic reader expected a Java type marker and overrode the declared abstract-type mapping.

Known workflow interfaces now bind to their declared built-in DTO implementation for plain JSON.
Existing tagged Java-client payloads remain supported. A mismatched root @CLASS marker is
rejected before caller-selected class resolution. This change is limited to the known workflow
DTO family; it is not a general redesign of polymorphic transport or scientific validation.

Regressions cover plain attachment/initialization bodies (including nested state metadata and
bytes), Java-client roundtrip, rejected unrelated markers, native flow/checkpoint restoration and
the real WorkflowController receiving a plain browser attachment request with its exact content.
No frontend request workaround or JVM class marker was added to the product UI.

## Evidence

- `pilot-e2e-workflow-binding.log`: focused normal Resources-server reactor BUILD SUCCESS,
  including mapper, native persistence, the browser scenario, behavior/checkpoint and HTTP tests.
- `pilot-e2e-combined-reactor.log`: normal combined reactor BUILD SUCCESS, 263 tests with none
  skipped: 43 common serialization/DTO, 55 core-service, 43 Reasoner bootstrap, 2 Resolver,
  117 Resources and 3 server tests. This includes the scenario a second time.
- `pilot-e2e-ui-regressions.log`: all 7 existing isolated review UI browser tests pass.
- `pilot-e2e-typecheck-final.log`: Vue/TypeScript typecheck passes.

Earlier logs retain harness failures: ambiguous Java Flow import (`pilot-e2e-reactor.log`), the
fixture-only five-second assertion timeout (`pilot-e2e-reactor-recheck.log`), and the real plain-DTO
binding failure (`pilot-e2e-native-ui.log`). The real-backend suite uses a bounded 30-second
assertion timeout, 120-second browser case and 180-second child-process deadline. All owned
test servers/browser processes and temporary native stores are closed during cleanup.

## Reproduction and limits

Run in an isolated checkout with existing UI node_modules and headless Edge available in this
Windows environment. The opt-in IT is not selected by the normal default *Test naming pattern;
explicit broad *Review* selections include it. It starts a strict loopback-only Vite fixture on
port 4233, refuses reuse of an existing server, and an ephemeral loopback backend. Logs/report
are written beside the isolated checkout; native database and fake outbox use JUnit temporary
directories. No dependency installation, SMTP settings or live service startup is performed.

```powershell
mvn -pl klab.services.resources -am test '-Dtest=ReviewPilotBrowserScenarioIT' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Duser.home=<isolated-test-home>'
```

The HTTP adapter maps fixed fixture identities to server-side authenticated scopes. It is not the
production security filter/login shell and must never be deployed. Existing controller and hub/token
tests cover their respective boundaries separately. This does not prove real JWT/IdP login,
remote multi-user sessions, authoritative role-revocation propagation or cold JVM restart.
The tested restart is a native store close/reopen with a fresh WorkflowManager in the test JVM.
This is a bounded four-principal scenario, not a production load or stress benchmark.

No designated authorized live test identities were available. Real delivery still needs an
authoritative verified-mailbox/consent directory and delivery authorization/configuration.
Archive dispatch remains disabled pending the readership policy and archive outbox/receipts.
There was no real mail, account creation, push, deployment, service restart or real-project write.
