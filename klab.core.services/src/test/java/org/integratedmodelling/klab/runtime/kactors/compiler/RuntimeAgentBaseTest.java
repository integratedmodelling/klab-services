package org.integratedmodelling.klab.runtime.kactors.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
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
import java.util.function.Supplier;
import org.integratedmodelling.common.authentication.scope.AMQPChannel;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.lang.QuantityImpl;
import org.integratedmodelling.common.runtime.actors.AgentEventBus;
import org.integratedmodelling.common.runtime.actors.AgentImpl;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.collections.DomainObject;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.AnnotationImpl;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsStatementImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.kactors.AgentScope;
import org.integratedmodelling.klab.runtime.kactors.ApplicationBase;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.integratedmodelling.klab.runtime.kactors.ScriptBase;
import org.integratedmodelling.klab.runtime.kactors.TestCaseBase;
import org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary;
import org.junit.jupiter.api.Test;

class RuntimeAgentBaseTest {

  @Test
  void testcaseScopeReceivesAssertionOutcomeSemanticBeanAndEvaluationException() throws Exception {
    var agent = new AssertionReportingTestAgent();
    var assertion = new KActorsStatementImpl.AssertImpl.AssertionImpl();
    assertion.setTag("complete-semantic-assertion");
    var serialized =
        JacksonConfiguration.newObjectMapper()
            .writerFor(KActorsStatement.Assert.Assertion.class)
            .writeValueAsString(assertion);
    var semanticAssertion = agent.semanticAssertion(serialized);

    agent.evaluate(() -> 42, () -> 42, semanticAssertion);

    assertEquals(1, agent.evaluations.size());
    assertSame(semanticAssertion, agent.evaluations.getFirst().assertion());
    assertEquals("complete-semantic-assertion", semanticAssertion.getTag());
    assertTrue(agent.evaluations.getFirst().success());
    assertNull(agent.evaluations.getFirst().exception());

    var mismatch =
        assertThrows(
            AssertionError.class,
            () -> agent.evaluate(() -> 42, () -> 43, semanticAssertion));
    assertEquals(2, agent.evaluations.size());
    assertSame(semanticAssertion, agent.evaluations.getLast().assertion());
    assertFalse(agent.evaluations.getLast().success());
    assertSame(mismatch, agent.evaluations.getLast().exception());

    var failure = new IllegalStateException("evaluation failed");
    assertSame(
        failure,
        assertThrows(
            IllegalStateException.class,
            () ->
                agent.evaluate(
                    () -> {
                      throw failure;
                    },
                    null,
                    semanticAssertion)));

    assertEquals(3, agent.evaluations.size());
    assertSame(semanticAssertion, agent.evaluations.getLast().assertion());
    assertFalse(agent.evaluations.getLast().success());
    assertSame(failure, agent.evaluations.getLast().exception());
  }

  @Test
  void assertionDistinguishesTruthChecksFromExplicitNullComparisons() {
    var agent = new AssertionReportingTestAgent();
    var assertion = new KActorsStatementImpl.AssertImpl.AssertionImpl();

    agent.evaluate(() -> true, null, assertion);
    agent.evaluate(() -> null, () -> null, assertion);

    var falseTruthCheck =
        assertThrows(AssertionError.class, () -> agent.evaluate(() -> false, null, assertion));
    assertEquals(
        "k.Actors assertion failed: expected a truthy value, got false",
        falseTruthCheck.getMessage());

    var nullMismatch =
        assertThrows(
            AssertionError.class, () -> agent.evaluate(() -> false, () -> null, assertion));
    assertEquals(
        "k.Actors assertion failed: expected null, got false", nullMismatch.getMessage());

    assertEquals(4, agent.evaluations.size());
    assertTrue(agent.evaluations.get(0).success());
    assertTrue(agent.evaluations.get(1).success());
    assertFalse(agent.evaluations.get(2).success());
    assertFalse(agent.evaluations.get(3).success());
  }

  @Test
  void testcaseRunnerIsSequentialByDefaultAndParallelWhenRequested() {
    var sequential = new TestExecutionAgent(false);
    var order = new ArrayList<String>();

    assertEquals(
        "second",
        sequential.execute(
            () -> {
              order.add("first");
              return "first";
            },
            () -> {
              order.add("second");
              return "second";
            }));
    assertEquals(List.of("first", "second"), order);

    var parallel = new TestExecutionAgent(true);
    var bothStarted = new CountDownLatch(2);
    var completed = new CopyOnWriteArrayList<String>();
    parallel.execute(
        concurrentTest("first", bothStarted, completed),
        concurrentTest("second", bothStarted, completed));

    assertEquals(0, bothStarted.getCount(), "both tests must start before either can finish");
    assertEquals(2, completed.size());
    assertTrue(completed.containsAll(List.of("first", "second")));
  }

  private static Supplier<Object> concurrentTest(
      String name, CountDownLatch bothStarted, List<String> completed) {
    return () -> {
      bothStarted.countDown();
      try {
        assertTrue(bothStarted.await(2, TimeUnit.SECONDS));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
      completed.add(name);
      return name;
    };
  }

  @Test
  void failedTestDoesNotAbortSuiteOrFailFiniteTestcaseAgent() {
    for (boolean parallel : List.of(false, true)) {
      var agent = new FailureIsolatingTestAgent(parallel);

      var result = agent.run();

      assertEquals(0, result.getErrorCode());
      assertEquals("stopped", agent.status());
      assertEquals(2, agent.executed().size());
      assertTrue(agent.executed().containsAll(List.of("failing_test", "passing_test")));
      assertEquals(2, agent.report().get("testsFinished", 0));
      assertEquals(1, agent.report().get("testsPassed", 0));
      assertEquals(1, agent.report().get("testsFailed", 0));
      assertNotNull(agent.report().get("end"));

      var failed =
          agent.report().getChildren().stream()
              .filter(test -> "failing_test".equals(test.urn()))
              .findFirst()
              .orElseThrow();
      var passed =
          agent.report().getChildren().stream()
              .filter(test -> "passing_test".equals(test.urn()))
              .findFirst()
              .orElseThrow();
      assertFalse(failed.get("outcome", true));
      assertNotNull(failed.get("stacktrace"));
      assertTrue(passed.get("outcome", false));
    }
  }

  @Test
  void testcaseReportTracksOnlyAnnotatedTestsAndAssociatesTheirAssertions() {
    var session = mock(org.integratedmodelling.klab.api.scope.SessionScope.class);
    var testCase = new StubTestCase(session);
    var rootScope = (AgentScope) testCase.rootScope();
    var helperScope = (TestCaseBase.TestCaseScope) rootScope.withId(1);
    helperScope.beforeAction("helper", List.of());
    helperScope.afterAction("helper", List.of());
    assertTrue(testCase.report().getChildren().isEmpty());

    var testAnnotation = AnnotationImpl.create("test", "name", "Checks values");
    var testScope = (TestCaseBase.TestCaseScope) rootScope.withId(2);
    testScope.beforeAction("checks_values", List.of(testAnnotation));
    var assertion = new KActorsStatementImpl.AssertImpl.AssertionImpl();
    assertion.setSourceCode("actual == expected");
    testCase.record(testScope, assertion, false, new AssertionError("different"));
    testCase.recordConsole(testScope, RuntimeAgent.ConsoleMessageType.STDOUT, "checking values\n");
    testCase.recordConsole(testScope, RuntimeAgent.ConsoleMessageType.STDERR, "different values\n");
    testScope.afterAction("checks_values", List.of(testAnnotation));

    assertEquals(1, testCase.report().getChildren().size());
    DomainObject test = testCase.report().getChildren().getFirst();
    assertEquals("test", test.type());
    assertEquals("checks_values", test.urn());
    assertEquals("Checks values", test.name());
    assertFalse(test.get("outcome", true));
    assertEquals(1L, test.get("assertionsFailed", Long.class));
    assertEquals(3, test.getChildren().size());
    assertEquals("actual == expected", test.getChildren().getFirst().urn());
    assertNotNull(test.getChildren().getFirst().get("stacktrace"));
    assertEquals("console", test.getChildren().get(1).type());
    assertEquals("STDOUT", test.getChildren().get(1).get("stream"));
    assertEquals("checking values\n", test.getChildren().get(1).get("text"));
    assertEquals("STDERR", test.getChildren().get(2).get("stream"));
    assertEquals(1L, testCase.report().get("assertions", Long.class));

    var throwingScope = (TestCaseBase.TestCaseScope) rootScope.withId(3);
    throwingScope.beforeAction("throws_error", List.of(testAnnotation));
    throwingScope.afterAction(
        "throws_error", List.of(testAnnotation), new IllegalStateException("broken test"));
    DomainObject throwingTest = testCase.report().getChildren().getLast();
    assertFalse(throwingTest.get("outcome", true));
    assertTrue(throwingTest.get("stacktrace", String.class).contains("broken test"));
  }

  @Test
  void deferredValuesAreReevaluatedWheneverAnAliasIsConsumed() {
    var agent = new ReactiveRuntimeAgent();
    var evaluations = new AtomicInteger();
    Object deferred = agent.deferred(evaluations::incrementAndGet);
    var frame = Map.of("x", deferred);

    assertEquals(1, agent.resolve("x", frame));
    assertEquals(2, agent.resolve("x", frame));

    Expression expression =
        (scope, parameters) -> ((Map<?, ?>) parameters[0]).get("x");
    assertEquals(3, agent.evaluate(expression, frame));
    assertEquals(4, agent.evaluate(expression, frame));

    Object validated =
        agent.validateTyped("accept", List.of("integer"), deferred)[0];
    assertEquals(5, agent.resolveValue(validated));
  }

  @Test
  void sharedMatcherDistinguishesTruthyWildcardCatchAllErrorsAndAnnotations() {
    var agent = new ReactiveRuntimeAgent();

    assertFalse(agent.match(false, ValueType.ANYVALUE, null));
    assertFalse(agent.match(0, ValueType.ANYVALUE, null));
    assertFalse(agent.match("", ValueType.ANYVALUE, null));
    assertFalse(agent.match(null, ValueType.ANYVALUE, null));
    assertTrue(agent.match("value", ValueType.ANYVALUE, null));
    assertTrue(agent.match(null, ValueType.ANYTHING, null));
    assertTrue(agent.match(new IllegalStateException("failed"), ValueType.ANYTHING, null));
    assertTrue(
        agent.match(
            Constant.create("MESSAGES.HELLO"),
            ValueType.CONSTANT,
            Constant.create("MESSAGES.HELLO")));
    assertFalse(
        agent.match(
            Constant.create("MESSAGES.GOODBYE"),
            ValueType.CONSTANT,
            Constant.create("MESSAGES.HELLO")));

    var annotated = new KActorsBehaviorImpl();
    annotated.setAnnotations(List.of(AnnotationImpl.create("selected")));
    assertTrue(agent.match(annotated, ValueType.ANNOTATION, "selected"));
    assertFalse(agent.match(annotated, ValueType.ANNOTATION, "other"));
    assertTrue(agent.match(Notification.error("failed"), ValueType.ERROR, null));
    assertFalse(agent.match(Notification.info("informational"), ValueType.ERROR, null));
  }

  @Test
  void actionTypeGuardMatchesSimpleJavaNamesCaseInsensitively() {
    var agent = new ReactiveRuntimeAgent();

    assertEquals(
        Boolean.TRUE,
        agent.validateTyped("accept", List.of("boolean"), Boolean.TRUE)[0]);
    assertThrows(
        org.integratedmodelling.klab.api.exceptions.KlabActorException.class,
        () -> agent.validateTyped("accept", List.of("boolean"), "true"));
  }

  @Test
  void rootScopeHooksRunExactlyOnceAndAgentsCannotRestart() {
    var completed = new LifecycleHookAgent();

    assertEquals(0, completed.setupCalls.get());
    assertEquals(0, completed.disposeCalls.get());
    assertEquals(0, completed.run().getErrorCode());
    assertEquals(1, completed.setupCalls.get());
    assertEquals(1, completed.disposeCalls.get());
    assertTrue(completed.run().getErrorCode() != 0);
    assertEquals(1, completed.setupCalls.get());
    assertEquals(1, completed.disposeCalls.get());

    var stoppedBeforeStart = new LifecycleHookAgent();
    stoppedBeforeStart.stop();
    stoppedBeforeStart.stop();
    assertEquals(0, stoppedBeforeStart.setupCalls.get());
    assertEquals(1, stoppedBeforeStart.disposeCalls.get());
    assertTrue(stoppedBeforeStart.run().getErrorCode() != 0);
  }

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
  void eachGeneratedActionReceivesAnInvocationLocalScopeWithItsCurrentAction() {
    var agent = new ActionScopeAgent();

    assertEquals("inner-result", agent.callOuter());
    assertNull(agent.rootScope().getCurrentAction());
    assertNotNull(agent.outerScope.get());
    assertNotNull(agent.innerScope.get());
    assertFalse(agent.outerScope.get() == agent.rootScope());
    assertFalse(agent.innerScope.get() == agent.outerScope.get());
    assertEquals("outer", agent.outerScope.get().getCurrentAction());
    assertEquals("inner", agent.innerScope.get().getCurrentAction());
    assertEquals(agent.outerScope.get().actionId(), agent.innerScope.get().actionId());

    var session = mock(org.integratedmodelling.klab.api.scope.SessionScope.class);
    var testCase = new StubTestCase(session);
    var testScope = testCase.callProbe();
    assertTrue(testScope instanceof TestCaseBase.TestCaseScope);
    assertFalse(testScope == testCase.rootScope());
    assertEquals("probe", testScope.getCurrentAction());
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

    for (var type : RuntimeAgent.TestMessageType.values()) {
      assertTrue(type.messageClass().startsWith("INT."));
      assertEquals(type.messageClass(), type.constant().getValue());
      assertTrue(RuntimeAgent.isReservedMessageClass(type.messageClass()));
    }
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
  void connectedPublisherDoesNotReceiveItsOwnLocalLoopback() {
    var channel = mock(ConnectedScope.class);
    var amqp = mock(AMQPChannel.class);
    when(channel.isConnected()).thenReturn(true);
    when(channel.getFederation())
        .thenReturn(new Federation("test-federation", "amqp://test"));
    when(amqp.isOnline()).thenReturn(true);
    var sender = new AgentImpl();
    sender.setUrn("agent:loopback");
    sender.setName("loopback sender");
    sender.setViable(true);
    var observer = new AgentImpl();
    observer.setUrn("agent:loopback");
    observer.setViable(true);
    var senderIncoming = new AtomicInteger();
    var senderOutgoing = new AtomicInteger();
    var observerIncoming = new AtomicInteger();
    var observedMessage = new AtomicReference<Message>();

    try (var mocked = mockStatic(AMQPChannel.class)) {
      mocked
          .when(() -> AMQPChannel.forAgent(any(), anyString(), any(), any()))
          .thenReturn(amqp);
      assertTrue(sender.connect(channel));
      assertTrue(observer.connect(channel));
      sender.addMessageListener(ignored -> senderIncoming.incrementAndGet());
      sender.addSentMessageListener(ignored -> senderOutgoing.incrementAndGet());
      observer.addMessageListener(
          message -> {
            observerIncoming.incrementAndGet();
            observedMessage.set(message);
          });

      sender.tell(new RuntimeAgent.CustomMessage(Constant.create("PING"), "payload"));

      assertEquals(0, senderIncoming.get());
      assertEquals(1, senderOutgoing.get());
      assertEquals(1, observerIncoming.get());
      assertEquals(
          "loopback sender",
          observedMessage
              .get()
              .getPayload(RuntimeAgent.CustomMessage.class)
              .senderName());
    } finally {
      sender.disconnect();
      observer.disconnect();
    }
  }

  @Test
  void naturalTerminationClosesTheRuntimeMessagingSubscription() {
    var channel = mock(ConnectedScope.class);
    var amqp = mock(AMQPChannel.class);
    when(channel.isConnected()).thenReturn(true);
    when(channel.getFederation())
        .thenReturn(new Federation("test-federation", "amqp://test"));
    when(amqp.isOnline()).thenReturn(true);
    var agent = new ReactiveRuntimeAgent();

    try (var mocked = mockStatic(AMQPChannel.class)) {
      mocked
          .when(() -> AMQPChannel.forAgent(any(), anyString(), any(), any()))
          .thenReturn(amqp);
      assertTrue(agent.initializeMessaging("agent:natural-stop", channel, ignored -> {}));
      assertTrue(AgentEventBus.INSTANCE.isSubscribed("agent:natural-stop", agent));

      agent.rootScope().done();

      assertFalse(AgentEventBus.INSTANCE.isSubscribed("agent:natural-stop", agent));
    } finally {
      agent.closeMessaging();
    }
  }

  @Test
  void startupConsoleOutputIsReplayedWhenTheConsoleAttaches() {
    var agent = new ConsoleBufferRuntimeAgent();
    agent.initializeMessaging("test:agent:console-buffer", null, ignored -> {});

    assertFalse(agent.sendToConsole(RuntimeAgent.ConsoleMessageType.STDOUT, "Ready"));
    assertTrue(agent.published.isEmpty());

    agent.send(
        Message.create(
            "client:console:1",
            Message.MessageClass.AgentCommunication,
            Message.MessageType.CustomAgentMessage,
            new RuntimeAgent.CustomMessage(
                RuntimeAgent.ConsoleMessageType.CONSOLE_ATTACH.constant(), null)),
        null,
        null);

    assertEquals(
        List.of(new ConsoleChunk(RuntimeAgent.ConsoleMessageType.STDOUT, "Ready")),
        agent.published);
    assertTrue(agent.sendToConsole(RuntimeAgent.ConsoleMessageType.STDOUT, "After attach"));
    assertEquals(
        List.of(
            new ConsoleChunk(RuntimeAgent.ConsoleMessageType.STDOUT, "Ready"),
            new ConsoleChunk(RuntimeAgent.ConsoleMessageType.STDOUT, "After attach")),
        agent.published);
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
    var customMessage =
        new RuntimeAgent.CustomMessage(
            Constant.create("TEMPERATURE_CHANGED"), new TestPayload("station-a", 12.5));
    customMessage.setSenderName("weather station");
    var outbound =
        Message.create(
            "sender:agent:7",
            Message.MessageClass.AgentCommunication,
            Message.MessageType.CustomAgentMessage,
            customMessage);
    var mapper = JacksonConfiguration.newObjectMapper();
    var received = mapper.readValue(mapper.writeValueAsString(outbound), Message.class);
    var agent = new MessageHandlingRuntimeAgent();
    assertEquals(List.of("TEMPERATURE_CHANGED"), agent.getHandledMessageClasses());

    agent.send(received, null, null);

    assertTrue(agent.handled.await(1, TimeUnit.SECONDS));
    assertEquals(new TestPayload("station-a", 12.5), agent.payload.get());
    assertEquals("sender:agent:7", agent.sender.get().getUrn());
    assertEquals("weather station", agent.sender.get().getName());
    assertEquals("\uD83D\uDC64 weather station", agent.sender.get().toString());
  }

  @Test
  void askCompletesFromHandleReturnValue() throws Exception {
    var channel = mock(ConnectedScope.class);
    var amqp = mock(AMQPChannel.class);
    when(channel.isConnected()).thenReturn(true);
    when(channel.getFederation())
        .thenReturn(new Federation("test-federation", "amqp://test"));
    when(amqp.isOnline()).thenReturn(true);
    var requester = new ReactiveRuntimeAgent();
    var recipient = new RequestReplyRuntimeAgent();

    try (var mocked = mockStatic(AMQPChannel.class)) {
      mocked
          .when(() -> AMQPChannel.forAgent(any(), anyString(), any(), any()))
          .thenReturn(amqp);
      assertTrue(requester.initializeMessaging("agent:requester", channel, ignored -> {}));
      assertTrue(recipient.initializeMessaging("agent:recipient", channel, ignored -> {}));

      assertEquals(
          "reply:payload",
          requester
              .supplier(
                  requester.core(recipient),
                  "ask",
                  (AgentScope) requester.rootScope(),
                  Constant.create("QUESTION"),
                  "payload",
                  Duration.ofSeconds(1))
              .get(1, TimeUnit.SECONDS));
    } finally {
      requester.closeMessaging();
      recipient.closeMessaging();
    }
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
  void testcaseLifecycleMessagesCannotReachLanguageHandlers() throws Exception {
    var agent = new MessageHandlingRuntimeAgent();
    var lifecycle =
        Message.create(
            "runtime:testcase:1",
            Message.MessageClass.AgentCommunication,
            Message.MessageType.CustomAgentMessage,
            new RuntimeAgent.CustomMessage(
                RuntimeAgent.TestMessageType.TEST_STARTED.constant(), "test-one"));

    agent.send(lifecycle, null, null);

    assertFalse(agent.reservedHandled.await(100, TimeUnit.MILLISECONDS));
    assertEquals(List.of("TEMPERATURE_CHANGED"), agent.getHandledMessageClasses());
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
  void classAliasesInvokeStaticFactoriesWhileInstancesInvokeNonStaticVerbs() {
    var agent = new ReactiveRuntimeAgent();
    var scope = (AgentScope) agent.rootScope();

    assertEquals("static", agent.function(TestActor.class, "kind", scope));
    assertThrows(
        org.integratedmodelling.klab.api.exceptions.KlabActorException.class,
        () -> agent.function(TestActor.class, "instanceValue", scope));

    var instance = agent.function(TestActor.class, "new", scope, "created");
    assertEquals("created", agent.function(instance, "instanceValue", scope));
  }

  @Test
  void reflectiveActorBridgeNegotiatesCompoundArgumentsAfterDirectMatchingFails() {
    var agent = new ReactiveRuntimeAgent();
    var scope = (AgentScope) agent.rootScope();
    var compoundDuration = "2.5 seconds";

    assertThrows(
        org.integratedmodelling.klab.api.exceptions.KlabActorException.class,
        () -> agent.function(TestActor.class, "duration", scope, compoundDuration));

    agent.setParameterNegotiator(
        (unmatchedParameterTypes, suppliedParameters) -> {
          assertEquals(List.of(double.class, TimeUnit.class), unmatchedParameterTypes);
          assertEquals(List.of(compoundDuration), suppliedParameters);
          return List.of(2.5, TimeUnit.SECONDS);
        });

    assertEquals(
        "2.5 SECONDS",
        agent.function(TestActor.class, "duration", scope, compoundDuration));
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
  void dynamicJavaObjectBridgeSupportsMethodsPropertiesAndSnakeCase() {
    var agent = new ReactiveRuntimeAgent();
    var root = (AgentScope) agent.rootScope();
    var values = new ArrayList<Object>();

    assertEquals(Boolean.TRUE, agent.dynamicValue(values, "add", root, "hello"));
    assertEquals(List.of("hello"), values);
    assertEquals(1, agent.dynamicValue(values, "size", root));
    assertEquals(null, agent.dynamicValue(values, "clear", root));
    assertTrue(values.isEmpty());

    var bean = new PlainJavaBean("test value");
    assertEquals("test value", agent.dynamicValue(bean, "display_name", root));
    assertEquals(
        "left:right", agent.dynamicValue(bean, "combine_values", root, "left", "right"));
  }

  @Test
  void javaVerbsReceiveInlineMetadataWithoutLosingOrdinaryArguments() {
    var agent = new ReactiveRuntimeAgent();
    var root = (AgentScope) agent.rootScope();
    Metadata metadata =
        Metadata.create("label", "test", "enabled", true, "disabled", false);
    Object[] arguments = agent.withMetadata(new Object[] {"payload"}, metadata);

    assertEquals(
        "payload:test:true:false",
        agent.function(TestActor.class, "metadata", root, arguments));

    Object[] received =
        (Object[]) agent.function(TestActor.class, "metadataVarargs", root, arguments);
    assertEquals("payload", received[0]);
    assertSame(metadata, received[1]);
  }

  @Test
  void javaVerbVarargsAcceptZeroOrMoreValuesAndNegotiateOnlySuppliedSlots() {
    var agent = new ReactiveRuntimeAgent();
    var root = (AgentScope) agent.rootScope();
    var quantity = new QuantityImpl();
    quantity.setValue(0);
    quantity.setUnit("s");

    assertTrue(
        agent.supplier(CoreActorLibrary.Timer.class, "in", root, quantity).join()
            instanceof TimeInstant);
    assertEquals(
        "payload",
        agent.supplier(CoreActorLibrary.Timer.class, "in", root, quantity, "payload").join());

    var negotiatedSignatures = new ArrayList<List<Class<?>>>();
    agent.setParameterNegotiator(
        (expected, supplied) -> {
          negotiatedSignatures.add(expected);
          var ret = new ArrayList<Object>(supplied);
          if (!ret.isEmpty() && ret.getFirst() instanceof String encoded) {
            ret.set(0, QuantityImpl.parse(encoded));
          }
          return ret;
        });

    assertTrue(
        agent.supplier(CoreActorLibrary.Timer.class, "in", root, "0.s").join()
            instanceof TimeInstant);
    assertEquals(
        "negotiated payload",
        agent
            .supplier(
                CoreActorLibrary.Timer.class,
                "in",
                root,
                "0.s",
                "negotiated payload")
            .join());
    assertEquals(List.of(Quantity.class), negotiatedSignatures.get(0));
    assertEquals(List.of(Quantity.class, Object.class), negotiatedSignatures.get(1));
  }

  @Test
  void coreAgentBehaviorExposesTheRegisteredRuntimeIdentity() {
    var agent = new ReactiveRuntimeAgent();
    var root = (AgentScope) agent.rootScope();
    agent.initializeIdentity("runtime:agent:test:41", "named runtime");
    var core = agent.core(agent);

    assertEquals("runtime:agent:test:41", agent.function(core, "urn", root));
    assertEquals("named runtime", agent.function(core, "name", root));
    assertEquals("runtime:agent:test:41", agent.getUrn());
    assertEquals("named runtime", agent.getName());
  }

  @Test
  void selfCallsFallThroughRetainedBehaviorDelegates() {
    var agent = new InheritingRuntimeAgent();

    assertEquals(
        "inherited:value",
        agent.callInherited((AgentScope) agent.rootScope(), "value"));
  }

  @Test
  void reservedNewPrefersAnnotatedFactoryThenFallsBackToPublicConstructor() {
    var agent = new ReactiveRuntimeAgent();
    var root = (AgentScope) agent.rootScope();

    var factory =
        (FactoryConstructedActor)
            agent.function(FactoryConstructedActor.class, "new", root, "value");
    var constructed =
        (ConstructorOnlyActor)
            agent.function(ConstructorOnlyActor.class, "new", root, "value");

    assertEquals("factory:value", factory.value);
    assertEquals("constructor:value", constructed.value);
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

    private final String value;

    private TestActor(String value) {
      this.value = value;
    }

    @org.integratedmodelling.klab.api.services.runtime.extension.Verb(
        name = "kind",
        executionType = Verb.Type.FUNCTION)
    public static String kind(RuntimeAgent.Scope scope) {
      return "static";
    }

    @org.integratedmodelling.klab.api.services.runtime.extension.Verb(
        name = "new",
        executionType = Verb.Type.FUNCTION)
    public static TestActor create(RuntimeAgent.Scope scope, String value) {
      return new TestActor(value);
    }

    @org.integratedmodelling.klab.api.services.runtime.extension.Verb(
        name = "instanceValue",
        executionType = Verb.Type.FUNCTION)
    public String instanceValue(RuntimeAgent.Scope scope) {
      return value;
    }

    @org.integratedmodelling.klab.api.services.runtime.extension.Verb(
        name = "sum",
        executionType = Verb.Type.FUNCTION)
    public static long sum(RuntimeAgent.Scope scope, long left, long right) {
      return left + right;
    }

    @org.integratedmodelling.klab.api.services.runtime.extension.Verb(
        name = "duration",
        executionType = Verb.Type.FUNCTION)
    public static String duration(
        RuntimeAgent.Scope scope, double amount, TimeUnit unit) {
      return amount + " " + unit;
    }

    @Verb(name = "metadata", executionType = Verb.Type.FUNCTION)
    public static String metadata(RuntimeAgent.Scope scope, String value, Metadata metadata) {
      return value
          + ":"
          + metadata.get("label")
          + ":"
          + metadata.get("enabled")
          + ":"
          + metadata.get("disabled");
    }

    @Verb(name = "metadataVarargs", executionType = Verb.Type.FUNCTION)
    public static Object[] metadataVarargs(RuntimeAgent.Scope scope, Object... values) {
      return values;
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

  private static class LifecycleHookAgent extends RuntimeAgentBase {

    private final AtomicInteger setupCalls = new AtomicInteger();
    private final AtomicInteger disposeCalls = new AtomicInteger();

    private LifecycleHookAgent() {
      super(null, null);
    }

    @Override
    protected AgentScope initializeScope() {
      return new AgentScope(this) {
        @Override
        public void setup() {
          setupCalls.incrementAndGet();
        }

        @Override
        public void dispose() {
          disposeCalls.incrementAndGet();
        }

        @Override
        public org.integratedmodelling.klab.api.scope.SessionScope getSession() {
          return null;
        }

        @Override
        public org.integratedmodelling.klab.api.scope.ContextScope getContext() {
          return null;
        }
      };
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

  private static class MessageHandlingRuntimeAgent extends RuntimeAgentBase {

    private final CountDownLatch handled = new CountDownLatch(1);
    private final CountDownLatch inputHandled = new CountDownLatch(1);
    private final CountDownLatch reservedHandled = new CountDownLatch(1);
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
          new AgentMessageHandler(
              "readLine", Verb.Type.FUNCTION, List.of("line", "sender"), false),
          RuntimeAgent.TestMessageType.TEST_STARTED.messageClass(),
          new AgentMessageHandler("reserved", Verb.Type.FUNCTION, List.of("payload")));
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

    @SuppressWarnings("unused")
    private Object action_reserved(AgentScope scope, Object... arguments) {
      reservedHandled.countDown();
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

  private static class RequestReplyRuntimeAgent extends RuntimeAgentBase {

    private RequestReplyRuntimeAgent() {
      super(null, null);
    }

    @Override
    protected Map<String, AgentMessageHandler> agentMessageHandlers() {
      return Map.of(
          "QUESTION",
          new AgentMessageHandler("answer", Verb.Type.FUNCTION, List.of("payload")));
    }

    @SuppressWarnings("unused")
    private Object action_answer(AgentScope scope, Object... arguments) {
      return "reply:" + arguments[0];
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

  private interface ConnectedScope extends Scope, MessagingChannel {}

  private record ConsoleChunk(RuntimeAgent.ConsoleMessageType type, String text) {}

  private static class ConsoleBufferRuntimeAgent extends ReactiveRuntimeAgent {

    private final List<ConsoleChunk> published = new CopyOnWriteArrayList<>();

    @Override
    protected boolean publishConsoleOutput(
        RuntimeAgent.ConsoleMessageType type, String text) {
      published.add(new ConsoleChunk(type, text));
      return true;
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

    private Object core(Object recipient) {
      return coreAgent(recipient);
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

    private Object[] validateTyped(
        String action, List<String> javaTypes, Object... arguments) {
      return validateActionArguments(
          action, List.of("value"), Arrays.asList((String) null), javaTypes, arguments);
    }

    private Object deferred(Supplier<Object> evaluator) {
      return defer(evaluator);
    }

    private Object resolve(String name, Map<String, Object> frame) {
      return resolveIdentifier(name, frame);
    }

    private Object resolveValue(Object value) {
      return resolveDeferred(value);
    }

    private Object[] withMetadata(Object[] arguments, Metadata metadata) {
      return withVerbMetadata(arguments, metadata);
    }

    private Object evaluate(Expression expression, Map<String, Object> frame) {
      return evaluateExpression(expression, null, frame);
    }

    private boolean match(
        Object payload,
        org.integratedmodelling.klab.api.data.ValueType type,
        Object criterion) {
      return matches(payload, type, criterion, false);
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

  @Test
  void terminalStatusDuringStartPublicationRemainsAuthoritative() {
    var channel = mock(ConnectedScope.class);
    var amqp = mock(AMQPChannel.class);
    when(channel.isConnected()).thenReturn(true);
    when(channel.getFederation())
        .thenReturn(new Federation("test-federation", "amqp://test"));
    when(amqp.isOnline()).thenReturn(true);
    var client = new AgentImpl();
    client.setUrn("agent:short-test");
    client.setViable(true);
    var runtime = new AgentImpl();
    runtime.setUrn("agent:short-test");
    runtime.setViable(true);

    try (var mocked = mockStatic(AMQPChannel.class)) {
      mocked
          .when(() -> AMQPChannel.forAgent(any(), anyString(), any(), any()))
          .thenReturn(amqp);
      assertTrue(client.connect(channel));
      assertTrue(runtime.connect(channel));
      runtime.addMessageListener(
          message -> {
            if (message.getMessageType() == Message.MessageType.AgentStartRequested) {
              runtime.tell(
                  Message.create(
                      runtime.getUrn(),
                      Message.MessageClass.AgentCommunication,
                      Message.MessageType.AgentStopped,
                      new RuntimeAgent.Status(
                          runtime.getUrn(),
                          RuntimeAgent.State.STOPPED,
                          true,
                          null,
                          System.currentTimeMillis())));
            }
          });

      assertTrue(client.start());
      assertFalse(client.isAlive());
    } finally {
      client.disconnect();
      runtime.disconnect();
    }
  }

  @Test
  void deferredTestStartDeliversLifecycleReportBeforeTerminalStatus() {
    var channel = mock(ConnectedScope.class);
    var amqp = mock(AMQPChannel.class);
    when(channel.isConnected()).thenReturn(true);
    when(channel.getFederation())
        .thenReturn(new Federation("test-federation", "amqp://test"));
    when(amqp.isOnline()).thenReturn(true);
    var runtime = new LifecycleReportingTestAgent();
    var client = new AgentImpl();
    client.setUrn("agent:reported-test");
    client.setViable(true);
    var lifecycle = new CopyOnWriteArrayList<RuntimeAgent.TestMessageType>();

    try (var mocked = mockStatic(AMQPChannel.class)) {
      mocked
          .when(() -> AMQPChannel.forAgent(any(), anyString(), any(), any()))
          .thenReturn(amqp);
      assertTrue(
          runtime.initializeMessaging("agent:reported-test", channel, ignored -> {}));
      assertTrue(client.connect(channel));
      client.addMessageListener(
          message -> {
            if (message.getMessageType() == Message.MessageType.CustomAgentMessage) {
              var custom = message.getPayload(RuntimeAgent.CustomMessage.class);
              for (var type : RuntimeAgent.TestMessageType.values()) {
                if (type.messageClass().equals(custom.type().getValue())) {
                  lifecycle.add(type);
                }
              }
            }
          });

      assertTrue(client.start());

      assertEquals(
          List.of(
              RuntimeAgent.TestMessageType.TESTCASE_STARTED,
              RuntimeAgent.TestMessageType.TEST_STARTED,
              RuntimeAgent.TestMessageType.TEST_FINISHED,
              RuntimeAgent.TestMessageType.TESTCASE_FINISHED),
          lifecycle);
      assertFalse(client.isAlive());
    } finally {
      client.disconnect();
      runtime.closeMessaging();
    }
  }

  @Test
  void failedTestcasePublishesStoppedInsteadOfAgentFailed() {
    var channel = mock(ConnectedScope.class);
    var amqp = mock(AMQPChannel.class);
    when(channel.isConnected()).thenReturn(true);
    when(channel.getFederation())
        .thenReturn(new Federation("test-federation", "amqp://test"));
    when(amqp.isOnline()).thenReturn(true);
    var runtime = new FailureIsolatingTestAgent(false);
    var client = new AgentImpl();
    client.setUrn("agent:failed-testcase");
    client.setViable(true);
    var lifecycle = new CopyOnWriteArrayList<Message.MessageType>();

    try (var mocked = mockStatic(AMQPChannel.class)) {
      mocked
          .when(() -> AMQPChannel.forAgent(any(), anyString(), any(), any()))
          .thenReturn(amqp);
      assertTrue(
          runtime.initializeMessaging("agent:failed-testcase", channel, ignored -> {}));
      assertTrue(client.connect(channel));
      client.addMessageListener(message -> lifecycle.add(message.getMessageType()));

      assertTrue(client.start());

      assertTrue(lifecycle.contains(Message.MessageType.AgentStopped));
      assertFalse(lifecycle.contains(Message.MessageType.AgentFailed));
      assertFalse(client.isAlive());
      assertTrue(client.isViable());
    } finally {
      client.disconnect();
      runtime.closeMessaging();
    }
  }

  private record AssertionEvaluation(
      KActorsStatement.Assert.Assertion assertion, boolean success, Throwable exception) {}

  private static class AssertionReportingTestAgent extends TestCaseBase {

    private final List<AssertionEvaluation> evaluations = new ArrayList<>();

    private AssertionReportingTestAgent() {
      super(null, null);
    }

    private void evaluate(
        Supplier<Object> actual,
        Supplier<Object> expected,
        KActorsStatement.Assert.Assertion assertion) {
      assertValue(actual, expected, assertion, (AgentScope) rootScope());
    }

    private KActorsStatement.Assert.Assertion semanticAssertion(String serialized) {
      return assertionLiteral(serialized);
    }

    @Override
    protected AgentScope initializeScope() {
      return new TestCaseScope(this, null, null) {
        @Override
        public void assertionEvaluated(
            KActorsStatement.Assert.Assertion assertion,
            boolean success,
            Throwable exception) {
          evaluations.add(new AssertionEvaluation(assertion, success, exception));
        }
      };
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

  private static class ActionScopeAgent extends RuntimeAgentBase {

    private final AtomicReference<AgentScope> outerScope = new AtomicReference<>();
    private final AtomicReference<AgentScope> innerScope = new AtomicReference<>();

    private ActionScopeAgent() {
      super(null, null);
    }

    private Object callOuter() {
      return invokeSelfFunction("outer", (AgentScope) rootScope());
    }

    @SuppressWarnings("unused")
    private Object action_outer(AgentScope scope, Object... arguments) {
      outerScope.set(scope);
      return invokeSelfFunction("inner", scope);
    }

    @SuppressWarnings("unused")
    private Object action_inner(AgentScope scope, Object... arguments) {
      innerScope.set(scope);
      return "inner-result";
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

  private static class InheritedActionRuntimeAgent extends ReactiveRuntimeAgent {

    @SuppressWarnings("unused")
    private Object action_inherited(AgentScope scope, Object... arguments) {
      return "inherited:" + arguments[0];
    }
  }

  private static class InheritingRuntimeAgent extends ReactiveRuntimeAgent {

    private InheritingRuntimeAgent() {
      registerInheritedBehavior(new InheritedActionRuntimeAgent());
    }

    private Object callInherited(AgentScope scope, Object... arguments) {
      return invokeSelfFunction("inherited", scope, arguments);
    }
  }

  private static class PlainJavaBean {

    private final String displayName;

    private PlainJavaBean(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }

    public String combineValues(String first, String second) {
      return first + ":" + second;
    }
  }

  public static class FactoryConstructedActor {

    private String value;

    public FactoryConstructedActor(String value) {
      this.value = "constructor:" + value;
    }

    @Verb(name = "new")
    public static FactoryConstructedActor create(String value) {
      var ret = new FactoryConstructedActor(value);
      ret.value = "factory:" + value;
      return ret;
    }
  }

  public static class ConstructorOnlyActor {

    private final String value;

    public ConstructorOnlyActor(String value) {
      this.value = "constructor:" + value;
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

    private AgentScope callProbe() {
      return (AgentScope) invokeSelfFunction("probe", (AgentScope) rootScope());
    }

    private DomainObject report() {
      return report;
    }

    private void record(
        AgentScope scope,
        KActorsStatement.Assert.Assertion assertion,
        boolean success,
        Throwable failure) {
      assertionEvaluated(scope, assertion, success, failure);
    }

    private void recordConsole(
        AgentScope scope, RuntimeAgent.ConsoleMessageType stream, String text) {
      sendToConsole(scope, stream, text);
    }

    @SuppressWarnings("unused")
    private Object action_probe(AgentScope scope, Object... arguments) {
      return scope;
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

  private static class LifecycleReportingTestAgent extends TestCaseBase {

    private LifecycleReportingTestAgent() {
      super(null, null);
    }

    @Override
    protected ExitValue main(AgentScope rootScope) {
      var annotation = AnnotationImpl.create("test", "name", "Lifecycle test");
      var testScope = (TestCaseScope) rootScope.withId(1);
      testScope.beforeAction("lifecycle_test", List.of(annotation));
      var assertion = new KActorsStatementImpl.AssertImpl.AssertionImpl();
      assertion.setSourceCode("true");
      testScope.assertionEvaluated(assertion, true, null);
      testScope.afterAction("lifecycle_test", List.of(annotation));
      return NORMAL_EXIT;
    }

    @Override
    public Verb.Type getAgentExecutionMode() {
      return Verb.Type.FUNCTION;
    }
  }

  private static class TestExecutionAgent extends TestCaseBase {

    private TestExecutionAgent(boolean parallel) {
      super(testBehavior(parallel), null);
    }

    private static KActorsBehaviorImpl testBehavior(boolean parallel) {
      var behavior = new KActorsBehaviorImpl();
      behavior.getProperties().put("parallel", parallel);
      return behavior;
    }

    @SafeVarargs
    private final Object execute(Supplier<Object>... tests) {
      return runTests(tests);
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

  private static class FailureIsolatingTestAgent extends TestCaseBase {

    private final List<String> executed = new CopyOnWriteArrayList<>();
    private final AtomicInteger actionId = new AtomicInteger();

    private FailureIsolatingTestAgent(boolean parallel) {
      super(testBehavior(parallel), null);
    }

    private static KActorsBehaviorImpl testBehavior(boolean parallel) {
      var behavior = new KActorsBehaviorImpl();
      behavior.getProperties().put("parallel", parallel);
      return behavior;
    }

    @Override
    protected ExitValue main(AgentScope rootScope) {
      Object result =
          runTests(
              () -> executeTest(rootScope, "failing_test", true),
              () -> executeTest(rootScope, "passing_test", false));
      return ExitValue.success(result);
    }

    private Object executeTest(
        AgentScope rootScope, String actionName, boolean shouldFail) {
      var annotation = AnnotationImpl.create("test", "name", actionName);
      var testScope =
          (TestCaseScope) rootScope.withId(actionId.incrementAndGet());
      testScope.beforeAction(actionName, List.of(annotation));
      Throwable failure = null;
      try {
        executed.add(actionName);
        if (shouldFail) {
          throw new AssertionError("expected test failure");
        }
        return actionName;
      } catch (RuntimeException | Error error) {
        failure = error;
        throw error;
      } finally {
        testScope.afterAction(actionName, List.of(annotation), failure);
      }
    }

    private List<String> executed() {
      return List.copyOf(executed);
    }

    private DomainObject report() {
      return report;
    }

    @Override
    public Verb.Type getAgentExecutionMode() {
      return Verb.Type.FUNCTION;
    }
  }
}
