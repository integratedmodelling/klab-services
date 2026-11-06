# Durable notification pilot

This slice is an opt-in library, independent of the review-round integration. It does not register a
worker, send mail, change configuration or modify workflow controllers. It builds on the existing
service-owned `EmailManager`; its blocking SMTP call remains outside workflow mutations.

The integrated pilot now captures round opening/closure, submitted feedback and cycle closure
intents atomically through `ReviewWorkflowNotifications`. It provides private replay and exact-round
reauthorization helpers, but still registers no delivery worker or real transport. The integration
status and outstanding directory/consent requirements are in
[`notes/ontology-review-notification-status.md`](../notes/ontology-review-notification-status.md).

## Integration contract

1. In the authorized workflow mutation, derive a stable event ID from a committed transaction ID
   plus event kind. Use opaque flow, stage and account IDs. Derive the recipient set on the server
   and freeze it at that revision. For contributions, notify the ontology's assigned editors;
   for openings/decisions/closures, use explicitly entitled stage participants. Authentication alone
   does not grant reviewer authority or access to other stages. Never trust request-supplied roles,
   audiences, mailboxes or notification metadata.
2. Remove/reject `ReviewNotificationIntents.METADATA_KEY` from incoming request metadata. Call
   `ReviewNotificationIntents.stage(serverTransactionMetadata, event)` on metadata owned by the
   server, then persist that transaction with its workflow history using the same aggregate write.
   For contributions that do not create a history transaction, persist an equivalent service-private
   immutable event ledger in the same contribution aggregate. A separate pre/post-commit file write
   is insufficient. No event is generated for a rejected or rolled-back mutation.
3. **Strip the reserved metadata key from all user-facing flow/history/attachment projections and
   archives.** Intents contain private recipient IDs. Base64 is a serialization format, not encryption.
   Do not blindly export this metadata to project Git history. Keep the durable server-side copy for
   recovery even after the cycle closes. Validate existing projection behavior before adding hooks.
4. After the successful workflow commit, wake an independent worker. On startup and periodically,
   scan committed intents, `read` them, and call `enqueueCommitted` for each. Start with full replay
   for the bounded pilot; there is deliberately no cursor that can advance before durable enqueue.
   A duplicate is a no-op. Same event ID with changed kind, stage, flow or recipient set is an error.
   Do not reconstruct historical recipients from today's group membership.
5. Enqueue/storage failures must become observable worker health failures, with source intents kept
   for replay. Never propagate them back as a failed contribution/transition after its commit. Failure
   to persist the workflow's own atomic intent is an ordinary persistence failure before commit;
   disabled or failed SMTP is never involved in that path.
6. Own one `ReviewNotificationOutbox` under the service's private data directory and close it during
   service shutdown. Configure an ACL limited to the service/operator, outside hosted projects and
   Git. Supply a UTC clock, explicit capacity, retry budget and positive delay (e.g. 10,000 rows,
   3 attempts, 5 minutes). A second owner fails closed via an OS lock. Call `dispatch(1..256, policy,
   transport)` from an independent bounded worker; it performs blocking I/O and must not run on the
   request thread or inside a workflow lock/transaction.
7. The `RecipientPolicy` must resolve one currently verified account mailbox and check the recipient
   is still entitled to this event's exact flow and stage. Return empty for revoked/missing identities
   or permission, causing permanent suppression. Directory outages should throw, producing a bounded
   failed lookup that can retry. Return only a mailbox for the exact opaque ID, never a guessed address
   or group list. A new grant does not retroactively add recipients to old events. A revoked account
   does not get automatically revived by replay. The integration owns this policy and consent rules.
8. Explicitly opt into `new ReviewEmailTransport(serviceEmailManager)` only when real delivery is
   authorized. Configuration is read by the existing manager; this component never changes it.

The metadata adapter is provided rather than live hooks because the baseline has transaction history
and metadata, but no integrated multi-contributor event model or safe recipient directory contract.
Wire each desired phase once against the final contribution/round model. No REST endpoint or role
inference is introduced by this patch.

## Delivery and operator behavior

Each event/recipient pair has independent status, attempts, next-attempt time and a fixed diagnostic
code. `deliveries()` is for a privileged internal operator view; do not expose it to review participants.
It stores no mailbox, message body, credentials or raw exception. Current messages have a generic
subject and sign-in notice, with no ontology, stage, contributor, comments, proposal, tokens or links.
The phase remains observable internally through the immutable event kind. A useful authenticated
deep link can be added later using a trusted application origin and freshly authorized coordinates.

| Status | Meaning and retry behavior |
| --- | --- |
| PENDING | Durable intent awaits a bounded worker tick. |
| DISABLED | EmailManager returned false (disabled or incomplete settings); no attempt budget consumed, next check delayed. |
| FAILED | Explicit known non-delivery or recipient lookup failure; delayed automatic retry up to the configured budget. |
| UNCERTAIN | Send threw, returned unknown, process crashed during send, or result checkpoint did not complete. No automatic retry. |
| ACCEPTED | Transport acknowledged acceptance; this is not proof of inbox delivery. Replay does not send it again. |
| SUPPRESSED | No authorized mailbox, invalid mailbox, or operator cancellation; replay does not send it again. |

Before transport invocation, the outbox forces an in-flight checkpoint to disk. Startup changes any
leftover in-flight row to `UNCERTAIN`. SMTP cannot provide exactly-once delivery: loss of the server's
acceptance acknowledgement is indistinguishable from non-delivery. Existing `EmailManager` exceptions
do not reliably distinguish these cases, so its adapter conservatively marks every exception uncertain.
Only another transport with a proven non-acceptance result should return `RETRYABLE_FAILURE`.

`resolveUncertain` is a privileged integration API for operator reconciliation: confirm acceptance,
cancel, or explicitly retry with duplicate risk (fresh bounded budget). Protect and audit that action
at the service boundary. The ledger retains the resolution reason. Exhausted FAILED records remain
visible; a later operator retry API is intentionally outside this bounded slice.

The store uses forced snapshots and atomic replacement, rejects unsupported filesystems, and poisons
the live instance on an ambiguous storage error. Reopen and reconcile before more sends. A failed
checkpoint before send prevents delivery; a failed checkpoint after send leaves the durable in-flight
record for recovery. Capacity exhaustion never prunes deduplication records; raise capacity/replay or
replace the pilot store with a transactional database before sustained production use. Entire-snapshot
I/O is appropriate only for the bounded pilot. Use local storage with atomic rename semantics; network
filesystems, hardware power-loss guarantees and multi-host leases are outside the tested scope.

## Verification

`ReviewNotificationOutboxTest` uses fake transport only. Coverage includes durable intents, missing
post-commit wake-up/replay, per-recipient deduplication, conflicting events, disabled configuration,
bounded retry and batch fairness, crash/uncertain recovery and explicit reconciliation, restart,
concurrent dispatch/exclusive ownership, corruption and persistence failure, revoked access,
single-recipient validation, generic-content privacy and EmailManager adapter outcome classification.
No real mail, identity accounts or SMTP settings are touched.
