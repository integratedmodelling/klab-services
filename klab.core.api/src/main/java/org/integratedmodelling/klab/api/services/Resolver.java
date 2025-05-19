package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

import java.util.concurrent.CompletableFuture;

public interface Resolver extends KlabService {

  default String getServiceName() {
    return "klab.resolver.service";
  }

  /**
   * All services publish capabilities and have a call to obtain them.
   *
   * @author Ferd
   */
  interface Capabilities extends ServiceCapabilities {}

  /**
   * Scope CAN be null for generic public capabilities.
   *
   * @param scope
   * @return
   */
  Capabilities capabilities(Scope scope);

  /**
   * Main entry point and sole function of the resolver. Resolution require multiple round-trips to
   * and from the runtime and may take some time, so it must be asynchronous.
   *
   * @param observation
   * @param contextScope
   * @return a dataflow that will resolve the passed observation, or an empty dataflow if nothing is
   *     needed.
   */
  CompletableFuture<Dataflow> resolve(Observation observation, ContextScope contextScope);

  /**
   * Encode a dataflow to its k.DL specification.
   *
   * @param dataflow
   * @return
   */
  String encodeDataflow(Dataflow dataflow);

  /**
   * Resolver administration functions.
   *
   * @author Ferd
   */
  interface Admin {

    /**
     * Load all usable knowledge from the namespaces included in the passed resource set. If there
     * is a linked semantic server and it is local and/or exclusive, also load any existing
     * semantics, otherwise raise an exception when encountering a concept definition. If the
     * resource set has focal URNs, make the correspondent resources available for consumption by
     * the resolver. If an incoming resource set contains resources already loaded, only substitute
     * the existing ones if they are tagged with a newer version.
     *
     * @param resources
     * @return
     */
    boolean loadKnowledge(ResourceSet resources);
  }
}
