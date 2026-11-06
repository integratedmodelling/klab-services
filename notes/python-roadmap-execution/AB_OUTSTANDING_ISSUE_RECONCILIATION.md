# A+B outstanding-issue reconciliation — 6 November 2026

## Decision and evidence boundary

**A and B remain incomplete.** C is excluded. The repaired IDE removes specific
prerequisites, not scientific acceptance. No issue is closed by this audit.
Current branch `feature/python-ide-state-audit`, base
`573a51d46896a4733450a4f81da512566aa864bf` (accepted PR242), is local only;
no new PR/push/merge. `origin/develop` fetched at `ddb478adb`, not adopted as the
Python integration baseline. Original dirty consolidation checkout is preserved.

Read [IDE contract](MODELER_IDE_STATE.md) and
[historical remediation](MODELER_REAL_DEM_REMEDIATION_REPORT.md) first. Exactly one
fresh read-only IDE refresh at **2026-11-06T14:20:57.464274Z**: managed four services
and graphdb healthy; accepted `imod` generation2/consistent; actual catalog/model/
resource/source and crop identity checks pass. Spatial READ passes, but current
G3–G7 UNKNOWN. Historical G3/G4 pass; G5 failed null scanner; G6/G7 not run.
Current semantic candidate reports bean INVALID/logical NOT_RUN while the served
generation remains accepted: **candidate state is not accepted-generation state**.
No newly executed scientific result or fixed warning is claimed.

## Methodology and counts

Authority: planning guide, complete acceptance map, FEATURE_LEDGER's full
constituent crosswalk (lines540–589), current checkpoint, source/tests/workflows,
scientific acceptance report/index and all19-study matrix. Stable parent IDs below
retain every material scenario; a parent is not closed by one passing slice.

Fresh GitHub receipts 14:13:31–14:13:34 UTC: **16 issues, 10 open/6 closed;
220 PRs, 0 open**; last10 integration CI runs all success, including exact base
42134379972 (Python) and42134380102 (build). Raw branch receipt includes162 local/
remote refs: a branch's existence is not an unmerged feature or acceptance proof.
PR238/239/240/241/242 are MERGED; old ref-creation/build-blocked checkpoints are
superseded for those deliveries, not silently erased. Earlier queue branches and
five old scientific candidates remain retained leads, not instructions to merge.

Portable [source/tracker inventory](evidence/ab-source-inventory-20261106.json)
contains all42 exact acceptance rows, tracker snapshot and7 source findings.
Private custody: `$HOME/.klab/scientific-examples/usgs-3dep-virginia/evidence/ab-audit-20261106/`
contains issues/PRs/open-PRs/CI/branches and bounded issue-body readback.
No TODO/FIXME/xfail/explicit skip decorators found in Python sources at this base;
7 `importorskip` calls cover notebook, cryptography and analysis dependencies.
Default pytest excludes `live`; that is an explicit environmental gate, not PASS.
Functional scenarios fail on missing selected prerequisites rather than skipping.

Inventory units: **42 acceptance parents** =24RC+18CAP, plus **10 open tracker
leads**, **6 closed-history checks**, **8 crosscutting scenario/workflow items**,
and **19 research IDs**. These sets overlap and are **not added** into a fake total.
42 parents: 5 bounded accepted (regression only),2 C-excluded,35 incomplete.
Audit dispositions of those35: 24 PYTHON_ACTIONABLE (often with separately named
external subgates),8 UNKNOWN_INSUFFICIENT_EVIDENCE,3 INFRA_OR_CI_BLOCKED.
None of the35 is RESOLVED_VERIFIED in this pass. No assertion that all constituent
subscenarios of a PYTHON_ACTIONABLE parent can be completed without Java contracts.

## Complete acceptance-parent ledger

Each symptom/expected behavior is the acceptance-map criterion plus the full
FEATURE_LEDGER crosswalk; evidence is the exact42-row inventory above and cited
delivery records. Scope A/B follows planning authority. Effort S/M/L is relative;
risk H/M/L is regression/acceptance risk, not a forecast. “None” means no IDE gate
for the next Python increment; deployed acceptance still requires its own tuple.

|ID / scope|Remaining symptom → expected behavior; current evidence|Disposition / risk / effort|Owner; gates; concrete next action|
|---|---|---|---|
|RC-P0 A/B|Incomplete operation/mixed-version matrix despite existing inventory|PYTHON_ACTIONABLE M/M|Python + #129; none; enumerate supported versus unavailable operations, test changed-capability diagnostics|
|RC-P1.1 A|TEST certificate journey exists, genuine Hub/license walkthrough absent|INFRA_OR_CI_BLOCKED H/M|Operator/issuer; authorized Hub tuple; provide consented principal/issuer/license then original journey|
|RC-P1.2 A|PR203 completed-Job/fresh-process recovery delivered; in-flight/history/restart not certified|UNKNOWN_INSUFFICIENT_EVIDENCE H/M|Runtime #89; own isolated lifecycle tuple; define retention/incarnation before broader claims|
|RC-P1.3 A|S private-parent denial passes; full collaborator/expiry/mutation matrix missing|PYTHON_ACTIONABLE H/M|Python/operator #89; no IDE dependency for fixtures; expand explicit role/expiry matrix, later authorized principals|
|RC-P1.4 A|Trust guards exist, provider outage/mixed-version acceptance partial|PYTHON_ACTIONABLE M/M|Python; none; deterministic outage/recovery and changed-capability cases|
|RC-P1.5 A|#200/N/S options pass; observer/project/dependency/executed-grid matrix incomplete|PYTHON_ACTIONABLE H/M|Python/Resolver; none for contract tests, G3–G5 for IDE execution; supported options only, never forced-model invention|
|RC-P2.1 A|N single-shard20cell oracle passes, multishard/Boolean/category/time fixture missing|UNKNOWN_INSUFFICIENT_EVIDENCE H/L|Provider/exporter; producing tuple/oracle; obtain advertised fixture, preserve unpublished exporter boundary|
|RC-P2.2 A|Point slices/descriptors exist, maintained block/time/revision absent|UNKNOWN_INSUFFICIENT_EVIDENCE H/L|Resources #123; authoritative schema/lease; inspect contract before API|
|RC-P2.3 A|Independent real-provider accuracy blocked; USGS direct400cells not Runtime output|INFRA_OR_CI_BLOCKED H/L|Custodian #130 + Java for IDE G5/G6; matched model/datum/reference/rights, no substitute grid|
|RC-P2.4 A|Socket64MiB and later PR206 Java1Mcell slice pass; representative sustained provider soak absent|UNKNOWN_INSUFFICIENT_EVIDENCE M/M|Operator/provider; own pinned workload; define permitted size/duration/memory oracle|
|RC-P2.5 A|PR204 native vectors delivered; broad category/time semantics unverified|PYTHON_ACTIONABLE H/M|Python/provider; none for adapter negatives; no invented category dictionaries or reshape|
|RC-P3.1 A|Hints/dedup not authoritative committed race convergence|UNKNOWN_INSUFFICIENT_EVIDENCE H/L|Runtime #126; commit/version contract, then two-client race fixture|
|RC-P3.2 A|#199 bounded reconnect/explicit partial refresh accepted|RESOLVED_VERIFIED (inherited bounded) M/S|Python; none; regression only, no durable catchup promotion|
|RC-P3.3 A|Job errors/cancel/binding exist; history/expiry/restart unknown|PYTHON_ACTIONABLE H/S|Python + Runtime #89; none; inspect finished-result envelope correctness, retain no-replay behavior|
|RC-P3.4 A|PR239/240 native awaited recorded evidence delivered, full/historical lineage absent|UNKNOWN_INSUFFICIENT_EVIDENCE H/L|Runtime/extractor; authoritative lineage/revision tuple, not another facade reimplementation|
|RC-P3.5 A|Async own-wait/notebook APIs exist, full multi-principal notebook acceptance missing|PYTHON_ACTIONABLE M/M|Python/operator; none for deterministic cancellation, original isolated tuple for notebook acceptance|
|RC-P4.1 B|Semantic/resource inspectors exist, applicability/version/access matrix partial|PYTHON_ACTIONABLE M/M|Python/Resources; G0–G2 read-only checks relevant; add exact served projection controls, not local reasoning|
|RC-P4.2 B|PR201/227 parsers delivered; maintained revision admission not proven|PYTHON_ACTIONABLE M/M|Python/Resources; none for stale/null contracts; parser validity is not persistent authoring|
|RC-P4.3 B|Original import/annotate/compute workflow missing|PYTHON_ACTIONABLE H/L|Python/Resources; inspect actual authorized mutation schema first; no import or Job in this audit|
|RC-P4.4 B|ADD slices exist, safe edits/locks/history/publication incomplete|PYTHON_ACTIONABLE H/L|Python/maintainer #129; authoritative routes/rights then one minimal workflow|
|RC-P4.5 B|Managed agent slices exist, production incarnation/independent cleanup unknown|PYTHON_ACTIONABLE H/M|Python/operator #125; none for status contracts, explicit managed principal for acceptance|
|RC-P5 C|Worker SDK deliberately excluded|OBSOLETE_SUPERSEDED (assignment boundary)|Separate product #127; not A+B acceptance work|
|RC-R1 A/B|Evidence layers exist; integrated mixed-version/provider negatives incomplete|PYTHON_ACTIONABLE M/M|Python; none; exact wheel/source tests, retain original failures|
|RC-R2 A/B|Package builds not supported public release|INFRA_OR_CI_BLOCKED H/M|Maintainer #129; approve support/version/publication explicitly|
|CAP-01 A|Credential profiles/renewal seam partial, real Hub absent|PYTHON_ACTIONABLE H/M|Python/operator; none for guard tests; no invented automatic refresh|
|CAP-02 A|Catalog health/selection exists, mixed-provider matrix partial|PYTHON_ACTIONABLE M/M|Python; none; readiness is not mere reachability|
|CAP-03 A/B|Configured origin routing not migration/failover|UNKNOWN_INSUFFICIENT_EVIDENCE H/L|Platform #124; authoritative ownership/composition contract|
|CAP-04 A|Typed submission/focus/grid exists, full option/relationship matrix partial|PYTHON_ACTIONABLE H/M|Python; none for wire tests; no scientific submissions here|
|CAP-05 B|Native async semantics merged, composition/revision scope incomplete|PYTHON_ACTIONABLE M/M|Python/Reasoner; none for DTO tests; no duplicate merged explorer|
|CAP-06 A|Bounded typed plans versus recorded execution accepted|RESOLVED_VERIFIED (inherited bounded) M/S|Regression only; complete lineage stays RC-P3.4|
|CAP-07 A|Job parity/concurrency partial, malformed-envelope behavior must be inspected|PYTHON_ACTIONABLE H/S|Python; none; reproduce before selecting correction|
|CAP-08 A|Bounded exact sparse values/missingness accepted|RESOLVED_VERIFIED (inherited bounded) M/S|Regression; not bulk/coordinate inference|
|CAP-09 A|Exports/metadata partial; published exporter and time/categories separate|PYTHON_ACTIONABLE H/M|Python/provider #123; none for decoding, producing fixtures for science|
|CAP-10 A/B|Partial graph/stale views not maintained version convergence|PYTHON_ACTIONABLE H/M|Python/Runtime #126; none for invalidation controls; preserve unknown revision|
|CAP-11 A/B|Best-effort hints no durable cursor/replay|UNKNOWN_INSUFFICIENT_EVIDENCE H/L|Runtime #126; define replay/resync before client claims|
|CAP-12 A|Offline snapshots accepted, never queued replay|RESOLVED_VERIFIED (inherited bounded) M/S|Regression only; no offline Engine|
|CAP-13 B|Catalog/inspection slices delivered; dependency/export matrix incomplete|PYTHON_ACTIONABLE M/M|Python/Resources; G0–G2 for finite live readback; actual routes only|
|CAP-14 B|Authoring/agents bounded, full authorized workflow incomplete|PYTHON_ACTIONABLE H/L|Python/Resources/operator; schemas/rights before writes|
|CAP-15 A|Optional vector delivered, broad categories/time/notebook still partial|PYTHON_ACTIONABLE M/M|Python; none for optional import/adapter tests|
|CAP-16 A/B|17 local coverage gates/CI exist; public compatibility/scale incomplete|PYTHON_ACTIONABLE M/M|Python + #129; exact package/coverage now, periodic worker separately blocked|
|CAP-17 C|Embedded provider SDK excluded|OBSOLETE_SUPERSEDED (assignment boundary)|#127 separate product, no Python scientific algorithm|
|CAP-18 A/B|Safety/no replay/optional imports accepted invariant|RESOLVED_VERIFIED (inherited bounded) H/S|Regress each change; not certification of unknown operations|

## Tracker leads, deduplication and closed history

All tracker IDs refer to `integratedmodelling/klab-services`. Open leads are not
additional missing Python features when they duplicate parents above.

|Issue|Scope / disposition / evidence|Owner and exact next action|
|---|---|---|
|89 OPEN|A/Java UNKNOWN_INSUFFICIENT_EVIDENCE; overlaps P1.2/3/P3.3/CAP07; bounded recovery/ACL already delivered|Runtime/operator: retained history/incarnation/role/expiry matrix on original isolated tuple|
|123 OPEN|Java UNKNOWN_INSUFFICIENT_EVIDENCE; P2.2/CAP09; native scanner is not remote block API|Resources: explicit versioned block/time/window/lease contract|
|124 OPEN|Platform UNKNOWN_INSUFFICIENT_EVIDENCE; CAP03|Platform: composition/migration ownership/partial-failure contract|
|125 OPEN|B PYTHON_ACTIONABLE + operator gates; P4.5/CAP14|Extend only supported status/control paths; operator supplies production principal/incarnation and independent cleanup|
|126 OPEN|Java UNKNOWN_INSUFFICIENT_EVIDENCE; P3.1/CAP10/11|Runtime: committed versions/durable cursor/retention contract; do not promote AMQP hints|
|127 OPEN|C OBSOLETE_SUPERSEDED for this assignment, not obsolete globally|Separate worker product scope, leave open|
|129 OPEN|INFRA_OR_CI_BLOCKED; P0/R2/CAP16|Maintainer: support/version/publication decisions; WRITE access is not publishing approval|
|130 OPEN|INFRA_OR_CI_BLOCKED; P2.3 + research|Custodians: original PaL producing inputs/time/units/reference/rights|
|144 OPEN|Java UNKNOWN_INSUFFICIENT_EVIDENCE; PR148 merged but tracker still open|Maintainer: verify exact RemoteContextCacheGenerationTest on proposed supported revision, then reconcile issue; no bulk closure|
|193 OPEN|Java UNKNOWN_INSUFFICIENT_EVIDENCE; historical frozen ODO SHA/source-validation failures|Fixture owner: explicitly version replacement and authority identities; accepted current imod does not fix original fixture|
|87 CLOSED|Typed requests delivered; OBSOLETE_SUPERSEDED as implementation lead|Retain P1.5 option-matrix gaps, no new duplicate API|
|88 CLOSED|Handles delivered; OBSOLETE_SUPERSEDED as implementation lead|Retain #89 incarnation/history gates|
|91 CLOSED|File streams delivered; OBSOLETE_SUPERSEDED as implementation lead|Retain provider soak and independent async-export inventory; do not reopen on IDE scanner|
|142 CLOSED|HTTP-test expectation correction delivered|Historical closure, not newly rerun here; preserve exact old negative|
|145 CLOSED|Java-only setup/authority test corrections delivered|Historical closure, no new general Java acceptance assertion|
|147 CLOSED|Baseline inventory preserved; modern bounded CI not original broad-suite proof|Historical closure; retain original shared failures and #193 separately|

No duplicate GitHub issues were closed. Ten open leads overlap acceptance parents;
six closed leads are historical checks, not six newly resolved workflows. Repeated
FEATURE_LEDGER/PROGRAM_STATUS “pending” snapshots deduplicate by stable Q/RC/CAP/PR
identity; latest authoritative merge receipts win only for delivery state.

## Crosscutting gates and former-IDE delta

|Stable item / scope|Old symptom, current evidence and disposition|Risk/effort; owner; next evidence|
|---|---|---|
|IDE-SEM Java|Empty worldview/Reasoner unready → accepted generation2 current; BLOCKER_REMOVED_RETEST_REQUIRED for dependent original workflows|H/M; actual original workflow/environment retest, not public health alone|
|IDE-CATALOG Java|Empty models/resources/adapters → authentic DEM/model/raster registered and read verified; BLOCKER_REMOVED_RETEST_REQUIRED|H/M; exact requested original model/resource/applicability, not any model|
|IDE-SPATIAL Java|Missing procedures/context creation → historical context and current procedures PASS; BLOCKER_REMOVED_RETEST_REQUIRED|H/M; no new context authorized/needed here|
|IDE-RESOLVER Java|Historical actual intended model RESOLVED, fresh selection not run; BLOCKER_REMOVED_RETEST_REQUIRED|H/M; do not call catalog lookup model selection|
|IDE-NUMERIC Java|Real Runtime Job9 empty/error, confirmed null scanner raster NPE183; JAVA_RUNTIME_BLOCKED|H/L; Java maintainer supplies fixed scanner/storage contract and attested build before one authorized original numerical retry; no repair here|
|NS-ORIGINAL A/infrastructure|Original N/S isolated bounded L4 passed, unchanged engineering/manual worker delivered PR238; no new N/S Jobs here|Inherited bounded PASS only; original pinned harness/ports/oracles remain the retest authority, not IDE8091–8094|
|L4-PERIODIC infrastructure|Worker/policy/artifacts/default-branch/cadence absent; INFRA_OR_CI_BLOCKED despite offline scheduled CI|H/M; operator authorization, runner label/environment/pinned bundle/private ports/quarantine, default adoption and actual scheduled N/S receipts|
|W3-ADMISSION Java/artifacts|Both inspectors present; primary+alternate coordinated original admission tuple absent; UNKNOWN_INSUFFICIENT_EVIDENCE|H/L; platform supplies original project/worldview/Runtime tuple, D1/D2 remain NOT_RUN|

## All original research leads (no new replication)

Every R01–R19 remains UNKNOWN_INSUFFICIENT_EVIDENCE for current original Java
execution, with artifact/rights subgates owned by scientific custodians and adapter
maintainers. Risk high/effort large unless a complete existing supported tuple is
provided. IDE G0–G2 does not admit any of these original methods. Specific bounded
next evidence, retained from the full19-row matrix:

|ID|Concrete missing original evidence / next action|
|---|---|
|R01|Frozen Sicily trained weights, forcing tile/mask/reference/rights and original adapter admission|
|R02|Exact paper, household/ward consented inputs, BN/folds/labels/reference|
|R03|Madagascar original routing/BN/beneficiary grids and matched m³/year outputs|
|R04|Green-Duwamish original parcel/scenario/valuation bundle and reference|
|R05|One complete Puget source/sink/use/flow/reference fixture, not aggregate proxy|
|R06|San Pedro original carbon rasters/parameters/common mask and stock-versus-flux reference|
|R07|Original urban-growth scenario source/time and matched stock/flow outputs|
|R08|Consented original preference weights and paired Southern Rockies surfaces|
|R09|Recovered3NET/CPTs retained; original supported evaluator/admission + published matched posterior vector/tolerance/rights|
|R10|Original aesthetic network/CPT/elicitation/input/reference and rights|
|R11|Original six-forest service surfaces/method/ranked overlap reference and producing route|
|R12|Original Trentino farm/payment rules/scenario/input/output/rights, not PaL substitute|
|R13|Exact paper, immutable original year pair/boundary/model/input/output identity|
|R14|Authentic PaL Radiation tuple/date/units plus original evaluator; generated R inputs/run/plot join for growth|
|R15|Consented Vitoria original tract/health/code/normalization/reference package|
|R16|Original twelve-city predictors/GLMM fit/prediction table and current evaluator binding|
|R17|Original marsh dynamic state/forcing/process-ML source/reference/rights|
|R18|Original solver/build/coupling/forcing/restart/hydrograph exchange contract|
|R19|Exact paper, original agricultural scenario/model/input/trade-off reference/rights|

Luxembourg/Kaduna/PaL historical pilots retain L3 PARTIAL/L4 BLOCKED/L5–L7 NOT_RUN;
separate P01/P02/P03/P04/P06 leads are not replacements for R cases. R09 conditional
admission investigation/P02 conditional fallback remain rankings, not runnable facts.

## Ranked independent development selection

1. **Finished Job result-envelope correctness (A; P3.3/CAP07/18)**: inspect and
   reproduce malformed `empty` values; ordinary true-empty/Error notifications
   already map to JobFailedError in both facades. Select only if a real protocol
   gap exists. Acceptance: malformed envelope → ProtocolError, real empty/error →
   JobFailedError, false/missing flag compatibility, sync/async parity, no replay,
   no decoder invocation on invalid payload, full relevant coverage. Small/high
   user-visible honesty value/low scope, zero IDE dependency.
2. **Capability-change diagnostics matrix (P0/P1.4/CAP02)**: exact mixed-service
   fixture, actionable unsupported/unavailable distinctions, no automatic rerouting;
   medium scope/risk, no backend fix prerequisite for deterministic tests.
3. **Broader mixed-principal notebook race matrix (P3.5/CAP15)**: PR210 own-wait,
   PR211 notebook andPR225 authorized concurrent readers are already MERGED;
   extend only their remaining integrated role/race matrix, not those APIs again.
   Medium scope, separate live ownership authorization/dependency availability.

The refreshed PR inventory also resolves stale parent-table delivery commentary:
PR203 completed recovery,PR201 parser acceptance,PR218 native async restoration,
PR219 native async atomic export andPR222 model authoring are MERGED. PR209 strategy
ADD andPR225 shared readers are likewise delivered bounded slices. None closes the
full parent requirement; “async export/restore not delivered” is now obsolete.

Java scanner handoff, periodic runner provisioning, original-study artifacts and
maintainer release decisions are separate queues, not excuses to stop Python work.
Publishing requires authorization through the existing repository workflow; none
has been inferred from WRITE permission. Completed implementation/test details and
exact local delivery boundaries follow below.

## Completed development pass and finite retest receipts

**PY-JOB-EMPTY-TYPE: RESOLVED_VERIFIED locally** (A, severity medium, effort small;
no IDE gate). This is one newly reproduced/closed deterministic Python regression,
not closure of any35 incomplete parent criteria or an assertion that the real IDE
emits malformed flags. `Job._decode_result` previously coerced any optional `empty`
value by Python truthiness. Eight malformed JSON values ×sync/async produced16red
cases: falsey values reached the decoder; truthy values became JobFailedError
instead of ProtocolError. Java ObservationImpl's field/setter is boolean.

The shared two-line guard now rejects present non-booleans before decoding. Missing
andfalse remain compatible; actualtrue/Error/Severe still mean JobFailedError,
with original notification precedence and transport redaction unchanged. No
endpoint/DTO inventing, retries, resubmission or local scientific computation.

|Retest / exact scope|Result and receipt|
|---|---|
|Checker v2 deterministic absence/malformed/stale/drift/secret/ownership/budget/history/document checks|18PASS; `python notes/python-roadmap-execution/test_modeler_ide_state.py -v`|
|Checker v2 offline CLI|All live gates UNKNOWN, expected unavailable exit; [offline receipt](evidence/modeler-ide-state-v2-offline-20261106.json); did not overwrite v1 live block|
|SDK capabilities, PROJECT/MODEL/RESOURCE list andMODEL/RESOURCE retrieve|7PASS on Modeler202610301844/currentIDs; [8-call original receipt](evidence/ab-sdk-readonly-20261106.json) includes one probe projection error|
|SDK semantic `geography:Elevation in m`|PASS on one additional read-only resolution; [corrected receipt](evidence/ab-sdk-semantic-corrected-20261106.json); initial nonexistent ObservableImpl.urn access was probe error, not SDK failure|
|Focused Job/client/async/binding/HTTP fixture suite|324PASS; before26cases16FAIL/10PASS, after26PASS; [validation/custody hashes](evidence/ab-validation-20261106.json)|
|Full offline unit+integration from exact built wheel extraction|**3579PASS/0skip**, Python3.12.7, one expected GDAL negative-fixture warning; no realJava scientific Jobs; same validation receipt|
|All existing workflow coverage gates|**17/17PASS**, unchanged required100% module line/branch and changed-code gates; [commands/results](evidence/ab-coverage-gates-20261106.json)|
|Lint `python -m ruff check src tests examples tools --select E9,F63,F7,F82`|PASS|
|`python -m build --no-isolation --outdir <private-output>`|Sdist+wheelPASS using existing build-equipped interpreter; [package hash and46source-file byte-parity checks](evidence/ab-package-20261106.json)|
|Post-documentation roadmap/handoff/envelope tests|37PASS against byte-identical wheel code|
|Current runner/policy readback|Both HTTP403, metadata unavailable, not proof runner absent; [receipt](evidence/ab-runner-availability-20261106.json). Scientific admission workflow is workflow_dispatch only; no schedule or dispatch added|
|End preservation/process/window check|All5 prior managed PIDs/parents/cwds still present;0matching residual auditPython/Java processes or visible windows; [scoped receipt](evidence/ab-preservation-20261106.json); no unrelated terminal closed|

Full command (from `python-client/`, existing equipped interpreter):

```powershell
$env:PYTHONPATH = '<private-output>/wheel-extracted'
python -m pytest -q tests/unit tests/integration --cov=klab_client --cov=workflows.discover_compare_explain --cov=workflows.milestone --cov=workflows.deployment --cov=build_milestone_deliverable --cov=run_milestone_acceptance --cov=run_milestone_throughput --cov=restore_milestone_results --cov=workflows.resource_inspection --cov=run_resource_inspector_acceptance --cov=check_release_readiness --cov=prepare_runtime_fixture --cov-branch --cov-report=json --tb=short
```

Focused command: `python -m pytest -q tests/unit/test_job_result_envelope.py
tests/unit/test_client.py tests/unit/test_async_client.py tests/unit/test_job_binding.py
tests/integration/test_job_binding_http.py --tb=short` (one line).
Coverage commands are the17literal invocations from `.github/workflows/python-client.yml`,
all retained in the linked receipt, not thresholds adapted for this patch.

The initial default interpreter lacked rasterio (3collection errors/1optional skip).
Existing equipped environment with source imports then passed3576but failed3
noneditable-SDK preflight controls. Original negatives are retained privately and
hashed, **not excluded or relabelled passing**. Exact source-to-built-wheel parity
allowed rerunning all3579from extraction; all passed without relaxing the guards.
First build interpreter lacked setuptools; existing build-equipped interpreter
succeeded. No package installation or existing-environment modification occurred.
Wheel extraction is not represented as a pip installation or production install
acceptance. Native N/S live cases remain historical; this offline run exercises
their validation/negative-control logic, not another isolated L4 execution.

Review: [contributor-guide self-review](../../python-client/docs/reviews/job-result-envelope-and-state-audit.md),
architectural/safety/scientific boundaries checked; no independent maintainer
approval claimed. Original Modeler reports are included as **sanitized historical
reference copies**; originals and raw custody remain unchanged. Supplied specs are
still byte-for-byte copies. RawJUnit paths stay private; portable receipts expose
only counts, hashes, attributed outcomes and bounded scope.

### Delivery and next milestone

Implementation plus deterministic/local package regression acceptance is complete.
Remote delivery/hostedCI/maintainer acceptance is **NOT_RUN**: no push, newPR or
merge. Changes are on `feature/python-ide-state-audit` based on accepted573a51d46.
The reviewed Python fix is local commit `391aa4b1d`; the contract/reconciliation
is a separate local documentation commit. Neither is a remote merged delivery.
The map's primary statuses and totals remain unchanged; corrections concern verified
merged bounded deliveries and this local regression only.

Next A+B milestone: capability-change diagnostic matrix (rank2above), with explicit
unsupported/unavailable/malformed distinctions and no automatic routing or replay.
Before any remote delivery of this pass, obtain publishing authorization, create a
normal PR against the accepted integration branch, run normal hosted checks and
request maintainer review. Java numerical science separately needs an attested
scanner fix plus one authorized original Runtime/oracle retry; periodicL4 needs
operator worker/artifact/policy/default-branch/cadence approval. Neither is a gate
for the next independent Python diagnostics tests.
