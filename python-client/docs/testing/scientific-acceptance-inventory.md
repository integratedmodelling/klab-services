# Scientific acceptance inventory — TA-1

Accepted source `937c758b62743a0ab68e48792870a9261cf7cc87` (#231). This milestone
does not adopt the seven unpublished A+B branches. The authoritative acceptance
map and its 24+18-ID crosswalk remain unchanged. [Machine-readable family map](../../tests/acceptance/scenarios/test-evidence-map.json)
retains all42 IDs, file-selection patterns, boundaries, owners and gaps.

## Retained test families and ownership

| Family / source selection | Evidence / oracle | Capability and negative coverage | Gap/owner |
|---|---|---|---|
| unit `*connection*`, `*transport*`, `*binding*` | L1 mock payloads/implemented service contracts | CAP01–03/18: origin, credentials, readiness, binding | Genuine Hub/licence/production matrix: operator |
| unit `*client*`, `*requests*`, `*recovery*`, `*handle*`, `*experiment*` | L1 DTOs, controlled identities and jobs | CAP04/07/12: no replay, unknown writes, disposal | Retained in-flight restart history: Runtime |
| unit `*scientific*`, `*nonuniform*`, `*export*`, `*scan*`, `*vector*` | L1/L2 local synthetic files; independent matrix exception explicit | CAP08/09/15: value/mask/unit/CRS/precision, atomic interruption | Multi-shard/time/provider truth: backend/provider |
| unit `*semantic*`, `*request_plan*`, `*evidence*`, `*provenance*` | L1 service-shaped controlled records | CAP05/06/13: stale/foreign/unsupported controls | Semantic meaning not proven by serializer: Reasoner/Resolver |
| unit `*graph*`, `*notification*`, `*refresh*` | L1 controlled graphs/broker messages | CAP10/11: bounds, partialness, disconnect | Root commit/durable replay: platform |
| unit `*resource*`, `*authoring*`, `*agent*`, `*preview*` | L1 parser/route/control payload fixtures | CAP13/14: denied mutation, ADD-only, unknown state | Import/versioned editing/managed principal: service owner |
| unit `*shared*`, `*permission*`, `*notebook*` | L1 local guards, not actual principal/kernel acceptance | CAP07/15, RC-P1.3/P3.5 | Real ACL plus cleanup requires live stack |
| integration `test_*.py` | L2 loopback HTTP, files, processes/private kernels | Transport, streams, saved handles, snapshots, kernels | Socket or Python kernel is not Java/science |
| functional `test_nonuniform_export.py` | Historical real-Java bounded numeric oracle (#197); new run NOT_RUN | RC-P2.1/P2.5; exact matrix/coordinates/masks | N needs plan/input association and new milestone receipt |
| functional `test_request_plan.py` | Historical real-Java options/plan/result (#200); new run NOT_RUN | RC-P1.5/CAP06; source literals66.5/234.5 | S needs real authorization negative and reviewed association |
| other functional `test_*.py`, named suites in `tools/run_functional_acceptance.py` | L3 candidates; collection/green unit wrapper does not establish live pass | Named parser/recovery/events/ACL/authoring use cases | Historical receipts stay scoped; no blanket L4 promotion |
| remaining unit harness/packaging/roadmap/release checks | L1, explicit catch-all; no claim every case tests security | CAP16/18, RC-P0/R1/R2 | Tool failures belong to contributor, not external prerequisite |

Each module can match multiple families. Fallback packaging attribution is
conservative, not evidence that an unrelated module proves release support.
Run discovery with `python -m pytest tests/unit tests/integration --collect-only -q`;
select exact file paths, retaining security/lifecycle tests for affected families.
TA-5 will add changed-path commands; no existing gate is removed meanwhile.

## Oracle audit and case choice

N reuses `tests/fixtures/nonuniform-export.json`: asymmetric4x5 north-first matrix,
positive/negative/fractional/zero, two missing cells,16777217.125 precision sentinel,
EPSG3857,100m cells. Unchanged Java test producer stores columns south-to-north;
expected matrix predates this milestone and is not read from exported bytes.
It can detect row flip, column reorder, mask loss and float32 truncation. This is
independent representation checking of synthetic engineering data, not measured
terrain/provider accuracy. Corrected unpublished exporter must be disclosed.

S reuses the existing scenario declaration and constant234.5m model. `docs/KIM.md`
states scenarios participate only when active. Explicit activation and actual
recorded model URNs plus independent literal outputs can test applicability without
inventing a worldview or forcing model selection. Partial plan cannot explain more
than Java serves. The disputed union failure remains separate and is not rerun.

## Baseline, duration and preservation

Historical isolated WindowsPython3.12.7 installed receipts for D6/D7 show3431tests
77.84s and3441tests77.73s respectively; **different source/test sets, not a speedup
comparison**. Per-family durations are UNKNOWN (`null`) until measured. New milestone
tests/scenarios are NOT_RUN except separately recorded validator runs. No flakiness
rate inferred from one run. Live missing prerequisites are BLOCKED; malformed local
test/manifest is FAIL, never an environmental excuse. Tests marked live/functional
require explicit deployment config; not available in normal offline hosted jobs.

Retain existing suites,17coverage gates,Python3.11/12/13 checks,core-only installation,
Windows fixture bytes and optional imports. Reuse one owned stack harness and the
existing N verifier/S plan extractor. Do not copy fixtures, add a deployment model,
replace workflow runners, rerun a broad Java suite or historical artifact sweep.
Duplicated startup cost: each owned named suite starts four services plus graph and
authority; keep N/S independent fresh state because N's producer is single-use.
Potential repeated mock payload setup remains valuable independent decoder/security
coverage; this milestone does not delete it to inflate efficiency metrics.
