package org.integratedmodelling.klab.api.actors;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * The handle to an agent running in a runtime service. This is available at both client and service
 * sides, and while it offers advanced functionalities through its action methods, it must remain
 * serializable across network boundaries. The agent runs a k.Actors {@link
 * org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior} and may or may not be tied to a
 * {@link org.integratedmodelling.klab.api.scope.UserScope}, {@link
 * org.integratedmodelling.klab.api.scope.SessionScope} or {@link
 * org.integratedmodelling.klab.api.scope.ContextScope}. In addition, the agent may represent an
 * agent {@link org.integratedmodelling.klab.api.knowledge.observation.Observation} in a {@link
 * org.integratedmodelling.klab.api.digitaltwin.DigitalTwin}. In all these cases, the reference to
 * the agent is obtained through their respective hosts. Otherwise, a raw behavior may be submitted
 * to the runtime for execution outside of these scopes.
 */
public interface Agent extends Serializable {

  /**
   * The URN of the agent, unique within the runtime service.
   *
   * @return
   */
  String getUrn();

  /**
   * The URN of the behavior the agent is running.
   *
   * @return
   */
  String getBehaviorUrn();

  /**
   * All agents have a name, which may be automatically generated and is not guaranteed to be
   * unique. If the agent is linked to an {@link
   * org.integratedmodelling.klab.api.knowledge.observation.Observation} the name is mandatorily the
   * same as the observation's. A name request is submitted with the instantiation call and may be
   * honored or not by the runtime.
   *
   * @return the name of the agent. Never null.
   */
  String getName();

  /**
   * The agent is returned at the runAgent endpoint of the {@link
   * org.integratedmodelling.klab.api.services.RuntimeService} in a stopped state. This method must
   * be checked before starting it; if the agent isn't viable (either because of compilation errors
   * or runtime failure) a subsequent call to {@link #start(Object...)} ()} will fail. If the agent
   * is not viable, notifications may explain why.
   *
   * @return
   */
  boolean isViable();

  /**
   * Send a ping and return whether the agent responds, which happens only after {@link
   * #start(Object...)} has been successfully called.
   *
   * @return
   */
  boolean isAlive();

  /**
   * Start the agent, passing any arguments to its behavior's constructor. A false return indicates
   * that the agent could not be started; notifications may explain why.
   *
   * @return
   */
  boolean start(Object... arguments);

  /**
   * Agent start their existence in a running state. They may stop due to error, their host going
   * out of scope, or being stopped by the user through this call.
   *
   * @return
   */
  boolean stop();

  /**
   * Agents maintain notifications sent through the messaging system. If the agent is not alive,
   * these may explain why.
   *
   * @return
   */
  List<Notification> getNotifications();

  /**
   * Textual CONSTANT values accepted by actions annotated with {@code @handle}, including
   * inherited handlers after runtime override resolution. These values describe the agent's
   * inspectable custom-message API. Reserved runtime messages such as {@code @stdin} are excluded.
   *
   * @return an immutable list, never null
   */
  default List<String> getHandledMessageClasses() {
    return List.of();
  }

  /**
   * Send a message asynchronously.
   *
   * @param <T>
   * @param message
   */
  <T extends Serializable> void tell(T message);

  /**
   * Ask a question and wait for the response. Assumes a fast response or no response;
   * implementations should use a sensible timeout and behave as needed (null return or unchecked
   * exception) if it's exceeded.
   *
   * @param <T>
   * @param <R>
   * @param message
   * @param responseClass
   * @return
   */
  <T extends Serializable, R extends Serializable> CompletableFuture<R> ask(
      T message, Class<? extends R> responseClass);
}
