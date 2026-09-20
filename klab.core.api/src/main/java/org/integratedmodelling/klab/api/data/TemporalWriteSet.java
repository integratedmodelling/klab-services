package org.integratedmodelling.klab.api.data;

import java.util.List;
import java.util.Set;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/** Transaction-owned temporal output, independent of the local or distributed storage backend. */
public interface TemporalWriteSet {
  enum Access {
    PRIOR,
    CURRENT,
    WRITE
  }

  <T extends Storage.Scanner> List<T> scan(
      Observation observation, Data.ShardingStrategy layout, Class<T> scannerType, Access access);

  boolean changed(Observation observation);

  Set<Observation> changedObservations();

  /** Flush immutable data and stage descriptors/coverage in the owning graph transaction. */
  void prepare();
}
