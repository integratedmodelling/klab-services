# #90 native fixture attempts — 2026-10-31

PR #90 merged normally on 2026-11-01 at
`d86f9927d32aba983060760fdc9953feaf532631`, accepting
`a0a013a188fb752907f2d143a61b86f4a9cb0a0b`. Source regression/build checks pass;
fresh Java/Jenkins checks pass on this head. Python checks did not rerun.
The three preparations below used `d687856…`; batch 4 used `7027fe1…`.
Python checks on that previous head remain historical. Native acceptance is separate; preparation failures
below are not permission-test passes or scientific executions.

## Attempt 1 — BLOCKED during preparation

- Explicitly authorized isolated metadata-only batch, with no automatic retry.
- GraphDB launched; Resources failed to initialize because the Java-only package
  did not contain `static/index.html`, required by `KlabWebUiController`.
- No native clients, sessions, contexts or observation Jobs were created.
- Owned GraphDB/Resources processes stopped; original listener PIDs unchanged.
- Root cause is demonstrated by the retained Spring exception, not the generic
  readiness-timeout summary alone.

Correction: use the existing documented `-Pwebui` profile. The real Vue/Quasar
dashboard typechecks, builds and packages successfully; all 18 modules pass.
Verified core JAR SHA256:
`c5c59d4a4eee51ff47e6cb569c5b3212569217373549dbbe2f253c145f3f6bfc`;
real dashboard index SHA256:
`3d8f25dd5d6642c83c90237d8d472859c41eec3745d66dec86b21b3454bfcdaa`;
six bundled JavaScript resources. No placeholder UI or production UI source
change was used. The harness now checks that artifact before starting anything,
detects fatal startup logs promptly, and separates listener startup from complete
topology availability.

## Attempt 2 — BLOCKED during preparation

- User explicitly renewed one batch under the same limits.
- Dashboard preflight passed. GraphDB and Resources listeners were verified as
  owned; Resources reported actual `available: true`, `operational: false`.
- Reasoner attempted default port 8091 despite the launcher's custom `-port`
  option and failed to bind. The original Reasoner retained that port/PID.
- No native clients, sessions, contexts or observation Jobs were created.
- Owned GraphDB/Resources/Reasoner processes stopped; fixture ports cleared.

Source trace: `ServiceNetworkedInstance.start()` puts the custom port into
Spring's **default properties**, which are lower priority than the Reasoner
YAML's `server.port`. Correction is fixture-only: pass `-Dserver.port` explicitly
for every service, retaining matching `-port` metadata so actual and advertised
URLs agree. Do not alter or restart the original services. The new offline
launch-argument test verifies all five JVM configurations, private homes/temp
paths and loopback binding. Ten safety tests pass. Independent read-only review
found no new execution blocker.

## Attempt 3 — BLOCKED during preparation; isolation incident

The user explicitly approved one fresh attempt after the port correction.
It uses a new, non-overwritable `native-batch-3` directory and preserves previous
attempts. Source/probe snapshots are saved before process creation; native client
classes are loaded from that frozen snapshot. Limits remain three ordinary test
users, two session-creation rounds/eight logical Runtime contexts, two owned
Runtime restarts, fifteen minutes after readiness, zero numerical submissions or
imports, no production changes, and no automatic retry after unexpected failure.

The attempt stopped on an unexpected service identity before any native client
ran. The fixture supplied `servicePath` to configuration classes that do not
support it. Runtime's existing configuration loader caught the parse error,
deleted/replaced its configuration and selected its default backend at port
7687. The retained Runtime log confirms successful connection to the original
GraphDB. This violated the intended isolation boundary. All owned services were
stopped; unchanged original PIDs do not establish unchanged database state.

The user separately authorized a bounded, read-only transaction-log audit, then
one read-only capabilities request to establish the active backend's data root.
Only 2,170,457 bytes of transaction logs were copied; no database queries,
repairs, rollback, restart or original-file writes were performed. The native
Neo4j parser read both copies completely: 443 `neo4j` and 46 `system`
transactions, no gaps, unhandled entries, broken tails or unfinished starts.
No committed transactions overlapped the enclosing incident window
`2026-10-31T20:50:00Z`–`20:50:30Z`, nor appeared afterward in the copies.
Independent evidence review agreed that no committed graph modification from
this event was found within retained-log coverage. Reads, aborted operations,
connection activity and nontransactional/file effects are not excluded.

The launcher is explicitly disabled pending typed Java configuration preflight,
backend isolation corrections, new-candidate validation, independent review and
renewed execution authorization. Local Runtime fail-closed configuration changes
are published by normal fast-forward at `7027fe1…`. Twenty focused Java regressions pass, including explicit-backend
presence, preserved invalid-file bytes, first-run/explicit-clean behavior and
actual constructor rejection before service initialization. Sixteen offline
harness/typed-parser tests pass, including all four valid service configurations,
the incident's unknown fields, wrong/default backends and the execution hold.
Earlier green CI/259-test results apply
to the published candidate, not these new changes. No native acceptance PASS,
relaunch authorization or merge approval is inferred from the audit.

Independent review of the first local hardening pass found two omissions: a
missing backend still inherited Java defaults, and endpoint mismatch was only
checked during scheduled initialization. The revised loader requires the field
explicitly in the YAML snapshot and checks endpoint expectations during
constructor admission, before superclass initialization; the later connection
guard remains defense in depth. Independent re-review found no remaining blocking
source defect in this correction. The stable cross-module run passes 47 tests
across eight modules, with zero failures/errors/skips. The subsequent broader
selected run passes **279 tests in 55 suites across 13 selected modules**, with
zero failures/errors/skips. Sequential dashboard-enabled packaging passes all
18 modules. A private frozen snapshot records four changed source/documentation
files, tooling, validation logs, two compiled Runtime classes and twelve artifact
hashes. The packaged Runtime classes match the validated compiled classes.
Final independent evidence review verified all source/tooling/artifact hashes
and supported publication only. The new head is published, with fresh Java and
Jenkins checks passing; PR90 remains draft and native acceptance remains blocked.
Additional offline integrity gates verify twelve JARs and 2,184 launch classes;
the expanded harness/typed suite passes twenty tests. The separate Spring
driver is explicitly bound to the private endpoint
by the disabled fixture launcher; the custom guard does not cover all drivers.

Offline preflight development also exposed Windows command-length limits, fixed
with JVM argument files. One combined offline run failed valid-config cases
during a concurrent Maven recompilation; a standalone diagnostic and the later
stable complete run passed. These failed offline logs remain retained and are
not native acceptance outcomes.

The first broader validation wrapper also stopped on Mockito's native stderr
warning because Windows PowerShell treated it as a terminating ErrorRecord.
It produced no complete test verdict and did not attempt packaging. Its log is
preserved separately; the corrected wrapper checks Maven's actual exit status.

The metadata-only fixture intentionally loads no worldview. The issuer supplies
no group observer semantics, and `DefaultObserver.select()` returns null in that
case; `DefaultObserverPreparation.prepare()` returns before submission. No
scientific interpretation or operational scientific interoperability is claimed.

Peer checks distinguish initial reconstruction, warmed caches and behavior after
Runtime-only restart. Restarting Runtime does **not** make existing
Reasoner/Resources/Resolver caches cold. Denials require explicit HTTP 403/404
and positive owner controls; null results, 5xx and timeouts are not denial proof.

## Attempt 4 — FAIL at first native context creation

The user explicitly approved one fresh bounded batch against published
`7027fe1debf1eb1f6776cc0b4458ddbc72737b12`. The user also approved read-only
diagnostics through PR90 closeout; this does not authorize additional mutation
batches or expand the existing workload/merge conditions.

After that approval, the launch hold became a default-deny authorization gate:
ordinary `main()` still refuses execution, and the single approved invocation
explicitly passes `authorized=True`. Twenty offline safety/typed tests pass after
this gate adjustment. `native-batch-4` is a fresh, non-overwritable directory.
The topology and identity gates passed, then the first native client obtained the
default session and requested a private context. Runtime returned HTTP 500:
`configuration.getPersistence()` was null at `KnowledgeGraphNeo4j.java:838`.
The probe had used the no-argument configuration builder without setting
persistence. The request's creation journal remains `SENT_UNKNOWN`; no context ID
was returned. No permission/restart matrix ran and zero observation Jobs ran.

Cleanup preserved the unknown outcome and refused to dispose of the parent
session `pr90_review_acceptance`. All six recorded owned processes are absent,
fixture listeners are clear, and original service listener PIDs are unchanged.
This is process cleanup, not confirmed object release. The unknown creation and
unconfirmed session remain explicit in `result.json`; no mutation was resent.
Private failed logs/results have a SHA256 inventory. Read-only diagnosis and
independent failure review are underway; no automatic/live retry is authorized.

Fixture-only additions disable the demonstrated Spring Mongo client in Reasoner
and Resources and explicitly select H2. Both Spring and custom Runtime Neo4j
paths bind to the private fixture backend. All seven classpath texts are frozen
before launch. Integrity preflight checks twelve validated JARs and 2,184 present
launch-class files; it is not comprehensive dependency/resource or egress
attestation. The final fixture review found no remaining known startup blocker
for requesting this authorization, not native/scientific success.

The limits remain three ordinary users, two session rounds/eight contexts, two
owned Runtime restarts, fifteen minutes after readiness, zero observations,
numerical jobs or imports, no changes to existing services, owned-state cleanup
only, stop at first unexpected failure, and no automatic retry.

## Attempt 5 — FAIL at first context request; cause undetermined

The user explicitly approved one new metadata-only batch on `1f541836…` after
fresh Java/Jenkins CI and independent readiness review passed. It runs in fresh
`native-batch-5` state with new service identities and an empty private graph;
batch-4 data and UNKNOWN journals remain untouched. The twenty-one offline checks,
exact candidate/artifact gates, frozen classpaths and explicit persistence are
preserved. The invocation explicitly supplies `authorized=True` only after that
approval. Original limits and stop/no-automatic-retry rules remain unchanged.

The first native client obtained the default session, then the first private
context request returned HTTP 500. The retained client log has no diagnostic
detail; Runtime's stdout and private rolling log do not record the underlying
exception. Unlike batch 4, missing persistence is not established as the cause.
One creation remains `SENT_UNKNOWN` with no returned context ID; parent-session
disposal was refused. Zero observation Jobs ran, and no permission/restart matrix
was reached. All six recorded owned PIDs are absent and fixture ports are clear;
the original six listener mappings remain unchanged.

Read-only copies of the stopped fixture's transaction logs total 18,481 graph
bytes and 27,230 system bytes, with matching source/copy hashes. Complete parsing
finds 49 graph and 46 system commits, no gaps or broken tails. Graph command
counts include three node commands plus token/schema/property work. This proves
partial durable private work, not Context creation or absence; bootstrap system
transactions are not all attributed to the failed request. No database was
opened, queried, restarted, repaired or deleted. Original failed journals remain
unchanged, and the failure logs have a SHA256 inventory.

Read-only diagnosis and independent source/evidence analysis are underway.
No native PASS, merge, cleanup-success claim or automatic/live retry is authorized.

### Offline discoveries after batch 5

The fixture's private GraphDB plugin directory was empty. The service loads its
plugin drop-ins from the process working directory; the isolated launcher had not
copied the repository's tracked spatial plugin there. Runtime context initialization
unconditionally calls `spatial.layers()`/`spatial.addLayer()`. The inspected plugin
SHA256 is `83c9d31deebcc5a951f11832ffbc58692a2795a461baba480efe74cafbeeff6b`,
matching the binary in Git at `1f541836…`.

An actual offline in-process probe, with HTTP/Bolt connectors disabled, reproduced
procedure-not-found without that plugin. With it installed, both procedure names,
layer enumeration and empty WKB metadata-layer creation/listing passed. No existing
database, Runtime contexts or observations were used. This demonstrates a missing
deployment prerequisite and failure mechanism, **not** batch 5's discarded cause.

The next fixture is default-denied and prepared in separate, uncreated
`native-batch-6` state. It verifies and exclusively copies that plugin, then runs
an owned-endpoint, read-only `SHOW PROCEDURES` gate before other services or user
mutations. A private native HTTP observer delegates each original request once
and returns the same response; it never replays a failed creation. It samples at
most sixteen KiB and records only fixed allowlisted categories/stage hints,
status/counts and a local diagnostic ID. Hints are explicitly untrusted: no raw
body, extracted identifier/frame, message, header, request payload or credential
is saved. Async calls are delegated unchanged.

Review caught a regex-based privacy gap in an earlier diagnostic version; that
version is superseded by fixed categories and forged-class/frame sentinel tests.
The expanded offline suite passes **26 tests**, including original-request/response
identity, one-send/no-replay, no evidence overwrite, plugin hashing/installation,
original-endpoint refusal, and forged-payload exclusion. Independent re-review
found no remaining known blocker to **requesting** one new bounded attempt.
Reviewed sources/classes/test evidence are frozen separately. No new live
authorization or merge acceptance follows from those offline results.

## Attempt 6 — FAIL at first peer check; native creation/restoration progressed

After the deployment/diagnostic review and twenty-six offline checks passed, the
user explicitly approved one fresh batch including the private plugin installation
and read-only procedure gate. It runs against `1f541836…` in new `native-batch-6`
state. The earlier UNKNOWN journals and data are not replayed, released or deleted.
Original limits remain three ordinary users, two session rounds/eight contexts,
two owned Runtime restarts, fifteen minutes after readiness, zero observations,
numerical jobs or model imports, no existing-service changes, stop at first
unexpected failure, and no automatic retry.

Both native client processes passed: three contexts returned IDs with expected
owners/Runtime binding, shared restoration through the native API passed, and
private/exclusion controls returned HTTP403. The aggregate batch stopped when
the first owner-scoped Reasoner metadata request returned HTTP404. The frozen
Python harness supplied `klab-service-id`, while production `ServicesAPI` defines
`klab-service`. Reasoner explicitly logged the missing required origin header.
Independent review confirmed this fixture defect; it is not evidence of a
production origin-resolution regression. Direct Runtime checks remain ACL/access
evidence, not explicit origin-enforcement proof: the wrong optional header was
ignored by the hosting Runtime.

All three context release responses and session cleanup returned HTTP200/true.
Final unknown/unconfirmed arrays are empty. All seven recorded owned PIDs are
absent, fixture listeners are clear, and original listener mappings are unchanged.
This is response-confirmed release/process cleanup, not independent DB absence
proof. Successful peer propagation, restart recovery, reversed order and outsider
checks remain unrun; the batch is **FAIL**, not partial acceptance.

### Header-contract correction and extended checks

Direct Java requests now use production header constants. Python's header map is
verified against a compiled `--protocol-contract` branch before any service launch;
all direct paths, including cleanup and wrong-origin controls, use that map.
New tests inspect outgoing headers and wrong-origin overrides, exercise actual
compiled constants, and ensure mismatches stop preparation. Peer positives require
HTTP200; negatives accept only HTTP403/404, rejecting timeouts/throttling/5xx.
Wrong-origin denial is followed by a valid-origin HTTP200 recheck. Events distinguish
rounds and before/after Runtime restart; a warmed 404 is not automatically claimed
as cached-ACL branch proof, nor do Runtime-only restarts make peer caches cold.

The expanded offline suite passes **30 tests**. Independent review found no
remaining known blocker to requesting one fresh bounded batch. The candidate
remains unchanged at `1f541836…`, default-denied fresh `native-batch-7` has not run,
and no renewed execution or merge authorization is inferred.

## Attempt 7 — PASS on `1f541836…`; independently verified bounded native gate

The user explicitly approved one fresh bounded metadata batch after the header
contract correction and thirty offline checks passed independent review. It runs
in new `native-batch-7` state against unchanged `1f541836…`; limits, stop-first-
unexpected-failure and no-automatic-retry rules remain unchanged. The compiled
protocol preflight precedes plugin installation and service launch. Prior UNKNOWN
journals/data remain sealed.

The aggregate result is PASS: eight distinct contexts, both creation orders, ten
native client processes, two owned Runtime restarts, zero observations, and empty
unknown/unconfirmed ledgers. All ten native result journals pass. Two native owner
releases plus six response-confirmed owner cleanup releases cover all eight IDs;
both privileged test-session cleanups return HTTP200/true. All seventeen tracked
start PIDs, including replaced Runtimes, are absent; transient-helper process check
is clear, fixture ports are clear, and original listener mappings are unchanged.

Runtime controls record eight positive HTTP200 and seven denial HTTP403 responses.
Each of four peer phases records nine positive HTTP200, three private HTTP403,
three wrong-origin HTTP403 and three valid-origin HTTP200 rechecks. Missing scope
returns HTTP404; malformed/expired credential controls return HTTP403. Ordinary
native client claims contain only ROLE_USER and no client service-secret files;
the separate outsider is checked via scoped HTTP. Independent review verified
fifteen acceptance hashes, thirteen frozen tooling inputs, seven classpaths,
caller/owner identities, response-level cleanup and the bounded time budget.

This accepts metadata identity/ACL/lifecycle behavior only. Peer caches stay warm
across Runtime-only restarts. It is not scientific, broker, separate-deployment,
or simultaneous identical-ID multi-Runtime acceptance, nor independent proof that
all graph records were deleted. Earlier failed attempts remain failed/sealed.

### Post-gate security finding — merge remains blocked

Final review found an inherited issuer/audience-contract gap: the legacy verifier
skipped audience validation and its wrong-audience branch did not reject. A real
RSA-signed wrong-audience regression reproduced authentication (one expected-false/
actual-true failure, no test error). The local correction now requires exact
trusted issuer and engine audience, validates required claims before principal
assignment, rejects invalid explicit credentials before controller dispatch, and
clears stale context. Anonymous/browser/private-local-key authority paths remain
distinct; deliberate local-key authority can independently authorize a request.
Unexpected nonfatal manager errors remain sanitized server failures, not successful
permission-denial evidence. Rejection logging avoids tokens/claim payloads.

Thirty-eight focused tests pass: twenty-seven signed-token tests, ten filter
tests and one identity-header test. Source/security re-review found no remaining
blocker. Broader selected validation and sequential dashboard packaging are running.
The corrected broader pass succeeds: **321 tests in 57 suites**, zero failures/
errors/skips across thirteen selected modules, followed by all eighteen modules
packaging with the dashboard profile. Independent final evidence review verified
thirteen frozen files, twelve artifact hashes and both compiled/packaged security
classes. The five-file correction was committed, source-attested and published by
guarded normal fast-forward at `a0a013a…`; fresh Java/Jenkins CI passes. Batch 7's
exact-head PASS does not validate this changed authentication candidate.

The updated default-denied harness pins `a0a013a…` and fresh, uncreated
`native-batch-8` state. Five new read-only credential controls run before contexts:
valid protected metadata, genuinely signed wrong-audience rejection without caller
assertions, malformed/expired credential rejection on public metadata, and explicit
anonymous public access. Compiled protocol preflight now also checks the anonymous
credential constant and protected route, avoiding guessed values. The expired
fixture is well beyond the thirty-second skew allowance. Thirty-three offline
checks pass and independent readiness review found no new blocker. Exact-head
native revalidation remains pending explicit renewed authorization; no automatic
retry or merge acceptance is inferred.

## Attempt 8 — exact security-corrected head PASS; final review pending

After publication, fresh Java/Jenkins CI and the updated thirty-three-check
harness review, the user explicitly approved one fresh batch against `a0a013a…`.
It runs in new `native-batch-8` state with the five additional read-only credential
controls and the unchanged eight-context/two-order/two-restart metadata matrix.
All prior limits and stop/no-automatic-retry rules remain in force; earlier failed
data and UNKNOWN journals are untouched. Historical batch-7 PASS is not reused.

Aggregate result is PASS on `a0a013a188fb752907f2d143a61b86f4a9cb0a0b`: eight
distinct contexts, both orders, ten native clients, two owned Runtime restarts,
zero observation Jobs and empty unresolved ledgers. All ten native journals pass;
two native fresh-context releases plus six ordinary cleanup responses cover all
eight IDs, and both test-session cleanups return HTTP200/true. All seventeen
tracked PIDs, including replaced Runtimes, are absent; normalized-path helper
checks find no remaining batch JVMs; fixture ports are clear and original listener
PIDs are unchanged. Native ordinary claims remain ROLE_USER/audience engine, with
no service-secret files in their homes. No credentials or keys were printed.

The additional credential controls return HTTP200 for valid protected and explicit
anonymous public access, and HTTP403 for signed wrong-audience, malformed and
expired credentials on public metadata without caller assertions. All four peer
phases again record nine allowed/200, three private/403, three wrong-origin/403 and
three valid-origin rechecks/200. Runtime controls and missing/invalid checks pass.
Acceptance hashes and a compact noncredential summary are retained privately.
Final independent result-aware review gives bounded ACCEPT. Fresh exact head,
live target, current-head CI and repository-rule guards passed before the normal
merge; merge tree matches the validated candidate. Scope and scientific limitations
from batch 7 still apply; response-confirmed release is not a DB-absence audit.

## Evidence and cleanup rules (all attempts)

### Read-only batch-4 audit and offline correction in progress

Only the stopped, owned fixture's transaction logs were copied for this audit:
16,294 bytes (`neo4j`) and 27,230 bytes (`system`), with matching source/copy
SHA256 hashes. Both copies parsed completely: 30 graph and 46 system commits,
no transaction-ID gaps or broken tails. Graph commits include three Node commands
and schema/property work. This confirms partial durable fixture writes; command
counts alone do not identify whether a Context node exists. The original
`SENT_UNKNOWN` journal and unconfirmed session remain unchanged. No database was
opened, queried, repaired, restarted or deleted for the audit.

Independent diagnosis found a fixture omission and a production validation gap.
The offline probe now explicitly requests `Persistence.EXPLICIT_ACTION`; its
compiled `--validate-fixture` check passes before constructing an engine/client.
Local Runtime changes reject missing persistence before ID assignment, graph
contextualization or registration, using the existing controlled invalid-request
exception. This does not claim controller-level handling of a null request body.

Local messaging changes treat a missing broker as unavailable rather than trying
a null connection. Review caught a shared-transport cleanup regression in the
first implementation: copied scopes borrow their parent's channel, and disconnect
can delete its consumer queue. The revision tracks whether a scope created its
channel, detaches borrowed channels without disconnecting them, and adds a
two-copy/parent regression. No broker has been added.

The first focused lifecycle/messaging run passed 18 tests. The expanded borrowed-
transport test initially failed compilation because its concrete test channel
omitted `getDispatchId()`; that test-only omission is corrected. The first broader
run stopped before tests because the offline cache could not resolve a Groovy
build-extension dependency. Its log is retained, and packaging was not attempted.
The selected-suite run now uses Maven's normal resolver, without dependency
overrides or manual cache-metadata edits, followed by sequential dashboard
packaging if tests pass. That corrected run passes **290 tests in 56 suites**,
with zero failures/errors/skips across thirteen selected modules; sequential
dashboard packaging passes all eighteen modules. Final independent evidence
review verified twenty-one frozen files, twelve artifact hashes, and compiled/
packaged byte equality for all three changed production classes. The five-file
correction was committed and normally fast-forward published at `1f541836…`.
The clean commit matches the frozen validated source, allowing Git EOL
normalization only. Fresh CI is pending; prior `7027…` CI is historical.

The disabled-by-default harness now pins `1f541836…` and a fresh, uncreated
`native-batch-5` directory. Twenty-one current offline safety/typed/native fixture
tests pass; the integrity gate verifies twelve JARs and 2,184 present launch
classes against the new manifest. Independent readiness review found no blocker
to requesting one renewed bounded batch after fresh CI passes. No new live
authorization exists, and `main(authorized=True)` has not been invoked again.

Private evidence root:
`C:/Users/lumsd/AppData/Local/Temp/opencode/pr90-closeout-evidence-20261031/`.
Each attempt has its own `result.json`, events, process arguments, owned-PID
registry, private service homes and logs. Attempt 2's executed runner/probe image
was recovered into `native-batch-2/executed-tooling` and verified against its
recorded hashes; later launcher edits did not overwrite the failed evidence.

Mutation intent is journaled before sending. Unknown creation/release outcomes
are retained, never retried; unresolved context outcomes prohibit session
disposal. Cleanup failures and unknown outcomes prevent PASS. Only owned client
and service processes may be terminated, with no visible service consoles.

Original listeners verified after both completed preparations:
Reasoner 40900:8091, Resources 37600:8092, Resolver 42988:8093,
Runtime 43696:8094, GraphDB 46944:8382/7687.

No merge decision may be inferred until the final attempt result, cleanup,
exact-candidate provenance and independent evidence review are complete. #98 and
#100 remain queued behind #90; the final public technical-page update remains
queued until that requested sequence is finished.
