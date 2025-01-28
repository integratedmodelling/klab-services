package org.integratedmodelling.klab.services.runtime.api;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.neo4j.gis.spatial.index.curves.SpaceFillingCurve;

/**
 * All scalar sequences are compiled into one of these, optimized to be run in parallel whenever
 * possible.
 */
public interface ScalarComputation {

  /**
   * Create a scalar computation by adding consecutive scalar contextualizables. These should be
   * compiled into the fastest, one-shot computation and analyzed for potential of parallelization.
   */
  interface Builder {
    void add(Contextualizable contextualizable);

    ScalarComputation build();
  }

  /**
   * According to the return value, the buffer passed to {@link #run(Storage.Buffer)} may be a
   * partial or full buffer.
   *
   * @return
   */
  boolean isParallelizable();


  boolean run(Storage.Buffer storage);
}
