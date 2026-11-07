# Staged review cycle pilot

Participant instructions: [Try the staged pilot](ONTOLOGY_REVIEW_PILOT.md).
New flows use `ontology-review-pilot@0.4`. Creation discovery lists only the latest version of
each workflow, and explicitly retired versions cannot be selected for a new flow. Existing
flows remain bound to their recorded definition. Unrelated workflow protocols retain their own
lifecycle.

## Authoritative lifecycle

The schema declares `bootstrap`, `peer-review`, `community-review`, `coalescing` and
`terminated`. Review commands create and close real `Flow.State` records and corresponding
review cycles/rounds in one conditional aggregate write. There is no independently editable
phase flag. Only bootstrap is an ordinary INIT target; generic transitions, direct stage
creation/deletion and reopening cannot bypass the staged protocol.

- Bootstrap is owned by the authenticated creator, regardless of caller-supplied owner,
  assignees or stage metadata. Only that creator with current editorial permission can see
  its content, including after handoff. Public visibility and administration do not bypass it.
  Bootstrap also writes no reference into the shared asset catalog; indexing begins at review handoff.
- Starting review closes bootstrap or coalescing and freezes its exact saved candidate. The
  initial audience defaults to REVIEWER, with COMMUNITY available through the command contract.
- Every round has a `stageId`. Peer/community rounds may overlap and repeat. Their stage and
  round status change together. Closing all rounds does not terminate the flow or its cycle.
- Entering coalescing closes every still-open round and cycle, atomically records closure
  notification/archive intents and creates an editor stage owned by the flow creator. The
  initiating editor/administrator remains recorded separately in provenance.
- Returning to review creates new cycle/round/stage records. Termination is allowed only from
  coalescing and creates a closed terminal record. Neither action means scientific acceptance,
  application or publication. Closed records never reopen or become editable. The catalog retains
  a historical REVIEWING summary alongside the explicit CLOSED flow status; that summary is not
  an acceptance or publication result.

## HTTP contract

`GET /api/v1/workflows/{workflowId}/creation` returns server-derived `canCreate`,
`initialStateIds` and `version`. This is a read-only UI hint; creation rechecks admission and
actual ontology/project access. The editing clients create with
`POST /api/v1/flows/initialize?workflowId=ontology-review-pilot`:

```json
{"initialState":{"schemaId":"bootstrap","assetUrn":"<authorized ontology URN>","assetType":"ONTOLOGY","title":"Ontology review","description":"Purpose and context"},"attachments":[]}
```

Each optional attachment contains `type`, `fileName`, `mediaType` and Base64 `content`.
The private state and all uploaded files persist atomically. Only staged-review bootstraps may
omit the initial transition; ordinary workflow initialization still requires one.
`POST /api/v1/flows` remains available for creating a state without initial uploads.

`PATCH /api/v1/flows/{flowId}/states/{stateId}` accepts
`{expectedRevision,title,description}` and returns the updated flow. The native client can also
POST the same body to the state `/details` endpoint. This narrow edit never round-trips protected
protocol metadata. Existing attachment POSTs accept optional `expectedRevision`; editing clients
supply it and refresh explicitly after conflicts.

`GET /api/v1/flows/{flowId}/review-cycles` returns revision, caller-filtered cycles, identity,
contributable round IDs, `phase`, `editableStageId`, stage descriptors and management
capabilities. A descriptor identifies its state schema, cycle, round, open status and caller
edit/contribution capability. The server is authoritative; hiding a button is not authorization.

`POST` to the same route takes `ReviewCycles.Command`, always with the exact
`expectedRevision`:

- `OPEN_CYCLE`: current bootstrap/coalescing `sourceStateId`, saved proposal attachment,
  optional ontology attachment and optional initial audience. It opens the initial round.
- `OPEN_ROUND`: cycle ID and audience, creating a further peer/community stage.
- `SAVE_CONTRIBUTION`: cycle/round IDs, optional owned draft ID, text, submit flag and optional
  alternative proposal. Submitted contributions cannot be rewritten.
- `CLOSE_ROUND`: cycle/round IDs. Closes the round and its stage, not the cycle or flow.
- `ENTER_COALESCING`: freezes open review and creates the next editorial stage.
- `TERMINATE`: closes coalescing and the flow without publication.

`CLOSE_CYCLE` remains for the older independent-cycle extension but is rejected for the staged
pilot: use `ENTER_COALESCING`. Unsupported phase, stale revision and closed-history writes are
conflicts, not an invitation to retry automatically.

`GET /api/v1/flows/{flowId}/review-cycles/{cycleId}/attachments/{attachmentId}` exposes only the
exact proposal or source of a caller-visible cycle, as plain Base64 text. It does not grant
access to its private bootstrap stage or unrelated materials. Review and creation-option
responses are `Cache-Control: private, no-store` and require an authenticated principal.

## Access, evidence and durability

Editorial management requires actual workflow permission plus ownership/assignment or
administration; bootstrap adds the strict creator condition. Peer rounds require a genuine
permitted REVIEWER; community requires a nonanonymous authenticated identity. Every private
contribution remains author-only, including for editors and public-flow projections. Generic
flow/stage/artifact reads enforce the same staged visibility before public shortcuts.

Candidate identity is derived from saved bytes, media types, checksums and the bundled proposal
schema. Schema validity is not semantic or scientific approval. Alternatives retain their own
identity and frozen round candidate. Changed bytes cannot reuse an already bound revision ID.
Referenced source stages and artifacts cannot be deleted.

All existing-flow mutations use the store's atomic expected-revision predicate. Competing
writers cannot silently overwrite one another. Nitrite/RocksDB has a single owning process;
this is not replicated multiwriter storage. Native close/reopen tests do not prove production
failover. Counts and UTF-8 payload bounds remain enforced.

Coalescing seed content is copied from an authorized frozen candidate into new attachments
when saved. It never mutates the prior candidate. Stage, cycle and round links survive reload;
historical editorial and review views remain read-only. Conflicting edits retain local text.

Closure retains the minimal digest and private outbox behavior described in
`klab.core.services/REVIEW_ARCHIVE_PILOT.md`. A dirty/locked/unavailable project defers the local
Git digest without undoing closure. No remote push occurs. Email delivery is still disabled;
a persisted notification intent is not an editor inbox delivery.

Real IdP login, real-account role/revocation propagation and production deployment remain
separate acceptance gates. Automated HTTP/native/browser fixtures must not be presented as
live authentication evidence. See the operator worksheet and staged-review verification note.
