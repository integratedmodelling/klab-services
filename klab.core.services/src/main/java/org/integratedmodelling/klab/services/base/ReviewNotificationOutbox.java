package org.integratedmodelling.klab.services.base;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/**
 * Bounded, service-private pilot outbox. No SMTP is performed by enqueue. Feed only committed,
 * replayable workflow facts; replay after startup and periodically to repair the commit/enqueue gap.
 * One owner holds an OS file lock for the lifetime of this store. Never place it in a project/Git
 * directory. This API deliberately has no workflow- or user-facing endpoint.
 */
public final class ReviewNotificationOutbox implements AutoCloseable {
  public enum Kind { ROUND_OPENED, CONTRIBUTION_SUBMITTED, CANDIDATE_INTEGRATED, ROUND_CLOSED, CYCLE_CLOSED }
  public enum Status { PENDING, DISABLED, FAILED, UNCERTAIN, ACCEPTED, SUPPRESSED }
  public enum Result { ACCEPTED, DISABLED, RETRYABLE_FAILURE, UNCERTAIN }
  public enum Resolution { CONFIRMED_ACCEPTED, RETRY_WITH_DUPLICATE_RISK, CANCEL }

  /** Opaque identifiers only: never include proposal text, comments, names, email or access tokens. */
  public record Event(String id, String flowId, String stageId, Kind kind, List<String> recipients) {
    public Event {
      token(id); token(flowId); token(stageId); Objects.requireNonNull(kind);
      recipients = List.copyOf(new TreeSet<>(Objects.requireNonNull(recipients)));
      if (recipients.size() > 256) throw new IllegalArgumentException("Too many recipients");
      recipients.forEach(ReviewNotificationOutbox::token);
    }
  }

  /** A single current, authorized mailbox. Implementations must fail closed on missing identity. */
  @FunctionalInterface public interface RecipientPolicy {
    Optional<String> authorizedMailbox(Event event, String recipientIdentity);
  }

  /** RETRYABLE_FAILURE promises that no message was accepted; exceptions mean UNCERTAIN. */
  @FunctionalInterface public interface Transport {
    Result send(String mailbox, String subject, String body);
  }

  /** Safe operational view; email addresses, bodies and raw transport exceptions are never stored. */
  public record Delivery(String eventId, String recipientIdentity, Status status, int attempts,
                         Instant nextAttemptAt, String reason) {}
  private record Key(String event, String recipient) {}
  private record Snapshot(Map<String, Event> events, Map<Key, Delivery> deliveries) {}
  private static final int MAGIC = 0x4f555431;
  private final Path file;
  private final FileChannel lockChannel;
  private final FileLock lock;
  private final Clock clock;
  private final int capacity;
  private final int maxAttempts;
  private final Duration retryDelay;
  private Snapshot state = new Snapshot(new LinkedHashMap<>(), new LinkedHashMap<>());
  private boolean closed;
  private boolean poisoned;

  public ReviewNotificationOutbox(Path directory, Clock clock, int capacity, int maxAttempts,
                                  Duration retryDelay) throws IOException {
    this.clock = Objects.requireNonNull(clock);
    if (capacity < 1 || capacity > 100000 || maxAttempts < 1 || maxAttempts > 100
        || retryDelay.isNegative() || retryDelay.isZero()) throw new IllegalArgumentException("Invalid limits");
    this.capacity = capacity;
    this.maxAttempts = maxAttempts;
    this.retryDelay = retryDelay;
    Files.createDirectories(directory);
    file = directory.resolve("review-notifications.bin");
    lockChannel = FileChannel.open(directory.resolve("review-notifications.lock"),
        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    FileLock acquired;
    try {
      acquired = lockChannel.tryLock();
      if (acquired == null) throw new IOException("Notification store already owned");
    } catch (RuntimeException | IOException e) {
      lockChannel.close();
      throw new IOException("Cannot own notification store", e);
    }
    lock = acquired;
    try {
      if (Files.exists(file)) state = read();
      var recovered = copy();
      // No automatic resend after a crash between send and the acceptance checkpoint.
      recovered.deliveries.replaceAll((key, value) -> "IN_FLIGHT".equals(value.reason())
          ? delivery(value, Status.UNCERTAIN, value.attempts(), "CRASH_DURING_DELIVERY") : value);
      if (!recovered.equals(state)) commit(recovered);
    } catch (IOException | RuntimeException e) {
      close();
      throw e;
    }
  }

  /** Idempotent replay; conflicting reuse of an event ID fails rather than widening its audience. */
  public synchronized boolean enqueueCommitted(Event event) throws IOException {
    requireOpen(); Objects.requireNonNull(event);
    var existing = state.events.get(event.id());
    if (existing != null) {
      if (!existing.equals(event)) throw new IllegalArgumentException("Conflicting committed event");
      return false;
    }
    if (state.events.size() >= capacity || state.deliveries.size() + event.recipients().size() > capacity)
      throw new IOException("Notification capacity reached; retain committed source for replay");
    var next = copy();
    next.events.put(event.id(), event);
    for (var recipient : event.recipients()) next.deliveries.put(new Key(event.id(), recipient),
        new Delivery(event.id(), recipient, Status.PENDING, 0, clock.instant(), "QUEUED"));
    commit(next);
    return true;
  }

  /**
   * Explicit worker tick, bounded by recipient count. Never call from a workflow transaction.
   * Rechecks authority at delivery time. Holding the store lock prevents overlapping workers.
   * Storage errors propagate to the worker; they must never be translated into workflow failure.
   */
  public synchronized int dispatch(int limit, RecipientPolicy policy, Transport transport) throws IOException {
    requireOpen(); Objects.requireNonNull(policy); Objects.requireNonNull(transport);
    if (limit < 1 || limit > 256) throw new IllegalArgumentException("Invalid batch size");
    int processed = 0;
    var keys = state.deliveries.entrySet().stream()
        .sorted(Comparator.comparing((Map.Entry<Key, Delivery> entry) -> entry.getValue().nextAttemptAt())
            .thenComparing(entry -> entry.getKey().event).thenComparing(entry -> entry.getKey().recipient))
        .map(Map.Entry::getKey).toList();
    for (var key : keys) {
      var item = state.deliveries.get(key);
      if (processed == limit) break;
      if (!(item.status() == Status.PENDING || item.status() == Status.DISABLED || item.status() == Status.FAILED)
          || item.attempts() >= maxAttempts || item.nextAttemptAt().isAfter(clock.instant())) continue;
      processed++;
      Optional<String> mailbox;
      try {
        mailbox = Objects.requireNonNull(policy.authorizedMailbox(state.events.get(key.event), key.recipient));
      } catch (RuntimeException e) {
        update(key, delivery(item, Status.FAILED, item.attempts() + 1, "RECIPIENT_LOOKUP_FAILED"));
        continue;
      }
      if (mailbox.isEmpty()) {
        update(key, delivery(item, Status.SUPPRESSED, item.attempts(), "NO_CURRENT_AUTHORIZED_MAILBOX"));
        continue;
      }
      if (!singleMailbox(mailbox.get())) {
        update(key, delivery(item, Status.SUPPRESSED, item.attempts(), "INVALID_MAILBOX"));
        continue;
      }
      var flight = delivery(item, Status.UNCERTAIN, item.attempts() + 1, "IN_FLIGHT");
      update(key, flight); // Durable BEFORE the external side effect.
      Result result;
      try {
        result = Objects.requireNonNull(transport.send(mailbox.get(), "Ontology review update",
            "An ontology review workflow update is available. Sign in to the review application to view authorized details."));
      } catch (RuntimeException e) {
        result = Result.UNCERTAIN;
      }
      var status = switch (result) {
        case ACCEPTED -> Status.ACCEPTED;
        case DISABLED -> Status.DISABLED;
        case RETRYABLE_FAILURE -> Status.FAILED;
        case UNCERTAIN -> Status.UNCERTAIN;
      };
      // Disabled configuration consumes no delivery attempts, but is rate limited.
      update(key, delivery(item, status, result == Result.DISABLED ? item.attempts() : flight.attempts(), result.name()));
    }
    return processed;
  }

  /** Privileged operator action after reconciliation, never automatic. Audit separately at API boundary. */
  public synchronized void resolveUncertain(String event, String recipient, Resolution resolution) throws IOException {
    requireOpen(); Objects.requireNonNull(resolution);
    var key = new Key(event, recipient);
    var item = state.deliveries.get(key);
    if (item == null || item.status() != Status.UNCERTAIN) throw new IllegalArgumentException("Not uncertain");
    var status = switch (resolution) {
      case CONFIRMED_ACCEPTED -> Status.ACCEPTED;
      case RETRY_WITH_DUPLICATE_RISK -> Status.PENDING;
      case CANCEL -> Status.SUPPRESSED;
    };
    // An explicit retry starts a fresh bounded attempt budget.
    update(key, delivery(item, status, status == Status.PENDING ? 0 : item.attempts(), resolution.name()));
  }

  public synchronized List<Delivery> deliveries() {
    requireOpen(); return List.copyOf(state.deliveries.values());
  }

  private Delivery delivery(Delivery item, Status status, int attempts, String reason) {
    return new Delivery(item.eventId(), item.recipientIdentity(), status, attempts,
        clock.instant().plus(retryDelay), reason);
  }

  private void update(Key key, Delivery value) throws IOException {
    var next = copy(); next.deliveries.put(key, value); commit(next);
  }

  private Snapshot copy() {
    return new Snapshot(new LinkedHashMap<>(state.events), new LinkedHashMap<>(state.deliveries));
  }

  private void commit(Snapshot next) throws IOException {
    var temporary = file.resolveSibling("review-notifications.tmp");
    try {
      try (var channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
           var out = new DataOutputStream(java.nio.channels.Channels.newOutputStream(channel))) {
        out.writeInt(MAGIC);
        out.writeInt(next.events.size());
        for (var event : next.events.values()) {
          out.writeUTF(event.id()); out.writeUTF(event.flowId()); out.writeUTF(event.stageId());
          out.writeUTF(event.kind().name()); out.writeInt(event.recipients().size());
          for (var recipient : event.recipients()) out.writeUTF(recipient);
        }
        out.writeInt(next.deliveries.size());
        for (var item : next.deliveries.values()) {
          out.writeUTF(item.eventId()); out.writeUTF(item.recipientIdentity()); out.writeUTF(item.status().name());
          out.writeInt(item.attempts()); out.writeLong(item.nextAttemptAt().toEpochMilli()); out.writeUTF(item.reason());
        }
        out.flush(); channel.force(true);
      }
      // Refuse unsupported filesystems: non-atomic replacement could lose the deduplication ledger.
      Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      state = next;
    } catch (IOException e) {
      poisoned = true; // Reopen/reconcile; do not send after an ambiguous persistence outcome.
      throw e;
    }
  }

  private Snapshot read() throws IOException {
    var loaded = new Snapshot(new LinkedHashMap<>(), new LinkedHashMap<>());
    try (var in = new DataInputStream(Files.newInputStream(file))) {
      if (in.readInt() != MAGIC) throw new IOException("Unknown notification store format");
      int events = count(in.readInt(), capacity);
      for (int i = 0; i < events; i++) {
        String id = in.readUTF(), flow = in.readUTF(), stage = in.readUTF();
        var kind = Kind.valueOf(in.readUTF());
        int n = count(in.readInt(), 256);
        var recipients = new ArrayList<String>();
        for (int j = 0; j < n; j++) recipients.add(in.readUTF());
        if (loaded.events.put(id, new Event(id, flow, stage, kind, recipients)) != null)
          throw new IOException("Duplicate event");
      }
      int rows = count(in.readInt(), capacity);
      for (int i = 0; i < rows; i++) {
        String event = in.readUTF(), recipient = in.readUTF();
        var item = new Delivery(event, recipient, Status.valueOf(in.readUTF()),
            count(in.readInt(), 100), Instant.ofEpochMilli(in.readLong()), in.readUTF());
        if (!loaded.events.containsKey(event) || !loaded.events.get(event).recipients().contains(recipient)
            || loaded.deliveries.put(new Key(event, recipient), item) != null) throw new IOException("Invalid delivery");
      }
      if (in.read() != -1 || loaded.events.values().stream().mapToInt(e -> e.recipients().size()).sum() != rows)
        throw new IOException("Invalid notification ledger");
    } catch (IllegalArgumentException e) { throw new IOException("Invalid notification ledger", e); }
    return loaded;
  }

  private static int count(int value, int max) throws IOException {
    if (value < 0 || value > max) throw new IOException("Invalid notification count"); return value;
  }

  private static void token(String value) {
    if (value == null || !value.matches("[A-Za-z0-9_.:/-]{1,256}"))
      throw new IllegalArgumentException("An opaque identifier is required");
  }

  private static boolean singleMailbox(String value) {
    return value.length() <= 254 && value.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");
  }

  private void requireOpen() {
    if (closed || poisoned) throw new IllegalStateException("Notification store must be reopened");
  }

  @Override public synchronized void close() throws IOException {
    if (!closed) { closed = true; try { lock.release(); } finally { lockChannel.close(); } }
  }
}
