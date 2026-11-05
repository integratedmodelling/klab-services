# Indexed-read request semantics isolation

Base95b7db67f38e75b1dd537c2b25284a6ad7dfc546 (merged PR223). Python-only
RC-P2.2/CAP-08/09 reliability correction. Existing owned worktree reused.

Reproduced against installed ba7773abd: a caller-supplied native semantics dict
is reused by reference across per-offset requests and final decoding. Mutation
after the first request changes later requested units and the final reported unit.
Deterministic mocked transport recorded ['m','km'] for a single read and a final
km-labelled table containing234.5 and0.2345. This is a Python request/attribution
defect, not Java scientific evidence or a demonstrated Java conversion failure.
Exact receipt indexed-read-semantics-mutation-reproducer.json retained privately.

Smallest fix: copy the validated explicit semantics dict once in shared _read_spec,
then return/use that snapshot for all sync/async requests and final read metadata.
Native Semantics strings are unchanged; preserve opaque meaning/key/extensions.
No Python conversion, global locking, deep-freezing arbitrary caller extensions,
scientific mediation, Java code/dependency/authorization change, cross-fetch lease,
or observation/geometry immutable-snapshot guarantee. Existing default semantics
and typed ScanSlice behavior stay unchanged.

Tests must reproduce failure on old source, pass after the change, and cover sync/
nativeasync, originalunits/observable and request equality after caller mutation,
caller dictionary not rewritten by SDK, exact wire values/duplicates and default
semantics regression. Source-derived Java Semantics fields/DTO contract reused.
Installed gates and one actual owned Java read with a request-hook mutation provide
separate transport/scalar evidence without a new scientific fixture. Normal guide
review/PR/CI/eligiblemerge/ancestry/postmerge required. Continue queue after delivery.

## Red/green checkpoint

Before correction:8new mutation cases FAILED as expected on old source;2default/
nonmutation casesPASS. After the minimal shared snapshot correction:145focused
client/async/slice/mutation testsPASS. Additional live-fixture guard test and full
installed gates are running. Actual Java acceptance remains pending. Only the
private shared _read_spec return tuple changes; both facades consume the same
copied native semantics. No scientific request values or unit conversions changed.

Executablee2b768501: fullinstalled3,241tests/all17gates/lint/build/sourceparityPASS;
146focusedtestsPASS including the source-derived live fixture guard. Wheel
381ffdc0d202289a92a7cb2f032ba12cfd1d463e01cf02eacd26dd287ca5ab2a;
sdist157916913b4b3c5f0d375384b78794cfaef56a362760f88dff0bdc3022cee10b.
One fresh owned Java read-semantics-isolation-live run ACTIVE, not predeclaredPASS.
Current source-only/installed results do not establish original-study execution.

## Verified actual read

e2b76850179d3df9155c3df0878987145d392dbf actual Java PASS:20native-source point
requests retained exactly their initial m/observable/range/currency/dimensions
despite caller metadata mutation after the first serialized request. All20values
were independently expected234.5m; final requested observable/unit remained initial.
SDK left caller mutation intact. Owned context/session/services/listeners closed;
unrelated Java unchanged. Exact installed/source/raw harness/live identity, private
report hash and credential absence verified. No Java/algorithm/sourcefixture change.
Normal PR/final-head CI/eligiblemerge/postmerge pending. No remote revision lease,
immutable observation/geometry or arbitrary-extension snapshot claim. Historical
pending checkpoints above are superseded only for this bounded PASS.
