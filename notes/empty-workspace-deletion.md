# Empty workspace descriptor deletion

Authorized narrow server prerequisite for Python authoring PR #110. This is not a
deployment, general resource-delete redesign or scientific/worldview change.

## Behavior

- Existing DELETE WORKSPACE route now checks owner/administrator and service-wide
  DELETE/ADMINISTER permission before attempting removal. Shared READ rights do
  not grant deletion. Missing catalog entries are not acknowledged as deleted.
- Only empty local workspaces can be removed. Configuration membership, live
  project list and active descriptors must all be empty. The reserved external
  workspace is refused. **No project/file deletion or cascading occurs.**
- Successful removal updates the ResourceInfo catalog, configuration and in-memory
  workspace index, then returns a matching WORKSPACE/DELETE descriptor. Source
  projects must be explicitly removed through their existing authorized operation
  first. A refusal is an error/empty ResourceSet, not a success receipt.
- Workspace/project creation and relevant manager transitions use the same local
  monitors as deletion to avoid admitting a locally concurrent project unnoticed.

## Failure boundaries

Catalog and YAML configuration are separate stores, not a shared transaction.
Ordinary runtime failures restore the catalog and in-memory/configuration state
where possible, and retain restoration failures on the original exception. Same
exception instances are never self-suppressed. No success receipt is returned
after a failure. Crash-atomicity, cross-process locking, distributed rollback and
safe automatic mutation replay are **not** claimed. A lost/failed response may
still require remote inspection before any retry.

This change does not fix unrelated project-history/orphan-record issues, permit
deleting nonempty workspaces, restore SCRIPT source fidelity, or grant remote
ordinary users new CREATE privileges. Earlier failed authoring evidence remains
preserved, including workspace descriptors in stopped private fixtures.

## Verification state

Initial targeted Java run: 18/19 passed, revealing self-suppression in rollback
error handling. Fixed without weakening the failure assertion; second run passed
20 tests, none skipped. Additional active-descriptor, shared/anonymous access and
post-write persistence-compensation tests are added for the expanded run.

Before acceptance require expanded Java regressions, a fresh source-matched
Resources server build and genuine namespace ADD/cleanup round trip, followed by
review and final-head CI. The old authoring fixture's Java tuple is substantially
different; do not overlay new classes onto it or presume binary equivalence.
No running deployment or unrelated process is to be changed for validation.
