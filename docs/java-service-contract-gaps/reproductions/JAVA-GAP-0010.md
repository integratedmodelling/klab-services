# JAVA-GAP-0010 — Published native continuous GeoTIFF loses measurement-unit metadata

Generated from `gaps.yaml`; simulator evidence is not native acceptance.

## Classification and affected contract

- Java: **CONFIRMED_DEFECT**; current execution **REAL_NOT_RUN**; historical **REAL_FAIL**.
- Service: geospatial; component: GeodataIO continuous GeoTIFF / Geotools.stateToCoverage.
- Original roadmap: RC-P2.1, RC-P2.5, CAP-09, CAP-15.
- API: GET /export/OBSERVATION/{urn}.
- Request/schema: python-client/docs/stream-exports.md.
- Tested Java build/environment: services a0a013a188fb752907f2d143a61b86f4a9cb0a0b; geospatial1.0-20261025.094856-223 archive c5192eec7fe1d47a5b4a4750c334ea3ff88520afe8d096e04c8640c3cfb289c6.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

Historical native 5x4 GeoTIFF has dataset.units==(None,) and no band unit tags despite observation units m. Values123.25/CRS/transform pass. Retained TIFF SHA940b21aee3d6eaa7e70c7baef52d29d48d577398b0352ccb0e43347ca37ba2ce.

## Independent simulator implementation and fixtures

Separately pinned synthetic TIFF has explicit scientific metadata; SDK never repairs native bytes

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-ADAPTERS-001, SIM-EXPORT-001

Exact executable controls:

- `python-client/tests/unit/test_nonuniform_export_oracle.py`
- `developer-tools/java-gap-governance/tests/test_native_export_capture.py::test_historical_native_unit_defect_is_preserved_without_rewriting_values_or_bytes`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0010 --output simulated-JAVA-GAP-0010.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0010 --output real-JAVA-GAP-0010.json
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

Original reproducer/evidence: https://github.com/integratedmodelling/klab.component.geospatial/issues/10

Keep upstream product bug open. Simulator success and a separately corrected local component do not verify an official published fix; values/spatial/mask/precision must stay unchanged.

- https://github.com/integratedmodelling/klab.component.geospatial/issues/10
