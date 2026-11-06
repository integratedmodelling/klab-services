# Ontology review: combined pilot checkpoint

6 November 2026. Working copy: `task-7/services-pilot`, branch
`ontology-review-pilot`, based on published Services develop `212548f761`.
The task-8 round implementation `9d4146e8c3` was imported without conflicts as
`ea5fe303d`, then independently reviewed and integrated with bootstrap and Resources UI.
Both original task copies and user checkouts are preserved.

## Usable path in this checkpoint

Create an `ontology-review-pilot@0.2` flow through the existing editor/Resources API.
Open `ui/review?flow=<id>&stage=<id>` on its Resources service, authenticate, prepare
the draft and edit/save a candidate. Start a cycle from the selected stored proposal;
open reviewer and/or community rounds in either order, including simultaneous rounds.
Participants save private drafts and submit attributed text feedback. Editors can see
all submitted feedback, integrate another proposal revision and open a later cycle.
Closed cycles remain immutable and consultable; their closure does not accept/apply or
publish ontology changes. The prototype 0.1 and legacy acceptance flows are not migrated.

The face-to-face UI retains the editor's current candidate on the left while filtering
feedback by proposal/cycle, iteration and contributor on the right. Filtering historical
feedback does not overwrite the editor's current draft. Community participants can read
the frozen candidate without gaining access to the editing stage. Cycle/round selection
updates `cycle=<id>&round=<id>` in the URL and survives reload/authentication return.
Private draft IDs remain stable when an author has multiple drafts. Conflicts preserve
unsaved feedback and require explicit refresh/retry; there is no automatic resubmission.

This remains a JSON candidate editor with text feedback, not the final structured
proposal/comment authoring UI. Proposal/diff contribution attachments are not implemented.

## Independent authorization and integrity review

The imported implementation's editor check admitted any EDITOR permitted for the
workflow, even on someone else's flow. Management now requires the authenticated flow
owner or an assigned/owning stage editor with EDITOR/workflow permission, or ADMIN.
Generic stage creation is blocked for this pilot so it cannot manufacture an assignment.
Existing stage owner checks remain in place.

Reviewer admission requires the real REVIEWER role and permission for this workflow.
Community admission requires an authenticated nonanonymous identity with a nonblank
username; email, known-person flags and synthesized roles are not required. Only
submitted feedback is stage-visible; another person's draft is hidden even from editors
and public flow projections. Server-derived management/contribution permissions are
view metadata, not client-authoritative input or mutable provenance fields.

A narrow artifact endpoint returns only a visible cycle's exact proposal/ontology bytes,
rechecking membership, media and checksums. It cannot read unrelated attachments or
supporting material. Unauthorized reads use 403; invalid state/revision operations use
409. HTTP cycle views and candidate responses are private/no-store. The controller
rejects unauthenticated EngineAuthorization principals even when a scope is supplied.

The bootstrap pilot can create a cycle directly from a current editing stage: the
server validates schema and derives candidate identity from stored attachment bytes.
It does not accept a caller's candidate identity claims. New proposal revisions do not
replace old artifacts; referenced artifacts/source-stage links and whole review flows
cannot be deleted through public mutation operations. A previously bound proposal id
and revision cannot later identify different proposal/source checksums. An unchanged
candidate may be reviewed in a later cycle. Editorial lineage remains explicit rather
than choosing an automatic merge or branch policy.

Java ResourcesClient methods now support cycle reads/commands and exact artifact reads.
The browser transport was corrected for Spring's plain Base64 string responses;
it no longer assumes those strings are JSON. No existing server string wire format was
changed.

## Concurrency and durability limits

All mutations still use one WorkflowManager's synchronized lock and aggregate revision.
Two callers writing the same observed revision have one winner; failed writes operate
on detached copies and leave the old aggregate intact. The Resources provider owns one
manager, which is the pilot's writing boundary. ResourcesKBox.putFlow is an atomic whole
aggregate write, **not compare-and-swap**. Independent writing managers/processes sharing
a store are not protected by this implementation. Do not advertise cross-process or
replicated multi-writer operation until storage-level CAS is implemented and tested.

The production Nitrite/RocksDB close/reopen test preserves the closed cycle, reviewer
attribution, text, timestamps and exact candidate payload bytes. This proves the tested
single-process storage path, not a distributed persistence protocol.

## Verification evidence

- `pilot-combined-tests-final.log`: normal Resources-server reactor BUILD SUCCESS;
  84 Resources tests, 2 common proposal tests, 5 core UI tests and 1 server UI test.
  Includes imported round tests, bootstrap/action tests, integrated-candidate tests,
  same-manager simultaneous writers and actual production-store close/reopen.
- `pilot-combined-boundary-tests.log`: normal reactor BUILD SUCCESS; 12 targeted
  Resources tests plus 2 server tests. Covers flow-specific editor denial, peer-only
  artifact denial, authorized community reads, immutable source/history, later cycle,
  durable reopen, and real EngineAuthorization objects at the HTTP controller boundary.
- `pilot-revision-binding-tests.log`: targeted normal reactor verification of revision
  labels bound to immutable proposal/source bytes and subsequent integrated drafts.
- `review-ui-round-tests-final.log`: six disposable browser tests covering explicit
  bootstrap, retained candidate originals, community-only candidate access, stable round
  URLs, proposal/contributor filters, multiple private drafts, explicit conflict retry,
  and actual plain-text attachment transport.
- Vue typecheck and standalone/embedded UI builds pass; logs:
  `review-ui-combined-typecheck.log`, `review-ui-combined-build.log`.

Logs and published dependency receipts are in task-7, outside Git. All Maven commands
use the task-local writable cache and test home plus the user cache as a read-only tail.
The five previously published Languages JARs/POMs remain pinned to the verified
`20261106.143015` publication; no new dependency overlay or global configuration change.

HTTP tests use real EngineAuthorization objects and scoped service boundaries, but no
live JWT validation, identity-provider sign-in, external accounts or deployed multi-user
browser sessions. Browser API fixtures are simulated. Do not claim live community
operation until those identity/group-mapping tests pass against an isolated deployment.

## Remaining pilot gates

1. Integrate structured proposal/comment contribution forms and alternate proposal/diff
   artifacts while preserving original submissions and stage audience.
2. Add durable editor/participant notices and asynchronous email delivery with retry and
   deduplication. Persisted feedback is not yet a delivered editor notice.
3. Export immutable cycle/contribution history into the hosting project's additional
   material and commit through an isolated, idempotent Git operation with receipts.
   Project actor write scaffolding exists; automatic history commits are not implemented.
4. Verify real authenticated editor/reviewer/community identities, role revocation and
   stage URLs against a disposable deployment; add storage CAS before multiple writers.

No user service, ontology project, original checkout, credential or global configuration
was changed. No email, external account, push, deployment or restart was performed.
