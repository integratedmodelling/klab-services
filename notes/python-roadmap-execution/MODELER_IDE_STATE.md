# Persistent Modeler IDE state contract

## 1. Purpose and mandatory planning preamble

This is a local development reference, **not production certification or an IDE
repair request**. The checked-in contract is useful even when services are absent.
Python-only work must not acquire an unrelated Java-readiness prerequisite.

> Before work, read `notes/python-roadmap-execution/MODELER_IDE_STATE.md` and its evidence timestamps. Determine whether this task actually depends on live Java Modeler/graphdb services. If yes, run the bounded read-only state checker and record the gate(s) required. If no, continue Python-only work without requiring IDE readiness. Never infer a currently working science pipeline from service health. If an environment blocker is detected, classify it and proceed with independent A+B tasks rather than starting unsolicited IDE repair. At the end, update the state file only when new verified evidence changes a gate or a known issue.

The supplied source specifications are preserved byte-for-byte in `specs/`:
`KLAB_IDE_STATE_CONTRACT_SPEC.md` (SHA256 `6eae3b2b1ac164a53b66fa995c8c1b91da62c8a3ac32d706a99dab0a57676c8d`)
and `KLAB_AB_OUTSTANDING_ISSUES_AND_DEVELOPMENT_SPEC.md`
(`cdf99b0da2a4f93a32b9f3f7196ab01aa1b4b9f55bc19142b48f8df2e938a9d8`).

## 2. Read-only refresh and latest observation

From the checkout root, using an existing Python3.11+ environment (the installed
`local` extra's psutil enables process ownership inspection; never auto-installed):

```powershell
python notes/python-roadmap-execution/check_modeler_ide_state.py --deep --update-document
```

Use `--receipt <path>` for a chosen JSON receipt and `--baseline <prior-receipt>`
to record identity/build/worldview drift. Default receipts stay in the private
`$HOME/.klab/scientific-examples/usgs-3dep-virginia/evidence/` custody directory.
`--offline --update-document` makes **no network/process/Java checks**; it records
live state UNKNOWN without rewriting historical passes. Source SHA256, checkout
commit, machine-name digest, UTC, selected scope and error classes attribute each
receipt. Uncommitted checker source is identified by hash, not falsely by HEAD alone.

<!-- BEGIN IDE CHECK -->
Last checked UTC: **2026-11-06T14:20:57.464274Z**; checker v1; mode `deep-read-only`.
Source SHA256 `76b84a7f2cc63152f5e166a3854e9cb47289d70b0a3372ca7422794f9726ce8e`; checkout `573a51d46896a4733450a4f81da512566aa864bf`.

| Service | Current classification | Operational | Connected | Health | ID / observed build |
|---|---|---|---|---|---|
| reasoner | PASS | True | True | UP | 570b4341-cd02-4e27-a77f-dc35352ffc0d / 202610301844 |
| resources | PASS | True | True | UP | 4fa96242-5fa1-4647-ba24-cdc8b29e7549 / 202610301844 |
| resolver | PASS | True | True | UP | 85406374-4413-4503-a651-054b88c3f7d0 / 202610301844 |
| runtime | PASS | True | True | UP | 100bf613-81b4-4347-8194-aec59a615959 / 202610301844 |

Graphdb: `PASS`; spatial procedure read: `PASS`.
Current read-only gates: `{"G0": "PASS", "G1": "PASS", "G2": "PASS", "G3": "UNKNOWN", "G4": "UNKNOWN", "G5": "UNKNOWN", "G6": "UNKNOWN", "G7": "UNKNOWN", "G8": "NOT_APPLICABLE"}`.
Identity drift: `[]`.
G3/G4 are not freshly passed by procedure discovery or catalog reads; no contexts/Jobs submitted.
G5 remains a historical reproduced Java failure, not a new execution or a verified fix.
<!-- END IDE CHECK -->

The generated block and JSON are a **snapshot**, not a watchdog. Freshness policy:
run once at the start of a task dependent on this live IDE; checks older than24h,
missing/malformed timestamps, future timestamps or absent checks are UNKNOWN.
Even a younger snapshot is not a substitute for the start-of-task check. A newer
build/service identity is drift, not automatically failure or scientific acceptance.
Exit0 means current G0/G1/G2 read-only checks pass; exit2 reports an observed failed
gate; exit3 means unknown/unavailable/incomplete, including ordinary offline mode.
G3–G7 are deliberately not rerun by this checker. `--deep` remains nonmutating.

Checker v2 review adds finite local budgets: at most8 JARs per service/plugin
directory,128MiB per hash,2MiB per local JSON/HTTP response,16KiB per credential,
3s socket timeout per HTTP operation,20s Java-helper subprocess and no retries.
Oversized/unreadable hashes are null/unknown; truncated inventories are explicit.
Timeouts are per operation, not a global wall-clock deadline. The preserved live
receipt above was collected with **v1**, whose exact source hash is retained;
v2 is validated offline, not falsely attributed a second live refresh.

The fresh receipt's `semantic.candidate` bean INVALID/logical NOT_RUN is distinct
from accepted generation2 serving consistently. G1 records served admission, not
candidate validation or a fresh `geography:Elevation` recognition operation. The
separate bounded SDK semantic check is in `evidence/ab-sdk-semantic-corrected-20261106.json`.
The warning projection returned no warning messages; six historical advisories
are not claimed resolved.

## 3. Semantic and model inventory

Historical source: latest on-disk [remediation report](MODELER_REAL_DEM_REMEDIATION_REPORT.md),
sections3,6,8,12–13, **2026-11-06 03:11:16 UTC**, status **REPAIR_PARTIAL**.
Raw receipts remain under private custody; key receipt `spatial-final-preservation.json`.
Selected build **202610301844** / Temurin21.0.12.1+1 is an observation, **not** a
hardcoded timeless expected distribution. The new checker discovers live processes,
parents, executable/classpath directory, build and service IDs; PIDs may change.

Historical Resources: `imod` and `scientific.usgs.virginia` projects;1namespace,
1model,1resource,4adapters. Example project's manifest explicitly declares `imod`;
namespace `worldviewBound=false` remains a separate API fact. Exact fixture identities:

- Model: `scientific.examples.usgs_virginia.elevation-quantifier`.
- Resource: `resources.eric.lumsden:staging:usgs:virginia20211229`.
- Observable: `geography:Elevation in m`; resource owning service identity was corrected.
- Original authentic USGS bare-earth DEM SHA256
  `026ec5429ddb1197b46247dd97832e2abe246be112e7133f4fce5029fea50de5`.
- Frozen native20×20 crop SHA256
  `2c9fcd0748b9ef3d8c036a59af5c1419a826ba14262a3a80d089b1d59023fb26`;
  oracle `22ed905702f9e8bed9dc6a98f07ff7762877459c55cb50e581ec1a6fa7519b76`.

Historical Reasoner: accepted generation2, consistenttrue, bean/logicalVALID,
`imod`, fingerprint `semantic-content-v2:65c8bb7ff248c915de0d2e6ef1db2b81df8514352a4720cea305979a255d2b02`.
The [semantic repair report](MODELER_SERVICE_REPAIR_REPORT.md#7-final-service-readiness-and-residual-advisories)
recorded six nonblocking warnings: fundamental type not established for core
Quality/Time/Space; unresolved described bearer for PhysicalPhenomena→PhysicalSystem,
Earth→EarthSystem and physical:Period→Frequency. Current receipt records warnings
exposed by the status payload; absence from that projection is **not proof of fix**.
The [first observation report](MODELER_FIRST_SCIENTIFIC_OBSERVATION.md) records the
older empty catalogs; that failure is preserved, not the current inventory authority.

## 4. Backend components and provenance

Historically installed/advertised geospatial KAR on Resources and Runtime:
`klab.component.geospatial-1.0-20260926.083046-222-component.kar`, SHA256
`e7d470f69943e93ce48e9669bad6a41b618dfbe0e7bc748076e010583daef644`.
Resources owns nonembeddable `raster`; Runtime advertises stac/wcs/wfs, not raster.
This difference is expected, not a missing Runtime raster install.

Neo4j Spatial2025.10.0 (publisher targets Neo4j2025.10.1 / Java21), SHA256
`83c9d31deebcc5a951f11832ffbc58692a2795a461baba480efe74cafbeeff6b`,
historically exposed48procedures, including spatial.layers/addLayer/addNode/removeLayer.
Two exact unsigned artifacts were explicitly authorized and publisher-digest verified;
checksums are **not signatures**. `--deep` records current owned archive/plugin hashes;
a changed hash has unknown provenance until separately attested, not implied approval.
The Java helper does only SHOW PROCEDURES and a READ spatial.layers count over the
existing loopback Bolt configuration. It does not change authentication or create layers.

## 5. Capability ladder — independent gates and environments

| Gate | Historical result / source | Fresh read-only checker limit |
|---|---|---|
| G0 services healthy | PASS, fouroperational/connected/UP plusgraphdb,03:11receipt | Checks local managed identities/status/health; HTTP200 alone insufficient |
| G1 semantics accepted | PASS imod generation2/consistent/recognizedelevation | Checks status/capability admission; does not mutate or reason anew |
| G2 authentic resource/model/adapter | PASS nativeUSGS fixture and catalogbinding | Deep retrieval/sourcehash/nativecrophash plus currentcatalogs; basic mode UNKNOWN |
| G3 spatial + context creation | PASS48procedures/contextcreated,report§7–8 | Procedure READ can pass; contextcreation remains historical, currentG3UNKNOWN |
| G4 actual Resolver model selection | PASS RESOLVED intendedmodel,report§8 | Catalog/URNresolution is not modelselection; no newJob, currentG4UNKNOWN |
| G5 Runtime numbers | FAILED nullscanner,report§12/Job9receipt | Historical reproducedJavafailure; freshUNKNOWN, not a freshreproduction or fix |
| G6 independent400cellcomparison | NOT_RUN; noRuntimefield | Earlier isolated400/400 is not thisgate |
| G7 Modeler elevationretrieval/display | NOT_RUN; no completedfield | No UI science success inferred fromservicehealth |
| G8 Python end-to-end | Separate specifiedenvironment required | NOT_APPLICABLE to a read-only IDEcheck; originalisolatedN/S evidence remains separate |

## 6. Known blockers

- **JAVA-REMOTE-SCANNER: ACTIVE in last reproduced tuple; UNVERIFIED_CURRENT**.
  ResourcesProvider.contextualizeSynchronous passes null Storage.Scanner (installed
  bytecode offset180); real RasterEncoder.encodeFromCoverage:183 dereferences
  DoubleScanner.add. RuntimeJob9FINISHED returned empty/error, not numbers.
  Maintainer handoff/reproducer: remediation report§12 and `dem-remediation/NullScannerReproducer.java`
  in the original remediation checkout; no product patch authorized here.
- Semantic admission, catalog provisioning and missing Spatial prerequisites:
  **FIXED_VERIFIED historically**, current read-only projections checked separately.
- Context-specific current model applicability, output scientific correctness and
  managed production release/support: **UNVERIFIED**, not fixed by healthy services.
- Worker SDK / TargetC: **OUT_OF_SCOPE**. No compiler/scheduler or terrain algorithm in Python.

## 7. Environment separation and A+B dependency map

| Work | Actual dependency | Must not require |
|---|---|---|
| Offline PythonDTO/error/serialization/unit/docs/packaging | Declared Pythonrevision and deterministiccontracts | G0–G7 or a runningIDE |
| Live IDEsemantic/catalog retrieval | FreshG0/G1 and applicableG2 inventory/auth | G5/G6 or contextcreation |
| IDEcontext/modelselection | SeparatelyauthorizedG3/G4 scenario | Inference fromcatalogcounts |
| Real IDEDEMnumerics/export | G0–G6 plusownedscope/provenance | Isolatedcoverage or FINISHED-onlyproof |
| ModelerUI acceptance | Realcompletedobservation +G7 | APImetadata-onlyevidence |
| N/S Pythonacceptance | Originalpinnedisolatedharness,historically38091–38094 | IDE8091–8094 or a substitutedcomponent |
| PeriodicL4 / studies / productionHub | Authorizedworker/artifacts/rights/originalreference/issuer | DeveloperIDEhealth as substitute |

Repository HEAD is not installedbinary identity. The Modeler distribution is not
the separately compiled Python fixture. BranchCI/test passes and historicalscientific
receipts each retain their own source/environment scope.

## 8. Runbook and append-only change history

Read this file → decide relevant gates → run the single bounded checker if needed →
read JSON/UTC/drift → proceed with independent Python work on unavailable gates.
No automatic diagnosis/repair/retry follows a failure. Deep scientific smoke tests
need separate workload/ownership authorization and a reason their outcome can change;
do not repeatedly hit the already demonstrated null-scanner path.

The checker writes only the requested receipt and this generated block/history.
It never installs plugins/packages, editsconfig/ontologies, launches/stopsModeler,
altersdatabases, submitscontexts/sessions/Jobs, or starts a polling/background service.

- 2026-11-06T03:11:16Z (historical): Spatial/context/realResolver passed; numericJobfailed;
  ownedcleanup and servicepreservation passed. Source remediationreport§7–13.
- 2026-11-06: durable contract introduced from the two preserved specifications;
  no historical FAIL was rewritten as current PASS. Refresh entries append below.

- 2026-11-06T14:20:57.464274Z: checker v1, `deep-read-only`, G0=PASS; G3–G7 not executed; drift=0.
