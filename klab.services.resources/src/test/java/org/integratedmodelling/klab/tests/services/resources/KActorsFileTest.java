package org.integratedmodelling.klab.tests.services.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler;
import org.integratedmodelling.klab.runtime.kactors.compiler.BehaviorAnalyzer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class KActorsFileTest {

  private record Case(
      String resource,
      KActorsVisitor.Validator validator,
      Consumer<KActorsTestSupport.Result> assertions) {}

  private final KActorsTestSupport support = new KActorsTestSupport();

  @TestFactory
  Stream<DynamicTest> parsesAndAnalyzesRealKActorsFiles() {
    return cases().stream()
        .map(
            testCase ->
                DynamicTest.dynamicTest(
                    testCase.resource(),
                    () ->
                        testCase
                            .assertions()
                            .accept(
                                support.loadResource(testCase.resource(), testCase.validator()))));
  }

  /** Add a resource, validator and focused assertion function here for each new language case. */
  private List<Case> cases() {
    return List.of(
        new Case("/simple.kactor", timerValidator(), this::assertSimpleBehavior),
        new Case(
            "/emitter-return-value.kactor",
            timerValidator(),
            this::assertEmitterReactiveReturnIsAnExitCode),
        new Case(
            "/assignment-expression.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertExpressionAssignment),
        new Case(
            "/deferred-value.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertDeferredValue),
        new Case(
            "/completed-functional-values.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertCompletedFunctionalValues),
        new Case(
            "/statement-verb-operands.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertStatementVerbOperands),
        new Case(
            "/behavior-contract.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertBehaviorContract),
        new Case(
            "/static-actions.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertStaticActionContract),
        new Case(
            "/returned-agent.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertReturnedAgentContract),
        new Case(
            "/typed-action-parameters.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertTypedActionParameters),
        new Case(
            "/simplegroup.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertGroupedBehaviorParses),
        new Case(
            "/trait-lifecycle.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertTraitLifecycleActions),
        new Case(
            "/component-lifecycle.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertComponentLifecycleActions),
        new Case(
            "/task-behavior.kactor",
            new KActorsVisitor.LenientValidator(),
            result -> assertBehaviorType(result, KActorsBehavior.Type.TASK)),
        new Case(
            "/parallel-testcase.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertParallelTestcaseProperty),
        new Case(
            "/user-behavior.kactor",
            new KActorsVisitor.LenientValidator(),
            result -> assertBehaviorType(result, KActorsBehavior.Type.USER)),
        new Case(
            "/adapted-assignment.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertAdaptedAssignment),
        new Case(
            "/console-agent.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertConsoleInputHandler),
        new Case(
            "/handle-annotation.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertHandleAnnotation),
        new Case(
            "/switch-assignment.kactor",
            functionalConsoleValidator(),
            this::assertFunctionalSwitchAssignment),
        new Case(
            "/nested-value-arguments.kactor",
            functionalConsoleValidator(),
            this::assertNestedValueArguments),
        new Case(
            "/semantic-literal-values.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertSemanticLiteralValues),
        new Case(
            "/java-object-interop.kactor",
            functionalConsoleValidator(),
            this::assertJavaObjectInteroperability),
        new Case(
            "/reserved-agent-verbs.kactor",
            functionalConsoleValidator(),
            this::assertReservedAgentVerbs),
        new Case(
            "/unknown-switch-recipient.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertUnknownSwitchRecipientHasLexicalContext),
        new Case(
            "/library-invalid-lifecycle.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertLibraryRejectsLifecycleActions),
        new Case(
            "/midcomplexity.kactor", midComplexityValidator(), this::assertMidComplexityCompiles));
  }

  private KActorsVisitor.Validator timerValidator() {
    return new KActorsVisitor.LenientValidator() {
      @Override
      public Verb.Type classifyActionCall(
          KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
        if ("timer".equals(verb.getRecipient())) {
          return switch (verb.getMessage()) {
            case "in" -> Verb.Type.SUPPLIER;
            case "random" -> Verb.Type.EMITTER;
            default -> Verb.Type.FUNCTION;
          };
        }
        return Verb.Type.FUNCTION;
      }
    };
  }

  private KActorsVisitor.Validator midComplexityValidator() {
    return new KActorsVisitor.LenientValidator() {
      @Override
      public Verb.Type classifyActionCall(
          KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
        if ("timer".equals(verb.getRecipient())) {
          return switch (verb.getMessage()) {
            case "in" -> Verb.Type.SUPPLIER;
            case "random" -> Verb.Type.EMITTER;
            default -> null;
          };
        }
        if (List.of("console", "context", "strings").contains(verb.getRecipient())) {
          return Verb.Type.FUNCTION;
        }
        return "agent".equals(verb.getRecipient()) && "send".equals(verb.getMessage())
            ? Verb.Type.SUPPLIER
            : null;
      }
    };
  }

  private void assertSimpleBehavior(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());

    var behavior = result.requireBehavior();
    var analyzer = result.requireAnalyzer();
    assertEquals("test.main", behavior.getUrn());
    assertEquals(KActorsBehavior.Type.BEHAVIOR, behavior.getBehaviorType());
    assertEquals("1.0.0", behavior.getVersion().toString());
    assertEquals("test.project", behavior.getProjectName());
    assertEquals(KActorsBehavior.Platform.ANY, behavior.getPlatform());
    assertTrue(behavior.getSourceCode().contains("behavior test.main"));
    assertEquals(
        List.of("timer", "console"),
        behavior.getImports().stream().map(KActorsBehavior.Import::getImportedAlias).toList());
    assertEquals(
        List.of("main", "emitter"),
        behavior.getStatements().stream().map(action -> action.getUrn()).toList());

    var emitter = analyzer.getActions().get("emitter");
    var main = analyzer.getActions().get("main");
    assertNotNull(emitter);
    assertNotNull(main);
    assertTrue(emitter.callsEmitters());
    assertEquals(Verb.Type.EMITTER, emitter.effectiveExecutionType());
    assertTrue(main.callsSuppliers());
    assertTrue(main.callsEmitters(), "local action calls must propagate emitter classification");
    assertEquals(Verb.Type.EMITTER, main.statement().getActionType());
    assertEquals(Verb.Type.EMITTER, analyzer.getAgentExecutionMode());
    assertEquals(BehaviorAnalyzer.Lifecycle.PERSISTENT, analyzer.getLifecycle());

    var firstCall = main.statement().getCode().getFirst();
    assertEquals("test.main", firstCall.getNamespace());
    assertEquals("test.project", firstCall.getProjectName());
    assertEquals(KlabAsset.KnowledgeClass.BEHAVIOR, firstCall.getDocumentClass());
    assertTrue(firstCall.getLength() > 0);
  }

  private void assertParallelTestcaseProperty(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());

    var behavior = result.requireBehavior();
    assertEquals(KActorsBehavior.Type.UNITTEST, behavior.getBehaviorType());
    assertEquals(Boolean.TRUE, behavior.getProperties().get("parallel"));
  }

  private void assertBehaviorContract(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());

    var behavior = result.requireBehavior();
    assertEquals(KActorsBehavior.Type.BEHAVIOR, behavior.getBehaviorType());
    assertEquals("2.3.0", behavior.getVersion().toString());
    assertEquals(KActorsBehavior.Platform.ANY, behavior.getPlatform());
    assertEquals("test.project", behavior.getProjectName());
    assertTrue(behavior.getSourceCode().contains("behavior test.user.contract"));

    var main = behavior.getStatements().getFirst();
    assertEquals(List.of("entry"), main.getAnnotations().stream().map(a -> a.getName()).toList());
    assertEquals("test.user.contract", main.getNamespace());
    assertEquals("test.project", main.getProjectName());
    assertEquals(KlabAsset.KnowledgeClass.BEHAVIOR, main.getDocumentClass());

    var returned =
        assertInstanceOf(KActorsStatement.Return.class, main.getCode().getFirst()).getValue();
    assertNotNull(returned);
    assertNull(returned.getCast(), "an absent cast must be represented by null");
    assertEquals(Integer.valueOf(0), returned.as(Integer.class));
    assertEquals("test.user.contract", returned.getNamespace());
    assertEquals("test.project", returned.getProjectName());
  }

  private void assertTypedActionParameters(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());

    var action = result.requireBehavior().getStatements().getFirst();
    assertEquals(
        List.of("worker", "enabled"),
        action.getArguments().stream().map(KActorsAction.Argument::getName).toList());
    assertEquals("type", action.getArguments().getFirst().getAnnotation().getName());
    assertEquals(
        "workers.base",
        KActorsVisitor.actionArgumentType(action.getArguments().getFirst()).behaviorUrn());
    assertEquals(
        "boolean", KActorsVisitor.actionArgumentType(action.getArguments().get(1)).javaClassName());
  }

  private KActorsVisitor.Validator functionalConsoleValidator() {
    return new KActorsVisitor.LenientValidator() {
      @Override
      public Verb.Type classifyActionCall(
          KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
        if ("console".equals(verb.getRecipient())) {
          return Verb.Type.FUNCTION;
        }
        return "source".equals(verb.getRecipient()) && "read".equals(verb.getMessage())
            ? Verb.Type.EMITTER
            : null;
      }
    };
  }

  private void assertStaticActionContract(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    var actions = result.requireBehavior().getStatements();
    assertTrue(actions.get(0).isStatic());
    assertFalse(actions.get(1).isStatic());
  }

  private void assertReturnedAgentContract(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    var actions = result.requireBehavior().getStatements();
    assertEquals("workers.product", KActorsVisitor.returnedBehaviorUrn(actions.get(0)));
    assertEquals("workers.product", KActorsVisitor.returnedBehaviorUrn(actions.get(1)));
  }

  private void assertGroupedBehaviorParses(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    assertEquals("test.can", result.requireBehavior().getUrn());
    assertTrue(result.requireAnalyzer().getActions().containsKey("main"));
    assertEquals(
        6,
        result.requireAnalyzer().getCalls().size(),
        "calls nested in groups must be visible to analysis");
  }

  private void assertTraitLifecycleActions(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    assertEquals(KActorsBehavior.Type.TRAIT, result.requireBehavior().getBehaviorType());
    assertTrue(result.requireAnalyzer().getActions().containsKey("init"));
    assertTrue(result.requireAnalyzer().getActions().containsKey("main"));
  }

  private void assertComponentLifecycleActions(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    assertEquals(KActorsBehavior.Type.COMPONENT, result.requireBehavior().getBehaviorType());
    assertTrue(result.requireAnalyzer().getActions().containsKey("init"));
    assertTrue(result.requireAnalyzer().getActions().containsKey("main"));
  }

  private void assertBehaviorType(
      KActorsTestSupport.Result result, KActorsBehavior.Type expectedType) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    assertEquals(expectedType, result.requireBehavior().getBehaviorType());
  }

  private void assertAdaptedAssignment(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    var assignment =
        assertInstanceOf(
            KActorsStatement.Assignment.class,
            result.requireBehavior().getStatements().getFirst().getCode().getFirst());
    assertEquals(KActorsStatement.Assignment.Scope.FRAME, assignment.getAssignmentScope());
    assertEquals("test.target", assignment.getAdaptedBehaviorUrn());
  }

  private void assertConsoleInputHandler(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    var action = result.requireBehavior().getStatements().getFirst();
    assertEquals("read_line", action.getUrn());
    assertEquals(
        List.of("line", "sender"),
        action.getArguments().stream().map(KActorsAction.Argument::getName).toList());
    assertEquals(List.of("stdin"), action.getAnnotations().stream().map(Annotation::getName).toList());
    var compiler = new AgentCompiler(result.requireBehavior());
    assertTrue(compiler.compile(), () -> compiler.getNotifications().toString());
    assertTrue(compiler.getSourceCode().contains("handlers.put(\"STDIN\""));
  }

  private void assertHandleAnnotation(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    var action = result.requireBehavior().getStatements().getFirst();
    var annotation = action.getAnnotations().getFirst();
    assertEquals("handle", annotation.getName());
    assertEquals(
        "SAYHELLO",
        KActorsVisitor.handledMessageClass(annotation),
        () ->
            "annotation="
                + annotation
                + ", unnamed="
                + annotation.getUnnamedArguments()
                + ", keys="
                + annotation.getUnnamedKeys());
    var compiler = new AgentCompiler(result.requireBehavior());
    assertTrue(compiler.compile(), () -> compiler.getNotifications().toString());
    assertTrue(compiler.getSourceCode().contains("handlers.put(\"SAYHELLO\""));
    var mapper = JacksonConfiguration.newObjectMapper();
    var restored =
        assertDoesNotThrow(
            () ->
                mapper.readValue(
                    mapper.writeValueAsString(result.requireBehavior()), KActorsBehavior.class));
    var restoredAnnotation = restored.getStatements().getFirst().getAnnotations().getFirst();
    assertEquals("SAYHELLO", KActorsVisitor.handledMessageClass(restoredAnnotation));
    var restoredCompiler = new AgentCompiler(restored);
    assertTrue(
        restoredCompiler.compile(), () -> restoredCompiler.getNotifications().toString());
    assertTrue(restoredCompiler.getSourceCode().contains("handlers.put(\"SAYHELLO\""));
    var noArgumentHandler = restored.getStatements().get(2);
    assertEquals(
        "HEY",
        KActorsVisitor.handledMessageClass(noArgumentHandler.getAnnotations().getFirst()));
    assertTrue(noArgumentHandler.getOffsetInDocument() > 0);
    assertTrue(restoredCompiler.getSourceCode().contains("handlers.put(\"HEY\""));
    var invalidAction = result.requireBehavior().getStatements().get(1);
    var source =
        assertDoesNotThrow(
            () -> {
              try (var input = result.source().openStream()) {
                return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
              }
            });
    assertEquals(
        source.indexOf("@handle(not_a_constant)"),
        invalidAction.getOffsetInDocument(),
        "the action lexical range must begin at its annotation");
  }

  private void assertUnknownSwitchRecipientHasLexicalContext(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertFalse(result.analysisSuccessful());
    var notification =
        result.requireAnalyzer().getNotifications().stream()
            .filter(n -> n.getMessage().contains("recipient") && n.getMessage().contains("strings"))
            .findFirst()
            .orElseThrow(() -> new AssertionError(result.allNotifications().toString()));
    assertNotNull(notification.getLexicalContext());
    assertTrue(
        notification.getLexicalContext().getOffsetInDocument() > 0, () -> notification.toString());
    assertTrue(notification.getLexicalContext().getLength() > 0, () -> notification.toString());
  }

  private void assertFunctionalSwitchAssignment(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());

    var code = result.requireAnalyzer().getActions().get("input").statement().getCode();
    var assignment = assertInstanceOf(KActorsStatement.Assignment.class, code.get(1));
    assertNull(assignment.getValue());
    assertNull(assignment.getFunction());
    var switched = assignment.getSwitch();
    assertNotNull(switched);
    assertEquals(ValueType.IDENTIFIER, switched.getValue().getType());
    assertEquals("message", switched.getValue().getValue(String.class));
    assertEquals(2, switched.getCases().size());
    assertEquals(ValueType.STRING, switched.getCases().get(0).getMatchCriterion().getType());
    assertEquals(ValueType.ANYVALUE, switched.getCases().get(1).getMatchCriterion().getType());

    var verb =
        assertInstanceOf(
            KActorsStatement.Verb.class,
            result
                .requireAnalyzer()
                .getActions()
                .get("match_cases")
                .statement()
                .getCode()
                .getFirst());
    assertEquals(
        List.of(
            ValueType.ANNOTATION,
            ValueType.EMPTY,
            ValueType.ERROR,
            ValueType.ANYVALUE,
            ValueType.ANYTHING),
        verb.getActions().stream().map(match -> match.getMatchCriterion().getType()).toList());

    var compiler =
        new AgentCompiler(result.requireBehavior(), null, functionalConsoleValidator(), null);
    assertTrue(compiler.compile(), () -> compiler.getNotifications().toString());
    assertTrue(compiler.getSourceCode().contains("catch (RuntimeAgentBase.SwitchYield yielded)"));
  }

  private void assertNestedValueArguments(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    var calls = result.requireAnalyzer().getActions().get("sayhello").statement().getCode();
    var format = assertInstanceOf(KActorsStatement.Verb.class, calls.getFirst());
    var switchArgument =
        assertInstanceOf(
            KActorsStatement.CallArgument.class,
            format.getArguments().getUnnamedArguments().get(1));
    assertNotNull(switchArgument.getSwitch());
    assertNull(switchArgument.getFunction());

    var printlnWithVerb = assertInstanceOf(KActorsStatement.Verb.class, calls.get(1));
    var verbArgument =
        assertInstanceOf(
            KActorsStatement.CallArgument.class,
            printlnWithVerb.getArguments().getUnnamedArguments().getFirst());
    assertEquals("strings", verbArgument.getFunction().getRecipient());
    assertEquals("lowercase", verbArgument.getFunction().getMessage());

    var printlnWithExpression = assertInstanceOf(KActorsStatement.Verb.class, calls.get(2));
    var expressionArgument =
        assertInstanceOf(
            org.integratedmodelling.klab.api.lang.kactors.KActorsValue.class,
            printlnWithExpression.getArguments().getUnnamedArguments().getFirst());
    assertEquals(ValueType.EXPRESSION, expressionArgument.getType());

    var compiler = new AgentCompiler(result.requireBehavior());
    assertTrue(compiler.compile(), () -> compiler.getNotifications().toString());
    assertTrue(compiler.getSourceCode().contains("Supplier<Object>"), compiler.getSourceCode());
    assertTrue(
        compiler.getSourceCode().contains("invokeDynamicValue("), compiler.getSourceCode());
    assertTrue(
        compiler.getSourceCode().contains("evaluateExpression(this.expression_0"),
        compiler.getSourceCode());
  }

  private void assertSemanticLiteralValues(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());

    var main = result.requireAnalyzer().getActions().get("main").statement();
    var returned = assertInstanceOf(KActorsStatement.Return.class, main.getCode().getFirst());
    var value = returned.getValue();
    assertEquals(ValueType.MAP, value.getType());
    var map = assertInstanceOf(Map.class, value.getValue(Object.class));
    var direct = map.get("direct");
    var nested = assertInstanceOf(List.class, map.get("nested"));
    var nestedMap = assertInstanceOf(Map.class, nested.get(1));
    assertInstanceOf(KimObservable.class, direct);
    assertInstanceOf(KimObservable.class, nested.getFirst());
    assertInstanceOf(KimObservable.class, nestedMap.get("value"));
    assertEquals("earth:Region", ((KimObservable) direct).getUrn());

    var compiler = new AgentCompiler(result.requireBehavior());
    assertTrue(compiler.compile(), () -> compiler.getNotifications().toString());
    assertTrue(compiler.getSourceCode().contains("observableLiteral("), compiler.getSourceCode());
    assertFalse(
        compiler.getSourceCode().contains("literalValue(ValueType.OBSERVABLE"),
        compiler.getSourceCode());
  }

  private void assertJavaObjectInteroperability(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());

    var input = result.requireAnalyzer().getActions().get("input").statement();
    var add = assertInstanceOf(KActorsStatement.Verb.class, input.getCode().getFirst());
    var call =
        result.requireAnalyzer().getCalls().stream()
            .filter(candidate -> candidate.statement() == add)
            .findFirst()
            .orElseThrow();
    assertEquals(Verb.Type.FUNCTION, call.executionType());
    assertNotNull(call.javaMethod());
    assertEquals("add", call.javaMethod().getName());

    var compiler = new AgentCompiler(result.requireBehavior());
    assertTrue(compiler.compile(), () -> compiler.getNotifications().toString());
    var source = compiler.getSourceCode();
    assertTrue(source.contains("new ArrayList<>()"), source);
    assertTrue(source.contains(").add("), source);
    assertTrue(source.contains(").isEmpty("), source);
    assertTrue(source.contains(").getAbsolutePath("), source);
  }

  private void assertReservedAgentVerbs(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    var action = result.requireBehavior().getStatements().getFirst();
    var tell = assertInstanceOf(KActorsStatement.Verb.class, action.getCode().getFirst());
    var assignment =
        assertInstanceOf(KActorsStatement.Assignment.class, action.getCode().get(1));
    var ask = assignment.getFunction();

    assertEquals(Verb.Type.FUNCTION, result.requireAnalyzer().getCalls().stream()
        .filter(call -> call.statement() == tell)
        .findFirst().orElseThrow().executionType());
    assertEquals(Verb.Type.SUPPLIER, result.requireAnalyzer().getCalls().stream()
        .filter(call -> call.statement() == ask)
        .findFirst().orElseThrow().executionType());
    assertEquals(List.of("timeout"), ask.getArguments().getMetadataKeys());
    assertEquals(ValueType.QUANTITY,
        assertInstanceOf(
                org.integratedmodelling.klab.api.lang.kactors.KActorsValue.class,
                ask.getArguments().get("timeout"))
            .getType());
    assertEquals(2, KActorsVisitor.argumentValues(ask.getArguments()).size());
    var reactiveAsk =
        assertInstanceOf(KActorsStatement.Verb.class, action.getCode().get(3));
    assertEquals("ask", reactiveAsk.getMessage());
    assertEquals(List.of("timeout"), reactiveAsk.getArguments().getMetadataKeys());
    assertEquals(Boolean.FALSE, reactiveAsk.getArguments().get("timeout"));
    assertFalse(reactiveAsk.getActions().isEmpty());
    assertEquals(
        Verb.Type.SUPPLIER,
        result.requireAnalyzer().getCalls().stream()
            .filter(call -> call.statement() == reactiveAsk)
            .findFirst()
            .orElseThrow()
            .executionType());
    var yieldingAsk =
        assertInstanceOf(KActorsStatement.Verb.class, action.getCode().get(4));
    assertInstanceOf(
        KActorsStatement.Yield.class,
        yieldingAsk.getActions().getFirst().getActionOnMatch());
    assertEquals(Verb.Type.SUPPLIER, action.getActionType());

    var compiler = new AgentCompiler(result.requireBehavior());
    assertTrue(compiler.compile(), () -> compiler.getNotifications().toString());
    assertTrue(compiler.getSourceCode().contains("tellAgent("));
    assertTrue(compiler.getSourceCode().contains("askAgent("));
    assertTrue(compiler.getSourceCode().contains("actionResult.complete("));
    assertTrue(compiler.getSourceCode().contains("false"));
  }

  private void assertLibraryRejectsLifecycleActions(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertFalse(result.analysisSuccessful());
    assertEquals(KActorsBehavior.Type.LIBRARY, result.requireBehavior().getBehaviorType());
    var messages = result.requireAnalyzer().getNotifications().toString();
    assertTrue(messages.contains("Library behaviors cannot declare the init action"), messages);
    assertTrue(messages.contains("Library behaviors cannot declare the main action"), messages);
  }

  private void assertMidComplexityCompiles(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    assertFalse(
        result.requireAnalyzer().getNotifications().stream()
            .anyMatch(
                notification ->
                    notification
                        .getMessage()
                        .contains("'then' has no preceding reactive call to wait for")),
        () -> result.requireAnalyzer().getNotifications().toString());
    var compiler = new AgentCompiler(result.requireBehavior());
    assertTrue(assertDoesNotThrow(compiler::compile), () -> compiler.getNotifications().toString());
    assertNotNull(compiler.getSourceCode());
    assertTrue(compiler.getSourceCode().contains("awaitReactions("));
    assertTrue(
        compiler.getSourceCode().contains("quantityLiteral("), compiler.getSourceCode());
  }

  private void assertEmitterReactiveReturnIsAnExitCode(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    assertEquals(
        Verb.Type.EMITTER,
        result.requireAnalyzer().getActions().get("main").effectiveExecutionType());
  }

  private void assertExpressionAssignment(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    var assignment =
        assertInstanceOf(
            KActorsStatement.Assignment.class,
            result.requireAnalyzer().getActions().get("main").statement().getCode().getFirst());
    assertEquals("doubled", assignment.getVariable());
    assertEquals(KActorsStatement.Assignment.Scope.FRAME, assignment.getAssignmentScope());
    assertNotNull(assignment.getValue());
    assertEquals(ValueType.EXPRESSION, assignment.getValue().getType());
    assertEquals("21 * 2", assignment.getValue().getValue(String.class));
    assertFalse(assignment.getValue().isDeferred());
  }

  private void assertDeferredValue(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    var assignment =
        assertInstanceOf(
            KActorsStatement.Assignment.class,
            result.requireAnalyzer().getActions().get("echo").statement().getCode().getFirst());
    assertEquals(ValueType.IDENTIFIER, assignment.getValue().getType());
    assertTrue(assignment.getValue().isDeferred());
    var compiler = new AgentCompiler(result.requireBehavior());
    assertTrue(assertDoesNotThrow(compiler::compile), () -> compiler.getNotifications().toString());
    assertTrue(
        compiler
            .getSourceCode()
            .contains("defer(() -> resolveIdentifier(\"input\", frame))"));
  }

  private void assertCompletedFunctionalValues(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());

    var actions = result.requireAnalyzer().getActions();
    var assignments = actions.get("assign_values").statement().getCode();
    assertEquals(ValueType.EXPRESSION,
        assertInstanceOf(KActorsStatement.Assignment.class, assignments.get(0))
            .getValue().getType());
    var deferred =
        assertInstanceOf(KActorsStatement.Assignment.class, assignments.get(1)).getValue();
    assertEquals(ValueType.EXPRESSION, deferred.getType());
    assertTrue(deferred.isDeferred());
    assertEquals("input + 2", deferred.getValue(String.class));
    assertNotNull(assertInstanceOf(KActorsStatement.Assignment.class, assignments.get(2))
        .getFunction());
    assertNotNull(assertInstanceOf(KActorsStatement.Assignment.class, assignments.get(3))
        .getSwitch());

    var stateAssignments = actions.get("set_values").statement().getCode();
    assertEquals(KActorsStatement.Assignment.Scope.ACTOR,
        assertInstanceOf(KActorsStatement.Assignment.class, stateAssignments.get(0))
            .getAssignmentScope());
    assertNotNull(assertInstanceOf(KActorsStatement.Assignment.class, stateAssignments.get(0))
        .getFunction());
    assertNotNull(assertInstanceOf(KActorsStatement.Assignment.class, stateAssignments.get(1))
        .getSwitch());

    var fires = actions.get("fire_values").statement().getCode();
    assertNotNull(assertInstanceOf(KActorsStatement.Fire.class, fires.get(0)).getValue());
    assertNotNull(assertInstanceOf(KActorsStatement.Fire.class, fires.get(1)).getFunction());
    assertNotNull(assertInstanceOf(KActorsStatement.Fire.class, fires.get(2)).getSwitch());

    var returned =
        assertInstanceOf(
            KActorsStatement.Return.class,
            actions.get("return_values").statement().getCode().getFirst());
    assertNotNull(returned.getSwitch());
    assertNotNull(
        assertInstanceOf(
                KActorsStatement.Yield.class,
                returned.getSwitch().getCases().getFirst().getActionOnMatch())
            .getFunction());
    assertNotNull(
        assertInstanceOf(
                KActorsStatement.Yield.class,
                returned.getSwitch().getCases().get(1).getActionOnMatch())
            .getSwitch());

    var ternaryReturn =
        assertInstanceOf(
            KActorsStatement.Return.class,
            actions.get("ternary_values").statement().getCode().getFirst());
    var ternary =
        ternaryReturn
            .getValue()
            .getValue(org.integratedmodelling.klab.api.lang.Ternary.class);
    assertInstanceOf(KActorsStatement.Verb.class, ternary.getTrueCase());
    assertInstanceOf(KActorsStatement.Switch.class, ternary.getFalseCase());

    var handler = actions.get("dotted_message").statement();
    assertEquals(
        "MESSAGES.HELLO",
        KActorsVisitor.handledMessageClass(handler.getAnnotations().getFirst()));

    var mapper = JacksonConfiguration.newObjectMapper();
    var restored =
        assertDoesNotThrow(
            () ->
                mapper.readValue(
                    mapper.writeValueAsString(result.requireBehavior()), KActorsBehavior.class));
    var restoredReturn =
        assertInstanceOf(
            KActorsStatement.Return.class,
            restored.getStatements().stream()
                .filter(action -> "ternary_values".equals(action.getUrn()))
                .findFirst()
                .orElseThrow()
                .getCode()
                .getFirst());
    var restoredTernary =
        restoredReturn
            .getValue()
            .getValue(org.integratedmodelling.klab.api.lang.Ternary.class);
    assertInstanceOf(KActorsStatement.Verb.class, restoredTernary.getTrueCase());
    assertInstanceOf(KActorsStatement.Switch.class, restoredTernary.getFalseCase());
    assertInstanceOf(
        org.integratedmodelling.klab.api.lang.kactors.KActorsValue.class,
        restoredTernary.getCondition());

    var compiler = new AgentCompiler(restored);
    assertTrue(assertDoesNotThrow(compiler::compile), () -> compiler.getNotifications().toString());
    var generated = compiler.getSourceCode();
    assertTrue(generated.contains("defer(() -> evaluateExpression("), generated);
    assertTrue(generated.contains("Constant.create(\"MESSAGES.HELLO\")"), generated);
    assertTrue(generated.contains("? invokeSelfFunction("), generated);
    assertTrue(generated.contains(": ((Supplier<Object>) () ->"), generated);
  }

  private void assertStatementVerbOperands(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    var code = result.requireAnalyzer().getActions().get("main").statement().getCode();

    var whileStatement = assertInstanceOf(KActorsStatement.While.class, code.get(0));
    assertNotNull(whileStatement.getFunction());
    assertNotNull(whileStatement.getBody());

    var doStatement = assertInstanceOf(KActorsStatement.Do.class, code.get(1));
    assertNotNull(doStatement.getFunction());
    assertNotNull(doStatement.getBody());

    var forStatement = assertInstanceOf(KActorsStatement.For.class, code.get(2));
    assertEquals("item", forStatement.getVariable());
    assertNotNull(forStatement.getFunction());
    assertNotNull(forStatement.getBody());

    var anonymousFor = assertInstanceOf(KActorsStatement.For.class, code.get(3));
    assertNull(anonymousFor.getVariable());
    assertNotNull(anonymousFor.getFunction());
    assertNotNull(anonymousFor.getBody());

    var ifStatement = assertInstanceOf(KActorsStatement.If.class, code.get(4));
    assertNotNull(ifStatement.getFunction());
    assertInstanceOf(KActorsStatement.Return.class, ifStatement.getThenBody());
    assertNotNull(
        assertInstanceOf(KActorsStatement.Return.class, ifStatement.getThenBody()).getFunction());
    assertEquals(1, ifStatement.getElseIfs().size());
    assertNotNull(ifStatement.getElseIfs().getFirst().getFirst().getSecond());
    assertNotNull(
        assertInstanceOf(
                KActorsStatement.Fire.class, ifStatement.getElseIfs().getFirst().getSecond())
            .getFunction());
    assertEquals(
        "no result", assertInstanceOf(KActorsStatement.Fail.class, code.get(5)).getMessage());

    var assertion = assertInstanceOf(KActorsStatement.Assert.class, code.get(6));
    assertEquals(2, assertion.getAssertions().size());
    assertEquals(1, assertion.getAssertions().getFirst().getCalls().size());
    assertEquals(
        Boolean.TRUE, assertion.getAssertions().getFirst().getValue().getValue(Boolean.class));
    assertEquals(ValueType.EXPRESSION, assertion.getAssertions().get(1).getExpression().getType());
  }

  private void assertNoParsingOrAdaptationErrors(KActorsTestSupport.Result result) {
    assertTrue(
        result.parserNotifications().isEmpty(), () -> result.parserNotifications().toString());
    assertTrue(
        result.adaptationNotifications().isEmpty(),
        () -> result.adaptationNotifications().toString());
    assertNotNull(result.behavior());
  }
}
