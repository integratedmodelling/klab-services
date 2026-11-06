# Replay worker and recipient policy checkpoint

Isolated branch `ontology-review-worker`, based on preserved notification checkpoint `df454aa4ad48`.
Earlier bundles/branches and all original user/task checkouts remain intact.

## Runtime lifecycle

Resources initialization starts one daemon replay worker. Its initial tick and one-minute periodic
ticks scan the committed flow intent ledgers into the idempotent private outbox. It does not call
dispatch, resolve a mailbox or instantiate a mail transport. There is deliberately no SMTP/delivery
enable switch in the worker. Existing SMTP settings have no effect on it.

The outbox lives under the Resources service data/configuration directory at
`private/review-notifications`, outside ontology projects. Path aliases, symlinks and unexpected
file types fail closed. This path relies on the deployment's service-private data-directory ACLs;
the code does not change existing filesystem ACLs or establish protection from a local OS administrator.
The existing database, private settings and this outbox all require appropriately protected service storage.

Replay errors mark cached health DEGRADED with a fixed diagnostic code, preserving source intents
and the last successful counters. Raw exception messages, paths, account details and tokens do not
enter health. The next tick reopens/reconciles the outbox and retries replay. Capacity remains bounded
at 10,000 events/delivery rows; exhaustion is visible and never silently prunes deduplication state.
Production retention/capacity policy is still needed before sustained use.

Shutdown interrupts scheduling and waits for the replay executor, then closes its outbox before
resource stores close. If it cannot stop within 30 seconds, Resources shutdown reports failure and
leaves its stores open rather than racing a writer. Reinitializing the same closed provider object
is not a service restart protocol; create a fresh service instance as usual.

## Privileged visibility

`GET /api/v1/admin/review-notifications?limit=50` exposes read-only cached health: lifecycle state,
delivery-disabled flag, last attempt/success, fixed reason, total counts by status, and at most
100 delivery rows. Rows contain opaque event/principal IDs and status only, never mailbox, body,
token or directory path. There is no public retry/reconcile action, synchronous replay trigger,
transport configuration or notification browsing endpoint for review participants.

The controller explicitly requires an authenticated EngineAuthorization with the service
ADMINISTRATOR role and a UserScope, independently of method-security annotations. The provider
also enforces authenticated, nonanonymous service ADMINISTER permission. Workflow editor/reviewer
roles alone do not grant access. Responses use one `Cache-Control: private, no-store` header.

## Identity and consent boundary

Existing UserIdentity exposes an email address and cached ScopeManager entries. Neither establishes
current mailbox verification, email consent or timely role revocation. No authoritative verification/
consent directory contract was found, so these are not guessed from metadata or current login state.

`ReviewRecipientPolicy` is a trusted-directory adapter, not an enabled delivery binding. It requires:

- the exact frozen hashed principal ID and a matching authenticated current identity;
- an authoritative account/permission snapshot no older than 60 seconds, with no future timestamp;
- an explicitly verified mailbox and GRANTED consent;
- current authorization for the original event's flow and exact round/cycle.

Known revoked/missing accounts, unverified mailboxes, declined consent and lost role entitlement
are suppressed. Unknown consent, stale snapshots and directory outages fail closed as retryable
lookup failures. New role grants do not add recipients to historical events. Single-mailbox validation
remains enforced by the outbox immediately before any transport invocation.

The interface is not populated from request fields or mutable user metadata and is not wired to
cached scopes. A production directory must supply these authoritative facts and an agreed consent
policy. Real delivery, trusted authenticated links, retention and privileged reconciliation audit
remain pending. The readonly operational API is not a reconciliation audit implementation.

## Verification and live-auth boundary

Tests use disposable directories, mocked current identities and fake transports. They cover startup
replay, restart/deduplication, pending deliveries with zero send attempts, scheduled shutdown,
provider store-close ordering, exclusive ownership, sanitized transient recovery, unexpected file
types, exact identity/role checks, consent, stale/future snapshots, and administrator HTTP/CRUD masks.

`pilot-worker-focused.log` records the first focused run: worker/policy tests passed; an HTTP header
test caught duplicate Cache-Control values. The controller was corrected to emit one header.
`pilot-worker-combined-reactor.log` records the combined review/schema/archive/notification and
existing HubWebAuthentication/TokenAuthorizationFilter regression run: BUILD SUCCESS, 216 tests
(55 core-service, 43 Reasoner bootstrap, 115 Resources and 3 server tests), no skipped tests.
`pilot-worker-provider-lifecycle-recheck.log` passes 4 targeted provider/admin tests, adding
initialization/private-path/idempotence verification and admin health timestamps. The earlier
fixture compile failure is retained in `pilot-worker-provider-lifecycle.log`. Delivery artifacts contain the final outcome and
checksums. The UI sources/build outputs were unchanged.

No designated live editor/reviewer/community test identities or test tokens were found in the
available task configuration/environment. Existing hub/token fixtures exercise authenticated
boundaries but do not prove an external IdP login, live multi-user browser sessions or production
revocation propagation. No accounts, production sessions, certificates or access-control bypasses
were used to fill that gap.

Archive dispatch remains disabled pending the user's readership decision and archive outbox/receipts.
No real message, SMTP configuration change, push, deployment, restart or real-project write occurred.
