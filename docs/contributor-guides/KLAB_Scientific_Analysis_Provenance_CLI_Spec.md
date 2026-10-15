# Analysis-ready results with traceable origins: CLI specification

Prepared 15 October 2026 for Eric. Status: implementation specification, not a
claim of completed implementation, passing tests or maintainer approval.

## 1. Objective and working boundary

Deliver one coherent scientist workflow:

**Compute a numerical field -> save its reference -> restore in a fresh process ->
export/open faithful labeled data -> analyze it -> inspect available recorded lineage.**

Combine two independently useful slices: scientific descriptors with one optional
xarray adapter, and typed read-only provenance browsing. Scientific reading is the
core deliverable; provenance is a narrow companion, not a complete twin subsystem.

Current checkout: `klab-python-client`. Current branch:
`feature/python-client-scientific-analysis-provenance`, renamed from
`feature/python-client-repo-organization`. HEAD when this specification was written:
`4ad3887cc6f5d4a816fc9aec5e0f170cfa5efcec`, originally on `feature/python-client`.
The branch contains uncommitted documentation organization work. Preserve it,
including staged renames, unstaged edits and untracked imported documents. Do not
reset, clean, switch away from, or indiscriminately stage this working tree.

This specification requests implementation planning and defines the future CLI
task. When explicitly invoked to implement, local source changes, documentation,
offline tests and isolated test-owned functional checks are in scope. Commit only
with applicable session authorization. Push, PR creation, merge, deployment to
shared services, contributor contact and destructive remote operations require
separate authorization. Do not treat imported historical prompts as active tasks.

## 2. Required inspection before implementation

Read applicable repository instructions and these references:

- [Complete supplied planning guide](KLAB_Python_Client_Planning_Guide.md), especially
  scientific values, provenance, expensive contracts and acceptance reporting.
- [Current reconciled plan](../PYTHON_CLIENT_PLANNING_GUIDE.md) and
  [contribution review guide](KLAB_CLI_Contribution_Review.md).
- `python-client/docs/public-api.md`, `contracts.md`, `stream-exports.md`,
  `exports-verification.md`, `geotiff-throughput.md`, `saved-handles.md`,
  `functional-testing.md`, `connection-layer.md` and `connection-verification.md`.
- `docs/PROVENANCE.md`, `KNOWLEDGE_GRAPH.md`, `DATAFLOW.md`, `RESOLUTION.md`,
  `STORAGE.md` and `SCOPES.md`; distinguish proposals from current behavior.
- `python-client/src/klab_client/client.py`, `exports.py`, `handles.py`, `dto.py`,
  `errors.py`, `api/runtime.py`, `api/primitives.py` and `api/services.py`.
- Actual Java graph query DTOs/controllers/authorization, provenance storage,
  resolution outcomes and native GeoTIFF exporter implementation/tests.
- Existing component patch/build helper, functional runners/shared workflows,
  scientific oracles, package configuration and feature-coverage gates.

Record actual branch, HEAD, working-tree state and integration base. Do not replace
the user-selected Python branch with develop or silently incorporate remote changes.
Reconcile existing implementations before adding competing abstractions.

### Deliver a compact contract inventory first

For every scientific/provenance field or operation, record:

1. Authoritative source, actual route/method/query DTO and decoder, with file/line
   or immutable revision evidence.
2. Scope, observation identity, Runtime origin/ID and permission checks.
3. Shape, dtype, precision, spatial CRS/axis/transform, units and validity evidence.
4. Recorded activity/source/dependency fields and whether they describe planned
   resolution or executed work.
5. Missing/empty/denied/unsupported outcomes; response bounds and pagination.
6. Revision/commit identity and consistency guarantees, if any; never infer them
   from timestamps, matching observation IDs or successful HTTP calls.

Identify a supported, bounded query path before designing provenance APIs. If it
does not exist, record the exact backend dependency. Continue independent reader
work; do not invent endpoints or reconstruct missing server lineage.

## 3. Scientific result slice

### Supported initial representation

Support one numerical, single-band, two-dimensional GeoTIFF representation from
the existing native export path. Use the separately corrected exporter when
required; record its exact component source/artifact identity. Advertisement alone
does not certify applicability or scientific correctness. Do not relabel the old
published exporter as corrected, modify exporter algorithms as incidental client
work, or silently repair/re-encode downloaded bytes.

Keep `ScientificData` sparse indexed reads intact. Do not reshape offsets into a
grid by guessing traversal, dimensions, time selection or geographic coordinates.

### Typed descriptor and artifact association

Expose a defensive typed description of supplied shape, dtype/precision, CRS,
affine transform, axes, source measurement unit and nodata/validity conventions.
Mark unavailable metadata explicitly; never derive measurement units from the
spatial CRS. Surface contradictions between applicable server and artifact metadata
rather than silently picking a convenient interpretation.

Associate the result with the originating observation, scoped Runtime identity,
export schema/media type and local artifact. Record export/fetch time as client
observation time, not authoritative commit time. A local content digest may identify
exact artifact bytes but does not prove server revision, authenticity or lineage.
Keep identities secret-free and do not alter existing version-1 handle meaning.

### One optional analysis adapter

Provide one explicit xarray conversion/opening API using an appropriate optional
GeoTIFF reader. Exact public names are provisional until contract inspection.
Use small reader/descriptor/conversion boundaries, not a general adapter registry.

- Preserve native numeric dtype where feasible and preserve validity separately;
  no silent float32 downcast or mask loss to fit a library.
- Missing cells must be distinguishable from valid zero. Do not use truthiness as
  validity. If a labeled dataset with value and validity variables is needed to
  preserve integer precision, prefer that over silent coercion.
- Derive cell-center coordinates from verified transform/axis evidence. Preserve
  native orientation, and make any intentional presentation reordering explicit.
- Initial labeled coordinates may be limited to rectilinear grids. Explicitly
  reject unsupported rotation/shear or provide correct two-dimensional coordinates;
  never fabricate one-dimensional axes for them.
- Reject unsupported multiband, category, temporal or malformed inputs clearly.
- Do not perform implicit reprojection, unit conversion, resampling or aggregation.
- Keep scientific dependencies optional: core installation, connection, Jobs,
  handles and exports must work without xarray or raster libraries installed.
- Define file ownership, close/lifetime behavior and copy/lazy semantics. No hidden
  network access during ordinary inspection of an already opened local artifact.
- Bound eager materialization using an explicit documented byte/cell budget;
  preflight declared dimensions/dtype and mask overhead. Fail before allocation
  when unsupported or over budget. Do not claim chunked server reads or
  constant-memory array loading because file downloading is streamed.

## 4. Recorded provenance slice

Implement typed read-only access to the observation's actually available recorded
activities, inputs/sources and dependency edges, using existing authorized routes.
Expose identifiers and source facts; human-readable summaries must be derived from
those facts, not invented explanations of scientific causality.

- Root queries in the bound observation/context and verified Runtime origin.
  Do not forward credentials to origins discovered in lineage references.
- Fetch bounded pages/neighborhoods, not a whole twin by default. Document node,
  edge, page, depth and request budgets appropriate to the supported route.
- Preserve useful harmless unknown DTO fields; malformed identity/edge/state data
  must not produce apparently valid lineage.
- Distinguish available-but-partial, empty recorded result, unsupported route,
  denied access, unavailable service and malformed protocol response.
- Never translate authorization failures into empty graphs. Never imply unreturned
  inputs do not exist. Do not infer completeness from lack of another page unless
  the server contract establishes what that means.
- Explain unresolved references and handle cycles/shared dependencies without
  infinite traversal or duplicated apparent executions.
- Support explicit refresh and identify when data was fetched. Previously loaded
  records are not silently current after remote changes; no background event loop,
  automatic coherence or durable replay claim is required.
- A resolution plan is planned work, not proof of execution. Keep separately typed
  or labeled if included; a full resolution-plan browser is not required here.
- Read-only browsing must not submit observations, mutate the graph, cancel Jobs
  or acquire remote release ownership.

If authoritative links are missing, show the available portion and its limitations.
Do not turn export URLs, source filenames, timestamps, or client-generated guesses
into purported server provenance.

## 5. Combined consistency contract

The export and provenance fetch may be separate operations over changing remote
state. Matching observation IDs establishes an association, not a consistent
revision-pinned snapshot.

Expose independently fetched artifact/lineage identity and time, plus supplied
revision/commit information if authoritative. Say explicitly when cross-fetch
consistency is unknown. If comparable authoritative revisions disagree, surface
the mismatch; do not label the pair coherent. Never infer portable reproducibility
from successful analysis and available provenance.

Offline artifact inspection and retained lineage inspection do not provide offline
Reasoner execution, server permission authority or queued mutation replay. Avoid
introducing a new persistent graph cache/schema unless the bounded workflow truly
requires one. Keep existing saved references and restoration behavior compatible.

## 6. Implementation increments

1. **Inventory and API sketch:** report actual contracts, supported representation,
   lineage query and consistency limitations; select one useful default for reversible
   ergonomics rather than blocking on every signature preference.
2. **Reader/descriptors:** metadata/validity decoding, bounded loading, optional
   dependencies and focused offline fixtures; no changes to native export bytes.
3. **xarray adapter:** faithful labeled representation and lifecycle tests, including
   operation without optional dependencies in the core package.
4. **Provenance facade:** typed bounded queries, partial/error/refresh semantics
   and exact wire-contract fixtures. Deliver independently of adapter internals.
5. **Integrated example and acceptance:** reuse shared functional scenarios and
   scientific oracles; document actual supported behavior and unresolved gates.

Keep changes independently reviewable and separate organization-only changes from
feature changes in the completion report. Do not commit unrelated work automatically.

## 7. Validation and acceptance

### Offline checks

Retain existing tests, coverage gates, lint and package checks; report exact commands
and interpreter/package versions. Do not invent exclusions or weaken assertions.

Scientific cases must include nonuniform values, valid zero, a missing cell,
float64 precision beyond float32, source units distinct from CRS units, asymmetric
dimensions, shifted origin, cell centers and orientation. Include missing/conflicting
metadata, malformed/unsupported files, rotated/sheared input handling, budget
rejection and resource cleanup. Verify actual adapter values and coordinates, not
only descriptor round-trips.

Provenance cases must include partial graphs, shared dependencies/cycles, unresolved
references, supported empty results, denial, unsupported routes, malformed data,
bounded traversal/pagination, explicit refresh and absent/mismatched revisions.
Validate that browsing never sends scientific mutations or leaks credentials.

Build/install the core package without scientific extras and verify ordinary
connection/handle/export imports still work. Test the selected extra in an installed
package environment too. Use independent known-answer fixtures; do not make both
the production decoder and its oracle derive expected values from the same decoder.

### Controlled real-service workflow

Use existing owned runners with a pinned compatible server/worldview and the exact
corrected geospatial component. Do not assume the current Python branch itself is
a certified server build. Preserve separate server identity/integration gates.

1. Compute a controlled nonuniform numerical field (for example 5 by 4), with
   independently specified coordinates/values, valid zero and missingness. If a
   missing-cell fixture needs unsupported production changes, report that blocker
   rather than faking remote evidence.
2. Save a secret-free observation reference and close only local client resources.
3. In a fresh process, restore the same accessible observation without resubmission.
4. Select and stream its applicable native GeoTIFF without altering bytes.
5. Independently check the real exported values, shape, dtype, transform, CRS,
   source units and mask; then verify the adapter agrees with that oracle.
6. Compute a simple independently known statistic using the validity mask.
7. Query recorded provenance for the same observation and check supplied activities/
   sources against independently established fixture expectations. Distinguish
   actual executed links from resolver plans; report unexposed facts honestly.
8. Exercise relevant unauthorized access with an authorized positive control;
   denied artifact or lineage access must not yield apparently successful empty data.
9. Verify explicit refresh/consistency reporting at the available evidence level.
10. Release only owned test scopes; close readers/transports and confirm owned
    process/port cleanup. Preserve primary errors when cleanup also fails.

Do not require synthetic records to claim unavailable server facts. If real
provenance acceptance is blocked, report a partially completed combined milestone:
reader acceptance and offline provenance checks are not full end-to-end acceptance.

Shared scenarios belong in `python-client/tools/workflows/`, named real-service tests
in `python-client/tests/functional/`, selected through the existing runner. Avoid
duplicate scenario implementations in examples or benchmarks.

## 8. Explicit non-goals and stop boundaries

- Complete graph caching/navigation, event subscriptions, durable replay or automatic
  synchronization; portable execution replay or provenance reconstruction.
- General temporal cubes, categorical dictionaries, arbitrary dimensionality,
  contextual unit conversions or new server window/chunk/snapshot protocols.
- Provider/worker SDK, Spark, async framework or new Runtime execution machinery.
- Automatic mutation retries, failover under existing handles, global discovery,
  new permissions, remote authoring or public release certification.

Stop expansion and report a precise decision when scientific mapping requires
guessing, truthful lineage requires new backend guarantees, or secure routing
requires new trust semantics. Continue independent bounded work where possible;
configuration and interfaces cannot manufacture server guarantees.

## 9. Completion report

Provide:

- Branch, actual base/candidate revisions, preserved preexisting changes and changed
  files grouped into organization, scientific reader, adapter, provenance and tests.
- Contract inventory, public API examples and provisional decisions with evidence,
  replacement seam, invalidation and migration implications.
- Exact commands/results for offline tests, coverage, lint, installed package checks
  and functional runs; exclusions, skips and blockers stated separately.
- Pinned client/server/worldview/component identities and secret-free evidence paths.
- Scientific metadata/precision/mask checks, lineage facts actually verified and the
  cross-fetch consistency guarantee actually available.
- Status by capability: offline implemented, controlled-service accepted,
  actual-deployment accepted, unsupported/backend-dependent.
- Remaining decisions and a focused draft contribution summary; no automatic push,
  PR creation or claim of maintainer acceptance.
