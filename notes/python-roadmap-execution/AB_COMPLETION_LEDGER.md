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
|D02 resource imports (MERGED_BOUNDED)|Current native Resources advertises RESOURCE/STREAM raster.geotiff.import andresources.legacy.files. Shared sync/native-async API, explicit consent, bounded byte hashing, native-long acknowledgement, scoped Resources Jobs/no retries.72focused/3651full SDK tests,18gates/lint/build/47module byte parity PASS|[PR244](https://github.com/integratedmodelling/klab-services/pull/244) **MERGED**16:40:01Z at`b618bd96ecab818279006b17f700598217d77e09`; all hosted Python3.11/12/13/core/Windows/build/Jenkins checks passed. Positive Java upload still needs approved disposable/retained-resource tuple; no supported owned-resource deletion found. Full import/annotation/compute remains separate|
|D03 simulator foundation (active)|Separate dev/test package `developer-tools/klab-service-simulator`, four services/ephemeral port, deterministic Jobs, scope ownership, independent400synthetic cells and400frozen USGS recorded cells. Real-process SDK HTTP tests check everyvalue/metadata/centre/provenance/cleanup; native async, replay, malformed/empty/failure/cancel, HTTP500no-fallback, bounded state and42criterion-wording checks.41tests and fatal-error lint PASS|Not Java science. Simulated/replay service modes and direct real read-comparison configuration are explicit; hybrid deferred. Normal package/CI publication next, then resource/streaming/lifecycle breadth; no all40-parent completion claim|

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

Dimension legend: **P** = PYTHON_IMPLEMENTED_UNVERIFIED at the full parent level
(partial implementation where named); **S(slice)** = PYTHON_SIMULATOR_VERIFIED
only for the named bounded case, not closure of the complete parent. **J(read)** =
JAVA_CONTRACT_VERIFIED for attributed read shapes only; **B(gap)** =
JAVA_INTEGRATION_BLOCKED by the named gap; **N** = NOT_YET_TESTED on this candidate.
SCIENTIFICALLY_VERIFIED_ON_JAVA is **not established by this campaign for any row**.
Inherited actual Java/scientific bounded evidence remains in the original map,
not erased or silently elevated to complete production acceptance.

Case IDs resolve to exact pytest IDs, native route/DTO evidence, confidence and
Java status in `developer-tools/klab-service-simulator/contract-cases.json`.
NOT_RUN means no simulator case yet; it is runnable backlog, not a waiver.
Original criterion text below is the exact criterion column of the42-row map.

|ID / original wording|Python implementation / simulator case|Authoritative evidence / Java dimension / delivery / next action|
|---|---|---|
|RC-P0 — A/B operation inventory and compatibility diagnostics|P; Client/connection/service inventory; S(slice) SIM-DISCOVERY-001|KlabServiceController/SDK transport + prior CI; N mixed-version matrix; extend changed-capability diagnostics|
|RC-P1.1 — A supported onboarding through computation|P; connect/profiles/certificate/token; S(slice) SIM-DISCOVERY-001 + SIM-GRID-001|Native scope/capability DTOs, PR205TESTHub; genuine Hub/issuer/license external; finish simulator certificate path, never relabel TESTHub production|
|RC-P1.2 — A fresh-process restoration without disposal|P; Handle/Client/AsyncClient restoration; SIM-LIFECYCLE-001 partial, fresh-process simulator case NOT_RUN|Native scopes/Jobs; PR203/218real retained completedJob; B(JAVA-RETENTION-001); extend simulator restore without create/dispose|
|RC-P1.3 — A collaborator reads, forbidden writes and expiry diagnosis|P; scope/access/error APIs; S(slice) SIM-LIFECYCLE-001 private denial|Native ACL contracts; S/PR225bounded real principals; N full expiry/collaborator matrix; extend positive controls|
|RC-P1.4 — A actionable readiness/trust diagnostics|P; Connection.refresh/wait_ready/client; S(slice) SIM-DISCOVERY-001|Native capabilities/status + connection tests; N outage/mixed-version matrix; add fault scenarios without rerouting|
|RC-P1.5 — A requested options visible in actual plan|P; ObservationRequest/options; S(slice) SIM-SELECTION-001|ResolutionConstraint/Dataflow DTOs; #200/N/Sactual bounded plans; N remaining observer/project/scenario/grid cases; extend supported options only|
|RC-P2.1 — A nonuniform multi-shard scientific export|P; exports/point reads; S(slice) SIM-GRID-001 + SIM-USGS-001; export/multishard simulator NOT_RUN|StorageScan/Geometry DTOs, frozen oracle; N bounded real export, B(JAVA-SCANNER-001) IDE; implement advertised simulator exports/negative metadata cases|
|RC-P2.2 — A time/window selection, precision, revision and masks|P; fetch_data/ScanSlice/descriptors; SIM-GRID-001 partial|StorageScan.Point + PR223/224/226/230; B(#123remote lease contract); add explicit time/mask/slice cases, preserve unknown revision|
|RC-P2.3 — A independent real-provider accuracy|P; provider-reference runner; S(slice) SIM-USGS-001 authentic recorded cells only|Pinned original oracle/TIFF; B(JAVA-SCANNER-001/#130), not scientific Java output; recorded handling verified, genuine matched provider acceptance separate|
|RC-P2.4 — A representative bounded stream/interrupt|P; sync/async atomic export; simulator stream NOT_RUN|Native export controller; #198socket64MiB/PR206Java1Mcell; N provider soak; add simulator interruptions/stream integrity, no capacity invention|
|RC-P2.5 — A faithful raster/xarray and categorical views|P; analysis/tables/vector; SIM-GRID-001/SIM-USGS-001 partial|Native formats, PR204GeoJSON and frozen metadata; N category/time/vector simulator; implement faithful served fixtures only|
|RC-P3.1 — A committed two-client race convergence|P; projection/invalidation/refetch; simulator race NOT_RUN|KnowledgeGraphQuery/activity hint contracts; B(JAVA-COMMIT-001/#126); test honest stale/refetch, label provisional ordering explicitly|
|RC-P3.2 — A disconnect and honest partial refresh|P for new simulator acceptance; existing bounded acceptance retained; simulator disconnect NOT_RUN|#199actual outage/restart/refetch; add simulator disconnect and explicit partial refresh, never durable replay|
|RC-P3.3 — A cancel/expiry/restart/no replay|P; Job/AsyncJob/binding; S(slice) SIM-LIFECYCLE-001/SIM-FAULTS-001|Native status enum/boolean empty; PR243MERGEDb9ee02b91; B(JAVA-RETENTION-001); extend deadlines/expiry/restart and cancellation races|
|RC-P3.4 — A explain actual dependencies and semantic effects|P; provenance/evidence/activity APIs; S(slice) SIM-PROVENANCE-001|Native query/LinkImpl and PR239/240real bounded views; B(JAVA-COMMIT-001); simulate explicit partial plans/recorded effects, no historical lineage claim|
|RC-P3.5 — A bounded notebook concurrency and own-wait cancellation|P; AsyncClient/ExperimentRunner; async vertical only, notebook/races NOT_RUN|PR210/211/225real bounded acceptance; N integrated multiprincipal notebook; reuse workflows with simulator concurrency/cancel controls|
|RC-P4.1 — B meaning-based asset applicability/version/access|P; semantic/resource inspectors; S(slice) SIM-SELECTION-001/REPLAY-001|J(read) native captures; B(JAVA-CANDIDATES-001) newer protocol not advertised; extend implemented semantic/asset routes without Python reasoning|
|RC-P4.2 — B revision-aware parse/validate|P; SourcePreviews/AsyncSourcePreviews/Authoring.parse; simulator NOT_RUN|Native parseAsset DTOs, PR201/228real parsers; N maintained revision; add finite parser/fault/stale cases, no compiler port|
|RC-P4.3 — B import/annotate/compute with input provenance|P partial; ResourceImporter/AsyncResourceImporter; S(slice) SIM-IMPORT-001 upload/Resources Job/private catalog/hash receipt|J(read) schema/controller; PR244MERGEDb618bd96e; B(JAVA-RESOURCE-CLEANUP-001); simulated explicit annotation/model binding/compute still required, no whole-parent closure|
|RC-P4.4 — B authorized edit/publish/conflict|P; Authoring workspace/project/document ADD; simulator NOT_RUN|Resources CRUD, PR209/222real bounded ADD; N locks/history/conflict; inspect exact contracts and add supported consent/denial cases|
|RC-P4.5 — B behavior invocation/outcomes/reconnect|P; agent execution/control/status/journal/messaging; simulator NOT_RUN|Native agent contracts/managed real slices; B(#125principal/incarnation/cleanup); add supported outcomes without worker SDK|
|RC-P5 — C separate scoped worker product|EXCLUDED_C|#127separate product; never an A+B completion gate|
|RC-R1 — A/B continuous evidence layers|P; SDK/fixture/wheel/CI layers; SIM case suite + REPLAY-001|3651SDK tests/18gates; PR243/244merged; simulator CI active; preserve errors/uncertainty and add per-row acceptance|
|RC-R2 — A/B supported public release|P tooling/docs; simulator release readiness NOT_RUN|Native support decisions not encoded by tests; B(#129approval), packaging independently runnable; final exact package/support assessment required|
|CAP-01 — A certificate/token providers, profiles, renewal seam|P; providers/profile/explicit reacquisition; SIM-DISCOVERY-001 token slice|Native Engine auth request; PR205TESTHub; N simulator certificate/expiry; no invented automatic refresh|
|CAP-02 — A explicit catalog/selection/health and peer initialization|P; Connection catalog/selection/init; S(slice) SIM-DISCOVERY-001|Native capabilities/status/scope advertisement; N changing-capability/outage matrix; add finite controls|
|CAP-03 — A/B configured origin-qualified routing|P; endpoints/handles; S(slice) SIM-LIFECYCLE-001 wrong-service refusal|Native scope/service identity; B(#124migration/composition); keep whole-workflow backend ownership, no automatic failover|
|CAP-04 — A typed scope/observe/register/query/focus/grid|P; typed request/scope APIs; SIM-LIFECYCLE-001/SIM-GRID-001 partial|Native typed option/geometry contracts; N relationships/focus/full option matrix; extend supported cases|
|CAP-05 — B semantic search/predicates/composition/revision objects|P; semantic facades; SIM-SELECTION-001/REPLAY-001 basic lookup|J(read) actual semantic capture; N broader predicates/composition/revision; finite vocabulary only, no ontology engine|
|CAP-06 — A typed plans versus execution|S(slice) SIM-SELECTION-001/SIM-PROVENANCE-001; inherited bounded acceptance retained|Native Dataflow/LinkImpl; PR239/240; full lineage remains RC-P3.4, no replay or scientific promotion|
|CAP-07 — A jobs, polling, cancellation, sync/async and bounded concurrency|P; Job/AsyncJob; S(slice) SIM-LIFECYCLE-001/SIM-FAULTS-001|Native enum/Job routes; PR243merged; B(JAVA-RETENTION-001); extend concurrency/deadline/race cases|
|CAP-08 — A exact sparse NUMBER/BOOLEAN/TEXT and explicit missingness|S(slice) SIM-GRID-001numeric/zero/negative/null/NaN; existing bounded acceptance retained|StorageScan.Point/text contract; N simulator Boolean/TEXT variants; preserve exact missingness and declared precision|
|CAP-09 — A scientific exports/descriptors, time/space/units/categories|P; native export/read descriptors; SIM-GRID-001/SIM-USGS-001 partial|Native formats/Geometry/StorageScan; B(JAVA-SCANNER-001) IDE; simulator exports/time/categories next, not byte synthesis in SDK|
|CAP-10 — A/B typed partial twin graph, stale markers, refresh|P; graph/projection/provenance; S(slice) SIM-PROVENANCE-001|KnowledgeGraphQuery/LinkImpl; B(JAVA-COMMIT-001); add paging/refetch/race cases without authoritative revision claims|
|CAP-11 — A/B best-effort scoped events and resync|P; activity notifications/polling/resync; simulator events NOT_RUN|Native activity hints; B(#126durable cursor/replay); test supported hints/gaps only|
|CAP-12 — A offline snapshot inspection, never queued replay|S(slice) SIM-GRID-001 exact snapshot round trips; inherited bounded acceptance retained|ScientificData format/PR196; add fresh-process simulator-produced snapshot inspection, never offline reasoning|
|CAP-13 — B resources/models read/query/dependency inspection|P; inspectors/services/imports; S(slice) SIM-SELECTION-001/REPLAY-001/SIM-IMPORT-001|J(read) real catalog/model captures; newer candidates schema drift; add implemented resolve/query/dependencies and explicit binding|
|CAP-14 — B authoring/behaviors actual authorized operations|P; Authoring/agents/imports; S(slice) SIM-IMPORT-001 only; authoring/behaviors simulator NOT_RUN|Native CRUD/agent DTOs, PR209/222/244merged; inspect supported mutations and explicit refusal, no compiler/scheduler port|
|CAP-15 — A optional NumPy/pandas/xarray/raster/vector and notebooks|P; optional adapters; SIM-GRID-001core-only numeric slice|PR204actual vector and PR211notebook; N full simulator optional-adapter/presentation matrix; keep imports optional|
|CAP-16 — A/B compatibility, fixtures, package, support/migration/scale|P; CI/wheel/support checker; simulator CI active|3651SDK/18gates/47module parity; N full simulator support matrix; external scheduled worker/#129does not hold Python tests|
|CAP-17 — C provider SDK / local embedded runtime|EXCLUDED_C|#127separate product|
|CAP-18 — A/B safe architecture and irreversible-contract boundaries|S(slice) SIM-LIFECYCLE-001/SIM-FAULTS-001/REPLAY-001; accepted invariant retained|Native origin/identity/no-replay contracts; regression each increment; REAL never silently becomes SIMULATED|

## Ordered runnable engineering queue

1. Publish separately packaged simulator foundation (PR243/244verified merged),
   pinned authentic fixture/replay, CI and
   executable compatibility cases. Never include simulator code in the SDK wheel.
2. Extend simulator Resources import/catalog/permission state to exercise the real
   new import public APIs and original input-provenance journey. Explicit source
   attribution and no server reasoning/registration guarantees invented in Python.
3. Add advertised streaming/export/partial-read/metadata-fault cases; drive existing
   scientific adapters, snapshot and native async APIs against them.
4. Scope restore/deadline/cancellation/concurrency/partial-refresh and notebook matrix.
5. Finite supported semantic/authoring/behavior cases and missing public APIs revealed
   by contracts; label simulator assumptions and retained Java gaps per row.
6. Exact combined integration/release checkpoint and full40-row Python assessment;
   assess Java/scientific compatibility separately. Do not stop after a PR.

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

Resource simulator follow-on (active branch `feature/python-simulator-resource-imports`):
public sync/async imports now exercise the pinned TIFF through multipart upload,
Resources Job, owned catalog/retrieve and matching source SHA/count. Cancellation,
cross-principal denial, duplicate/pending URNs, malformed/multiple fields and bounded
pending reservations have executable tests. This is a partial RC-P4.3/CAP13/14 slice,
not input annotation/model-bound scientific execution. Native schema/envelope shapes
are sourced; suggested URN/hash metadata/context-release cleanup are provisional.
Foundation [PR245](https://github.com/integratedmodelling/klab-services/pull/245) is
published at `ba5f4d825`; hosted CI is pending, not represented as merged.
Follow-on verification: 50 tests against both extracted current wheels, fatal-error
lint and 13-file source/wheel/sdist byte parity PASS. Receipt:
`evidence/ab-simulator-imports-validation-20261106.json`. Multipart self-review found
the email parser permits trailing MIME epilogue data; the importer now rejects it
and the malformed-multiple-body negative test passes. No scientific oracle changed.

Final foundation artifact checkpoint (2026-11-06T17:07Z): 41 tests passed against
both extracted current SDK and simulator wheels; fatal-error lint passed; all 12
simulator source/data files match both wheel and sdist byte-for-byte. Receipt:
`evidence/ab-simulator-foundation-validation-20261106.json`. Simulator wheel SHA256
`93cc8eee28936fe45298eea14c6f67674c08a774b95d7c2c5713d7a790d1a10a`.
This supersedes stale earlier simulator builds, not the pinned scientific fixtures.
Self-review is not independent maintainer approval; hosted CI/publication is pending.

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
Simulator self-review corrected its initial overly strict rejection of native
`StorageScan.Point.semantics=null`, using the unchanged SDK/native nullable contract.
It also removed a projected100m `sgrid` from the recorded geographic fixture request
and now refuses mismatched bounds/CRS/span/semantics, unimplemented constraints and
time/fill-curve mediation rather than silently substituting native values. Frozen
expected cell data was never regenerated to clear a failing test.
The initial simulator run exposed a stale installed SDK and Decimal-versus-binary
oracle comparison mismatch; using the exact built current SDK and exact native
numeric comparisons yielded29PASS, not a waiver or altered scientific oracle.
