# Finite managed behavior workflow (experimental)

`ManagedBehaviors` and native-awaited `AsyncManagedBehaviors` require an explicit owned
root Context, `allow_experimental=True` and exact Runtime advertisement
`managedBehaviorProtocol="klab.experimental.managed-behaviors/1"`. There is no fallback
to native execution or from a failed native actor service to a simulator.

```python
behaviors = ManagedBehaviors(context, allow_experimental=True)
admission = behaviors.validate(source)
created = behaviors.create(source, admission=admission)
saved = created.reference.to_wire()  # public identifiers, no client/credential/grant
current = behaviors.reconnect(saved)
running = behaviors.command(current.reference, "start", expected_revision=current.revision)
terminal = behaviors.read(current.reference)
disposed = behaviors.read(current.reference)  # independent explicit disposal step
assert disposed.private_session_disposed
behaviors.command(current.reference, "purge", expected_revision=disposed.revision)
```

## Precisely what is validated/executed

The independent `managed-behavior` fixture recognizes only two **exact literal source
recipes**, named `simulator.managed.finish` and `simulator.managed.fail`. Admission
checks exact UTF8 bytes/SHA256/recipe identity. It is not a parser, semantic validator,
compiler, interpreter or sandbox. Unknown/modified source refuses rather than executing.
Creating a recipe rechecks its source/hash and starts nothing; explicit CAS `start`
changes READY to RUNNING. An explicit read advances the deterministic synthetic recipe
to FINISHED/FAILED, with `DECLARED_SUCCESS`/`DECLARED_FAILURE`. `stop` changes READY or
RUNNING to STOPPING, and an explicit read confirms STOPPED/`DECLARED_STOP`.

All evidence labels `DECLARED_FINITE_SOURCE_STATE_MACHINE_NOT_KACTORS_EXECUTION`.
These outcomes are fixture declarations, not real k.Actors results, assertions, science
or operating-system isolation. Existing native `AgentExecutions` BEHAVIOR registration,
native broker controls, nonexecuting Authoring BEHAVIOR ADD and old fixture-only
`managed-agents/1` retain their distinct scopes. None is silently expanded or replaced.
The source uses illustrative script syntax; no real Xtext/compiler compatibility is
claimed by literal recipe admission. Actual native grammar/services remain authority.

## Private-session ownership and reconnect

Create allocates one separately tracked synthetic private session, owned by the agent,
not the caller's original session. Status and saved reference pin exact Runtime/root,
agent/epoch/source hash/recipe and private-session ID. Current read checks current server
principal and root; an explicit new same-owner client can reconnect to the saved public
identifiers. Saved identifiers confer no authorization or permission lease.

Terminal state alone is **not disposal evidence**. A separate explicit read removes the
owned private session and confirms disposal. Public workflows independently observe the
service's session count go from owner+private to owner only, while the original context
remains usable. Purge refuses until terminal disposal is confirmed, then410 on reads.
Owner-context release cleans only its own private rows. Changed ownership markers,
unexpected disappearance or independent contexts added under a private session refuse;
the coordinator does not delete unrelated sessions or orphan those contexts.

No instance restart/replay; stale CAS409 requires explicit reconcile, absent/purged/old
epoch410 requires a new explicit decision. History is ephemeral; durable mode refuses.
Bounds:4096source bytes,8192request bytes,65536response bytes,16retained agent rows and
the existing16session capacity. No automatic purge or owner-resource release to fit.

Configured secret material is refused without echo. Malformed/foreign status never
becomes success. Bad mutation acknowledgments or post-write identity changes raise
`SubmissionOutcomeUnknown`, not rollback. Cancellation may race with creation/start/
disposal; no automatic retry, orphan cleanup or successful-disposal claim follows from
cancellation/transport failure. Caller must explicitly inspect known identity and
resolve unknown creation through approved service diagnostics, not blind replay.

Native private-session creation, startup failure/stop/finish disposal and reconnectable
agent execution remain JAVA-GAP-0008 obligations requiring approved real evidence.
No Java/IDE repair, native script execution, public release or scientific acceptance is
authorized by this finite reference.
