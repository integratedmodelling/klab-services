package org.integratedmodelling.klab.runtime.kactors;

import groovy.lang.GroovyObjectSupport;
import java.net.URL;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.kactors.actors.runtime.AgentScope;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

/// Base class for the Java-compiled agent that translates a k.Actors behavior. Specialized
/// agents such as test cases, scripts, and applications derive from this class, adding support
/// for their specialized behaviors.
///
/// Guidelines for the action behavior (applies to k.Actors in general):
///
/// 1. Actions may contain `return` statements and/or `fire` statements. The return statement
///    makes the action a "supplier", which will exit (removing all listeners installed in the
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
///    fire events multiple times and are only removed if and when a match calls `return`.
/// 4. Anything fired from actions other than `main` should be intercepted by the calling
///    code. If `main` fires, the object fired will be visible in the containing scope. For
///    behaviors, the scope will be the context observation (if it has a behavior of its own).
///    For scripts and tests, the scope can be the session or user otherwise, assuming there is
///    a behavior there. The session running a test case or application has a behavior by default
///    and its `main` can install `when` listeners for events fired by a lower agent's main.
/// 5. Suppliers used in a functional context (for example, as the right-hand side of an assignment)
// are
///    compiled with blocking behavior (i.e., calling get() on their CompletableFuture result). This
///    can also be triggered by the calling code starting the next statement with `then`. If
///    `then` follows a group, suppliers in the entire group will be waited for before continuing.
///
/// Applications in a service's purview should be automatically available at the URL
/// `<runtimeUrl>/<applicationUrn>.app`. The app URN should be substitutable with a
/// resource URN containing the application's behavior (adapter TBD). It should be a
/// universal adapter, so that only the catalog and the app ID need to be passed (the
/// rest would be klab:app:) - maybe with the catalog using group-dependent defaults.
///
public abstract class AgentBase extends GroovyObjectSupport implements Agent {

  private AgentScope rootScope;
  private final KActorsBehavior behavior;
  protected final Sinks.Many<Event> eventBus = Sinks.many().multicast().onBackpressureBuffer();
  private final Object eventEmissionLock = new Object();
  private ContextScope contextScope;
  private SessionScope sessionScope;
  private int errors = 0;

  /** The value returned by a void action. */
  public static final Object VOID_VALUE = new Object();

  public static final ExitValue NORMAL_EXIT = new ExitValue();
  public static final ExitValue FORCED_EXIT = new ExitValue();
  public static final ExitValue TASK_RUNNING = new ExitValue();
  public long id = -1;

  /** The type of an event sent through the event bus. */
  public enum EventType {
    EXTERNAL,
    FIRE,
    RETURN,
    TERMINATION,
    EXCEPTION
  }

  /**
   * TODO the Event should contain the scope linked to it and returned by onEvent, not the channel
   *  (which is the ID in the scope).
   *
   * @param type
   * @param channel
   * @param targetAction
   * @param value
   */
  public record Event(EventType type, long channel, long targetAction, Object value) {}

  public Sinks.Many<Event> getEventBus() {
    return eventBus;
  }

  public Sinks.EmitResult emitEvent(Event event) {
    synchronized (eventEmissionLock) {
      return eventBus.tryEmitNext(event);
    }
  }

  public static class ExitValue {

    private int exitCode = 0;
    private int errorCode = 0;
    private String errorMessage = null;
    private Object returnValue = null;

    public static ExitValue success(Object returnValue) {
      // TODO
      return new ExitValue();
    }

    public static ExitValue failure(Object... args) {
      var ret = new ExitValue();
      // TODO
      ret.setErrorCode(1);
      return ret;
    }

    public int getExitCode() {
      return exitCode;
    }

    public void setExitCode(int exitCode) {
      this.exitCode = exitCode;
    }

    public int getErrorCode() {
      return errorCode;
    }

    public void setErrorCode(int errorCode) {
      this.errorCode = errorCode;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
    }

    public Object getReturnValue() {
      return returnValue;
    }

    public void setReturnValue(Object returnValue) {
      this.returnValue = returnValue;
    }
  }

  // for testing only, remove
  public AgentBase() {
    this(null, null);
  }

  public AgentBase(KActorsBehavior behavior, SessionScope scope) {
    this.behavior = behavior;
    if (scope instanceof ContextScope) {
      this.contextScope = (ContextScope) scope;
      this.sessionScope =
          scope.getParentScope(
              org.integratedmodelling.klab.api.scope.Scope.Type.SESSION, SessionScope.class);
    } else {
      this.sessionScope = scope;
    }
    this.rootScope = initializeScope();

    /*
     * TODO there should be a maintained Status object that can be sent when a status message is
     *  received. These messages should be `this.xxxx` and be pre-defined and imported; overriding
     *  should come with a warning.
     *
     * The minimal CLI that can be compiled into the agent should allow for start, stop, and status at
     * a minimum.
     */
  }

  protected AgentScope initializeScope() {
    return new AgentScope(this) {
      @Override
      public SessionScope getSession() {
        return sessionScope;
      }

      @Override
      public ContextScope getContext() {
        return contextScope;
      }
    };
  }

  public Agent.Scope rootScope() {
    return rootScope;
  }

  /**
   * The actor's URL identifies it uniquely within the runtime it's part of and enables HTTP access
   * to the accessible state of the actor.
   *
   * @return
   */
  public URL getURL() {
    return null;
  }

  /**
   * Code with this Agent's handle can send a message to this. If a response is expected, the sender
   * can add a message consumer which may be called or not.
   *
   * @param message
   * @param sender
   * @param responseConsumer pass null if no response is expected
   */
  public void send(Message message, Agent sender, Consumer<Message> responseConsumer) {
    // TODO handle installed actions and core pre-defined ones ("status", "stop")
    throw new UnsupportedOperationException("not implemented");
  }

  /**
   * Stop the agent if it was started as a thread.
   *
   * @param conditions
   */
  public void stop(Object... conditions) {
    // TODO stop or finish eventbus
    rootScope.done(conditions);
  }

  /**
   * Root-level main entry point. If there is no "main" action, one is provided to just listen for
   * any events.
   *
   * <p>Java-based actors may simply implement this.
   *
   * @param rootScope the rppt scope, obtained by calling #getRootScope().
   * @return
   */
  protected abstract ExitValue main(AgentScope rootScope);

  /**
   * The compiler must override this to return the inferred execution mode for the agent. The
   * execution mode depends on the main behavior class and the content of the actions. Even if an
   * actors is a Behavior, the execution mode will be FUNCTION unless at least one action installs a
   * reactor.
   *
   * @return
   */
  public abstract Verb.Type getAgentExecutionMode();

  /**
   * Subscribe to events emitted in the {@code scope}. Used to register actions before calling
   * {@link #runEmitter(AgentScope, Consumer)} or {@link #runSupplier(AgentScope, Function)} in the
   * same scope passed here. The generated code should use this instead of wiring Reactor directly,
   * so terminal events and subscription disposal have one implementation.
   *
   * <p>TODO onEvent should automatically create the derived scope with the next ID, which can be
   * used in the submission.
   */
  protected void onEvent(
      AgentScope scope, Consumer<Event> eventConsumer, EventType... acceptedEventTypes) {
    var acceptedTypes = acceptedEventTypes(acceptedEventTypes);
    Disposable subscription =
        this.eventBus
            .asFlux()
            .filter(event -> event.targetAction() == scope.actionId())
            .takeUntil(event -> event.type() == EventType.TERMINATION)
            .filter(event -> acceptedTypes.contains(event.type()))
            .subscribe(
                event -> {
                  try {
                    eventConsumer.accept(event);
                  } catch (Throwable t) {
                    scope.done(t);
                  }
                },
                scope::done);
    scope.disposeWith(subscription);
  }

  private Set<EventType> acceptedEventTypes(EventType... eventTypes) {
    if (eventTypes == null || eventTypes.length == 0) {
      return EnumSet.of(EventType.EXTERNAL, EventType.FIRE, EventType.RETURN, EventType.EXCEPTION);
    }
    var ret = EnumSet.noneOf(EventType.class);
    Collections.addAll(ret, eventTypes);
    return ret;
  }

  /**
   * Call to start a supplier after having called {@link #onEvent(AgentScope, Consumer,
   * EventType...)} to register its action upon completion. The supplier must return a
   * CompletableFuture; the event registration must register EventType.RETURN.
   *
   * @param scope
   * @param supplier
   * @return
   * @param <T>
   */
  protected <T> ExitValue runSupplier(
      AgentScope scope, Function<AgentScope, CompletableFuture<T>> supplier) {

    if (rootScope != null && rootScope.isDone()) {
      return ExitValue.failure(new KlabIllegalStateException("Agent already terminated"));
    }

    try {
      CompletableFuture<T> future =
          Objects.requireNonNull(supplier.apply(scope), "supplier future");
      scope.disposeWith(() -> future.cancel(true));
      future.whenComplete(
          (value, throwable) -> {
            if (throwable == null) {
              scope.doReturn(value);
            } else {
              scope.done(throwable);
            }
          });
      return TASK_RUNNING;
    } catch (Throwable t) {
      scope.done(t);
      return ExitValue.failure(t);
    }
  }

  /**
   * Call to start an emitter after having called {@link #onEvent(AgentScope, Consumer,
   * EventType...)} to register its action upon completion. The emitter is simply an AgentScope
   * consumer; the event must be EventType.FIRE.
   *
   * @param scope
   * @param runnable
   * @return
   */
  protected ExitValue runEmitter(AgentScope scope, Consumer<AgentScope> runnable) {

    if (rootScope != null && rootScope.isDone()) {
      return ExitValue.failure(new KlabIllegalStateException("Agent already terminated"));
    }

    try {
      Thread.ofVirtual()
          .name("kactors-agent-" + scope.actionId())
          .start(
              () -> {
                try {
                  runnable.accept(scope);
                  scope.awaitDone();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  scope.done(e);
                } catch (Throwable t) {
                  scope.done(t);
                }
              });
      return TASK_RUNNING;
    } catch (Throwable t) {
      return ExitValue.failure(t);
    }
  }

  public void handleNotification(Notification... notifications) {
    if (notifications != null) {
      for (var notification : notifications) {
        Logging.INSTANCE.notifications(notification);
        if (notification.getLevel().severity >= Notification.Level.Error.severity) {
          // TODO something better than this - also involve the lowest-level scope available
          errors++;
        }
      }
    }
  }

  /**
   * Start the actor as an independent thread. To stop the actor, call done() on the root scope.
   *
   * @return
   */
  public ExitValue run() {
    if (getAgentExecutionMode() == Verb.Type.FUNCTION) {
      return main(rootScope);
    }
    return runEmitter(rootScope, this::main);
  }
}
