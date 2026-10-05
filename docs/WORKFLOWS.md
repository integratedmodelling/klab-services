# k.LAB resource workflows

## Purpose and scope

The Resources service owns asynchronous workflows used to coordinate work on k.LAB assets. A
workflow definition is configuration; a flow is one durable execution of that definition. Both
are ordinary API beans, so a client can render tasks, inspect history, and pre-validate actions
without maintaining a second workflow engine. The server remains authoritative for authorization,
concurrency, persistence, and attachments.

The first bundled definition is `asset-review` (currently schema version `1.1`), which supports editing, peer review, optional
public community review, acceptance, rejection, and requests for changes. Definitions live under
`klab.services.resources/src/main/resources/workflows`; `index.txt` lists definitions imported at
service startup.

## Domain model

### Workflow

`org.integratedmodelling.klab.api.services.resources.workflow.Workflow` is a versioned schema. It
contains keyed state and transition schemas plus provenance metadata. Its top-level `assetTypes`
set lists the `KlabAsset.KnowledgeClass` values on which a new flow may be opened. An empty set is
unrestricted for compatibility with existing workflow definitions. This whole-workflow constraint
is checked before the initial state's more specific asset constraint.

A state schema defines:

- a stable ID, description, completion criteria, and participant instructions;
- manager and contributor roles;
- an optional group allow-list, where `PUBLIC` has the special meaning described below;
- admitted attachment rules, each with a logical type, media type, optional
  `KlabAsset.KnowledgeClass`, arity (`-1` means unlimited), and `required` flag. `required`
  defaults to `false`; declaring an attachment rule never makes it mandatory by itself;
- the k.LAB asset classes managed in that state;
- whether the state is open or terminal;
- arbitrary metadata that is copied through the schema and can carry provenance vocabulary.

A transition schema defines:

- a stable event ID and description;
- one or more source state-schema IDs, or the reserved source `INIT`;
- one target state-schema ID;
- roles permitted to perform the transition;
- optional admitted source attachment asset classes and media types;
- arbitrary provenance metadata.

`Workflow.validate()` performs structural validation. `admittedTransitions(...)` and
`validateTransition(...)` execute entirely on client data. These are advisory at client side: the
Resources service always repeats validation using its authoritative schema and flow revision.

### Flow

`org.integratedmodelling.klab.api.services.resources.workflow.Flow` is a persistent aggregate. It
records its workflow ID and exact version, the target asset URN and knowledge class, owner, status,
optimistic revision, timestamps, metadata, all states, current-state IDs, a `publicRead` flag, and
append-only transaction history. Flow creation resolves the target first and rejects a missing
asset or an asset class not admitted by the workflow or by the initial state schema.

Each flow state has a unique instance ID and points to a state-schema ID. It contains its mutable
`owner` identity, title, description, open/closed status, optional explicit assignees, attachment
descriptors, metadata, and timestamps. Attachment bytes are deliberately excluded, keeping routine
flow retrieval cheap.

Each transaction records the transition event, source and target state IDs, actor identity,
timestamp, and request metadata. The initial transaction has no source state and records the
authorized schema transition whose source is `INIT`.

A flow is closed automatically when no open current states remain. Closed flows remain queryable.
The editor who created a flow may explicitly delete the complete aggregate while they still hold
the `EDITOR` role and workflow permission; `ADMIN` may do the same. This is a deliberate,
confirmation-gated exception to the normal append-only lifecycle.

### Caller-specific projections

The database holds the full aggregate. A response is projected for the requesting `UserScope`:

- a `publicRead` flow is returned in full to every identified caller, but remains read-only;
- administrators and the flow owner receive all states for private flows;
- other participants receive only states whose role and group rules admit them;
- `currentStateIds` contains only visible tasks assigned to the caller, or unassigned visible
  tasks;
- history entries involving hidden states are omitted.

Consequently, two users can legitimately receive different JSON for the same flow ID. Clients
must not infer that a missing state or history entry does not exist.

## Participants and authorization

`WorkflowParticipant` is the small serializable projection derived from `UserScope`. Identity is
always recorded. Roles are `ADMIN`, `REVIEWER`, and `EDITOR`.

Group custom properties configure workflow authorization:

| Property | Value | Effect |
| --- | --- | --- |
| `workflow.roles` | Comma-separated roles | Adds roles supplied by the group. |
| `workflow.permitted` | Comma-separated workflow names | Allows the listed workflow schemas. `*` allows every workflow. |
| `workflow.disallowedTransitions` | Comma-separated transition IDs | Denies those events even if a role permits them. |
| `workflow.maxResponseHours` | Positive integer | Denies responses after that many hours from state creation. The smallest value from all groups wins. |

If no group supplies a workflow role, an authenticated, non-anonymous user may receive the
`REVIEWER` role only when all of the following hold:

- the identity has a nonblank email;
- identity data contains `workflow.knownRealPerson=true`;
- the target state admits `REVIEWER` and its group list contains `PUBLIC` (or has no group filter).

The flag is the authentication system's assertion that identity and email verification have
already happened. The workflow layer does not attempt to verify email itself.

For non-administrators, `workflow.permitted` is an allow-list and an absent or empty value admits no
non-public workflow. Entries from all of the user's groups are combined. A workflow may be named by
its stable ID (recommended, for example `asset-review`), `id@version`, full workflow URN, or its
human-readable name. `*` grants access to all workflow types. Administrators always have this
wildcard access; for non-administrators it does not grant a role or bypass stage/group rules.
Public-review stages remain available to known-real-person reviewers, and a
`publicRead` flow remains browsable, even when its workflow is not in the allow-list; neither
exception permits starting that workflow or editing a non-public stage.

An empty admitted-group set means that groups add no further state restriction after workflow and
role checking.
`PUBLIC` is not a normal group ID: it admits only the known-real-person reviewer case. Any other
listed value must match a group ID. `ADMIN` bypasses role and group checks, but still operates
through an identified `UserScope` so provenance is complete.

Authorization, including the workflow allow-list, is checked again for every schema read, flow
creation, mutation, transition, upload, and download. A client-side projection is never trusted as
authorization evidence. Clients also apply the allow-list when constructing menus and action bars
so forbidden workflows are not offered optimistically.

## Lifecycle and invariants

1. A client retrieves a workflow schema and chooses a state reached by an authorized `INIT`
   transition. Interactive clients keep this first stage provisional while it is edited.
2. `initializeFlow` validates the initial state, attachment payloads, and first outgoing transition
   in memory. Only after every check succeeds are the payloads, INIT entry, first transition, and
   resulting aggregate persisted. A validation error leaves no Flow or attachment record.
   `createFlow` remains the lower-level operation for trusted integrations that intentionally need
   a persistent initial task before its first transition.
3. State CRUD may maintain task metadata. A state's schema and attachment descriptors cannot be
   changed through update. Current or historically referenced states cannot be deleted.
4. Attachment upload validates type, media type, asset class, and arity before storing bytes.
   Completion requires only rules explicitly marked `required: true`.
5. A transition must originate at a current state, match the source schema, pass role/group/group
   constraint checks, and satisfy required attachment inputs.
6. The source state closes, the target state is created, history is appended, and the aggregate
   revision increments.
7. A target whose schema is closed is terminal. If it leaves no current states, the flow closes.

Transition requests can carry `expectedRevision`. `-1` disables the check; otherwise a mismatch is
rejected. Clients should normally send the revision they rendered, refresh after a conflict, and
ask the user to confirm the action again. Service mutation methods are synchronized so the
validate/write sequence is atomic within one Resources-service process.

The aggregate supports several current states, which permits branching and independent tasks.
The initial implementation commits one source-to-one-target transition at a time. Manager-created
states support construction of branches; join/consensus transitions are intentionally left for a
later schema extension.

### Service stage-lifecycle extension

`WorkflowManager` accepts a `WorkflowStageLifecycleHandler` as an optional service extension. The
workflow API currently represents a stage instance as `Flow.State`; the handler uses stage
terminology to distinguish the lifecycle task from the state-schema definition. The one-argument
`WorkflowManager(WorkflowStore)` constructor installs a no-op handler, preserving the default
behavior. A service that needs lifecycle actions supplies the handler through the two-argument
constructor:

```java
var manager =
    new WorkflowManager(
        workflowStore,
        new WorkflowStageLifecycleHandler() {
          @Override
          public void afterStageCreated(Context context) throws Exception {
            // Perform the service-specific post-creation action.
          }

          @Override
          public void beforeStageDeleted(Context context) throws Exception {
            // Perform the service-specific cleanup or deletion guard.
          }
        });
```

The callback contract is:

- `afterStageCreated` runs after the flow aggregate containing the stage has been persisted and its
  `ResourceInfo` catalog projection has been synchronized. It covers the initial stage created by
  `createFlow`, a manager-created stage from `createState`, and the target stage created by a
  transition.
- A post-creation exception is caught and logged. Creation remains committed: reporting callback
  failure as a rollback would leave the caller with a false view of durable state.
- `beforeStageDeleted` runs after authorization, editability, and reference checks succeed, but
  before any attachment payload or stage record is removed. An exception is wrapped as a
  `KlabIllegalStateException`, aborting deletion and preserving both the stage and its attachments.
- Closing a source stage during a transition is not stage deletion and does not invoke
  `beforeStageDeleted`. Stage update, close, and reopen operations do not currently have lifecycle
  hooks.

Callbacks run synchronously within the manager's synchronized mutation operation. Their `Context`
contains snapshots of the flow, stage, workflow, and stage schema plus the initiating `UserScope`.
Handlers must treat API objects in the context as read-only and use service APIs for mutations;
they should also keep synchronous work bounded so unrelated workflow mutations are not delayed.
The stage snapshot lets a deletion handler inspect attachment descriptors while the corresponding
payloads are still present in `WorkflowStore`.

Initially, the Resources service may implement the interface with hard-coded Java actions. The
interface deliberately carries no dependency on the action mechanism, so the same handler can
later resolve and invoke k.Actors behavior loaded through the service. A future k.Actors adapter
must preserve the lifecycle boundary: a deletion guard must complete before deletion can proceed,
whereas post-creation work may be dispatched asynchronously only when its effects are not required
for the creation response. Callback failure should remain observable through service logging and,
for pre-deletion behavior, through the failed mutation result.

## YAML configuration

The schema is intentionally data rather than Java code. A minimal definition is:

```yaml
id: example-review
version: "1.0"
name: Example review
assetTypes: [RESOURCE]
states:
  draft:
    description: Prepare the asset.
    completionCriteria: A candidate is attached.
    instructions: Attach one candidate.
    managerRoles: [ADMIN, EDITOR]
    contributorRoles: [EDITOR]
    attachments:
      - type: candidate
        mediaType: application/*
        arity: 1
        required: true
    assetTypes: [RESOURCE]
    open: true
  published:
    description: Published.
    completionCriteria: Terminal state.
    managerRoles: [ADMIN]
    contributorRoles: [ADMIN]
    assetTypes: [RESOURCE]
    open: false
transitions:
  initialize:
    sourceStates: [INIT]
    targetState: draft
    roles: [ADMIN, EDITOR]
  publish:
    sourceStates: [draft]
    targetState: published
    roles: [ADMIN, EDITOR]
```

Media rules accept exact types, a family wildcard such as `text/*`, or `*/*`. State and transition
maps are keyed by stable IDs. An omitted nested `id` is populated from its map key during schema
validation; a conflicting nested ID is an error.

On startup, every indexed classpath YAML file is parsed and validated. Definitions are stored as
`workflowId@version`; importing a new version never destroys one referenced by an existing flow.
Retrieval by workflow ID returns the highest semantic version, while reconstruction retrieves the
exact pinned version.

## Persistence

`WorkflowStore` is the storage port. The included implementation is `ResourcesKBox`, using the
Nitrite 4 dependency already present in `klab.core.services` and its RocksDB module. It uses the
same durable `resources.db` database as other Resources-service data and adds these repositories:

| Nitrite repository | Key and indexes | Contents |
| --- | --- | --- |
| `workflows` | unique `storageId` (`id@version`) | Immutable-by-version workflow definitions. |
| `workflowFlows` | unique `id`; indexes on `workflowId` and `status` | Complete active and closed flow aggregates. |
| `workflowAttachments` | unique `id`; indexes on `flowId` and `stateId` | Opaque attachment byte arrays. |

Flow and schema beans remain database-neutral. A Mongo implementation only needs to implement
`WorkflowStore`; authorization, lifecycle, REST, and client code do not depend on Nitrite. For a
distributed implementation, `putFlow` must use an atomic compare-and-set on `revision` rather than
the process-level lock used by the embedded implementation.

Attachment descriptors include byte length, SHA-256 checksum, actor, and timestamp. Payloads can
later move to filesystem or object storage behind `WorkflowStore` without changing a `Flow`.

### ResourceInfo catalog and asset ownership

`ResourceInfo` remains primarily the status and permission record for first-class Resources-service
assets: components, projects, workspaces, and resources. Those records are created as part of the
normal asset lifecycle and own their permissions. Documents and other items contained by a project
(for example namespaces, ontologies, models, and statements) continue to inherit permissions from
their containing project; opening a flow does not turn them into independent permission owners.

Flows may nevertheless target either first- or second-class assets. When the first flow is opened
on an asset for which no status record exists, `ResourcesKBox` creates a minimal `ResourceInfo`
record on demand. This makes review/update status addressable at the individual asset URN without
changing the normal automatic catalog policy.

An on-demand second-class record stores `permissionsOwnerUrn`, normally the containing project.
Authorization and `getRights` dereference that first-class record, while `setRights` refuses to
write through the child record. This prevents the catalog entry from becoming a separate
permissions boundary.

`ResourceInfo.flows` is a map keyed by permanent flow URN. Each `FlowReference` records the workflow
URN, active/closed status, current flow-state URNs, review stage/status, and last-update timestamp.
The map is one-to-many: opening or changing one flow only replaces its own entry, and closed flow
references are retained. Thus multiple simultaneous or historical reviews of the same asset are
fully distinguishable. The top-level `ResourceInfo.stage` and `reviewStatus` mirror the most
recently updated flow as a cheap summary; consumers needing an individual review result must read
the corresponding map entry.

Every successful flow creation or mutation synchronizes this catalog in the same Resources-service
operation. Active states default to `REVIEWING` with review status `1`. A state schema may define
`metadata.resourceStage` and `metadata.reviewStatus` to publish a different result; the bundled
review workflow uses `REVIEWED`/`2` for acceptance and `REJECTED`/`-1` for rejection.

### Knowledge classes and URNs

Workflow objects are ordinary transmissible `KlabAsset` values. The following
`KlabAsset.KnowledgeClass` values are defined and are handled by the Resources service's generic
`retrieve`, `list`, `resolve`, `submit`, and (where safe) `delete` machinery:

| Knowledge class | Java asset | Permanent URN |
| --- | --- | --- |
| `WORKFLOW` | `Workflow` | `urn:klab:workflow:{workflowId}@{version}` |
| `WORKFLOW_STATE` | `Workflow.StateSchema` | `urn:klab:workflow-state:{workflowId}@{version}:{schemaId}` |
| `WORKFLOW_TRANSITION` | `Workflow.TransitionSchema` | `urn:klab:workflow-transition:{workflowId}@{version}:{transitionId}` |
| `FLOW` | `Flow` | `urn:klab:flow:{flowId}` |
| `FLOW_STATE` | `Flow.State` | `urn:klab:flow-state:{flowId}:{stateId}` |
| `FLOW_TRANSITION` | `Flow.Transaction` | `urn:klab:flow-transition:{flowId}:{transactionId}` |
| `FLOW_ATTACHMENT` | `Flow.Attachment` | `urn:klab:flow-attachment:{flowId}:{attachmentId}` |

URN components are case-sensitive and must match `[A-Za-z0-9._~-]+`. Workflow and flow IDs should
therefore be slugs or UUIDs. This deliberately excludes separators from components, makes parsing
unambiguous, and keeps the URNs usable as a single Resources REST path segment. Clients should
still URL-encode the complete URN when constructing a URL.

`WorkflowUrns` is the canonical API utility for constructing and parsing these identifiers. A
workflow version is part of every schema URN because flows remain pinned to the exact schema they
started with. Flow child objects carry their owning flow ID when transmitted; old Nitrite records
are hydrated with this coordinate when read.

## Java and REST APIs

The `ResourcesService` contract exposes schema retrieval, flow creation/list/retrieval, state
create/update/delete, transition creation, and attachment upload/download. `ResourcesClient`
implements the same methods over HTTP, and `ResourcesMerger` forwards workflow operations to its
primary Resources service because a flow belongs to one authoritative store.

All routes require the normal authenticated Resources-service user role:

| Method | Route | Result |
| --- | --- | --- |
| `GET` | `/api/v1/workflows/{workflowId}` | Current workflow schema. |
| `POST` | `/api/v1/flows?workflowId=...` | Create a flow from a `Flow.State` body. |
| `POST` | `/api/v1/flows/initialize?workflowId=...` | Atomically submit a provisional state, attachment uploads, and its first transition. |
| `GET` | `/api/v1/flows?includeClosed=false` | Caller-accessible flow projections. |
| `GET` | `/api/v1/flows/{flowId}` | One caller-specific flow projection. |
| `DELETE` | `/api/v1/flows/{flowId}` | Permanently delete the caller-created flow as `EDITOR`, or any flow as `ADMIN`. |
| `POST` | `/api/v1/flows/{flowId}/reopen` | Reopen the latest actionable stage; workflow `ADMIN` only. |
| `POST` | `/api/v1/flows/{flowId}/states` | Create a manager-authorized state. |
| `PUT` or `POST` | `/api/v1/flows/{flowId}/states/{stateId}` | Update task data. `POST` is retained for the current Java HTTP helper. |
| `DELETE` | `/api/v1/flows/{flowId}/states/{stateId}` | Delete an unreferenced state. |
| `POST` | `/api/v1/flows/{flowId}/transitions` | Create an authorized transition. |
| `POST` | `/api/v1/flows/{flowId}/states/{stateId}/attachments` | Upload `Flow.AttachmentUpload`. |
| `GET` | `/api/v1/flows/{flowId}/attachments/{attachmentId}` | Base64 attachment payload. |

The dedicated routes above are lifecycle conveniences, not a separate transport requirement.
Every persistent workflow asset can also use the standard Resources CRUD routes:

| Operation | Generic route | Workflow behavior |
| --- | --- | --- |
| Retrieve | `GET /api/v1/retrieve/{knowledgeClass}/{urn}` | Returns the caller-authorized asset or flow projection. |
| List | `GET /api/v1/list/{knowledgeClass}` | Lists schemas, accessible flows, or their accessible child assets. |
| Resolve | `GET /api/v1/resolve/{knowledgeClass}/{urn}` | Returns a `ResourceSet` descriptor for later retrieval. |
| Submit | `PUT /api/v1/submit/{knowledgeClass}/{mode}/{urn}` | Creates or updates the permitted artifact as described below. |
| Delete | `DELETE /api/v1/delete/{knowledgeClass}/{urn}` | Only unreferenced `FLOW_STATE` and attachments on open states are deletable. |

Generic submission follows these conventions:

- `WORKFLOW` accepts a complete validated `Workflow`. Its body URN must equal the route URN and the
  caller must have workflow `ADMIN` authorization. Use `ADD` or `CREATE_OR_UPDATE`; an existing
  version is returned unchanged because workflow versions are immutable. State and transition
  schemata are immutable children and are submitted only as part of their workflow.
- `FLOW` accepts a `Flow` containing `workflowId`, target `assetUrn` and `assetType`, and exactly one
  initial state. The dedicated API may place the target fields on that initial state. Use
  `urn:klab:flow:new` as the submission URN; the service resolves the target, assigns the permanent
  flow ID, and adds it to the target asset's `ResourceInfo.flows` map.
- `FLOW_STATE` accepts a `Flow.State`. The route URN supplies the authoritative flow and state IDs;
  `ADD`, `UPDATE`, `REPLACE`, and `CREATE_OR_UPDATE` select creation/update semantics.
- `FLOW_TRANSITION` accepts a `Flow.TransitionRequest` command at
  `urn:klab:flow-transition:{flowId}:new`. The response identifies the new immutable transaction
  using its permanent `FLOW_TRANSITION` URN.
- `FLOW_ATTACHMENT` accepts a `Flow.AttachmentUpload`. For submission only, the last component is
  the target state ID: `urn:klab:flow-attachment:{flowId}:{stateId}`. The response identifies the
  new descriptor using its permanent attachment ID.

Flows, workflow-version history, and transition transactions are intentionally not generically
deletable: this preserves the reconstruction and provenance guarantees. Attachment bytes continue
to use the dedicated download route because the generic `FLOW_ATTACHMENT` asset is its cheap JSON
descriptor, not the potentially large blob.

`AttachmentUpload.content` is a byte array and therefore Base64 in JSON. This provides a portable
small API without requiring multipart support in every client. Infrastructure intended for large
review artifacts should add streaming/multipart transport while retaining the same service and
storage contracts.

Example transition body:

```json
{
  "transitionId": "submit",
  "sourceStateId": "a-state-uuid",
  "expectedRevision": 3,
  "targetState": {
    "title": "Peer review of urn:klab:example"
  },
  "metadata": {
    "comment": "Ready for independent review",
    "client": "modeler"
  }
}
```

The server assigns the target state ID, timestamps, attachment list, actor, transaction ID, and
new aggregate revision. Callers may suggest a target ID, but it must be unique in the flow.

Workflow bean metadata setters use `Map<String, Object>` as their transport contract. Their getters
return the idiomatic `Metadata` API required by `KlabAsset`. On input, the beans accept both a plain
map (as produced by YAML and ordinary clients) and k.LAB's polymorphic Metadata envelope; the latter
is unwrapped internally. `ResourceInfo.metadata` remains a `Metadata` property and is independent of
the per-flow catalog held in `ResourceInfo.flows`.

## Provenance and operational guidance

- Put stable vocabulary terms, policy identifiers, and form/schema versions in workflow, state,
  transition, and transaction metadata. Do not put access tokens or secrets there.
- Treat history as append-only. Correct an erroneous decision with a compensating transition
  rather than editing history.
- Include a rationale and client identifier in transition metadata when decisions have scientific
  or publication consequences.
- Back up `resources.db` and any future external attachment store together. Attachment descriptors
  without corresponding payloads are detected as an integrity error on download.
- Schema changes require a new semantic version. Never reinterpret an existing version because
  active and closed flows are reconstructed against it.
- `GET /flows` is also the portable JSON export available to ordinary participants. An owner or
  administrator receives a complete flow; other exports intentionally contain only their
  authorized projection.

## IDE integration

`klab-ide` provides a generic JavaFX `WorkflowEditor` hosted as an auxiliary tab of the
`WorkspaceEditor` that owns the target asset. Its header reports flow status, start time, revision,
and public-read mode; its stage browser exposes the caller's projection, or the complete flow when
`publicRead` is true. A new flow is a client-side draft focused on its INIT stage and is not stored
until its first successful confirmation. An existing private flow focuses the first
open stage assigned to the caller, then falls back to the first visible current stage.

Install a `WorkflowUIProvider` on `WorkspaceView` to supply two callbacks: the workflow definitions
that may be started for an `(asset, UserScope)`, and an optional specialized stage editor selected
from the workflow, flow, state, and state schema. The specialized editor returns its JavaFX node,
a live validity predicate, a metadata exporter, and a read-only callback. Calling the supplied
`validationChanged` runnable refreshes Update and Submit enablement. Returning `null` selects the
generic stage editor, which provides persisted title and description fields alongside metadata.

The common shell renders instructions and an attachment-rule selector. Each choice identifies its
logical type, required/optional status, admitted media type, optional asset class, and arity; the
upload prompt follows the selected rule. Uploaded attachment descriptors record the detected
concrete media type, while wildcard media types in the workflow remain admission constraints. Draft
uploads are queued locally.

The action bar uses compact, tooltip-labelled icons for stage update and deletion. Admitted
transitions appear in one expandable selector, initially set to the non-functional
`-- Choose the next stage --` entry, followed by a single **Submit** button. Submit is enabled only
after the user deliberately selects an admitted transition and the specialized editor reports valid
contents. A persisted editable stage also provides the update action, which saves its title,
description, and specialized-editor metadata without performing a transition. Stages that fail the
same access/ownership checks used by the service are rendered read-only. The first confirmation sends one atomic initialization request;
subsequent confirmations save the specialized editor's export as `Flow.State.metadata`, then submit
the transition with an optimistic revision. Failed actions remain in the editor with a prominent
inline explanation, allowing correction and retry; cancelling a draft closes it without cleanup
because nothing has been stored. A private open stage is editable only by workflow `ADMIN`, its `owner`, or an
assigned participant with `EDITOR`; public-read and closed flows are browsers. Only `ADMIN` sees the
reopen action on a closed flow.

Atomic initialization uses a fail-fast HTTP command: a non-success response or an unreadable
response body is propagated to the editor instead of being represented as a null Flow. The editor
does not clear its provisional state or invoke initialization callbacks until it receives a valid
Flow, so the user can correct and resubmit the same draft.

For a persisted flow, the global **Flow** menu offers **Delete flow…** only to its `EDITOR` creator
or an `ADMIN`. Confirmation permanently removes every state, transaction, attachment payload, and
the corresponding entry in `ResourceInfo.flows`. When the last flow on a second-class asset is
removed, its flow-only `ResourceInfo` is removed as well; first-class resource information is
retained and reset to its non-reviewing status.

The workspace-tree context menu contains a separated **Workflows** section only when content exists.
It uses human-readable workflow names and provides **Start workflow**, **Open flows**, and **Closed
flows** submenus. **Start workflow** intersects the provider callback, the caller's
`workflow.permitted` grants, and the workflow's top-level `assetTypes`; a disallowed asset therefore
never receives the action, and the service repeats the check if a client attempts creation directly.
Accessible flows are filtered by exact target asset URN, and multiple flows on the
same asset are shown independently. Assets with one or more accessible flows carry a small blue dot
to the right of their label in the workspace tree. Both this menu and the corresponding workflow controls in
`ResourceEditor` intersect the provider callback's results with `workflow.permitted`; direct start
callbacks repeat the check, and `WorkflowEditor` derives editability from `Workflow.canAccess`.

The current code does not implement durable timers, a notification outbox, reviewer quorum, cryptographic
signatures, attachment virus scanning, or multi-source joins. These are policy/execution features
that can be added without changing the persisted core abstractions.


## k.Actors instrumentation

A workflow may name **one** behavior URN in `behavior`. Omit the field for the existing,
uninstrumented execution model. Only the k.Actors `behavior` category is accepted: applications,
scripts, tasks, libraries, and standalone traits are rejected. Each flow has its own global state;
the schema and the compiled behavior are not shared mutable flow state.

```yaml
id: instrumented-review
version: "1"
name: Instrumented review
behavior: examples.review
states:
  editing:
    managerRoles: [ADMIN, EDITOR]
    contributorRoles: [EDITOR]
    onStart:
      - action: prepare
    onCommit:
      - action: validate
    actions:
      - id: send-reminder
        label: Send reminder
        action: remind
        parameters:
          subject: Review reminder
    open: true
  complete:
    managerRoles: [ADMIN]
    contributorRoles: [ADMIN]
    open: false
transitions:
  initialize:
    sourceStates: [INIT]
    targetState: editing
    roles: [ADMIN, EDITOR]
    actions:
      - action: created
  finish:
    sourceStates: [editing]
    targetState: complete
    roles: [ADMIN, EDITOR]
    actions:
      - action: submitted
```

Bindings are ordered objects with `action` and optional `parameters`. Stage buttons additionally
require a unique, nonblank `id`; `label` is their display text. Empty or omitted lists mean no
calls. `init` and `main` cannot be explicitly bound. Structural validation rejects bindings without
a behavior, duplicate button IDs, and invalid action names. Resolution additionally checks that
bound actions exist and that configured parameter names are declared by those actions.

### Execution order and transaction boundary

Creation resolves the behavior through the owner's `UserScope` and connected Resources services.
The compiler uses the Resources service's component registry, making installed Java actors
(including core facilities) available without a Runtime service. The owner remains the agent's
creation scope; the user triggering an individual operation is passed separately.

Creation executes the constructor `init`, then `main` if declared, then the selected `INIT`
transition's `actions`, then the initial stage's `onStart`. On an ordinary transition the sequence is:

1. Validate authorization, revision, and transition applicability.
2. Restore the flow's latest checkpoint, without rerunning `init` or `main`.
3. Invoke the outgoing stage's `onCommit` actions in order.
4. Invoke the transition's `actions` in order, validate the resulting required attachments and
   transition inputs, and checkpoint the outgoing stage.
5. Prepare the review candidate from the resulting content, close the source, add the target,
   and record the transition.
6. Invoke the incoming stage's `onStart` actions and checkpoint the incoming stage.
7. Persist the flow, its history, stage snapshots, and latest global state as one aggregate.

Atomic first-stage submission also uses this sequence, including initialization, before publishing
the aggregate or attachment payloads. Direct stage creation runs `onStart`. Ordinary field edits,
attachment operations, reads, and administrative reopening do not replay behavior hooks.
A button invokes only its configured action and checkpoints the selected stage; it does not
implicitly commit or transition the flow. The revision advances after a successful invocation.

A thrown action exception, a missing automatic parameter, or an unpersistable global value prevents
the flow mutation from being stored. Return values are currently ignored: validation actions must
throw/assert on rejection rather than return `false`. Behaviors are trusted service code configured
by workflow administrators. They receive actual aggregate beans and can prepare stage content;
they should not change IDs, ownership, revision, workflow topology, or authorization fields.

The execution profile accepts **FUNCTION and finite SUPPLIER actions**, including `init`, `main`,
and inherited actions. Suppliers (such as the core email actor) are awaited for up to 60 seconds
per invocation before checkpointing. Failure, interruption, or timeout aborts the mutation and
disposes the action scope. EMITTER actions, including currently unbound actions, are rejected by
the compiler's effective execution classification. Workflows remain asynchronous between operations,
while each instrumentation operation finishes before persistence. No running agent, reactor, timer, message
subscription, future, or Java service instance is stored. The isolated agent is stopped after the
operation. Long-running Java functions can still block an operation; this is not a preemptive sandbox.

External effects are not rolled back with the aggregate. In particular, an email sent before a
later failure or process crash may be sent again on retry. Use idempotent integrations and avoid
assuming exactly-once delivery. A durable outbox and restartable asynchronous continuation contract are tracked
in [WORKFLOW_EXTENSION.md](WORKFLOW_EXTENSION.md).

### Parameters and the editor agent

Arguments are resolved in declaration order using the following precedence:

1. Exact reserved context name.
2. Explicit binding parameter, or an allowed interactive input.
3. A unique context object matching `@type(class="...")`.

| Name | Value |
| --- | --- |
| `workflow` | The resolved `Workflow` schema |
| `flow` | The working `Flow` aggregate |
| `stage` | The current `Flow.State`; source for commit/transition, target for start |
| `content` | Content mutation agent for the bound stage |
| `transition` | The `Workflow.TransitionSchema`, or null for independent buttons/direct stage creation |
| `user` | The requesting `UserIdentity` |
| `participant` | The requesting `WorkflowParticipant`, including workflow roles |
| `editor` | Read-only `WorkflowBehaviorBridge.Editor` facade |
| `document` | `core.document` subtype for a document target, when requested |
| `ontology`, `namespace`, `strategy_document`, `behavior_document` | Alias for the applicable target document subtype |
| `project` | `core.project` for a project target or the target document's containing project |

The `content` agent exposes `title(value)`, `description(value)`, `metadata(key, value)` and
`value(key)`. Use these finite calls to prepare stage content, e.g. `content.title("Ready for review")`.
Metadata values must be portable. Prefer this agent to changing aggregate fields directly.
Dynamic Java suppliers are awaited, and dynamic
emitters are rejected before invocation.

### Attachments through the content agent

The same `content` agent creates and reads attachments on its bound stage. Java camel-case names
can be called in k.Actors snake case, for example:

```kactors
action prepare(content):
    content.attach_text("supporting-material", "readme.txt", "text/plain", "Ready for review")
```

| Call | Input/result |
| --- | --- |
| `attach_text(type, name, mediaType, text)` | UTF-8 text; returns an attachment descriptor |
| `attach_bytes(type, name, mediaType, bytes)` | Direct Java `byte[]` content |
| `attach_bytes(type, name, mediaType, assetType, bytes)` | Also supplies a knowledge-class enum name |
| `attach_file(type, path, mediaType)` | Server-side file; filename inferred from the path |
| `attach_url(type, url, name, mediaType)` | Downloads HTTP(S) content under the supplied filename |
| `attachments` | List of the bound stage's attachment descriptors |
| `attachment(id)` | Defensive copy of a bound-stage attachment's bytes |
| `remove_attachment(id)` | Removes the descriptor; returns whether it was present |

The `type` must match a stage attachment rule. Existing media-type, asset-type, arity, count,
byte limits, checksums and author attribution apply to generated attachments too. An asset type
is inferred from the rule when available. Commit and transition actions can generate required
inputs before transition validation; initial start actions can do so during atomic first submission.
Review-bound candidate artifacts remain immutable. A behavior can read both existing and newly
generated payloads within the operation. Descriptors and byte arrays are transient Java values;
persist IDs or other scalar metadata in globals instead.

New payloads are staged until the action and checkpoint succeed, then written before the flow
aggregate. Failed saves clean up attempted payload writes. Removals delete stored bytes only after
a successful aggregate save and only when no stored flow still references them. These compensating
operations handle ordinary failures; a process crash between records may leave orphaned blobs.
The store does not provide a multi-record transaction or a durable garbage-collection journal.

File access is restricted to real paths under the roots in the Resources JVM system property
`klab.workflow.attachment.roots`, separated by the platform path separator (`;` on Windows).
The default is the dedicated `klab-workflow-attachments` directory under `java.io.tmpdir`;
create that directory before placing generated files there. A path names a file on the service
host, not on the IDE user's computer. IDE-local files continue to use the upload API.

URL downloads have a 10-second connection timeout, a 30-second overall timeout, and a bounded
streaming body. Redirects, credentials embedded in URLs, and non-HTTP(S) schemes are rejected.
The optional comma-separated system property `klab.workflow.attachment.hosts` restricts downloads
to exact hostnames and explicitly permits those hosts even on internal networks. Without it,
private, loopback and reserved resolved addresses are rejected. DNS checks are not a network
sandbox: configure service egress controls or an explicit trusted-host list where required.
No caller credentials are forwarded. Authenticated source adapters and durable transfer retries
remain extension points.

### Read-only editor and parameter contracts

The editor facade exposes `flowId`, `stageId`, `title`, `description`, `owner`, `revision`, and
`attachmentNames` through ordinary k.Actors Java-agent calls. It holds immutable scalar values and
an immutable list, with no editor widget, mutation operation, or live client connection. It describes
**saved server content**. The bridge neither accesses nor executes against unsaved IDE buffers.
For example, an action can accept `editor` and call `editor.title`; an action accepting
`@type(class="Flow") job` receives the flow by type. Canonical Java names match exactly and simple
names match case-insensitively, including implemented interfaces and superclasses. Ambiguous type
matches fail rather than choose an arbitrary object. Named values are also type checked.

Configured values cannot replace reserved context values. Interactive callers can supply only
parameters that discovery reported as unresolved; attempts to replace injected or configured values
are rejected. Unresolved automatic parameters abort the operation. For buttons, discovery returns
names plus Java/behavior type hints, allowing the UI to request values before invocation. The
compiler's normal argument contracts remain the final runtime type check.

Do not retain `flow`, `stage`, `user`, `editor`, or another live Java object in a global. Copy the
needed scalar values into globals and accept fresh context parameters on subsequent actions.
The document/project actor wrappers are an explicit exception: they persist stable references
and re-resolve data with the current participant's permissions. They can be accepted in `init`
and retained in globals. See [the actor reference](AGENTS_REFERENCE.md#workflow-target-binding-and-restoration).

### Checkpoint storage, restarts, and behavior updates

`Flow.behaviorCheckpoint` stores the latest globals; `Flow.State.behaviorCheckpoint` records that
stage's latest start, commit, or button snapshot. Both are server-owned and committed with the
aggregate. Each checkpoint contains the canonical behavior URN, version, and detached global-value
tree. Flow and state responses omit checkpoints, including public-flow responses. Client-supplied
checkpoints are discarded on creation and ignored during ordinary updates.

The portable state subset is null, strings, booleans, finite numbers, lists, and string-keyed maps.
It also includes the versioned core document/project actor references; no credentials or permission
grants are stored in those references. Project CRUD uses the triggering participant, not the behavior owner's authority.
Nested data is copied; live agents, scopes, service objects, arbitrary DTOs, non-string map keys,
cycles, and excessive nesting are rejected. Inherited delegates have separate nested snapshots.
Inherited initializers currently run without workflow-context arguments; place context-dependent
initialization in the top-level `init` action.
Dynamically constructed imported agents are not a portable state graph: retaining them in globals
or changing the delegate layout prevents checkpointing/restoration. JSON number representation may
normalize numeric wrapper types across a restart.

Every instrumented operation resolves through the owner's services again. The existing
scope-isolated semantic-bean cache checks service identity, canonical URN, version, and source
timestamp before reuse. Updated source with the same behavior version is recompiled and receives
the old globals, so same-version changes must preserve state layout. A changed behavior version or
inherited delegate layout fails closed pending explicit migration; no implicit `init` replay or
state reset occurs. Currently generated classes are compiled for each isolated operation rather
than cached across workflow operations.

After a service restart, an owner must have a live authenticated user scope again before another
participant can resume their instrumented flow. The server never substitutes the requesting user's
permissions for an absent owner's scope. A durable owner execution identity remains an upstream
policy decision.

### Interactive API and IDE

`ResourcesService.getFlowActions(flowId, stateId, scope)` and
`executeFlowAction(flowId, stateId, actionId, request, scope)` expose the bridge to clients.
The authenticated REST routes, relative to the Resources API base, are:

- `GET /flows/{flowId}/states/{stateId}/actions`: configured button IDs, labels, action names,
  unresolved parameter descriptors, and the authoritative flow revision. This does not run
  initialization or action code.
- `POST /flows/{flowId}/states/{stateId}/actions/{actionId}` with
  `{"expectedRevision": 3, "parameters": {"message": "Please review"}}`: invokes that configured
  button and returns the updated projected flow. The revision is mandatory and must match exactly;
  negative values do not disable concurrency checking.

Both operations require an active current stage, contributor permission, stage-editor ownership
(or the existing administrator/assigned-editor allowance), and an unexpired response deadline.
Neither accepts arbitrary behavior action names or executes buttons belonging to another stage.
Discovery is advisory: authorization, revision, configuration and parameter matching are checked
again when executing.

The `klab-ide` workflow editor renders configured buttons separately from transition selection.
Discovery and execution run in background tasks, disabling the editor while a request is pending.
A modal requests unresolved text, boolean, and numeric Java parameters, validates input before
sending, and refreshes the stage on success. It explains that actions use saved content. Cancel
performs no invocation. Agent-valued inputs and richer Java object editors are not yet supported;
the REST descriptor leaves room for future IDE and web form providers. Unsaved content must be saved
before running a button. Existing proposal-draft confirmation is retained.
