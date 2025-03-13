package org.integratedmodelling.klab.api.services.runtime;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;

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
    boolean add(ServiceCall contextualizable);

    /**
     * Create the final computation, which must know its scales and context so that it can be run
     * without further parameters than the data buffer.
     *
     * @return
     */
    ScalarComputation build();
  }

  /**
   * Run sequentially or map over the buffer. This may be called on partial buffers or an entire
   * state.
   */
  boolean execute(Geometry geometry);
}
