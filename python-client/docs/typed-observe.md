# Typed observation requests

The maintained typed-request facade provides validated observation and context
options while retaining the existing Job lifecycle and raw submission APIs.
It is independent of saved handles and changes no Java contract/session policy.
The non-local federation-session concern in
`docs/PYTHON_CLIENT_FEDERATION_COMPATIBILITY.md` remains inherited and unresolved.

Historical candidate hashes, dated validation evidence and the independent merge
handoff are preserved in [Typed observation validation history](typed-observe-validation.md).

## Issue and PR boundary

This checkout implements [issue #87](https://github.com/integratedmodelling/klab-services/issues/87)
for [PR #85](https://github.com/integratedmodelling/klab-services/pull/85).
Its scope is typed request/context construction, supported option encoding,
binding validation and retained submission/Job behavior. The test matrix below
maps those implementation criteria to maintained regressions and live acceptance.
Publication and exact published-candidate checks remain required before closing
the issue; a local commit alone does not update the PR.

Saved-handle work belongs to #88/PR #86; federation integration and its remaining
rollout checks belong to #89 in a separate checkout. Geometry/metadata/constraints
provide foundations for roadmap export and provenance work, but this PR delivers
neither an exporter nor full plan/provenance navigation. Reference this commit
from those later issues rather than expanding or closing them through #87.

The client PR retains integration base `ad2bc949c` on `feature/python-client`.
The develop/federation merge is published separately under #89 to that same
integration branch; neither client feature is stacked on the other. Live checks
identify the separate server candidate explicitly. Promotion to develop remains
subject to the server issue's remaining gates.

## API

```python
from klab_client import ContextOptions, ObservationOptions, ObservationRequest, RectangularGeometry

context = session.create_context(options=ContextOptions(persistence="EXPLICIT_ACTION"))
region = context.observe(ObservationRequest(
    "earth:Region", geometry=RectangularGeometry(200000, 200500, 6000000, 6000400, 5, 4)
)).result(120)
job = context.within(region).observe("geography:Elevation in m",
    options=ObservationOptions(namespace="my.models", project="my.project"))
observation = job.result(120)
cells = observation.fetch_data([0, 1], curve="D2_YX")
```

`observe` accepts a frozen `ObservationRequest`, a resolved `ObservableImpl`,
or definition text (resolved through the configured Reasoner). It returns the
existing Job immediately after acceptance. Closing the client only closes HTTP.
Timeout, cancellation, failure, unavailable jobs and ambiguous mutations retain
the established distinctions; no retry, hidden context creation or disposal occurs.

Requests carry optional URN/name, checked geometry, finite JSON metadata and
explicit extensions. Their wire snapshots are defensive and deterministic;
metadata/extensions are recursively read-only. Extension keys cannot overwrite
identity, discriminators or validated options. Unsupported/unknown semantic
activities cannot become locally authoritative defaults. Unknown constructor
keywords fail rather than being ignored.

Contained legacy observable/geometry DTOs can still be mutated, but those changes
do not alter an existing request's submission snapshot. The current refactor makes
nested read-only metadata and extension mappings reusable in constructors or
`dataclasses.replace`. Options
retain explicit constraints separately from the effective submission constraints,
so copying lexical options does not duplicate generated constraints. A bound
observer's identity is snapshotted for submission; copying options revalidates
the supplied observer object and its current binding.

## Supported options and source map

| Public option | Wire placement / implemented consumer |
|---|---|
| namespace / project | ResolutionNamespace / ResolutionProject constraints; ModelVisibility and ModelKbox visibility, Prioritizer lexical ranking |
| scenarios | Scenarios constraint; ModelVisibility participation and Prioritizer scenario ranking |
| observer | Observer constraint and selected `#observerId` token; ServiceContextScope.withResolutionConstraints/withObserver |
| typed constraints | Only the four kinds above; immutable, nonempty payloads and no duplicate kinds |
| geometry | ObservationImpl geometry DTO; Runtime observation registration/scale alignment |
| context persistence | ConfigurationImpl Persistence enum; only at explicit new-context creation |
| inline grid / named grid | ConfigurationImpl.gridDefinition/gridUrn; KnowledgeGraphNeo4j context preparation and GridAlignmentSupport |

Authoritative DTOs: ResolutionRequest, ResolutionConstraintImpl (`type`, `data`),
ConfigurationImpl, GeometryImpl. Lexical options use implemented constraints,
not invented headers. `GridOptions` uses numeric native-CRS span/anchor, validated
finite values and boolean strict/snap. Named grid strings are explicit URNs.
`RectangularGeometry` is deliberately a square-cell EPSG:3857 facade; use an
already checked GeometryImpl for other supported geometry. Server-resolved CRS,
grid availability and actual alignment remain server decisions.

**Explicitly unsupported:** forced selected-model selection (`UsingModel` is
emitted by Modeler but has no server consumer), whitelist/blacklist and arbitrary
constraint kinds. Context-creation observer settings are rejected; select an
existing observer at submission. Grid is not a request-level substitute for
context configuration. Typed and raw context settings cannot be mixed, and an
attached context is never recreated/mutated to change its lattice.

Bound observer objects must already describe a persisted AGENT and be bound to
this Runtime/root context. Available client/configuration service IDs must agree;
differing known source/destination Runtime IDs cannot alias merely because their
URLs match. Legacy missing IDs retain URL/root checks plus server authorization,
and do not establish service-incarnation identity. Integer observer IDs are
validated signed-64-bit IDs and rechecked by the server. Every ID already present
in focus/observer tokens is range-checked before resolution or submission, with
a decimal-text bound before integer conversion. Existing focus and
observer selections are preserved; conflicting observer selections and mismatched
root tokens fail before submission. Raw Context.submit/ObservationImpl/mapping
compatibility is unchanged.

Focusing on a bound observation and reading its scientific cells use the same
available-identity validation as submission: root IDs and canonical Runtime URLs
must match, and configured/cached/reported Runtime IDs must be consistent. A
known foreign Runtime cannot be relabelled by first turning its observation into
a focused view. Raw integer focus IDs have no origin information and remain
subject to server checks; legacy missing service IDs do not prove incarnation identity.

## Behavioral and coverage matrix

| Requirement/failure | Named tests |
|---|---|
| All supported request forms, DTO/header identity | observe_resolved_preserves_focused_observer_scope_and_constraints, string_resolution_is_explicit_and_preserves_extensions |
| Every constraint and invalid combination | each_constraint_is_defensive, invalid_constraints, invalid_options, unsupported_constraints_never_silently_degrade |
| Observer identity/focus and root mismatch | observer_object_is_bound_defensive_and_never_foreign, validation_and_unconfigured_reasoner_prevent_mutation |
| Observer AGENT/type and stable identity omissions | bound_observer_requires_known_agent_semantics_before_any_http, same_endpoint_different_known_runtime_rejects_bound_observer, observer_same_runtime_and_documented_legacy_unknown_identity, inconsistent_or_invalid_known_observer_runtime_identity_is_local, reported_identity_fallback_and_destination_contradiction, destination_known_identity_is_checked_even_without_observer |
| Existing selection overflow and valid boundaries | existing_selection_range_is_checked_before_resolution_or_mutation, existing_supported_selection_boundaries_remain_exact |
| Bound focus/read origin and canonical URLs | test_runtime_binding.py: different_known_runtime_at_same_url_and_root_fails_before_http, same_runtime_equivalent_urls_and_legacy_unknown_ids_remain_supported, contradictory_or_invalid_binding_identity_is_local, different_root_context_is_rejected_before_http |
| Typed context vs request settings | context_options_encode_grid_only_at_context_creation, context_creation_facade_and_raw_compatibility, invalid_context_options |
| Geometry/finite numbers/IDs | rectangle_invalid_numbers, rectangle_bounds_and_square_cell_contract, rectangle_derived_extents_and_cell_sizes_are_finite, invalid_grid |
| Frozen/copy isolation/extensions/old payload parity | typed_objects_are_defensive_and_wire_encoding_repeatable, checked_geometry_snapshot_and_old_new_payload_equivalence, reserved_extensions_are_rejected_before_http |
| Reusable nested mappings and copied options | nested_read_only_metadata_and_extensions_can_be_reused, read_only_mapping_cycles_and_non_json_values_still_fail, copied_options_do_not_duplicate_generated_constraints, copying_bound_observer_options_preserves_identity_checks, observer_option_copies_revalidate_current_inputs_without_changing_original_snapshot |
| Unsupported semantic/model/option contracts | unknown_activities_cannot_be_invented, selected_model_is_explicitly_unsupported, error_notifications_and_locally_invented_semantics_are_rejected |
| Invalid JSON structure and cycles | invalid_request_structure, circular_json_is_rejected |
| 401/403/404/5xx, lost mutation, no retry/redaction | submit_errors_are_classified_and_never_retried, lost_submission_outcome_and_resolution_failure |
| Job failed/interrupted/expired/unknown/wait-resume | typed_facade_preserves_job_terminal_states, typed_wait_timeout_keeps_handle_and_can_resume |
| No hidden network/mutation/disposal | requests are constructed outside HTTP calls; exact counts in facade tests; retained baseline offline guard and close/release tests |

New production `requests.py` requires **100% executable lines and branches**;
all changed production lines/arcs are checked separately from preexisting gaps.
No exclusions or coverage pragmas are used. Coverage/lint remain dev extras.

The Runtime-binding correction passed **297 maintained tests** on Windows Python
3.12, including the shared focus/read regressions. New-module and changed-code
coverage gates and targeted lint passed. Historical cross-version, package and
live results are retained with their own source identities in the linked validation
record; a changed candidate requires fresh checks before publication.

The fixed coverage comparison base measures this contribution against its
integration baseline. When integrating, retain the new-module gate and select
the repository's maintained comparison base for future changes.

Run these commands from `python-client` with the dev extras installed:

```powershell
python -m pytest -q --cov=klab_client --cov-branch --cov-report=json
python tools/check_feature_coverage.py coverage.json --base ad2bc949c167e1c2e370973ce4427fe67cb1cc30 --new-module src/klab_client/requests.py
python -m ruff check src/klab_client/requests.py tests/unit/test_typed_requests.py tools/check_feature_coverage.py --select E9,F63,F7,F82
python -m build
```

## Reproducible live acceptance

`examples/typed_observe.py --state-dir C:\prepared-state` runs only on an explicitly
prepared synthetic fixture. It verifies the nonuniform 20-cell field, valid zero
and missingness against independent coordinates; typed scenario selection produces
234.5 m, a private namespace produces 66.5 m, and a project-private model produces
7.5 degree_angle. This demonstrates actual server option effects, not merely sent
fields. It also checks returned inline lattice metadata and observer/focus tokens.
Failure retains printed IDs; successful cleanup is explicit and test-owned.

Build a fresh fixture and include this example with:

```powershell
python tools/run_local_acceptance.py --java C:\jdk\bin\java.exe --worldview C:\pinned-imod --state-dir C:\existing-parent\new-state --build --typed-observe --samples 1 --duration 1
```

The maintained live regression
`test_bound_computed_quality_is_rejected_as_observer_without_any_http` checks
an actual computed quality: rejection must precede both semantic resolution and
submission. A remote authorization error does not satisfy this local-validation
assertion.

The broader federation/promotion gate and production/provider, bulk/temporal,
worker and automatic coordinate-conversion capabilities are not certified here.
