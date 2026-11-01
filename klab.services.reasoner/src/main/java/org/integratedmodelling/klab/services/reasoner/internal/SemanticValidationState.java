package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.*;

/**
 * Lock-free status signal for document validation. A settled revision identifies readable knowledge,
 * not a transactionally accepted or error-free worldview. Never publish a mutation as ready early.
 */
public final class SemanticValidationState {
  public static final String METADATA_KEY = "semantic.validation";
  private final String boot = UUID.randomUUID().toString();
  private volatile String signal = boot + ":-1:unavailable";
  private final Set<String> pendingUpdates = new HashSet<>();
  private boolean reloadRequired = true;

  public String signal() { return signal; }
  public boolean ready() { return signal.endsWith(":ready"); }
  public boolean loading() { return signal.endsWith(":loading"); }
  public synchronized void begin(long revision) {
    reloadRequired = true;
    signal = boot + ":" + revision + ":loading";
  }
  /** Full reload is the only operation that can clear every outstanding damaged item. */
  public synchronized void settle(long revision, boolean readable) {
    reloadRequired = !readable;
    if (readable) pendingUpdates.clear();
    signal = boot + ":" + revision + (readable ? ":ready" : ":failed");
  }

  public synchronized void beginUpdate(long revision, Set<String> affected) {
    pendingUpdates.addAll(affected);
    signal = boot + ":" + revision + ":loading";
  }

  public synchronized void settleUpdate(long revision, Set<String> affected, boolean readable) {
    if (readable) pendingUpdates.removeAll(affected);
    else if (affected.isEmpty()) reloadRequired = true; // Unattributed damage needs a full reload.
    signal = boot + ":" + revision
        + (readable && !reloadRequired && pendingUpdates.isEmpty() ? ":ready" : ":failed");
  }

  /** Successful lazy enrichment changes the revision but cannot repair failed ingestion. */
  public synchronized void enriched(long revision) {
    if (!loading()) signal = boot + ":" + revision + signal.substring(signal.lastIndexOf(':'));
  }
}
