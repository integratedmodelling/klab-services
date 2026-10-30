package org.integratedmodelling.common.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;

/** Opt-in terminal-result diagnostics. Never logs headers, semantic text or exception messages. */
public final class SubmissionDiagnostics {
  public static final String PROPERTY = "klab.diagnostics.submissionOutcome";
  public static final String ENVIRONMENT = "KLAB_DIAGNOSTICS_SUBMISSION_OUTCOME";

  private SubmissionDiagnostics() {}

  public static boolean enabled() {
    try {
      var configured = System.getProperty(PROPERTY);
      return Boolean.parseBoolean(configured == null ? System.getenv(ENVIRONMENT) : configured);
    } catch (SecurityException unavailable) { return false; }
  }

  /** Return the exact input result even when diagnostic inspection or logging fails. */
  public static Observation result(String origin, ContextScope scope, Observation request,
      Observation result, Long jobId, Throwable failure) {
    try {
      var summary = summary(origin, scope, request, result, jobId, failure);
      if (summary != null) Logging.INSTANCE.info("SUBMISSION_DIAGNOSTIC " + summary);
    } catch (Throwable ignored) {
      // Diagnostic failure must not change completion, cancellation or the returned observation.
    }
    return result;
  }

  /** Bounded sanitized fields suitable for an existing test-report diagnostic. */
  public static String summary(String origin, ContextScope scope, Observation request,
      Observation result, Long jobId, Throwable failure) {
    if (!enabled()) return null;
    try {
      var fields = snapshot(origin, request, result, jobId, failure);
      fields.put("scopeHash", scope == null ? null : fingerprint(scope.getId()));
      return Utils.Json.asString(fields);
    } catch (Throwable ignored) { return null; }
  }

  static Map<String, Object> snapshot(String origin, Observation request, Observation result,
      Long jobId, Throwable failure) {
    var fields = new LinkedHashMap<String, Object>();
    fields.put("origin", origin);
    fields.put("jobId", jobId);
    fields.put("requestTransientId", request == null ? null : request.getTransientId());
    fields.put("requestHash", request == null || request.getObservable() == null ? null
        : fingerprint(request.getObservable().getUrn()));
    fields.put("resultPresent", result != null);
    fields.put("deliveryState", failure != null ? "EXCEPTION" : result == null ? "NO_RESULT" : "RESULT");
    if (result != null) {
      fields.put("resultId", result.getId());
      fields.put("empty", result.isEmpty());
      fields.put("applicationResult", result.isEmpty() ? "EMPTY" : "NONEMPTY");
      var observable = result.getObservable();
      fields.put("hasObservable", observable != null);
      fields.put("hasSemantics", observable != null && observable.getSemantics() != null);
      fields.put("collective", observable != null && observable.getSemantics() != null
          && observable.getSemantics().isCollective());
      fields.put("hasGeometry", result.getGeometry() != null);
      fields.put("resolvedCoverage", Double.isFinite(result.getResolvedCoverage())
          ? result.getResolvedCoverage() : "nonfinite");
      var notifications = new ArrayList<Map<String, Object>>();
      var values = result.getNotifications();
      fields.put("notificationCount", values == null ? 0 : values.size());
      if (values != null) for (var notification : values) {
        if (notifications.size() == 8) break;
        if (notification == null) continue;
        var item = new LinkedHashMap<String, Object>();
        item.put("level", notification.getLevel() == null ? null : notification.getLevel().name());
        item.put("code", messageCode(notification.getMessage()));
        item.put("messageHash", fingerprint(notification.getMessage()));
        notifications.add(item);
      }
      fields.put("notifications", notifications);
    }
    if (failure != null) {
      var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
      while (failure.getCause() != null && seen.add(failure) && seen.size() < 8)
        failure = failure.getCause();
      fields.put("failureType", failure.getClass().getName());
      for (var frame : failure.getStackTrace()) {
        if (frame.getClassName().startsWith("org.integratedmodelling.")) {
          fields.put("failureSite", frame.getClassName() + "." + frame.getMethodName()
              + ":" + frame.getLineNumber());
          break;
        }
      }
    }
    return fields;
  }

  private static String messageCode(String message) {
    if (message == null) return "NO_MESSAGE";
    if (message.startsWith("Resolution of ") && message.endsWith(" failed: empty dataflow"))
      return "RESOLUTION_EMPTY_DATAFLOW_OR_COMPILE_COMMIT";
    if (message.startsWith("Contextualization of ") && message.endsWith(" failed"))
      return "CONTEXTUALIZATION_FAILED";
    if (message.startsWith("Commit of ") && message.endsWith(" failed")) return "COMMIT_FAILED";
    if (message.equals("Reference submission commit failed")) return "REFERENCE_COMMIT_FAILED";
    if (message.equals("Submission failed")) return "SUBMISSION_FAILED";
    return "OTHER_MESSAGE";
  }

  private static String fingerprint(String value) {
    if (value == null) return null;
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
