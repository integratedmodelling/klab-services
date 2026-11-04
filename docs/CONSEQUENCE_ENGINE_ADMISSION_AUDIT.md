# Consequence engine audit and staged repair prompts

Audit date: **3 November 2026**. Scope: the current working tree, including the admission and
collective-input repairs that made `staging.vxii.basic.cotechino` resolve successfully. The base
commit is `f6ae0ab21`; auditing that commit alone omits the repairs. This audit changes no
production behavior and does not restart or submit work to the live stack.

Read [admission](ADMISSION.md), [consequence contracts](CONSEQUENCE_ENGINE.md), and
[existing implementation status](CONSEQUENCE_ENGINE_STATUS.md) alongside this plan. The existing
C1–C7 acceptance gaps and explicitly unsupported semantics remain separate work.

## Findings

### Resources supplier boundary — 4 November 2026

The user clarified the deployment contract after the investigation below: “local” means the
Runtime and Resolver committed to a ContextScope, not localhost. Other services may reconstruct
the requesting scopes; access to a non-public originating knowledge graph is the obstacle.
Requiring Resources to participate in that graph/semantic-cycle workflow was the wrong boundary.

The implementation now obtains authenticated candidate source snapshots from Resources suppliers
without sending graph, transaction, consequence or semantic-cycle headers. Optional namespace,
project and scenario selectors preserve lexical visibility; no namespace/project is required for
an ordinary concept-URN request in the selected context. Public authorized sources are captured
broadly, then mapped and filtered with the committed Resolver's retained Reasoner. Source-only and
executable admission remain in that workflow, and captured source bytes/digests bypass the global
model cache and later provider re-fetch during model admission. Supplier project READ checks,
parser diagnostics, inactive sources and native execution checks remain in force.

Candidate responses are retained within one Resolver attempt. The new supplier endpoint has a
capability-selected user-scoped authorized namespace-list compatibility path for older Resources
servers, with no probing of unsupported routes or fallback to graph-dependent frozen queries. A failed supplier does not discard usable local
candidates; it marks discovery incomplete. An unresolved result under incomplete discovery is
DEFERRED, not a model-absence certificate, including optional requests. This changes the previous
all-provider-completion policy deliberately; it does not weaken model execution admission.

See ADMISSION.md and RESOURCES.md for the implemented contract. The older investigation below
is retained as historical evidence; its suggested topology requirement is superseded here.
**Verification:** the final isolated Maven run passed **170/170**, zero failures/errors/skips:
21 common-client/source controls, 56 core admission controls, 10 Resolver controls, 15 Resources
controls and 68 Runtime/collective/consequence controls. API, common, core services, Reasoner,
Resolver, Resources, Resources server and Runtime compiled in that run. The isolation was a
byte-identical copy of all 19 changed Java source/test files, with no validation-only edits;
it avoided inconsistent build artifacts observed in the shared IDE checkout. Earlier incremental
and clean-checkout classpath failures remain in their logs and are not passing results.

Coverage includes authenticated supplier HTTP transport without graph/cycle headers, capability
negotiation for old providers, source catalogs larger than the diagnostic response limit, immutable
source digests, source-backed admission with no provider re-fetch, concept-only requests without
namespace/project selectors, public-model versus private/project/scenario controls, supplier READ
permissions, local coarse semantic/spatial filtering, and transported DEFERRED outcomes when
missing suppliers prevent an explanation. Existing admission, collective-input and A1–A5 controls
also pass. Snapshot validation log: `review-evidence/candidate-supplier-isolated-tests.log`.

The command used the ordinary offline Maven reactor with
`-f review-evidence/candidate-supplier-validation/pom.xml -pl klab.services.runtime,klab.services.resources.server -am`
and `-Dsurefire.failIfNoSpecifiedTests=false`, selecting FrozenModelQueryFailureTest,
ModelCandidateSourcesTest, ResourcesMergerTest, ModelValidationGateTest, ModelAdmissionReceiptTest,
FrozenSourceAdmissionTest, PinnedPublicationAdmissionTest, ModelAdmissionBeforeRankingTest,
ModelCandidateIngestionTest, ModelCandidateSupplierTest, ResolutionOutcomeTest, NamespaceModelIndexTest,
AdapterDiscoveryTransportTest, ProjectMaterialAuthorizationTest, ProducingModelAdmissionTest,
ConsequenceSubmissionTest, ChangeProcessHandlerTest, PreparedConfigurationRecomputeTest,
ConsequenceEngineTest, CollectiveSelectionProducerClassificationDependencyTest,
MemberQualityDependencyPlanningTest and DiagnosticHttpPostTest.

Read-only live verification confirmed that the existing remote provider advertises candidate
protocol 0 and serves its authorized namespace listing with HTTP 200 (19 sources), without graph
workflow headers. Its old routing returned 500 for the unimplemented new route; capability
negotiation avoids that probe. This proves access to the legacy source-export path, not execution
of a remotely supplied scientific model. The new implementation has not been deployed, no service
was restarted, and the original basic process example has not been rerun against rebuilt services.

### Authenticated federation follow-up — 4 November 2026

The reported `staging.vxii.test.process.basic.testregion` failure was traced through the running
services in context `ESA_INSTITUTIONAL.42fb7ia008`. Local Resources logged a successful match for
`staging.vxii.test.process.basic.terrestrial-region-explainer` at 10:27:13 Madrid time; Resolver
failed the federated model query at 10:27:14, before the CHANGE consequence handler ran.

The advertised remote Resources provider, `im.resources-main` at
`https://services.integratedmodelling.org/resources/main`, was healthy and exposed the frozen
model-query API. Bounded read-only probes using the existing user's credential and actual dotted
federation ID (`ESA.INSTITUTIONAL`) succeeded without a context, but both status and model-query
requests carrying the existing context and Runtime ID returned **404 with an empty body**.
The model-query probe used an empty body and no fabricated cycle/lease, so it initiated no
scientific resolution. This reproduces a remote context-access failure; the exact status of the
earlier original request was not retained in its generic exception. Remote diagnostic log queries
returned no records, so the server's precise internal reconstruction failure remains unconfirmed.

Source inspection shows that frozen discovery intentionally fails when any participating provider
fails, even when local discovery finds a model. Context restoration requires a known authorized
originating Runtime; a caller header alone cannot establish that authority. Loopback-only local
Runtime/Reasoner access is therefore a deployment concern for full remote participation. This
failure path predates the A1–A5 repair commit. The evidence points to the authenticated federation
topology rather than a demonstrated regression in those repairs. Local-only selection is a
bounded way to rerun the previous local-stack acceptance; no live selection, authentication,
deployment or service identity was changed during this investigation.

A targeted diagnostic repair now preserves provider ID, base URL without credentials/query/fragment,
and HTTP status in ResourcesClient's frozen model-query errors, retaining fail-closed behavior and
the typed underlying cause. **17/17 focused Maven tests passed**, including real HTTP empty 403/404
responses with a successful local provider and preservation of frozen-discovery completeness.
Command: `mvn -o '-Dmaven.repo.local=C:/Users/Ferd/.m2/repository' -pl klab.core.common -am
'-Dtest=FrozenModelQueryFailureTest,ResourcesMergerTest,CollectionFailureDetailTest,SemanticCycleHttpTest'
'-Dsurefire.failIfNoSpecifiedTests=false' test`. Local log:
`review-evidence/federated-model-query-tests.log`. This diagnostic repair is not yet deployed and
does not itself make the remote provider able to use the local context.

### Repair checkpoint — 4 November 2026

All five findings have local repairs in the working tree. No additional semantic-policy decision
was needed. The findings below retain the original evidence; they describe the audited behavior
before these repairs. The staged prompts remain acceptance guidance, not a claim that every
stage's live exit gate has been completed.

- **A1:** CHANGE candidates are bounded by committed quality or bearer changes. A newly relevant
  occurrence can still revisit existing qualities; a CHANGE result does not cause that rescan
  when an unchanged occurrence already exists. A first CHANGE occurrence can enable discovery.
  Optional CHANGE construction, observer-specific discovery and joint-writer reuse remain intact.
- **A2:** APPLIED requires native currentness, CHANGE semantics compatible with the request and
  observer, complete resolved coverage and complete geometric support. A typed RESOLVED result
  is checked against these requirements. Successful ordinary Runtime submissions currently lack
  a positive terminal receipt; for those, the handler separately reloads and checks the native
  result rather than trusting delivery metadata or a positive ID. Typed negative outcomes take
  precedence over such reuse evidence.
  Typed NO_MODEL is ordinary optional absence; generic empty responses and incomplete results
  remain DEFERRED. Missing positive receipts require independently complete
  native evidence. FAILED receipts and error notifications remain failures.
  Decision observationIds continue to identify the fulfilled **qualities**, not new process IDs.
  Existing complete reusable results and native INFLUENCE writers avoid new submission; their
  native identity/support is evidence distinct from a new submission's terminal receipt.
- **A3/A4:** Receipts are scalar JSON strings with `{version:1, receipts:{key:"RESOLVED"}}`;
  activity input-change provenance is scalar JSON with `version:1` and the existing evidence
  fields. Both survive the actual native metadata encoder. A native beforeCommit callback
  replaces the birth key and provenance identity after allocation, before modified assets are
  encoded. Both the configuration and activity are registered for update. Cleanup captures the
  original registration key; commit and rollback release both in-flight registries. Rollback
  restores prior receipt and activity metadata. Legacy map decoding accepts at most 10,000 valid
  receipt rows; JSON input is limited to 1 MiB and version 1. Unknown/malformed evidence is ignored.
  Receipts remain descriptive duplicate evidence inside the existing admitted native/cycle path;
  they confer no execution or source authority and introduce no durable crash replay.
- **A5:** Engine cancellation now has a separate inherited supplier from request cancellation.
  `forConsequence` still detaches a completed user request, while retaining engine lifetime through
  configuration publication, role attribution and membership-derived work. These nested paths
  check cancellation before opening a transaction. All production call sites were inspected,
  including scope restoration, engine snapshots and membership validation. Cycle leases and the
  bounded close/drain implementation are unchanged.

Regression sources now include the retained ObservableBuildStrategy boundary for Elevation/Slope,
process-only follow-up and changed-support controls, typed completion negatives, production native
encoding, a real TransactionImpl birth/allocation/update callback path, restored-metadata duplicate
lookup, rollback cleanup, native transient ownership and nested cancellation during engine drain.
The test fixture recognizes explicit durable inputs and uses real TransactionImpl ownership for
transient inputs; production currentness rules were not relaxed.

**Local verification:** 150 tests passed, zero failures/errors/skips: 4 in core services,
1 in the Resolver module and 145 in Runtime (including resolver-package planning controls).
PreparedConfigurationRecomputeTest passed **23/23**, including its original 20 controls and
three new persistence/identity controls. Ordinary and reference submission controls passed
without changing Runtime delivery identity or its existing metadata contract. The source trace
confirmed that successful ordinary/reference submissions lack a typed positive outcome, while
empty terminal failures carry typed outcomes. ObserverResolution.deferred deliberately allows
observer-specific discovery over an absolute result; the repair bounds which commits may invoke
that discovery rather than disabling this observer policy.

The final command was:

```powershell
mvn -o '-Dmaven.repo.local=C:/Users/Ferd/.m2/repository' -pl klab.services.runtime -am '-Dtest=ChangeProcessHandlerTest,PreparedConfigurationRecomputeTest,ConfigurationRecomputeTest,ResolutionCycleScopeTest,SchedulerCurrentValidityTest,ConsequenceEngineTest,GuardedConfigurationPublisherTest,DefaultRoleConsequenceRuntimeTest,ProducingModelAdmissionTest,ModelAdmissionBeforeRankingTest,CollectiveSelectionProducerClassificationDependencyTest,MemberQualityDependencyPlanningTest,ConfigurationTransactionTest,PreparedConfigurationInputChangeTest,ConfigurationExplanationPreparationTest,ConsequenceAdmissionTest,SubmissionAdmissionCancellationTest,DirectTransactionCycleTest,ConsequenceSubmissionTest,ObserverReferenceSubmissionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The final local log is `review-evidence/consequence-admission-repair/tests.log` (ignored by Git).
Default-cache and sandbox-access attempts failed before validation; the final command used the
existing cache with the necessary filesystem access. Intermediate runs exposed fixture mistakes;
the final result above is the passing production-source run, not a shadow copy.

Live stack restart, the six historical
request/result correlations, deployed cotechino acceptance and live durable reload remain unverified.
The ignored audit probe/log directory named below was absent in this checkout, so its exact
historical 97-test command could not be recovered. The repair run names its own test group explicitly.

### A1 — P1: CHANGE follow-up commits rescan and resubmit unchanged qualities

`ChangeProcessHandler.evaluate` only uses the incoming commit to check whether anything was
added or modified. It then enumerates all observations and considers every resolved concrete
quality whenever any occurrence exists. A process-only consequence commit therefore reconsiders
the original qualities. Only writer/reuse checks prevent another submission; no trigger relevance
check distinguishes an original quality change from the consequence's own result.

The live client log confirms this in context `anonymous.3h4iftxkug`: activities **2749** and
**3563** request Elevation and Slope change; **4542/4726** repeat both under 2749, and
**5277/5450** repeat both under 3563. The corresponding CHANGE decisions report the same quality
IDs **14181/14182** as applied on the parent pass and both child passes. A controlled probe also
reproduces both unchanged qualities being submitted again after a process-only follow-up commit
when no writer/reuse guard accepts them.

The observed request means `change in geography:Elevation in m optional`, rather than plain
Elevation. `optional` is the present best-effort request policy. The evidence establishes redundant
requests, but does not establish that optionality alone causes the defect or that duplicate native
quality observations were created. The selected observer's `ObserverResolution.deferred` branch,
writer coverage, and typed resolver results need tracing together before selecting the repair.

Source: `ChangeProcessHandler.java:27–86`; `ObserverResolution.java:39`.

### A2 — P2: CHANGE reports unproved results as APPLIED

The same handler counts any nonempty result with a positive ID as applied. It does not require
resolved coverage, verify that the result is the requested change process, or inspect a typed
terminal resolution outcome before choosing its decision. A controlled result with zero resolved
coverage is counted as `APPLIED`. This repeats the distinction exposed by the earlier admission
failure: successful submission/native existence is insufficient evidence of scientific resolution.

This is a separate confirmed defect from A1. The live logs also show initial CHANGE resolver
`NO_MODEL` outcomes followed by later reference-like `RESOLVED` graphs; correlate those results
before concluding which particular live submission was falsely counted. A1's repeated activity
IDs alone cannot prove A2 caused every redundant request.

Source: `ChangeProcessHandler.java:80–97`. Compare the typed outcome handling in
`DefaultRoleConsequenceRuntime.submit` and `RuntimeService.prepareConfigurationExplanation`.

### A3 — P2: recomputation receipts and activity provenance disappear on persistence

`ConfigurationRecompute.recordReceipt` stores `klab.scheduler.configurationRecomputations` as a
nested map on the observation and `klab.scheduler.configurationInputChange` as a map on the
Activity. The native encoder's `sanitizeMetadata` retains only scalar POD values. The Activity's
special audit allowlist does not include this provenance key. Both values are therefore absent
from the encoded `_metadata`, despite being present in memory.

An isolated probe calls the actual native encoder and confirms both omissions. Existing helper
tests retain the in-memory objects and do not exercise a storage/reload boundary. Idempotence
and audit evidence can consequently differ between a warm cached object and a restored one.
This does not establish a durable crash-replay contract; that remains explicitly unsupported.

Source: `ConfigurationRecompute.java:363,377`; `AbstractKnowledgeGraph.java:106,233–258,420–431`;
`Utils.Data.isPOD` in `klab.core.api`.

### A4 — P2: birth ID allocation breaks receipt keys and in-flight cleanup

Before a new configuration has a durable ID, `ConfigurationRecompute` builds its receipt key
and indexes `configurationsInFlight` with the negative ID. Its release callback later reads
`configuration.getId()` again. When commit assigns a positive ID, cleanup removes that new key
and leaves the old negative-key entry holding the transaction. The stored receipt key and
input-change provenance also retain the original negative identity.

The probe runs the real helper, changes the birth ID from `-7` to `700` before the commit hooks,
and confirms both the orphaned `-7` in-flight entry and the old receipt key. Repeating the same
initial trigger/revision then returns `DEFERRED`, rather than `ALREADY_EXECUTED`. This is the
same before/after ID-allocation boundary recently repaired for collective producer bindings.

Source: `ConfigurationRecompute.java:233,352–377`. The same problem is not present in the
constant `inputChangesInFlight` cleanup key; distinguish the two registries when repairing it.

### A5 — P2: nested consequence scope creation loses handler cancellation

`ConsequenceEngine.evaluate` attaches engine-lifetime cancellation to its handler scope.
`GuardedConfigurationPublisher.publishBatch` and the role-conferral path subsequently call
`forConsequence` again. That method clears the entire cancellation supplier, including the newly
attached handler token. A probe using real `ServiceContextScope` objects confirms that the handler
becomes interrupted while the nested scope remains live.

The normal twin shutdown also closes `ResolutionCycles`, which supplies another cancellation
layer. That mitigates some shutdown paths; the probe does not establish graph disposal during
active work. The confirmed defect is the lost lifetime signal, especially for direct engine close
or future nested work. Detaching a completed user request is legitimate; detaching the active
handler's lifetime requires a different contract.

Source: `ServiceContextScope.java:141–154,337–347`; `ConsequenceEngine.java:228`;
`GuardedConfigurationPublisher.java:158`; `DefaultRoleConsequenceRuntime.java:701`.

## Verification boundary

- Existing targeted Maven run: **97 tests, 89 passed, 8 failed, no errors or skips**. All eight
  failures are in `PreparedConfigurationRecomputeTest`. Keep that failure visible.
- That fixture supplies lifecycle evidence only for the configuration; prerequisite observations
  receive Mockito's default `false` from the new currentness gate. In an isolated shadow copy,
  explicitly recognizing the fixture's durable inputs and exact transaction-owned staged inputs
  makes **20/20** prepared-configuration controls pass. Repository test sources were not changed.
  This isolates a fixture defect; it is not a passing result for the original Maven run.
- Isolated assertion probes confirm A1's handler path, A2's acceptance of zero coverage, A3's
  encoder omissions, A4's ID/cleanup mismatch, and A5's lost cancellation token. They use test
  collaborators; they do not prove full service-side configuration or cessation acceptance.
- Live evidence is read-only existing-log corroboration of the CHANGE repetition. No new live
  consequence, process, configuration, or cessation was initiated for this audit.

Local audit artifacts are in `review-evidence/consequence-audit/`: `ChangeAuditProbe.java`,
`ConsequenceAuditProbe.java`, their result files, `change-live-evidence.txt`, and
`existing-suite-results.log`. The directory is ignored by Git. This document retains the findings
and reproduction requirements independently; promote desired reproductions into normal tests
during Stage 0 rather than relying on ignored artifacts in a future checkout.

## Repair sequence

Implement the following as separate reviewable changes. Do not fix unrelated C1–C7 semantics,
restart a general Reasoner optimization effort, or weaken admission to make tests pass. Each stage
ends with tests of the production path and an update to this document's evidence, not just helper
assertions. Preserve the working cotechino admission and dependency-only input-plan fixes.

### Stage 0 — establish a trustworthy baseline

**Prompt**

> Read docs/CONSEQUENCE_ENGINE_ADMISSION_AUDIT.md and preserve the existing working-tree repairs.
> Repair PreparedConfigurationRecomputeTest's lifecycle setup: explicit durable fixture inputs may
> be current; a negative input must be the exact staged object owned by the active transaction.
> Retain unowned, failed, foreign, reference-only and wrong-perspective rejection controls. Replace
> broad mock shortcuts with a real native-transaction control where ownership is being tested.
> Promote the audit reproductions into focused regression tests, using real encoding and commit
> callbacks for A3/A4. Do not change production currentness rules. Run the original 97-test group
> and the previous admission/collective-input suite; record exact outcomes and remaining failures.

**Exit gate:** original prepared-configuration controls pass with explicit evidence; each planned
production repair has an independently failing regression. Keep tests scoped to their contracts.

### Stage 1 — stop redundant CHANGE discovery without suppressing legitimate change

**Prompt**

> Reproduce A1 using one grid-time bearer, Elevation and Slope, and the actual retained semantic
> builder. Correlate source commit, decision, requested operator/URN, result ID, coverage, typed
> outcome and writer/reuse rejection reason for the six logged requests. Determine why the first
> successful or NO_MODEL result fails to suppress its own process-only follow-up. Preserve optional
> best-effort behavior and observer-specific discovery. Bound candidate selection by relevant
> quality/occurrence changes, while retaining the necessary first-occurrence scan of old qualities.
> Use existing resolution/cycle ownership and reuse evidence; do not add a permanent negative cache,
> blanket-disable CHANGE, or suppress every process-origin commit. Test unchanged follow-up commits,
> a newly introduced quality, changed support, a new relevant occurrence, a joint writer for two
> qualities, observer-specific versus absolute results, and cycle drain.

**Exit gate:** unchanged qualities produce no repeated work from their own follow-up commits;
new relevant support or semantics can still trigger valid discovery. The actual request remains
a CHANGE process rather than an optional copy of the original quality.

### Stage 2 — make CHANGE decisions reflect actual resolution

**Prompt**

> Fix A2 in ChangeProcessHandler after Stage 1's result trace identifies the real contract.
> Evaluate the typed terminal outcome, result meaning, lifecycle/access, and actual support before
> declaring a change obligation fulfilled. Distinguish a genuinely complete reusable change/writer
> from native existence, a partial result, zero coverage, typed NO_MODEL, DEFERRED and failure.
> Keep ordinary optional absence nonfatal. Do not treat an absent executor or an empty generic
> response as a NO_MODEL certificate, and do not infer successful scientific execution from a
> positive ID. Add controls for wrong-result semantics and reused complete results. Keep decision
> observationIds' meaning explicit and compatible with the client. Rerun Stage 1's loop tests.

**Exit gate:** zero-coverage or unrelated results never become APPLIED; unchanged successful
reuse and genuine optional NO_MODEL terminate without redundant semantic submissions.

### Stage 3 — make recomputation evidence survive persistence and ID allocation

**Prompt**

> Repair A3 and A4 together in ConfigurationRecompute. Choose an explicit versioned persistence
> representation for recomputation receipts and input-change provenance that the actual native
> encoder retains. Define bounded legacy decoding for the current in-memory map form. Rebind new
> configuration identities, receipt keys and provenance after durable allocation, before native
> updates are encoded. Capture stable registration keys for cleanup; do not remove a transient-key
> entry using a later ID. Verify commit and rollback leave both in-flight registries empty. Preserve
> source/observer/cycle/native read-set authority: receipt metadata alone must not authorize execution
> or skip a required admission. Test a real birth through allocation, encoding, reload, duplicate
> trigger/revision lookup, changed revision, rollback and warm-cache versus restored behavior.

**Exit gate:** durable receipts and provenance round-trip with the durable identity; no old
negative-key registry entry retains a transaction; duplicate detection has a documented authority
boundary. This stage does not introduce durable crash replay.

### Stage 4 — preserve cancellation through nested consequence work

**Prompt**

> Fix A5 by distinguishing detachment of the completed user's request from derivation inside an
> already admitted handler. Preserve handler/engine and cycle-lifetime cancellation in nested
> configuration publication, role attribution, and membership-derived work. Audit every
> forConsequence call site rather than changing its reset behavior globally. A cancelled completed
> user request must not retroactively cancel valid post-commit consequences. Test engine close
> before nested transaction admission, cancellation during preparation, and draining of an already
> executing atomic operation. Retain bounded shutdown and cycle lease ownership; do not dispose
> storage/graph before active work drains or use forced interruption of synchronous Bolt I/O as a
> completion certificate.

**Exit gate:** nested scopes observe their handler's cancellation, new work cannot start after
close, and valid already-admitted work drains without leaking its cycle or storage ownership.

### Stage 5 — bounded live acceptance and documentation

**Prompt**

> Validate Stages 0–4 in the existing anonymous single-service-per-type configuration, using the
> established owner/client contracts. Preserve the live projects, worldview and exact service
> identities; do not launch competing services or change authentication. First rerun cotechino and
> confirm model discovery, dependency initialization and successful coverage. Correlate CHANGE
> parent/child decisions until cycle quiescence and prove the next explicit root can proceed. Then
> use a separately identified owned fixture to verify one configuration birth, same-ID input-change
> recomputation, durable receipt reload and rollback. Record source hashes, deployed versions,
> actual model execution and immutable earlier history. Obtain any deployment authority still
> required before changing the live stack. Update ADMISSION.md and consequence status with precise
> evidence; passing controlled tests does not establish the untouched cessation/network paths.

**Exit gate:** finite relevant CHANGE activity, correct decisions, complete cycle drain, preserved
cotechino resolution, durable configuration evidence, and an explicit list of unverified branches.

## Invariants for every stage

- Separate planning ownership, operative lifecycle, input-plan association, and scientific execution.
- Keep exact transaction, context, user, observer, semantic-cycle and source/read-set checks.
- Preserve temporary planning identities until allocation, then bind durable evidence deliberately.
- Dependency-only consumers retain input plans without acquiring fictitious executable operations.
- Preserve immutable history and atomic commit/rollback. Do not turn a durable result into failure
  because later diagnostics or delivery fail.
- Make no broader termination, replay, named-event, multi-root explanation or negative-emergence
  promise than the existing consequence contract.
