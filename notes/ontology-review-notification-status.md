# Notification integration checkpoint

Isolated branch `ontology-review-notifications`, starting at `addbdff193e97`.
Task-10 source `353b3a785a3a` was imported as `65e83fd26`. Earlier delivery bundles,
the durable mutation/proposal checkpoint and original task/user checkouts are preserved.

## Implemented boundary

Round opening, submitted contributions, round closure and cycle closure stage immutable
notification events before the flow's database-conditional write. A failed authorization,
schema check or CAS publishes neither the review change nor its intent. Private draft saves
do not create events. Opening a candidate is not labeled as an integrated/accepted proposal.

Contributions notify the flow creator/editor. Additional assigned-editor notifications need
a directory that proves their current editor role. Closing rounds includes submitted authors;
opening rounds includes earlier submitted authors of the same audience. Cycle closure includes
its submitted authors. No global reviewer/group list or authenticated-user broadcast is inferred.
Recipients are frozen server-side and batched into at most 256 per event. The private pilot
ledger is bounded to 8,192 events and an 8 MiB encoded budget; capacity failure rejects the
mutation rather than silently losing notice intent. Sustained production needs archival/retention
or a transactional event table rather than an indefinitely growing aggregate.

Recipient IDs are SHA-256-derived principal IDs, supporting Unicode/email-shaped account
names without storing names or mailboxes in notification metadata. These are private identifiers,
not encryption. A trusted directory must resolve the exact account and verified mailbox and
invoke current workflow/round authorization before delivery. New grants do not add historical
recipients; role revocation fails the authorization check. There is no mailbox lookup or SMTP
configuration in this integration.

The ledger is encoded under a flat `klab:review:notificationLedger:v1` metadata key. The first
native reopen test caught that dotted dynamic metadata keys do not round-trip as flat keys through
Nitrite. Nested library intent metadata is therefore encoded inside the ledger string as well.
The corrected native close/reopen test passes and replays all four committed phase/submission
events exactly once after reopening the production store.

All user flow projections (including owners/admins/public flows) recursively strip reserved
notification keys from flow, state and transaction metadata. New/updated state and transition
metadata reject those keys, including nested entries. Actor metadata read/write helpers also
reject them. The Git mapper explicitly exports candidate/submitted-history fields and never the
private flow metadata; a regression test confirms intent metadata is absent from the mapped archive.

## Independent delivery-library review

The outbox persists before invoking transport, retains per-recipient deduplication, uses an OS
owner lock and atomic file replacement, and refuses further sends after an ambiguous write.
Lost post-commit wake-ups are repaired by full replay of the committed ledger. Replay is internal,
never part of a request transaction, and failure belongs to worker health rather than workflow
rollback. No worker is registered in this checkpoint.

Current authorization is required for each attempt. Revoked/missing accounts are suppressed;
directory outages must throw and retry within a bounded budget. Generic notices contain no
proposal, comment, ontology, contributor, stage detail, credential or URL. Transport exceptions
and crashes during send remain uncertain and never automatically resend. Operator reconciliation
is an internal library operation with no public endpoint; a future endpoint must protect/audit it.

Remaining operational requirements: service-private directory ACLs and symlink/path control,
verified directory/consent integration, lifecycle-owned bounded worker and shutdown, trusted
application links, health/status visibility, retention/capacity policy and privileged reconciliation
audit. No power-loss or network-filesystem durability guarantee is claimed by the file outbox.

## Verification and limits

Normal reactor logs are task-7 artifacts: `pilot-notification-integration.log` records the initial
native metadata round-trip failure; `pilot-notification-integration-recheck.log` records the corrected
checks (47 tests passing in the normal Resources-server reactor). Tests use fake mail transports only. Coverage includes atomically rejected contributions,
lost wake-up replay, restart/deduplication, metadata spoof rejection, owner/public projection and
Git privacy, audience-specific recipients, role revocation, native RocksDB reopen, uncertain sends,
bounded retries and operator reconciliation.

The full broader regression run is recorded separately with the delivery receipt. Existing UI
artifacts were unchanged. Live IdP/multi-user testing still needs authorized test identities.
Archive dispatch remains disabled pending the user visibility decision and archive outbox/receipts.
No real email, SMTP settings, push, deployment, restart or real-project writes occurred.
