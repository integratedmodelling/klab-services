package org.integratedmodelling.klab.api.scope;

import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;

/**
 * A reactive scope talks to a Klab agent through the exposed {@link Agent agent} and can route
 * messages to the agent. It also adds the ask() method to wait for an agent's response. All scopes
 * except {@link ServiceScope} are reactive.
 */
public interface ReactiveScope extends MessagingChannel, Scope {

  /**
   * A reactive scope has a home in a service and possibly peers in others. The service ID enables
   * peers to find out if they have access to the original service for operations that require
   * access to the physical content linked to the scope.
   *
   * @return
   */
  String getHostServiceId();

  /**
   * If this scope is owned by an agent, return the agent handle for communication.
   *
   * @return the agent or null.
   */
  Agent getAgent();
}
