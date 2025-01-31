package org.integratedmodelling.klab.api.scope;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;

import java.io.Serializable;
import java.util.concurrent.Future;

/**
 * A reactive scope talks to a Klab agent through the exposed {@link
 * org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref agent ref} and can route
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
  KActorsBehavior.Ref getAgent();

  /**
   * Pass a message to the agent based on the passed arguments and expect a response of the passed
   * type, blocking until one is received. Implementation may impose timeouts.
   *
   * @param resultClass the expected result type
   * @param messageArgs anything that can be converted to a {@link
   *     org.integratedmodelling.klab.api.services.runtime.Message}, including a prebuilt Message.
   *     The MessageClass will be automatically set to {@link
   *     org.integratedmodelling.klab.api.services.runtime.Message.MessageClass#ActorCommunication}
   *     and passing anything else will cause an exception.
   * @param <T>
   * @return
   */
  <T extends Serializable> T ask(Class<T> resultClass, Object... messageArgs);
}
