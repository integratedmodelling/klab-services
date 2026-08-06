package org.integratedmodelling.klab.runtime.kactors;

import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.GroovyObjectSupport;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.lang.QuantityImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.runtime.actors.AgentEventBus;
import org.integratedmodelling.common.runtime.actors.AgentImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.exceptions.KlabActorException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeDuration;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.computation.GroovyProcessor;
import org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary;
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
///    compiled with blocking behavior (i.e., calling get() on their CompletableFuture result). A
///    `then` statement waits for the preceding reactor's first value. If `then` follows a group,
///    every reactor in the group must supply a value before execution continues.
///
/// Applications in a service's purview should be automatically available at the URL
/// `<runtimeUrl>/<applicationUrn>.app`. The app URN should be substitutable with a
/// resource URN containing the application's behavior (adapter TBD). It should be a
/// universal adapter, so that only the catalog and the app ID need to be passed (the
/// rest would be klab:app:) - maybe with the catalog using group-dependent defaults.
///
public abstract class RuntimeAgentBase extends GroovyObjectSupport implements RuntimeAgent {

  private static final int MAX_PENDING_CONSOLE_OUTPUTS = 256;
  private static final ObjectMapper KLAB_OBJECT_MAPPER = JacksonConfiguration.newObjectMapper();

  private record ConsoleOutput(RuntimeAgent.ConsoleMessageType type, String text) {}

  /** Keeps inline verb metadata separate from the ordinary positional action arguments. */
  private record VerbMetadata(Metadata metadata) {}

  private record SuppliedArguments(Object[] values, Metadata metadata) {}

  private AgentScope rootScope;
  private final KActorsBehavior behavior;
  protected final Sinks.Many<Event> eventBus = Sinks.many().multicast().onBackpressureBuffer();
  private final Object eventEmissionLock = new Object();
  private ContextScope contextScope;
  private SessionScope sessionScope;
  private final Observation observation;
  private int errors = 0;
  private final AtomicLong nextId = new AtomicLong(0);
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicBoolean failureReported = new AtomicBoolean(false);
  private final AtomicBoolean consoleAttached = new AtomicBoolean(false);
  private final Object consoleOutputLock = new Object();
  private final Map<String, KimObservable> observableLiterals = new ConcurrentHashMap<>();
  private final Map<String, KActorsStatement.Assert.Assertion> assertionLiterals =
      new ConcurrentHashMap<>();
  private final ArrayDeque<ConsoleOutput> pendingConsoleOutputs = new ArrayDeque<>();
  private final AtomicLong startedAt = new AtomicLong(-1);
  private final AtomicLong lastActivityAt = new AtomicLong(-1);
  private final List<RuntimeAgentBase> inheritedBehaviorInstances =
      new CopyOnWriteArrayList<>();
  private final Map<String, KActorsBehaviorAdapter> behaviorAdapters =
      new ConcurrentHashMap<>();
  private static final ThreadLocal<ExternalBehaviorAdapter> CONSTRUCTION_ADAPTER =
      new ThreadLocal<>();
  private static final ThreadLocal<ParameterNegotiator> CONSTRUCTION_PARAMETER_NEGOTIATOR =
      new ThreadLocal<>();
  private static final ThreadLocal<BehaviorTypeChecker> CONSTRUCTION_BEHAVIOR_TYPE_CHECKER =
      new ThreadLocal<>();
  private ExternalBehaviorAdapter externalBehaviorAdapter = CONSTRUCTION_ADAPTER.get();
  private ParameterNegotiator parameterNegotiator = CONSTRUCTION_PARAMETER_NEGOTIATOR.get();
  private BehaviorTypeChecker behaviorTypeChecker = CONSTRUCTION_BEHAVIOR_TYPE_CHECKER.get();
  private String agentUrn;
  private String agentName;
  private org.integratedmodelling.klab.api.scope.Scope creatingScope;
  private Consumer<Notification> notificationConsumer;
  private volatile Runnable managedStopHandler;
  private final Disposable lifecycleSubscription;
  private final Object dynamicLifecycleLock = new Object();
  private int activeDynamicCalls;
  private boolean dynamicMainCompleted;
  private boolean dynamicRootCompleted;
  private Object dynamicMainResult;
  private Throwable dynamicFailure;

  private record KActorsBehaviorAdapter(
      RuntimeAgentBase target, String action, Verb.Type executionType) {}

  @FunctionalInterface
  public interface ExternalBehaviorAdapter {
    Object adapt(String behaviorUrn, Object source, RuntimeAgent.Scope scope);
  }

  @FunctionalInterface
  public interface ParameterNegotiator {
    List<Object> negotiate(
        List<Class<?>> unmatchedParameterTypes, List<?> suppliedParameters);
  }

  @FunctionalInterface
  public interface BehaviorTypeChecker {
    boolean implementsBehavior(String actualBehaviorUrn, String requiredBehaviorUrn);
  }

  /**
   * Make a component adapter available while a generated constructor runs its {@code init}
   * action. The previous construction context is restored so recursively constructed agents and
   * concurrent compilations remain isolated.
   */
  public static <T> T constructWithExternalBehaviorAdapter(
      ExternalBehaviorAdapter adapter, java.util.concurrent.Callable<T> constructor)
      throws Exception {
    return constructWithRuntimeCallbacks(adapter, null, constructor);
  }

  /** Install all runtime resolver callbacks while a generated constructor executes. */
  public static <T> T constructWithRuntimeCallbacks(
      ExternalBehaviorAdapter adapter,
      ParameterNegotiator negotiator,
      java.util.concurrent.Callable<T> constructor)
      throws Exception {
    return constructWithRuntimeCallbacks(adapter, negotiator, null, constructor);
  }

  /** Install all runtime resolver callbacks while a generated constructor executes. */
  public static <T> T constructWithRuntimeCallbacks(
      ExternalBehaviorAdapter adapter,
      ParameterNegotiator negotiator,
      BehaviorTypeChecker typeChecker,
      java.util.concurrent.Callable<T> constructor)
      throws Exception {
    var previous = CONSTRUCTION_ADAPTER.get();
    var previousNegotiator = CONSTRUCTION_PARAMETER_NEGOTIATOR.get();
    var previousTypeChecker = CONSTRUCTION_BEHAVIOR_TYPE_CHECKER.get();
    CONSTRUCTION_ADAPTER.set(adapter);
    CONSTRUCTION_PARAMETER_NEGOTIATOR.set(negotiator);
    CONSTRUCTION_BEHAVIOR_TYPE_CHECKER.set(typeChecker);
    try {
      return constructor.call();
    } finally {
      if (previous == null) {
        CONSTRUCTION_ADAPTER.remove();
      } else {
        CONSTRUCTION_ADAPTER.set(previous);
      }
      if (previousNegotiator == null) {
        CONSTRUCTION_PARAMETER_NEGOTIATOR.remove();
      } else {
        CONSTRUCTION_PARAMETER_NEGOTIATOR.set(previousNegotiator);
      }
      if (previousTypeChecker == null) {
        CONSTRUCTION_BEHAVIOR_TYPE_CHECKER.remove();
      } else {
        CONSTRUCTION_BEHAVIOR_TYPE_CHECKER.set(previousTypeChecker);
      }
    }
  }

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
   * The Event is sent to the reactor sink when a fire, return, or exception is invoked on the scope
   * during execution.
   *
   * @param type
   * @param actionId
   * @param payload
   */
  public record Event(EventType type, long actionId, Object payload) {}

  /** Compile-time descriptor for an action annotated with {@code @handle}. */
  public static final class AgentMessageHandler {

    private final RuntimeAgentBase target;
    private final String action;
    private final Verb.Type executionType;
    private final List<String> argumentNames;
    private final boolean customApi;

    public AgentMessageHandler(
        String action, Verb.Type executionType, List<String> argumentNames) {
      this(action, executionType, argumentNames, true);
    }

    public AgentMessageHandler(
        String action,
        Verb.Type executionType,
        List<String> argumentNames,
        boolean customApi) {
      this(null, action, executionType, argumentNames, customApi);
    }

    private AgentMessageHandler(
        RuntimeAgentBase target,
        String action,
        Verb.Type executionType,
        List<String> argumentNames,
        boolean customApi) {
      this.target = target;
      this.action = action;
      this.executionType = executionType;
      this.argumentNames = argumentNames == null ? List.of() : List.copyOf(argumentNames);
      this.customApi = customApi;
    }

    public String action() {
      return action;
    }

    public Verb.Type executionType() {
      return executionType;
    }

    public List<String> argumentNames() {
      return argumentNames;
    }

    private AgentMessageHandler targeting(RuntimeAgentBase target) {
      return new AgentMessageHandler(target, action, executionType, argumentNames, customApi);
    }
  }

  public Sinks.Many<Event> getEventBus() {
    return eventBus;
  }

  public Sinks.EmitResult emitEvent(Event event) {
    markActivity();
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
      var ret = new ExitValue();
      ret.setReturnValue(returnValue);
      return ret;
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
  public RuntimeAgentBase() {
    this(null, null, null);
  }

  public RuntimeAgentBase(KActorsBehavior behavior, SessionScope scope) {
    this(behavior, scope, null);
  }

  public RuntimeAgentBase(
      KActorsBehavior behavior, SessionScope scope, Observation observation) {
    this(behavior, scope, observation, scope);
  }

  public RuntimeAgentBase(
      KActorsBehavior behavior,
      SessionScope scope,
      Observation observation,
      org.integratedmodelling.klab.api.scope.Scope creationScope) {
    this.behavior = behavior;
    this.observation = observation;
    this.creatingScope = creationScope;
    if (scope instanceof ContextScope) {
      this.contextScope = (ContextScope) scope;
      this.sessionScope =
          scope.getParentScope(
              org.integratedmodelling.klab.api.scope.Scope.Type.SESSION, SessionScope.class);
    } else {
      this.sessionScope = scope;
    }
    this.rootScope = initializeScope();
    this.rootScope.disposeWith(this.rootScope::dispose);
    this.lifecycleSubscription =
        eventBus
            .asFlux()
            .filter(event -> event.actionId() == 0)
            .filter(
                event ->
                    event.type() == EventType.EXCEPTION
                        || event.type() == EventType.TERMINATION)
            .subscribe(this::handleLifecycleEvent);

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

  protected final SessionScope sessionScope() {
    return sessionScope;
  }

  protected final ContextScope contextScope() {
    return contextScope;
  }

  public RuntimeAgent.Scope rootScope() {
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

  @Override
  public Observation getObservation() {
    return observation;
  }

  @Override
  public org.integratedmodelling.klab.api.scope.Scope getCreationScope() {
    return creatingScope;
  }

  @Override
  public long getStartedAt() {
    return startedAt.get();
  }

  @Override
  public long getLastActivityAt() {
    return lastActivityAt.get();
  }

  /**
   * Connect this runtime instance to all local and remote peers of its serializable agent handle.
   * The transport is intentionally maintained by {@link AgentEventBus}, not by the handle.
   */
  public boolean initializeMessaging(
      String agentUrn,
      org.integratedmodelling.klab.api.scope.Scope creatingScope,
      Consumer<Notification> notificationConsumer) {
    initializeIdentity(agentUrn, agentName);
    if (this.creatingScope == null) {
      this.creatingScope = creatingScope;
    }
    this.notificationConsumer = notificationConsumer;
    if (!(creatingScope instanceof MessagingChannel messagingChannel)
        || !messagingChannel.isConnected()) {
      notifyAgent(
          Notification.info(
              "Agent messaging is disabled because its creating scope is not connected"));
      return false;
    }
    if (!AgentEventBus.INSTANCE.subscribe(agentUrn, this, messagingChannel, this::receiveMessage)) {
      notifyAgent(
          Notification.info(
              "Agent messaging is disabled because its AMQP transport could not be established"));
      return false;
    }
    return true;
  }

  /**
   * Install the registry identity on this runtime and all retained inherited behavior delegates.
   */
  public final void initializeIdentity(String agentUrn, String agentName) {
    this.agentUrn = agentUrn == null ? "" : agentUrn;
    this.agentName =
        agentName == null || agentName.isBlank() ? defaultAgentName() : agentName;
    inheritedBehaviorInstances.forEach(
        inherited -> inherited.initializeIdentity(this.agentUrn, this.agentName));
  }

  @Override
  public final String getUrn() {
    return agentUrn == null ? "" : agentUrn;
  }

  @Override
  public final String getName() {
    return agentName == null || agentName.isBlank() ? defaultAgentName() : agentName;
  }

  private String defaultAgentName() {
    String urn = behavior == null ? null : behavior.getUrn();
    if (urn == null || urn.isBlank()) {
      return "agent";
    }
    int separator = Math.max(urn.lastIndexOf('.'), urn.lastIndexOf(':'));
    return separator < 0 ? urn : urn.substring(separator + 1);
  }

  /** Disconnect the runtime peer without modifying the serializable agent handle. */
  public void closeMessaging() {
    synchronized (consoleOutputLock) {
      consoleAttached.set(false);
      pendingConsoleOutputs.clear();
    }
    if (agentUrn != null) {
      AgentEventBus.INSTANCE.unsubscribe(agentUrn, this);
    }
    lifecycleSubscription.dispose();
    inheritedBehaviorInstances.forEach(RuntimeAgentBase::closeMessaging);
  }

  /**
   * Install the registry-owned terminal stop operation. Remote stop requests must use this hook so
   * that terminating the execution scope also removes the managed handle and its transport.
   */
  public final void setManagedStopHandler(Runnable managedStopHandler) {
    this.managedStopHandler = managedStopHandler;
  }

  /** Publish through this runtime's exact federation transport. */
  public final boolean publishAgentMessage(String recipientAgentUrn, Object... messageArguments) {
    if (agentUrn == null
        || !(creatingScope instanceof MessagingChannel messagingChannel)
        || !messagingChannel.isConnected()) {
      return false;
    }
    stampSenderName(messageArguments);
    return AgentEventBus.INSTANCE.publish(
        messagingChannel,
        this,
        agentUrn,
        recipientAgentUrn,
        messageArguments);
  }

  /** Send a correlated request through this runtime's exact federation transport. */
  public final <R extends java.io.Serializable> CompletableFuture<R> askAgentMessage(
      String recipientAgentUrn,
      RuntimeAgent.CustomMessage request,
      Class<? extends R> responseClass,
      Duration timeout,
      boolean enforceTimeout) {
    if (agentUrn == null
        || !(creatingScope instanceof MessagingChannel messagingChannel)
        || !messagingChannel.isConnected()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Agent messaging is not connected for " + agentUrn));
    }
    stampSenderName(request);
    return AgentEventBus.INSTANCE.ask(
        messagingChannel,
        this,
        agentUrn,
        recipientAgentUrn,
        request,
        responseClass,
        timeout,
        enforceTimeout);
  }

  /**
   * Code with this Agent's handle can send a message to this. If a response is expected, the sender
   * can add a message consumer which may be called or not.
   *
   * @param message
   * @param sender
   * @param responseConsumer pass null if no response is expected
   */
  public void send(Message message, RuntimeAgent sender, Consumer<Message> responseConsumer) {
    requireAgentMessage(message);
    String senderUrn =
        sender instanceof Agent agent
            ? agent.getUrn()
            : sender instanceof RuntimeAgentBase runtime ? runtime.agentUrn : null;
    receiveMessage(
        senderUrn == null
            ? message
            : Message.create(
                senderUrn,
                message.getMessageClass(),
                message.getMessageType(),
                message.getPayload(Object.class)));
    // Response-producing custom actions are not installed yet. The callback remains part of the
    // contract so ask/reply can be layered on message correlation without changing this method.
  }

  @Override
  public boolean send(String recipientAgentUrn, Message message) {
    requireAgentMessage(message);
    markActivity();
    return publishAgentMessage(recipientAgentUrn, message);
  }

  @Override
  public boolean sendToScope(Message message) {
    if (!(creatingScope instanceof MessagingChannel messagingChannel)
        || !messagingChannel.isConnected()
        || message == null) {
      return false;
    }
    markActivity();
    creatingScope.send(message);
    return true;
  }

  @Override
  public boolean sendToConsole(RuntimeAgent.ConsoleMessageType type, String text) {
    if ((type != RuntimeAgent.ConsoleMessageType.STDOUT
            && type != RuntimeAgent.ConsoleMessageType.STDERR)
        || agentUrn == null) {
      return false;
    }
    markActivity();
    synchronized (consoleOutputLock) {
      if (!consoleAttached.get()) {
        if (pendingConsoleOutputs.size() == MAX_PENDING_CONSOLE_OUTPUTS) {
          pendingConsoleOutputs.removeFirst();
        }
        pendingConsoleOutputs.addLast(new ConsoleOutput(type, text));
        // Retain the existing local fallback while making startup output available to a console
        // that attaches after main has begun.
        return false;
      }
    }
    return publishConsoleOutput(type, text);
  }

  protected boolean publishConsoleOutput(
      RuntimeAgent.ConsoleMessageType type, String text) {
    return publishAgentMessage(
        agentUrn,
        Message.MessageType.CustomAgentMessage,
        new RuntimeAgent.CustomMessage(type.constant(), text));
  }

  /**
   * Stop the agent if it was started as a thread.
   *
   * @param conditions
   */
  public void stop(Object... conditions) {
    inheritedBehaviorInstances.forEach(inherited -> inherited.stop(conditions));
    rootScope.done(conditions);
  }

  /**
   * Terminate this agent from an explicitly executed {@code return} in its persistent
   * {@code main} action.
   *
   * <p>The return may execute in a nested control-flow branch or reactive callback, whose current
   * action scope is not necessarily the root scope. Generated code therefore uses this method
   * instead of completing only the current child scope.
   */
  protected final void terminateAgent(Object returnValue) {
    rootScope.done(returnValue);
  }

  /**
   * Register cleanup that must run exactly when the root agent scope terminates. If the agent has
   * already terminated, the cleanup runs immediately.
   */
  public void onTermination(Runnable cleanup) {
    if (cleanup != null) {
      rootScope.disposeWith(cleanup::run);
    }
  }

  /** A deliberately small status surface used by generated test CLIs. */
  public String status() {
    if (rootScope == null || rootScope.isDone()) {
      return "stopped";
    }
    return started.get() ? "running" : "ready";
  }

  private void receiveMessage(Message message) {
    requireAgentMessage(message);
    markActivity();
    switch (message.getMessageType()) {
      case AgentStartRequested -> {
        if (!started.get()) {
          try {
            run();
          } catch (Throwable failure) {
            rootScope.done(failure);
          }
        } else {
          publishStatus(Message.MessageType.AgentStatusChanged, null);
        }
      }
      case AgentStopRequested -> {
        var stopHandler = managedStopHandler;
        if (stopHandler == null) {
          stop("Stop requested by " + message.getDispatchId());
        } else {
          Thread.ofVirtual().name("kactors-stop-" + agentUrn).start(stopHandler);
        }
      }
      case AgentStatusRequested -> publishStatus(Message.MessageType.AgentStatusChanged, null);
      case CustomAgentMessage -> handleCustomAgentMessage(message);
      default -> {
        // Lifecycle reports are consumed by remote handles, not by the executing runtime peer.
      }
    }
  }

  /**
   * Entry point for language-defined messages. The compiler/runtime can override this to dispatch
   * the constant discriminator to installed k.Actors handlers.
   */
  protected void handleCustomAgentMessage(Message message) {
    var customMessage = message.getPayload(RuntimeAgent.CustomMessage.class);
    if (customMessage == null || customMessage.type() == null) {
      notifyAgent(Notification.warning("Ignoring an agent message without a custom payload"));
      return;
    }
    if (customMessage.inResponseTo() != null) {
      // AgentEventBus consumes correlated responses before dispatching local subscribers. Runtime
      // peers must not feed the response back through the language handler.
      return;
    }
    String messageClass = customMessage.type().getValue();
    if (RuntimeAgent.ConsoleMessageType.CONSOLE_ATTACH.name().equals(messageClass)) {
      List<ConsoleOutput> pending;
      synchronized (consoleOutputLock) {
        consoleAttached.set(true);
        pending = List.copyOf(pendingConsoleOutputs);
        pendingConsoleOutputs.clear();
      }
      pending.forEach(output -> publishConsoleOutput(output.type(), output.text()));
      return;
    }
    if (RuntimeAgent.ConsoleMessageType.CONSOLE_DETACH.name().equals(messageClass)) {
      synchronized (consoleOutputLock) {
        consoleAttached.set(false);
      }
      return;
    }
    if (RuntimeAgent.ConsoleMessageType.STDIN.name().equals(messageClass)) {
      consoleAttached.set(true);
    } else if (RuntimeAgent.ConsoleMessageType.STDOUT.name().equals(messageClass)
        || RuntimeAgent.ConsoleMessageType.STDERR.name().equals(messageClass)) {
      // Output is consumed by client consoles sharing this agent endpoint.
      return;
    }
    if (RuntimeAgent.isReservedMessageClass(messageClass)
        && !RuntimeAgent.ConsoleMessageType.STDIN.name().equals(messageClass)) {
      // Test lifecycle and other runtime protocol messages are consumed by connected clients.
      // They must never fall through to language-defined handlers.
      return;
    }
    var handler = agentMessageHandlers().get(messageClass);
    if (handler == null) {
      emitEvent(new Event(EventType.EXTERNAL, 0, customMessage));
      return;
    }
    Object payload = mediateAgentMessagePayload(customMessage);
    Agent sender =
        senderHandle(
            message.getDispatchId(), customMessage.senderName(), customMessage.requestId());
    Object[] arguments =
        handler.argumentNames().stream()
            .map(name -> "sender".equals(name) ? sender : payload)
            .toArray();
    dispatchAgentMessage(handler, arguments, message, customMessage);
  }

  /**
   * Generated classes override this and merge their declarations with {@code super}, allowing
   * handlers inherited through a generated behavior superclass to remain active.
   */
  protected Map<String, AgentMessageHandler> agentMessageHandlers() {
    return Map.of();
  }

  /**
   * Return the resolved custom message API implemented through {@code @handle}, including inherited
   * handlers and excluding reserved runtime handlers such as {@code @stdin}.
   */
  public final List<String> getHandledMessageClasses() {
    return agentMessageHandlers().entrySet().stream()
        .filter(entry -> entry.getValue().customApi)
        .filter(entry -> !RuntimeAgent.isReservedMessageClass(entry.getKey()))
        .map(Map.Entry::getKey)
        .sorted()
        .toList();
  }

  /**
   * Merge handlers contributed by an inherited behavior delegate. Earlier inherited behaviors
   * retain precedence; local generated handlers are added afterward and may override them.
   */
  protected final void inheritAgentMessageHandlers(
      Map<String, AgentMessageHandler> handlers, RuntimeAgentBase inheritedBehavior) {
    if (inheritedBehavior != null) {
      inheritedBehavior
          .agentMessageHandlers()
          .forEach(
              (messageClass, handler) ->
                  handlers.putIfAbsent(messageClass, handler.targeting(inheritedBehavior)));
    }
  }

  /**
   * Retain an inherited behavior delegate as part of this agent's lifecycle. Generated
   * constructors use this hook so reactive inherited handlers are stopped and disposed with their
   * owning agent.
   */
  protected final RuntimeAgentBase registerInheritedBehavior(RuntimeAgentBase inheritedBehavior) {
    if (inheritedBehavior != null) {
      if (externalBehaviorAdapter != null) {
        inheritedBehavior.setExternalBehaviorAdapter(externalBehaviorAdapter);
      }
      if (parameterNegotiator != null) {
        inheritedBehavior.setParameterNegotiator(parameterNegotiator);
      }
      if (behaviorTypeChecker != null) {
        inheritedBehavior.setBehaviorTypeChecker(behaviorTypeChecker);
      }
      inheritedBehaviorInstances.add(inheritedBehavior);
    }
    return inheritedBehavior;
  }

  /** Retain a constructed imported behavior under the same owning-agent lifecycle. */
  protected final RuntimeAgentBase registerImportedBehavior(RuntimeAgentBase importedBehavior) {
    return registerInheritedBehavior(importedBehavior);
  }

  /**
   * Install the runtime callback used for component-provided Java actor adapters. The registry sets
   * this immediately after constructing the generated agent.
   */
  public final void setExternalBehaviorAdapter(ExternalBehaviorAdapter adapter) {
    this.externalBehaviorAdapter = adapter;
    inheritedBehaviorInstances.forEach(
        inherited -> inherited.setExternalBehaviorAdapter(adapter));
  }

  /** Install the resolver callback used when ordinary Java parameter coercion cannot match. */
  public final void setParameterNegotiator(ParameterNegotiator negotiator) {
    this.parameterNegotiator = negotiator;
    inheritedBehaviorInstances.forEach(
        inherited -> inherited.setParameterNegotiator(negotiator));
  }

  public final void setBehaviorTypeChecker(BehaviorTypeChecker typeChecker) {
    this.behaviorTypeChecker = typeChecker;
    inheritedBehaviorInstances.forEach(
        inherited -> inherited.setBehaviorTypeChecker(typeChecker));
  }

  /**
   * Register an action annotated with {@code @adapt} for one compiled k.Actors behavior.
   * Supplier actions are joined by {@link #adaptToBehavior(Object, String, AgentScope)}.
   */
  protected final void registerBehaviorAdapter(
      String behaviorUrn,
      RuntimeAgentBase target,
      String action,
      Verb.Type executionType) {
    if (behaviorUrn == null || target == null || action == null || executionType == null) {
      return;
    }
    if (target != this) {
      registerImportedBehavior(target);
    }
    behaviorAdapters.put(
        behaviorUrn, new KActorsBehaviorAdapter(target, action, executionType));
  }

  private void dispatchAgentMessage(
      AgentMessageHandler handler,
      Object[] arguments,
      Message request,
      RuntimeAgent.CustomMessage customRequest) {
    RuntimeAgentBase target = handler.target == null ? this : handler.target;
    var actionScope = target.rootScope.withId(target.nextId.incrementAndGet());
    actionScope.disposeWith(
        target.eventBus
            .asFlux()
            .filter(event -> event.actionId() == actionScope.actionId())
            .filter(event -> event.type() == EventType.EXCEPTION)
            .take(1)
            .subscribe(event -> rootScope.done(event.payload())));
    Thread.ofVirtual()
        .name("kactors-message-" + handler.action())
        .start(
            () -> {
              try {
                switch (handler.executionType()) {
                  case FUNCTION -> {
                    Object result =
                        target.invokeSelfFunction(handler.action(), actionScope, arguments);
                    actionScope.done(result);
                    publishAgentResponse(request, customRequest, result, null);
                  }
                  case SUPPLIER ->
                      target
                          .invokeSelfSupplier(handler.action(), actionScope, arguments)
                          .whenComplete(
                              (result, failure) -> {
                                if (failure == null) {
                                  actionScope.done(result);
                                  publishAgentResponse(request, customRequest, result, null);
                                } else {
                                  actionScope.done(failure);
                                  publishAgentResponse(request, customRequest, null, failure);
                                }
                              });
                  case EMITTER ->
                      target.invokeSelfEmitter(handler.action(), actionScope, arguments);
                }
              } catch (Throwable failure) {
                actionScope.done(failure);
                publishAgentResponse(request, customRequest, null, failure);
              }
            });
  }

  private void publishAgentResponse(
      Message request,
      RuntimeAgent.CustomMessage customRequest,
      Object result,
      Throwable failure) {
    if (request == null
        || customRequest == null
        || customRequest.requestId() == null
        || request.getDispatchId() == null) {
      return;
    }
    java.io.Serializable payload =
        result == null || result == VOID_VALUE
            ? null
            : result instanceof java.io.Serializable serializable ? serializable : null;
    var response = new RuntimeAgent.CustomMessage(customRequest.type(), payload);
    response.setInResponseTo(customRequest.requestId());
    if (failure != null) {
      Throwable cause =
          failure instanceof java.util.concurrent.CompletionException completion
                  && completion.getCause() != null
              ? completion.getCause()
              : failure;
      response.setFailure(
          cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage());
    } else if (result != null && result != VOID_VALUE && payload == null) {
      response.setFailure(
          "The response payload " + result.getClass().getName() + " is not serializable");
    }
    publishAgentMessage(
        request.getDispatchId(),
        Message.MessageType.CustomAgentMessage,
        response);
  }

  /**
   * Restore a registered DTO that crossed the Jackson {@code Object} boundary as a map. Unknown
   * advertised class names are deliberately left as maps: components must explicitly register
   * their serializable message types with {@link AgentEventBus#registerPayloadType(Class)}.
   */
  protected Object mediateAgentMessagePayload(RuntimeAgent.CustomMessage message) {
    Object payload = message.payload();
    if (!(payload instanceof Map<?, ?> map) || message.payloadClass() == null) {
      return payload;
    }
    Class<? extends java.io.Serializable> payloadType =
        AgentEventBus.INSTANCE.resolvePayloadType(message.payloadClass());
    if (payloadType == null) {
      notifyAgent(
          Notification.warning(
              "Agent payload type "
                  + message.payloadClass()
                  + " is not registered; delivering it as a map"));
      return payload;
    }
    try {
      return JacksonConfiguration.newObjectMapper().convertValue(map, payloadType);
    } catch (IllegalArgumentException failure) {
      notifyAgent(
          Notification.warning(
              "Cannot restore agent payload " + message.payloadClass() + ": " + failure.getMessage()));
      return payload;
    }
  }

  /** Build a lightweight reply handle whose outgoing messages originate from this agent. */
  protected Agent senderHandle(String senderUrn) {
    return senderHandle(senderUrn, null, null);
  }

  /** Build a reply handle that preserves request correlation when used from an ask handler. */
  protected Agent senderHandle(String senderUrn, String requestId) {
    return senderHandle(senderUrn, null, requestId);
  }

  /** Build a named reply handle from the portable identity advertised by a custom message. */
  protected Agent senderHandle(String senderUrn, String senderName, String requestId) {
    var sender = new AgentImpl();
    sender.setUrn(senderUrn);
    sender.setName(senderName);
    sender.setViable(senderUrn != null && !senderUrn.isBlank());
    sender.setLocalSenderUrn(agentUrn);
    sender.setLocalSenderName(agentName);
    sender.setLocalResponseTo(requestId);
    if (creatingScope instanceof MessagingChannel messagingChannel) {
      sender.setLocalMessagingChannel(messagingChannel);
    }
    return sender;
  }

  private void stampSenderName(Object... messageArguments) {
    if (messageArguments == null) {
      return;
    }
    for (Object argument : messageArguments) {
      if (argument instanceof RuntimeAgent.CustomMessage customMessage) {
        stampSenderName(customMessage);
      } else if (argument instanceof Message message
          && message.getMessageType() == Message.MessageType.CustomAgentMessage) {
        stampSenderName(message.getPayload(RuntimeAgent.CustomMessage.class));
      }
    }
  }

  private void stampSenderName(RuntimeAgent.CustomMessage message) {
    if (message != null && (message.senderName() == null || message.senderName().isBlank())) {
      message.setSenderName(agentName);
    }
  }

  /** Implement the base {@code core.agent.tell} verb for an agent handle. */
  protected Object tellAgent(Object recipient, Object type, Object payload) {
    String recipientUrn = recipientAgentUrn(recipient);
    var message = customAgentMessage(type, payload);
    if (publishAgentMessage(
        recipientUrn, Message.MessageType.CustomAgentMessage, message)) {
      markActivity();
      return null;
    }
    if (recipient instanceof Agent agent) {
      agent.tell(message);
      return null;
    }
    throw new KlabActorException(this, "Agent messaging is not connected for " + recipientUrn);
  }

  /** API used by the implicit {@code core.agent} Java behavior. */
  public final Object tellAgentValue(Object recipient, Object type, Object payload) {
    return tellAgent(recipient, type, payload);
  }

  /** Implement the base {@code core.agent.ask} verb and return its correlated response future. */
  protected CompletableFuture<Object> askAgent(
      Object recipient, Object type, Object payload, Object timeout) {
    String recipientUrn = recipientAgentUrn(recipient);
    if (agentUrn == null) {
      return CompletableFuture.failedFuture(
          new KlabActorException(this, "The requesting runtime agent has no messaging URN"));
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    CompletableFuture<Object> future =
        (CompletableFuture)
            askAgentMessage(
                recipientUrn,
                customAgentMessage(type, payload),
                java.io.Serializable.class,
                Boolean.FALSE.equals(timeout) ? null : agentTimeout(timeout),
                !Boolean.FALSE.equals(timeout));
    markActivity();
    return future;
  }

  /** API used by the implicit {@code core.agent} Java behavior. */
  public final CompletableFuture<Object> askAgentValue(
      Object recipient, Object type, Object payload, Object timeout) {
    return askAgent(recipient, type, payload, timeout);
  }

  /** Resolve an agent recipient's URN for the implicit base behavior. */
  public final String agentUrn(Object recipient) {
    return recipientAgentUrn(recipient);
  }

  /** Resolve an agent recipient's display name for the implicit base behavior. */
  public final String agentName(Object recipient) {
    if (recipient instanceof Agent agent) {
      return agent.getName() == null || agent.getName().isBlank() ? "agent" : agent.getName();
    }
    if (recipient instanceof RuntimeAgent runtime) {
      return runtime.getName();
    }
    throw new KlabActorException(this, "core.agent verbs require a viable agent handle");
  }

  /** Bind the universal Java behavior to a concrete agent recipient. */
  protected final Object coreAgent(Object recipient) {
    return new CoreActorLibrary.Agent(recipient);
  }

  private String recipientAgentUrn(Object recipient) {
    String urn =
        recipient instanceof Agent agent
            ? agent.getUrn()
            : recipient instanceof RuntimeAgent runtime ? runtime.getUrn() : null;
    if (urn == null || urn.isBlank()) {
      throw new KlabActorException(this, "core.agent verbs require a viable agent handle");
    }
    return urn;
  }

  private RuntimeAgent.CustomMessage customAgentMessage(Object type, Object payload) {
    if (!(type instanceof Constant constant)) {
      throw new KlabActorException(this, "Agent message type must be a CONSTANT");
    }
    if (payload != null && !(payload instanceof java.io.Serializable)) {
      throw new KlabActorException(
          this, "Agent message payload " + payload.getClass().getName() + " is not serializable");
    }
    return new RuntimeAgent.CustomMessage(constant, (java.io.Serializable) payload);
  }

  private Duration agentTimeout(Object timeout) {
    if (timeout == null) {
      return null;
    }
    if (timeout instanceof Duration duration) {
      return duration;
    }
    if (timeout instanceof Quantity quantity) {
      return Duration.ofMillis(TimeDuration.of(quantity).getMilliseconds());
    }
    throw new KlabActorException(
        this, "Agent ask timeout must be a temporal Quantity or false through !timeout");
  }

  private void handleLifecycleEvent(Event event) {
    if (event.type() == EventType.EXCEPTION) {
      failureReported.set(true);
      publishStatus(Message.MessageType.AgentFailed, event.payload());
    } else if (event.type() == EventType.TERMINATION) {
      beforeTermination(event.payload());
      publishStatus(Message.MessageType.AgentStopped, event.payload());
      publishStatus(Message.MessageType.AgentStatusChanged, event.payload());
      closeMessaging();
    }
  }

  /**
   * Give specialized agents a chance to publish their final domain messages while messaging is
   * still connected. Scope disposables run after the termination event has been emitted, so they
   * are too late for transport-visible finalization.
   *
   * @param detail the value or failure that terminated the root scope
   */
  protected void beforeTermination(Object detail) {}

  private void publishStatus(Message.MessageType type, Object detail) {
    if (agentUrn == null) {
      return;
    }
    long timestamp = markActivity();
    publishAgentMessage(
        agentUrn,
        type,
        new RuntimeAgent.Status(
            agentUrn,
            failureReported.get()
                ? RuntimeAgent.State.FAILED
                : switch (status()) {
                  case "running" -> RuntimeAgent.State.RUNNING;
                  case "ready" -> RuntimeAgent.State.READY;
                  default -> RuntimeAgent.State.STOPPED;
                },
            !failureReported.get(),
            detail == null ? null : String.valueOf(detail),
            timestamp,
            observation == null ? Observation.UNASSIGNED_ID : observation.getId(),
            startedAt.get(),
            lastActivityAt.get()));
  }

  private long markActivity() {
    long timestamp = System.currentTimeMillis();
    lastActivityAt.accumulateAndGet(timestamp, Math::max);
    return timestamp;
  }

  private void notifyAgent(Notification notification) {
    handleNotification(notification);
    if (notificationConsumer != null) {
      notificationConsumer.accept(notification);
    }
  }

  private void requireAgentMessage(Message message) {
    if (message == null
        || message.getMessageClass() != Message.MessageClass.AgentCommunication) {
      throw new IllegalArgumentException(
          "Runtime agents only receive messages in the AgentCommunication class");
    }
  }

  /**
   * Resolve an imported actor at runtime. Callers embedding generated agents can pass explicit
   * bindings to the generated constructor. Component-backed deployments may override this method
   * to consult their component registry; unresolved imports fail only when invoked.
   */
  protected Object resolveImportedActor(
      String urn, String alias, Map<String, Object> explicitBindings) {
    if (explicitBindings != null) {
      if (explicitBindings.containsKey(alias)) {
        return explicitBindings.get(alias);
      }
      if (explicitBindings.containsKey(urn)) {
        return explicitBindings.get(urn);
      }
    }
    return new UnresolvedActor(urn, alias);
  }

  /**
   * Bind a compiled k.Actors import. Static actions are dispatched on one lazily constructed
   * behavior instance, while the synthetic {@code new} verb constructs and returns an independent
   * instance initialized with the call arguments.
   */
  protected Object resolveImportedBehavior(
      String urn,
      String alias,
      Map<String, Object> explicitBindings,
      ImportedBehaviorFactory factory) {
    if (explicitBindings != null) {
      if (explicitBindings.containsKey(alias)) {
        return explicitBindings.get(alias);
      }
      if (explicitBindings.containsKey(urn)) {
        return explicitBindings.get(urn);
      }
    }
    return new ImportedBehaviorBinding(urn, alias, factory);
  }

  @FunctionalInterface
  protected interface ImportedBehaviorFactory {
    RuntimeAgentBase create(Object[] initArguments);
  }

  private static final class ImportedBehaviorBinding {
    private final String urn;
    private final String alias;
    private final ImportedBehaviorFactory factory;
    private volatile RuntimeAgentBase staticTarget;

    private ImportedBehaviorBinding(String urn, String alias, ImportedBehaviorFactory factory) {
      this.urn = urn;
      this.alias = alias;
      this.factory = factory;
    }

    private RuntimeAgentBase create(Object[] arguments) {
      return factory.create(arguments == null ? new Object[0] : arguments);
    }

    private RuntimeAgentBase staticTarget() {
      var target = staticTarget;
      if (target == null) {
        synchronized (this) {
          target = staticTarget;
          if (target == null) {
            staticTarget = target = create(new Object[0]);
          }
        }
      }
      return target;
    }
  }

  protected record UnresolvedActor(String urn, String alias) {}

  /** Compile one analyzed k.Actors expression into the generated agent's immutable fields. */
  protected Expression compileExpression(String code) {
    var descriptor =
        new GroovyProcessor()
            .analyze(
                ExpressionCode.of(code, "groovy"),
                rootScope == null ? null : rootScope.getSession(),
                List.of(),
                List.of());
    return descriptor.compile();
  }

  protected Object evaluateExpression(
      Expression expression, AgentScope scope, Map<String, Object> frame) {
    org.integratedmodelling.klab.api.scope.Scope evaluationScope = null;
    if (scope != null) {
      evaluationScope = scope.getContext() == null ? scope.getSession() : scope.getContext();
    }
    if (frame == null || frame.isEmpty()) {
      return expression.eval(evaluationScope, Map.of());
    }
    var evaluatedFrame = new LinkedHashMap<String, Object>();
    frame.forEach((name, value) -> evaluatedFrame.put(name, resolveDeferred(value)));
    return expression.eval(evaluationScope, evaluatedFrame);
  }

  /**
   * Preserve a k.Actors back-ticked value until a consumer evaluates it.
   *
   * <p>The supplier is deliberately not memoized: a parameter or variable containing the returned
   * object behaves like a small lexical closure, and each use recomputes the original value in the
   * scope and frame captured by generated code.
   */
  protected Object defer(Supplier<Object> evaluator) {
    return new DeferredValue(Objects.requireNonNull(evaluator));
  }

  /** Evaluate one deferred value, or return an ordinary value unchanged. */
  protected Object resolveDeferred(Object value) {
    return value instanceof DeferredValue deferred ? deferred.evaluate() : value;
  }

  private record DeferredValue(Supplier<Object> evaluator) {
    private Object evaluate() {
      return evaluator.get();
    }

    private DeferredValue map(Function<Object, Object> mapper) {
      return new DeferredValue(() -> mapper.apply(evaluate()));
    }
  }

  protected Map<String, Object> bindArguments(List<String> names, Object... arguments) {
    var ret = new LinkedHashMap<String, Object>();
    var supplied = arguments == null ? new Object[0] : arguments;
    if (supplied.length == 1 && supplied[0] instanceof Map<?, ?> map) {
      map.forEach((key, value) -> ret.put(String.valueOf(key), value));
      return ret;
    }
    for (int i = 0; i < names.size(); i++) {
      ret.put(names.get(i), i < supplied.length ? supplied[i] : null);
    }
    return ret;
  }

  /**
   * Enforce the runtime portion of k.Actors {@code @type} parameter contracts.
   *
   * <p>Compile-time analysis invokes the same contracts whenever the supplied agent behavior or
   * Java return type is known. This guard covers calls whose recipient or values are discovered
   * only at runtime, including externally dispatched actions.
   */
  protected Object[] validateActionArguments(
      String action,
      List<String> names,
      List<String> requiredBehaviors,
      List<String> requiredJavaTypes,
      Object... arguments) {
    Object[] supplied = arguments == null ? new Object[0] : arguments;
    Map<String, Object> named = null;
    if (supplied.length == 1 && supplied[0] instanceof Map<?, ?> map) {
      named = new LinkedHashMap<>();
      for (var entry : map.entrySet()) {
        named.put(String.valueOf(entry.getKey()), entry.getValue());
      }
      supplied = new Object[] {named};
    }
    for (int index = 0; index < names.size(); index++) {
      String requiredBehavior =
          index < requiredBehaviors.size() ? requiredBehaviors.get(index) : null;
      String requiredJavaType =
          index < requiredJavaTypes.size() ? requiredJavaTypes.get(index) : null;
      if (requiredBehavior == null && requiredJavaType == null) {
        continue;
      }
      Object value =
          named == null
              ? (index < supplied.length ? supplied[index] : null)
              : named.get(names.get(index));
      if (value instanceof DeferredValue deferred) {
        var argumentName = names.get(index);
        value =
            deferred.map(
                resolved ->
                    validateActionArgument(
                        action,
                        argumentName,
                        requiredBehavior,
                        requiredJavaType,
                        resolved));
        if (named == null) {
          if (index < supplied.length) {
            supplied[index] = value;
          }
        } else {
          named.put(argumentName, value);
        }
        continue;
      }
      validateActionArgument(
          action, names.get(index), requiredBehavior, requiredJavaType, value);
    }
    return supplied;
  }

  private Object validateActionArgument(
      String action,
      String argumentName,
      String requiredBehavior,
      String requiredJavaType,
      Object value) {
    if (value == null) {
      throw new KlabActorException(
          this, "Argument '" + argumentName + "' for action " + action + " cannot be null");
    }
    if (requiredBehavior != null && !implementsBehavior(value, requiredBehavior)) {
      throw new KlabActorException(
          this,
          "Argument '"
              + argumentName
              + "' for action "
              + action
              + " requires an agent implementing "
              + requiredBehavior);
    }
    if (requiredJavaType != null && !matchesJavaType(value, requiredJavaType)) {
      throw new KlabActorException(
          this,
          "Argument '"
              + argumentName
              + "' for action "
              + action
              + " requires Java type "
              + requiredJavaType
              + ", but received "
              + value.getClass().getTypeName());
    }
    return value;
  }

  /**
   * Behavior URNs implemented by this generated runtime object. Generated classes override this so
   * the information remains available when dependency instances are constructed without their
   * parsed behavior bean.
   */
  protected Set<String> implementedBehaviorUrns() {
    if (behavior == null || behavior.getUrn() == null) {
      return Set.of();
    }
    return Set.of(behavior.getUrn());
  }

  private boolean implementsBehavior(Object value, String requiredBehavior) {
    if (value instanceof RuntimeAgentBase runtimeAgent) {
      return runtimeAgent.implementedBehaviorUrns().contains(requiredBehavior);
    }
    if (value instanceof Agent agent) {
      String actual = agent.getBehaviorUrn();
      return Objects.equals(actual, requiredBehavior)
          || (behaviorTypeChecker != null
              && behaviorTypeChecker.implementsBehavior(actual, requiredBehavior));
    }
    return false;
  }

  private boolean matchesJavaType(Object value, String requiredType) {
    Class<?> actual = value.getClass();
    if (!requiredType.contains(".")) {
      return actual.getSimpleName().equalsIgnoreCase(requiredType);
    }
    if (Objects.equals(actual.getName(), requiredType)) {
      return true;
    }
    try {
      Class<?> expected = Class.forName(requiredType, false, actual.getClassLoader());
      return expected.isAssignableFrom(actual);
    } catch (ClassNotFoundException | LinkageError ignored) {
      try {
        return Class.forName(requiredType).isAssignableFrom(actual);
      } catch (ClassNotFoundException | LinkageError alsoIgnored) {
        return false;
      }
    }
  }

  protected Map<String, Object> childFrame(Map<String, Object> parent) {
    return parent == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parent);
  }

  protected Object resolveIdentifier(String name, Map<String, Object> frame) {
    if ("self".equals(name)) {
      return this;
    }
    if (frame != null && frame.containsKey(name)) {
      return resolveDeferred(frame.get(name));
    }
    if (rootScope != null && rootScope.containsKey(name)) {
      return resolveDeferred(rootScope.get(name));
    }
    throw new KlabActorException(this, "Unresolved k.Actors identifier: " + name);
  }

  /**
   * Adapt a value to an object implementing the requested k.Actors behavior.
   *
   * <p>Generated local assignments carrying an {@code as <behavior-urn>} clause call this method
   * after evaluating the original value and before storing it in the frame. Specialized runtime
   * agents or component-backed bases may override it to perform the actual conversion.
   *
   * @param value unadapted assignment value
   * @param behaviorUrn target behavior URN validated by the compiler environment
   * @param scope current action scope
   * @return the adapted object
   */
  protected Object adaptToBehavior(Object value, String behaviorUrn, AgentScope scope) {
    var local = behaviorAdapters.get(behaviorUrn);
    if (local != null) {
      return switch (local.executionType()) {
        case FUNCTION -> local.target().invokeSelfFunction(local.action(), scope, value);
        case SUPPLIER ->
            local.target().invokeSelfSupplier(local.action(), scope, value).join();
        case EMITTER ->
            throw new KlabActorException(
                this, "The @adapt action for " + behaviorUrn + " is an emitter");
      };
    }
    if (externalBehaviorAdapter != null) {
      return externalBehaviorAdapter.adapt(behaviorUrn, value, scope);
    }
    throw new KlabActorException(
        this, "No runtime adapter is available for behavior " + behaviorUrn);
  }

  /**
   * Convert an already behavior-adapted value for use as a condition. Specialized bases may
   * override this when adapted agents expose a stricter truth contract.
   */
  protected boolean adaptToBoolean(Object value, AgentScope scope) {
    return truthy(value);
  }

  /**
   * Convert an already behavior-adapted value for iteration. Specialized bases may override this
   * when an adapted agent exposes its own iteration contract.
   */
  protected Iterable<?> adaptToIterable(Object value, AgentScope scope) {
    return asIterable(value);
  }

  /**
   * Internal control-flow signal used by generated functional switches. Reactive match yields
   * complete their enclosing action directly and do not use this signal. Public visibility is
   * required because source generation happens in the compiler package while the generated
   * subclass catches the signal.
   */
  public static final class SwitchYield extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Object value;

    public SwitchYield(Object value) {
      super(null, null, false, false);
      this.value = value;
    }

    public Object value() {
      return value;
    }
  }

  protected void setActorState(String name, Object value) {
    rootScope.put(name, value);
  }

  protected Object literalValue(ValueType type, String encoded) {
    // Extension point for ranges, localized strings and other non-POD literals whose definitive
    // runtime mediation belongs to language/runtime services. Quantities and semantic observables
    // use typed hooks so their models are not reduced to text.
    return encoded;
  }

  /** Reconstruct a source quantity without relying on environment-specific parsing. */
  protected Quantity quantityLiteral(Number value, String unit, String currency) {
    if (value == null) {
      throw new KlabActorException(this, "Missing numeric value in quantity literal");
    }
    var ret = new QuantityImpl();
    ret.setValue(value);
    ret.setUnit(unit);
    ret.setCurrency(currency);
    return ret;
  }

  /** Compatibility path for semantic models that still store a quantity as source text. */
  protected Quantity quantityLiteral(String encoded) {
    Quantity quantity = encoded == null ? null : QuantityImpl.parse(encoded);
    if (quantity == null) {
      throw new KlabActorException(this, "Invalid quantity literal: " + encoded);
    }
    return quantity;
  }

  /**
   * Reconstruct a compiler-embedded semantic literal without degrading it to its textual URN.
   * Values are cached per agent so repeated evaluation of the same source literal retains stable
   * object identity as well as its complete {@link KimObservable} semantic model. This method is
   * deliberately protected: the polymorphic JSON is produced from the already-adapted source bean
   * by {@code AgentCompiler} and must not become a general deserialization endpoint.
   */
  protected KimObservable observableLiteral(String serialized) {
    if (serialized == null || serialized.isBlank()) {
      throw new KlabActorException(this, "Missing serialized observable literal");
    }
    return observableLiterals.computeIfAbsent(
        serialized,
        key -> {
          try {
            return KLAB_OBJECT_MAPPER.readValue(key, KimObservable.class);
          } catch (Exception error) {
            throw new KlabActorException(this, error);
          }
        });
  }

  /** Reconstruct the complete semantic assertion bean embedded by the agent compiler. */
  protected KActorsStatement.Assert.Assertion assertionLiteral(String serialized) {
    if (serialized == null || serialized.isBlank()) {
      throw new KlabActorException(this, "Missing serialized assertion");
    }
    return assertionLiterals.computeIfAbsent(
        serialized,
        key -> {
          try {
            return KLAB_OBJECT_MAPPER.readValue(
                key, KActorsStatement.Assert.Assertion.class);
          } catch (Exception error) {
            throw new KlabActorException(this, error);
          }
        });
  }

  /** Build a mutable insertion-ordered map for a compiled k.Actors map literal. */
  protected Map<Object, Object> mutableMap(Object[] keys, Object[] values) {
    if (keys == null || values == null || keys.length != values.length) {
      throw new KlabActorException(this, "Invalid compiled map literal");
    }
    var ret = new LinkedHashMap<Object, Object>();
    for (int index = 0; index < keys.length; index++) {
      ret.put(keys[index], values[index]);
    }
    return ret;
  }

  /**
   * Adapt one compiled argument to an ordinary Java method parameter. This deliberately shares the
   * same coercion rules as reflective dynamic invocation.
   */
  protected Object adaptJavaArgument(Object value, Class<?> target) {
    return coerceArgument(resolveDeferred(value), target);
  }

  protected boolean truthy(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value instanceof Number number) {
      return number.doubleValue() != 0;
    }
    if (value instanceof CharSequence sequence) {
      return !sequence.isEmpty();
    }
    if (value instanceof Collection<?> collection) {
      return !collection.isEmpty();
    }
    if (value instanceof Map<?, ?> map) {
      return !map.isEmpty();
    }
    return true;
  }

  protected Iterable<?> asIterable(Object value) {
    if (value instanceof Iterable<?> iterable) {
      return iterable;
    }
    if (value != null && value.getClass().isArray()) {
      var ret = new ArrayList<>();
      for (int i = 0; i < Array.getLength(value); i++) {
        ret.add(Array.get(value, i));
      }
      return ret;
    }
    throw new KlabActorException(this, "Value is not iterable: " + value);
  }

  protected boolean matches(
      Object payload, ValueType type, Object criterion, boolean exclusive) {
    boolean match =
        switch (type) {
          case ANYTHING -> true;
          case ANYVALUE, ANYTRUE -> truthy(payload);
          case NODATA -> payload == null;
          case EMPTY ->
              payload == null
                  || (payload instanceof Collection<?> collection && collection.isEmpty())
                  || (payload instanceof Map<?, ?> map && map.isEmpty())
                  || (payload instanceof CharSequence sequence && sequence.isEmpty());
          case ERROR -> isErrorPayload(payload);
          case ANNOTATION ->
              criterion != null
                  && Utils.Annotations.hasOrInheritsAnnotation(payload, criterion.toString());
          case REGEXP ->
              criterion instanceof java.util.regex.Pattern pattern
                  && payload != null
                  && pattern.matcher(payload.toString()).matches();
          case CLASS, TYPE ->
              criterion instanceof Class<?> cls && payload != null && cls.isInstance(payload);
          case LIST, SET -> criterion instanceof Collection<?> set && set.contains(payload);
          case CONSTANT ->
              payload != null
                  && criterion != null
                  && Objects.equals(constantText(payload), constantText(criterion));
          default -> Objects.equals(payload, criterion);
        };
    return exclusive ? !match : match;
  }

  private String constantText(Object value) {
    return value instanceof Constant constant ? constant.getValue() : String.valueOf(value);
  }

  private boolean isErrorPayload(Object payload) {
    return payload instanceof Throwable
        || (payload instanceof Notification notification
            && notification.getLevel() != null
            && notification.getLevel().severity >= Notification.Level.Error.severity);
  }

  protected void bindMatch(
      Map<String, Object> frame,
      Object payload,
      List<String> variables,
      String captureAs) {
    if (captureAs != null && !captureAs.isBlank()) {
      frame.put(captureAs, payload);
    }
    if (variables == null || variables.isEmpty()) {
      return;
    }
    List<?> values;
    if (payload instanceof List<?> list) {
      values = list;
    } else if (payload != null && payload.getClass().isArray()) {
      var arrayValues = new ArrayList<>();
      for (int i = 0; i < Array.getLength(payload); i++) {
        arrayValues.add(Array.get(payload, i));
      }
      values = arrayValues;
    } else {
      values = Collections.singletonList(payload);
    }
    for (int i = 0; i < variables.size(); i++) {
      frame.put(variables.get(i), i < values.size() ? values.get(i) : null);
    }
  }

  protected Object invokeFunction(
      Object actor, String verb, AgentScope scope, Object... arguments) {
    Object ret = invokeActor(actor, verb, scope, arguments);
    if (ret instanceof CompletableFuture<?> future) {
      return future.join();
    }
    return ret;
  }

  @SuppressWarnings("unchecked")
  protected CompletableFuture<Object> invokeSupplier(
      Object actor, String verb, AgentScope scope, Object... arguments) {
    Object ret = invokeActor(actor, verb, scope, arguments);
    if (ret instanceof CompletableFuture<?> future) {
      return (CompletableFuture<Object>) future;
    }
    return CompletableFuture.completedFuture(ret);
  }

  protected void invokeEmitter(
      Object actor, String verb, AgentScope scope, Object... arguments) {
    invokeActor(actor, verb, scope, arguments);
  }

  protected Object invokeSelfFunction(String action, AgentScope scope, Object... arguments) {
    return invokeGeneratedAction(action, scope, arguments);
  }

  protected CompletableFuture<Object> invokeSelfSupplier(
      String action, AgentScope scope, Object... arguments) {
    Object ret = invokeGeneratedAction(action, scope, arguments);
    if (ret instanceof CompletableFuture<?> future) {
      @SuppressWarnings("unchecked")
      var typed = (CompletableFuture<Object>) future;
      return typed;
    }
    return CompletableFuture.completedFuture(ret);
  }

  protected void invokeSelfEmitter(String action, AgentScope scope, Object... arguments) {
    invokeGeneratedAction(action, scope, arguments);
  }

  /**
   * Invoke a verb whose function/supplier/emitter nature could not be established during
   * validation. The selected runtime method determines the contract: a normal return is a
   * function result, {@link CompletableFuture} is a supplier, and {@code void} is an emitter.
   * Results are relayed through the supplied action scope so compiled match actions and lifecycle
   * barriers continue to use the normal event model.
   */
  protected ExitValue runDynamicVerb(
      Object actor, String verb, AgentScope scope, Object... arguments) {
    if (rootScope != null && rootScope.isDone()) {
      return ExitValue.failure(new KlabIllegalStateException("Agent already terminated"));
    }
    dynamicCallStarted();
    try {
      Thread.ofVirtual()
          .name("kactors-dynamic-" + scope.actionId())
          .start(
              () -> {
                boolean completionDeferred = false;
                try {
                  var invocation = invokeActorDynamically(actor, verb, scope, arguments);
                  switch (invocation.type()) {
                    case FUNCTION -> scope.doReturn(invocation.value());
                    case SUPPLIER -> {
                      @SuppressWarnings("unchecked")
                      var future = (CompletableFuture<Object>) invocation.value();
                      scope.disposeWith(() -> future.cancel(true));
                      completionDeferred = true;
                      try {
                        future.whenComplete(
                            (value, error) -> {
                              try {
                                if (error == null) {
                                  scope.doReturn(value);
                                } else {
                                  scope.done(error);
                                }
                              } catch (Throwable t) {
                                scope.done(t);
                                dynamicFailure(t);
                              } finally {
                                dynamicCallFinished(error);
                              }
                            });
                      } catch (Throwable t) {
                        completionDeferred = false;
                        throw t;
                      }
                    }
                    case EMITTER -> scope.awaitDone();
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  scope.done(e);
                  dynamicFailure(e);
                } catch (Throwable t) {
                  scope.done(t);
                  dynamicFailure(t);
                } finally {
                  if (!completionDeferred) {
                    dynamicCallFinished(null);
                  }
                }
              });
      return TASK_RUNNING;
    } catch (Throwable t) {
      scope.done(t);
      dynamicCallFinished(t);
      return ExitValue.failure(t);
    }
  }

  /**
   * Mark the statically compiled part of {@code main} as complete while unresolved dynamic calls
   * are still being classified and executed. The root scope completes when every dynamic function
   * or supplier has settled. A dynamic emitter keeps the root alive until its action scope is
   * explicitly completed.
   */
  protected ExitValue awaitDynamicCalls(Object result) {
    synchronized (dynamicLifecycleLock) {
      dynamicMainCompleted = true;
      dynamicMainResult = result;
    }
    finishDynamicLifecycleIfReady();
    return TASK_RUNNING;
  }

  /** Error counterpart of {@link #awaitDynamicCalls(Object)}. */
  protected ExitValue failDynamicCalls(Throwable error) {
    synchronized (dynamicLifecycleLock) {
      dynamicMainCompleted = true;
      if (dynamicFailure == null) {
        dynamicFailure = error;
      }
      if (dynamicRootCompleted) {
        return ExitValue.failure(error);
      }
      dynamicRootCompleted = true;
    }
    rootScope.done(error);
    return ExitValue.failure(error);
  }

  /** Resolve an unknown call used as a value, joining suppliers and returning null for emitters. */
  protected Object invokeDynamicValue(
      Object actor, String verb, AgentScope scope, Object... arguments) {
    var invocation = invokeActorDynamically(actor, verb, scope, arguments);
    return switch (invocation.type()) {
      case FUNCTION -> invocation.value();
      case SUPPLIER -> ((CompletableFuture<?>) invocation.value()).join();
      case EMITTER -> null;
    };
  }

  private Object invokeGeneratedAction(String action, AgentScope scope, Object... arguments) {
    RuntimeAgentBase target = generatedActionTarget(action);
    if (target == null) {
      throw actorFailure(new NoSuchMethodException("action_" + action));
    }
    try {
      Method method = findMethod(target.getClass(), "action_" + action, false);
      AgentScope actionScope = scope.forAction(action);
      return method.invoke(target, actionScope, splitSuppliedArguments(arguments).values());
    } catch (InvocationTargetException e) {
      throw actorFailure(e.getTargetException());
    } catch (ReflectiveOperationException e) {
      throw actorFailure(e);
    }
  }

  private RuntimeAgentBase generatedActionTarget(String action) {
    try {
      findMethod(getClass(), "action_" + action, false);
      return this;
    } catch (NoSuchMethodException ignored) {
      for (var inherited : inheritedBehaviorInstances) {
        var target = inherited.generatedActionTarget(action);
        if (target != null) {
          return target;
        }
      }
      return null;
    }
  }

  private Object invokeActor(Object actor, String verb, AgentScope scope, Object... arguments) {
    return invokeActorDynamically(actor, verb, scope, arguments).value();
  }

  private record DynamicInvocation(Verb.Type type, Object value) {}

  private void dynamicCallStarted() {
    synchronized (dynamicLifecycleLock) {
      activeDynamicCalls++;
    }
  }

  private void dynamicFailure(Throwable error) {
    synchronized (dynamicLifecycleLock) {
      if (dynamicFailure == null) {
        dynamicFailure = error;
      }
    }
  }

  private void dynamicCallFinished(Throwable error) {
    synchronized (dynamicLifecycleLock) {
      if (error != null && dynamicFailure == null) {
        dynamicFailure = error;
      }
      activeDynamicCalls--;
    }
    finishDynamicLifecycleIfReady();
  }

  private void finishDynamicLifecycleIfReady() {
    Object result;
    Throwable failure;
    synchronized (dynamicLifecycleLock) {
      if (!dynamicMainCompleted || activeDynamicCalls != 0 || dynamicRootCompleted) {
        return;
      }
      dynamicRootCompleted = true;
      result = dynamicMainResult;
      failure = dynamicFailure;
    }
    if (failure == null) {
      rootScope.done(result);
    } else {
      rootScope.done(failure);
    }
  }

  private DynamicInvocation invokeActorDynamically(
      Object actor, String verb, AgentScope scope, Object... arguments) {
    if (actor instanceof UnresolvedActor unresolved) {
      throw new KlabActorException(
          this,
          "Imported actor '" + unresolved.alias() + "' (" + unresolved.urn() + ") was not bound");
    }
    if (actor instanceof ImportedBehaviorBinding importedBehavior) {
      if ("new".equals(verb)) {
        return new DynamicInvocation(
            Verb.Type.FUNCTION,
            importedBehavior.create(splitSuppliedArguments(arguments).values()));
      }
      actor = importedBehavior.staticTarget();
    }
    if (actor instanceof RuntimeAgentBase runtimeAgent) {
      try {
        Method method = findMethod(runtimeAgent.getClass(), "action_" + verb, false);
        return invokeDynamically(
            method,
            runtimeAgent,
            scope,
            splitSuppliedArguments(arguments).values());
      } catch (ReflectiveOperationException e) {
        throw actorFailure(e);
      }
    }
    Class<?> actorClass = actor instanceof Class<?> cls ? cls : actor.getClass();
    Object target = actor instanceof Class<?> ? null : actor;
    try {
      if (target == null && "new".equals(verb)) {
        try {
          Method factory = findActorMethod(actorClass, verb, true, scope, arguments);
          return invokeDynamically(factory, null, scope, arguments);
        } catch (NoSuchMethodException noFactory) {
          return new DynamicInvocation(
              Verb.Type.FUNCTION, constructActor(actorClass, scope, arguments));
        }
      }
      Method method = findActorMethod(actorClass, verb, target == null, scope, arguments);
      return invokeDynamically(method, target, scope, arguments);
    } catch (InvocationTargetException e) {
      throw actorFailure(e.getTargetException());
    } catch (ReflectiveOperationException | IllegalArgumentException e) {
      throw actorFailure(e);
    }
  }

  private DynamicInvocation invokeDynamically(
      Method method, Object target, AgentScope scope, Object[] arguments)
      throws ReflectiveOperationException {
    Object[] actualArguments = prepareArguments(method, scope, arguments);
    Object value =
        method.invoke(Modifier.isStatic(method.getModifiers()) ? null : target, actualArguments);
    Verb.Type type = dynamicMethodType(method);
    return new DynamicInvocation(type, value);
  }

  private Object constructActor(Class<?> actorClass, AgentScope scope, Object[] arguments)
      throws ReflectiveOperationException {
    String mismatch = null;
    for (Constructor<?> constructor : actorClass.getConstructors()) {
      try {
        Object[] actual = prepareConstructorArguments(constructor, scope, arguments);
        return constructor.newInstance(actual);
      } catch (IllegalArgumentException failure) {
        mismatch = failure.getMessage();
      }
    }
    throw new NoSuchMethodException(
        "No public constructor of "
            + actorClass.getName()
            + " accepts the supplied arguments"
            + (mismatch == null ? "" : ": " + mismatch));
  }

  private Object[] prepareConstructorArguments(
      Constructor<?> constructor, AgentScope scope, Object[] supplied) {
    try {
      return prepareConstructorArgumentsDirect(constructor, scope, supplied);
    } catch (IllegalArgumentException directFailure) {
      if (parameterNegotiator == null) {
        throw directFailure;
      }
      var split = splitSuppliedArguments(supplied);
      var expected = new ArrayList<Class<?>>();
      for (int index = 0; index < constructor.getParameterCount(); index++) {
        Class<?> parameter = constructor.getParameterTypes()[index];
        if (!RuntimeAgent.Scope.class.isAssignableFrom(parameter)
            && !Metadata.class.isAssignableFrom(parameter)) {
          if (constructor.isVarArgs() && index == constructor.getParameterCount() - 1) {
            Class<?> component = parameter.getComponentType();
            while (expected.size() < split.values().length) {
              expected.add(component);
            }
          } else {
            expected.add(parameter);
          }
        }
      }
      var suppliedValues = new ArrayList<Object>();
      Collections.addAll(suppliedValues, split.values());
      var actual =
          parameterNegotiator.negotiate(
              List.copyOf(expected), Collections.unmodifiableList(suppliedValues));
      if (actual == null) {
        throw directFailure;
      }
      return prepareConstructorArgumentsDirect(
          constructor, scope, withVerbMetadata(actual.toArray(), split.metadata()));
    }
  }

  private Object[] prepareConstructorArgumentsDirect(
      Constructor<?> constructor, AgentScope scope, Object[] suppliedArguments) {
    var split = splitSuppliedArguments(suppliedArguments);
    Object[] supplied = split.values();
    Metadata metadata = split.metadata();
    var parameters = constructor.getParameterTypes();
    var ret = new Object[parameters.length];
    boolean hasExplicitMetadata =
        java.util.Arrays.stream(parameters).anyMatch(Metadata.class::isAssignableFrom);
    int source = 0;
    for (int target = 0; target < parameters.length; target++) {
      Class<?> parameter = parameters[target];
      if (RuntimeAgent.Scope.class.isAssignableFrom(parameter)) {
        ret[target] = scope;
      } else if (Metadata.class.isAssignableFrom(parameter)) {
        ret[target] = metadata == null ? Metadata.create() : metadata;
      } else if (constructor.isVarArgs() && target == parameters.length - 1) {
        Class<?> component = parameter.getComponentType();
        boolean appendMetadata =
            !hasExplicitMetadata && component == Object.class && metadata != null;
        Object array =
            Array.newInstance(component, supplied.length - source + (appendMetadata ? 1 : 0));
        for (int index = source; index < supplied.length; index++) {
          Array.set(array, index - source, coerceArgument(supplied[index], component));
        }
        if (appendMetadata) {
          Array.set(array, supplied.length - source, metadata);
        }
        ret[target] = array;
        source = supplied.length;
      } else if (source < supplied.length) {
        ret[target] = coerceArgument(supplied[source++], parameter);
      } else {
        throw new IllegalArgumentException("Not enough constructor arguments");
      }
    }
    if (source != supplied.length) {
      throw new IllegalArgumentException("Too many constructor arguments");
    }
    return ret;
  }

  private Verb.Type dynamicMethodType(Method method) {
    var annotation = method.getAnnotation(Verb.class);
    if (annotation != null) {
      if (annotation.fires() != Void.class) {
        return Verb.Type.EMITTER;
      }
      if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
        return Verb.Type.SUPPLIER;
      }
      return annotation.executionType();
    }
    return CompletableFuture.class.isAssignableFrom(method.getReturnType())
        ? Verb.Type.SUPPLIER
        : Verb.Type.FUNCTION;
  }

  private Method findActorMethod(
      Class<?> actorClass,
      String verb,
      boolean requireStatic,
      AgentScope scope,
      Object[] arguments)
      throws NoSuchMethodException {
    String mismatch = null;
    String camelName = lowerUnderscoreToCamel(verb);
    var methodNames = new ArrayList<String>();
    methodNames.add(verb);
    if (!Objects.equals(verb, camelName)) {
      methodNames.add(camelName);
    }
    if (splitSuppliedArguments(arguments).values().length == 0 && !camelName.isBlank()) {
      String property = Character.toUpperCase(camelName.charAt(0)) + camelName.substring(1);
      methodNames.add("get" + property);
      methodNames.add("is" + property);
    }
    for (String methodName : methodNames) {
      for (Method method : actorClass.getMethods()) {
        var annotation = method.getAnnotation(Verb.class);
        String exposedName =
            annotation == null || annotation.name().isBlank()
                ? method.getName()
                : annotation.name();
        if ((annotation == null ? exposedName.equals(methodName) : exposedName.equals(verb))
            && method.getDeclaringClass() != Object.class
            && (!requireStatic || Modifier.isStatic(method.getModifiers()))) {
          try {
            prepareArguments(method, scope, arguments);
            method.setAccessible(true);
            return method;
          } catch (IllegalArgumentException failure) {
            mismatch = failure.getMessage();
          }
        }
      }
    }
    throw new NoSuchMethodException(
        actorClass.getName() + "." + verb + (mismatch == null ? "" : ": " + mismatch));
  }

  private String lowerUnderscoreToCamel(String name) {
    var ret = new StringBuilder(name.length());
    boolean uppercase = false;
    for (int index = 0; index < name.length(); index++) {
      char c = name.charAt(index);
      if (c == '_') {
        uppercase = ret.length() > 0;
      } else if (uppercase) {
        ret.append(Character.toUpperCase(c));
        uppercase = false;
      } else {
        ret.append(c);
      }
    }
    return ret.toString();
  }

  private Method findMethod(Class<?> type, String name, boolean publicOnly)
      throws NoSuchMethodException {
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      for (Method method : current.getDeclaredMethods()) {
        if (method.getName().equals(name)) {
          method.setAccessible(true);
          return method;
        }
      }
      if (publicOnly) {
        break;
      }
    }
    throw new NoSuchMethodException(type.getName() + "." + name);
  }

  private Object[] prepareArguments(Method method, AgentScope scope, Object[] supplied) {
    try {
      return prepareArgumentsDirect(method, scope, supplied);
    } catch (IllegalArgumentException directFailure) {
      if (parameterNegotiator == null) {
        throw parameterMismatch(method, supplied, directFailure);
      }
      var split = splitSuppliedArguments(supplied);
      var expected = negotiableParameterTypes(method, split.values().length);
      var suppliedValues = new ArrayList<Object>(split.values().length);
      Collections.addAll(suppliedValues, split.values());
      var negotiated =
          parameterNegotiator.negotiate(
              expected, Collections.unmodifiableList(suppliedValues));
      if (negotiated == null) {
        throw parameterMismatch(method, supplied, directFailure);
      }
      try {
        return prepareArgumentsDirect(
            method, scope, withVerbMetadata(negotiated.toArray(), split.metadata()));
      } catch (IllegalArgumentException negotiatedFailure) {
        var mismatch = parameterMismatch(method, supplied, negotiatedFailure);
        mismatch.addSuppressed(directFailure);
        throw mismatch;
      }
    }
  }

  private Object[] prepareArgumentsDirect(Method method, AgentScope scope, Object[] supplied) {
    var split = splitSuppliedArguments(supplied);
    supplied = split.values();
    Metadata metadata = split.metadata();
    var parameters = method.getParameterTypes();
    var ret = new Object[parameters.length];
    boolean hasExplicitMetadata =
        java.util.Arrays.stream(parameters).anyMatch(Metadata.class::isAssignableFrom);
    int source = 0;
    for (int target = 0; target < parameters.length; target++) {
      Class<?> parameter = parameters[target];
      if (RuntimeAgent.Scope.class.isAssignableFrom(parameter)) {
        ret[target] = scope;
      } else if (Metadata.class.isAssignableFrom(parameter)) {
        ret[target] = metadata == null ? Metadata.create() : metadata;
      } else if (method.isVarArgs() && target == parameters.length - 1) {
        Class<?> component = parameter.getComponentType();
        boolean appendMetadata =
            !hasExplicitMetadata && component == Object.class && metadata != null;
        Object array =
            Array.newInstance(component, supplied.length - source + (appendMetadata ? 1 : 0));
        for (int i = source; i < supplied.length; i++) {
          Array.set(array, i - source, coerceArgument(supplied[i], component));
        }
        if (appendMetadata) {
          Array.set(array, supplied.length - source, metadata);
        }
        ret[target] = array;
        source = supplied.length;
      } else if (source < supplied.length) {
        ret[target] = coerceArgument(supplied[source++], parameter);
      } else {
        throw new IllegalArgumentException("Not enough arguments for " + method);
      }
    }
    if (source != supplied.length) {
      throw new IllegalArgumentException("Too many arguments for " + method);
    }
    return ret;
  }

  private List<Class<?>> negotiableParameterTypes(Method method, int suppliedCount) {
    var ret = new ArrayList<Class<?>>();
    for (int index = 0; index < method.getParameterCount(); index++) {
      var parameter = method.getParameterTypes()[index];
      if (RuntimeAgent.Scope.class.isAssignableFrom(parameter)) {
        continue;
      }
      if (Metadata.class.isAssignableFrom(parameter)) {
        continue;
      }
      if (method.isVarArgs() && index == method.getParameterCount() - 1) {
        Class<?> component = parameter.getComponentType();
        while (ret.size() < suppliedCount) {
          ret.add(component);
        }
      } else {
        ret.add(parameter);
      }
    }
    return List.copyOf(ret);
  }

  private IllegalArgumentException parameterMismatch(
      Method method, Object[] supplied, IllegalArgumentException cause) {
    return new IllegalArgumentException(
        "Cannot match Java verb parameters for "
            + method.getDeclaringClass().getName()
            + "."
            + method.getName()
            + ": expected "
            + negotiableParameterSignature(method)
            + " but received "
            + splitSuppliedArguments(supplied).values().length
            + " argument(s); parameter negotiation did not produce a compatible match",
        cause);
  }

  private List<String> negotiableParameterSignature(Method method) {
    var ret = new ArrayList<String>();
    for (int index = 0; index < method.getParameterCount(); index++) {
      var parameter = method.getParameterTypes()[index];
      if (RuntimeAgent.Scope.class.isAssignableFrom(parameter)
          || Metadata.class.isAssignableFrom(parameter)) {
        continue;
      }
      ret.add(
          method.isVarArgs() && index == method.getParameterCount() - 1
              ? parameter.getComponentType().getTypeName() + "..."
              : parameter.getTypeName());
    }
    return List.copyOf(ret);
  }

  /**
   * Attach inline metadata to a generated invocation without making it an ordinary k.Actors
   * argument. Java verbs receive it through a {@link Metadata} parameter or, when no such
   * parameter exists, as the last element of an {@code Object...} parameter.
   */
  protected Object[] withVerbMetadata(Object[] arguments, Metadata metadata) {
    Object[] values = arguments == null ? new Object[0] : arguments;
    if (metadata == null || metadata.isEmpty()) {
      return values;
    }
    Object[] ret = java.util.Arrays.copyOf(values, values.length + 1);
    ret[values.length] = new VerbMetadata(metadata);
    return ret;
  }

  private SuppliedArguments splitSuppliedArguments(Object[] supplied) {
    Object[] values = supplied == null ? new Object[0] : supplied;
    if (values.length > 0 && values[values.length - 1] instanceof VerbMetadata metadata) {
      return new SuppliedArguments(
          java.util.Arrays.copyOf(values, values.length - 1), metadata.metadata());
    }
    return new SuppliedArguments(values, null);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Object coerceArgument(Object value, Class<?> target) {
    value = resolveDeferred(value);
    if (value == null || target.isInstance(value)) {
      return value;
    }
    if (target.isPrimitive() && value instanceof Number number) {
      if (target == int.class) return number.intValue();
      if (target == long.class) return number.longValue();
      if (target == double.class) return number.doubleValue();
      if (target == float.class) return number.floatValue();
      if (target == short.class) return number.shortValue();
      if (target == byte.class) return number.byteValue();
    }
    if (target == boolean.class && value instanceof Boolean) {
      return value;
    }
    if (target.isEnum() && value instanceof CharSequence text) {
      return Enum.valueOf((Class<? extends Enum>) target, text.toString());
    }
    if (target == String.class) {
      return value.toString();
    }
    // TODO mediate Quantity into value/unit parameters and use descriptor-declared conversions.
    throw new IllegalArgumentException(
        "Cannot coerce " + value.getClass().getName() + " to " + target.getName());
  }

  private KlabActorException actorFailure(Throwable throwable) {
    return throwable instanceof KlabActorException actorException
        ? actorException
        : new KlabActorException(this, throwable);
  }

  protected void handleText(String text, AgentScope scope, Map<String, Object> frame) {
    scope.getPrintWriter().print(text);
  }

  protected void assertValue(Object actual, Object expected) {
    assertValue(actual, expected, expected != null);
  }

  private void assertValue(Object actual, Object expected, boolean expectedSupplied) {
    boolean success = expectedSupplied ? Objects.equals(actual, expected) : truthy(actual);
    if (!success) {
      String expectation = expectedSupplied ? String.valueOf(expected) : "a truthy value";
      throw new AssertionError(
          "k.Actors assertion failed: expected " + expectation + ", got " + actual);
    }
  }

  /**
   * Evaluate a compiled assertion inside the runtime so outcomes and evaluation failures can be
   * reported together with the complete semantic assertion bean.
   */
  protected void assertValue(
      Supplier<Object> actual,
      Supplier<Object> expected,
      KActorsStatement.Assert.Assertion assertion,
      AgentScope scope) {
    try {
      assertValue(actual.get(), expected == null ? null : expected.get(), expected != null);
    } catch (RuntimeException | Error failure) {
      try {
        assertionEvaluated(scope, assertion, false, failure);
      } catch (RuntimeException | Error callbackFailure) {
        failure.addSuppressed(callbackFailure);
      }
      throw failure;
    }
    assertionEvaluated(scope, assertion, true, null);
  }

  /** Hook specialized runtime scopes use to collect assertion results. */
  protected void assertionEvaluated(
      AgentScope scope,
      KActorsStatement.Assert.Assertion assertion,
      boolean success,
      Throwable exception) {}

  /** Return the immutable semantic annotations declared on a generated action. */
  protected List<Annotation> actionAnnotations(String actionName) {
    if (behavior == null || behavior.getStatements() == null || actionName == null) {
      return List.of();
    }
    return behavior.getStatements().stream()
        .filter(action -> Objects.equals(actionName, action.getUrn()))
        .findFirst()
        .map(
            action ->
                action.getAnnotations() == null
                    ? List.<Annotation>of()
                    : List.copyOf(action.getAnnotations()))
        .orElseGet(List::of);
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
   * Subscribe to events emitted in a new {@code scope} created for a new action. Used to register
   * reactions within a call to {@link #runEmitter(AgentScope, Consumer)} or {@link
   * #runSupplier(AgentScope, Function)} that defines and starts an emitter or supplier.
   *
   * <p>The normal usage pattern is:
   *
   * <pre>
   *         runEmitter(
   *           // this sets up the scope to react to, register the reaction, and returns
   *           // the scope for the runner
   *           onEvent(
   *             currentScope,
   *             (event, scope) -> {
   *               // --- code compiled from the action body after the timer verb
   *               // ...
   *               // --- end of code
   *             },
   *             EventType.FIRE),
   *           // --- code that starts the emitter process
   *           scope -> { void emitter call(s) }
   *           // --- end of emitter
   *         );
   * </pre>
   *
   * and similarly for suppliers, using {@link #runSupplier(AgentScope, Function)}, the supplier
   * code producing a {@link CompletableFuture}, and EventType.RETURN as the trigger.
   *
   * <pre>
   *
   * </pre>
   *
   * @return the scope to use for the runXXX function, with an ID that's unique within the agent.
   */
  protected AgentScope onEvent(
      AgentScope parentScope,
      BiConsumer<Event, AgentScope> eventConsumer,
      EventType... triggerEvents) {
    var scope = parentScope.withId(nextId.incrementAndGet());
    var acceptedTypes = acceptedEventTypes(triggerEvents);
    Disposable subscription =
        this.eventBus
            .asFlux()
            .filter(event -> event.actionId() == scope.actionId())
            .takeUntil(event -> event.type() == EventType.TERMINATION)
            .filter(event -> acceptedTypes.contains(event.type()))
            .subscribe(
                event -> {
                  try {
                    eventConsumer.accept(event, parentScope);
                  } catch (Throwable t) {
                    parentScope.done(t);
                  }
                },
                scope::done);
    scope.disposeWith(subscription);
    return scope;
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
   * Call to start a supplier after having called {@link #onEvent(AgentScope, BiConsumer,
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
   * Complete the first-value signal generated for a reactive call. A normal RETURN or FIRE releases
   * a following {@code then}; an exception propagates through the barrier instead of allowing the
   * sequential statement to run.
   */
  protected void completeReaction(CompletableFuture<Void> completion, Event event) {
    if (completion == null || completion.isDone()) {
      return;
    }
    if (event != null && event.type() == EventType.EXCEPTION) {
      Throwable failure =
          event.payload() instanceof Throwable throwable
              ? throwable
              : new KlabActorException(this, String.valueOf(event.payload()));
      completion.completeExceptionally(failure);
    } else {
      completion.complete(null);
    }
  }

  /** Wait until every reactive call in the preceding statement or group has produced a value. */
  @SafeVarargs
  protected final void awaitReactions(CompletableFuture<?>... completions) {
    if (completions != null && completions.length > 0) {
      CompletableFuture.allOf(completions).join();
    }
  }

  /**
   * Call to start an emitter after having called {@link #onEvent(AgentScope, BiConsumer,
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
    if (rootScope == null || rootScope.isDone()) {
      return ExitValue.failure(
          new KlabIllegalStateException("Agent has already been stopped and cannot be restarted"));
    }
    if (!started.compareAndSet(false, true)) {
      return ExitValue.failure(new KlabIllegalStateException("Agent has already been started"));
    }
    try {
      rootScope.setup();
      long timestamp = markActivity();
      startedAt.compareAndSet(-1, timestamp);
      publishStatus(Message.MessageType.AgentStarted, null);
      publishStatus(Message.MessageType.AgentStatusChanged, null);
      if (getAgentExecutionMode() == Verb.Type.FUNCTION) {
        var result = main(rootScope);
        rootScope.done(result);
        return result;
      }
      return runEmitter(rootScope, this::main);
    } catch (Throwable failure) {
      rootScope.done(failure);
      throw actorFailure(failure);
    }
  }

}
