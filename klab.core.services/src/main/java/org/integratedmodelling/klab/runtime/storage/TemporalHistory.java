package org.integratedmodelling.klab.runtime.storage;

import java.util.List;

/** Committed version manifest. Event identity, not a timestamp, identifies a state revision. */
public record TemporalHistory(int version, List<Revision> revisions) {
  public static final String KEY = "klab.storage.history";
  public static final String EPHEMERAL = "klab.storage.ephemeral";
  public record Revision(String event, long start, long end, String support, List<String> shards) {
    public Revision {
      shards = List.copyOf(shards);
      if (event == null || event.isBlank() || end < start || support == null || support.isBlank()
          || shards.isEmpty() || new java.util.HashSet<>(shards).size() != shards.size())
        throw new IllegalArgumentException("Invalid temporal state revision");
    }
  }
  public TemporalHistory {
    if (version != 1) throw new IllegalArgumentException("Unsupported temporal history");
    revisions = List.copyOf(revisions);
    if (revisions.stream().map(Revision::event).distinct().count() != revisions.size())
      throw new IllegalArgumentException("Duplicate temporal event revision");
  }
}
