# Selected finite reference windows (experimental)

`ReadPlanSelections` / `AsyncReadPlanSelections` require an owned root Context,
`allow_experimental=True` and exact Runtime capability
`readPlanSelectionProtocol="klab.experimental.read-plan-selections/1"`.
No native-service failure triggers a simulator fallback.

```python
from klab_client import ReadPlanSelections, ReferenceWindow, analyze_selected_read_plan

reads = ReadPlanSelections(context, allow_experimental=True)
states = reads.states(observation)
snapshot = reads.capture(observation, ReferenceWindow(2, 5, 4, 8), states[0],
                         shard_cells=5, max_cells=12)
dataset = analyze_selected_read_plan(snapshot)  # detached; no network or live grant
```

Async methods are natively awaited. `capture` opens once, reads its established grant
and attempts release in `finally`, including read cancellation. Cancellation during
open may leave an unknown server grant; no identity is invented, retry performed or
cleanup claimed. Repeated cancellation or transport failure can interrupt release.
Malformed mutation acknowledgments mean `SubmissionOutcomeUnknown`, not rollback.
Callers can instead use explicit `open`, `read`, `release` with their own lifecycle.

## Meaning and provenance

Windows are half-open integer row/column indices, not geographic bounding boxes.
States are explicitly server-listed finite declarations with labels, UTC instants,
source content hashes, epoch and exact shape/CRS/transform/units. The independent
fixture lists baseline and alternate declared states. They are not native temporal
history, committed graph revisions, provider science or authenticated provenance.
State/window association is validated; no reprojection, interpolation, resampling,
unit conversion or scientific execution occurs in the SDK. Fixture slicing stays
outside production packages.

The selected protocol explicitly wraps an existing `read-plans/1` manifest. Its exact
old-protocol immutable shard/release routes serve that selected grant. Existing full
`open_read_plan` semantics are unchanged: full baseline data remains a separate grant.
Source-state hash and selected-content hash have different meanings and are retained
separately. All results use
`DECLARED_FINITE_STATE_WINDOW_NOT_NATIVE_TIME_OR_COMMIT`.

Offline analysis validates complete immutable data and preserves centre coordinates,
mask, units, source identity, server-listed state label/instant, source-state revision,
selected window and local descriptor/selection hashes. Returned arrays are independent
copies; editing arrays does not edit prior data. Local hashes record declarations, not
native trust. Passing only `snapshot.data` to the older generic adapter intentionally
loses wrapper state/window metadata: use `analyze_selected_read_plan` to retain it.
The adapter needs the optional analysis dependencies and reserves8192bytes of its
explicit budget for selected metadata before delegating array allocation validation.

## Refusals and limits

Exact current root/Runtime/client/credential, source observation/state/epoch/window,
layout/masks/digests and configured-secret checks apply. Foreign owners refuse; saved
public identifiers do not confer grants. Expired/released/old grants do not replan.
Bounds:4096source/selected cells,128cells per shard,60second lease,16shared read grants,
65536byte manifests; too-large manifests refuse before allocating grants. The fixture
is ephemeral, not durable native retention. Independent contexts and unrelated user
resources must not be released to make space.

Native read-plan/window/time/provider compatibility remains JAVA-GAP-0006/0014 and
requires maintained real-service evidence. No scientific or final roadmap acceptance,
Java/IDE repair, public release or deployment authorization follows from this proposal.
