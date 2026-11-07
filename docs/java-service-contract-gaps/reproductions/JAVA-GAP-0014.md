# JAVA-GAP-0014 — Synthetic grid metadata, selected temporal export and descriptor mappings need real tuple evidence

Generated from `gaps.yaml`; simulator evidence is not native acceptance.

## Classification and affected contract

- Java: **CONTRACT_PROVISIONAL**; current execution **REAL_NOT_RUN**; historical **REAL_NOT_RUN**.
- Service: scientific-data; component: GeometryImpl / StorageScan.Point / KlabServiceController.exportAsset / BaseService.
- Original roadmap: RC-P2.1, RC-P2.2, RC-P2.5, CAP-09, CAP-12, CAP-15.
- API: GET /runtime/export/OBSERVATION/{urn}.
- Request/schema: python-client/docs/native-async.md.
- Tested Java build/environment: NOT_RUN / exact native build and environment not established.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

NOT_TESTED general remote temporal/multishard/category/revision mapping. Native staging harness has real temporal and mediation source contracts; REST matching tuple not established here.

## Independent simulator implementation and fixtures

Synthetic metadata.grid/UNSPECIFIED offsets and finite event assignment over immutable TIFF; requested_slice is a request only. Pinned3x2x3float32 multiband TIFF preserves18values/masks/units with explicit caller RasterBandAxis, not native time/history. Separate pinned2x3int16 synthetic:CategoryCode export has no units; immutable caller RasterCategories assigns exact labels while preserving raw integer codes/mask and unknown-code refusal. No native category dictionary, ontology inference or committed revision is claimed. Both fixture point-offset mappings remain unestablished and explicitly422, not guessed.

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-GRID-001, SIM-USGS-001, SIM-EXPORT-001, SIM-ADAPTERS-001, SIM-TEMPORAL-EXPORT-001, SIM-OFFLINE-SNAPSHOT-001, SIM-DECLARED-BANDS-001, SIM-CATEGORY-CODES-001, SIM-READ-PLAN-ANALYSIS-001, SIM-SELECTED-READ-PLAN-001, SIM-REFERENCE-RASTER-LABELS-001

Exact executable controls:

- `developer-tools/klab-service-simulator/tests/test_scientific_adapters.py::test_nonuniform_native_raster_xarray_precision_mask_coordinates_and_source_trace`
- `developer-tools/klab-service-simulator/tests/test_offline_snapshots.py::test_fresh_sdk_only_process_inspects400_frozen_cells_offline_after_owned_release`
- `python-client/tests/unit/test_multiband_analysis.py`
- `developer-tools/klab-service-simulator/tests/test_declared_bands.py`
- `python-client/tests/unit/test_raster_categories.py`
- `developer-tools/klab-service-simulator/tests/test_category_codes.py`
- `python-client/tests/unit/test_read_plan_analysis.py`
- `developer-tools/klab-service-simulator/tests/test_read_plan_analysis.py`
- `python-client/tests/unit/test_reference_window_analysis.py`
- `developer-tools/klab-service-simulator/tests/test_reference_windows.py`
- `python-client/tests/unit/test_reference_raster_labels.py`
- `python-client/tests/unit/test_reference_raster_analysis.py`
- `developer-tools/klab-service-simulator/tests/test_reference_raster_labels.py`
- `developer-tools/klab-service-simulator/tests/test_reference_raster_notebook.py`
- `developer-tools/klab-service-simulator/tests/test_reference_raster_fixture.py`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0014 --output simulated-JAVA-GAP-0014.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0014 --output real-JAVA-GAP-0014.json
```

The real-mode command intentionally records REAL_NOT_RUN and exits3 without network calls.
It is an executable missing-adapter disposition, **not** an actual native test or a simulator fallback.

## Required native startup prerequisites and executable test plan

1. Obtain authorization for an owned disposable native fixture and its cleanup policy; never reuse unrelated Jobs.
2. Pin service/component build hashes, endpoint identities, principal roles, worldview and fixture revisions; consult MODELER_IDE_STATE.md and run its bounded read-only gates only for this live-dependent work.
3. Start the maintained service tuple using the operator's supported procedure. Python-only development does not authorize IDE/service repair.
4. Supply a reviewed bounded native adapter for the exact contract and run the linked public Python workflow with explicit real endpoints, no automatic retries or simulation fallback.
5. Capture request/response/error, positive/negative authorization, independent scientific checks where relevant and owned cleanup; compare with the acceptance criteria above.
6. If no reviewed adapter/build/permission exists, retain NOT_RUN. Do not invent native endpoints or turn simulator success into a Java fix.

Existing native test references: none reviewed for this complete gap

## Custody, related trackers and remaining questions

Original reproducer/evidence: developer-tools/klab-service-simulator/README.md

Unknown actual mappings stay unknown; caller-declared category labels do not establish native dictionary/block/revision semantics. Proposed klab.experimental.read-plans/1 pins explicit float64/2D/CRS/affine/north-to-south/reference-origin descriptors and complete hashed shards for three established numerical fixtures only; unsupported mappings refuse. Offline arrays revalidate complete snapshot/layout/masks and preserve source binding, content+localmanifesthashes; these are NOT native committed revisions, server signatures/authenticmetadata or provenancegraphs. Caller edits require separate derivative attestation. No stamping georeferencing/units or rewriting authentic TIFF. Historical native N/S and vector receipts remain distinct, not erased.

- https://github.com/integratedmodelling/klab-services/issues/123
- notes/python-roadmap-execution/evidence/ab-staging-vxii-source-reference-20261106.json
- python-client/docs/raster-categories.md
- notes/python-roadmap-execution/evidence/full-roadmap-raster-categories-20261107.json
- python-client/docs/read-plan-reference.md
- python-client/docs/read-plan-analysis.md
- notes/python-roadmap-execution/evidence/full-roadmap-read-plan-analysis-20261107.json
