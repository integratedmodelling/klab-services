# Experimental retained Job notification reference

**SIMULATED_PROVISIONAL — JAVA-GAP-0005, native contract UNKNOWN_PENDING_REFERENCE.**
This opt-in development protocol is separate from `ActivityNotifications` transient
AMQP hints. It neither changes AMQP delivery semantics nor claims a supported
native Java replay endpoint. The SDK has no simulator import/dependency or fallback.

```python
from klab_client import read_notifications, NotificationCursor

checkpoint = read_notifications(context, allow_experimental=True).cursor
# Perform explicit owned operations; reference emits finite Job lifecycle hints.
batch = read_notifications(context, checkpoint, allow_experimental=True, limit=64)
for event in batch.events:
    process_idempotently(event)
# Caller persists ONLY after processing. The SDK never advances this checkpoint.
checkpoint = NotificationCursor.from_dict(batch.cursor.to_dict())
```

`async_read_notifications` uses native async transport with the same schema. The
only proposed route is GET `/experimental/notifications/v1/events` under the bound
Runtime; `protocol=klab.experimental.notifications/1`, `limit=1..64`, and optional
`epoch`+`after`. Every read reuses current configured credentials and exact root
scope/Runtime binding. Foreign scopes/credentials are denied. No redirects,
mutation retry, background consumer, broker provisioning or HTTP→AMQP fallback.

Response fields: `protocol`, `epoch`, positive integer `first`/`next`, `state`,
`events`, `coverage`. Events have contiguous exact `sequence`, bounded `job_id`,
`service` and kind JOB_WAITING/STARTED/FINISHED/ABORTED/INTERRUPTED. Cursor records
Runtime URL/ID/root scope/epoch/sequence, never credentials. All response bytes are
bounded64KiB and decoded as strict UTF-8 JSON; duplicate keys/nonfinite literals
refuse. Immutable batches can be replayed by reusing a checkpoint; consumers must
tolerate duplicates, not assume exactly-once processing.

The first explicit read establishes a **tail** cursor, not fabricated history.
Coverage is always `SINCE_EXPLICIT_REFERENCE_ACTIVATION_ONLY`. Only subsequent
finite Job transitions are recorded (no full provenance, payload/data changes,
wall-clock scheduling, scientific result or complete graph convergence claim).
The simulator retains128events per activated context; overflow/epoch loss or future
cursor returns RESYNC_REQUIRED and no partial history. The SDK raises
`NotificationResyncRequired` without advancing anything. Explicitly refetch needed
authoritative state and then decide whether to establish a new tail cursor; never
silently reset a cursor or substitute synthetic state after a real-service error.

Opt-in owned SQLite checkpoints retain this reference epoch/event history across
simulator restart. Old checkpoints preserve their existing Jobs/scopes but start
with inactive event logs; pre-activation history is UNKNOWN, not synthesized.
Released contexts remove their retained logs. This is not a maintained native
retention/incarnation/authorization/revocation guarantee; issue126 remains OPEN.
