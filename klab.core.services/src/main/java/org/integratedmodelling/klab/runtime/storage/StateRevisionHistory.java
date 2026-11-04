package org.integratedmodelling.klab.runtime.storage;

import java.util.*;

/** Immutable untimed state ancestry. No entry denotes or advances modeled time. */
public record StateRevisionHistory(int version, List<Revision> revisions) {
  public static final String KEY = "klab.storage.stateRevisions";

  public record Revision(String id, String previousId, String support, List<String> shards) {
    public Revision {
      shards = List.copyOf(shards);
      if (id == null
          || id.isBlank()
          || support == null
          || support.isBlank()
          || shards.isEmpty()
          || new HashSet<>(shards).size() != shards.size())
        throw new IllegalArgumentException("Invalid state revision");
    }
  }

  public StateRevisionHistory {
    if (version != 1) throw new IllegalArgumentException("Unsupported state history");
    revisions = List.copyOf(revisions);
    var ids = new HashSet<String>();
    String previous = null;
    for (var revision : revisions) {
      if (!ids.add(revision.id()) || !Objects.equals(previous, revision.previousId()))
        throw new IllegalArgumentException("Broken state revision ancestry");
      previous = revision.id();
    }
  }
}
