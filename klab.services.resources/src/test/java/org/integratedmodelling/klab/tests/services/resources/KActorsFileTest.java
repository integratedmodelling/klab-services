package org.integratedmodelling.klab.tests.services.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
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
                        testCase.assertions().accept(
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
            "/statement-verb-operands.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertStatementVerbOperands),
        new Case(
            "/simplegroup.kactor",
            new KActorsVisitor.LenientValidator(),
            this::assertGroupedBehaviorParses));
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

  private void assertSimpleBehavior(KActorsTestSupport.Result result) {
    assertNoParsingOrAdaptationErrors(result);
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());

    var behavior = result.requireBehavior();
    var analyzer = result.requireAnalyzer();
    assertEquals("test.main", behavior.getUrn());
    assertEquals(KActorsBehavior.Type.BEHAVIOR, behavior.getBehaviorType());
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
                KActorsStatement.Fire.class,
                ifStatement.getElseIfs().getFirst().getSecond())
            .getFunction());
    assertEquals(
        "no result",
        assertInstanceOf(KActorsStatement.Fail.class, code.get(5)).getMessage());

    var assertion = assertInstanceOf(KActorsStatement.Assert.class, code.get(6));
    assertEquals(2, assertion.getAssertions().size());
    assertEquals(1, assertion.getAssertions().getFirst().getCalls().size());
    assertEquals(
        Boolean.TRUE,
        assertion.getAssertions().getFirst().getValue().getValue(Boolean.class));
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
