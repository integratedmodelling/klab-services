package org.integratedmodelling.klab.components;

import java.util.IdentityHashMap;
import org.slf4j.LoggerFactory;

/** Server-only evidence for independently owned public-provider bootstrap. */
public final class OwnedBootstrapDiagnostics {
  private OwnedBootstrapDiagnostics() {}
  public enum Phase { COMPONENT_LOAD, COMPONENT_EXPORT, COMPONENT_VERIFY, COMPONENT_INSTALL, AUTHORITY_CONFIGURATION }
  public enum Reason { EXCEPTION, REGISTRY_UNINITIALIZED, SERVICE_UNAVAILABLE, SOURCE_MISMATCH,
    MD5_MISMATCH, SHA256_MISMATCH, INSTALL_REJECTED, DEPENDENCY_REFRESH_REJECTED }

  /** Only canonical public component coordinates may cross this logging boundary. */
  public static String identifier(String value) {
    return value != null && value.matches("[A-Za-z0-9_.:@+\\-]{1,256}") ? value : "[redacted]";
  }
  public static void report(Phase phase, String component, Reason reason, Throwable original) {
    try {
      LoggerFactory.getLogger(OwnedBootstrapDiagnostics.class).error(
          "AUTHORITY_BOOTSTRAP_COMPONENT_FAILURE phase=" + phase + " component=" + identifier(component)
              + " reason=" + reason
              + (httpStatus(original) == null ? "" : " httpStatus=" + httpStatus(original)), snapshot(original));
    } catch (Throwable loggingUnavailable) { /* Preserve the original failure and cleanup. */ }
  }

  /** Preserve only a numeric transport status, never provider response bodies or request data. */
  static Integer httpStatus(Throwable original) {
    var seen = new IdentityHashMap<Throwable, Boolean>();
    for (Throwable current = original; current != null && seen.size() < 16; current = current.getCause()) {
      if (seen.put(current, true) != null) break;
      if (current instanceof org.integratedmodelling.common.utils.Utils.Http.Client.RequestFailure failure) {
        int status = failure.getStatus();
        return status >= 100 && status <= 599 ? status : null;
      }
    }
    return null;
  }

  /** Preserve original types, locations, causes and suppressed cleanup, without retaining secrets. */
  public static Throwable snapshot(Throwable original) {
    return snapshot(original, new IdentityHashMap<>(), 0);
  }

  private static Throwable snapshot(Throwable original, IdentityHashMap<Throwable, Throwable> seen,
      int depth) {
    if (original == null) return null;
    if (seen.containsKey(original)) return seen.get(original);
    if (depth >= 16 || seen.size() >= 128) return new Redacted("truncated");
    var result = new Redacted(original.getClass().getName());
    seen.put(original, result);
    var frames = original.getStackTrace();
    result.setStackTrace(java.util.Arrays.copyOf(frames, Math.min(128, frames.length)));
    var cause = snapshot(original.getCause(), seen, depth + 1);
    if (cause != result) result.initCause(cause);
    var suppressed = original.getSuppressed();
    for (int i = 0; i < Math.min(16, suppressed.length); i++) {
      var copy = snapshot(suppressed[i], seen, depth + 1);
      if (copy != result) result.addSuppressed(copy);
    }
    return result;
  }

  private static final class Redacted extends Exception {
    private final String originalType;
    Redacted(String originalType) {
      super(originalType + ": message redacted");
      this.originalType = originalType;
      setStackTrace(new StackTraceElement[0]);
    }
    @Override public String toString() { return originalType + ": message redacted"; }
  }
}
