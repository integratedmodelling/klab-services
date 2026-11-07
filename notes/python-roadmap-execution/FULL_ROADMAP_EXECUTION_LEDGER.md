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
PR270 first CI failed: generated CONTRACT_MATRIX was omitted from staging and a
POSIX dead-PID inspection raced process disappearance. Corrective commit194a429d7
preserves the cleanup assertion and includes generated output. Its local run also
exposed a Windows asynchronous Job-termination/temp-directory cleanup race
(48passed/1failed), not claimed green: implementation now waits for zero active
Job processes before removing owned temporary custody. Reverification and final
head hosted matrix remain required; PR270 must not merge until those pass.
Containment corrective headb2fc7a414 includes explicit Windows Job termination,
zero-active-member confirmation and bounded retries while OS CWD handles release;
three consecutive49test local runs PASS. Fresh corrected installed suite and
final-head hosted checks are running. The original installed receipt is superseded
for modified execution modules, not silently relabeled.

Current branch `feat/python-worker-memory-budgets` preserves local WIP106bc9139
and integrates containment corrections without discarding work. Eight focused
memory tests PASS for invalid bounds, bounded success and actual over-allocation.
Full memory source suites now57worker+6HTTP PASS, mypy5files PASS; fresh installed
verification running in task-only `worker-memory` targets. A separate corrected
containment installed pass49worker/20HTTP+recovery/127simulator/33governance PASS
with90.22%aggregate Windows coverage. Its built bytes precede the final Windows-only
loader `getattr` typing correction14dca6345; no final-head installed-byte equivalence
is fabricated. Hosted runtime tests passed on Ubuntu in the prior run; final-head
matrix is running after correcting Linux mypy's Windows loader attribute check.
This is not complete verification: full installed/cross-platform memory evidence,
types/coverage/governance and normal publication remain pending. Explicit memory
semantics are Windows aggregate committed memory / POSIX per-process virtual
address space, not aggregate RSS. DefaultNone remains explicitly unbounded.
Next: validate full source memory/HTTP tests, build/install new artifacts, update
same0017/handoff, then publish after preceding feature integration; continue A/B.
Fresh memory artifacts now installed-verified:57worker/21HTTP+recovery/128full
simulator/33governance PASS, zero skips;5worker+14simulator module parity,
90.43%Windows aggregate coverage/common90%gatePASS, mypy/lint/schema/handoffPASS.
Receipt: `evidence/full-roadmap-worker-memory-20261107.json`. Native/scientific
acceptance unchanged. Publish a reviewable stacked memory PR against270's branch
while its final checks run; retarget to integration after270's verified merge.
Do not merge the stack out of order or call POSIX memory acceptance locally verified.

### Current next A/B vertical — declared multiband analysis

PR270 verified MERGED`a5828164341d810913fc9acdccf21514373a6c93`,00:14:46UTC,
Windows/Ubuntu3.11+3.13 and applicable final-head checksPASS. PR271 retargeted to
integration/marked ready, then verified MERGED
`96f696218ebc0f4603c0e1ecf350f4d73f308656`,00:17:41UTC; all applicable hosted
memory/runtime/schema/build/Jenkins checksPASS. Native/deployment/science untouched.

Current branch `feat/python-declared-multiband-analysis` integrates actual271merge;
uncommitted A/B SDK RasterBandAxis/open_raster extension and synthetic simulator
fixture/export flow are implemented. Source39scientific unit controls and one HTTP
multiband flowPASS. Initial manifest checksum failed because the creation script
reported LF text hash while Windows wrote CRLF; exact authored bytes are now pinned
as085d074c… with a specific -text attribute, no authentic fixture overwritten.
New TIFF6a41239e… is explicitly synthetic, not USGS/native-science evidence.
FreshSDK/worker/simulator wheel/sdist/installed verification and SDK changed-code
coverage are next. Existing0014/handoff updated in this same feature; no new gap.
Current localHEAD`1f7fc44bc26cab48be1f1cca55e01bbcc8a79b74` integrates actual271merge;
the multiband code/tests/fixture/docs remain uncommitted. Initial installed pass
4012SDK/57worker/21HTTP+recovery/129simulator/33governancePASS, zero skips. A
subsequent production change accounts for declared band-coordinate/provenance
buffers in the memory estimate, so final rebuilt artifacts are under separate
verification, not relabeled from the first pass. Source53analysis testsPASS and
all changed analysis.py lines/arcs covered; full-package __init__ coverage still
requires the final installed SDK report. Analysis typecheck (check-untyped-defs,
follow-imports=silent/ignore-missing-imports) and fatal lintPASS.
Running task: final fresh installed verifier in
`full-roadmap-20261106/declared-bands-final` (automatic completion notification).
No CI watcher or long-running service remains from270/271. Next command after
verification: compare source/artifact module bytes, evaluate SDK changed-code
coverage againstHEAD, run17gap simulated/real matrices, commit/push normalPR with
all generated outputs (especially CONTRACT_MATRIX) and evidence, then continue.
Two exact agent-created temporary directories left by pre-fix Windows tests were
owner-checked and narrowly removed; no unrelated user terminals/services touched.
Live issue re-review00:24:56UTC7November: all10services+geospatial10 still OPEN,
all original body hashes unchanged, all comments re-inspected with no new
unclassified obligations beyond the recorded127scope correction. Keep existing
8OPEN_JAVA_REQUIRED/3OPEN_OTHER; zero closure candidates or remote issue mutations.
Final installed pass is now verified:4012SDK/57worker/21workerHTTP+recovery/
129fullsimulator/33governance, zero skips/failures/errors. Module parity48SDK+
5worker+14simulator; exact hashes in
`evidence/full-roadmap-declared-bands-20261107.json`. FullSDKcoverage98.35% with
165historical excluded lines; ALL changed lines/arcs are covered/unexcluded, not
a false no-exclusions claim for the whole SDK. Scientific CI-equivalent separate
53unit+8HTTP suite alsoPASS and covers changed lines/arcs. Combined invocation
initially had2collection errors due conftest name collision; separate processes
fixed invocation isolation without weakening/skipping assertions.17linked cases
PASS(124SDK+57worker+58simulator/capture),17realNOT_RUN/exit3.
Next: final generated/lint/diff checks, stage ALL feature/generated files and
receipt, publish normal PR against integration; continue next independent work.
Final review found the new fixture could otherwise reach the inherited single-grid
point reader with a guessed first-band offset map. It now explicitly refuses that
unestablished path with422; new regression verifies this alongside foreign-owner
denial. SDK and worker tested module bytes remain unchanged, but simulator state.py
changed, so a separate fresh simulator-only rebuild/full129test+17gap rerun is
running before final artifact attribution/publication. Do not reuse the earlier
simulator hash for this refusal change.
All23feature files (including new binary fixture, explicit byte-custody attribute,
generated CONTRACT_MATRIX, CI coverage helper and living gap/handoff) are staged
pending the simulator-refusal verification receipt. No unrelated work is staged.
Current installedSDK047f895a… and worker8cb862eb… remain module-byte identical to
tested source. The only running verification is the fresh simulator-refusal build/
full-suite/linked-case runner; no live Modeler/Java/broker service was started.
That final refusal rebuild is now verified: simulator wheel3b2981b4…/sdista702a676…,
14module source/wheel/sdist/installed byte parity, full129testsPASS84.08s and17linked
casesPASS. SDK/worker bytes unchanged. No background verification remains. Finish
staging refreshed receipt/ledger/handoff, commit and publish normal featurePR now.

### Next active A/B slice — explicit retained-notification reference

Multiband PR272 verified MERGED`d65d940f979d0c03e481c37c9786c9ebc165da66`,
2026-11-07T00:41:13Z, all applicable final-head SDK3.11/3.12/3.13/core/scientific/
workerWindows+Ubuntu/schema/simulator/build/Jenkins checksPASS. No independent
maintainer review, native science or complete-roadmap acceptance claimed.

Current branch `feat/python-durable-notification-reference`, parentd65d940f9;
uncommitted new durable_notifications.py and separate simulator notifications.py,
SQLite log custody/old-checkpoint migration, tests/docs/CI and gap0005/handoff.
Explicit proposed protocol, not a native Java API: root-owner Job hints,
origin/epoch/sequence checkpoints, immutable bounded batches, duplicate replay,
retention/epoch RESYNC_REQUIRED, no implicit checkpoint advancement or earlier
history. Existing AMQP unchanged; native async/sync share schema.
Initial focused29unit+3HTTP/15recovery source testsPASS; new module100%unexcluded
lines/branches. Additional bad-query/corrupt-storage/migration tests now added;
rerun expanded suite before installed acceptance. Full packaging/SDK+simulator/
coverage/type/lint/schema/generated handoff and normal feature delivery pending.
Next: run expanded source tests and types, build/install fresh packages, full
verification/17case matrix, commit+publish normalPR, then continue independent work.
Expanded source suite35unit+9notificationHTTP/15recovery testsPASS. New module
100%unexcluded lines/branches and ALL changed SDK decisions covered; mypy body
checkPASS. Strict byte/UTF-8/duplicate-key/nonfinite decoding now uses an explicit
raw-bytes transport response mode; existing JSON/text semantics unchanged. An
initial installed verifier started before this last wire-hardening change; its
results must not be attributed to final SDK bytes. Final fresh package rebuild
and full suites/coverage remain required; simulator source is unchanged by this
SDK-only hardening.
Initial pre-wire installed receipt completed4041SDK/24SDK-reference/57worker/
21HTTP+recovery/138simulator/33governancePASS, zero skips. It is retained as an
earlier pass, NOT final strict-decoder acceptance. Final strict-byte SDK adds six
bad-wire unit cases; expected final counts must be read from the separate completed
receipt, not inferred. Final verifier runs in `durable-notifications-final`;
initial targets remain unchanged. Coverage attribution must select only the final
installed module paths (and use a unique data file for any focused rerun) before
claiming newmodule100% and changed-code acceptance.
Final installed strict-wire pass completed4047SDK/24SDKreference+recovery/57worker/
21workerHTTP+recovery/138fullsimulator/33governancePASS, zero skips/errors/failures.
New module97/97lines and40/40branches, zero exclusions; all changed SDK decisions
covered. FullSDK98.37% retains165historical exclusions; every measured SDK path
verified uniquely in the final installed target. Artifacts49SDK+15simulator+5worker
module parity, exact hashes in `evidence/full-roadmap-durable-notifications-20261107.json`.
Initial pre-wire artifacts remain separately attributed. Final test assertions now
also verify no bad-query activation and a real bounded server read (not cached
configuration or complete graph resync); focused9HTTP+17case rerun completedPASS.
Current real matrix17NOT_RUN/exit3, no fallback. Next: finish final case receipts,
generate/check all outputs, stage same-feature code/tests/0005/handoff/ledgers/receipt
and publish normalPR; then continue next independent vertical.

PR273 published as90a5d41420bb13071e491f61955168dc788234e6 and remains OPEN:
hosted offline SDK-only coverage correctly failed because successful sync/async
wire wrappers and bytes transport were covered only by the separate simulator
suite in the combined local report. This invalidates delivery acceptance, not
the historical combined-test coverage receipt. No gate is weakened.
Workflow run42321214993 also failed before jobs: GitHub reports line31 col14,
Unexpected symbol HEAD^ within doubled-single-quoted expression. YAML parsing
alone did not validate GitHub expression syntax. Correction uses an env expression
with one quoted HEAD^ literal and passes its value as a quoted shell argument.
Isolated worktree `notification-ci` preserves the primary revision-safe-drafts
branch's two untracked/unwired prototypes. Two added SDK-only MockTransport tests
exercise real sync/async Clients, bound contexts, tail bootstrap, subsequent cursor
query, strict raw-byte decoding and transport response handling. Focused37tests
and newmodule100%unexcluded line/branch gate PASS without simulator tests. Full
SDK unit/integration+historical-baseline coverage gate and24HTTP/recovery rerun
are running; final-head hosted success remains required before PR273 can merge.

Corrective notification head289ba5325 pushed; installed4049SDK tests and SDK-only
historical-baseline changed-code/newmodule100%gate PASS plus24HTTP/recoveryPASS.
Verification attempts retained: source-tree imports caused3preflight failures
(4046passed); the first installed custody check stopped before tests due Windows
checkout CRLF vs earlier LF bytes. All49modules match after newline normalization,
not raw-byte parity; production source diff of correction is empty. Final installed
rerun passed4049 in83.47s and24HTTP/recovery in10.10s, using prior fresh-wheel target
with unchanged normalized production content. Hosted reference workflow now starts
and PASS; one Windows3.11 worker recovery job failed a2second ordinary Job wait
before worker execution. Run42321807691 failed job128247863255 rerun once unchanged;
no retry inside tests or timeout/assertion/coverage weakening. PR273 staysOPEN until
all applicable final-head checksPASS. Original failures remain part of evidence.

### Active private draft reference pass

Current primary branch `feat/python-revision-safe-drafts` integrates289ba5325 and
retains/extends original two prototypes. Explicit root-owner private draft CAS and
bounded8name/8version history now wired to simulator, SQLite snapshot/validate/
restore/rollback and released-scope cleanup. New sync/nativeasync SDK APIs require
explicit opt-in/expected revision and exact hash/source/result validation. No
semantic validation, accepted-source editing, publication or execution inferred.
Source24SDK-only unit tests PASS with101/101lines+38/38branches and no exclusions;
14HTTP/storage tests PASS (concurrent writer, stale fence, restart, owner denial,
bounded-history410,capacity429,corruption,no partial restore,failed-save507).
New module bodytypecheck/lintPASS. Same0009/handoff/scenario/docs/CI updated; full
fresh installed SDK/simulator/worker/governance and17case evidence pending. Sleep
requested ONLY after the entire roadmap checklist finishes, never after this
partial pass or a blocked checkpoint; sleep remains pending, no power action run.
PR273 subsequently verified remotely MERGED
`0c982589322c47733fd80166069f97f879207c8b`,2026-11-07T01:09:50Z, exact corrective
head289ba532513fab5a9c8777802642e7f80522b8e4. All applicable final-head SDK3.11/12/13,
reference, workerWindows/Ubuntu, schema/scientific/simulator/build/Jenkins checks
PASS. Windows recovery's unchanged rerun passed job128248687104; failure retained,
not erased or relabelled. Primary branch fast-forwarded actual integration merge
without touching uncommitted draft work. No external acceptance status promoted.
Fresh draft-package verifier started in task-only
`full-roadmap-20261106/revision-drafts`, own coverage data and source/artifact byte
comparison. SDK coverage report is deliberately SDK-only: the separate HTTP/storage
process does NOT append to it, preventing recurrence of273's acceptance mistake.
Await automatic completion, then verify newmodule100%/all changed decisions against
that report and run17linked simulated/real-not-run matrices. No Java/IDE readiness
check or repair is needed/authorized; persistent state snapshot remains historical.
Fresh installed draft verification nowPASS4073SDK/38referenceHTTP+storage+recovery/
57worker/21workerHTTP+recovery/152fullsimulator/33governance, zero skips/errors/failures.
50SDK+16simulator+5worker source/wheel/sdist/installed module byte parity. SDK-only
newmodule101/101lines+38/38branches, no exclusions, all report paths uniquely in the
new installed target. FullSDK98.40% retains165historical exclusions; no whole-SDK
zero-exclusions claim. Exact artifacts in `evidence/full-roadmap-revision-drafts-20261107.json`.
SDK changed-code gate and17linked simulated-case process started separately; await
its automatic completion before attesting those results.17currentrealNOT_RUN/exit3
already verified without service invocation or fallback. Next publish normalPR
with all same-feature generated outputs only after final matrix attestation.
Final SDK-only changed-code/newmodule gate PASS.17linked casesPASS using185SDK/
81simulator-capture-reference/57worker tests, zero skips/failures/errors. The real
matrix remains17NOT_RUN/exit3. All local acceptance for this bounded private-draft
slice is verified; publish normal featurePR now and await final-head hosted checks.
Broader authoring validation/publication and full42criterion acceptance are NOT
complete. Fresh01:13:01UTC issue inventory retains all11open obligations, no new
closure candidate; no remote issue action. Entire-roadmap sleep remains pending.

Draft slice committedc96b74c59 and published normalPR274 against integration;
watcher found Windows3.13 recovery failure run42323183860/job128252296397 during
WorkerClient's200msstatus poll, yielding the intentional fail-closed unknown
transport error. This is distinct from273's ordinary2sJobwait. One unchanged
diagnostic rerun requested; no production status timeout, assertion or gate change.
PR274 staysOPEN until all applicable final-head checksPASS. The200msmonitoring
policy can reject slow service responses; native/deployment latency acceptance
is not inferred from a successful rerun. Prior failure evidence remains retained.

### Active caller-declared categorical raster pass

Current branch `feat/python-declared-raster-categories`, parentc96b74c59, preserves
pending274 delivery. New `RasterCategories`/`categorize_raster` performs only local
explicit code→label mapping over detached integer rasters; all raw codes/dtypes,
mask/coordinates/units preserved. Unknown valid codes refuse, no zero/negative→
nodata coercion or floating conversion; exact signed64/extremeunsigned controls
included. Source20SDKunit tests and one SDK-owned export/classification/cleanup
HTTP testPASS, newmodule51lines/20branches100%unexcluded; bodytypecheckPASS after
using explicit dynamic optional-xarray dataset typing, not typeignore suppression.
Synthetic rawcode observable `synthetic:CategoryCode` has no units; it is not a
fake elevation or landcover model. Separate pinned2x3int16 TIFF e025556f… and
UTF8LFmanifest8fa1375f… (-text custody) do not change authentic fixtures. Explicit
caller dictionary/provenance is not embedded native category metadata/ontology
inference/committed science. Same0014/handoff/scenario/docs/scientificCI updated;
fresh installedSDK+simulator/changed-code+scientificsubset/governance still pending.
Next: finish unchanged274checks/permittedmerge, fresh package categorical slice,
verify fullSDK/newmodule100% and17cases, publish next normalPR; continue remaining
multishard/managedagents/provenance/federation. Entire-roadmap sleep remains pending.
PR274 subsequently verified remotely MERGED
`88373f7683c8acd994f27031bbd1654fc5b2b62a`,2026-11-07T01:24:39Z, exact head
c96b74c5946475ad5b16faeaf5f521ce07c82a74. All applicable final-head checksPASS;
Windows3.13 recovery's unchanged single rerun passed128252893568. The original
short-monitoring failure remains recorded, not declared fixed. Primary category
branch fast-forwarded actual integration merge, preserving all uncommitted work.
Categorical source scientificCI-equivalent73unit+9HTTP PASS with100%newmodule and
all changed decisions covered. Fresh installed full verifier now running in
`full-roadmap-20261106/raster-categories` against new package bytes, separate SDK-only
coverage. No native/scientific result promoted; sleep remains pending.
Final review tightened the numerical raster/mask contract: matching shape alone
does not establish matching dimension identities. Added an explicit misdimensioned
boolean-mask refusal and regression; source20tests/newmodule100%51lines20branches/
bodytypecheck/lintPASS. Because this changed production code after the first build,
fresh `raster-categories-final` package verification was started separately; earlier
`raster-categories` artifacts are superseded for final-head SDK attribution. Keep
both receipts distinct. The scientificCI-equivalent73unit/9HTTP result predates
this guard and must be rerun against final installed bytes for final acceptance.
Earlier categorical installed pass completed4093SDK/39referenceHTTP+storage+
recovery/153simulator/57worker/21workerHTTP+recovery/33governancePASS, zero skips.
Its SDKa93da2d8… wheel precedes the final mask-dimension guard and is NOT final-head
acceptance. Preserve that receipt separately; final `raster-categories-final`
verifier is still running. No background CI watcher remains after274merge.
Final categorical rebuilt pass now4093SDK/39referenceHTTP+storage+recovery/
153fullsimulator/57worker/21workerHTTP+recovery/33governancePASS, zero skips/failures/
errors.51SDK+16simulator+5worker raw module source/wheel/sdist/installed parity.
SDK-only newmodule51/51lines+20/20branches, zero exclusions; ALL changed SDK lines/
arcs covered. FullSDK98.41% retains165historical exclusions, not an exclusion-free
wholeSDK claim. Final installed scientificCI-equivalent73unit+9HTTP alsoPASS,
including scientific changed-decision/newmodule100%gate. Exact hashes/initial-pass
attribution in `evidence/full-roadmap-raster-categories-20261107.json`.
Only final17linked simulated matrix is running; real17NOT_RUN/exit3 alreadyverified.
Await automatic completion then publish normalPR with all code/fixtures/generated
outputs/evidence. Do not sleep at this checkpoint or call fullABC complete.
Linked categorical final matrix now17PASS(205SDK/82simulator-capture-reference/
57worker tests), zero skips/failures/errors; real17NOT_RUN/exit3 unchanged. All
bounded local verification complete; publish categorical normalPR and await
applicable final-head checks, then continue independent roadmap implementation.
PR275 subsequently verified remotely MERGED
`c1ab4dd669941df8ef5d3d0eedde7204726d4236`,2026-11-07T01:40:54Z, exact head
19078f5471e7f4078315210aa6fe4e7dd0bd0e8c. All applicable final-head checksPASS,
including scientific/newmodule gate and Windows+Ubuntu worker controls; no failed
check rerun was needed for275. No independent maintainer review or native acceptance
claimed. Next branch `feat/python-scoped-read-plan-reference` fast-forwarded actual
merge without discarding its uncommitted prototype.

### Active next read-plan/shard reference prototype

Uncommitted `python-client/src/klab_client/read_plans.py`, simulator `read_plans.py`
and `test_read_plans.py`, plus state.py wiring, implement explicitly proposed
`klab.experimental.read-plans/1` under existing0006/0014. Synchronous root-owner
open/bounded shards/explicit release, immutable manifest/cell masks/hashes and full
assembly guards are implemented. Hash revision is explicitly
REFERENCE_CONTENT_SHA256_NOT_NATIVE_COMMIT; sourcefixture/origin, float64dtype,
shape/CRS/north-to-south row-major affine/unit metadata remain declared reference
semantics, NOT existing native Point route assumptions or committed science.
Process-local TTL grants intentionally do not persist across restart; fresh epoch
fences old grants and no automatic replan/replay. Ephemeral rows/epoch participate
in failed-durable-request rollback without persisting capabilities.
Source3HTTP/server controlsPASS:400cells/sixshards incl zero/negative/missing,
wrong-order refusal, foreign owner, release410, restart loss/new explicit plan and
synthetic buffer pinning/expiry. Initial2HTTP tests failed501because routing strips
trailing collection slashes; proposal now uses explicit `/open`, no native API
authority inferred and no automatic retry introduced. Bodytypecheck/fatalLintPASS.
Still pending: SDK-only positive/negative/newmodule100%coverage, nativeasync parity,
expanded lease/budget/rollback controls, public exports, same0006/0014 handoff/docs/
scenario/CI, fresh full installed artifacts,17case runner and normal delivery.
No current background verifier/watcher remains after275; no live Java service,
IDE repair or scientific model execution occurred. Entire-roadmap sleep pending.

Read-plan follow-up: first SDK-only run6PASS/47FAIL because test observation
omitted required observable DTO, not a native contract defect. Corrected fixture
without simulator imports or weakened decoding; now64SDK-only testsPASS and
180/180lines+62/62branches100%unexcluded. Real sync/nativeasync MockTransport open/
read/release, preflight/foreignbinding/budgets/malformedJSON/incomplete or mixed
assembly and unknown mutation outcomes/no replay tested. Exact boolean release
confirmation rejects numeric1. Newmodule bodytypecheck/fatalLintPASS.
Expanded22HTTP/server testsPASS: nativeasync lifecycle,16grant capacity, oversized
manifest no-allocation, owner/root/Runtime/epoch denial, TTL/cleanup/restart loss,
failed save open/release/reset restores durable state AND ephemeral rows/epoch,
intake/byte/cell denials and successful reset epoch rotation. First expanded pass
4FAIL/4PASS used insufficient synthetic manifest size and invalid manually seeded
observation counter; corrected fixtures (1600cells/counter2), no production gate
weakening. Read-plan proposed docs/publicexports/CI and same0006/0014registry,
scenario/Ferdinando narrative/generated companions now updated together.
Fresh separate wheel/sdist/installed verifier running in
`full-roadmap-20261106/read-plans-first`, SDK-only coverage/report process separate
from simulator HTTP. Final tests were extended during run; use receipt counts as
observed and rerun late tests against installed artifacts before final attestation.
No read-plan commit/push/PR yet. Next commands after automatic completion: SDK-only
changed-code/newmodule gate; installed22HTTP/64SDK controls if receipt predates test
additions;17linked simulated versus realNOT_RUN matrices; record exact custody and
publish ordinary featurePR/finalheadCI. Entire42row roadmap remains incomplete;
sleep remains pending and live Modeler gates are not needed for this Python work.
Read-plan fresh installed verifier completed4157SDK/61referenceHTTP+storage+
recovery/175fullsimulator/57worker/21workerHTTP+recovery/33governancePASS, zero
skips/failures/errors. Observed totals include all64new SDK and22HTTP/server tests,
so late test additions WERE included.52SDK+17simulator+5worker raw module bytes
match source/wheel/sdist/installed, rechecked against current source after receipt.
SDK-only newmodule180/180lines+62/62branches100%unexcluded; fullSDK98.45% retains
165historical exclusions, not a wholeSDK exclusion-free claim. Exact custody:
`evidence/full-roadmap-read-plans-20261107.json`. Final SDK changed-code gate and
17linked simulated matrix started; wait automatic completion before attestation.
17realNOT_RUN/exit3 verified with no native invocation/fallback. No read-planPR yet;
normal delivery follows final matrix success; all native status distinctions held.
Final read-plan SDK-only changed-code/newmodule gatePASS.17linkedcasesPASS using
269SDK/104simulator-capture-reference/57worker tests, zero skips/failures/errors;
real17NOT_RUN/exit3unchanged. All bounded local checks now verified. Publish normal
read-plan featurePR next, merge only after applicable final-head hostedchecksPASS,
then continue independent managed-agent/provenance/federation scope. Entire42row
roadmap still incomplete and sleep remains pending; no native repair authorized.
Read-plan committed/pusheddd8ee781d and normalPR276 published against integration;
final-head watcher running, no merge or hosted success inferred yet. Local evidence
remains distinct from native acceptance. No read-plan artifact overwrite.

### Active managed synthetic lifecycle reference

Current `feat/python-managed-agent-reference` branches fromdd8ee781d while276CI
runs. New SDK/simulator `managed_agents.py` explicitly proposes
klab.experimental.managed-agents/1; it parses/compiles/executes NO k.Actors source.
Named idle/finish/fail finite fixtures reference only caller-root BEHAVIOR-like
ownership. No SCRIPT/APP/TESTCASE private sessions, task restrictions, user singletons,
observation binding or custommessaging/askreply semantics are fabricated. Native
AgentExecutions/DeferredAgent/AgentChannel remain separate and no real-error fallback.
Immutable Runtime/root/epoch references, single-use start, CASrevision stop, terminal
read and later synthetic cleanup confirmation/purge implemented. SDK close never
auto-stops/purges. Current owner/root/Runtime denials, explicit SDK rebind/no automatic
command replay and restart-loss410with new process epoch tested. Failed durable
create/start/stop/status tick/purge/reset rolls back rows/epoch+durable state.
Source35SDK-only testsPASS at133lines/48branches100%unexcluded;12HTTP/server tests
PASS incl finite16retainedrow cap and request/identity/intake denials. Newmodule
bodytypecheck/fatalLintPASS. Same0008/handoff/scenario/docs/CI/publicexports updated,
but fresh installed regressions/SDKcoveragegate/17case evidence and normal delivery
remain pending. No actual native execution/disposal acceptance inferred;0008stays
NOT_TESTED and issue125open. Read-planPR276must pass final-head checks before merge.
Entire42criterion roadmap and final sleep remain pending.
Read-planPR276 subsequently verified remotely MERGED
`8d4068b3bb6a3683526b805d5f269ef09ecd004e`,2026-11-07T02:04:58Z, exact head
dd8ee781d452914a18517ee67a9b4fd082b8d75b. All applicable final-head checksPASS,
no failed-check rerun needed. Current managed-reference branch fast-forwarded actual
integration merge without discarding uncommitted work; no independent maintainer
review/native acceptance claimed. Fresh managed installed verifier is running in
`full-roadmap-20261106/managed-agents-first`; do not poll/overwrite its artifacts.
Fresh02:04:41UTC read-only issue inventory fetched all11open bodies/comments;
original bodyhashes unchanged, no newly unclassified commentURLs or safe closure
candidate. All8Java/3other obligations stayopen; no remote issue mutation.
Fresh managed-reference installed4192SDK/73referenceHTTP+storage+recovery/
187fullsimulator/57worker/21workerHTTP+recovery/33governancePASS, zero skips/errors/
failures.53SDK+18simulator+5worker source/wheel/sdist/installed rawmodule parity;
current source rechecked after receipt. SDK-only newmodule133/133lines+48/48branches
100%unexcluded; fullSDK98.48% retains165historical exclusions. Exact custody:
`evidence/full-roadmap-managed-agents-20261107.json`. SDK-only changed-code gate/
17linked simulated matrix running;17realNOT_RUN/exit3alreadyverified without native
invocation/fallback. All source/execution limitations preserved; no managedPR yet.
Next wait automatic matrix receipt, record result then publish ordinaryPR/finalCI.
Final managed-reference SDK-only changed-code/newmodule gatePASS;17linkedcases
PASS(304SDK/116simulator-capture-reference/57worker), zero skips/errors/failures.
17realNOT_RUN/exit3unchanged. Publish ordinary managed-referencePR next and await
applicable final-head checks before permitted merge. Synthetic lifecycle success
does NOT satisfy native execution/private-session/resource-disposal acceptance.
Managed-reference normalPR277 published atd5c2f3af5. Hosted schema-and-substitutes
failed runs42327949766/job128267539442 and42327954764/job128267555276 on generated
JAVA_GAP_STATUS.md drift. This file WAS generated locally but omitted from explicit
gitadd, so local33governance/generated-checkPASS did not establish committed report
parity. No rerun can fix an unchanged deterministic missing-file commit. Correction
adds the missing generated companion only, keeps0008nativeNOT_TESTED and all drift
gates unchanged. Local33governance and generated-checkPASS again; push correction
then require final-head hosted checks before merge. Earlier installed production
code evidence remains attributable because this fix changes no tracked production
SDK/simulator/worker code. A separate untracked read_plan_analysis.py prototype
belongs to the next branch and is NOT part of277's artifacts or correction.
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
|RC-P1.2|PARTIAL; SIM-RESTORE-001 and SIM-DURABLE-RESTORE-001 delivered269/8f9fe3805 (0004/0017)|Completed/in-flight saved Job, context and observation reattach across owned simulator processes without replay; native retained-Job/incarnation semantics still external.|
|RC-P1.3|PARTIAL; SIM-SCOPE-SELECTION-001|Complete collaborator/forbidden/expiry scenario, preserving server authority.|
|RC-P1.4|PARTIAL; discovery guards|Mixed-provider outage/recovery and identity drift scenario.|
|RC-P1.5|PARTIAL; SIM-SELECTION-001|Cover material project/observer/grid option effects, distinguishing advertised model from actual selected input.|
|RC-P2.1|PARTIAL; SIM-EXPORT-001/SIM-ADAPTERS-001|Synthetic nonuniform multi-shard native transport/descriptor acceptance; native exporter/scanner remain external.|
|RC-P2.2|PARTIAL; SIM-GRID-001/SIM-TEMPORAL-EXPORT-001, declared-band272/d65d940f9|Versioned explicitly provisional block/slice adapter and simulator; caller-labelled time bands are not native committed history; preserve units/masks/revision/unknown mappings.|
|RC-P2.3|PARTIAL tools; authentic recorded fixture only|Independent actual-provider model/datum/reference tuple external; no study replication or synthetic accuracy claim.|
|RC-P2.4|COMPLETE bounded transport; synthetic stream tests|Maintain interrupt/budget evidence; representative real-provider soak external.|
|RC-P2.5|PARTIAL; raster/vector/table and delivered declared-band272/d65d940f9|Explicit categorical dictionary/multishard still pending; native time/revision meaning remains unestablished despite caller-declared band support.|
|RC-P3.1|PARTIAL; SIM-REFRESH-001|Explicit provisional committed-revision two-client simulator contract/races, native semantics remain unknown.|
|RC-P3.2|COMPLETE bounded refresh; SIM-REFRESH-001|Retain stale/partial/outage controls while broadening linked protocols.|
|RC-P3.3|PARTIAL; SIM-LIFECYCLE-001/SIM-FAULTS-001 plus delivered269 durable restart/no-replay controls|Preserve expiry/cancel/no-replay and scoped results; native durable history/incarnation acceptance separate.|
|RC-P3.4|PARTIAL; SIM-PROVENANCE-001|Broaden dependency/semantic-effect fixtures without calling finite simulator lineage authoritative Java.|
|RC-P3.5|PARTIAL; SIM-NOTEBOOK-001|Multi-principal notebook/race acceptance over ordinary installed async client.|
|RC-P4.1|PARTIAL; SIM-SEMANTICS-001/SIM-INSPECTION-001|Capability-aware applicability/version/access and actual candidate source protocol adapter.|
|RC-P4.2|PARTIAL; source previews|Revision-aware validation scenario, parser and semantic admission kept distinct.|
|RC-P4.3|PARTIAL delivered imports; SIM-IMPORT-001/SIM-INPUT-BINDING-001|Broader annotation/compute input provenance with owned cleanup; native resource custody gate separate.|
|RC-P4.4|PARTIAL ADD/locks plus delivered274 private draftCAS/history; native acceptance separate|Private CAS is not accepted-source editing/validation/publication; maintained native conditional authoring contract remains unestablished.|
|RC-P4.5|PARTIAL native agent control/registration; protocol tests|Complete isolated managed-agent simulator lifecycle, terminal cleanup, reconnect and owner/denial.|
|RC-P5|IN_SCOPE PARTIAL; worker268/30460119d, recovery269/8f9fe3805, containment270/a58281643, memory271/96f696218 merged|Declared trusted worker lifecycle implemented/tested across Windows+POSIX; total disk/hostile-code isolation and maintained native backend/ABI/deployment acceptance remain separate deficits/gates.|
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
|CAP-11|PARTIAL; transient hints plus delivered273/0c9825893 retained reference|Explicit provisional durable cursor/resync/gap reference verified; maintained expiry/revocation/full convergence/native acceptance still unverified.|
|CAP-12|COMPLETE bounded offline snapshots|Maintain socket-forbidden fresh-process inspection and no queued replay.|
|CAP-13|PARTIAL resource inspection/import|Complete declared CRUD/visibility/dependency scenarios and supported native candidates.|
|CAP-14|PARTIAL authoring/agent facades|Complete revision-safe authoring and managed lifecycle simulator flows with separate native status.|
|CAP-15|PARTIAL optional scientific/notebook adapters|Categories/time/multidimensional and coherent notebook examples.|
|CAP-16|PARTIAL compatibility/package matrix|Fresh wheel/sdk/worker installs, types/lint/coverage and reproducible starter; real deployment/support gates separate.|
|CAP-17|IN_SCOPE PARTIAL; same separate worker as RC-P5, merged268–271 with installed and hosted lifecycle/resource controls|Total disk/hostile-code isolation and maintained native deployment remain separate; no compiler/inference/storage port or embedded engine claim.|
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
