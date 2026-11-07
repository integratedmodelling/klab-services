# Finite execution-dependency explanations

`ExecutionExplanations(context, allow_experimental=True).inspect(observation)` and
awaited `AsyncExecutionExplanations` read a separate proposed Runtime protocol
`klab.experimental.execution-explanations/1`. Exact advertisement through
`executionExplanationProtocol` is required. No native fallback or write is performed.

The complete finite reference receipt joins:
- supplied input URNs/provider IDs/concrete versions/source-manifest hashes and optional
  binary hashes (binary bytes and manifest text are **different hash domains**);
- selected model URN/concrete version/source hash and component ID/concrete version/
  provider/prototype, with explicit references to supplied input URNs;
- separate planned effects and recorded effects, pinned to the exact observation and
  selection. A plan is never proof of execution; recorded outcomes may explicitly fail;
- successful recorded semantic-update associations with ordered contiguous before/after
  reference revisions and observable labels. Every successful semantic effect needs its
  exact revision record; the last semantic revision must match the receipt head.

All groups must be present and bounded. Empty/missing input/selection/plan/recorded
evidence refuses this completed-execution reference. Duplicate IDs, missing component
or source/version pins, wildcard versions, dangling links, wrong target/root/Runtime,
bad canonical digest, unsupported fields and configured credentials refuse the whole
receipt. Bounds:128entries/group,1MiB response/canonical document, finite JSON and
concrete three-part version identifiers (optional build/prerelease suffixes).

The immutable receipt pins exact root/Runtime/observation/epoch/revision and SHA256 of
its complete canonical reference document. Bindings/credentials/Runtime identity are
checked around reads. Digest and version stamps remain **server claims**, not
cryptographic provenance, an atomic distributed snapshot, permission lease or native
scientific lineage. Reader closure releases no owner resources. Historical receipts
never imply current authorization; each remote read rechecks current server authority.

## Independent end-to-end reference

The `execution-explanation` fixture extends the separately admitted authentic crop
import/source binding: actual public upload and explicit namespace ADD, completed
Resolver dataflow, observation and InputProvenance association all precede explanation.
Source/model pins derive from owned imported/source state and must match completed
plan requirements. Component/provider/version/prototype stamps are explicit provisional
`referenceComponentSelection` in that selected plan, not inferred from inventory.
Completion without an explicit matching finished Resolver plan records no explanation.

Public sync/async workflows additionally check same-owner second-client reads,
foreign-principal/root refusal, immutable prior receipts, restored current observation
metadata, all400recorded crop cells and owned Runtime/reference cleanup. Persistent
source ADD assets are reset only through authenticated fixture control—not fabricated
native SDK RESOURCE deletion.

The authenticated test-only `executionSemanticUpdate` declares **one finite semantic
label association**, recorded both in explanation history and observation metadata.
It is not a native semantic update API. The native-shaped observation's original
observable and recorded numeric values stay unchanged; changing a label from metres
to centimetres **does not perform numeric/unit mediation**. Planned/recorded reference
effects explain the declared association only, never a newly executed scientific result.

The label `COMPLETE_FINITE_REFERENCE_NOT_NATIVE_SCIENTIFIC_LINEAGE` means complete
only for these declared inputs/selections/effects. Existing partial provenance pages,
source-bound graphs, native cached CommitInspector reports and finite graph checkpoint
reconciliation retain separate meanings and are not silently upgraded by this receipt.
Native component history (`ComponentHistory` via generic service info) exists but its
availability is not proof of per-observation component selection or scientific execution.
Maintained native source/component/semantic-effect lineage contracts and approved real
science/two-client evidence remain JAVA-GAP-0016 obligations. No Java/IDE repair,
native mutation, release promotion or scientific acceptance is authorized.
