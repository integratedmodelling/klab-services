# JAVA-GAP-0001 — Resources remote numeric scanner is null on preserved USGS workflow

Supporting runner/evidence only. The canonical contract, status and inline reproduction are in
[JAVA_GAPS_FOR_FERDINANDO.md](../JAVA_GAPS_FOR_FERDINANDO.md#java-gap-0001).
Generated from the synchronized index; simulator evidence is not native acceptance.

## Engineering decision and immediate next action

- Classification: **REPRODUCED_JAVA_FAILURE**; workflow: [Authentic elevation hits null scanner](../JAVA_GAPS_FOR_FERDINANDO.md#numerical-execution).
- User-impact priority: P0; Java owner of record: UNASSIGNED (do not infer assignment from issue authorship).
- Required native input / owner action: Evidence custodian must package the original USGS 20x20 crop, oracle, exact request and NullScannerReproducer.java; Ferdinando must identify an approved maintained build and scanner adapter. Full Runtime reproduction is not currently portable.
- Expected after Java change or usage clarification: The recovered original Runtime request selects the same USGS model/resource, returns 400 oracle cells instead of empty/Error, and preserves declared traversal, masks, precision and metre provenance; separately verify owned cleanup.
- Historical observations and source findings retain their original scope; current-build execution is not inferred.

## Classification and affected contract

- Independent status triplet: CONFIRMED_DEFECT; SIMULATOR_PASS; REAL_NOT_RUN.
- Java: **CONFIRMED_DEFECT**; current execution **REAL_NOT_RUN**; historical **REAL_FAIL**.
- Service: resources; component: ResourcesProvider.contextualizeSynchronous / RasterEncoder.encodeFromCoverage:183.
- Original roadmap: RC-P2.1, RC-P2.3, RC-P2.5, CAP-09.
- API: POST /runtime/api/v1/submit.
- Request/schema: python-client/docs/contracts.md.
- Tested Java build/environment: Installed Modeler services build202610301844; pinned geospatial artifacts in remediation report.
- Evidence basis: Repository/evidence audit only; not fresh Java execution; last verified 2026-11-06.

## Expected behavior / native compatibility acceptance

- Pin the maintained Java build, identity, input and exact request contract
- Run the linked Python flow against that real Java tuple without simulator fallback
- Verify applicable authorization, lifecycle, cancellation, schema and independent scientific oracles
- Retain evidence before changing Java status to JAVA_FIXED_VERIFIED or lifecycle to REAL_VERIFIED/CLOSED

## Actual observed Java behavior

Historical Job9 empty:true/Error; NullPointerException: Cannot invoke Storage$DoubleScanner.add(double) because filler is null. Installed bytecode passes aconst_null at offset180 to Adapter.encode. Not a new run.

## Independent simulator implementation and fixtures

Frozen authentic400 cells delivered over isolated HTTP; not Runtime-produced science. Separate provider-avro scenario serves finite independent fastavro golden raw DataRequest/Instance bytes over source-defined Resources/contextualize and Job/retrieveData routes; no SDK codec dependency or scientific reasoning. Direct synthetic provider success does not reproduce or repair the scanner defect.

Scenario index: `developer-tools/klab-service-simulator/contract-cases.json`.
Scenarios: SIM-USGS-001, SIM-GRID-001, REPLAY-001, SIM-PROVIDER-AVRO-001

Exact executable controls:

- `developer-tools/klab-service-simulator/tests/test_vertical.py::test_native_async400_and_authentic_recorded_delivery_not_java`
- `python-client/tests/unit/test_provider_avro.py`
- `python-client/tests/unit/test_provider_data.py`
- `python-client/tests/unit/test_provider_handles.py`
- `developer-tools/klab-service-simulator/tests/test_provider_avro.py`

## Reproduction commands

From repository root with the test extras installed:

```powershell
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0001 --output simulated-JAVA-GAP-0001.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0001 --output real-JAVA-GAP-0001.json
python developer-tools/java-gap-governance/native_probe.py --gap JAVA-GAP-0001 --output preflight-JAVA-GAP-0001.json
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

Original reproducer/evidence: notes/python-roadmap-execution/MODELER_REAL_DEM_REMEDIATION_REPORT.md#12-remaining-failures--smallest-next-action

Require attested scanner fix and authorized original400-cell/orientation/mask/unit oracle comparison. Historical report names NullScannerReproducer.java but that path is not present in this checkout; executable real reproducer custody is an explicit evidence gap, not a fabricated test reference. Does not imply all Java runtimes fail; staging harness has distinct native working flows. No new upstream issue is fabricated.

- notes/python-roadmap-execution/MODELER_REAL_DEM_REMEDIATION_REPORT.md
- python-client/docs/provider-binary-contextualization.md
- notes/python-roadmap-execution/evidence/provider-binary-source-authority-20261107.json
- notes/python-roadmap-execution/evidence/full-roadmap-provider-binary-20261107.json
