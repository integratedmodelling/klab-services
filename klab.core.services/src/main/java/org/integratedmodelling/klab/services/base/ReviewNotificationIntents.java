package org.integratedmodelling.klab.services.base;

import java.io.*;
import java.util.*;

/**
 * Server-owned durable intents embedded in a workflow transaction's metadata BEFORE putFlow.
 * The existing Flow.Transaction metadata is JSON-compatible and persists with its history entry.
 * Never accept this reserved key or recipients from request metadata. A workflow adapter must
 * authorize the phase and derive/freeze recipient IDs, then persist the intent and transition in
 * the same aggregate write. After commit, replay these intents into the independent outbox.
 */
public final class ReviewNotificationIntents {
  public static final String METADATA_KEY = "klab.review.notificationIntents.v1";
  private ReviewNotificationIntents() {}

  public static void stage(Map<String, Object> serverTransactionMetadata, ReviewNotificationOutbox.Event event) {
    var existing = read(serverTransactionMetadata);
    for (var item : existing) if (item.id().equals(event.id())) {
      if (!item.equals(event)) throw new IllegalArgumentException("Conflicting notification intent");
      return;
    }
    if (existing.size() >= 16) throw new IllegalArgumentException("Too many notification intents");
    var encoded = new ArrayList<String>();
    existing.forEach(item -> encoded.add(encode(item)));
    encoded.add(encode(event));
    serverTransactionMetadata.put(METADATA_KEY, List.copyOf(encoded));
  }

  public static List<ReviewNotificationOutbox.Event> read(Map<String, Object> committedTransactionMetadata) {
    var value = committedTransactionMetadata.get(METADATA_KEY);
    if (value == null) return List.of();
    if (!(value instanceof List<?> list) || list.size() > 16)
      throw new IllegalArgumentException("Invalid notification intents");
    var events = new ArrayList<ReviewNotificationOutbox.Event>();
    for (var item : list) {
      if (!(item instanceof String encoded) || encoded.length() > 200000)
        throw new IllegalArgumentException("Invalid notification intent");
      try (var in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
        String id = in.readUTF(), flow = in.readUTF(), stage = in.readUTF();
        var kind = ReviewNotificationOutbox.Kind.valueOf(in.readUTF());
        int count = in.readInt();
        if (count < 0 || count > 256) throw new IOException("Invalid recipient count");
        var recipients = new ArrayList<String>();
        for (int i = 0; i < count; i++) recipients.add(in.readUTF());
        var event = new ReviewNotificationOutbox.Event(id, flow, stage, kind, recipients);
        if (in.read() != -1 || events.stream().anyMatch(e -> e.id().equals(event.id())))
          throw new IOException("Invalid notification intent");
        events.add(event);
      } catch (IOException | IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid notification intent", e);
      }
    }
    return List.copyOf(events);
  }

  private static String encode(ReviewNotificationOutbox.Event event) {
    try {
      var bytes = new ByteArrayOutputStream();
      try (var out = new DataOutputStream(bytes)) {
        out.writeUTF(event.id()); out.writeUTF(event.flowId()); out.writeUTF(event.stageId());
        out.writeUTF(event.kind().name()); out.writeInt(event.recipients().size());
        for (var recipient : event.recipients()) out.writeUTF(recipient);
      }
      return Base64.getEncoder().encodeToString(bytes.toByteArray());
    } catch (IOException e) { throw new IllegalStateException("Cannot encode notification intent", e); }
  }
}
