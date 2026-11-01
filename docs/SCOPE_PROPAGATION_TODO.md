# Scope propagation and lifecycle: staged implementation plan

Status: remaining work from the scope lifecycle audit of 2026-10-09.

The immediate concern is long-lived production services handling hundreds or more foreign
scopes. A Reasoner can retain users, messaging connections and service infrastructure without
ever reconstructing a context. Testing must therefore cover user-only traffic separately from
sessions and digital twins.

This document is a sequence of implementation stages. Each prompt can be used to start a
separate implementation task. Run the stages in order; recheck the current source before each
stage because the audit describes a changing working tree. Completion criteria below are
requirements, not claims that the work has already been done.

## Baseline to preserve

The audit changes already address:

- Retaining advertised topology across request-local user-scope copies, rebuilding clients with
  the current request's identity, and replacing withdrawn services in subsequent requests.
- Preventing cached advertised status from repeatedly overriding a monitor's later offline result.
- Retiring service monitors after their last client disappears, removing canceled polling tasks,
  and keeping polling alive after listener or dispatch failures.
- Recovering from rejected advertisement dispatch and invalidating queued deliveries at shutdown.
- Stopping scope maintenance and releasing local messaging and foreign-twin recovery resources at
  shutdown, without disposing hosted twins or deleting shared exchanges.
- Cleaning up partially opened AMQP connections and continuing transport closure after queue
  cleanup failures.

The focused regression suite passed 35 tests, including 1,000 topology replacements. These are
unit/regression results, not evidence of a production memory plateau or a completed soak test.
Existing workspace changes outside this work must be preserved.

## Stage 1 — Define ownership, identity and lifecycle contracts

**Purpose:** establish the rules needed to make expiration safe, rather than adding a timeout to
the existing registry.

The current service-side registry uses usernames and bare scope IDs. Expiration and logout are
placeholders. Engines stop retrying advertisements after acknowledgment. These behaviors cannot
be changed independently without risking lost topology, incorrect identity reuse or interrupted
work.

**Completion criteria**

- Document ownership of managed user scopes, request copies, clients, monitors, messaging
  transports, sessions, foreign twins and hosted twins.
- Define separate operations for local peer eviction, user logout, service shutdown and explicit
  twin deletion, including behavior when resources are shared.
- Define principal, origin-runtime and advertising-engine identity, including multiple engines
  logged in as the same user and identical session IDs on different runtimes.
- Define how active requests, jobs, agents and child scopes prevent premature cleanup.
- Record compatibility and restart behavior, configurable limits, and the required metrics.

**Implementation prompt**

```text
Establish the scope ownership and lifecycle contract for k.LAB services. Read
docs/SCOPE_PROPAGATION_TODO.md and inspect ScopeManager, ServiceUserScope,
ServiceSessionScope, ServiceContextScope, ServiceAuthorizationManager,
EngineAuthorization, EngineImpl, ScopeAdvertisements, ServiceClientCatalog,
MessagingChannelImpl, AMQPChannel and ClientScopeManager.

Trace construction, ownership, copying, registration, advertisement, refresh and destruction.
Distinguish a cached logical scope from a request's credential-bearing view. Identify all
strong references and scheduled tasks that can retain either. Cover Reasoner user-only
traffic as well as runtime and foreign context paths.

Write the lifecycle contract into the repository's documentation. Specify states and
transitions for advertisement acceptance, renewal, expiry, draining, eviction, logout and
shutdown. Specify which operations may delete durable data. Resolve identity boundaries
for partner-qualified users, origin runtimes and multiple engines advertising for one user.
Document how live requests/jobs/children pin resources and how those pins are released.

Define a compatible lease/re-advertisement protocol, including service restarts, old clients,
network partitions, delayed messages and reordered acknowledgments. Choose justified,
configurable limits rather than silently introducing fixed expiration. Identify any policy
decision that cannot be established from existing contracts. Do not enable eviction yet.
Preserve existing workspace changes and distinguish observations from proposed behavior.
```

## Stage 2 — Isolate registry keys and coordinate reconstruction

**Depends on:** Stage 1's identity contract.

**Purpose:** prevent scopes from different origins from aliasing, and prevent duplicate
construction from leaving discarded peers and recovery tasks alive.

**Completion criteria**

- Service-side lookup distinguishes origin runtimes and the required principal namespace.
- Cached hits validate the expected type, origin and caller authority.
- Concurrent misses for the same logical scope share one construction; unrelated scopes can
  progress independently.
- Failed construction and replacement release only resources they own.
- Coordination structures themselves have bounded retention.

**Implementation prompt**

```text
Implement the registry identity and reconstruction rules established in Stage 1 of
docs/SCOPE_PROPAGATION_TODO.md. Inspect both ScopeManager's foreign reconstruction path
and RuntimeService's durable reconstruction path, plus all registration and release callers.

Replace ambiguous internal keys with keys that include the required origin and principal
namespace. Preserve runtime-owned wire IDs unless an explicitly versioned protocol change
is necessary. Define behavior for APIs that provide insufficient identity information;
never silently return a scope from a different runtime or principal.

Coordinate concurrent construction per logical scope without holding a global lock across
network requests or storage operations. Publish only fully initialized peers. Make failure,
retry, replacement, close and shutdown races explicit. A losing or replaced instance must
release its own local resources without disconnecting a shared transport, disposing the
winning instance's twin, or deleting remote data. Avoid an indefinitely growing lock map.

Add deterministic concurrency tests for simultaneous first access, construction failure and
retry, shutdown during construction, and replacement while requests are active. Include the
same default session ID on two runtimes and colliding usernames in distinct identity
namespaces. Verify that unrelated scopes are not serialized behind a slow reconstruction.
Document migration and compatibility behavior and run the relevant regression suites.
```

## Stage 3 — Implement advertisement leases and resynchronization

**Depends on:** Stages 1–2. Must precede automatic eviction.

**Purpose:** allow a service to forget an inactive peer without permanently losing the topology
needed when its engine returns.

**Completion criteria**

- Advertisements have explicit ownership, ordering and renewal semantics.
- Restart or missing/expired topology triggers a bounded resynchronization path.
- Old acknowledgments and delayed advertisements cannot revive superseded state.
- Same-user engines have defined isolation or reconciliation behavior; one engine cannot
  accidentally withdraw another engine's live topology.
- Withdrawn destinations and abandoned retries are released according to the contract.
- Renewal does not overwrite independently polled status or reuse stale credentials.

**Implementation prompt**

```text
Implement the advertisement lease and resynchronization protocol specified in Stage 1 of
docs/SCOPE_PROPAGATION_TODO.md, using Stage 2's identity keys. Inspect UserScopeNotification,
EngineImpl, ScopeAdvertisements, BaseServiceClient.notifyScope, KlabScopeController and
ServiceUserScope, and update the relevant API contracts and clients together.

Provide an explicit way to renew topology and recover after server restart, expiration or
missing cached state. Define ordering using revisions/incarnations or an equivalent scheme;
do not trust arrival order alone. Keep topology metadata separate from request credentials
and live service status. Preserve the current rule that an older acknowledgment cannot
discard a newer pending advertisement.

Handle multiple engines for one user, topology withdrawal, duplicate delivery, offline
destinations, empty topology where meaningful, and abandoned engine instances. Bound pending
state and retries without silently dropping topology required for active work. Use bounded
backoff and jitter where appropriate. Define behavior for old peers that lack lease support.

Test restart with unchanged service IDs, lease renewal and expiry, reordered messages,
duplicate advertisements, lost acknowledgments, concurrent same-user engines, token rotation,
network partitions and shutdown during delivery. Use a controllable clock and transport;
avoid long sleeps. Leave automatic eviction disabled until Stage 4 completes.
```

## Stage 4 — Bound user retention and implement safe eviction/logout

**Depends on:** Stages 1–3.

**Purpose:** address the primary remaining Reasoner retention path: an unbounded population of
distinct managed users, independent of context creation.

**Completion criteria**

- Activity tracking, idle expiry and capacity policies are implemented and configurable.
- Live work is pinned; exhausted capacity has explicit admission behavior.
- Eviction and logout close owned local resources and release registry entries reliably.
- Cleanup is idempotent and failure in one resource does not strand unrelated resources.
- Returning users restore topology through Stage 3 rather than relying on stale clients.

**Implementation prompt**

```text
Implement bounded service-side scope retention using the contracts and lease protocol in
Stages 1–3 of docs/SCOPE_PROPAGATION_TODO.md. Prioritize a Reasoner serving user-only traffic.
Inspect the empty ScopeManager.expiredScopeCheck and logout implementations, user messaging
setup, JobManager, request authorization and the paths that retain child scopes or agents.

Track activity with an appropriate monotonic clock and implement configurable idle and
capacity policies. Pin active requests, jobs, agents and children according to the contract;
release pins on success, failure, cancellation and timeout. Do not equate an old last-access
timestamp with permission to interrupt ongoing work. Specify admission behavior when all
retained entries are active and the configured capacity is reached.

Implement local eviction, logout and shutdown as distinct operations. Releasing a foreign
peer must not delete its remote digital twin, stop a shared service process, or close another
scope's live transport. Ensure cleanup continues after individual failures and is safe to
repeat. Prevent new registrations from surviving a shutdown/drain race. Avoid network work
under the global registry lock and keep cleanup concurrency bounded.

Add fake-clock and concurrency tests covering hundreds of distinct users, repeated same-user
requests, idle eviction, active long-running work, full capacity, logout with children,
cleanup exceptions, and reconnect after eviction. Verify registry counts, messaging resources,
jobs and scheduled tasks return to the expected baseline. Test user-only behavior separately
from hosted-twin persistence and deletion behavior.
```

## Stage 5 — Refresh membership, permissions and foreign configuration

**Depends on:** Stages 1–4.

**Purpose:** separate availability refresh from authority and configuration freshness. Polling a
service's status does not refresh a user's membership or a foreign context's access rights.

**Completion criteria**

- Each cached authority/configuration field has a source of truth and invalidation policy.
- Group/federation changes and context ACL revocations reach cached peers within a defined bound.
- Current request credentials remain isolated from cached metadata and other requests.
- Failure to reach an authority has explicit behavior appropriate to the operation.
- Refreshes are coordinated and cannot publish older data over newer data.

**Implementation prompt**

```text
Implement authority and configuration refresh for cached scopes, following
docs/SCOPE_PROPAGATION_TODO.md. Trace initial user identity creation, forAuthorization,
federation membership, allowsManagedScope, foreign context reconstruction and runtime
getConfiguration. Identify which claims come from verified tokens, the hub, or the host
runtime; do not substitute untrusted request headers for authority.

Define and implement refresh/invalidation for group membership, federation membership and
foreign context configuration/ACLs. Keep this separate from service availability polling and
advertisement renewal. Use revisions/events where supported, with a bounded fallback freshness
policy where needed. Coordinate refreshes so concurrent requests do not create a stampede.
Prevent delayed responses from overwriting newer state, and release resources withdrawn by
the refreshed topology or authority when safe.

Specify behavior during authority outages, especially for revoked access and privileged
operations. Never let a successful historical lookup grant indefinite access. Preserve
request-local tokens and roles and avoid retaining expired credentials through clients or
background tasks. Recheck authorization at the appropriate operation boundary.

Test membership removal, federation changes, ACL revocation while the peer remains cached,
token rotation, concurrent callers, reordered refresh responses and authority outages. State
the maximum supported revocation delay and any compatibility limitations in documentation.
```

## Stage 6 — Make polling and refresh scale under slow peers

**Depends on:** Stages 1–5.

**Purpose:** avoid global refresh starvation. At audit time, service monitors shared ten blocking
workers and HTTP requests inherited a 300-second default timeout.

**Completion criteria**

- Status probes have an explicit timeout and scheduling policy separate from application calls.
- Slow peers cannot monopolize refresh or prevent timely orphan cleanup for healthy peers.
- At most the intended number of requests runs per endpoint and per process.
- Background work is bounded, cancelable and observable, including failure/backoff behavior.
- Endpoint identity changes do not reuse an incompatible monitor silently.

**Implementation prompt**

```text
Harden service monitoring for hundreds of foreign peers. Read the previous stages of
docs/SCOPE_PROPAGATION_TODO.md and inspect ServiceClientCatalog, BaseServiceClient, HTTP
client ownership, polling settings and all callers that force immediate status refresh.

Introduce explicit status-probe deadlines, bounded concurrency and scheduling that prevents
slow/unreachable endpoints from starving healthy peers or delaying cleanup indefinitely.
Choose an implementation justified by the workload; moving every poll to an unbounded task
queue is not an acceptable fix. Prevent overlapping probes for the same endpoint, coordinate
forced refresh with scheduled refresh, and use bounded backoff/jitter for failures.

Audit monitor keys and reuse when a service changes URL, ID, type or incarnation. Define how
old monitors drain and release their HTTP resources without disrupting clients that still
own them. Preserve weak client ownership, conditional catalog removal and cancellation
cleanup. Ensure callbacks cannot block the entire monitoring system or stop future polling.

Add deterministic tests with hundreds of simulated peers, including slow, failing, flapping
and healthy endpoints. Assert concurrency limits, fairness, cancellation, no overlapping
polls, and retirement after the last client disappears. Measure refresh latency and queued
work; document the supported bounds and configuration rather than asserting scale from a
single successful poll.
```

## Stage 7 — Bound foreign context state and history recovery

**Depends on:** Stages 1–6.

**Purpose:** bound retained graph/history state as well as cached assets. The audit found bounded
asset caches alongside growing graph, adjacency and commit-history collections, with periodic
history scans per foreign twin.

**Completion criteria**

- Foreign peers have an explicit memory budget and local retention policy.
- Graph indexes, commit deduplication and histories remain correct when data is evicted.
- Durable history remains recoverable without treating a local cache as authoritative storage.
- Recovery work is bounded and does not rescan all history indefinitely at a fixed rate.
- Cleanup and reconstruction preserve persistent hosted twins and their storage.

**Implementation prompt**

```text
Bound memory and background recovery for foreign context peers. Read
docs/SCOPE_PROPAGATION_TODO.md and inspect ServiceContextScope, ClientDigitalTwin,
ClientKnowledgeGraph, TransitionHistory, transaction/provisional-observation maps and runtime
configuration/storage reconstruction. Check which state is authoritative and which can be
retrieved again before introducing eviction.

Implement explicit budgets for retained graph structures, adjacency markers, asset caches,
commit deduplication and transition history. Evict dependent indexes coherently; an entry
must not remain marked as fully loaded after its supporting graph data has been discarded.
Provide on-demand paging or durable backing for history that must remain available. Ensure
completed/aborted transaction state is released on every terminal path.

Replace unbounded repeated history scans with bounded recovery work and shared scheduling
where appropriate. Account for late commits and out-of-order visibility: a simple largest-ID
cursor is insufficient unless the storage contract guarantees it. Preserve idempotency and
the ability to recover after missed messages, eviction and service restart.

Test hundreds of foreign peers, histories larger than all configured budgets, late commits,
reordered/duplicate events, aborted transactions, cache misses after eviction and repeated
connect/disconnect. Verify both bounded retained state and equivalent observable results.
Ensure local disposal, timeout and shutdown never delete a persistent twin on its host.
```

## Stage 8 — Add lifecycle diagnostics and production-scale soak tests

**Depends on:** Stages 1–7.

**Purpose:** verify retention and refresh behavior under sustained load rather than relying on
short-lived unit tests.

**Completion criteria**

- Metrics distinguish managed scopes, active request views, pinned/idle entries, jobs, transports,
  clients, monitors, queued work, pending advertisements and foreign history/cache sizes.
- Instrumentation does not create its own high-cardinality retention problem or expose tokens.
- Repeatable tests cover same-user churn and distinct-user growth separately.
- Evidence includes steady-state and post-cleanup measurements, not only peak throughput.
- Failure thresholds, workload parameters and remaining limitations are recorded.

**Implementation prompt**

```text
Build a repeatable lifecycle/soak verification harness for the implementation described in
docs/SCOPE_PROPAGATION_TODO.md. Add operational diagnostics for retained scopes and their
resource ownership, refresh ages, lease state, cleanup outcomes, polling latency and queued
work. Prefer bounded aggregate labels; do not put usernames, tokens or arbitrary scope IDs
into unbounded metric dimensions. Protect any detailed diagnostic endpoint appropriately.

Run separate workloads for: repeated requests and topology changes by a small fixed user
population; hundreds or thousands of distinct user-only scopes on a Reasoner; and many foreign
contexts with growing histories. Include token rotation, engine disappearance, service
restart, network partitions, slow peers, active long-running jobs, logout and shutdown.

Measure heap retention, thread/task counts, broker connections/channels, registry sizes,
pending deliveries and refresh/reconstruction latency across several activity/expiry cycles.
Use actual integration components where needed to validate HTTP and broker cleanup, in
addition to deterministic unit tests. Distinguish temporary allocation churn from retained
objects and document dominator/reference paths for unexpected growth.

Define thresholds from the configured budgets and expected live workload. Verify a plateau
for a fixed population and return to the expected baseline after churn and cleanup. Confirm
authorization revocation behavior and persistent-twin survival throughout. Record commands,
environment, workload, duration, results and unresolved limits in reviewable repository
evidence. Do not describe a short unit test as a production soak test.
```

## Stage 9 — Compatibility rollout and operational guidance

**Depends on:** Stage 8's evidence.

**Purpose:** enable the new lifecycle policies without silently breaking older engines or
changing durable storage semantics.

**Completion criteria**

- Mixed-version behavior is tested and documented.
- Defaults and supported capacity are justified by evidence.
- Operators can detect missing renewals, capacity pressure and stalled cleanup.
- Rollback behavior preserves durable twins and makes any loss of transient state explicit.
- Remaining deferred work is listed with concrete scope and risk.

**Implementation prompt**

```text
Prepare the scope lifecycle changes for a compatible rollout using the evidence from
Stages 1–8 of docs/SCOPE_PROPAGATION_TODO.md. Review protocol versioning, lease negotiation,
registry migration, expiration defaults, capacity limits and diagnostic coverage.

Test new engines with old services and old engines with new services wherever compatibility
is claimed. Define behavior when leases or re-advertisement are unsupported; do not silently
enable eviction for a peer that cannot recover. Document rollout ordering, configuration,
capacity/admission behavior, operational alerts, troubleshooting and rollback implications.

Verify that restart, upgrade and rollback preserve durable twins and their access controls.
Document any transient scope state that cannot survive a version transition. Select defaults
from measured results, record known limits, and retain clear separation between disconnect,
logout, expiry and deletion in the public API and operator guidance.

Produce a final review checklist tied to tests and measured evidence, and update the stage
status in this document. Prepare code and documentation for review; do not deploy or change
production configuration as part of this task.
```

## Source map

Paths below are relative to the repository root.

| Area | Primary files |
| --- | --- |
| Scope registry and request views | `klab.core.services/src/main/java/org/integratedmodelling/klab/services/scopes/{ScopeManager,ServiceUserScope,ServiceSessionScope,ServiceContextScope}.java` |
| Advertisement receiver | `klab.core.services/src/main/java/org/integratedmodelling/klab/services/application/controllers/KlabScopeController.java` |
| Authorization | `klab.core.services/src/main/java/org/integratedmodelling/klab/services/application/security/{ServiceAuthorizationManager,EngineAuthorization}.java` |
| Advertisement sender | `klab.core.common/src/main/java/org/integratedmodelling/common/services/client/engine/{EngineImpl,ScopeAdvertisements}.java` |
| Advertisement payload | `klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/runtime/objects/UserScopeNotification.java` |
| Client monitoring | `klab.core.common/src/main/java/org/integratedmodelling/common/services/client/{ServiceClientCatalog,BaseServiceClient}.java` |
| Messaging ownership | `klab.core.common/src/main/java/org/integratedmodelling/common/authentication/scope/{MessagingChannelImpl,AMQPChannel}.java` |
| Client scope registry | `klab.core.common/src/main/java/org/integratedmodelling/common/services/client/scope/ClientScopeManager.java` |
| Foreign twin state | `klab.core.common/src/main/java/org/integratedmodelling/common/services/client/digitaltwin/{ClientDigitalTwin,ClientKnowledgeGraph,TransitionHistory}.java` |
| Runtime persistence | `klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/RuntimeService.java` and its `digitaltwin`/`neo4j` packages |

For every implementation stage: preserve unrelated changes, update affected API documentation,
run focused tests for the changed behavior, and report what was verified and what remains
unproven. Use deterministic clocks and synchronization for unit tests; reserve timed workloads
for integration and soak verification.
