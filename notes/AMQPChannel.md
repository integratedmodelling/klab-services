### What I changed and why

- Investigated the current `AMQPChannel` behavior and identified three key problems preventing the expected pub-sub
  semantics:
    1. Self-message filtering was commented out, so senders received their own messages.
    2. Messages were filtered by `Channel.getDispatchId()` vs `Message.getDispatchId()`, which caused receivers to
       accept only messages that looked like they came from themselves (i.e., effectively only self-messages).
    3. A `FANOUT` exchange was used but messages were published using the consumer queue name as a routing key, and
       queues were bound using that same key. With `FANOUT`, routing keys are ignored; while this didn’t break delivery
       by itself, it obscured the intended broadcast semantics and helped mask the real issues.

- Implemented the following fixes in `AMQPChannel`:
    - Re-enabled self-message filtering using a header. Each channel now sets a `channelId` header (per
      connection/channel instance) and drops deliveries whose `channelId` equals the local channel’s tag.
    - Removed the `dispatchId`-based acceptance filter so that all instances bound to the same exchange receive each
      other’s messages. This was the main blocker to cross-instance delivery.
    - Published to the `FANOUT` exchange with an empty routing key (explicitly signaling that routing key is
      irrelevant), and bound consumer queues to the exchange also with an empty key for clarity.
    - Removed stray noisy logging.

- Resulting behavior:
    - All instances using the same `exchangeId` (and connected to the same broker) receive each message exactly once via
      their own auto-declared exclusive queue.
    - The sender does not receive its own message (header-based self-filter).

### Files changed

- `klab.core.common/src/main/java/org/integratedmodelling/common/authentication/scope/AMQPChannel.java`
    - Propagate a per-channel `channelId` header when publishing.
    - Use empty routing key for `FANOUT` publish and bind.
    - Reinstate self-message filtering from delivery headers.
    - Remove `dispatchId`-based message filtering.
    - Remove stray debug log.

### Build status

- Full project build completed successfully.

### Notes and follow-ups

- If you later need directed routing (by `dispatchId` or other criteria), that should be implemented with a `direct` or
  `topic` exchange and appropriate routing keys, not via filtering after receipt.
- The `close()` logic remains as-is; if you want lifecycle/persistence cleanup to match specific federation/context
  persistence rules across exchanges/queues, that can be addressed separately.