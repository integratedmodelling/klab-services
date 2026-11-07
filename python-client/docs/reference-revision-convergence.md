# Experimental reference checkpoint convergence

The separate `revision-convergence` simulator scenario exposes protocol
`klab.experimental.revision-snapshots/1` at Runtime
`/experimental/revision-snapshots/v1/checkpoint`. **This is not a maintained Java
endpoint, scientific commit or SDK fallback.** Ordinary sync/nativeasync transports
exercise it explicitly; no production SDK facade or simulation dependency is added.

POST requires exactly `protocol` and integer `expected_revision` (zero for an
absent head). Under the simulator State lock, one successful compare-and-swap
captures sorted IDs of currently completed synthetic observations in the owned
root, advances a reference counter and returns its predecessor. Conflict returns409:
explicitly reread/reconcile, never blindly retry mutation. Finishing a Job does not
automatically checkpoint it. An unfinished Job contributes no completed observation.
Checkpointing neither submits computation nor publishes source; empty IDs are valid.

GET requires the protocol query and optionally an exact positive revision. Only the
latest eight checkpoints per root are retained; absent/evicted revisions return410,
never substitution with the current head. The fixture caps snapshots at256IDs and
intake at4096bytes, refusing duplicate JSON fields. Exact originating Runtime and
owned root scope are required; foreign principals/focused scopes are denied.
No cross-principal sharing grants are inferred.

Returned kind is `SYNTHETIC_OBSERVATION_CHECKPOINT_NOT_NATIVE_COMMIT`;
`durable` and `complete_lineage` are false. Counters are not hashes, signatures,
permission grants, model/dataflow versions or scientific commit IDs. Checkpoints
include **IDs only**, not graph edges/values or multi-service atomicity. Existing
draftCAS/read-plan hashes remain separate contracts, not native scientific commits.

Two independent same-owner clients test stale local evidence, exact retained history,
explicit head refresh and owner-state preservation after reader closure. Concurrent
independent writers have exactly one CAS winner; nativeasync inspection reads the
exact winning reference revision. These finite properties do not prove general
eventual consistency, native isolation, notification convergence or federation.

The API explicitly refuses durable simulator storage: restart history is not
implemented/certified. Context/session release and authorized test reset discard
checkpoints. Other SQLite scenarios do not supply this scenario's durability.
Evidence: **SIMULATED_PROVISIONAL / JAVA-GAP-0016/0013**. Native committed revisions,
historical lineage and matching real execution remain unverified. RC-P3.1/CAP-10
stay PARTIAL; this is a bounded reference convergence component, not fullABC.
