# Service coordination and discovery

Status: architectural description and proposal, based on the working tree on 2026-09-20.
Section 1 describes the existing implementation, including the scope propagation fixes made
on that date. Section 2 proposes new contracts and components; these are not implemented.
This document does not authorize replacing the current discovery mechanism.

## 1. Current service discovery and scope propagation

### 1.1. Retain the existing mode

The current mechanism is appropriate for small, bounded networks whose services are known at
authentication time. It must remain implemented for the time being and remain a supported mode
for local development, sandboxed networks, isolated installations, and testing. Such networks
must not acquire a dependency on a production coordination cluster.

The Hub supplies the authenticated identity and advertised service references. The Engine adds
its configured local service endpoints and monitors the resulting service clients. Service-side
user scopes receive an authorized peer catalog from the Engine. Services then communicate
directly over HTTP and use scope-aware messaging through AMQP.

There is no authoritative, continuously maintained network-wide registry in this mode. The
Hub's authentication response is a bootstrap catalog, not a lease or a proof of current readiness.
See [Services](SERVICES.md), [Scopes](SCOPES.md), and [Architecture](ARCHITECTURE.md).

### 1.2. Discovery, readiness, and user-scope advertisements

The principal steps are:

1. Authentication establishes the user's identity, privileges, federation information, and
   service references. Local user-owned services can reuse the Engine's local authentication
   package; networked services also have their own service identity and authentication path.
2. `ServiceMonitor` creates clients for configured local endpoints and, when enabled, remote
   references. `ServiceClientCatalog.ClientMonitor` polls `/public/status`. Service identity,
   connectivity, availability, and operational readiness are distinct properties.
3. Once the Engine becomes operational outside a transition, it builds a `UserScopeNotification`.
   The payload includes known service IDs, URLs, types, and status snapshots. Incomplete entries
   are skipped. Sending to a remote service excludes localhost services, which would otherwise
   refer to the receiver's own machine.
4. The Engine posts the notification to `/notifyUserScope` on its usable service peers.
   `KlabScopeController` obtains the authenticated service-side user scope and adds personalized
   clients for the advertised services. The hosting service is used directly when its ID matches.
5. Subsequent operational service-status changes cause the Engine to refresh the advertisement
   across ready peers, including peers already notified. `ScopeAdvertisements` retains the latest
   pending payload per target, serializes deliveries to that target, and retries unsuccessful
   deliveries every five seconds. An older acknowledgement cannot discard a newer pending payload.

The last step repairs incomplete startup snapshots and failed delivery; it is not a general
membership protocol. Advertisements add or replace service entries rather than reconcile an
authoritative desired catalog with removals. They carry no network-wide revision, expiry, or
ownership generation. Status polling makes unavailable entries unusable, but does not establish
that they have permanently departed.

Service-side typed lookups currently accept operational services, or local services that are
available. A local polling failure can mark a client offline until a successful refresh. Each
process has its own cached view: a ready service can temporarily be unusable from another
process. `ServiceUserScope.validateServices()` currently logs validation and returns true; the
notification acknowledgement does not prove that every advertised dependency is reachable.

### 1.3. Scope identity and propagation

Scopes form a hierarchy: user, session, context, and contextual views focused on observations,
observers, relationships, or transactions. A service process also has its own service scope.
These are execution and authorization envelopes, not replicated Java objects.

Runtime creates or reconnects a digital twin and registers its context locally. A session and
its contexts identify their host Runtime. Context IDs contain the parent session ID and a context
component; focused scope tokens encode additional selection information. Do not infer a permanent
physical endpoint from that syntax.

For an outgoing call, `Utils.Http.Client.withScope` creates a request-specific client copy and
sets the relevant headers. For example, `ResolverClient.resolve` sends the observation and
resolution constraints with the context scope.

| Information | Current transport |
| --- | --- |
| Caller identity | HTTP authorization, with local service authentication where applicable |
| Session/context and encoded focus | `klab-scope` |
| Originating host Runtime identity | `klab-service` |
| Transaction | `klab-transaction` |
| Context observation | `klab-context-observation` |
| Relationship endpoints and lexical resolution context | Additional headers declared in `ServicesAPI` |

The originating Runtime header is a lookup key. It must not become authority to connect to an
arbitrary caller-supplied endpoint or to access a twin without authorization.

At the receiver, `TokenAuthorizationFilter` collects the headers and validates the identity
through `ServiceAuthorizationManager`. The manager parses the scope token, asks `ScopeManager`
for the corresponding peer scope, contextualizes it, and attaches it to the request principal.
Missing scope headers yield a user-level scope; they do not imply a context.

### 1.4. Reconstructing a missing context

The reconstruction path is lazy:

1. Reuse a compatible registered scope if present, applying the requesting identity when needed.
2. For a missing context, require the originating Runtime ID and locate it among the user's
   advertised services. If that Runtime is known but filtered out by cached status, refresh its
   status once and repeat the usable-service lookup. An unknown Runtime is not discovered from
   the header alone.
3. Ask that Runtime for the context configuration using the user's scope. Runtime checks the
   configuration's owner/access rights and currently looks up a registered context; this call
   is not a general search through every persisted twin.
4. Require a matching configuration ID. Reconstruct the parent session and context peer, copy
   the applicable service clients, set the host ID, and let the receiving service declare and
   instrument its context. A private application, script, or test session is accepted through
   this path only after the host Runtime verifies access to its context.
5. Apply request focus and other contextual headers before invoking the service operation.

User-scope lookup/initialization and login are synchronized so concurrent first requests cannot
replace an advertised user scope with a competing empty instance. This does not make every
session/context creation operation transactional or synchronize all child scope catalogs.
Child scopes copy service lists; they are not automatically live views of later user-catalog
changes.

If Resolver still lacks a context, its controller returns a conflict with reconnection guidance.
The error handler now preserves the status and detail of Spring status exceptions rather than
embedding a 409 inside a generic 500 response. Unknown host, known-but-unusable host, unauthorized
configuration, and unavailable configuration should remain distinguishable in diagnostics.

### 1.5. Messaging and persistence today

Federation information provides the broker connection. Scope messaging uses AMQP exchanges,
with message queue categories filtered by consumers. `AMQPChannel` currently uses fanout
exchanges and creates transient, broker-named receiver queues. Consumption uses automatic
acknowledgement. Exchange durability and message delivery mode depend on federation/context
configuration, but durable exchanges or persistent messages do not make those transient receivers
a durable replay mechanism. Sender-only channels do not create a receiver queue.

A persistent twin's graph and storage can outlive a Runtime process. Its live scope objects,
client catalogs, subscriptions, pending executions, and some scheduler state cannot be assumed
to survive with them. [Persistent twins](PERSISTENT_TWINS.md) describes implemented restoration
and remaining execution-recovery gaps. [Connected twins](DISTRIBUTED_TWINS.md) proposes composition
and versioned source synchronization; it is not an implemented discovery or failover protocol.

### 1.6. Boundary of the current design

This mode provides direct communication, small local caches, and useful recovery from transient
startup inconsistencies. It does not provide membership leases, resumable catalog watches,
authoritative twin placement, automatic host migration, protection against competing owners,
or durable event replay. A disconnected Engine is not a suitable long-term custodian of a
persistent network's topology. In a large ecosystem, repeating whole catalogs and maintaining
peer polling relationships can approach all-to-all coordination costs.

Implementation references:

- [EngineImpl](../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/engine/EngineImpl.java),
  [ServiceMonitor](../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/engine/ServiceMonitor.java),
  [ScopeAdvertisements](../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/engine/ScopeAdvertisements.java).
- [ServiceClientCatalog](../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/ServiceClientCatalog.java),
  [KlabScopeController](../klab.core.services/src/main/java/org/integratedmodelling/klab/services/application/controllers/KlabScopeController.java).
- [ScopeManager](../klab.core.services/src/main/java/org/integratedmodelling/klab/services/scopes/ScopeManager.java),
  [ServiceAuthorizationManager](../klab.core.services/src/main/java/org/integratedmodelling/klab/services/application/security/ServiceAuthorizationManager.java),
  [ServiceUserScope](../klab.core.services/src/main/java/org/integratedmodelling/klab/services/scopes/ServiceUserScope.java).
- [RuntimeService](../klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/RuntimeService.java),
  [AMQPChannel](../klab.core.common/src/main/java/org/integratedmodelling/common/authentication/scope/AMQPChannel.java).

## 2. Proposed coordination and discovery service

### 2.1. Role and authority

Introduce a logical **Coordination and Discovery Service (CDS)** for deployments requiring dynamic
membership and durable twin networks. It should expose a k.LAB-specific control API backed by a
replicated, quorum-based metadata store. Multiple CDS API instances serve one logical authority;
a single indispensable coordinator process would defeat the purpose. Select the implementation
technology separately, after workload, failure-domain, and operational requirements are measured.

Keep responsibilities explicit:

| Component | Responsibility |
| --- | --- |
| Hub | Authentication, identity, federation membership, trust policy, and bootstrap discovery of authorized CDS endpoints |
| CDS | Authorized service membership, leases, topology revisions, twin placement/ownership, and durable coordination metadata |
| Runtime | Twin graph/storage, configuration authorization, execution, checkpoints, and enforcement of ownership fences |
| Resolver, Reasoner, Resources | Their existing domain responsibilities and local execution scopes |
| Messaging infrastructure | Event/command transport, delivery handling, and subscription routing; AMQP today, potentially Kafka in larger deployments |
| Engine and service clients | Discovery caches, authorized selection, request scope propagation, and reconnect behavior |

CDS does not proxy every scientific request, hold observation payloads, replace the Hub, or become
a global scientific transaction manager. Services continue communicating directly. Membership is
not authorization; the destination still validates each operation. Lease expiry indicates loss
of a valid claim, not proof that a process or machine has stopped.

### 2.2. Records and identities

Separate durable identity from process incarnation and current address. Suggested records are:

| Record | Minimum proposed fields |
| --- | --- |
| Service | Stable service ID, federation/tenant, type, capabilities, API/worldview compatibility, endpoint descriptors, instance ID, incarnation, lease ID, readiness, revision |
| Twin directory | Stable twin ID, legacy context aliases, owner/policy reference, persistence policy, configuration version, lifecycle state, storage/checkpoint references, record revision |
| Twin assignment | Twin ID, hosting Runtime instance/incarnation, lease, monotonically increasing ownership epoch, recovery progress, assignment revision |
| Twin dependency | Source and dependent twin IDs, authorized selection, required versions, freshness/recovery policy, lifecycle state |
| Subscription intent | Durable consumer ID, source twin/stream, policy and transport reference, replay requirement, desired lifecycle |

Keep credentials out of public descriptors; resolve credential references through authorized
mechanisms. Store high-volume event cursors and payloads with their stream/consumer state rather
than write each message acknowledgement to the registry. Directory references must not expose
private storage locations to unauthorized callers.

A stable twin ID survives host replacement. Existing context IDs remain supported as aliases;
new code must not equate a parent session or host service ID with permanent twin ownership.
Directory existence and active assignment are separate: an intentionally dormant persistent twin
can exist without any live Runtime lease.

### 2.3. Registration, discovery, and watches

A service authenticates to CDS, registers an instance/incarnation, and obtains a renewable lease.
It advertises initializing, ready, draining, or unhealthy state separately from lease validity.
Lease duration, renewal interval, retry backoff, and jitter are deployment settings with measured
failure-detection targets. Expiry/removal is published as a versioned change; graceful withdrawal
first stops new placement and drains work.

Clients query by federation, service type, compatibility, locality, and required capabilities.
Results are paginated and authorization-filtered. They then watch only relevant subsets rather
than subscribe to every service in the ecosystem. Discovery returns candidates; client policy
selects among them, and direct readiness checks may refine that choice.

A snapshot includes a revision from which its watch can continue without a gap. Changes carry
monotonic revisions, explicit removals, and sufficient identity to reject old incarnations.
Duplicate changes are harmless. A reconnect resumes from the last applied revision; if retention
has expired, the client obtains a fresh snapshot. Watch termination, authorization changes, and
revision gaps are explicit outcomes, never interpreted as an empty healthy catalog.

Keep tombstones long enough for the supported resume window. A registry revision orders registry
changes; it does not order all scientific events in every twin.

### 2.4. Scope reconstruction in coordinated mode

Retain compact scope headers and local `ScopeManager` peers. Change how the host and authorized
service catalog are resolved:

1. Validate the caller and parse the logical twin/context selection.
2. Resolve the twin's current assignment through an authorized directory cache, refreshing it
   on expiry, assignment mismatch, or host failure. Treat a legacy originating Runtime header
   as a hint to check, not permanent ownership authority.
3. Call the assigned Runtime using the user's identity or a bounded, audience-specific delegated
   credential. That Runtime authorizes the configuration and selection.
4. Build or refresh the local context peer with the twin configuration version and assignment
   epoch. Invalidate host-specific clients/subscriptions when either changes; preserve caller
   identity, observer selection, and scientific constraints.
5. On a stale-owner response, refresh placement and retry only operations known to be safe.
   Mutating requests need durable idempotency keys and an outcome lookup before retrying an
   ambiguous timeout. Do not silently move an in-flight transaction to a different owner.

Introduce a common discovery abstraction with snapshot, lookup, watch, and freshness semantics.
A `StaticPeerDiscovery` adapter implements the current Hub/Engine advertisement mode; a
`CoordinatedDiscovery` adapter uses CDS. These are proposed names, not existing interfaces.
Versioned catalog handles or explicit invalidation should replace copied, indefinitely stale
child-scope service lists in coordinated mode.

### 2.5. Twin ownership, recovery, and fencing

Start with one active writer per twin. The current owner renews its assignment lease. A candidate
replacement must satisfy placement and version constraints, have access to required durable
state, and acquire a new assignment through a conditional update against the expected revision.
Concurrent acquisition attempts cannot both succeed.

Every ownership change advances a fencing epoch. Storage writes, checkpoint commits, job claims,
and effectful message consumers must reject work from obsolete epochs. Checking an epoch once
at request entry is insufficient: a paused old owner may resume after reassignment. The durable
write/effect boundary must enforce the fence. If a storage backend or external effect cannot do
this, automatic failover for that operation is not safe and must remain disabled or require an
explicit recovery procedure.

A replacement progresses through assigned, restoring, catching-up, and ready states. It verifies
checkpoint integrity, reconstructs executable state, reconciles unfinished jobs, restores
subscriptions, and catches up required inputs before accepting writes. A directory entry must
not advertise recovery completion merely because an HTTP process is listening.

Runtime-local files do not become portable because CDS knows their paths. Shared/replicated
storage or an explicit transfer/restore protocol, compatible executable definitions, and tested
checkpoint recovery are prerequisites. Graceful migration additionally requires quiescing writes,
recording a recovery boundary, transferring ownership, and redirecting clients. These depend on
the recovery work in [Persistent twins](PERSISTENT_TWINS.md).

### 2.6. Messaging and persistent connected twin networks

Use a transport-neutral messaging contract alongside CDS. AMQP remains the current implementation;
Kafka is a possible transport for larger deployments. Neither choice should determine the public
scope model, stable twin identity, or ownership protocol. A deployment may use either transport,
or both for different traffic classes, through explicit adapters and routing policy.

Keep two distinct classes of traffic:

- Control notifications announce service/assignment/dependency revisions and prompt cache refresh.
  CDS remains authoritative; notifications can be duplicated or missed without losing the ability
  to reconstruct current topology from a snapshot and watch.
- Twin events and commands carry domain changes and execution requests. They require their own
  durable delivery/replay contract; a registry watch is not their event log.

For persistent connected twins, persist dependency and subscription intent independently of
transient broker connections. Map that intent to durable AMQP subscriptions or Kafka topic,
partition, and consumer-group arrangements as appropriate. The adapter must declare its delivery,
ordering, retention, and replay capabilities; a requested recovery guarantee must fail explicitly
if the configured transport cannot provide it. Acknowledge delivery or advance the consumer
position only after durable processing, and define retry limits, dead-letter handling, retention,
backpressure, and observability. Keep lightweight transient channels for UI notifications and
sandbox use where that is the intended contract.

Publish committed domain changes through a durable outbox or equivalent commit-coupled mechanism.
Require the transport's configured durable publication acknowledgement before marking publication
complete. Consumers keep durable event IDs or per-stream sequence checkpoints and make repeated
delivery idempotent. Broker acknowledgements and Kafka offsets are transport-specific recovery
positions, not substitutes for logical twin revisions or proof that an external effect committed. Target at-least-once
transport with deduplicated effects; do not claim end-to-end exactly-once execution.

An event envelope should identify federation, source twin, source incarnation/ownership epoch,
stream ID, event ID, sequence/revision, schema version, correlation/causation IDs, and payload or
an authorized payload reference. Define ordering per source stream and preserve it in the adapter's routing/partitioning policy.
Do not assume a total order across partitions or independent twins. On a gap, recover retained
events or load a consistent snapshot and resume from its boundary. A new ownership epoch requires
validation of stream continuity, not blindly resetting every consumer cursor.

For an AMQP-to-Kafka migration, record a logical stream boundary and its transport positions,
provision the new subscriptions, verify catch-up, and then switch routing. Preserve event IDs and
source revisions across any overlap so consumers can deduplicate. Do not treat dual publication
as an atomic cross-broker commit or translate an AMQP acknowledgement directly into a Kafka
offset. Specify rollback and replay retention before cutting over. Kafka consumer-group ownership,
if used, coordinates delivery work; it does not replace CDS twin ownership or storage fencing.

Connected twin C retains the independent identities and ownership of sources A and B. It records
their authorized selections, revision vector, and freshness requirements. Losing B produces an
explicit degraded state. Computations needing fresh B input wait with a deadline or fail; stale
input is usable only under an explicit policy recorded in provenance. Recovery refreshes B's
assignment and resumes its source stream. Closing or deleting C releases C's subscription intent
without deleting A or B. Dependency cycles require explicit scheduling semantics; discovery alone
cannot resolve causal loops. This preserves the composition contract in
[Connected twins](DISTRIBUTED_TWINS.md).

### 2.7. Failure and consistency policy

Use strong consistency for lease ownership, fencing epochs, and conditional assignment changes.
Discovery caches and notifications may be eventually consistent within stated freshness bounds.
Scientific state keeps its own transaction and consistency model.

| Failure | Required behavior |
| --- | --- |
| Service disappears | Expire its lease, withdraw it from new selection, retain durable twin records, evaluate eligible recovery |
| CDS quorum unavailable | No new ownership grants or renewals; owners stop accepting writes before their locally established safe lease deadline |
| Network partition or paused owner | Fence obsolete writes/effects; connectivity loss alone cannot authorize takeover |
| Discovery cache is stale | Use bounded cached reads where policy permits; refresh before placement or ownership-sensitive work |
| Messaging transport unavailable | Preserve committed outbox records, expose lag/degraded state, apply bounded buffering/backpressure |
| Consumer loses replay history | Rebuild from an authorized consistent snapshot; report inability to meet freshness until complete |
| Credentials expire or access is revoked | Reject affected operations and refresh authorization; do not treat cached discovery as an access grant |
| Durable state cannot be restored | Keep the twin unavailable/degraded with an actionable reason; never initialize an empty replacement under its identity |

Renewal responses must support conservative local deadlines using monotonic elapsed time and
account for request latency and pauses; synchronized wall clocks alone are not a lease protocol.
Cached reads during a partition need a policy for both data freshness and authorization freshness.
There must be no automatic fallback from coordinated ownership to unfenced static ownership when
CDS is unreachable. Existing read-only work may continue only within its declared policy.

### 2.8. Federation, scale, and operations

Partition authority by federation or an explicit coordination domain. Cross-federation discovery
uses authorized referrals and scoped queries; it does not replicate every tenant's catalog to
every client. Each twin has one designated ownership authority. Transferring that authority is
an explicit protocol, not two registries independently electing owners for the same twin.

Hub trust establishes who may register which service types, advertise endpoints, discover twins,
claim assignments, and administer recovery. Endpoints require transport authentication and
identity verification. Filter both snapshots and watches, remove revoked cache entries, and audit
registration, placement, ownership, and policy changes. Broker/topic permissions must match federation,
stream, and subscription authorization. Long-lived autonomous twins need renewable service or
workload credentials with explicit delegation; they cannot depend on an Engine user's login
remaining active forever.

Index and shard metadata by domain/type/twin as needed; restrict watch fanout and batch renewal
where appropriate. Keep high-frequency measurements out of the strongly consistent registry.
Measure watch lag, lease renewal failures, expired assignments, advertisement/reconciliation
backlog, rejected stale epochs, recovery duration, replay lag, and time spent degraded. Logs should
correlate caller, logical twin, host incarnation, assignment epoch, and registry revision without
exposing credentials.

### 2.9. Delivery plan and acceptance criteria

Implement this as an opt-in progression:

1. **Preserve and formalize static mode.** Keep existing tests and sandbox workflows. Introduce
   discovery contracts without changing current defaults or scope authorization semantics.
2. **Add coordinated membership.** Deliver registration/lease APIs, filtered snapshot/watch,
   cache reconciliation, and Hub bootstrap configuration. Compare results in shadow mode before
   making CDS the discovery authority for a deployment.
3. **Add the twin directory.** Resolve existing twins to their current host and track configuration
   versions. Initially keep placement fixed; discovery does not imply automatic failover.
4. **Make recovery safe.** Complete durable checkpoints, write/effect fencing, idempotent commands,
   and storage accessibility. Enable controlled migration, then automatic reassignment only for
   twin types whose recovery prerequisites have been demonstrated.
5. **Persist connected networks.** Add dependency/subscription intent, outbox/inbox processing,
   replay/snapshot recovery, and revision-aware degraded operation. Exercise recovery with the
   Engine absent.

A deployment explicitly selects static or coordinated mode per coordination domain. Any migration
bridge has a designated authority and does not create competing writers. Preserve current headers
through a versioned compatibility layer while clients learn stable twin IDs and assignment epochs.
Rollback to static mode requires quiescing coordinated owners and an explicit ownership handoff;
switching a configuration flag during an outage is not a safe rollback procedure.

Acceptance tests must cover late service arrival and removal, concurrent registration, failed
renewal, duplicate/out-of-order watch events, watch compaction, broker outage, transport cutover/replay, ambiguous command
timeouts, revoked authorization, stale owner resumption, CDS quorum loss, and crashes at each
checkpoint/outbox/acknowledgement boundary. Demonstrate that only one epoch can commit writes,
that connected twins never silently replace missing sources with empty data, and that sandbox
tests still run without CDS. Establish deployment-specific recovery-time, data-loss, registry
capacity, and watch-lag targets before production acceptance.

Open decisions include the metadata-store technology, lease and retention settings, ownership
partitioning, supported storage-fencing mechanisms, AMQP/Kafka adapter contracts and durability topology, cross-federation
trust agreements, and which execution types can safely resume. The first implementation should
resolve these for one bounded deployment rather than claim universal automatic failover.
