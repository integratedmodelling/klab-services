# Ontology review pilot handoff

Use an isolated Services checkout with the supplied bundle/patch and its delivery receipt.
The published develop prerequisite is `212548f761ada2b5eecaac013191be0d5ce4152d`.
The earlier worker and simulated end-to-end checkpoints are preserved. Do not build/test by
resetting a user's checkout, restarting their services or committing into an ontology project.

In a fresh isolated clone containing the develop prerequisite, inspect the delivery receipt, then:

```powershell
git bundle verify <ontology-review-digest.bundle>
git fetch <ontology-review-digest.bundle> refs/heads/ontology-review-digest
git switch -c pilot-digest FETCH_HEAD
```

The audit bundle separately preserves `ontology-review-audit`; the digest bundle includes those
fixes. Use the receipt's exact commit/hash rather than combining overlapping patches twice.

## Repeatable verification

Requirements: Java 21, Maven, the verified published Languages artifacts, isolated UI node_modules,
and headless Edge in this Windows environment. Keep the writable Maven cache/test home separate
from personal configuration; use the existing user Maven repository only as a read-only tail.
The task's `published-language-inputs.json` records the pinned Languages publication used here.

From the isolated checkout, substitute real local paths in these arguments:

```powershell
mvn -o -pl klab.services.resources.server -am test `
  '-Dmaven.repo.local=<writable-verified-cache>' `
  '-Dmaven.repo.local.tail=<read-only-cache>' `
  '-Dtest=*SerializationTest,WorkflowRequestBindingTest,ReflectionMetadataTransportTest,LookupTableTransportTest,*Review*,Workflow*,*Bootstrap*,ProjectMaterialIOTest,WebUiConfigurationTest,ProposalSchemaValidatorTest,IsolatedProposalCandidateValidatorTest,ProjectMaterialAuthorizationTest,HubWebAuthenticationTest,TokenAuthorizationFilterTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-DargLine=-Duser.home=<isolated-test-home> -javaagent:<byte-buddy-agent-1.17.5.jar>'
```

The explicit *Review* selection includes the opt-in browser/native-store scenario. It starts only
loopback fixtures (Vite 4233 plus an ephemeral API port), four synthetic principals and temporary
native storage, then closes them. It writes browser/scenario evidence beside the isolated checkout.
It does not start the production Resources server or perform real login/mail/archive dispatch.

From `klab.core.services/src/main/webui`:

```powershell
$env:KLAB_TEST_BROWSER_CHANNEL='msedge'
node node_modules/vue-tsc/bin/vue-tsc.js --noEmit
node node_modules/@playwright/test/cli.js test --config playwright.review.config.ts
node node_modules/vite/bin/vite.js build
node node_modules/vite/bin/vite.js build --config vite.embedded.config.ts
```

The normal fixture suite uses loopback 4229 and refuses server reuse. A second native-only run can
select `ReviewPilotBrowserScenarioIT,ReviewSecurityAuditTest,WorkflowConditionalWriteTest` through
the Maven command above. Source test bridges must never be deployed as authentication endpoints.

## Before a real-account pilot

- Supply authorized editor, two reviewer and community test identities on an isolated deployment.
  Configure authoritative group grants narrowly: editor/reviewer workflow.roles and
  workflow.permitted=ontology-review-pilot; an authenticated community user needs no reviewer grant.
  Workflow roles alone do not grant service administration. Verify expiry/revocation and anonymous,
  unrelated-editor, peer-only and cross-stage denials through the actual login/filter/session path.
- Configure the advertised Resources UI origin and authentication return route. Create an
  ontology-review-pilot@0.2 flow through the existing editor/Resources createFlow API, using an
  authorized ontology target. Enter `ui/review?flow=<id>&stage=<id>`; stable cycle/round parameters
  identify iterations. Bootstrap requires current read access to the ontology's server-owned host
  project and optional BOOTSTRAP.md.
- Protect local service data with appropriate ACLs and backups. The existing RocksDB database has
  one owning process; replicated writers/failover are not established. Check capacity for retained
  review data and the bounded replay outbox. Closed cycles remain immutable and consultable.
- Inspect `GET /api/v1/admin/review-notifications?limit=50` using service ADMINISTRATOR plus an
  ADMINISTER-authorized scope. Expect REPLAY_ONLY/deliveryEnabled=false. A persisted/queued notice
  is not an editor inbox delivery. No SMTP enable switch exists in the replay worker.

## Remaining rollout work

Real mail requires a bound authoritative current-account/verified-mailbox/consent directory,
trusted authenticated links, explicit delivery authorization/configuration, retention/capacity
policy and audited privileged reconciliation for uncertain sends. Cached scopes or an email
address alone do not satisfy that contract. No live identities were available for verification.

The archive visibility choice is implemented as a minimal digest with permission-enforced links.
Verify the advertised Resources base is stable and reachable after login, project write authority
is intentional, and the host is an initialized ordinary Git repository with a clean index/worktree.
Check the closed-cycle receipt after automatic close-time attempt. Pending/blocked jobs survive
restart and require a fresh authorized editor retry; background replay retains no user scope and
does not write projects. Test real-account/project permission revocation before rollout. Preserve
private workflow/queue backups together. A branch-published/index-failed condition requires operator
inspection; no reset/repair is automatic. Do not invoke the old full-history mapper as a public
export. No remote push is implied by a local digest commit. See the digest status note for evidence.

No push, deployment, service restart, real email, account creation or real-project write is part
of this handoff's verification commands. Approval to deploy or enable real delivery is separate.
