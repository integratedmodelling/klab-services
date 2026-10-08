# Typed observation requests

Base: upstream integration **ad2bc949c167e1c2e370973ce4427fe67cb1cc30**.
This branch is independent of saved handles and changes no Java contract/session
policy. Baseline checks on that exact source: 97 Python tests and the strict
56-case Java selection passed. The non-local federation-session concern in
`docs/PYTHON_CLIENT_FEDERATION_COMPATIBILITY.md` remains inherited and unresolved.

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

`observe` accepts an immutable `ObservationRequest`, a resolved `ObservableImpl`,
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

Observer objects must be bound to this Runtime/root context; integer IDs are
validated signed-64-bit IDs and rechecked by the server. Existing focus and
observer selections are preserved; conflicting observer selections and mismatched
root tokens fail before submission. Raw Context.submit/ObservationImpl/mapping
compatibility is unchanged.

## Behavioral and coverage matrix

| Requirement/failure | Named tests |
|---|---|
| All supported request forms, DTO/header identity | observe_resolved_preserves_focused_observer_scope_and_constraints, string_resolution_is_explicit_and_preserves_extensions |
| Every constraint and invalid combination | each_constraint_is_defensive, invalid_constraints, invalid_options, unsupported_constraints_never_silently_degrade |
| Observer identity/focus and root mismatch | observer_object_is_bound_defensive_and_never_foreign, validation_and_unconfigured_reasoner_prevent_mutation |
| Typed context vs request settings | context_options_encode_grid_only_at_context_creation, context_creation_facade_and_raw_compatibility, invalid_context_options |
| Geometry/finite numbers/IDs | rectangle_invalid_numbers, rectangle_bounds_and_square_cell_contract, rectangle_derived_extents_and_cell_sizes_are_finite, invalid_grid |
| Frozen/copy isolation/extensions/old payload parity | typed_objects_are_defensive_and_wire_encoding_repeatable, checked_geometry_snapshot_and_old_new_payload_equivalence, reserved_extensions_are_rejected_before_http |
| Unsupported semantic/model/option contracts | unknown_activities_cannot_be_invented, selected_model_is_explicitly_unsupported, error_notifications_and_locally_invented_semantics_are_rejected |
| Invalid JSON structure and cycles | invalid_request_structure, circular_json_is_rejected |
| 401/403/404/5xx, lost mutation, no retry/redaction | submit_errors_are_classified_and_never_retried, lost_submission_outcome_and_resolution_failure |
| Job failed/interrupted/expired/unknown/wait-resume | typed_facade_preserves_job_terminal_states, typed_wait_timeout_keeps_handle_and_can_resume |
| No hidden network/mutation/disposal | requests are constructed outside HTTP calls; exact counts in facade tests; retained baseline offline guard and close/release tests |

New production `requests.py` requires **100% executable lines and branches**;
all changed production lines/arcs are checked separately from preexisting gaps.
No exclusions or coverage pragmas are used. Coverage/lint remain dev extras.

```powershell
python -m pytest -q --cov=klab_client --cov-branch --cov-report=json
python tools/check_feature_coverage.py coverage.json --base ad2bc949c167e1c2e370973ce4427fe67cb1cc30 --new-module src/klab_client/requests.py
python -m ruff check src/klab_client/requests.py tests/test_typed_requests.py tools/check_feature_coverage.py --select E9,F63,F7,F82
python -m build
```

## Exact-candidate live acceptance

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

Executed final production candidate **3957f39fe** (implementation introduced at 0ca4fc4ce):

* 230 offline tests passed, 5 live deselected, on Windows Python 3.12 and the
  installed-package Ubuntu/WSL Python 3.11/3.12/3.13 matrix. Each Linux version
  built wheel/sdist and passed the same coverage/changed-code gates.
* `requests.py`: 242 executable statements and 100 branches, all covered, with
  zero excluded lines. Changed existing production lines/arcs passed separately.
* Ruff targeted checks passed. A fresh dependency-light Windows Python 3.11
  environment installed the wheel and exercised imports/construction without HTTP.
* Exact-candidate fresh run `klab-typed-review-final`, **2026-10-08
  01:57:27–02:00:03 UTC**: the typed example, 2 existing live tests, cold ACL checks
  and all smoke phases passed. Cleanup confirmed all five JVM exits. Java source
  is unchanged from the recorded baseline; compiled/source hashes are retained.

Evidence is external under `C:\Users\lumsd\AppData\Local\Temp\opencode`:
`typed-coverage.json`, per-version `typed-3.xx` CI/coverage/XML/distributions,
`typed-handles-baseline-java.log`, and `klab-typed-review-final` reports/provenance.
The first attempted matrix rerun refused existing venv directories before tests;
fresh SHA-qualified environments were then created and the complete matrix passed.
The final Python 3.13 sdist build encountered a local shared-checkout staging race;
its tests/coverage had passed, and a serialized wheel/sdist rebuild then passed.
The external report preserves the failed attempt and successful retry. Hosted
matrix jobs use separate checkouts, unlike this local parallel runner.

Review: identity/scope and observer binding, immutable snapshots, model-option
rejection, mutation ambiguity, wire compatibility and negative controls were
checked against the Java consumers. The fixture confirms real option effects;
the bare integer observer form remains subject to server authorization/validation.
No saved-handles dependency or Java policy change is included.

The broader federation/promotion gate and production/provider, bulk/temporal,
worker and automatic coordinate-conversion capabilities are not certified here.

## Independent merge handoff

Shared additions with saved handles are expected in package exports, dev extras,
the CI coverage step and README/API inventory. Merge exports additively and run
the coverage checker for both new modules. Neither feature imports the other.
An actual merge-tree check found only these content conflicts: coverage CI,
`docs/public-api.md`, package `__init__.py` and local-runner feature flags.
Resolve each additively; Client implementation, README, dev extras and identical
coverage tooling merge automatically. This is a handoff, not a merged branch.
