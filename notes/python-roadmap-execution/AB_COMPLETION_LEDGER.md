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

Native awaited behavior-preview pass (active `feature/python-async-agent-previews`):
red public-API test reproduced the missing AsyncAgentPreviews counterpart. Added
native awaited parse/translation and shared pure identity/source/result/receipt
validation, preserving all fixed nonexecution flags. No k.Actors compiler, broker,
execution control or simulator endpoint is introduced. Cancellation/lost-response
tests require no retry or cleanup writes; drift at either identity recheck fails.
Two further red tests reproduced raw UnicodeEncodeError for malformed UTF-8 source
on both facades; intake now reports InvalidRequestError before HTTP. Final packaged
validation:124focused/3734full SDK tests, all18gates,100%agents lines/branches,
93simulator regressions against both current wheels, lint and47module source/wheel/
sdist parity PASS.42criteria unchanged. Receipt:
`evidence/ab-async-agent-preview-validation-20261106.json`.
This is bounded Python implementation, not full behavior/reconnect/Java acceptance.

Temporal export pass on `feature/python-temporal-export-selection` (MERGED_BOUNDED
PR254 atb532cd779f3f5919144fec770e3fd3e35894b2d8,2026-11-06T18:47:09Z,
all hosted SDK/core/Windows/simulator/scientific/build/Jenkins checks passed):
two red tests reproduced absent `slice=` on both public facades. Optional explicit
TEMPORAL_TRANSITION selection now propagates through native streaming and both
analysis helpers; immutable `requested_slice` is requested metadata only.152focused
tests PASS, including exact Java-long boundaries, mutable-input snapshot isolation,
unsupported/credential/control-key refusal before HTTP, and422/no-unsliced-retry.
Existing nonuniform HTTP acceptance also passes selected/unselected sync/async paths
with unchanged frozen data; no endpoint added or oracle changed. Full packaged gates
PASS:3705full SDK tests, all18coverage gates,100%lines/branches in sync/async
exports and analysis,93packaged simulator tests, lint and source/wheel/sdist byte
parity (47SDK modules/20simulator files).42original criteria checked unchanged.
Receipt: `evidence/ab-temporal-export-validation-20261106.json`.
Bounded implementation delivery accepted. Source POD coercion and
genuine Java temporal-state/history/window/revision limits remain explicit.
PR253 remotely MERGED at716dbca5c3ad2cd1d7860e124ce40e81cb027ecc,
2026-11-06T18:31:54Z, after hosted SDK/core/Windows/simulator/scientific/build/Jenkins
checks passed. No independent maintainer review is claimed. Python work remains
independent of the IDE snapshot; no live check/scientific Job/repair was needed.

|Item|Implementation/public API, verification, delivery|Remaining / next action|
|---|---|---|
|D01 finished-Job envelope|Shared Job/AsyncJob decoder guard,26newcases;324focused tests reverified;18checker tests reverified; prior3579wheel-extracted tests/17coverage gates/lint/build/46file parity bound by existing receipts|[PR243](https://github.com/integratedmodelling/klab-services/pull/243) **MERGED**15:39:32Z, merge`b9ee02b91bf18b916c68b18d870a685bca10c282`; exact-head checks including Python3.11/12/13,core-only,Windows,build andJenkins PASS; no required approval bypass, self-review not human approval. Current work fast-forwarded onto verified merge|
|E01 IDE refresh|15:33:52Z checker v2 read-only deep refresh: G0/G1/G2PASS, identity drift[], no context/session/Job. Private receipt `ab-audit-20261106/campaign-start-state.json`|G3/G4 historical only; G5 historical null scanner failure, freshUNKNOWN; G6/G7UNKNOWN; no numeric or UI promotion|
|D02 resource imports (MERGED_BOUNDED)|Current native Resources advertises RESOURCE/STREAM raster.geotiff.import andresources.legacy.files. Shared sync/native-async API, explicit consent, bounded byte hashing, native-long acknowledgement, scoped Resources Jobs/no retries.72focused/3651full SDK tests,18gates/lint/build/47module byte parity PASS|[PR244](https://github.com/integratedmodelling/klab-services/pull/244) **MERGED**16:40:01Z at`b618bd96ecab818279006b17f700598217d77e09`; all hosted Python3.11/12/13/core/Windows/build/Jenkins checks passed. Positive Java upload still needs approved disposable/retained-resource tuple; no supported owned-resource deletion found. Full import/annotation/compute remains separate|
|D03 simulator foundation (MERGED_BOUNDED)|Separate dev/test package, four services/ephemeral port, deterministic Jobs, ownership,400synthetic/400recorded authentic cells, replay and failure cases.41packaged tests/lint/parity passed; hosted Python3.11/3.13/build/Jenkins passed|PR245 remotely MERGED at1591c7fdb on2026-11-06T17:15:59Z. Not Java science or all40-parent completion; resource/lifecycle/export/authoring breadth continues|

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
Contract classifications are a separate required dimension:
**VERIFIED_JAVA** is limited to attributable observed Java/harness properties;
**SIMULATED_PROVISIONAL** covers proposed finite behavior where evidence is incomplete
(alias of older PROVISIONAL_SIMULATOR_CONTRACT); **JAVA_INCOMPATIBLE** requires an
observed difference, not mere absence of testing. Every row's named cases resolve
to property-level categories/evidence in that machine-readable index. Rows without
simulator cases remain untested, not presumed compatible/incompatible. No category
alone establishes whole-row Python implementation or genuine Java integration.
NOT_RUN means no simulator case yet; it is runnable backlog, not a waiver.
Original criterion text below is the exact criterion column of the42-row map.

|ID / original wording|Python implementation / simulator case|Authoritative evidence / Java dimension / delivery / next action|
|---|---|---|
|RC-P0 — A/B operation inventory and compatibility diagnostics|P; Client/connection/service inventory; S(slice) SIM-DISCOVERY-001|KlabServiceController/SDK transport + prior CI; N mixed-version matrix; extend changed-capability diagnostics|
|RC-P1.1 — A supported onboarding through computation|P; connect/profiles/certificate/token; S(slice) SIM-DISCOVERY-001 + SIM-GRID-001|Native scope/capability DTOs, PR205TESTHub; genuine Hub/issuer/license external; finish simulator certificate path, never relabel TESTHub production|
|RC-P1.2 — A fresh-process restoration without disposal|S(slice) SIM-RESTORE-001: separate SDK processes restore retained context/Job/observation without create/submit/release|Native handle/scope/Job contracts; PR203/218real retained completedJob; B(JAVA-RETENTION-001) durability/incarnation remains separate; full parent not closed by finite simulator retention|
|RC-P1.3 — A collaborator reads, forbidden writes and expiry diagnosis|P; scope/access/error APIs; S(slice) SIM-LIFECYCLE-001 private denial|Native ACL contracts; S/PR225bounded real principals; N full expiry/collaborator matrix; extend positive controls|
|RC-P1.4 — A actionable readiness/trust diagnostics|P; Connection.refresh/wait_ready/client; S(slice) SIM-DISCOVERY-001|Native capabilities/status + connection tests; N outage/mixed-version matrix; add fault scenarios without rerouting|
|RC-P1.5 — A requested options visible in actual plan|P; ObservationRequest/options; S(slice) SIM-SELECTION-001|ResolutionConstraint/Dataflow DTOs; #200/N/Sactual bounded plans; N remaining observer/project/scenario/grid cases; extend supported options only|
|RC-P2.1 — A nonuniform multi-shard scientific export|P; S(slice) SIM-GRID-001/SIM-USGS-001/SIM-EXPORT-001 exact nonuniform native TIFF and sync/async atomic export; multishard simulator NOT_RUN|StorageScan/Geometry/export DTOs and frozen oracle; B(JAVA-SCANNER-001) IDE; multishard/category/time scenarios still required|
|RC-P2.2 — A time/window selection, precision, revision and masks|P; fetch_data/ScanSlice/descriptors; SIM-GRID-001 + SIM-TEMPORAL-EXPORT-001 selected sync/async native export and requested-slice association|StorageScan.Point + PR223/224/226/230; controller/BaseService temporal query source only, not Java execution; B(#123remote lease contract). Unsupported selection fails before HTTP; missing state422 never retries unsliced; genuine temporal/window/revision acceptance separate|
|RC-P2.3 — A independent real-provider accuracy|P; provider-reference runner; S(slice) SIM-USGS-001 authentic recorded cells only|Pinned original oracle/TIFF; B(JAVA-SCANNER-001/#130), not scientific Java output; recorded handling verified, genuine matched provider acceptance separate|
|RC-P2.4 — A representative bounded stream/interrupt|P; S(slice) SIM-EXPORT-001 sync/async truncation/media/denial/byte-limit controls preserve original outputs|Native export controller; #198socket64MiB/PR206Java1Mcell; N representative provider soak; small recorded simulation is not throughput acceptance|
|RC-P2.5 — A faithful raster/xarray and categorical views|P; S(slice) SIM-ADAPTERS-001 nonuniform raster/xarray float64/masks/CRS/centres/units and pandas exact missingness; category/time/vector simulator remain partial|Independent pre-existing N-oracle; native adapter checks; fixture admission SIMULATED_PROVISIONAL. No inferred categories/time or guessed georeferencing; delivery active|
|RC-P3.1 — A committed two-client race convergence|P; projection/invalidation/refetch; simulator race NOT_RUN|KnowledgeGraphQuery/activity hint contracts; B(JAVA-COMMIT-001/#126); test honest stale/refetch, label provisional ordering explicitly|
|RC-P3.2 — A disconnect and honest partial refresh|S(slice) SIM-REFRESH-001 actual HTTP403/500/disconnect invalidates retained pages, explicit one-page refetch leaves others stale/partial|#199actual bounded outage/refetch retained; simulator faults SIMULATED_PROVISIONAL; no global resync/durable replay or rechecked authorization claim; delivery active|
|RC-P3.3 — A cancel/expiry/restart/no replay|P; Job/AsyncJob/binding; S(slice) SIM-LIFECYCLE-001/SIM-FAULTS-001/SIM-RESTORE-001 zero-deadline resume, finite EMPTY expiry, concurrent cancellation|Native JobManager/status/boolean empty; PR243MERGEDb9ee02b91; B(JAVA-RETENTION-001); Java retention/incarnation/restart still pending|
|RC-P3.4 — A explain actual dependencies and semantic effects|P; provenance/evidence/activity APIs; S(slice) SIM-PROVENANCE-001|Native query/LinkImpl and PR239/240real bounded views; B(JAVA-COMMIT-001); simulate explicit partial plans/recorded effects, no historical lineage claim|
|RC-P3.5 — A bounded notebook concurrency and own-wait cancellation|P; AsyncClient/ExperimentRunner; async vertical only, notebook/races NOT_RUN|PR210/211/225real bounded acceptance; N integrated multiprincipal notebook; reuse workflows with simulator concurrency/cancel controls|
|RC-P4.1 — B meaning-based asset applicability/version/access|P; S(slice) SIM-SELECTION-001/REPLAY-001/SIM-SEMANTICS-001/SIM-INSPECTION-001; sync/async finite canonical search, stale candidates, hierarchy/predicates and inert asset advertisements|VERIFIED_JAVA limited captured reads; new fixture answers SIMULATED_PROVISIONAL. Newer candidates protocol historical advertisement gap remains, not a forced Python selection API|
|RC-P4.2 — B revision-aware parse/validate|P; SourcePreviews/AsyncSourcePreviews/Authoring.parse; simulator NOT_RUN|Native parseAsset DTOs, PR201/228real parsers; N maintained revision; add finite parser/fault/stale cases, no compiler port|
|RC-P4.3 — B import/annotate/compute with input provenance|S(slice) SIM-IMPORT-001/SIM-INPUT-BINDING-001: public sync/async upload, semantic source ADD, bound plan,400recorded cells/input receipt|PR244/249 merged, AsyncAuthoring delivered. SIMULATED_PROVISIONAL admission/input projection; JAVA_INCOMPATIBLE historical scanner; B(JAVA-RESOURCE-CLEANUP-001). No general native annotation/immutable lineage claim|
|RC-P4.4 — B authorized edit/publish/conflict|P; Authoring/new AsyncAuthoring ADD; S(slice) SIM-INPUT-BINDING-001 exact-source/new workspace/project/document; no update/publish/conflict closure|Native CRUD and PR209/222real ADD; SIMULATED_PROVISIONAL finite parser/admission; remote locks/history/conditional edits remain contract inspection, not assumed absent|
|RC-P4.5 — B behavior invocation/outcomes/reconnect|P; agent execution/control/status/journal/messaging; new native AsyncAgentPreviews nonexecuting translation, simulator NOT_RUN|Native agent contracts/managed real slices; B(#125principal/incarnation/cleanup); async protocol/cancellation/identity tests do not prove execution/reconnect; continue supported outcomes without worker SDK|
|RC-P5 — C separate scoped worker product|EXCLUDED_C|#127separate product; never an A+B completion gate|
|RC-R1 — A/B continuous evidence layers|P; SDK/fixture/wheel/CI layers; SIM case suite + REPLAY-001|3651SDK tests/18gates; PR243/244merged; simulator CI active; preserve errors/uncertainty and add per-row acceptance|
|RC-R2 — A/B supported public release|P tooling/docs; simulator release readiness NOT_RUN|Native support decisions not encoded by tests; B(#129approval), packaging independently runnable; final exact package/support assessment required|
|CAP-01 — A certificate/token providers, profiles, renewal seam|P; providers/profile/explicit reacquisition; SIM-DISCOVERY-001 token slice|Native Engine auth request; PR205TESTHub; N simulator certificate/expiry; no invented automatic refresh|
|CAP-02 — A explicit catalog/selection/health and peer initialization|P; Connection catalog/selection/init; S(slice) SIM-DISCOVERY-001|Native capabilities/status/scope advertisement; N changing-capability/outage matrix; add finite controls|
|CAP-03 — A/B configured origin-qualified routing|P; endpoints/handles; S(slice) SIM-LIFECYCLE-001 wrong-service refusal|Native scope/service identity; B(#124migration/composition); keep whole-workflow backend ownership, no automatic failover|
|CAP-04 — A typed scope/observe/register/query/focus/grid|P; typed request/scope APIs; SIM-LIFECYCLE-001/SIM-GRID-001 partial|Native typed option/geometry contracts; N relationships/focus/full option matrix; extend supported cases|
|CAP-05 — B semantic search/predicates/composition/revision objects|P; S(slice) SIM-SEMANTICS-001 concept/observable, canonical authority paging, hierarchy/predicates, stale worldview refusal; broader composition/revision remains|VERIFIED_JAVA limited actual captured lookup; SIMULATED_PROVISIONAL finite SIM vocabulary, no local reasoning; absent reliable revision stays unknown|
|CAP-06 — A typed plans versus execution|S(slice) SIM-SELECTION-001/SIM-PROVENANCE-001; inherited bounded acceptance retained|Native Dataflow/LinkImpl; PR239/240; full lineage remains RC-P3.4, no replay or scientific promotion|
|CAP-07 — A jobs, polling, cancellation, sync/async and bounded concurrency|S(slice) SIM-LIFECYCLE-001/SIM-FAULTS-001/SIM-RESTORE-001: zero deadlines, resume, expiry distinct from cancellation, four concurrent async contexts|Native JobManager/Job routes; PR243merged; B(JAVA-RETENTION-001); finite retention is not Java durability|
|CAP-08 — A exact sparse NUMBER/BOOLEAN/TEXT and explicit missingness|S(slice) SIM-GRID-001numeric/zero/negative/null/NaN; existing bounded acceptance retained|StorageScan.Point/text contract; N simulator Boolean/TEXT variants; preserve exact missingness and declared precision|
|CAP-09 — A scientific exports/descriptors, time/space/units/categories|P; S(slice) SIM-GRID-001/SIM-USGS-001/SIM-EXPORT-001 exact native TIFF; time/categories partial|Native formats/Geometry/StorageScan; B(JAVA-SCANNER-001) IDE; time/categories/multishard remain, never synthesize native export bytes in SDK|
|CAP-10 — A/B typed partial twin graph, stale markers, refresh|P; graph/projection/provenance; S(slice) SIM-PROVENANCE-001|KnowledgeGraphQuery/LinkImpl; B(JAVA-COMMIT-001); add paging/refetch/race cases without authoritative revision claims|
|CAP-11 — A/B best-effort scoped events and resync|P; activity notifications/polling/resync; simulator events NOT_RUN|Native activity hints; B(#126durable cursor/replay); test supported hints/gaps only|
|CAP-12 — A offline snapshot inspection, never queued replay|S(slice) SIM-GRID-001 exact snapshot round trips; inherited bounded acceptance retained|ScientificData format/PR196; add fresh-process simulator-produced snapshot inspection, never offline reasoning|
|CAP-13 — B resources/models read/query/dependency inspection|P; inspectors/services/imports; S(slice) SIM-SELECTION-001/REPLAY-001/SIM-IMPORT-001|J(read) real catalog/model captures; newer candidates schema drift; add implemented resolve/query/dependencies and explicit binding|
|CAP-14 — B authoring/behaviors actual authorized operations|P; Authoring/AsyncAuthoring/AgentPreviews/new AsyncAgentPreviews/agents/imports; S(slice) SIM-IMPORT-001/SIM-INPUT-BINDING-001; behaviors simulator NOT_RUN|Native CRUD/agent DTOs, PR209/222/244merged; finite semantic-source admission SIMULATED_PROVISIONAL; awaited preview is nonexecuting protocol evidence only; continue supported behavior/conditional mutation contracts, no compiler/scheduler port|
|CAP-15 — A optional NumPy/pandas/xarray/raster/vector and notebooks|P; S(slice) SIM-ADAPTERS-001 raster/xarray/NumPy/pandas, plus core numeric slice; vector/notebook integrated simulator matrix remains|Independent N-oracle and PR204vector/PR211notebook receipts; new dedicated CI requires analysis/tabular dependencies, core SDK imports stay optional|
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

Production async-analysis correction (active `feature/python-async-analysis-export`):
two red tests reproduced a missing public awaited association helper and the sync
helper's AttributeError/unawaited coroutine on an async-bound observation. Added
`async_export_for_analysis` plus early matching-facade checks, sharing origin/units/
SHA association rather than adding simulator behavior. Existing HTTP scientific
acceptance now checks native async artifact and partial-provenance association too.
80focused/3673full SDK tests, all18coverage gates,100%analysis lines/branches,
sdist/wheel/47module byte parity/lint and86simulator tests against both current
wheels PASS. Zero new endpoints; receipt:
`evidence/ab-async-analysis-export-validation-20261106.json`.
Next concrete Python gap: native BaseService/controller already carry temporal
export parameters storageEvent/storageStart/storageEnd, but SDK export facades do
not expose them. RC-P2.2 is runnable client work for those supported fields, separate
from unestablished remote window/revision leases; add a named acceptance test first.
PR252 remotely MERGED at `b873ee42b0428611a61375e87e8a400ae118de97`
(18:21:26Z), after hosted simulator/scientific/build/Jenkins checks passed.

Finite semantic/asset increment (MERGED_BOUNDED PR252):
86tests/lint pass locally. Public sync/async semantic facades now traverse native-shaped
concept lookup, canonical authority paging with ambiguous labels, explicit selection,
inert documentation, declared hierarchy/boolean predicates and stale-worldview refusal.
UNAVAILABLE/UNSUPPORTED remain distinct from successful empty results. Reasoner
worldviewId is supplied; no knowledgeRevision is invented. Public resource inspectors
resolve advertised models/resources while retaining unknown selection/dependency
completeness; inert provider URLs never reroute or expand credential trust. All new
fixture answers are SIMULATED_PROVISIONAL, not verified Java ontology/provider facts.
Final validation:86tests against both current extracted wheels, fatal-error lint and
20-file source/wheel/sdist byte parity PASS. Receipt:
`evidence/ab-simulator-semantics-validation-20261106.json`. No production SDK change.
Per the user's priority clarification, further simulator additions require a named
documented A+B client acceptance test; realism/breadth alone is not a work item.
PR251 remotely MERGED at `013fda33e02880369e3280203801f8bf67f3a2ec`
(18:07:24Z), hosted core3.11/3.13, dedicated scientific adapters, build and Jenkins
passed. Current branch fast-forwarded to accepted integration preserving semantic edits.

Scientific adapter increment (MERGED_BOUNDED PR251):
initial native materialization of the pre-existing independent 4x5 N-oracle is
separately pinned SYNTHETIC_SIMULATOR data, not a rewritten expected oracle or USGS
substitute. The simulator serves frozen bytes with no scientific runtime dependencies.
Positive sync/native-async export→raster/xarray tests check all20values, missingness,
zero/negative values, float64 precision, CRS/transform/centres/metre units; pandas
preserves explicit null versus NaN and repeated offsets. The original authentic TIFF
is unchanged and correctly rejected by open_raster when internal georeferencing is
insufficient; external oracle geometry is never stamped into it. Dedicated optional
scientific CI checks dependencies before acceptance, so missing extras cannot silently
skip that gate. Source-category/applicability stays SIMULATED_PROVISIONAL.
Validation:78tests including all5 optional scientific adapter cases (zero skips),
both extracted current wheels, fatal-error lint and19-file source/wheel/sdist byte
parity PASS. Receipt: `evidence/ab-simulator-adapters-validation-20261106.json`.
PR249 is remotely MERGED at `3c9e1a5a4a49eca4ac5c072cb987277281574a68`
(17:51:04Z), hosted SDK3.11/12/13/core/Windows/simulator/build/Jenkins passed.
Partial refresh PR250 is remotely MERGED at `a74fda6a85e9d192a756d0d1be446b382b27e884`
(17:58:20Z), all hosted simulator/build/Jenkins checks passed; its72-test packaged
gate remains separately attributed. Current branch fast-forwarded to that integration.

Partial-refresh increment (MERGED_BOUNDED PR250): the
existing production graph APIs now have actual simulator HTTP denial/500/disconnect
cases with positive recovery. All pages become stale on failed refresh; explicit
one-page refetch does not mark other pages current, consistency remains UNKNOWN,
local retained data remains NOT_RECHECKED authorization, and clear does not dispose
remote state. Test-only bounded fault controls are SIMULATED_PROVISIONAL. PR249/250
are remotely verified merged; no feature-branch merge bypass.
Validation:72tests against both current extracted wheels, fatal-error lint and
16-file source/wheel/sdist parity PASS. Receipt:
`evidence/ab-simulator-refresh-validation-20261106.json`. This does not establish
Java committed-version ordering, durable replay or a full-parent RC-P3.1 closure.

Source-binding pass (MERGED_BOUNDED PR249): the public native async gap in source authoring was
implemented as `AsyncAuthoring`, sharing exact-source/consent/identity/confirmation
validation with `Authoring`; transport is awaited directly, not threaded. The finite
import-workflow now runs sync/async upload→Resources Job→explicit semantic source
parse/new workspace/project/document ADD→Resolver bound model/resource plan→all400
recorded cells and input SHA/count receipt. Default source admission and input receipt
metadata remain SIMULATED_PROVISIONAL, not a general compiler or native lineage proof.
Final SDK validation:3664 tests, all18coverage gates, authoring module100%lines/branches,
sdist/wheel and47module source byte parity passed. Both final extracted wheels pass
69simulator tests; simulator16-file source/wheel/sdist parity and fatal-error lint
passed. Exact receipts: `evidence/ab-async-authoring-sdk-validation-final-20261106.json`
and `evidence/ab-simulator-input-binding-validation-20261106.json` (the latter's first
SDK hash is superseded by the final SDK receipt). Earlier3658validation remains its
own checkpoint. Self-review additionally tightened malformed nonboolean ResourceSet
empty acknowledgements in both authoring facades; no false confirmations or replay.
No Java Jobs submitted. Publication/review/hosted checks remain separate gates.

Ferdinando's `klab.staging.vxii` is locally accessible. Read-only inspection found
the actual testsuite/manifest/basic/process sources and selected-input/scan references;
hashes/provenance are in `evidence/ab-staging-vxii-source-reference-20261106.json`.
Working Java end-to-end workflows are owner-confirmed; this pass inspected source,
not their execution receipts or live HTTP calls. Initialization is substantial
terrestrial Region→quality within it with explicit lexical project/namespace;
staging also asserts native millimetre/curve mediation, committed input receipts and
calendar-month revisions. These are dependency-order references, not permission to
copy generated terrain into the independent oracles, infer unavailable wire routes,
or claim native temporal/commit functionality is missing. Monday is not a blocker.

PR247 remotely MERGED at `f5e31e1cf9d53976dbdc2282c6f4fddc3b19ca30`
(17:28:32Z); PR248 remotely MERGED at `b1891a64eab456092d6063567c3015580b835140`
(17:34:24Z), after hosted simulator3.11/3.13/build/Jenkins checks. Active branch
fast-forwarded to that accepted integration without losing source-binding edits.

Export follow-on (`feature/python-simulator-exports`): 66 simulator tests and lint
pass locally, adding exact recorded native TIFF sync/async export and deterministic
interrupted/media-mismatch/denied/byte-limit controls. Original file SHA verifies
all cells/metadata byte-for-byte; failures preserve existing output, remove partial
temporaries and keep the owned observation readable. Export identity/applicability
is provisional; this 2032-byte fixture does not establish scale/soak acceptance.
All66 tests also pass against both extracted current wheels; all14 simulator
source/data files match wheel/sdist bytes. Exact artifact receipt:
`evidence/ab-simulator-exports-validation-20261106.json`.
PR246 remotely MERGED at `75ac5b7e1eeb899bd9dd4e91d5f442b9d33c80c1`
(17:22:12Z), hosted simulator3.11/3.13/build/Jenkins passed. PR247 was subsequently
verified merged, with its separate56-test packaged receipt retained.

Lifecycle follow-on (MERGED_BOUNDED PR247): 56 simulator tests
and fatal-error lint pass locally, including two fresh SDK processes restoring saved
context/Job/observation handles with no remote create/submit/disposal; deterministic
zero deadlines/resume; four-context native-async cancellation isolation; native EMPTY
expiry versus a released-scope 404. Explicit test-only expiry is provisional and does
not assert Java retention periods. All56 tests also pass against both extracted
current wheels; 13-file byte parity/build/lint passed. Receipt:
`evidence/ab-simulator-lifecycle-validation-20261106.json`. Publication remains active.
Foundation PR245 is remotely MERGED at `1591c7fdb3edb4cced0aa690072f86f914722711`
(17:15:59Z), after hosted Python3.11/3.13 simulator/build/Jenkins checks passed.
Resource follow-on PR246 is verified merged; its 50-test checkpoint
remains separately attributed, not silently claimed to cover later lifecycle edits.

Resource simulator follow-on (active branch `feature/python-simulator-resource-imports`):
public sync/async imports now exercise the pinned TIFF through multipart upload,
Resources Job, owned catalog/retrieve and matching source SHA/count. Cancellation,
cross-principal denial, duplicate/pending URNs, malformed/multiple fields and bounded
pending reservations have executable tests. This is a partial RC-P4.3/CAP13/14 slice,
not input annotation/model-bound scientific execution. Native schema/envelope shapes
are sourced; suggested URN/hash metadata/context-release cleanup are provisional.
Foundation [PR245](https://github.com/integratedmodelling/klab-services/pull/245) was
published at `ba5f4d825` and is now remotely verified merged at `1591c7fdb`.
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
Self-review is not independent maintainer approval. Hosted CI subsequently passed
and foundation PR245 was verified merged; later slices retain their own gates.

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
