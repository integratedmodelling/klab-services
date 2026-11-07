# Experimental private draft revisions

**JAVA-GAP-0009 / SIMULATED_PROVISIONAL. Native contract UNKNOWN_PENDING_REFERENCE.**
This is explicit private draft custody, not accepted-source editing, a compiler,
semantic validation, project publication, a scientific revision or a Java API.
The proposed `klab.experimental.drafts/1` reference uses GET/PUT
`/experimental/drafts/v1/{opaque_name}` under an explicitly bound root context.
No simulator code is imported by the SDK and no real-service failure falls back.

```python
from klab_client import write_draft, read_draft, DraftConflict

first = write_draft(context, "model.kim", source,
    expected_revision=0, allow_experimental=True)
# Caller reconciles edits against the exact revision it read.
try:
    second = write_draft(context, "model.kim", edited_source,
        expected_revision=first.revision, allow_experimental=True)
except DraftConflict:
    latest = read_draft(context, "model.kim", allow_experimental=True)
    # Decide explicitly how to reconcile. No blind overwrite/replay.
```

`async_read_draft`/`async_write_draft` use native asynchronous transport. Source
bytes are bounded16KiB UTF-8, SHA256 checked and returned exactly with an immutable
`DraftRevision`. Every result says `UNVALIDATED_PRIVATE_DRAFT`: even invalid language
text can be retained; no source syntax or language semantics are inferred. Current
credentials, originating Runtime ID and exact root scope gate every request.
Atomic CAS admits only one writer for an expected revision; zero creates a new
name only. HTTP409 raises `DraftConflict`; HTTP5xx/disconnect during a write is an
unknown outcome, never automatically retried. An exact read is required before a
caller considers another submission. Publication and execution are not operations
of this reference. Existing native Resources authoring/project locks stay separate.

The simulator retains at most8names/context and8versions/name. Evicted revision
reads fail410, never substitute the latest. Opt-in owned SQLite preserves retained
history/revision fences on restart. Old checkpoints gain empty draft state, not
invented edits. Corrupt source/hash/noncontiguous history/foreign owner state fails
before live restore. Failed durable writes roll back in-memory changes and return
507 instead of acknowledging unsaved edits. Released contexts clean their drafts.
Finite capacity is not unlimited archival history or native publication acceptance.
Issue129 and the broader roadmap acceptance remain open; maintained native version,
conflict, permission, validation and publication contracts are still required.
