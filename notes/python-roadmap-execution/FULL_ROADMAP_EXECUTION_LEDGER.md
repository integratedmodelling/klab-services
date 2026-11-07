# Full Python A+B+C roadmap execution ledger

## Current authority, scope and recovery

Active directive: user's self-contained **Single OpenCode directive: complete Python
A+B+C and maintain all Java gaps in-repository**, received2026-11-06 after PR266.
It supersedes the prior audit-stop/C-exclusion instructions prospectively. Original
42IDs, criterion wording and historical statuses are not rewritten. C/RC-P5/CAP-17
are **now IN_SCOPE, PARTIAL (experimental worker vertical)**; not retrospectively
complete or excluded. The first handoff commit correctly recorded NOT_IMPLEMENTED
before worker code existed; the subsequent feature evidence below changes current
implementation status, not historical acceptance.

This is the full-campaign orchestration ledger. `AB_COMPLETION_LEDGER.md` remains
the sole A+B evidence history, linked here rather than independently maintaining
another competing A+B acceptance record. Canonical native gaps remain only
`docs/java-service-contract-gaps/gaps.yaml`; maintainer narrative/appendix only
`docs/java-service-contract-gaps/JAVA_GAPS_FOR_FERDINANDO.md`.

Baseline freshly fetched: PR265 MERGED8fe0f449c at21:26:33UTC, PR266 MERGED
eda8697daf827d77066a5a2a7f90da4d56faf6d1 at21:36:19UTC; current feature integration
matches eda8697da; develop212548f76, no campaign/develop promotion. Clean starting
checkout; new branch `docs/python-full-roadmap-handoff`. No user work overwritten.
IDE state consulted: last live read-only receipt2026-11-06T14:20:57Z. This pass
needs no live gates, repair, service changes, scientific Jobs or staging execution.

### Last command / unfinished work / next action

Handoff PR267 remotely MERGED `7c5f90af926aa858fa8444d18fbb4dce4b2513cd`,
2026-11-06T23:22:30Z after build/schema/substitute/Jenkins checks PASS; no independent
maintainer review claimed. All16original gap records/IDs preserved;32governance
tests passed. An initial merge command used a mismatched head and GitHub safely
refused; corrected using the actual queried head, no bypass/force operation.

Worker vertical PR268 subsequently verified remotely MERGED
`30460119dccf3db17f1e850eadd577df3685d3da`,2026-11-06T23:42:58Z. All applicable
final-head build/Jenkins/worker3.11+3.13/schema/simulator/notebook/scientific checks
PASS; no independent maintainer review claimed. Original handoff/worker source
delivery is feature integration, not develop/native/scientific/release promotion.
Current branch `feat/python-simulator-durable-recovery`, parent30460119d.
Initial worker code implemented, installed-tested and merged. Fresh
wheel/sdist/pip-target parity:4worker modules and13simulator modules;43worker unit,
4realHTTP worker scenarios,111full simulator and33governance tests PASS, zero skips,
94.04%aggregate line/branch worker coverage, no exclusions. SDK source unchanged.
Fresh installedSDK regression also PASS3988tests/zero skips/74.17s (expected Rasterio
negative-test warning); no SDK source change. Fresh installed-process starter PASS:
known synthetic outputs/atomic publication/zero owned state after cleanup. Linked
17gap cases SIMULATED_PASS;17current realNOT_RUN/exit3. Typecheck mypy1.20.2 PASS4files,
schema/generated-handoff/fatal-error lint PASS. Final fresh worker wheel65e04a21…
and simulator wheelef855f47… have full installed-suite evidence, with exact digests
in `evidence/full-roadmap-worker-vertical-20261106.json`. No long-running live
service was launched; tests own local emulators only.
Current pass implements opt-in owned SQLite simulator checkpoints: ordinary SDK
scope/Job/observation reattachment across process restart; worker published/cancelled
state retention, active lease fencing/RECOVERY_REQUIRED, explicit recompute consent,
durable cleanup, tuple/corruption/single-writer/failed-write controls. Final installed
126simulator tests PASS/82.11s;43worker unit,15focused recovery and33governance PASS,
zero skips; worker coverage94.46%/no exclusions, mypy/fatal lint/schema/handoffdrift
PASS. Fresh wheel/sdist and actual pip-target parity4worker+14simulator modules;
17linked cases PASS(100SDK+43worker+55simulator/capture/recovery),17realNOT_RUN/exit3.
Receipt: `evidence/full-roadmap-durable-recovery-20261106.json`. Initial
mixed-suite run47passed/1failed because the worker package-independence assertion
correctly saw simulator imported by the other suite; separate runs43+5PASS, no
original assertion removed or skipped. Code/installed verification complete; normal
PR/final-head hosted CI/permitted merge remain pending. Issue127 received verified
idempotent keep-open scope correction after merged268 (comment6698860944), zero
closures/owner/label/priority operations; original native obligations preserved.
Durable recovery PR269 verified remotely MERGED
`8f9fe3805140bdcc252b4d53a91fee5ffb978598`,2026-11-07T00:02:13Z. Applicable
final-head build/Jenkins/worker3.11+3.13/schema/simulator/notebook/scientific checks
PASS; no independent maintainer review claimed. Local parent fast-forwarded to
that actual integration merge, without changing the next feature's uncommitted work.
Current next branch `feat/python-worker-cancellation-containment`, parent8f9fe3805,
implements startup-gated Windows Job Object/POSIX process group cleanup and retained
task-state polling during execution. Source tests49worker+5HTTP PASS including
descendant cleanup after success/crash/deadline/cancel, setup refusal before script
execution and remote cancellation with no publication. Cross-platform hosted and
fresh installed verification remain pending. Hard memory/disk budgets and hostile
code isolation remain unfinished; no native contract status promotion.
First installed48worker/20HTTP+recovery/127simulator/33governance pass succeeded;
subsequent empty-bootstrap-input guard and shorter monitoring timeouts changed
source afterward, so those artifacts are superseded for final-head attribution.
Final rebuilt installed verification PASS49worker unit/20HTTP+recovery/127full
simulator/33governance tests, zero skips/errors/failures;5worker+14simulator module
source/wheel/sdist/installed parity,92.98%aggregate Windows coverage/no exclusions,
common-code90%gate PASS, mypy5files/lint PASS. Evidence:
`evidence/full-roadmap-worker-containment-20261107.json`. Windows process-tree
behavior is verified locally; POSIX kernel acceptance awaits hosted Ubuntu matrix.
Coverage CI reports all platform-specific lines without exclusions:80% aggregate
gate and90% common-code gate, Windows/Ubuntu3.11+3.13 matrix. Kernel-specific code
cannot execute on the opposite OS; this is not silently lowering common-code gates.
Next command: finish installed verification/types/lint/generated handoff and publish
hardening PR after269 integration; continue independent full-roadmap requirements.
Do not stop at its PR/CI/checkpoint. Inspect remote state before repeating operations.

## Historical acceptance baseline and current implementation plan

Prior independent audit:6bounded COMPLETE/34PARTIAL/2then-excluded; acceptance
9infra-blocked/23Java-blocked/7simulator-verified/3untested. Fresh Java/science
NOT_RUN throughout this campaign unless an explicitly attributed new receipt says
otherwise. Detailed exact APIs, tests, PR/merge provenance and missing full-parent
acceptance for **every row** remain in
[`acceptance-audit.json`](../../docs/java-service-contract-gaps/acceptance-audit.json).
That immutable audit is history, not the current C scope decision. Baseline merge
for each row below is eda8697da; new merge references are appended per delivery.

All42rows below are in current campaign scope. **PARTIAL** is not complete merely
because some supported slice passes. Existing simulator cases/guards are evidence
of their exact assertion, not the missing broader guarantee. Fresh native status
for every row is NOT_RUN; existing historical native receipts are preserved.

|Original ID|Current Python implementation / simulator baseline|Next independently actionable item or exact external gate|
|---|---|---|
|RC-P0|PARTIAL; discovery/compatibility guards|Complete operation inventory against supported source and simulator coverage; production support decision separate.|
|RC-P1.1|PARTIAL; SIM-DISCOVERY-001 vertical|Exercise bootstrap through computation with positive/denial/expiry; actual issuer/license evidence external.|
|RC-P1.2|PARTIAL; SIM-RESTORE-001 and installed SIM-DURABLE-RESTORE-001 (0004/0017), publication pending|Completed/in-flight saved Job, context and observation reattach across owned simulator processes without replay; native retained-Job/incarnation semantics still external.|
|RC-P1.3|PARTIAL; SIM-SCOPE-SELECTION-001|Complete collaborator/forbidden/expiry scenario, preserving server authority.|
|RC-P1.4|PARTIAL; discovery guards|Mixed-provider outage/recovery and identity drift scenario.|
|RC-P1.5|PARTIAL; SIM-SELECTION-001|Cover material project/observer/grid option effects, distinguishing advertised model from actual selected input.|
|RC-P2.1|PARTIAL; SIM-EXPORT-001/SIM-ADAPTERS-001|Synthetic nonuniform multi-shard native transport/descriptor acceptance; native exporter/scanner remain external.|
|RC-P2.2|PARTIAL; SIM-GRID-001/SIM-TEMPORAL-EXPORT-001|Versioned explicitly provisional block/slice adapter and simulator; preserve units/masks/revision/unknown native mappings.|
|RC-P2.3|PARTIAL tools; authentic recorded fixture only|Independent actual-provider model/datum/reference tuple external; no study replication or synthetic accuracy claim.|
|RC-P2.4|COMPLETE bounded transport; synthetic stream tests|Maintain interrupt/budget evidence; representative real-provider soak external.|
|RC-P2.5|PARTIAL; raster/vector/table adapters|Explicit categorical dictionary and multidimensional/time descriptors with faithful synthetic fixtures.|
|RC-P3.1|PARTIAL; SIM-REFRESH-001|Explicit provisional committed-revision two-client simulator contract/races, native semantics remain unknown.|
|RC-P3.2|COMPLETE bounded refresh; SIM-REFRESH-001|Retain stale/partial/outage controls while broadening linked protocols.|
|RC-P3.3|PARTIAL; SIM-LIFECYCLE-001/SIM-FAULTS-001 plus installed durable restart/no-replay controls, publication pending|Preserve expiry/cancel/no-replay and scoped results; native durable history/incarnation acceptance separate.|
|RC-P3.4|PARTIAL; SIM-PROVENANCE-001|Broaden dependency/semantic-effect fixtures without calling finite simulator lineage authoritative Java.|
|RC-P3.5|PARTIAL; SIM-NOTEBOOK-001|Multi-principal notebook/race acceptance over ordinary installed async client.|
|RC-P4.1|PARTIAL; SIM-SEMANTICS-001/SIM-INSPECTION-001|Capability-aware applicability/version/access and actual candidate source protocol adapter.|
|RC-P4.2|PARTIAL; source previews|Revision-aware validation scenario, parser and semantic admission kept distinct.|
|RC-P4.3|PARTIAL delivered imports; SIM-IMPORT-001/SIM-INPUT-BINDING-001|Broader annotation/compute input provenance with owned cleanup; native resource custody gate separate.|
|RC-P4.4|PARTIAL ADD/locks; SIM-PROJECT-LOCK-001/SIM-BEHAVIOR-DRAFT-001|Explicit conditional revision/edit/history/publication protocol seam; no blind overwrite.|
|RC-P4.5|PARTIAL native agent control/registration; protocol tests|Complete isolated managed-agent simulator lifecycle, terminal cleanup, reconnect and owner/denial.|
|RC-P5|IN_SCOPE PARTIAL; worker vertical mergedPR268/30460119d; durable simulator recovery installed-tested, pending next delivery|Next resource/descendant/cancellation hardening; native backend/ABI/deployment acceptance remains separate.|
|RC-R1|PARTIAL layered CI/test tooling|Install/coverage/contract/handoff CI per feature; external scheduled worker adoption is not local test success.|
|RC-R2|PARTIAL release checker|Package development artifacts; production release/support/owner approval remains external, no upload inferred.|
|CAP-01|PARTIAL providers/profiles|Expiry/reacquisition and simulator bootstrap matrix; actual issuer approval external.|
|CAP-02|PARTIAL catalog/peers|Mixed capabilities/explicit selection/recovery, no trust expansion.|
|CAP-03|PARTIAL origin-qualified routing|Explicit provisional composition/migration workflow behind contract adapter; no silent automatic failover.|
|CAP-04|PARTIAL typed requests|Broaden supported collections/relationship/context selection scenarios.|
|CAP-05|PARTIAL semantic explorers|Supported remote operations and revision/applicability fixtures, no local ontology inference.|
|CAP-06|COMPLETE bounded evidence DTOs|Maintain plan-versus-executed-effect distinction; full lineage belongs RC-P3.4.|
|CAP-07|PARTIAL Jobs/native async; durable completed/in-flight restoration installed-tested|Broaden bounded fairness/lifecycle where independently needed; native retention/restart contract remains unverified.|
|CAP-08|COMPLETE bounded scalar data|Preserve exact NUMBER/BOOLEAN/TEXT/null precision while adding separate block contract.|
|CAP-09|PARTIAL descriptors/exports|Multidimensional/category/time/multi-shard declared mappings and revision integrity.|
|CAP-10|PARTIAL graph/projections|Two-client revision/race/partial refresh scenario through explicit protocol seam.|
|CAP-11|PARTIAL transient hints|Explicit provisional durable cursor/resync/gap/expiry/revocation simulator adapter, not invented Java guarantee.|
|CAP-12|COMPLETE bounded offline snapshots|Maintain socket-forbidden fresh-process inspection and no queued replay.|
|CAP-13|PARTIAL resource inspection/import|Complete declared CRUD/visibility/dependency scenarios and supported native candidates.|
|CAP-14|PARTIAL authoring/agent facades|Complete revision-safe authoring and managed lifecycle simulator flows with separate native status.|
|CAP-15|PARTIAL optional scientific/notebook adapters|Categories/time/multidimensional and coherent notebook examples.|
|CAP-16|PARTIAL compatibility/package matrix|Fresh wheel/sdk/worker installs, types/lint/coverage and reproducible starter; real deployment/support gates separate.|
|CAP-17|IN_SCOPE PARTIAL; same separate worker as RC-P5, merged268 plus installed recovery pass|Complete resource/descendant/cancellation boundaries and maintained native deployment; no compiler/inference/storage port or embedded engine claim.|
|CAP-18|COMPLETE current safety invariants|Enforce per change: explicit origins, no mutation replay, no hidden fallback, optional extras and separate simulator.|

## Execution order and C interpretation

1. Install supplied seed, generated all-gap appendix/status counts and CI invariant;
   preserve16records/IDs/historical evidence. Commit first, then continue code.
2. Worker SDK is a **separate experimental package**, not embedded Java runtime:
   declared scoped inputs, exact geometry/units/validity, isolated computation,
   cancellation/budgets, validated atomic outputs and attributable provenance
   (planning guide Phase5 and originalRC-P5/CAP-17). Registration/task transport
   must use verified source where available; unestablished routes are explicitly
   SIMULATED_PROVISIONAL with a stable new gap, not fabricated Java APIs.
3. Coherent A/B workflows (durable lifecycle/data descriptors/revision authoring/
   agents/federation) each with adapter, simulator behavior, tests, handoff/registry,
   installed packaging and ordinaryPR/CI/merge; continue next independent task.
4. Exact native contracts, actual-provider science, public release decisions remain
   separately blocked; keep working on Python/simulator scope. Full staging execution
   follows implementation. No IDE repair/Java product changes/historical replication.

## Live issue review

Fresh full bodies/comments inspected2026-11-06T23:20:40Z:10services issues
(#89,#123–127,#129,#130,#144,#193) and one geospatial#10. Every original body hash
unchanged; no new obligation comments beyond preserved audit/knownkeep-open links.
Current receipt: `docs/java-service-contract-gaps/full-roadmap-issues.json`;8
OPEN_JAVA_REQUIRED and3OPEN_OTHER (#127nowin-scope C,#129release,#130research).
No fully superseded tracking-only candidates; zero new remote changes/closures.
Prior comments/conditions remain in canonical issue history; refresh before action.
New classes: FIXED_REAL, FIXED_PYTHON, TRACKING_MIGRATED, OPEN_JAVA_REQUIRED,
OPEN_OTHER. No tracking migration is justified until all original obligations and
exact executable tests have stable gap successors; Java functionality remains open
unless exact original issue obligations are resolved or lawful explicit migration.

## Continuous delivery and completion gate

No completion by test/PR counts. For every feature update this ledger's evidence,
the A+B evidence ledger when applicable, exact acceptance mapping, authoritative gaps
and maintainer handoff in the same PR. Verify actual final-head CI and merge ancestry.
No public release/develop promotion without its distinct approval/evidence. On reset
inspect current branch/remote/status and running tasks before repeating actions.
No autonomous execution is claimed after a session ends.
