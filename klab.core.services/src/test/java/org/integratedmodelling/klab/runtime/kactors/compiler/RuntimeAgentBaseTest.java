package org.integratedmodelling.klab.runtime.kactors.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.runtime.actors.AgentEventBus;
import org.integratedmodelling.common.runtime.actors.AgentImpl;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.kactors.AgentScope;
import org.integratedmodelling.klab.runtime.kactors.ApplicationBase;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.integratedmodelling.klab.runtime.kactors.ScriptBase;
import org.integratedmodelling.klab.runtime.kactors.TestCaseBase;
import org.junit.jupiter.api.Test;

class RuntimeAgentBaseTest {

  @Test
  void specializedScopesRetainTheirTypeWhenDerivingSubScopes() {
    var session = mock(org.integratedmodelling.klab.api.scope.SessionScope.class);
    var script = new StubScript(session);
    var application = new StubApplication(session);
    var testCase = new StubTestCase(session);

    assertTrue(script.rootScope() instanceof ScriptBase.ScriptScope);
    assertTrue(((AgentScope) script.rootScope()).withId(1) instanceof ScriptBase.ScriptScope);
    assertSame(session, ((AgentScope) script.rootScope()).withId(2).getSession());

    assertTrue(application.rootScope() instanceof ApplicationBase.ApplicationScope);
    assertTrue(
        ((AgentScope) application.rootScope()).withId(1)
            instanceof ApplicationBase.ApplicationScope);
    assertSame(session, ((AgentScope) application.rootScope()).withId(2).getSession());

    assertTrue(testCase.rootScope() instanceof TestCaseBase.TestCaseScope);
    assertTrue(
        ((AgentScope) testCase.rootScope()).withId(1) instanceof TestCaseBase.TestCaseScope);
    assertSame(session, ((AgentScope) testCase.rootScope()).withId(2).getSession());
  }

  @Test
  void agentMessageContractIncludesLifecycleStatusAndCustomTypes() {
    var types = Arrays.asList(Message.MessageClass.AgentCommunication.messageTypes);

    assertTrue(types.contains(Message.MessageType.AgentStartRequested));
    assertTrue(types.contains(Message.MessageType.AgentStopRequested));
    assertTrue(types.contains(Message.MessageType.AgentStatusRequested));
    assertTrue(types.contains(Message.MessageType.AgentStarted));
    assertTrue(types.contains(Message.MessageType.AgentStopped));
    assertTrue(types.contains(Message.MessageType.AgentStatusChanged));
    assertTrue(types.contains(Message.MessageType.AgentFailed));
    assertTrue(types.contains(Message.MessageType.CustomAgentMessage));

    var custom =
        new RuntimeAgent.CustomMessage(Constant.create("temperature_changed"), Double.valueOf(12.5));
    var message =
        Message.create(
            "sender:agent:1",
            Message.MessageClass.AgentCommunication,
            Message.MessageType.CustomAgentMessage,
            custom);

    assertEquals(Message.Queue.Events, message.getQueue());
    assertEquals(
        "temperature_changed",
        message.getPayload(RuntimeAgent.CustomMessage.class).type().getValue());
  }

  @Test
  void disconnectedClientHandleKeepsMessagingDisabled() {
    var agent = new AgentImpl();
    agent.setUrn("test:agent:1");
    agent.setViable(true);

    assertFalse(agent.connect(null));
    assertFalse(agent.start());
    assertEquals(1, agent.getNotifications().size());
    assertEquals(Notification.Level.Info, agent.getNotifications().getFirst().getLevel());
  }

  @Test
  void disconnectedCreatingScopeDisablesMessagingWithInfoNotification() {
    var agent = new ReactiveRuntimeAgent();
    var notifications = new CopyOnWriteArrayList<Notification>();

    assertFalse(agent.initializeMessaging("test:agent:1", null, notifications::add));
    assertEquals(1, notifications.size());
    assertEquals(Notification.Level.Info, notifications.getFirst().getLevel());
  }

  @Test
  void creationScopeIsAvailableDuringTheWholeAgentLifetime() {
    var creationScope =
        mock(org.integratedmodelling.klab.api.scope.Scope.class);
    var agent = new ReactiveRuntimeAgent(creationScope);
    var cleanups = new AtomicInteger();

    assertSame(creationScope, agent.getCreationScope());
    agent.onTermination(cleanups::incrementAndGet);
    agent.rootScope().done();
    agent.rootScope().done();
    assertEquals(1, cleanups.get());

    agent.onTermination(cleanups::incrementAndGet);
    assertEquals(2, cleanups.get(), "late lifecycle cleanup must run immediately");
  }

  @Test
  void customMessagesInvokeMatchingHandlerWithRestoredPayloadAndSender() throws Exception {
    AgentEventBus.INSTANCE.registerPayloadType(TestPayload.class);
    var outbound =
        Message.create(
            "sender:agent:7",
            Message.MessageClass.AgentCommunication,
            Message.MessageType.CustomAgentMessage,
            new RuntimeAgent.CustomMessage(
                Constant.create("TEMPERATURE_CHANGED"), new TestPayload("station-a", 12.5)));
    var mapper = JacksonConfiguration.newObjectMapper();
    var received = mapper.readValue(mapper.writeValueAsString(outbound), Message.class);
    var agent = new MessageHandlingRuntimeAgent();

    agent.send(received, null, null);

    assertTrue(agent.handled.await(1, TimeUnit.SECONDS));
    assertEquals(new TestPayload("station-a", 12.5), agent.payload.get());
    assertEquals("sender:agent:7", agent.sender.get().getUrn());
  }

  @Test
  void stdinMessagesInvokeTheReservedConsoleHandler() throws Exception {
    var agent = new MessageHandlingRuntimeAgent();
    var input =
        Message.create(
            "client:console:1",
            Message.MessageClass.AgentCommunication,
            Message.MessageType.CustomAgentMessage,
            new RuntimeAgent.CustomMessage(
                RuntimeAgent.ConsoleMessageType.STDIN.constant(), "inspect status"));

    agent.send(input, null, null);

    assertTrue(agent.inputHandled.await(1, TimeUnit.SECONDS));
    assertEquals("inspect status", agent.input.get());
    assertEquals("client:console:1", agent.inputSender.get().getUrn());
  }

  @Test
  void nonFunctionAgentWaitsForRootScopeDoneAfterMainReturns() throws Exception {
    var agent = new BackgroundRuntimeAgent();
    Thread thread = null;

    try {
      assertEquals("ready", agent.status());
      assertEquals(-1, agent.getStartedAt());
      assertEquals(-1, agent.getLastActivityAt());
      assertSame(RuntimeAgentBase.TASK_RUNNING, agent.run());
      assertTrue(agent.mainReturned.await(1, TimeUnit.SECONDS));
      assertEquals("running", agent.status());
      assertTrue(agent.getStartedAt() > 0);
      assertTrue(agent.getLastActivityAt() >= agent.getStartedAt());

      thread = agent.agentThread.get();
      assertNotNull(thread);
      assertTrue(thread.isVirtual());
      assertTrue(thread.isAlive());

      agent.rootScope().done();
      thread.join(1000);

      assertFalse(thread.isAlive());
      assertEquals("stopped", agent.status());
    } finally {
      agent.rootScope().done();
      if (thread != null) {
        thread.join(1000);
      }
    }
  }

  @Test
  void scopedSubscriptionsIgnoreTerminationAndDisposeOnDone() {
    var agent = new ReactiveRuntimeAgent();
    var parentScope = (AgentScope) agent.rootScope();
    var received = new AtomicInteger();

    var eventScope =
        agent.listen(
                parentScope, (event, scope) -> received.incrementAndGet(), RuntimeAgentBase.EventType.FIRE);

    eventScope.doFire("first");
    eventScope.done();
    eventScope.doFire("second");

    assertEquals(1, received.get());
  }

  @Test
  void returnEmitsOnePayloadEventBeforeTerminatingScope() {
    var agent = new ReactiveRuntimeAgent();
    var parentScope = (AgentScope) agent.rootScope();
    var received = new CopyOnWriteArrayList<RuntimeAgentBase.EventType>();

    var eventScope =
        agent.listen(
                parentScope,
                (event, scope) -> received.add(event.type()),
                RuntimeAgentBase.EventType.RETURN);

    eventScope.doReturn("done");

    assertEquals(List.of(RuntimeAgentBase.EventType.RETURN), received);
    assertTrue(eventScope.isDone());
  }

  @Test
  void supplierCompletionEmitsReturnPayloadAndTerminatesScope() {
    var agent = new ReactiveRuntimeAgent();
    var parentScope = (AgentScope) agent.rootScope();
    var future = new CompletableFuture<String>();
    var received = new CopyOnWriteArrayList<Object>();

    var eventScope =
        agent.listen(
                parentScope,
                (event, scope) -> received.add(event.payload()),
                RuntimeAgentBase.EventType.RETURN);
    assertSame(RuntimeAgentBase.TASK_RUNNING, agent.supply(eventScope, scope -> future));

    future.complete("done");

    assertEquals(List.of("done"), received);
    assertTrue(eventScope.isDone());
  }

  @Test
  void exceptionalScopeCompletionEmitsExceptionPayloadBeforeTermination() {
    var agent = new ReactiveRuntimeAgent();
    var parentScope = (AgentScope) agent.rootScope();
    var received = new CopyOnWriteArrayList<RuntimeAgentBase.EventType>();

    var eventScope =
        agent.listen(
                parentScope,
                (event, scope) -> received.add(event.type()),
                RuntimeAgentBase.EventType.EXCEPTION);

    eventScope.done(new IllegalStateException("boom"));

    assertEquals(List.of(RuntimeAgentBase.EventType.EXCEPTION), received);
    assertTrue(eventScope.isDone());
  }

  @Test
  void nestedEmitterRelaysFireThroughEnclosingActionScope() {
    var agent = new ReactiveRuntimeAgent();
    var rootScope = (AgentScope) agent.rootScope();
    var received = new CopyOnWriteArrayList<Object>();

    var enclosingActionScope =
        agent.listen(
                rootScope,
                (event, scope) -> received.add(event.payload()),
                RuntimeAgentBase.EventType.FIRE);
    var nestedVerbScope =
        agent.listen(
                enclosingActionScope,
                (event, scope) -> scope.doFire("relayed"),
                RuntimeAgentBase.EventType.FIRE);

    nestedVerbScope.doFire("source");

    assertEquals(List.of("relayed"), received);
  }

  @Test
  void reflectiveActorBridgeInjectsScopeAndHonorsExecutionShapes() {
    var agent = new ReactiveRuntimeAgent();
    var scope = (AgentScope) agent.rootScope();

    assertEquals(3L, agent.function(TestActor.class, "sum", scope, 1, 2));
    assertEquals("ready", agent.supplier(TestActor.class, "later", scope).join());

    var received = new CopyOnWriteArrayList<Object>();
    var eventScope =
        agent.listen(
            scope,
            (event, parent) -> received.add(event.payload()),
            RuntimeAgentBase.EventType.FIRE);
    agent.emitter(TestActor.class, "emit", eventScope, "event");
    assertEquals(List.of("event"), received);
  }

  @Test
  void dynamicVerbBridgeDiscoversFunctionSupplierAndEmitterAtRuntime() throws Exception {
    var agent = new ReactiveRuntimeAgent();
    var root = (AgentScope) agent.rootScope();

    assertEquals(3L, agent.dynamicValue(TestActor.class, "sum", root, 1, 2));
    assertEquals("ready", agent.dynamicValue(TestActor.class, "later", root));

    var returned = new CopyOnWriteArrayList<Object>();
    var supplierDone = new CountDownLatch(1);
    var supplierScope =
        agent.listen(
            root,
            (event, parent) -> {
              returned.add(event.payload());
              supplierDone.countDown();
            },
            RuntimeAgentBase.EventType.RETURN);
    assertSame(
        RuntimeAgentBase.TASK_RUNNING,
        agent.dynamic(TestActor.class, "later", supplierScope));
    assertTrue(supplierDone.await(1, TimeUnit.SECONDS));
    assertEquals(List.of("ready"), returned);

    agent.dynamicMainDone("main result");
    root.awaitDone();
    assertTrue(root.isDone(), "finite dynamic calls must allow the root lifecycle to finish");

    var emitterAgent = new ReactiveRuntimeAgent();
    var emitterRoot = (AgentScope) emitterAgent.rootScope();
    var fired = new CopyOnWriteArrayList<Object>();
    var emitterDone = new CountDownLatch(1);
    var emitterScope =
        emitterAgent.listen(
            emitterRoot,
            (event, parent) -> {
              fired.add(event.payload());
              emitterDone.countDown();
            },
            RuntimeAgentBase.EventType.FIRE);
    assertSame(
        RuntimeAgentBase.TASK_RUNNING,
        emitterAgent.dynamic(TestActor.class, "emit", emitterScope, "event"));
    assertTrue(emitterDone.await(1, TimeUnit.SECONDS));
    assertEquals(List.of("event"), fired);
    emitterAgent.dynamicMainDone("main result");
    var rootCompletion =
        CompletableFuture.runAsync(
            () -> {
              try {
                emitterRoot.awaitDone();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
              }
            });
    assertFalse(rootCompletion.isDone(), "a dynamic emitter must keep the root lifecycle alive");
    emitterScope.done();
    rootCompletion.get(1, TimeUnit.SECONDS);
  }

  @Test
  void sequentialBarrierWaitsForEveryReaction() {
    var agent = new ReactiveRuntimeAgent();
    var first = new CompletableFuture<Void>();
    var second = new CompletableFuture<Void>();
    var waiting = CompletableFuture.runAsync(() -> agent.await(first, second));

    first.complete(null);
    assertFalse(waiting.isDone(), "one completed reactor must not release the group barrier");

    second.complete(null);
    waiting.join();
    assertTrue(waiting.isDone());
  }

  private static class TestActor {

    @org.integratedmodelling.klab.api.services.runtime.extension.Verb(
        name = "sum",
        executionType = Verb.Type.FUNCTION)
    public static long sum(RuntimeAgent.Scope scope, long left, long right) {
      return left + right;
    }

    @org.integratedmodelling.klab.api.services.runtime.extension.Verb(
        name = "later",
        executionType = Verb.Type.SUPPLIER)
    public static CompletableFuture<String> later(RuntimeAgent.Scope scope) {
      return CompletableFuture.completedFuture("ready");
    }

    @org.integratedmodelling.klab.api.services.runtime.extension.Verb(
        name = "emit",
        executionType = Verb.Type.EMITTER)
    public static void emit(RuntimeAgent.Scope scope, Object value) {
      scope.doFire(value);
    }
  }

  public static class TestPayload implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    private String station;
    private double value;

    public TestPayload() {}

    private TestPayload(String station, double value) {
      this.station = station;
      this.value = value;
    }

    public String getStation() {
      return station;
    }

    public void setStation(String station) {
      this.station = station;
    }

    public double getValue() {
      return value;
    }

    public void setValue(double value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object object) {
      return object instanceof TestPayload other
          && station.equals(other.station)
          && Double.compare(value, other.value) == 0;
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(station, value);
    }
  }

  private static class MessageHandlingRuntimeAgent extends RuntimeAgentBase {

    private final CountDownLatch handled = new CountDownLatch(1);
    private final CountDownLatch inputHandled = new CountDownLatch(1);
    private final AtomicReference<TestPayload> payload = new AtomicReference<>();
    private final AtomicReference<Agent> sender = new AtomicReference<>();
    private final AtomicReference<String> input = new AtomicReference<>();
    private final AtomicReference<Agent> inputSender = new AtomicReference<>();

    private MessageHandlingRuntimeAgent() {
      super(null, null);
    }

    @Override
    protected Map<String, AgentMessageHandler> agentMessageHandlers() {
      return Map.of(
          "TEMPERATURE_CHANGED",
          new AgentMessageHandler(
              "temperatureChanged", Verb.Type.FUNCTION, List.of("payload", "sender")),
          RuntimeAgent.ConsoleMessageType.STDIN.name(),
          new AgentMessageHandler("readLine", Verb.Type.FUNCTION, List.of("line", "sender")));
    }

    @SuppressWarnings("unused")
    private Object action_temperatureChanged(AgentScope scope, Object... arguments) {
      payload.set((TestPayload) arguments[0]);
      sender.set((Agent) arguments[1]);
      handled.countDown();
      return VOID_VALUE;
    }

    @SuppressWarnings("unused")
    private Object action_readLine(AgentScope scope, Object... arguments) {
      input.set((String) arguments[0]);
      inputSender.set((Agent) arguments[1]);
      inputHandled.countDown();
      return VOID_VALUE;
    }

    @Override
    protected ExitValue main(AgentScope rootScope) {
      return NORMAL_EXIT;
    }

    @Override
    public Verb.Type getAgentExecutionMode() {
      return Verb.Type.EMITTER;
    }
  }

  private static class BackgroundRuntimeAgent extends RuntimeAgentBase {

    private final CountDownLatch mainReturned = new CountDownLatch(1);
    private final AtomicReference<Thread> agentThread = new AtomicReference<>();

    private BackgroundRuntimeAgent() {
      super(null, null);
    }

    @Override
    protected ExitValue main(AgentScope rootScope) {
      agentThread.set(Thread.currentThread());
      mainReturned.countDown();
      return NORMAL_EXIT;
    }

    @Override
    public Verb.Type getAgentExecutionMode() {
      return Verb.Type.EMITTER;
    }
  }

  private static class ReactiveRuntimeAgent extends RuntimeAgentBase {

    private ReactiveRuntimeAgent() {
      super(null, null);
    }

    private ReactiveRuntimeAgent(
        org.integratedmodelling.klab.api.scope.Scope creationScope) {
      super(null, null, null, creationScope);
    }

    private AgentScope listen(
        AgentScope scope,
        BiConsumer<Event, AgentScope> consumer,
        RuntimeAgentBase.EventType... eventTypes) {
      return onEvent(scope, consumer, eventTypes);
    }

    private <T> ExitValue supply(
        AgentScope scope, Function<AgentScope, CompletableFuture<T>> supplier) {
      return runSupplier(scope, supplier);
    }

    private Object function(Object actor, String verb, AgentScope scope, Object... arguments) {
      return invokeFunction(actor, verb, scope, arguments);
    }

    private CompletableFuture<Object> supplier(
        Object actor, String verb, AgentScope scope, Object... arguments) {
      return invokeSupplier(actor, verb, scope, arguments);
    }

    private void emitter(Object actor, String verb, AgentScope scope, Object... arguments) {
      invokeEmitter(actor, verb, scope, arguments);
    }

    private ExitValue dynamic(
        Object actor, String verb, AgentScope scope, Object... arguments) {
      return runDynamicVerb(actor, verb, scope, arguments);
    }

    private Object dynamicValue(
        Object actor, String verb, AgentScope scope, Object... arguments) {
      return invokeDynamicValue(actor, verb, scope, arguments);
    }

    private ExitValue dynamicMainDone(Object result) {
      return awaitDynamicCalls(result);
    }

    private void await(CompletableFuture<?>... completions) {
      awaitReactions(completions);
    }

    @Override
    protected ExitValue main(AgentScope rootScope) {
      return NORMAL_EXIT;
    }

    @Override
    public Verb.Type getAgentExecutionMode() {
      return Verb.Type.EMITTER;
    }
  }

  private static class StubScript extends ScriptBase {

    private StubScript(org.integratedmodelling.klab.api.scope.SessionScope scope) {
      super(null, scope);
    }

    @Override
    protected ExitValue main(AgentScope rootScope) {
      return NORMAL_EXIT;
    }

    @Override
    public Verb.Type getAgentExecutionMode() {
      return Verb.Type.FUNCTION;
    }
  }

  private static class StubApplication extends ApplicationBase {

    private StubApplication(org.integratedmodelling.klab.api.scope.SessionScope scope) {
      super(null, scope);
    }

    @Override
    protected ExitValue main(AgentScope rootScope) {
      return NORMAL_EXIT;
    }

    @Override
    public Verb.Type getAgentExecutionMode() {
      return Verb.Type.FUNCTION;
    }
  }

  private static class StubTestCase extends TestCaseBase {

    private StubTestCase(org.integratedmodelling.klab.api.scope.SessionScope scope) {
      super(null, scope);
    }

    @Override
    protected ExitValue main(AgentScope rootScope) {
      return NORMAL_EXIT;
    }

    @Override
    public Verb.Type getAgentExecutionMode() {
      return Verb.Type.FUNCTION;
    }
  }
}
