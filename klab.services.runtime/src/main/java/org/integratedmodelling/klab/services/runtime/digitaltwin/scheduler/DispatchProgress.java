package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

/** Committed registration cursor, stored atomically with its journal and computation results. */
public record DispatchProgress(int version, String registration, String revision, long through,
    String eventId, long requestedThrough) {
  public DispatchProgress(int version, String registration, String revision, long through, String eventId) {
    this(version, registration, revision, through, eventId, through);
  }
  public static final String KEY = "klab.scheduler.progress";
  public DispatchProgress {
    if (version != 1 || registration == null || revision == null || eventId == null)
      throw new IllegalArgumentException("Invalid temporal dispatch progress");
  }
}
