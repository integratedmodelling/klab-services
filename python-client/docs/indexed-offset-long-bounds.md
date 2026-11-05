# Native indexed-read offset bounds

Base597b2fed15f3b25960667a7d47322a55ef96d796 (PR224). Shared-reader PR225 is
independently under review/CI; reuse same owned worktree on a separate branch from
accepted integration. Do not include its acceptance evidence as this feature's tests.

Reproduced Python input-validation defect: Context._read_spec accepts offset2**63
and sends it to HTTP although native StorageScan.Point declares long offset and
requires nonnegative values (StorageScan.java338–359). Python arbitrary integers
outside that wire domain are invalid, not a Java defect or a reason for Java repair.
Reproducer indexed-offset-long-bound-reproducer.json retained, mocked network only.

Smallest correction: shared preflight accepts exact int0..2**63-1 only; retain the
existing1..256 bounded iterable/ordering/duplicate rules. Both sync and nativeasync
inherit it; no signature/dependency/Java/scientific algorithm changes. Do not infer
geometry cell counts or map high offsets to fabricated positions. A signed-long
maximum is transport-domain valid, not proof that any observation contains that cell.

Tests: failing-beforefix >long cases for both facades, no HTTP for oversized offset
anywhere in a request, positive0/maxlong/duplicates/iterator boundaries on mocked
transport, existing bool/negative/empty/257cell rejections. Focused/installed gates
and exactcandidate binding required. Existing actualJava low-offset science proves
ordinary values only; no giant-observation scientific fixture or unchanged model
rerun is required for rejecting impossible wire integers locally. No temporal/
revision/offset-existence or original-study acceptance promoted. Review/normalPR/
CI/eligiblemerge/ancestry/postmerge required; then continue next executable queue.

## Red/green/native contract evidence

New tests on unchanged SDK:6FAIL/2PASS (out-of-long cases in sync/async vs positive
wire-domain/control cases). Correction adds one shared upperbound, no offset casting
or clamping. Focused/fullinstalled validation active. NativePointOffsetContractProbe
on unchanged Java constructor/JSON PASS: offsets0/maxlong preserved exactly, negative
constructor offset rejected and out-of-long Java string parse rejected. This is
domain/serialization evidence only, not a service/giant-observation scientific run.

e9a4b6e37:154focused/3,249installed/all17gates/lint/build/sourceparityPASS.
Wheel e8b41bdea24a8c4b1ad8189578212959f9f936a011f478c33f9440c0f8d2f45c;
sdist24e467444690e0755ed7b46029888ffa4594e7d3697fb4a2bc7717dc894164f8.
Exact source/installed/artifact/rawharness identity and native-domain probe bound
in indexed-offset-long-bounds-final-validation.json. No new services/credentials
or scientific live run. Combined tree with eventual accepted PR225 must be checked
before delivery; its additional harness tests are absent from this baseline count.
