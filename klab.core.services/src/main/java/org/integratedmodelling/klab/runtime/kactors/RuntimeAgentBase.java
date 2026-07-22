package org.integratedmodelling.klab.runtime.kactors;

import groovy.lang.GroovyObjectSupport;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.exceptions.KlabActorException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.computation.GroovyProcessor;
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

  private AgentScope rootScope;
  private final KActorsBehavior behavior;
  protected final Sinks.Many<Event> eventBus = Sinks.many().multicast().onBackpressureBuffer();
  private final Object eventEmissionLock = new Object();
  private ContextScope contextScope;
  private SessionScope sessionScope;
  private int errors = 0;
  private final AtomicLong nextId = new AtomicLong(0);
  private final AtomicBoolean started = new AtomicBoolean(false);

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
    this(null, null);
  }

  public RuntimeAgentBase(KActorsBehavior behavior, SessionScope scope) {
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

  /**
   * Code with this Agent's handle can send a message to this. If a response is expected, the sender
   * can add a message consumer which may be called or not.
   *
   * @param message
   * @param sender
   * @param responseConsumer pass null if no response is expected
   */
  public void send(Message message, RuntimeAgent sender, Consumer<Message> responseConsumer) {
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

  /** A deliberately small status surface used by generated test CLIs. */
  public String status() {
    if (rootScope == null || rootScope.isDone()) {
      return "stopped";
    }
    return started.get() ? "running" : "ready";
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
    return expression.eval(evaluationScope, frame == null ? Map.of() : frame);
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

  protected Map<String, Object> childFrame(Map<String, Object> parent) {
    return parent == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parent);
  }

  protected Object resolveIdentifier(String name, Map<String, Object> frame) {
    if ("self".equals(name)) {
      return this;
    }
    if (frame != null && frame.containsKey(name)) {
      return frame.get(name);
    }
    if (rootScope != null && rootScope.containsKey(name)) {
      return rootScope.get(name);
    }
    throw new KlabActorException(this, "Unresolved k.Actors identifier: " + name);
  }

  protected void setActorState(String name, Object value) {
    rootScope.put(name, value);
  }

  protected Object literalValue(ValueType type, String encoded) {
    // Extension point for observables, quantities, ranges, ternaries, localized strings and other
    // non-POD literals whose definitive runtime mediation belongs to language/runtime services.
    return encoded;
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
          case ERROR -> payload instanceof Throwable || payload instanceof Notification;
          case REGEXP ->
              criterion instanceof java.util.regex.Pattern pattern
                  && payload != null
                  && pattern.matcher(payload.toString()).matches();
          case CLASS, TYPE ->
              criterion instanceof Class<?> cls && payload != null && cls.isInstance(payload);
          case LIST, SET -> criterion instanceof Collection<?> set && set.contains(payload);
          default -> Objects.equals(payload, criterion);
        };
    return exclusive ? !match : match;
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

  private Object invokeGeneratedAction(String action, AgentScope scope, Object... arguments) {
    try {
      Method method = findMethod(getClass(), "action_" + action, false);
      return method.invoke(this, scope, arguments);
    } catch (InvocationTargetException e) {
      throw actorFailure(e.getTargetException());
    } catch (ReflectiveOperationException e) {
      throw actorFailure(e);
    }
  }

  private Object invokeActor(Object actor, String verb, AgentScope scope, Object... arguments) {
    if (actor instanceof UnresolvedActor unresolved) {
      throw new KlabActorException(
          this,
          "Imported actor '" + unresolved.alias() + "' (" + unresolved.urn() + ") was not bound");
    }
    if (actor instanceof RuntimeAgentBase runtimeAgent) {
      return runtimeAgent.invokeGeneratedAction(verb, scope, arguments);
    }
    Class<?> actorClass = actor instanceof Class<?> cls ? cls : actor.getClass();
    Object target = actor instanceof Class<?> ? null : actor;
    try {
      Method method = findActorMethod(actorClass, verb, target == null, scope, arguments);
      Object[] actualArguments = prepareArguments(method, scope, arguments);
      return method.invoke(Modifier.isStatic(method.getModifiers()) ? null : target, actualArguments);
    } catch (InvocationTargetException e) {
      throw actorFailure(e.getTargetException());
    } catch (ReflectiveOperationException | IllegalArgumentException e) {
      throw actorFailure(e);
    }
  }

  private Method findActorMethod(
      Class<?> actorClass,
      String verb,
      boolean requireStatic,
      AgentScope scope,
      Object[] arguments)
      throws NoSuchMethodException {
    for (Method method : actorClass.getMethods()) {
      var annotation = method.getAnnotation(Verb.class);
      String exposedName =
          annotation == null || annotation.name().isBlank() ? method.getName() : annotation.name();
      if (exposedName.equals(verb)
          && (!requireStatic || Modifier.isStatic(method.getModifiers()))
          && canPrepareArguments(method, scope, arguments)) {
        method.setAccessible(true);
        return method;
      }
    }
    throw new NoSuchMethodException(actorClass.getName() + "." + verb);
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

  private boolean canPrepareArguments(Method method, AgentScope scope, Object[] arguments) {
    try {
      prepareArguments(method, scope, arguments);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private Object[] prepareArguments(Method method, AgentScope scope, Object[] supplied) {
    var parameters = method.getParameterTypes();
    var ret = new Object[parameters.length];
    int source = 0;
    for (int target = 0; target < parameters.length; target++) {
      Class<?> parameter = parameters[target];
      if (RuntimeAgent.Scope.class.isAssignableFrom(parameter)) {
        ret[target] = scope;
      } else if (method.isVarArgs() && target == parameters.length - 1) {
        Class<?> component = parameter.getComponentType();
        Object array = Array.newInstance(component, supplied.length - source);
        for (int i = source; i < supplied.length; i++) {
          Array.set(array, i - source, coerceArgument(supplied[i], component));
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

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Object coerceArgument(Object value, Class<?> target) {
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
    boolean success = expected == null ? truthy(actual) : Objects.equals(actual, expected);
    if (!success) {
      throw new AssertionError("k.Actors assertion failed: expected " + expected + ", got " + actual);
    }
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
    if (!started.compareAndSet(false, true)) {
      return ExitValue.failure(new KlabIllegalStateException("Agent has already been started"));
    }
    if (getAgentExecutionMode() == Verb.Type.FUNCTION) {
      var result = main(rootScope);
      rootScope.done(result);
      return result;
    }
    return runEmitter(rootScope, this::main);
  }
}
