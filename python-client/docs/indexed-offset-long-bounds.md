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
