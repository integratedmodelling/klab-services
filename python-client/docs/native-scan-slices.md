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

## Use and limits

```python
from klab_client import ScanSlice

values = observation.fetch_data([0, 19], curve="D2_YX", slice=ScanSlice.initialization())
# Use await observation.fetch_data(...) with AsyncClient.
# Only use a transition/input-change key and bounds obtained from real supported
# evidence; constructing a ScanSlice does not establish that the slice exists.
```

Dictionary callers retain their existing extension fields. The native top-level
fields are copied before the request loop; do not mutate nested extension metadata
while reads are in flight. Typed slices are immutable; to_wire() returns a fresh dict.
Default initialization is unchanged. Unknown types, whitespace-only keys, bool/float/
out-of-long timestamps and invalid intervals now raise InvalidRequestError locally,
instead of reaching Java as invalid reads. Initialization preserves explicit caller
bounds as Java's record does; its event() normalizes to initialization, not a chosen
time transition. EVENT/INPUT_CHANGE permit zero duration; temporal transitions do not.

Source-focused validation:135 client/slice/async tests PASS and lint PASS. A Java
constructor/JSON probe confirmed all four types, long edges, instantaneous event/
input changes, initialization behavior and rejected blank/reversed/zero transition
inputs. Initial old-installed-wheel import failure and a test's incorrect peer-header
assumption were corrected before installed validation; no production routing change.
Executable ba7773abd; full installed gates active. Actual typed initialization
transport/scalar validation remains pending; positive temporal execution NOT_RUN.

## Verified candidate outcome

ba7773abd:3,229 installed tests/all17gates/lint/build/source parity PASS. New slice
module32/32 statements,12/12 branches covered; no coverage exclusions or thresholds
changed. Actual owned Java PASS:20 independently specified234.5m scenario cells,
3 duplicate/order dict reads, exact typed/dict initialization metadata and23 read-only
requests; invalid slices produced zero HTTP. Creator context/session, all owned
services/listeners closed; unrelated Java unchanged. Exact installed/source/raw
harness/live bindings, private report hash and credential absence verified.
Wheel5fd95df06ac51237921783b2a79a5692b5b5c691f23f189c283bc5cce044ca21;
sdist6d5993c01d045a98cbe6f6ff9e726481b2f535c8169bdb8bdf68125c85b9d5ab.
Normal PR/CI/eligible merge remains pending. Positive transition/input-change
execution NOT_RUN; committed revision lease NOT_CLAIMED. Earlier pending checkpoints
above are historical, superseded only for this explicitly bounded outcome.
