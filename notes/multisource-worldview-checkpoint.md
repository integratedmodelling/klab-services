# Incremental multi-project worldview ingestion: architecture checkpoint

Status: architecture checkpoint plus bounded registry implementation below. 2026-10-12.
Branch: `codex/multisource-worldview-design`.
Base: `3b01ceaa907000ffccafac97444029027c81d30b` (develop).
Worktree: `C:\Users\Ferd\Documents\Codex\2026-10-12\task-11\klab-services`.
Reviewed separately: `24c834490513c483a299a5f4a74e45c8ed7c5375`, and task-4's
`notes/worldview-ingestion-contract.md`. Neither was modified or cherry-picked.
The integration worker owns foundation integration. Recheck anchors after its merge.

## Decision boundary

A worldview is composed from multiple projects supplied by recorded Resources
services. Each project advances independently. Source arrival is incremental;
publication is one coherent, validated local revision over an explicit revision
vector. Neither a single incoming worldview object nor a distributed transaction
is required. Start with full candidate rebuilds from retained immutable project
contributions; optimize dependency-closed incremental compilation only later.

Keep invalid authoring content and source health outside the operational snapshot.
A failed, unresolved, missing-source or superseded candidate leaves the previous
coherent snapshot available, with its age and source status visible. Existing
authorization still governs access; retaining a snapshot is not permission to serve
revoked content. No live subscriptions, polling, service calls or mutations belong
to this checkpoint. No new language syntax or resolver semantics are proposed.

## Verified integration anchors

Paths below are relative to this worktree; line numbers refer to the base.

| Existing surface | Evidence and consequence |
| --- | --- |
| `klab.core.api/.../knowledge/organization/Project.java` | `Manifest.getDefinedWorldview()`, `getPrerequisiteProjects()`, `getVersion()`, `getPrivileges()`; project exposes ontologies/strategies and asset URN. `computeTimestamp()` is maximum document time, not an immutable revision. |
| `klab.core.api/.../services/KlabService.java:388` | `serviceId()` identifies the server instance; use it rather than URL or service name. Do not assume migration to a new instance preserves identity. |
| `klab.core.api/.../services/resources/ResourceSet.java` | Resource has serviceId, projectUrn, resourceUrn, version, timestamp, CRUD operation; enclosing services map supplies URLs. No first-class immutable revision, sequence, replay cursor or subscription lease. |
| `klab.core.api/.../services/ResourcesService.java:239` | `retrieve(urn, assetClass, UserScope)`; `resolve` supplies descriptors. Project locks at 410/419 are authoring locks, not snapshot reads or distributed ingestion locks. No expected-project-revision argument on retrieve. |
| `klab.core.api/.../knowledge/Worldview.java` | Flat ontology/strategy view and authority definitions; stable worldview ID is not a publication digest. Existing same-version documentation concerns compatibility, not a common source revision. Preserve it until explicitly changed. |
| `klab.core.api/.../knowledge/impl/WorldviewImpl.java:132` | `update` resolves resources and mutates the view using version/timestamp comparisons; unsuitable as the candidate registry. |
| `klab.core.api/.../lang/kim/KlabDocument.java:74`, `KimOntology.java:26` | Project name and imported ontology URNs support provenance/import edges; resolve project names against supplying service/project descriptors rather than treating names as globally unique. |
| `klab.services.resources/.../storage/WorkspaceManager.java:3531,4058` | Existing project dependency graph and worldview assembly; getWorldview includes multi-project contributions locally. Cross-service completion remains explicitly pending in its documentation. |
| Same file, 2435 | Sends `ResourceLifecycle / WorkspaceChanged` with ResourceSet through the initiating scope. Receiver fetches contents. This is a hint, not proven durable project subscription delivery. |
| `klab.core.api/.../services/runtime/Message.java:142`, `MessagingChannel.java` | WorkspaceChanged uses Queue.UI, which is absent from default MessagingChannel queues. Reuse existing channel transport only after authorized routing and queue negotiation; do not assume Reasoner receives it today. |
| `klab.core.services/.../services/application/controllers/WebsocketsBus.java` | Existing websocket transport; no need for a new global event bus. |
| `klab.core.api/.../services/Reasoner.java:909,934` | Existing `loadKnowledge(Worldview, Scope)` and `updateKnowledge(ResourceSet, UserScope)` remain compatibility entrances, adapted into capture/registry operations. A legacy flat view without verified project provenance cannot silently become a tracked subscription. |
| `klab.services.reasoner/.../ReasonerService.java:1656,1760` | load clears/reinitializes live state; update releases ontology/authority state before fetching replacements. 1895 tags live concepts; 2584 indexes during build. All must move behind candidate boundaries. |
| `klab.services.reasoner/.../ObservationReasoner.java:32` | Already accepts `Reasoner` and declaration function. Supply candidate facade and candidate callback; default constructor captures live service. Initialize strategies only after candidate ontologies. |
| `klab.services.reasoner/.../internal/AuthorityBindings.java:37` | configure invokes provider configuration, wraps persistent cache and codelist state. A fresh map alone does not isolate external provider state. |
| `klab.services.reasoner/.../internal/AuthorityIdentityResolver.java` | Bound to OWL and cache invalidation callback by ReasonerService:613. Both dependencies must be candidate-local. Lazy hierarchy creation also needs snapshot ownership. |
| `klab.services.reasoner/.../internal/AuthorityCodelists.java:243` | commit forces a temporary file then atomically replaces one file before changing state. Reusable persistence pattern, not a multi-file transaction. AuthorityCache's cache behavior is not a commit protocol. |
| `klab.services.reasoner/.../owl/OWL.java:273,332,2797,3083` | initialize resets graph and disposes reasoner; consistency and satisfiability APIs exist. Bootstrap/null reasoner can return permissive results; absence of a capable checker must not certify postconditions. |
| `klab.core.services/.../runtime/language/KimWorldviewValidator.java`, reviewed patch's `WorldviewDeclarationSupport`, `WorldviewDomainSupport`, `DescriptionBearerSupport` | Reuse settled validation rules; extract a pretranslation plan, since reviewed clause staging still allows class/index mutations before validation and swallowed child failures. |

Here `...` expands to `src/main/java/org/integratedmodelling/klab/api` for
klab.core.api, `src/main/java/org/integratedmodelling/klab` for klab.core.services,
and `src/main/java/org/integratedmodelling/klab/services/{reasoner,resources}`
for the respective service modules. `.agents` is empty in the inspected checkout;
the only AGENTS.md is docs/AGENTS.md (language guide, not a normative test oracle).

## Minimum registry and interface proposal

Names below describe contracts, not already available APIs. Keep public transport
DTOs in core.api only when reviewed; keep assembly/publishing inside Reasoner.

```text
SourceKey = (serviceId, projectUrn)
RevisionRef = (providerRevision?, contentDigest, digestSchemaVersion)
ProjectContribution = (SourceKey, RevisionRef, worldviewUrn, compatibilityVersion,
  immutableManifest, documentBlobsAndDigests, importEdges, prerequisites,
  authorityDeclarations, strategyDocuments, provenance)
SourceSubscription = (SourceKey, generation, endpointLocator, capabilities,
  mode, pollPolicy, opaqueCursor?, sequenceEpoch?, lastSequence?,
  authorizationRef, authorizationExpiry?, notificationLeaseExpiry?,
  lastSeenRevision?, lastCapturedRevision?, lastPublishedRevision?, status)
SourceRegistry = (generation, desiredSourceSet, contributionsByRevision, subscriptions)
CapturedVector = sorted Map<SourceKey, RevisionRef>
KnowledgeSnapshot = (publicationRevision, capturedVector, semanticView, owl,
  indexesAndCaches, domainMaps, sourceHashesAndTags, authorityState,
  observationStrategies, validationCertificate, durableManifestRef)
SourceAdapter.describe(key, authorizedScope) -> capabilities + current descriptor
SourceAdapter.capture(key, expectedRevision?, authorizedScope) -> capture | changed | unavailable
SourceAdapter.changes(key, cursor?, authorizedScope) -> hints + nextCursor | resyncRequired
SourceAdapter.requestNotifications(key, existingChannel, leaseRequest) -> lease | unsupported
CandidateAssembler.build(basePublication, registryGeneration, capturedVector) -> candidate | diagnostics
SnapshotStore.prepare(candidate) -> immutable durable version
Publisher.publish(expectedPublication, expectedRegistryGeneration, prepared) -> published | superseded
SnapshotAccess.acquire() -> AutoCloseable SnapshotLease
```

SourceKey is stable within the verified service identity and project URN. URL changes
do not change it. Service identity change/project rename is explicit replacement,
not automatic aliasing. Credential material never appears in the registry;
authorizationRef refers to existing scope/credential facilities. Check authorization
at capture and publication and gate reads through current access rules. Expiry,
revocation, disconnect and notification lease expiry are separate events. Renewal
must not broaden authorization. No indefinite persistence of a UserScope.

Capabilities explicitly advertise conditional/immutable capture, push availability,
replay, sequencing, cursor scope and retention. Polling is the baseline; interval,
jitter and bounded backoff are operational configuration, with clock-driven fakes.
Push requests use the existing authorized transport and fall back to reconciliation
polling. No use of the runtime actor event bus for this contract.

Every hint is scoped to SourceKey and subscription generation. Reject mismatched
service identity; ignore obsolete generations. Sequence comparisons require the same
provider epoch; gaps/expired cursors/restarts trigger complete reconciliation.
Without ordering guarantees use hints only as invalidations. Deduplicate captures by
key + revision/digest; acknowledge a capture cursor after durable inbox/capture storage,
not after publication. A rejected captured revision stays recorded with diagnostics;
it must not be lost or repeatedly treated as new. Published progress is separate.

## Capture and composition invariants

1. Fetch each project independently into retained immutable content. Digest the
   complete manifest, source bytes, document membership (including deletions),
   strategies, authority declarations, dependency declarations and relevant metadata,
   with deterministic ordering and a versioned encoding. Include validator, adapter,
   ODO/component versions and authority evidence revisions in the candidate certificate.
   Do not hash mutable Java bean identity or trust Version/timestamp as a content hash.
2. Existing retrieve lacks a verified atomic project capture guarantee. A future
   Resources adapter must return one internally coherent project capture under its
   own local read boundary, with a revision token/digest covering the payload; historical
   revision retrieval is optional if immutable content is retained locally. Repeated
   before/after timestamp reads cannot prove coherence (including ABA). Providers
   lacking this guarantee report CAPTURE_UNVERIFIED, never operational certification.
   This is a per-service capability extension, not a distributed lock.
3. Build dependency/import graph from project prerequisites, ontology imports,
   strategy references, domain/authority references and validated semantic references.
   Resolve every document to an explicit SourceKey in the captured vector. Preserve
   existing dependency ordering/cycle rules. Reject missing/incompatible dependencies,
   ambiguous duplicate namespace ownership and conflicting roots/bindings; never use
   last arrival as precedence. A mirror is not silently a second owner.
4. An update to A can use retained B at its published revision if constraints hold.
   If A requires B's new revision, retain A pending until it is captured and validated.
   No callback may fetch an uncaptured latest import during compilation. Evidence
   establishes coherence of this vector, not simultaneous currency at all services.
5. Desired registry changes invalidate in-flight publication. Source hints mark dirty
   and schedule capture; accepted captures advance registry generation. Compare both
   publication revision and registry generation at commit. A remote update not yet
   observed does not invalidate a coherent captured vector; report capture times and
   reconcile later instead of claiming a distributed commit.
6. Transient absence is not deletion. Explicit authorized removal creates a tombstone
   in desired membership; rebuild dependents before publishing removal. Missing required
   projects block that candidate. Replacement adds the new key and removes the old key
   in one desired-set generation; no silent partial switch. Keep prior revisions for
   current leases/recovery, subject to existing authorization and retention policies.

## Candidate isolation and publication

The candidate owns copied sources, semantic symbol/reference view, OWL manager and
reasoner, generated expressions, ontology/class maps, index, every semantic cache,
domain maps, source digests/tags, diagnostics, authority bindings and strategy state.
Audit mutable static/singleton dependencies before enabling publication. A snapshot
facade must route all recursive declaration/resolution calls to that snapshot. Frozen
source beans cannot be shared with current WorldviewImpl's mutation path.

Preflight k.LAB source/reference/type/domain/bearer/value obligations before OWL
translation using a candidate semantic view. Child failure propagates to candidate
failure. UNAVAILABLE/UNRESOLVED required typed evidence is not a valid certificate.
Translate only the accepted plan. Then run OWL global consistency and satisfiability
for all required named concepts, excluding the intentional bottom class; timeout or
unavailable DL checking is unresolved. Failure after accepted preflight is
VALIDATION_TRANSLATION_BUG, not an alternative definition of valid input.

Authorities require a side-effect-free binding plan plus immutable evidence. Do not
call live provider configure/release or write production authority caches from a
candidate. If validation needs unavailable provider evidence, return unresolved.
Future activation needs independently scoped provider handles or a validated pure
adapter; a provider that mutates shared configuration is a blocking integration gap.
Snapshot-local lazy graph/cache work must be serialized within that snapshot or
materialized before publication; it cannot cross revisions. Codelist mutations need
their own revision capture/CAS participation, not mutable shared binding references.

Use versioned publication: write/force all candidate artifacts beneath a unique
immutable revision directory, then prepare a checksummed complete manifest. Under
the ingestion/publication lock recheck expected publication, registry generation,
authorization generation and validation evidence. Atomically replace ONE durable
current-manifest pointer, then install the corresponding in-memory snapshot before
releasing the reader-acquisition gate. Pointer update is the durable commit point.
Source tags, indexes and persistent configuration consumed by readers must live in
that version, not independent best-effort callbacks. Atomic replacement unavailable
means failure; do not downgrade silently. Verify Windows/filesystem crash durability
and restart behavior before claiming durable atomicity. Existing single-file ledger
pattern is reusable, but its guarantees do not extend automatically to this store.

If pointer outcome is uncertain, reconcile it under the gate; do not report rollback
while storage may name the new revision. Recovery loads only the complete manifest
referenced by the durable pointer and revalidates/rebuilds executable state before
serving. Incomplete staging is never live. No distributed source writes participate.

Acquire reader leases under the same gate as the pointer install, incrementing the
selected snapshot's reader count before publication can retire it. All query work,
recursive calls and async continuations retain that lease; no per-field global reads.
On release decrement; retire the previous reasoner/index/provider handles only after
the last lease. Bound retained generations and apply backpressure, never force-dispose
an active reader's reasoner. Readiness describes the published snapshot; failed
candidate diagnostics must not flip a healthy old snapshot into partial maintenance.

Expose candidate ID, base/attempted vector, dependency chain and lexical location,
phase and diagnostic code, capture/check/publication times and retries. Minimum
statuses: CURRENT, DIRTY, STALE, MISSING, AUTH_EXPIRED, REMOVED_PENDING,
CAPTURE_UNVERIFIED, REJECTED, UNRESOLVED, SUPERSEDED. Distinguish source health from
snapshot validity. Diagnostics must not expose credentials or unauthorized source text.

## Bounded implementation sequence and executable acceptance plan

1. Review this design and coordinate with integrated foundation. Add immutable registry
   DTOs and fake SourceAdapter; test defensive copies, same project name on two
   services, URL relocation, identity replacement, deterministic digest, content
   change at equal timestamp, tombstones and epoch/cursor dedup.
2. Add Resources coherent per-project capture capability and reuse existing channel
   negotiation. Fake provider/clock tests: push unavailable, dropped/duplicate/out-of-order
   hints, cursor expiry, auth/lease expiry, revoke during capture, ABA during reads.
   No real subscriptions or polling in tests.
3. Extract candidate semantic/preflight and snapshot-bound authority/strategy facades;
   retain reviewed clause rules. Test valid A plus failing B/child/import/domain/value
   leaves every old graph/index/hash/tag/binding/strategy unchanged; spy callbacks must
   fail if the live Reasoner or provider configuration is invoked.
4. Implement versioned store, gated CAS publication and leases with fault injection.
   Test crash before/after pointer replacement, failed force/rename, uncertain outcome,
   restart, competing candidates, source/codelist/auth generation race, reader spanning
   publication and last-reader retirement. Check full state, not only ontology axioms.
5. Adapt loadKnowledge/updateKnowledge and migrate query paths to leases. Multi-service
   test: A2 arrives before required B2; old vector remains queryable; B2 arrival produces
   exactly one coherent new publication. Also test strategy-only change, deletion with
   dependents, ambiguous namespace providers and no distributed commit assumption.

Future focused tests should run in the existing API/common/Reasoner/Resources test
modules alongside Worldview*, OWLSemanticClauseSupportTest and
KimValueMappingValidatorTest. Run the repository review selection from
run-review-tests.ps1 plus the new tests through the Reasoner/Resources reactor;
then applicable aggregate `mvn -o -pl klab.services.reasoner,klab.services.resources -am test`,
using a task-local Maven head repository/test home and read-only shared dependency tail.
Report blocked, selected pass and aggregate failures separately. Do not inherit a
historical pass from patch24 or label an unrun suite passed.

## Review choices and checkpoint verification

No new semantic choice is required to implement the baseline above. Existing
compatibility-version rules and dependency/cycle rules remain unchanged. Optional
future policy choices are explicit precedence/failover between duplicate providers,
relaxing compatibility versions, and a maximum staleness age that disables otherwise
coherent authorized reads; this proposal adds none implicitly. Interval/backoff and
retention are engineering configuration, not language decisions.

Delivery changes only this note. No DTO scaffold was added because revision capture,
provider side effects and durable publication must be reviewed together before their
interfaces are frozen. Focused tests above are acceptance specifications, not executed
tests. Java/Maven aggregates were not run for a documentation-only checkpoint; no
runtime validation pass is claimed. Repository anchor checks and whitespace validation
are recorded in the task's completion report. Remaining implementation blockers are
coherent capture API, side-effect-free authority evidence/activation, complete reader
lease migration, proven durable pointer semantics, and integration of the reviewed
foundation. These are concrete engineering work, not requests for new language syntax.

## Authorized stage 1 increment (supersedes documentation-only delivery status)

Architecture review approved the registry/fake-capture boundary. Added internal
`worldview/SourceRegistry.java` and `worldview/SourceRegistryTest.java` in the Reasoner
module. Nothing is wired to the live service. API identities are copied from
ResourceSet.Resource, CRUDOperation describes hints, and the adapter uses UserScope.
No public core API change, foundation edit, provider activation or network operation.

Registry transitions are immutable: register/relocate/remove, accept hints, accept a
coherent capture or explicit unavailable/mismatch result, compute a complete captured
vector, and acknowledge an externally validated/published vector with generation
guards. The acknowledgement is bookkeeping, not an implemented atomic publisher.
Callers must serialize/CAS registry installation. Captured and published revisions
remain separate; rejected input does not replace published state. Retained revision
identifiers are not themselves a blob store or an authorization grant.

Conditional capture's expected revision means "capture the current project only if
still this revision". It returns coherent payload plus revision together, Mismatch,
or Unavailable. Historical provider reads are not required. Adapters without coherent
capture cannot produce a resolved state even if they return a payload. The optional
local digest helper uses sorted, length-delimited envelope entries and SHA-256; the
adapter remains responsible for a complete envelope (including dependency declarations)
and verification of any other provider encoding. Timestamp is never content identity.

Document deletion changes the complete capture's content/membership. A DELETE hint
only invalidates/reconciles. Confirmed provider project deletion is an unavailable
source state. Only explicit desired-membership removal produces a tombstone. Required
dependency loss prevents a complete candidate vector. Two services with the same
project name stay distinct; namespace ownership ambiguity remains for later semantic
composition, with no implicit precedence or failover.

Review narrowed this stage: replay cursor protocols, notification leases, durable
publication, authority/provider lifecycle and candidate graphs remain proposals above,
not frozen interfaces in the implementation. Fresh authorization must gate future
publication and every read admission; a lifetime lease will only prevent disposal.
No polling interval, lease duration, staleness cutoff or provider priority was chosen.

Tests cover immutable maps, service/project identity, endpoint relocation, multi-source
dependency arrival, retained publication, deduplication, epoch/gap handling, coherent
capture unavailability, rejection/auth expiry, conditional mismatch, deletion distinctions,
deterministic digests, equal-timestamp content changes, and stale generation guards.
Execution evidence is reported separately; initial sandbox compilation could not read
the shared cached JUnit jar and was retried with authorized offline execution, task-local
Maven head repository and test home. No shared dependency/security configuration changed.

Final stage-1 verification:

- `../registry-focused-authorized.log`: BUILD SUCCESS, 15 tests, no failures/errors/skips.
- `../registry-review-aggregate.log`: BUILD SUCCESS, 117 tests, no failures/errors/skips
  (2 API, 7 common, 29 Reasoner including the 15 new tests, 79 Resources).
- `git diff --cached --check`: passed. Only the two new Java files and this note changed.
- The unrestricted all-tests aggregate was not run for this isolated increment; the
  selected aggregate below is the actual scope of the successful result.
- No verification process remains active. Main checkout still has only its original
  four untracked review-evidence directories; reference patch/worktree untouched.

Commands (PowerShell argument quoting required for the test patterns):

```text
mvn -o -pl klab.services.reasoner -am test -Dtest=SourceRegistryTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -o -pl klab.services.reasoner,klab.services.resources -am test -Dtest=SourceRegistryTest,Worldview*,OWLSemanticClauseSupportTest,KimValueMappingValidatorTest,WorkflowSchemaTest,WorkflowManagerAuthorizationTest,ProposalReviewProtocolTest,ProposalDocumentTest,IsolatedProposalCandidateValidatorTest,ProposalSchemaValidatorTest -Dsurefire.failIfNoSpecifiedTests=false
```

Both successful commands also used:

```text
-Dmaven.repo.local=C:/Users/Ferd/Documents/Codex/2026-10-12/task-11/m2
-Dmaven.repo.local.tail=C:/Users/Ferd/.m2/repository
-DargLine=-Duser.home=C:/Users/Ferd/Documents/Codex/2026-10-12/task-11/test-home
```
