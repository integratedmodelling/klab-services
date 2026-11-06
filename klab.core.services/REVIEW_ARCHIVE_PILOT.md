# Closed review digest pilot

The default project archive is a minimal digest containing a stable Resources URL to a persistent
review cycle. Authentication and stage permissions remain enforced by the workflow service.
The link grants no access. Detailed submitted contributions and private drafts stay in the
workflow database, including after closure. Digest content contains no contributor identity,
feedback, candidate/proposal text, aggregate revision, workflow timestamps, audience labels or
contribution counts. Standard Git commit metadata records the service identity and commit time.

`ReviewCycleArchive.archiveDigest` writes `manifest.json` and `digest.md` under
`review-history/<SHA-256 ontology URN>/<SHA-256 flow UUID>/<SHA-256 cycle UUID>/` and commits only
those generated paths with a fixed service Git identity. The link accepts canonical opaque flow/
cycle UUIDs and the trusted advertised Resources base, never request Host headers, credentials,
tokens, fragments or caller-selected paths. Configure the advertised URL for durable participant
access. Moving that public URL requires a durable redirect; a retry cannot rewrite an old digest.

Closing a cycle and its private replay intent use the same database conditional write. Git runs
after that transaction. `ReviewArchiveOutbox` records bounded private pending jobs, pins the actual
project/root/link before Git, and persists the returned commit/path/manifest-hash receipt. Metadata
is reserved against request injection and removed from user projections. Private queue files must
remain outside projects/Git in the protected service configuration directory.

The provider attempts the closing cycle using the request's authenticated editor scope. The
manager rechecks workflow control, and WorkspaceManager resolves the ontology's actual host,
requires material ADD/write authority, rejects read-only storage or another mutation owner, and
holds its mutation monitor throughout the archive callback. No project/path/committer comes from
the archive REST request. Git failure never rolls back or falsely reports failure of a committed
workflow closure. The private queue remains pending/blocked for an explicit authorized retry.

Startup/periodic replay repairs a lost commit wake-up but never writes projects with cached user
scopes. Fresh editor requests can inspect or retry exactly one closed cycle through
`GET/POST {FLOW}/review-cycles/{cycleId}/archive` (`private, no-store`). The closed-cycle UI has
receipt/retry controls. Status exposes only a generic state and a successful local commit receipt;
raw paths, exceptions, identity and operational ledger data are not user DTOs. Existing cycles
created before intent integration are not silently migrated or mutated.

## Commit and recovery boundaries

The shared writer holds the real Git index lock across verification, scoped tree construction,
branch compare-and-swap and index publication. It requires an initialized ordinary local repository,
attached branch and clean index/worktree for a new archive. Unrelated staged/unstaged edits are
preserved by deferring. Exact completed exports left by an interrupted attempt may be adopted;
partial/tampered exports, path aliases, ignored paths and unfinished Git operations fail closed.
Retries return the original introducing commit even after later commits. They never reset, replace
or remove editor material, invoke hooks, use credentials, contact a remote or push.

Git cannot atomically publish both branch and index. A failure after branch update may leave a
commit with stale index evidence; the writer refuses success/retry until an operator reconciles
that condition. A crash after successful Git publication but before receipt storage is recovered
through exact archive verification without creating another commit. Queue storage failures poison
the open instance; reopen/replay before another attempt. Closure facts remain the durable source.
Capacity/retention and protected data backups are deployment responsibilities.

`ReviewCycleArchive.archive(ClosedCycle, ...)` and the frozen full-history mapper remain internal
opt-in scaffolding for an explicitly authorized readership policy. No runtime/public/default path
calls them. Their detailed format must never replace this digest default implicitly.

## Verification

Tests use disposable initialized Git repositories and simulated authenticated scopes, with no
remotes or real projects. `ReviewDigestArchiveTest`, `ReviewWorkflowArchivesTest`,
`ReviewArchiveWorkerTest`, `ReviewArchiveProviderTest`, `ReviewArchiveProjectAuthorizationTest`
and the HTTP/browser checks cover disclosure, atomic intents, lost wake-up/restart, receipt
recovery, original-commit idempotence, single-cycle dispatch, project isolation, current project
authorization and status/retry controls. Existing archive/material tests retain dirty-work,
index/ref locks, concurrent attempts, path/tamper and reserved-section coverage. The native/browser
scenario additionally verifies private archive ledger replay after Nitrite/RocksDB restart.
