package org.integratedmodelling.klab.api.digitaltwin;

import java.io.Serializable;
import java.util.List;

/** Fetchable committed quality support, stored atomically alongside the completion journal. */
public record TemporalStateDelta(
    int version, long observationId, String eventId, String support, List<String> shards)
    implements Serializable {
  public static final String METADATA_KEY = "klab.scheduler.qualityDeltas";

  public TemporalStateDelta {
    if (version != 1
        || observationId <= 0
        || eventId == null
        || eventId.isBlank()
        || support == null
        || support.isBlank()) throw new IllegalArgumentException("Invalid temporal quality delta");
    shards = List.copyOf(shards);
    if (shards.isEmpty()) throw new IllegalArgumentException("Temporal quality delta has no data");
  }
}
