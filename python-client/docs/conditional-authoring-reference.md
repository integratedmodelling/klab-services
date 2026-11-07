# Conditional source authoring reference

`AcceptedDocuments` and `AsyncAcceptedDocuments` provide a **proposed finite
reference**, not an established native source-publication adapter. Require
`allow_experimental=True`, a checked ordinary Runtime root context/client, and
Resources advertising `acceptedDocumentProtocol="klab.experimental.accepted-documents/1"`
before any reference route. No advertisement/failure means refusal, never fallback.
Existing ADD-only `Authoring` and private `RevisionDrafts` are unchanged.

```python
docs = AcceptedDocuments(context, allow_experimental=True)
knowledge = docs.knowledge()
draft = docs.update("synthetic.document0", source, expected_revision=0)
checked = docs.validate(draft.name, expected_revision=draft.revision,
                        expected_knowledge_revision=knowledge.revision)
published = docs.publish(draft.name, expected_revision=draft.revision,
                         expected_knowledge_revision=knowledge.revision)
snapshot = docs.published(draft.name)
```

Await corresponding async methods directly. Mutations never retry automatically.
`expected_revision=0` means conditional creation only; a stale editor cannot silently
overwrite a current revision. Updating source creates a new revision and invalidates
its prior validation. Validation is independently server-owned and source/knowledge
bound. An INVALID diagnostic report cannot publish. Publication atomically requires
the current document revision, successful exact-source validation and unchanged
knowledge service/worldview/revision/session/content. Merely sending the new current
knowledge integer cannot authorize publication using an older validation.

`DocumentRevision`, `AuthoringKnowledge` and `DocumentPublication` are immutable
server-reported reference receipts, **not cryptographic or local authority**.
Publication here means a separately readable reference snapshot, not a native
compiler, saved ontology, accepted scientific model or real Resources catalog entry.
Python `VALID` reports alone never authorize server publication. The facade does not
silently call unconditional native UPDATE or treat resource-info MARK_PUBLISHED as
namespace admission. Native CAS/validation/publication/retention remain external
contract gates under JAVA-GAP-0009.
The inspected native ResourcesProvider explicitly rejects PUBLISH for assets other
than Resource; its deferred namespace save/workspace validation branches are not
this conditional source-publication protocol. The reference never translates a
namespace publication request into those native operations.

## Revision, readback and recovery

`read(name)` returns current editable state; `read(name, revision=N)` reads retained
history. `published(name)` reads the last reference publication independently.
Editing source does not mutate that prior published snapshot. Publishing a source
revision twice refuses with409; after a lost publication response inspect the
published snapshot instead of replaying. Likewise reread current source after a lost
update response. Such inspection is current server state, not a lease, idempotency
proof or attribution to a particular concurrent caller's request.

Knowledge changes invalidate old validation for future publication. Explicitly fetch
current knowledge and revalidate source; no hidden synchronization/reload/repair or
auto-publish occurs. Old receipts remain historical, not assurances of currentness.
Known post-mutation binding changes and disconnects produce unknown outcome; malformed
confirmations also require reconciliation rather than assuming nonexecution.

## Independent bounded policy

The separate `accepted-documents` fixture admits eight identities
`synthetic.document0`..`synthetic.document7`. Each valid source is exactly
`namespace <name>;\n`; other bounded source may be edited but returns declared INVALID
diagnostics, never inferred semantics. This is a literal finite validator, not a
language implementation. Root ownership/current credentials and originating Runtime
are checked by the service, not from `context.owned` or a supplied DTO.

Source is limited to16KiB, history to eight revisions/name, storage to256root/name
pairs across the isolated fixture. Evicted history returns410; conflicts409 and
budget exhaustion429 remain distinct. Root owner cleanup removes reference state.
Durable-storage mode refuses: saved Runtime context state does not imply durable
source/validation/publication authority after restart. There is no automatic native
source deletion or public release.

Public sync/nativeasync HTTP workflows cover publication/readback, stale editor,
invalid source, source/knowledge invalidation, owner/root denial, lost-reply lookup
without replay and owner cleanup. Supplementary fixture controls are separate from
those public workflows. Configured credentials in public route/source/response fields
refuse; current Resources/root bindings are checked before and after calls.

No native management test is fabricated for the unestablished contract. Maintainer
confirmation and explicitly approved disposable fixtures are needed before such
calls. Existing native ADD/parser/source-diagnostic tests do not prove conditional
publication. No native source mutation, ontology repair, public package release or
scientific computation was performed here.
