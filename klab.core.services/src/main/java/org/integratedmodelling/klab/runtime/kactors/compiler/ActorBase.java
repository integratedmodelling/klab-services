package org.integratedmodelling.klab.runtime.kactors.compiler;

import groovy.lang.GroovyObjectSupport;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.runtime.kactors.actors.runtime.ActionScope;
import reactor.core.publisher.Sinks;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;

/// Base class for the Java/Groovy compiled actor that translates a k.Actors behavior,
/// substituting the interpreted k.Actors VM.
///
/// Guidelines for the action behavior (applies to k.Actors in general):
///
/// 1. Actions may contain `return` statements and/or `fire` statements. The return statement
///    makes the action a "reactor", which will exit (removing all listeners installed in the
///    action) when encountered.
/// 2. If an action ends with a `return` statement, it will exit immediately and remove all its
///    listeners. So an action that ends with return normally has *functional* behavior and
///    should not
///    contain listeners. If it does, there should be something in it that waits. Actions with
///    returns as part of match actions or conditionals and not at the end of the main body
///    normally have *reactor* behavior: they install listeners whose match code
///    returns, making all listener(s) disappear after the first time one matches.
/// 3. Actions that contain `fire` statements have *emitter* behavior, i.e., they install listeners
///    that may
///    fire events multiple times and are only removed if and when when a match calls `return`.
/// 4. Anything fired from actions other than `main` should be intercepted by the calling
///    code. If `main` fires, the object fired will be visible in the containing scope. For
///    behaviors, the scope will be the context observation (if it has a behavior of its own).
///    For scripts and tests, the scope can be the session or user otherwise, assuming there is
///    a behavior there. The session running a test case or application has a behavior by default
///    and its `main` can install `when` listeners for events fired by a lower agent's main.
///
/// Applications in a service's purview should be automatically available at the URL
/// `<runtimeUrl>/<applicationUrn>.app`. The app URN should be substitutable with a
/// resource URN containing the application's behavior (adapter TBD). It should be a
/// universal adapter, so that only the catalog and the app ID need to be passed (the
/// rest would be klab:app:) - maybe with the catalog using group-dependent defaults.
///
public abstract class ActorBase extends GroovyObjectSupport {

  private final KActorsBehavior behavior;
  protected final Sinks.Many<Event> eventBus = Sinks.many().multicast().onBackpressureBuffer();
  private CompletableFuture<ActionScope> mainTask = null;

  /** The value returned by a void action. */
  public static final Object VOID_VALUE = new Object();

  public static final ExitValue NORMAL_EXIT = new ExitValue();
  public static final ExitValue FORCED_EXIT = new ExitValue();
  public static final ExitValue NO_TASK = new ExitValue();
  public long id = -1;

  public static class Event {}

  public static class ExitValue {}

  public ActorBase() {
    this(null);
  }

  public ActorBase(KActorsBehavior behavior) {
    this.behavior = behavior;
  }

  /**
   * Root-level main entry point. If there is no "main" action, one is provided to just listen for
   * any events.
   *
   * <p>Java-based actors may simply implement this.
   *
   * @param initialScope
   * @param session
   * @return
   */
  protected abstract ActionScope main(ActionScope initialScope, SessionScope session);

  /**
   * Run the behavior asynchronously. The behavior will end when a return instruction is reached in
   * the main action, when the host of the behavior (user, session, observation, digital twin) ends
   * its lifecycle, or upon encountering a throw instruction anywhere in the k.Actors code.
   *
   * <p>This can be called on scripts or test cases although it's not recommended. Calling this
   * function instead of run() is expected on behaviors and applications.
   *
   * @return a future for the exit value so that the caller can control the execution. This should
   *     be used only for monitoring purposes: to stop or complete the behavior, stop() should be
   *     used to ensure proper cleanup of the runtime environment.
   */
  public CompletableFuture<ActionScope> runAsync(SessionScope scope, Object... parameters) {
    return mainTask = CompletableFuture.supplyAsync(() -> main(ActionScope.of(parameters), scope));
  }

  /**
   * Run the behavior synchronously and return the exit value. Blocks until the behavior completes
   * (which may never happen). If the behavior is a script or a test case, a return instruction is
   * inserted at the end of main() even if a main action is there and it does not end with one.
   *
   * @return
   */
  public ExitValue run(SessionScope session, Object... parameters) {
    return runAsync(session, parameters).join().getExitValue();
  }

  public ExitValue stop() {
    if (mainTask != null) {
      if (mainTask.isDone()) {
        return NORMAL_EXIT;
      }
      mainTask.complete(null);
      return FORCED_EXIT;
    }
    return NO_TASK;
  }

  public void fire(ActionScope scope, Object fired) {}

  public void doReturn(ActionScope scope, Object returnValue) {}

  /**
   * Send a message to be handled by this actor. For the <code>@handle</code>-annotated actions when
   * there is no return value or it's not important. If the actor is connected to a scope, calling
   * send() on the scope with a {@link
   * org.integratedmodelling.klab.api.services.runtime.Message.MessageClass#ActorCommunication} will
   * call this.
   *
   * @param message same parameters as {@link Message#create(String, Object...)} ()} without the
   *     identity and the MessageClass (which will raise an exception if passed).
   */
  public void tell(Object... message) {}

  /**
   * Send a message to be handled by this actor and return a completable future for the response.
   * For the <code>@handle</code>-annotated actions when there is no return value or it's not
   * important.
   *
   * @param message same parameters as {@link Message#create(String, Object...)} ()} without the
   *     identity and the MessageClass (which will raise an exception if passed).
   */
  public CompletableFuture<Object> ask(Object... message) {
    return null;
  }

  protected Object resolveIdentifier(String identifier, ActionScope scope) {
    return null;
  }
}
