# Copied snapshot composition reference — not native live federation

The separate simulator scenario `copied-composition` exposes explicit protocol
`klab.experimental.copied-composition/1` at Runtime
`/experimental/copied-composition/v1/`. Existing ordinary SDK transports exercise
it; there is no production SDK federation facade, native route claim or simulator
fallback. The operation is **copied-data composition**, not live twin attachment,
migration, failover, collaborator grants or scientific model execution.

The submitting client independently reads two source observations through the
opt-in reference read-plan API and its current authorized connections. Complete
shards are assembled and each source plan is explicitly released. An origin contains
Runtime URL/ID, root context and observation ID; duplicate numeric IDs on different
Runtimes remain distinct. Each pin also retains the plan ID, epoch, content revision
and `REFERENCE_CONTENT_SHA256_NOT_NATIVE_COMMIT` revision kind. These are reference
snapshot identities, not signed upstream receipts or native scientific commits.

POST `create` requires protocol, operation `PAIRWISE_SUM_REFERENCE`, exactly two
source snapshots and an exact expected source vector. Content hashes, values/masks
and descriptors are validated before C publishes anything. Different units, shapes,
CRS, transforms, data type or order refuse rather than resampling/converting. Values
are added only where both explicit masks are valid, otherwise output is null/invalid.
Nonfinite/overflow output refuses. No model applicability or scientific meaning of
adding two fields is inferred; this is declared synthetic arithmetic.

The returned result is owned by the authenticated coordinator C root and qualified
by C's Runtime/context/result ID. It retains both source pins, output descriptor,
mask, values and content hash. GET `<result_id>` returns the stored copy; DELETE
`<result_id>` releases **only C's copy**, never source observations or contexts.
Closing an independent reader also leaves C's owner state intact. C-context/session
release or authorized simulator reset removes its results. At most eight results
per root,4096cells/source and262144request bytes are accepted. Source values are
bounded finite numbers; duplicate JSON keys/unknown fields/unsupported operations
and vector mismatches refuse. Expected-vector mismatch is409, not permission for
automatic refresh or replay. Ambiguous submissions require inspection, not retries.

## Authority and lifecycle limits

C performs **no remote fetch and no source authorization check**. Origin claims
arrive with copied input and are explicitly labelled
`CALLER_ASSERTED_COPIED_REFERENCE_NOT_REMOTE_GRANT`. Input hashes validate copied
content, not who supplied it or whether a claimed upstream origin is authentic.
The HTTP workflow independently proves successful/denied source access by the
submitting SDK client; C does not infer grants or forward source credentials.
An arbitrary supplied URL is never requested by the coordinator.

Once copied, C's result is not a lease on A/B: revocation, source mutation, deletion
or outage does not propagate. It is not an atomic multi-service snapshot, live
source revalidation or native federated security policy. To compose a different
revision, capture and pin it explicitly. Restart retention is not provided; the
API refuses when durable simulator storage is configured. No transaction, rollback
or cleanup promise for native graph state follows from this finite fixture.

The three-owned-process workflow uses independent A/B/C Runtime identities,
colliding numeric observation IDs, actual source read-plan HTTP delivery, C-side
composition, independent nativeasync C inspection and C-only detach. A/B counts and
source revisions remain unchanged and each has exactly one original submission;
C performs no Runtime observation submission. Thus it is more than a client union
or standby example, but still **not native composed-twin acceptance**.

Evidence: **SIMULATED_PROVISIONAL / JAVA-GAP-0007/0013**. Native federation/API,
source authenticity, delegated authorization, revocation propagation, live pin
validation, durable history, semantic/scientific compatibility and independently
owned real-runtime cleanup remain open. CAP-03 and full A+B+C stay PARTIAL.
