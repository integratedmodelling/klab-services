# k.LAB Python client: planning guide

Primary planning reference, supplied by Eric on 14 October 2026 from
`KLAB_Python_Client_Planning_Guide.md`. It supersedes routine use of the historical
roadmap for current status/defaults, while preserving maintainer authority and
actual contracts. The complete historical Part II remains in
[PYTHON_CLIENT_ROADMAP.md](PYTHON_CLIENT_ROADMAP.md), unchanged from the supplied
snapshot; its architecture, capability matrix, phases, acceptance scenarios,
integration gate and source links are retained, not discarded.

## Authority and checkpoints

The supplied guide was prepared by source/docs inspection, not new service tests.
Its client checkpoint was `a11462ddcf7aa0e2643c1f44faad95e8dd9fed36`, develop
`3b01ceaa907000ffccafac97444029027c81d30b`. Assessments are not maintainer approval.
This implementation starts at `9c7c371539ff31a05238cfc7355395ca05f21ace` on
`feature/python-client-connection`, based on current `feature/python-client`.

Reconciliation: #84 foundation, #85 typed requests, #86 saved handles and #92
streamed exports are merged. The supplied 630-test statement is historical;
the newer branch records 634 offline tests and a pinned buildable GeoTIFF component
patch. Its corrected artifact is not an upstream distribution release. #89/#90
server identity/permissions integration and separate-deployment gates remain open.
New upstream changes must be reconciled rather than silently discarded.

## Completion targets

1. **Dependable scientific client:** authenticate, find usable services, submit work,
   retrieve meaningful results, inspect provenance and reconnect later.
2. **Full remote user client:** additionally cover implemented Engine/Modeler semantics,
   assets, graph, authoring and server-behavior workflows; not Java method-count parity.
3. **Provider/worker SDK or embedded runtime:** a separate product with execution and
   deployment contracts; not required to finish the remote client.

## Delivery principle and reversible decisions

Proceed on documented assumptions supported by server behavior. Choose one useful
default, put unsettled policy behind a small replacement boundary and test provider/
policy substitution without changing scientific workflows. Do not wait on every
signature/preference. For each provisional choice record evidence, default, scope,
seam, invalidation, migration impact and acceptance tests.

Interfaces cannot create absent durable replay, revision authority, safe mutation
retries or scientific coordinates. Seek architectural agreement when correctness or
lasting cross-system meaning depends on it. Label implementation and live acceptance
separately; never present choices as confirmed maintainer policy.

## Connection default

Implement native direct **Hub/ENGINE-certificate authentication** plus configured
issued tokens. A k.LAB certificate is not generic TLS mutual authentication. Inspect
native certificate/properties/DTO/Hub routes. Use replaceable credential providers
and separate bootstrap/catalog adapters; the connection owns profiles, trusted
endpoints, readiness, selection and scoped peer setup. Existing Client owns science.

Preserve origin-bound credentials, verified TLS, no automatic redirects, explicit
credential sources, local-only close and precise login failure without anonymous
fallback. Dashboard sign-in is service-specific, not an interchangeable scientist
login. Engine attachment awaits an actual verified interface.

Reacquire only through implemented provider behavior; no invented refresh endpoint
or replay of an ambiguous mutation. Principal/issuer changes invalidate assumptions.
Saved remote identities and server authorization remain authoritative.

## Complete capability plan

All original capability/phase details remain in the historical Part II. Initial
slices, replaceable seams and difficult shared contracts are:

| Capability | Initial default / replaceable seam | Shared-contract boundary |
|---|---|---|
| Identity/onboarding | Certificate/Hub and tokens, expiry diagnostics, secret-free profiles; provider/bootstrap/optional secret-store adapter | Issuer/audience/delegation and group meaning |
| Catalog/selection | Authorized bounded catalog, explicit choice, readiness diagnostics; discovery/polling/selection policy | Global trust/membership |
| Cross-service/deployment routing | Authorized providers and origin-qualified routing; provider lookup/route policy | Migration, ownership generations, failover, global placement |
| Scopes/lifecycle | Supported typed options, saved handles, explicit detach/release; wire/configuration codecs | Persisted identity, suspend/delete/quiescence, continuation |
| Semantics | Meaningful objects, supported remote search/predicates/composition; DTO/facade | Worldview/knowledge identity and revisions |
| Observations | Supported observe/register/query/focus and model/lexical/grid options; typed constraints/capability checks | Unsupported constraints or new commit authority |
| Planning/explanation | Typed existing Dataflow/diagnostics; decoder/presentation | Portable replay/reconstruction |
| Jobs/cancellation | Existing states, supplied progress, deadlines and unknown outcomes; polling/sync/async transport | Retention, idempotency, crash recovery/cancellation guarantees |
| Scientific values | Native exports and bounded sparse reads; readers/data-view facade | Chunks, stable snapshots, read leases/revision pinning |
| Space/time | Supplied CRS, axes, traversal, coordinates/time selections; typed descriptors/adapters | Missing or ambiguous scientific mapping |
| Units/categories | Native units/dictionaries/precision/masks and supported mediation; conversion adapter | Contextual area/duration conversion and dictionary/worldview meaning |
| Twin graph/provenance | Typed read-only partial graph with loaded/stale state and refresh; decoder/cache | Authoritative revision/commit visibility |
| Events/reconnection | Polling/scoped invalidation and resync; subscription/refresh policy | Durable replay/cursors/retention and gap-free convergence |
| Resources/models | Typed list/retrieve/resolve/query/dependencies; codecs/query interface | Provider routing and safe import/edit/publish/version/lock mutations |
| Behaviors/agents | One implemented invocation with outcomes/permissions; operation adapter | Durable execution/side-effect retry |
| Analysis | Optional NumPy/pandas/xarray/vector/raster over verified data; extras/adapters | Preserve scientific meaning rather than discard it for library fit |
| Compatibility/operations | Per-service inventory, exact fixtures, builds/support limits; feature probes/version adapters | Maintained mixed-version support |
| Provider/worker SDK | Separate experimental package if needed; shared codecs | Budgets, cancellation, output publication/provenance/isolation |

## Assumptions that can proceed

- Keep synchronous APIs; later explicit async shares codecs/state, not hidden loops.
- Scientific dependencies stay optional for basic connection and Jobs.
- Explicit service choice wins; auto-select only an unambiguous verified candidate.
- Bounded readiness polling/explicit refresh precede broad background monitoring.
- Graphs are partial/stale as appropriate; resync rather than promise replay.
- Preserve harmless unknown DTO fields; reject unknown correctness-affecting states.
- Expose implemented read-only resource/semantic/resolution/provenance routes.
- Preserve native bytes with readers above transport.
- Offline inspection is not offline reasoning or queued mutation replay.
- No automatic retry of ambiguous mutation; bounded read retry only when supported.

## Expensive-to-undo contracts

Persistent cross-Runtime identities require reference/cache migration; retain v1
handles and deliberate versioning. Issuer/audience/delegation fixes ownership of
credentials. Geometry/time/missingness/category guesses can corrupt analyses.
Revision-pinned chunks require server snapshots/leases. Replay/commit ordering
needs retention and visibility guarantees. Mutations need idempotency/locks/
transactions. Simulation continuation needs checkpoints. Placement/migration
affects authoritative ownership/ACLs. Worker execution/publication budgets and
isolation need separate feasibility work. Configuration cannot supply these guarantees.

## Revised implementation sequence

1. Connection and functional onboarding: two providers, profiles/catalog/selection,
   readiness/peer setup and genuine compute/export/recovery.
2. Scientific descriptors and one optional adapter, over verified representations.
3. Typed twin/provenance and resolution browsing with partial/stale/refresh semantics.
4. Remaining supported semantic/resource/observation ergonomics and precise limits.
5. Explicit async/scoped updates: bounded concurrency and resync, not durable replay.
6. Narrow supported authoring/behaviors with real version/conflict/permission contracts.
7. Demonstrated platform-dependent chunks/global routing/continuation/worker work.

Maintain the Phase 0 capability inventory and regressions across every step. This
sequence does not declare roadmap phases finished.

## Acceptance, reporting and integration

Track each capability as offline-implemented, controlled-service accepted, actual
deployment accepted, or unsupported/backend-dependent. Controlled Hub exchange is
not real Hub onboarding. HTTP success is not a scientific oracle. Keep independent
values/coordinates/masks, secret-free evidence, fresh-process restoration, positive
authorization controls and owned cleanup preserving primary failures.

Shared scenarios belong in `tools/workflows`, named real-service tests in
`tests/functional`, with existing runner selection; do not copy them into benchmarks.
Pin client/server/worldview/component/Hub evidence. Report exclusions/skips/missing
environments separately. Preserve existing coverage gates without invented exclusions.
Public release additionally needs support/version policy, deployment reproduction,
migration guidance and scale-appropriate evidence.

Historical identity-sharing diagnoses remain traceable in Part II. Later upstream
authorization work and client/server integration do not finish all original gates.
Reproduce old defects before assigning fresh work. Promotion into develop requires
no unacceptable Java regression at the exact combined candidate, relevant strict/
native identity tests, unchanged-baseline comparison as applicable, packaging,
contracts, science/lifecycle/restart evidence. Python-only work does not mandate
unrelated full server rewrites or every platform test.

## Source map and current milestone

Use `SCOPES.md`, `SERVICE_COORDINATION_AND_DISCOVERY.md`, `SERVICES.md`,
`HUB_IMPROVEMENT.md`, native Authentication/KlabCertificate/Engine DTOs,
EngineImpl/ServiceMonitor/ScopeAdvertisements/ServiceClientCatalog, and their
Java/Python tests. `CONNECTION.md` describes scientific relationships, not login.
The current connection implementation specification, contract inventory, assumption
ledger and results are in `python-client/docs/connection-layer.md` and
`python-client/docs/connection-verification.md` as those deliverables are completed.

The next bounded scientific analysis/provenance slice is now implemented in the
working feature branch: optional faithful raster/xarray reading and partial
read-only recorded neighborhoods. See [usage](../python-client/docs/scientific-analysis.md)
and [current validation/review](../python-client/docs/scientific-analysis-review.md).
This does not declare the broader graph, snapshot/replay or public-deployment
roadmap complete.

## Part II — Complete historical upstream roadmap

The full supplied historical snapshot remains at
[PYTHON_CLIENT_ROADMAP.md](PYTHON_CLIENT_ROADMAP.md), preserving every original
requirement, acceptance scenario, integration gate and source link. Interpret its
historical missing-feature statements through the current status above. This split
is a documentation-layout choice; no historical capability was dropped.
