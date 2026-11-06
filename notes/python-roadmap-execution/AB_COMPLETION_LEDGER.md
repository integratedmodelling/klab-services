# A+B completion ledger — ongoing campaign

## Authority and current decision

Standing user directive6November2026: finish all independently implementable A+B
work, publish normal reviewable contributions, satisfy CI/protections/required
approvals, continue across milestones. C is excluded. Do not repair the Java IDE.
This is the **single ongoing campaign ledger**; the earlier42-row reconciliation
and milestone reports are historical evidence, not competing current queues.

**Overall: A/B IN_PROGRESS; full system acceptance NOT_COMPLETE.** No claim of
implementation completion while runnable public-API gaps remain. Historical study
replication is not a prerequisite for unrelated features. Counts of tests or PRs
are evidence, not the completion measure.

Baseline verified at15:31UTC: clean `feature/python-ide-state-audit`, exact commits
`391aa4b1d1a49c90ea278b731234eb3fbb68ac26` and
`c23f3f9472e97b70bee2559ec7b20ab63d8686d5`, based on accepted
`573a51d46896a4733450a4f81da512566aa864bf`. Fresh fetch: integration unchanged,
develop advanced to212548f76 (not silently adopted). Repository WRITE; integration
branch reports unprotected and ruleset list empty; no approval bypass permitted.

## Delivery and verification log (append within this ledger)

|Item|Implementation/public API, verification, delivery|Remaining / next action|
|---|---|---|
|D01 finished-Job envelope|Shared Job/AsyncJob decoder guard,26newcases;324focused tests reverified;18checker tests reverified; prior3579wheel-extracted tests/17coverage gates/lint/build/46file parity bound by existing receipts|[PR243](https://github.com/integratedmodelling/klab-services/pull/243) **MERGED**15:39:32Z, merge`b9ee02b91bf18b916c68b18d870a685bca10c282`; exact-head checks including Python3.11/12/13,core-only,Windows,build andJenkins PASS; no required approval bypass, self-review not human approval. Current work fast-forwarded onto verified merge|
|E01 IDE refresh|15:33:52Z checker v2 read-only deep refresh: G0/G1/G2PASS, identity drift[], no context/session/Job. Private receipt `ab-audit-20261106/campaign-start-state.json`|G3/G4 historical only; G5 historical null scanner failure, freshUNKNOWN; G6/G7UNKNOWN; no numeric or UI promotion|
|D02 resource imports (active)|Current native Resources advertises RESOURCE/STREAM raster.geotiff.import andresources.legacy.files. Shared sync/native-async import API, explicit write consent, bounded actual-byte hashing, positive Java-long acknowledgement, no components, scoped Resources Jobs/no retries.72focused tests/100%newmodule coverage;3651full SDK tests/18gates/lint/sdist/wheel/47module byte parity PASS|Ready for normal publication; full imported-resource annotation/compute still separate. Positive Java upload needs approved disposable/retained-resource tuple: inspected ResourcesProvider.delete has TODO RESOURCE/no resource-delete implementation. Do not pollute preserved IDE. Actual1MiBmultipart HTTP integration and native read-only capability parity passed|
|D03 simulator foundation (active)|Separate dev/test package `developer-tools/klab-service-simulator`, four logical services on one ephemeral local port, deterministic poll-step Jobs, scope ownership, independent fixed20x20synthetic oracle and pinned400USGS recorded cells. SDK-driven real-process/HTTP cases verify everycell, metadata/centres, provenance and cleanup.29initial simulator/state/replay tests pass|Not Java science. Further schema/fault/resource/state breadth and isolated CI/contract backlog are active; no completion claim for all40in-scope rows|

Evidence references: [initial reconciliation](AB_OUTSTANDING_ISSUE_RECONCILIATION.md),
[IDE contract](MODELER_IDE_STATE.md), [historical scanner handoff](MODELER_REAL_DEM_REMEDIATION_REPORT.md#12-remaining-failures--smallest-next-action),
[exact validation receipts](evidence/ab-validation-20261106.json),
[17coverage commands](evidence/ab-coverage-gates-20261106.json),
[package/parity](evidence/ab-package-20261106.json). Original3579test checkpoint
is verified historical evidence, not a newly rerun full suite for this campaign.

## Complete requirement register

All24RC+18CAP retained:40in-scope parents,2excludedC. “Partial” below means the
parent's full verification is incomplete, not that its existing APIs are stubs.
Every row includes public entry point, actual meaningful evidence, and explicit
remaining engineering versus acceptance. Parent closure requires all constituent
scenarios in the planning guide/acceptance map, not merely this compact row.
Mock transport is deterministic protocol evidence only. N/S, Java and notebook
receipts retain their original environment; no substituted IDE acceptance.

|ID|Implemented/public API and meaningful evidence|Current disposition; remaining scope / next evidence|
|---|---|---|
|RC-P0|Client/service facades, connection catalog, documented operation matrix; existing package/CI plus bounded Java fixtures|PARTIAL; audit current implemented routes and changed-capability/mixed-version diagnostics; #129support policy external|
|RC-P1.1|connect, DeploymentProfile, issued-token/Hub certificate providers; PR205controlled TEST onboarding/fresh-process compute|IMPLEMENTED_PARTIAL_ACCEPTANCE; genuine supported Hub/issuer/license/principal consent external; do not call TESTHub production|
|RC-P1.2|Handle save/restore, Client/AsyncClient restoration; PR203/218real retained observation/completedJob recovery|IMPLEMENTED_PARTIAL_ACCEPTANCE; runtime retained in-flight/history/incarnation semantics #89EXTERNAL_BLOCKED|
|RC-P1.3|Scope headers/access errors, explicit releases; S private denial andPR225authorized concurrent reads|PARTIAL; verify remaining collaborator/expiry/forbidden-mutation matrix with positive controls; production roles/operator #89external|
|RC-P1.4|Connection.refresh/wait_ready/selected/client trust guards; controlled outage/identity tests|RUNNABLE; current capability/malformed/readiness diagnostics and no stale success after outage; no rerouting|
|RC-P1.5|ObservationRequest/ContextOptions/ObservationOptions; #200/N/Sactual plan/value slices|PARTIAL; supported observer/project/material dependency/grid option matrix; unavailable backend constraints stay explicit|
|RC-P2.1|Native exports and sparse reads; N independent asymmetric20cell/zero/nodata/orientation oracle|IMPLEMENTED_PARTIAL_ACCEPTANCE; multishard/Boolean/category/time producer fixture and corrected exporter publication external|
|RC-P2.2|fetch_data and native scan slices/descriptors, exact offsets and masks; PR223/224/226/230|IMPLEMENTED_PARTIAL_ACCEPTANCE; remote block/window/maintained revision contract #123external; no scanner API invention|
|RC-P2.3|Provider-reference validator exists; USGS direct400cell oracle not Runtime output|EXTERNAL_BLOCKED; matched model/input/datum/tolerance/rights #130and Java scanner for this IDE; no Python terrain substitute|
|RC-P2.4|Streaming sync/async export and atomic publication; #198socket64MiB/PR206actualJava1Mcell fault controls|IMPLEMENTED_PARTIAL_ACCEPTANCE; authorized representative provider soak/oracle/scale workload external|
|RC-P2.5|ScientificData, analysis/tables/vector adapters; PR204actual native GeoJSON|PARTIAL; inspect real available category/time/dictionary mappings; no guessed dictionary/worldview or coordinate reshape|
|RC-P3.1|Graph projection invalidation/dedup/explicit refetch|EXTERNAL_BLOCKED for authoritative race convergence; #126committed version/ordering contract absent; deterministic safety remains runnable|
|RC-P3.2|Explicit partial refresh, stale selections; #199actual owned outage/restart/refetch and denial|ACCEPTED_BOUNDED inherited; regression invariant, not durable replay|
|RC-P3.3|Job/AsyncJob status/wait/cancel/error/binding; PR243strict envelope; existing actual recovery|PARTIAL; finish meaningful lifecycle negative matrix; history/incarnation/restart #89external; no ambiguous replay|
|RC-P3.4|Provenance/evidence/activity/plan APIs; PR239/240actual Java recorded neighborhoods|IMPLEMENTED_PARTIAL_ACCEPTANCE; complete/historical/revision-pinned lineage/rootcommit backend extraction external|
|RC-P3.5|Async observation/ExperimentRunner and own-wait/notebook; PR210/211/225actual bounded slices|PARTIAL; integrated multi-principal notebook race/cleanup beyond delivered slices; do not duplicate APIs|
|RC-P4.1|SemanticExplorer/AsyncSemanticExplorer and ResourceInspector/AsyncResourceInspector|RUNNABLE; inspect implemented candidate/query/dependency/routing routes and expose missing supported contracts; meaningful live read-only checks|
|RC-P4.2|SourcePreviews/AsyncSourcePreviews four-family parsing; PR201/228actual parser acceptance; Authoring.parse|IMPLEMENTED_PARTIAL_ACCEPTANCE; maintained knowledge-revision and semantic admission guarantees external; parser receipt is not persisted authoring|
|RC-P4.3|No complete original import/annotate/compute public journey; generic Java import and schemas require inspection|RUNNABLE_CONTRACT_INSPECTION; inspect current/installed advertised import schemas, implement supported bounded public import workflow; scientific execution may remain external|
|RC-P4.4|Authoring.create_workspace/project/document; PR209strategy ADD/PR222model source accepted|PARTIAL; inspect implemented edit/lock/version/history/publication routes; no inferred overwrite/idempotency/rollback; production #129rights external|
|RC-P4.5|Agent execution/control/status/journal/messaging APIs, actual managed slices|IMPLEMENTED_PARTIAL_ACCEPTANCE; deployed principal/incarnation/independent remote terminal cleanup #125external; inspect remaining supported outcomes|
|RC-P5|Separate worker SDK/local runtime|EXCLUDED_C; not A+B gate|
|RC-R1|Offline/protocol/wheel/Java/science evidence layers and17coverage gates|PARTIAL; current combined delivery, mixed-version compatibility and exact installed release checkpoint; keep failures and skips visible|
|RC-R2|Builds, release-readiness checker, docs/migration limitations|EXTERNAL_BLOCKED release approval #129; package validation runnable, do not equate build with public support|
|CAP-01|Certificate/token providers, profiles, explicit reacquisition seam|PARTIAL; current expiry/identity/origin matrix; real issuer and optional chosen secret-store policy external|
|CAP-02|Connection catalog/selection/status/peer initialization|RUNNABLE; capability-change/readiness/mixed-service matrix, no global trust inference|
|CAP-03|Origin-qualified explicit routing and handles|IMPLEMENTED_PARTIAL_ACCEPTANCE; migration/composition/automatic failover ownership #124external|
|CAP-04|Typed scopes/observe/register/query/focus/grid|PARTIAL; inspect full implemented options and relationships against current service contracts|
|CAP-05|Semantics/search/hierarchy/predicates/authority views|PARTIAL; inspect remaining implemented composition/revision support; no Python reasoning|
|CAP-06|Typed plans distinct from execution, native awaited recorded views|ACCEPTED_BOUNDED inherited; complete lineage remains RC-P3.4|
|CAP-07|Job sync/async/cancel/wait/concurrency/binding|PARTIAL; PR243delivery plus remaining lifecycle matrix; no durable-history invention|
|CAP-08|Exact NUMBER/BOOLEAN/TEXT/null sparse1..256reads, precision/order|ACCEPTED_BOUNDED inherited; preserve false/zero and unknown semantics|
|CAP-09|Native formats/streaming and descriptors/scan slices|PARTIAL; producer-specific categories/time/multishard acceptance external; inspect public mapping gaps|
|CAP-10|Typed partial graph/provenance/stale markers and refresh|IMPLEMENTED_PARTIAL_ACCEPTANCE; authoritative version convergence #126external|
|CAP-11|Best-effort activity hints/subscriptions/polling/resync|IMPLEMENTED_PARTIAL_ACCEPTANCE; durable cursor/replay/retention #126external; explicit gap handling only|
|CAP-12|ScientificData.dumps/loads offline snapshot inspection|ACCEPTED_BOUNDED inherited; no queued mutations or offline engine|
|CAP-13|Resource/model list/retrieve/resolve inspection|RUNNABLE; current query/candidate/dependency/import schema inventory; no local model selection|
|CAP-14|Authorized ADD source authoring and managed behaviors|PARTIAL; current implemented mutation contracts, no compiler/scheduler port|
|CAP-15|Optional numpy/pandas/xarray/raster/vector/notebooks|PARTIAL; remaining verified category/time/notebook matrix, extras stay optional|
|CAP-16|Python3.11/12/13CI, packaging, tests, fixture identity and support checker|PARTIAL; exact contribution CI/compatibility/release gates; scheduled worker/operator #129external|
|CAP-17|Provider/worker SDK / embedded Runtime|EXCLUDED_C|
|CAP-18|Origin guards, explicit errors, no replay, local-only close, optional imports|ACCEPTED_INVARIANT inherited; regress every contribution, not certification of unknown APIs|

## Ordered runnable engineering queue

1. Finish normal PR243CI/review/merge and verify remote ancestry; continue below
   while checks/approval wait, without stacking unrelated unreviewed work.
2. Current service contract inventory for Resources model candidate/query/import/
   editing and mixed-capability connection diagnostics. Select a demonstrated gap,
   not a feature already merged. Exercise actual read-only IDE contracts where safe.
3. Complete supported model/resource/semantic public API gaps identified by2.
4. Implement supported original-data import/annotation entry points identified by2;
   explicit write consent/no retries/source provenance; no scientific algorithm port.
5. Complete remaining supported scope/observation/authorization/lifecycle and
   notebook race matrix; reuse existing public workflows rather than duplicate them.
6. Exact combined integration/release checkpoint and full requirements assessment.

Remaining estimated implementation scope: **not yet bounded**;35incomplete parent
criteria overlap, not35separate features. Contract inventory must distinguish
delivered code, genuine Python gaps and external guarantees before making a size
or completion claim. Small protocol regressions cannot substitute for this inventory.

## External gates (not permission to stop independent work)

- Java Modeler Resources remote numeric scanner: installed null Scanner into real
  raster encoder, NPE183; exact reproducer/handoff preserved. No engine repair here.
- Runtime retained identity/history, full lineage, committed versions/replay and
  federation migration: #89/#124/#126 authoritative contracts and supported tuple.
- Scientific producer/exporter category/time/multishard/provider fixtures and
  original input/reference/rights: #123/#130; research replication remains separate.
- Production issuer/Hub/license/roles and agent incarnation/cleanup: operator/#125.
- Scheduled pinned N/S worker/artifacts/policy/default adoption/cadence: operator;
  latest runner/policy metadata403means unavailable evidence, not absent runners.
- Public release support/version/publication approval #129; any actual PR-required
  approval/protection. User now authorizes normal publication, not bypass.

## Completion assessment

NOT_COMPLETE. Each row needs a final supported implementation and verification
disposition; RUNNABLE items above remain engineering work. Implementation completion
and full-system acceptance will be assessed separately only after runnable gaps are
delivered. No requirement will be silently dropped because an isolated test passes,
a scientific study is unavailable, or a milestone/PR completes.

### Simulator directive and independent completion dimensions

The subsequent user execution specification authorizes a separate contract-driven
simulator to unblock Python implementation acceptance. Per-row simulator verification
may establish PYTHON_SIMULATOR_VERIFIED, **not** JAVA_CONTRACT_VERIFIED,
JAVA_INTEGRATION_BLOCKED removal or SCIENTIFICALLY_VERIFIED_ON_JAVA. Behaviors with
no authoritative Java evidence are PROVISIONAL_SIMULATOR_CONTRACT and remain visible
in `docs/JAVA_SERVICE_CONTRACT_GAPS.md` and executable case metadata. Real mode has
no fallback; replay is out-of-band REPLAY, never a live execution claim. Hybrid is
optional and not a gate for the all-simulated vertical slice.

Resource-import final integration checkpoint:3651full SDK tests,18coverage gates
(the original17plus imports.py), lint, sdist/wheel and47source-module byte parity
PASS, including the final native Job-ID domain guard. Exact commands/outcomes:
`evidence/ab-resource-import-validation-final-20261106.json`. Earlier3649checkpoint
remains attributed to its earlier source; it is not silently reused for changed code.
Self-review found no remaining demonstrated defect in this bounded import API:
actual advertised native schema/controller/Job contracts, denial versus ambiguous
outcome, source privacy, no replay, bounded memory and primary-error cleanup were
checked. This is not independent maintainer approval or Java-positive admission.
The initial simulator run exposed a stale installed SDK and Decimal-versus-binary
oracle comparison mismatch; using the exact built current SDK and exact native
numeric comparisons yielded29PASS, not a waiver or altered scientific oracle.
