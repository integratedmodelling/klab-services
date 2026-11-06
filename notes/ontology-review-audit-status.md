# Review audit checkpoint

Reviewed baseline `b59382f933ec` in isolated branch `ontology-review-audit`. The review used
fresh negative tests rather than treating the successful scenario as authorization proof.

## Reproduced and fixed

- A new cycle could reuse a submitted alternative's proposal/revision identity with changed
  bytes. Candidate opening now checks the same complete submitted-identity binding as contribution
  submission. Exact adoption of an alternative remains allowed; changed inputs require a fresh ID.
- A foreign draft's proposed body was schema-checked before ownership denial. Ownership and
  submitted immutability now precede content processing. The original code still rejected the
  write; the fix makes authorization precedence consistent and avoids parsing an unauthorized update.
- Internal notice authorization granted editors access before checking whether its round/cycle
  coordinate existed. Coordinates and event kind/closure state are now checked before privilege.
  No public event-enqueue endpoint or real transport was enabled before or after this fix.
- Refresh after an external close hid an unsaved feedback form and left navigation locked. Own
  unsaved work now remains read-only, downloadable and explicitly discardable. Saved server drafts
  are not deleted by discarding local unsaved edits.
- A denied refresh left cached shared feedback visible. The UI clears the shared review view and
  read-only candidate on a 403, while retaining the author's unsaved working copy. Pending candidate/
  context replies cannot repopulate an invalidated view or leave the page permanently busy.
- A partial permission loss could silently select an admitted community iteration while retaining
  unsaved peer text. Missing context now retains its original IDs and becomes read-only; no text
  is redirected to another audience. The user explicitly discards/recovers and selects another stage.
- An unavailable cycle/round URL silently opened another visible context. It now preserves the
  requested URL, shows an unavailable message, and requires an explicit admitted selection. Links
  remain locators, never access grants. This is required by the new public-digest link policy.

Own editable candidate work remains intact when comparing feedback or recovering from access
errors. Cache clearing happens on refresh/denial; it cannot retract prior downloads or erase a
reader's knowledge, and it is not a push-based revocation channel.

## Boundaries checked without a new bypass finding

Reviewer admission uses the authenticated scope's real REVIEWER/workflow grant. Community
admission uses authentication and a nonblank identity, without synthetic reviewer roles. Forged
command owner/identity/roles/canManage fields cannot promote a community participant. Flow control
still requires an authorized editor who owns the flow or owns/is assigned a stage, or service admin.
Stage assignment is trusted editorial authority; the pilot blocks arbitrary stage creation.

Submitted feedback is audience-filtered on the server. Another author's draft is excluded even
from editor/public projections. Narrow artifact access checks the visible cycle and exact bound
proposal/source checksum, without opening unrelated stage material. Reserved service metadata
cannot be injected through request/actor metadata and does not enter user views or Git mappings.

Two independent managers were forced through both close/submit commit orders. One CAS wins;
closure-first rejects the submission, submission-first preserves it when closure retries. No later
write changes the closed record. Only the winning facts enter durable notice replay. Native CAS,
exclusive database ownership and close/reopen checks remain part of the combined regressions.

Notification recipients remain frozen and currently reauthorized through a fresh trusted directory
adapter. Missing verification/consent fails closed. That directory is still unbound and real delivery
is disabled. Existing auth/controller fixtures are not real IdP or production revocation proof.

## Evidence

Initial failures are retained in `pilot-audit-repro.log`, `pilot-audit-ui-repro.log`,
`pilot-audit-pending-repro.log` and `pilot-audit-context-repro.log`. Fix verification uses
`pilot-audit-fixed.log`, `pilot-audit-combined-reactor.log`, `pilot-audit-context-native.log`,
`pilot-audit-ui-complete.log`, `pilot-audit-typecheck-final.log` and final UI build logs.
The delivery receipt records exact outcomes and hashes. No production service/project was touched.

## Archive decision and next integration

The user selected a Git digest with links to permission-checked persistent stages. This decision
is settled. The default must omit contribution text, private drafts, participant identities and
sensitive proposal/operational metadata. Use minimal opaque review coordinates and trusted Resources
links; keep detailed provenance in the permission-enforced workflow. The old full-history mapper
must not feed a public/default Git export. Digest outbox/receipts and project authorization are the
next integration, without a remote push or real-project write during development.

See [pilot handoff](ontology-review-pilot-handoff.md) for prerequisites and repeatable checks.
