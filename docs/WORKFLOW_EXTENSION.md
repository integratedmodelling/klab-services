# Workflow behavior bridge: upstream decisions

Temporary decision list for the initial checkpointed k.Actors integration. Implemented contracts
and examples are in [WORKFLOWS.md](WORKFLOWS.md#kactors-instrumentation).

1. **Durable owner identity.** Today the owner must reconnect after restart. Decide whether a
   persisted, revocable delegated execution identity may run with the owner's services while they
   are offline, how its grants are renewed, and how permission changes stop existing flows.
2. **Behavior revisions and migration.** Same-version source updates restore existing globals;
   a changed version or delegate layout blocks execution. Define pinned source fingerprints,
   migration actions and administrator approval/rollback. Include transitive import/trait revisions,
   state schemas, and independently versioned checkpoint serialization.
3. **Suppliers, emitters and durable continuations.** The initial profile accepts functions and
   suppliers awaited within one operation.
   Decide how pending calls, timers, listeners, retries, cancellation, deadlines and remote replies
   are journaled and replayed before admitting actions that remain pending across restarts.
   A thread timeout alone cannot
   undo external effects or guarantee an action stopped.
4. **Reliable notifications and other effects.** Choose an outbox contract committed with the flow,
   worker leases, per-action idempotency keys, effect status inspection and retry policy. SMTP send
   success followed by process failure is currently an at-least-once retry risk. Transition revision
   checks prevent stale mutations, but do not provide exactly-once effects.
5. **Global object graphs.** Core document/project handles now have a versioned coordinate codec
   and resolve with the current participant on use. The portable subset deliberately excludes service/client objects,
   arbitrary DTOs and actor handles. Decide versioned codecs for quantities, instants, constants,
   resources, child agents and digital-twin-backed agent state. Inherited snapshots verify delegate
   order and generated behavior class identity; stable persistent
   identity keys would support controlled inheritance changes.
   Define workflow-context binding for inherited initializers; the initial bridge injects parameters
   into the top-level initializer and invokes inherited initializers without arguments.
6. **Editor/flow agent capabilities.** The first editor agent is a read-only saved-stage facade;
   a separate content agent changes titles, descriptions, metadata and attachments from text,
   bytes, permitted server files or HTTP(S) URLs. Behaviors also receive the
   actual working Java beans. Extend the content agent with validation findings and structured
   document access, then constrain which
   fields instrumentation can change. Decide whether any trusted action may inspect unsaved buffers.
7. **Forms and discovery.** Add annotation-driven labels, descriptions, defaults, required/null rules,
   enum/asset/agent pickers, collections, secrets, validation messages, and a web form provider.
   Initial IDE forms support text, booleans and numeric Java parameters. Consider per-button role
   restrictions beyond the existing stage contributor/editor rule and client feature negotiation.
8. **Action results and audit.** Return values are currently ignored and rejection uses exceptions.
   Choose structured validation results and a durable button-execution log distinct from transition
   history, including triggering identity, action revision, inputs with secret redaction, outcomes,
   durations and effect receipts.
9. **Execution capacity.** Workflow mutations share the manager's existing synchronization boundary.
   Compilation currently occurs per instrumented operation. Plan scoped dependency-aware compiled
   class caching, bounded executors, per-flow serialization, multi-process compare-and-swap storage,
   and resource/time budgets before high-volume or untrusted instrumentation is enabled.
10. **Lifecycle policy.** Administrative reopen and ordinary field/attachment edits do not rerun hooks.
    Decide explicit resume/reopen/delete hooks and whether direct stage closure should be prohibited
    for instrumented flows. Terminal stages still run their configured start actions.
11. **Attachment source and storage policy.** Decide authenticated URL adapters, source provenance,
    MIME discovery, durable transfer retries and server-file staging lifetimes. Current URL inputs
    require explicit media types and never forward credentials or follow redirects. Establish
    deployment egress restrictions and an orphan-blob collector or transactional blob journal for
    process crashes between payload writes and aggregate persistence.
