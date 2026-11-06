# Digest integration checkpoint

Built on the independent audit commit `54c307645a7c` (baseline `b59382f933ec`). The archive policy
is the user's selected minimal digest with links to persistent permission-checked review stages.
The earlier full submitted-history helper is not used by a runtime/default export.

## Implemented

- Closure and a service-private replay intent are one aggregate CAS. Rejected closure writes
  publish neither closed history nor archive intent. The original closed revision is retained
  privately; later cycles and receipt changes do not rewrite it or bump the flow revision.
- Default export accepts routing coordinates and a trusted Resources base only. It publishes
  one closed-cycle link and generic explanation, without restricted/private text, candidate data,
  participant identity, proposal metadata, audience labels or counts. Standard Git metadata has
  a fixed service identity and commit time. Opaque links grant no authority.
- The provider attempts exactly the newly closed cycle after persistence. It rechecks workflow
  editor control. WorkspaceManager resolves the loaded ontology's actual hosting project, checks
  current material ADD/write authority, read-only storage and mutation ownership, and holds its
  monitor across the archive callback. No user-supplied project/path/public URL/committer is used.
- The private bounded queue pins project, canonical root and link before Git, then persists the
  actual original introducing commit, relative path and manifest hash. Another target/base on a
  retry is blocked. A missing receipt after successful Git publication is recovered without a
  duplicate commit through the writer's exact-export checks.
- Startup/minute replay repairs lost wake-ups and retains pending/blocked work. It never writes
  projects with cached request scopes. Automatic close-time attempts use the current request;
  deferred attempts require an explicit freshly authorized editor retry.
- GET/POST `{FLOW}/review-cycles/{cycleId}/archive` provide private/no-store status/retry with
  authenticated scope forwarding. UI controls inspect receipts and retry one closed cycle.
  Only successful receipts expose commit/path/hash. Private paths, exceptions and ledgers never
  enter these DTOs or ordinary workflow projections; reserved metadata injection is rejected.
- Worker shutdown precedes resource-store shutdown. An incomplete stop leaves those stores open.
  No remote push, hook, SMTP, production service action or real project write was performed.

## Verification and boundaries

`pilot-digest-combined-reactor.log` passed 280 checks with zero failures/errors/skips (43 common,
60 core, 43 reasoner, 2 resolver, 128 Resources, 4 server); the delivery receipt records hashes.
`pilot-digest-ui.log` contains 14 successful browser checks, including
receipt/retry controls and audit regressions. Type-checking and standalone/embedded builds pass.
The initial focused log preserves one corrected test expectation: peer-only community access
returns 403 rather than an empty successful view. A preceding compile attempt overlapped source
editing and used the pre-method core class; the final normal reactor rebuild is authoritative.

Disposable Git tests cover minimal disclosure, token/identity link rejection, current/revoked
project authority, read-only/other-owner storage, dirty-work preservation, target isolation, pinned
retry binding, queue restart/dedup, lost-receipt recovery and original-commit receipts. Workflow
tests cover atomic failed closure, reserved/private ledgers, protected stage access after closure,
foreign drafts, fresh editor control, single-cycle dispatch and sanitized blocked states. The native
browser scenario replays both closure intents after actual Nitrite/RocksDB close/reopen and then
reopens the archive queue. Its four principals remain explicit simulations, not live IdP users.

Deployment needs a durable advertised Resources URL reachable through the existing auth-return
route, protected workflow/private-queue backups and initialized clean ordinary host repositories.
Real-account login, permission revocation and project authorization still need verification using
authorized accounts. A moved public origin requires a durable redirect; a pinned digest is never
rewritten to a new URL. Cycles closed before this integration lack intents and are not automatically
migrated. Pending jobs after restart require fresh editor retry; no authoritative asynchronous
identity directory has been invented.

Git branch and index publication cannot be one atomic operation. Stale index evidence after a
branch-published failure, partial exports and tampering require operator inspection; no speculative
reset/delete/repair occurs. A generic BLOCKED status does not expose private raw failure details.
Capacity/retention and privileged reconciliation remain operational work. Real mail remains
disabled pending authoritative verified-mailbox/consent integration and delivery authorization.

See [handoff](ontology-review-pilot-handoff.md) and [archive contract](../klab.core.services/REVIEW_ARCHIVE_PILOT.md).
