package org.integratedmodelling.common.diagnostics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;

/** Optional correlation for observable building and strategy computation. */
public final class SemanticOperationTrace {
  public static final String PROPERTY = "klab.profile.semanticOperations";
  public static final String HEADER = "X-Klab-Semantic-Trace";
  private static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  private SemanticOperationTrace() {}

  public static boolean enabled(String operation) {
    return ENABLED && ("buildObservable".equals(operation)
        || "computeObservationStrategies".equals(operation));
  }

  public static String currentId(String operation) {
    return enabled(operation) ? CURRENT.get() : null;
  }

  public static <T> T inbound(String id, Supplier<T> call) {
    if (!ENABLED || id == null || id.length() != 36) return call.get();
    String parsed;
    try {
      parsed = UUID.fromString(id).toString();
    } catch (IllegalArgumentException invalid) {
      return call.get();
    }
    var previous = CURRENT.get();
    CURRENT.set(parsed);
    try {
      return call.get();
    } finally {
      restore(previous);
    }
  }

  public static ClientCall client(String operation) {
    return enabled(operation) ? new ClientCall(operation) : null;
  }

  public static final class ClientCall implements AutoCloseable {
    private final String operation;
    private final String id = UUID.randomUUID().toString();
    private final String previous = CURRENT.get();
    private final String caller;
    private final long started;
    private boolean completed;

    private ClientCall(String operation) {
      this.operation = operation;
      this.caller = StackWalker.getInstance().walk(frames -> frames
          .filter(frame -> {
            var type = frame.getClassName();
            return type.startsWith("org.integratedmodelling.klab.services.")
                || type.startsWith("org.integratedmodelling.klab.runtime.");
          })
          .map(frame -> frame.getClassName() + "." + frame.getMethodName())
          .findFirst().orElse(null));
      started = System.nanoTime();
      CURRENT.set(id);
    }

    public void completed() {
      completed = true;
    }

    @Override
    public void close() {
      long elapsed = System.nanoTime() - started;
      restore(previous);
      var fields = new LinkedHashMap<String, Object>();
      fields.put("id", id);
      fields.put("operation", operation);
      fields.put("caller", caller);
      fields.put("elapsedNanos", elapsed);
      fields.put("completed", completed);
      emit("SEMANTIC_OPERATION_CLIENT", fields);
    }
  }

  public static void emit(String event, Map<String, ?> fields) {
    try {
      Logging.INSTANCE.info(event + " " + Utils.Json.asString(fields));
    } catch (RuntimeException ignored) {
      // Profiling must not affect the semantic call.
    }
  }

  private static void restore(String previous) {
    if (previous == null) CURRENT.remove();
    else CURRENT.set(previous);
  }
}
