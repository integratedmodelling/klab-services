# Worker output/protocol custody development review

ParentPR302 verifiedMERGED4fe5d45adb9da57fc9fc7573c840ec36403aa535 at
2026-11-07T20:19:37Z, exact4ed4352bfaaf387cbdd9219ade942941c5188e98/all34checks
PASS; remote/local ancestry checkpoint verified.42original IDs/history/22runnable/
zero finaldispositions. Native actor/science/final-row acceptance remains separate.

Worker changes: one aggregate stdout+stderr acceptance/monitoring budget instead of
separate per-stream acceptance; owned ProcessTree stop before final size/read/validation;
strict exact operation ACK/state/identity, claimed input/lease, published scoped output/
provenance digest. A protocol-only200 is not a receipt. Local input/output layout/mask/
unit remains unchanged; output/implementation/input hashes are custody, not science or
cryptographic authority. No retry/recompute/replay introduced; FAILED rows stay retained.

Actual public HTTP aggregate-output/changed-unit failures publish neither output nor
provenance; explicit new task can succeed while failed predecessor remains. Existing
cancel/restart/lostlease/explicitconsent recovery tests preserved. Wrong or missing ACK,
bad lease/input/state/scope/provenance/hash refuses; invalid claim cannot run operator
script. Child errors/stderr/credentials are not forwarded as backend failure messages.

Source113workerunit/23publicHTTP-recovery PASS; entire new _responses module100% and
all changed worker lines/decisions unexcluded PASS; unchanged common90%gatePASS at
95.95%, platform containment remains fully reported with no exclusions. Types/fatal
lint PASS;7coverage-tool controlsPASS after generalizing the unchanged defaultSDKgate
with explicit --package python-worker. No SDK production module changed in this slice.
Existing Windows/POSIX containment semantics stay distinct; hosted OS matrix pending.

Deployment handoff explicitly lists maintained native ABI/roles/lease/publication/
retention/scientific and actual reviewed OS/container enforcement evidence still owed.
Monitored output can overshoot; arbitrary cwd files are NOT capped. No hard-total-disk
quota, hostile-code sandbox, uniform aggregate RSS or native deployment approval claimed.
POSIX trusted scripts must not deliberately escape process groups; Windows Job semantics
are stronger/different. Handoff documentation is not approval or deployment authorization.

Retained development evidence:
- Independent aggregate-output next-slice probe initiallyDIDNOTRAISE: valid stdout and
  diagnostics each below800bytes could exceed800combined. Existing contract was per-
  stream; new aggregate requirement now verified, not mislabelled prior scientific defect.
- Protocol-only registration200 probe initiallyDIDNOTRAISE; strict ACK control now refuses
  without replay. Existing72workerunit/6publicvertical tests passed on initial changes.
- Firstexpanded113candidate run110PASS/1FAIL (before2new controls): unpaired Unicode
  string was correctly escaped by default json.dumps(ensure_ascii=True), not a transport
  encoding error. Removed unnecessary UnicodeError handling and corrected the test to
  actual nonfiniteJSON refusal; no product parser relaxation. Hugeint deadline now safely
  raises WorkerError via finite() instead of raw OverflowError.
- Added live-child aggregate monitoring and deterministic tree-stop-before-final-read
  ordering controls. Output monitors remain acceptance limits, not hard disk enforcement.

Fresh installed artifacts/linked matrix/exactheadCI/ordinaryPR delivery pending. Native
workerABI/scientific execution/deployment policy remain JAVA-GAP-0017; no native fallback,
IDE/OS repair, release/develop promotion or original-row acceptance authorized.

Final source pass115workerunit/23publicHTTP-recovery PASS; newmodule/changedworker
100%unexcluded, unchanged aggregate80%gate93.07% and common90%gate96.85% PASS;
full6worker-module bodymypy/fatallint/56governance-queue-ordering-featuretoolcontrolsPASS.
Review additionally refused '.'/'..' path-only task selectors both locally and in
remote claim receipts before HTTP normalization can escape the task route. No route
or namespace authority is inferred from a syntactically valid dot segment. Source frozen.

Final worker-output-custody-first installed5152SDK/501simulator/115worker/23workerHTTP/
allreference/39governancePASS zero skips/errors/failures;raw69SDK35sim6worker parity,
no simulatorinproduction.17linkedPASS(1218SDK378sim115worker)vs17nativeNOT_RUN.
Fullinstallednewmodule/changedworker100%/originalaggregate93.07%common96.85%gatesPASS;
14olderSDKfocusedCI+orderedselectionPASS. No simulatorservicesremain. PR303finalreceipt/
exactnewheadCI/ordinarymergepending; no nativeABI/OS/scientific/final-row acceptance.
