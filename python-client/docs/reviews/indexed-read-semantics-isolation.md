# Contribution review: indexed-read semantics isolation

## Recommendation

Ready for maintainer review for the bounded snapshot correction. Author/CLI review under the contributor guide,
not human approval. A reproduced Python aliasing defect can change later cell
requests and final semantic labels within one indexed read. Copying the validated
native request definitions once is the smallest correction; no Java repair needed.

## Reviewed state

Integration95b7db67f(PR223), branch fix/python-indexed-read-semantics-isolation;
contract710d455f4 before code. Reused owned worktree; failed peer/categorical work
and original research evidence retained. Includes shared sync/async read preparation,
focused tests and one owned actual-read acceptance harness; no unrelated changes.

## Capability and boundaries

One fetch_data call retains its initial validated explicit semantics for every
offset and returned metadata, even if caller code mutates or clears its own dict
while a request runs. Native string fields are copied verbatim; SDK does not undo
caller mutations or convert values. Default None behavior unchanged. This does
not freeze mutable observations/geometry, arbitrary nested extension objects,
remote revisions or cross-fetch state. No new public API/Java/dependency/permissions.

## Findings, ordered by consequence

Important defect reproduced: client.py Context.fetch_data/_read_spec and
async_client.py AsyncContext.fetch_data retained caller dict references. A request
hook changed m→km after the first serialized point; next point requested km and
final table labelled km although the first value was requested in m. Deterministic
mocked transport is request/attribution evidence, not Java scientific conversion.
Eight regression cases failed on unchanged source (unit/observable/all-fields/clear,
sync/nativeasync); two default/nonmutation cases passed. Shared preparation now
copies validated explicit definitions and both facades use that returned snapshot.
No other important/blocking defect demonstrated in the bounded correction.

## Validation actually performed

| Check | Revision/environment | Status | Establishes / limits |
|---|---|---|---|
| Deterministic mutation reproducer | installed ba7773abd, Windows Python3.12.7 | Failed as expected | Per-cell units m,km and incorrect final label; no real network |
| New regression tests before correction | source95b7db67f | Failed:8; passed:2 | Demonstrates actual old-path aliasing, not a hypothetical concern |
| Mutation/client/async/slice tests after correction | changed Python sources | Passed:145 before additional fixture guard test | Snapshot semantics, defaults, scope/read regression; mocked transport not science |
| Full installed gates/focused checks | e2b768501 | Passed:3,241/all17gates;146focused | Exact wheel build/install/parity/lint; fixture guard included; no live claim |
| Owned Java native-semantics read | e2b768501 fresh fixture | Passed |20native-source pointrequests unchanged despite caller mutation;20values234.5m/final unit/observable retained; owned cleanup |

## Scientific and lifecycle evidence

Native Java StorageScan.Semantics/ValueMediation.target remain authoritative.
Actual fixture must expose ordinary m unit with no contextual dimensions, range
or currency. Same-source request uses exact served observable URN and native
empty-map dimensions serialization. A request hook mutates only caller-owned dict;
no changed model, synthetic unit conversion oracle or Python recalculation.
Scope/session/service/listener cleanup confirmed; unrelated Java unchanged. Exact
installed/source/raw harness/live identity, report hash and credential absence
verified in read-semantics-isolation-final-validation.json. No important/blocking
defect demonstrated after correction within this scope; not an exhaustive proof.

## Contributor coordination

No Java/component repair, publication, original-study execution or peer fixture
retry. Import-schema TODO/null contract is separately recorded, not expanded here.

## Smallest next actions

Final focused map/documentation checks, then normal exact-head PR/CI/eligible
merge/ancestry/source/postmerge confirmation. No further scientific rerun or Java
repair is needed for the unchanged executable. Maintainer acceptance is separate
from this contributor recommendation and local/installed/live evidence.

## Suggested PR description

Prevent caller mutation from changing semantics mid-read or corrupting requested
unit/observable attribution. Share the checked snapshot through sync/async paths;
document remote revision and arbitrary-object snapshot limits. Include red/green,
installed and bounded actual Java evidence, not a scientific reproduction claim.
