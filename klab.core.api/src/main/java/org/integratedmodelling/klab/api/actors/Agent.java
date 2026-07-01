package org.integratedmodelling.klab.api.actors;

import java.io.PrintStream;
import java.net.URL;
import java.util.function.Consumer;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.Message;

/** The runtime peer of a k.Actors actor. */
public interface Agent {

  /**
   * Actor scope, a set of parameters that can be used to pass data to the actor and is used to
   * implement actor function. Java @{@link
   * org.integratedmodelling.klab.api.services.runtime.extension.Verb} implementations use the scope
   * to interact with the actor.
   */
  interface Scope extends Parameters<String> {

    /**
     * A SessionScope is always defined during an Actor's lifetime. The scope may be a ContextScope
     * when the actor is part of a digital twin.
     *
     * @return the current session scope. Never null.
     */
    SessionScope getSession();

    /**
     * A ContextScope is only defined during an Actor's lifetime if the actor is part of an active
     * digital twin.
     *
     * @return the current context scope, or null if not in a digital twin.
     */
    ContextScope getContext();

    /**
     * The actor that runs in this scope.
     *
     * @return
     */
    Agent getActor();

    /**
     * Call this to fire an event to any subscribers, leaving the actor's code running. Can be
     * called multiple times during the lifetime of the actor.
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
     * If the actor is capable of writing to a console, this will return a PrintStream.
     *
     * @return a PrintStream for output or null
     */
    PrintStream getPrintWriter();

    /**
     * Action code that runs in the background must check this to know when to exit.
     *
     * @return true if the actor is done, false otherwise.
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
   * The actor's URL identifies it uniquely within the runtime it's part of and enables HTTP access
   * to the accessible state of the actor.
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
  void send(Message message, Agent sender, Consumer<Message> responseConsumer);
}
