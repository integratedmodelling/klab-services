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

Expanded run: 45 pass/6 fail; clean integration base: 30 pass/the same six failing
AuthoringSourceStorageTest methods. Comparison of individual JUnit methods confirms
all 15 new deletion tests pass; the six existing failures are not waived or repaired.

Rebuilt Resources candidate `84afa1a4165faaa424cd58efeaad45370e021242` includes
separately authorized/tested handler-registration PR115, normally merged at
`91319acead67d43a154b0018b7dcf28e1d238d21`. Post-merge tests: 22 Resources tests
and seven core handler tests pass, no skips. Both hosted checks pass at this code
candidate. Final documentation-only head still requires hosted checks before merge.

Fresh actual Resources-only namespace authoring acceptance PASS with Python
`852ae9b25d9a7d31bb8a01f96a3fa53dbe50391e`: three confirmed creations, exact
namespace source round trip, invalid-source rejection, matching project/workspace
DELETE descriptors and independent post-delete listings. Distinct signed fixture
administrator only; ordinary remote users were verified for read readiness, not
write support. No scientific Job, SCRIPT, overwrite or publication was attempted.

Separate read-only restart PASS: deleted project/workspace stay absent from
authorized listings and workspace membership stays absent in persisted YAML.
Zero mutation requests on restart. Both owned JVM identities and hub threads
stopped; class/dependency fingerprints unchanged through each run. No running
deployment, unrelated process or shared worldview changed. Stopped fixture state
and the four earlier failed authoring attempts remain preserved.

The first two source-matched server starts failed before writes: missing real
Web UI assets (built through the checkout's Maven webui profile), then the existing
duplicate Spring handler mapping (fixed by separate PR115). They are not acceptance
passes. Existing unchanged frontend dependency audit reports two high/one low
advisories, preserved separately; no upgrade or security-clearance claim is made.

Raw local evidence is private (credentials never committed):
`resources-workspace-delete-live-3-20261102/` under the approved OpenCode temp root.
SHA256:

- lifecycle result: `26c7aa54820e4749db41cf19e14f0bb5a87f58abc0365d139f20adb2afb37ec6`
- backend input fingerprints: `9cff8e689f4ac6d386a58ab2b26229482483366416817c6fa7601862d8740867`
- functional JUnit: `dc6f1f80693ed7acf826f66848d6ce9c3ae821f667af73a357f761fbd4b7ec1c`
- mutation journal: `15cafc8bb674770ce842f8061e2c0719c7a5f82a82a845208ff14177f67e5096`

Local diff/behavior review confirms empty-only/no-cascade scope and retained
failure assertions. This is author-conducted review, not independent maintainer
approval. Actual compatibility is bounded to the recorded rebuilt tuple, not the
old authoring fixture or blanket upstream compatibility. Crash interruption and
cross-store atomic recovery remain outside acceptance.
