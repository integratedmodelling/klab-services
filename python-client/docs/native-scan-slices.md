# Native indexed-read slice contract

Base c10555ebfcd698a192fe6e19e932b3b6e30b2501 (PR222). Python-only RC-P2.2 /
CAP-09 prerequisite: represent the existing StorageScan.Slice faithfully, not a
new remote window/revision API or scheduler. Reuse the existing owned worktree.

Demonstrated source gaps: Context._read_spec omits INPUT_CHANGE from maintained
Scheduler.Event.Type, accepts whitespace-only keys, reversed/zero-duration temporal
transitions and integers outside Java long. The current server Slice constructor
requires a nonblank key; non-initialization end >= start; TEMPORAL_TRANSITION needs
positive duration while EVENT/INPUT_CHANGE may be instantaneous. Java initialization
event normalization is not an assertion that arbitrary initialization metadata is a
temporal result. Preserve supplied key/timestamps verbatim, including negative epochs.

Add immutable ScanSlice(type,key,start,end), explicit initialization factory and
to_wire(). Validate typed and existing dict forms through one shared sync/async
read path before HTTP. Java long bounds and exact int (not bool) required; no date
guessing, unit conversion, key reconstruction, scheduler execution, snapshot lease,
revision discovery, bulk reader, scientific interpolation or historical fixture.
Existing default initialization and required explicit temporal selection remain.

Tests: all four actual Java types/interval rules/long edges/blank fields and unknown
types; typed/dict parity, exact key/timestamp retention, mutation isolation, sync/
async rejection before HTTP, source-derived Java constructor/JSON probe. Bounded
actual Java initialization read may establish typed read transport/scalar fidelity,
not positive INPUT_CHANGE/transition execution. Such execution requires independent
registered temporal model/support/result evidence; do not invent it here.

Review under KLAB_CLI_Contribution_Review.md; installed gates, eligible normal PR/CI/
merge and postmerge tests required. No Java production changes, dependency installs,
component publication or failed peer reruns. Full RC-P2.2/A/B/studies remain open.
