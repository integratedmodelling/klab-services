package org.integratedmodelling.klab.api.actors;

import java.io.PrintStream;
import java.net.URL;
import java.util.function.Consumer;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.Message;

/**
 * The runtime peer of a k.Actors agent. The agent can be connected to a {@link
 * org.integratedmodelling.klab.api.scope.UserScope} (user agent with configurable behavior
 * instantiated on authentication), a {@link SessionScope} (applications, scripts or test cases,
 * each running within a session that is private to the agent), a {@link ContextScope} (for DT-wide
 * automation) or an {@link org.integratedmodelling.klab.api.knowledge.observation.Observation}
 * (autonomous agents within a {@link org.integratedmodelling.klab.api.digitaltwin.DigitalTwin}.
 *
 * <p>Both client and service-side agents implement this interface.
 */
public interface RuntimeAgent {

  /**
   * Agent scope, containing the local state for the agent and its actions, and methods to implement
   * agent function in Java @{@link
   * org.integratedmodelling.klab.api.services.runtime.extension.Verb} implementations, which use
   * the scope to interact with the agent.
   */
  interface Scope extends Parameters<String> {

    /**
     * A SessionScope is always defined during an agent's lifetime. The scope may be a ContextScope
     * when the agent is part of a digital twin.
     *
     * @return the current session scope. Never null.
     */
    SessionScope getSession();

    /**
     * A ContextScope is only defined during an agent's lifetime if the agent is part of an active
     * digital twin.
     *
     * @return the current context scope, or null if not in a digital twin.
     */
    ContextScope getContext();

    /**
     * The agent that runs in this scope.
     *
     * @return
     */
    RuntimeAgent getAgent();

    /**
     * Call this to fire an event to any subscribers, leaving the agent's code running. Can be
     * called multiple times during the lifetime of the agent, which must be tagged as an emitter to
     * work correctly.
     *
     * @param firedObject
     */
    void doFire(Object firedObject);

    /**
     * Calling return will stop the actor's code execution and return the passed object to any
     * subscribed listener.
     *
     * @param returnedObject
     */
    void doReturn(Object returnedObject);

    /**
     * If the agent is capable of writing to a console, this will return a PrintStream.
     *
     * @return a PrintStream for output or a no-op PrintStream (ideally logging) if the agent is not
     *     capable of writing to a console.
     */
    PrintStream getPrintWriter();

    /**
     * Action code that runs in the background must check this to know when to exit.
     *
     * @return true if the agent is done, false otherwise.
     */
    boolean isDone();

    /**
     * The actor code may call this to signal that it's done. If an exception or an error
     * notification is passed, the actor will be stopped exceptionally and should report
     * accordingly.
     */
    void done(Object... conditions);
  }

  /**
   * The actor's URL identifies it uniquely within the runtime it's part of and enables REST and
   * HTTP access to the accessible state of the actor.
   *
   * @return
   */
  URL getURL();

  /**
   * An Actor can send a message to this actor using this. If a response is expected, the sender can
   * add a message consumer which may be called or not.
   *
   * @param message
   * @param sender
   * @param responseConsumer pass null if no response is expected
   */
  void send(Message message, RuntimeAgent sender, Consumer<Message> responseConsumer);
}
