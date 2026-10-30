package org.integratedmodelling.common.diagnostics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Opt-in observations only. No source, diagnostic message, reason or exception message is logged. */
public final class DocumentValidationTrace {
  public static final String PROPERTY = "klab.traceDocumentValidation";
  public enum Event {
    RESOURCES_SNAPSHOT, IDE_INPUT, IDE_RETAINED, IDE_INVALIDATED, IDE_DOCUMENT_ERRORS,
    IDE_RPC_SEND, IDE_RPC_RETURN, IDE_DELIVERY, IDE_PRESENTATION, IDE_RENDER, IDE_RENDER_RESPONSE,
    REASONER_RECEIVED, REASONER_RETURN, VALIDATION_EXCEPTION
  }
  public enum Decision { REPLACE, REUSE, OBSERVE, ACCEPT, DISCARD, GLOBAL_SAVE, SKIP_ERRORS, MASKED_EXCEPTION }
  private static final class State {
    static final String BOOT = UUID.randomUUID().toString();
    static final AtomicLong SEQUENCE = new AtomicLong();
    static final AtomicLong DROPPED = new AtomicLong();
    static final ArrayBlockingQueue<String> QUEUE = new ArrayBlockingQueue<>(512);
    static volatile boolean started;
  }
  private DocumentValidationTrace() {}
  public static boolean enabled() { return Boolean.getBoolean(PROPERTY); }

  public static void snapshot(Event event, KlabDocument<?> document, String version,
      long revision, long epoch, Decision decision) {
    if (!enabled()) return;
    try { enqueue(format(event, document, version, revision, revision, epoch, decision,
        document.getNotifications(), null, null)); }
    catch (Throwable ignored) { /* Tracing cannot alter validation or presentation. */ }
  }

  public static void response(Event event, SemanticValidationRequest request,
      SemanticValidationResponse response, long epoch, Decision decision) {
    if (!enabled()) return;
    try { enqueue(format(event, request.document(), request.getDocumentVersion(),
        request.getKnowledgeRevision(), response == null ? -1 : response.getKnowledgeRevision(),
        epoch, decision, response == null ? null : response.getNotifications(),
        response == null ? null : response.getStatus().name(),
        response == null ? null : response.getReason())); }
    catch (Throwable ignored) { /* Tracing cannot alter validation or presentation. */ }
  }

  public static void resources(ResourceSet.Resource resource, KlabDocument<?> document) {
    if (!enabled()) return;
    try {
      enqueue(format(Event.RESOURCES_SNAPSHOT, document, version(document), -1, -1, -1,
          Decision.OBSERVE, document.getNotifications(), null, null)
          + " op=" + token(String.valueOf(resource.getOperation()))
          + " resourceUrn=" + token(resource.getResourceUrn())
          + " resourceDiagnostics=" + diagnostics(resource.getNotifications()));
    } catch (Throwable ignored) { /* Tracing cannot alter Resources publication. */ }
  }

  public static void render(KlabDocument<?> document, SemanticValidationRequest request,
      SemanticValidationResponse response, String summary, int treeErrors, int treeWarnings) {
    if (!enabled()) return;
    try {
      enqueue(format(Event.IDE_RENDER, document, version(document), -1,
          response == null ? -1 : response.getKnowledgeRevision(), -1, Decision.OBSERVE,
          document.getNotifications(), null, null)
          + " presentationHash=" + hash(summary) + " treeErrors=" + treeErrors + " treeWarnings=" + treeWarnings);
      if (request != null) response(Event.IDE_RENDER_RESPONSE, request, response, -1, Decision.OBSERVE);
    } catch (Throwable ignored) { /* Tracing cannot alter the rendered cell. */ }
  }

  /** Original classes/frames, with messages hashed; never Throwable.toString/printStackTrace. */
  public static void exception(SemanticValidationRequest request, long revision, Throwable failure) {
    if (!enabled()) return;
    try {
      enqueue(formatFailure(request, revision, failure));
    } catch (Throwable ignored) { /* Preserve the original result/exception. */ }
  }
  static String formatFailure(SemanticValidationRequest request, long revision, Throwable failure) {
      var record = new StringBuilder(format(Event.VALIDATION_EXCEPTION, request.document(),
          request.getDocumentVersion(), request.getKnowledgeRevision(), revision, -1,
          Decision.MASKED_EXCEPTION, null, null, null));
      var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
      int causes = 0;
      while (failure != null && causes < 8 && seen.add(failure)) {
        record.append(" exception").append(causes).append('=').append(token(failure.getClass().getName()));
        record.append(" messageHash").append(causes).append('=').append(hash(failure.getMessage()));
        var stack = failure.getStackTrace();
        for (int frame = 0; frame < Math.min(48, stack.length); frame++)
          record.append(" frame").append(causes).append('_').append(frame).append('=')
              .append(token(stack[frame].toString()));
        failure = failure.getCause(); causes++;
      }
      return record.toString();
  }

  public static String version(KlabDocument<?> document) {
    var version = SemanticValidationRequest.sourceHash(document.getSourceCode()) + ":" + document.getLastUpdateTimestamp();
    if (org.integratedmodelling.klab.api.services.resources.AuthoringSource.id(document) != null)
      version += ":" + document.getMetadata().get(org.integratedmodelling.klab.api.services.resources.AuthoringSource.VERSION)
          + ":" + org.integratedmodelling.klab.api.services.resources.AuthoringSource.diagnosticVersion(document);
    return version;
  }
  static String format(Event event, KlabDocument<?> document, String version, long expected,
      long revision, long epoch, Decision decision, Collection<Notification> notifications,
      String status, String reason) {
    var sourceHash = SemanticValidationRequest.sourceHash(document.getSourceCode());
    var kind = KlabAsset.KnowledgeClass.classify(document.getClass()).name();
    var identity = document.getProjectName() + "\n" + kind + "\n" + document.getUrn() + "\n" + sourceHash;
    if (org.integratedmodelling.klab.api.services.resources.AuthoringSource.id(document) != null)
      identity += "\n" + org.integratedmodelling.klab.api.services.resources.AuthoringSource.key(document);
    return "DOCUMENT_VALIDATION_TRACE v=1 boot=" + State.BOOT + " seq=" + State.SEQUENCE.incrementAndGet()
        + " at=" + Instant.now() + " event=" + event + " decision=" + decision
        + " project=" + token(document.getProjectName()) + " kind=" + kind + " urn=" + token(document.getUrn())
        + " source=" + sourceHash + " doc=" + hash(identity)
        + " version=" + hash(version) + " request=" + hash(identity + "\n" + version + "\n" + expected)
        + " updated=" + document.getLastUpdateTimestamp() + " expected=" + expected
        + " revision=" + revision + " epoch=" + epoch + " status=" + token(status)
        + " reasonHash=" + hash(reason) + " diagnostics=" + diagnostics(notifications);
  }
  static String diagnostics(Collection<Notification> notifications) {
    int errors = 0, warnings = 0, other = 0;
    var canonical = new StringBuilder();
    if (notifications != null) for (var notification : notifications) {
      if (notification.getLevel() == Notification.Level.Error || notification.getLevel() == Notification.Level.SystemError) errors++;
      else if (notification.getLevel() == Notification.Level.Warning) warnings++;
      else other++;
      canonical.append(notification.getLevel()).append(':').append(hash(notification.getMessage()));
      var context = notification.getLexicalContext();
      if (context != null) canonical.append('@').append(hash(context.getDocumentUrn())).append(':')
          .append(hash(context.getProjectUrn())).append(':').append(context.getOffsetInDocument())
          .append(':').append(context.getLength());
      canonical.append('\n');
    }
    return errors + ":" + warnings + ":" + other + ":" + hash(canonical.toString());
  }
  static String hash(String value) {
    if (value == null) return "-";
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception e) { return "unavailable"; }
  }
  private static String token(String value) {
    if (value == null) return "-";
    // One line, bounded identifiers only; no executable/log-control characters.
    return value.substring(0, Math.min(240, value.length())).replaceAll("[^A-Za-z0-9_.:/@$()<>-]", "_");
  }
  private static void enqueue(String line) {
    if (!State.QUEUE.offer(line)) State.DROPPED.incrementAndGet();
    if (!State.started) synchronized (State.class) {
      if (!State.started) {
        var writer = new Thread(() -> {
          while (true) try {
            var record = State.QUEUE.take();
            long lost = State.DROPPED.getAndSet(0);
            Logging.INSTANCE.info(record + " dropped=" + lost);
          } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
          catch (Throwable ignored) { /* Diagnostics must not break the writer or callers. */ }
        }, "document-validation-trace-writer");
        writer.setDaemon(true); State.started = true; writer.start();
      }
    }
  }
}
