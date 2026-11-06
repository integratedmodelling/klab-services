# Closed review cycle archive pilot

`ReviewCycleArchive.archive(projectRoot, closedCycle, committer)` writes a frozen cycle to the
ontology host project's additional material and commits it locally. It uses JGit directly and
does not use the project's general commit/push operation or the k.Actors `write_material` verb.
Those material verbs already support authenticated project reads/writes, but neither they nor
`WorkflowBehaviorBridge.Content` provide an archive transaction or a safe scoped commit.

The section is `review-history/<SHA-256 ontology URN>/<SHA-256 workflow ID>/<SHA-256 cycle ID>/`.
The IDs themselves remain readable in `manifest.json`. It records the closed cycle revision,
closure timestamp, project, ontology, workflow, source revision and each supplied document's
contributor, proposal, revision, kind, media type, payload path, size and SHA-256. Binary payloads
are retained exactly under `revisions/`. Distinct cycles append directories; a new revision of a
closed cycle is rejected. Generic `ProjectMaterialIO` mutations reject the reserved section
(case insensitively); listing and reading still work. Direct filesystem/Git administrators can
change their repositories; retries detect changes to the archived tree, worktree or index.

## Required workflow integration

This helper is not exposed as a public or actor operation. Before invoking it, the service must:

1. Authorize the editor and resolve the ontology's actual hosting, service-owned writable project.
   Do not accept an arbitrary filesystem path or caller-selected project from an HTTP request.
2. Persist closure and an archive job atomically, with the frozen workflow/cycle revision as the
   job's idempotency key. Retain that exact snapshot for retries, including the original closure
   time. Do not reconstruct it from the current candidate or a later cycle.
3. Supply every retained candidate/proposal/comment revision and the workflow/event snapshot as
   entries. Entry IDs identify documents; `(id, revision)` must be unique. Use explicit stable
   IDs/contributor/proposal fields for the candidate and event snapshot as well. Preserve source
   provenance in the entries. This helper cannot determine whether supplied history is complete.
4. Apply an archive visibility policy consistent with the host project's readership. Do not
   export private drafts or stage-restricted feedback into a broadly readable Git repository.
   The current pilot intentionally has no implicit redaction or permission-broadening policy.
5. Invoke the helper with an explicit service/editor committer identity and record its receipt
   only after return. Store the relative path, manifest hash and actual commit ID. On failure,
   keep the job pending/failed with its error; do not advertise an archive as committed.

The workflow closure/outbox hook, project resolution/authorization, visibility decision, receipt
persistence, retry worker and UI links are integration work. No running service or real ontology
project has been changed by this slice.

## Transactions and failures

The writer holds the actual Git index lock across validation, file export, tree construction,
branch compare-and-swap and index publication. Competing archives or ordinary Git writers must
retry after lock contention. Existing index entries are copied exactly, new archive blobs are
inserted explicitly and the commit is parented to the inspected HEAD. Unrelated worktree edits
made while the lock is held are never added. A branch change or compare-and-swap failure leaves
HEAD unchanged by this writer and retains the exported files for inspection/retry.

An ordinary initialized repository with an attached branch and a clean index/worktree is required
for a new archive. Dirty projects, ignored sections, unfinished Git operations, linked worktrees,
symlinks/path aliases and incomplete/colliding exports are deferred or rejected. The writer
does not delete, reset, replace user files, run hooks, use credentials, access a remote or push.

A complete exact untracked export left by a failed commit is retryable automatically. A partial
export is retained and rejected for operator inspection. An already committed exact archive is
verified without mutation, even if unrelated files are currently dirty; its original introducing
commit ID is returned, rather than the current HEAD. Same-cycle changed content is rejected.

Git cannot atomically publish a branch and the index together. A crash or disk failure after the
branch update and before index publication can leave a real commit with a stale index. The helper
does not return success if index publication fails; its exception identifies the commit when
known, and retries reject inconsistent index evidence. Operators must reconcile that condition.
No speculative reset/repair is attempted.

The export limit is 1,000 document revisions and 32 MiB including the manifest. Individual
provenance strings are bounded to 4,096 characters. Files use generated ASCII paths, not caller
filenames. Metadata and content are copied into the closed-cycle input so later byte-array edits
cannot change the export.

## Validation

The tests use disposable initialized Git repositories, explicit test identities and no remotes.
Run the two test classes with the normal Services dependencies available:

```shell
mvn -pl klab.core.services -am -Dtest=ReviewCycleArchiveTest,ProjectMaterialIOTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Coverage includes attributed revisions, clean status after commit, another cycle, stable retry
receipts after later commits, changed-snapshot rejection, dirty staged/unstaged preservation,
index/ref lock contention, failed-commit retry, incomplete exports, worktree/index tampering,
detached/merge state, ignored archives, bounded payloads, symbolic-link rejection where supported,
concurrent attempts, and material read/list versus mutation protection.
