package org.integratedmodelling.klab.api.services.runtime;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.lang.Contextualizable;

/**
 * During contextualization, any sequence of scalar operations is compiled into one of these. The
 * API is meant to be run in parallel whenever possible by mapping the computation on a {@link
 * Storage.Buffer}. Implementations may use local buffers or build parallel pipelines backed by
 * Spark or other distributed computational engines. In general the computation should be compiled
 * into the fastest-executing strategy, when possible independent from the context so that it can be
 * processed remotely.
 */
public interface ScalarComputation {

  /**
   * Create a scalar computation by adding consecutive scalar contextualizables. These should be
   * compiled into the fastest, one-shot computation and analyzed for potential of parallelization.
   */
  interface Builder {

    /**
     * Add a contextualizable to the computation. Must be a scalar contextualizable or an exception
     * will be thrown. The types of contextualizables normally used with the builder are: constants,
     * expressions for calculation or integration, lookup tables and classifications, contextual or
     * non-contextual value mediators, and scalar contextualizers from adapters and functors.
     *
     * @param contextualizable
     * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException if the
     *     contextualizable is not suited for scalar computation.
     */
    void add(Contextualizable contextualizable);

    /**
     * Create the final computation, which must know its scales and context so that it can be run
     * without further parameters than the data buffer.
     *
     * @return
     */
    ScalarComputation build();
  }

  /**
   * This should be called before execution to assess the level of parallelism that the computation
   * enables.
   *
   * <p>According to the return value, the buffer passed to {@link #run(Storage.Buffer)} may be a
   * partial or full buffer and the strategy compiled may be executed in parallel with other
   * instances of the same.
   *
   * <p>TODO use a Parallelism enum and process a suggested one to return the one that the
   * computation supports.
   *
   * @return
   */
  boolean isParallelizable();

  /**
   * Run sequentially or map over the buffer. This may be called on partial buffers or an entire
   * state according to the result of {@link #isParallelizable()}.
   *
   * @param storage
   * @return
   */
  boolean run(Storage.Buffer storage);
}
