# Ontology review: durable mutation and proposal checkpoint

6 November 2026. Isolated `task-7/services-pilot`, branch `ontology-review-pilot`;
published Services develop ancestor `212548f761`. Previous clean integration `9ec81e2c152`
is retained at `checkpoint/review-integration-9ec`. Original checkouts/services unchanged.

## Delivered after the previous checkpoint

- `29d54fbf4`: database-atomic revision updates for existing flows, independent-manager
  conflict protection, staged attachment recovery and real cross-process database-lock tests.
- `cbf8b88e1`: alternative proposal/source contributions, server-derived identity/schema
  evidence, private incomplete drafts, immutable submissions and contributor/proposal UI filters.
- `035824ecf`: task-9 archive implementation imported from `27d3fcb3c424` with attribution.
  Its 17 archive and 6 material tests run in the normal combined reactor.
- Closed cycles now retain their exact closure revision. A pure archive mapping exports
  candidate bytes, each submitted peer/community contribution (including alternative proposal
  and source), and an attributed round/cycle history document. It verifies artifact media,
  membership and checksums, excludes every private draft, and remains stable after later cycles.
  Cycles predating closure-revision capture fail closed rather than invent provenance.

Alternative proposal content is inline in the contribution aggregate, with exact UTF-8
checksums. It is not a fabricated stage attachment, an accepted candidate or semantic approval.
Missing alternative source means retaining the enclosing round candidate's ontology.
Changed inputs cannot reuse a bound proposal/revision identity at submission. Submitted
alternatives remain attributable originals; the editor integrates a new candidate explicitly.
The UI keeps the candidate alongside alternatives and preserves drafts on conflicts.

## Persistence boundary

WorkflowStore now requires compareAndSetFlow: a read followed by an unconditional put is
not an implementation. ResourcesKBox uses Nitrite's atomic filtered update under its collection
write lock. Two independent managers and two detached production-store snapshots have one
winner for the same revision. Failed new payloads are reclaimed; payloads referenced after an
uncertain successful aggregate write are retained. Materialized catalog summaries reject stale
revisions. Pilot flow deletion is blocked even before its first cycle, avoiding a delete/open race.

The production RocksDB database has one owning process. A separate child JVM is rejected
while the owner holds the database and can open it after shutdown. This is a verified exclusive
ownership boundary, not simultaneous replicated writers or failover. Process crashes may leave
unreferenced blobs; safe orphan collection and durable event/outbox work remain future work.

## Independent Git archive audit and integration boundary

The imported writer uses generated hashed paths and CREATE_NEW, validates ordinary Git
repository ownership and path aliases, holds the real index lock, compares branch HEAD before
publishing, and never runs hooks or pushes. A new archive requires a clean project. Dirty
staged/unstaged work is preserved by deferring, not silently committed. Exact completed exports
are idempotent and return the introducing commit ID. Partial/tampered exports and an inconsistent
index after a branch/index publication failure require inspection; no destructive repair occurs.

The submitted-history mapper is internal and has no HTTP/k.Actors or automatic Git caller.
It is not an archive job, receipt or notification. The host project name is an input only to this
internal mapper; no caller-selected path/project is exposed. Do not wire it to unrestricted
repositoryStorage, retrieveProject or user-supplied filesystem paths. The provider must resolve
the actual ontology host and enforce requireMaterialWrite plus writable storage/project locks.

Before automatic export, resolve readership explicitly: reviewer feedback is stage-restricted,
community feedback requires authentication, and neither implies permission to publish to Git.
The current project rights API does not establish the effective remote Git readership. No
automatic export policy is invented. Export to an access-equivalent archive, or obtain an explicit
approved publication policy with suitable attribution/consent handling. Private drafts stay private.

Closure and a frozen archive job/outbox must persist together. A worker then authorizes the host,
commits idempotently, persists relative path/manifest hash/commit receipt conditionally and exposes
pending/failed/committed status through the UI. A crash after Git commit but before receipt must
recover the exact existing archive before retry. These integrations and the receipt UI remain open.

## Verification

- `pilot-conditional-writes-recheck.log`: normal Resources reactor, 6 tests passing,
  including native conditional writes, separate-process locking and uncertain attachment outcomes.
- `pilot-structured-contributions.log`: normal Resources reactor, 18 tests passing,
  including private invalid drafts, server schema/identity checks, byte/Unicode limits,
  immutable alternatives and exact structured payload close/reopen.
- `review-ui-proposals-browser.log`: 7 disposable Edge browser tests passing, including
  alternative proposal/source draft resume, submission/filtering and unchanged candidate display.
- `review-ui-durable-typecheck.log` and `review-ui-durable-build.log`: Vue typecheck and
  standalone/embedded production builds pass.
- `pilot-durable-review-reactor.log`: normal Resources-server reactor BUILD SUCCESS;
  84 Resources tests, 28 core-service tests (including all 23 archive/material tests),
  and 2 HTTP/configuration server tests pass with no skipped tests.

All logs are task-7 artifacts outside Git. Dependencies remain the verified published Languages
artifacts in task-local Maven storage with a read-only user-cache tail. No credentials/global
configuration changed. Git operations in tests use disposable repositories with no remote.

## Remaining pilot gates

1. Live isolated editor/reviewer/community sign-in, role revocation, stage URLs and multi-user
   persistence tests. No designated authorized live test identities were found in available
   configuration. EngineAuthorization HTTP tests and browser fixtures do not prove JWT/IdP login.
2. Durable editor/participant notices and asynchronous email with retry/deduplication. No real
   messages were sent, and persisted contributions are not delivered editor notices.
3. Archive readership policy, authorized actual host resolution, atomic closure/outbox,
   receipt persistence/recovery and pending/committed UI links as above.
4. Richer semantic diff/comment authoring can follow the functional document/source pilot.

No push, deployment, restart, external account creation or real-project archive was performed.
