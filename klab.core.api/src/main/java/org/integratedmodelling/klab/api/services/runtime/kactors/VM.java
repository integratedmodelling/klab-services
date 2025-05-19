package org.integratedmodelling.klab.api.services.runtime.kactors;

import java.io.Serializable;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ReactiveScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Channel;

/**
 * The k.Actors virtual machine. For now just a simple interface as a place to put a runtime scope,
 * needed by extensions.
 *
 * @author mario
 */
public interface VM {

  /**
   * The scope for a behavior is a specialized Channel whose {@link #send(Object...)} methods will
   * direct any message that implements {@link AgentMessage} to the underlying actor, if the scope
   * of execution implies one.
   *
   * @author Ferd
   * @deprecated this should be transparent and the implementation should just use a delegate - the
   *     API only implies carrying another scope.
   */
  interface BehaviorScope extends Channel {

    /**
     * The authorized runtime scope for the behavior execution.
     *
     * @return
     */
    Scope getMainScope();
  }

  /**
   * Generic agent message is just a serializable tag interface. Used to recognize and dispatch
   * messages to agents when the scope's send() and post() are called with these as parameters.
   *
   * @author Ferd
   */
  interface AgentMessage extends Serializable {}

  /**
   * Run the passed behavior with the passed parameters in the passed scope. Should create the
   * {@link BehaviorScope} internally.
   *
   * @param behavior
   * @param arguments
   * @param scope
   */
  void run(KActorsBehavior behavior, Parameters<String> arguments, ReactiveScope scope);

  /** Stop any running behavior and reclaim any storage and cache. */
  void stop();
}
