package org.integratedmodelling.klab.runtime.kactors.compiler;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsArgumentsImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsStatementImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsValueImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.junit.jupiter.api.Test;

class BehaviorAnalyzerTest {

  @Test
  void infersActionTypesAndBuildsCompilerRecords() {
    var function = action("function", returned(number(1)));

    var reaction = returned(number(2));
    var match = new KActorsStatementImpl.VerbImpl.MatchActionImpl();
    match.setActionOnMatch(reaction);
    var call = verb("external", "listen");
    call.setActions(List.of(match));
    var supplier = action("supplier", call);

    var emitter = action("emitter", fired(number(3)));
    var behavior = behavior(function, supplier, emitter);
    behavior.setImports(List.of(imported("component.behavior", "external")));

    var analyzer =
        new BehaviorAnalyzer(
            behavior,
            new KActorsVisitor.LenientValidator() {
              @Override
              public Verb.Type classifyActionCall(
                  KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
                return Verb.Type.SUPPLIER;
              }
            });

    assertTrue(analyzer.analyze(), messages(analyzer));
    assertEquals(Verb.Type.FUNCTION, function.getActionType());
    assertEquals(Verb.Type.SUPPLIER, supplier.getActionType());
    assertEquals(Verb.Type.EMITTER, emitter.getActionType());
    assertEquals(1, analyzer.getActions().get("supplier").returns());
    assertEquals(1, analyzer.getActions().get("emitter").fires());
    assertEquals(1, analyzer.getCalls().size());
    assertEquals("external", analyzer.getCalls().getFirst().agent());
  }

  @Test
  void emitterMayStopFromAMatchAndExposeTheReturnValueAsAnExitCode() {
    var stop = returned(number(0));
    var match = new KActorsStatementImpl.VerbImpl.MatchActionImpl();
    match.setActionOnMatch(stop);
    var call = verb("external", "stream");
    call.setActions(List.of(match));
    var emitter = action("emitter", fired(number(1)), call);
    var behavior = behavior(emitter);
    behavior.setImports(List.of(imported("component.behavior", "external")));

    var analyzer = new BehaviorAnalyzer(behavior, new ResolvingValidator());

    assertTrue(analyzer.analyze(), messages(analyzer));
    assertEquals(Verb.Type.EMITTER, emitter.getActionType());
    assertEquals(1, analyzer.getActions().get("emitter").fires());
    assertEquals(1, analyzer.getActions().get("emitter").returns());
  }

  @Test
  void effectiveEmitterMayUseAValuedReactiveReturn() {
    var match = new KActorsStatementImpl.VerbImpl.MatchActionImpl();
    match.setActionOnMatch(returned(number(0)));
    var call = verb("external", "stream");
    call.setActions(List.of(match));
    var wrapper = action("wrapper", call);
    var behavior = behavior(wrapper);
    behavior.setImports(List.of(imported("component.behavior", "external")));

    var analyzer = new BehaviorAnalyzer(behavior, new ResolvingValidator());

    assertTrue(analyzer.analyze(), messages(analyzer));
    assertEquals(Verb.Type.EMITTER, wrapper.getActionType());
    assertTrue(analyzer.getActions().get("wrapper").callsEmitters());
  }

  @Test
  void reactiveReturnInEmitterStillRequiresAnExitCode() {
    var match = new KActorsStatementImpl.VerbImpl.MatchActionImpl();
    match.setActionOnMatch(returned(null));
    var call = verb("external", "stream");
    call.setActions(List.of(match));
    var emitter = action("emitter", fired(number(1)), call);
    var behavior = behavior(emitter);
    behavior.setImports(List.of(imported("component.behavior", "external")));

    var analyzer = new BehaviorAnalyzer(behavior, new ResolvingValidator());

    assertFalse(analyzer.analyze());
    assertEquals(Verb.Type.EMITTER, emitter.getActionType());
    assertTrue(messages(analyzer).contains("Exactly one return value"));
  }

  @Test
  void supplierReactiveReturnStillRequiresAValue() {
    var match = new KActorsStatementImpl.VerbImpl.MatchActionImpl();
    match.setActionOnMatch(returned(null));
    var call = verb("external", "later");
    call.setActions(List.of(match));
    var supplier = action("supplier", call);
    var behavior = behavior(supplier);
    behavior.setImports(List.of(imported("component.behavior", "external")));
    var validator =
        new KActorsVisitor.LenientValidator() {
          @Override
          public Verb.Type classifyActionCall(
              KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
            return Verb.Type.SUPPLIER;
          }
        };

    var analyzer = new BehaviorAnalyzer(behavior, validator);

    assertFalse(analyzer.analyze());
    assertEquals(Verb.Type.SUPPLIER, supplier.getActionType());
    assertTrue(messages(analyzer).contains("Exactly one return value"));
  }

  @Test
  void reportsDocumentedScopeAndControlFlowErrors() {
    var state = assignment("state", KActorsStatement.Assignment.Scope.ACTOR, number(1));
    var init = action("init", state);

    var shadow = assignment("state", KActorsStatement.Assignment.Scope.FRAME, number(2));
    var breakStatement = new KActorsStatementImpl.BreakImpl();
    var unknown = returned(identifier("missing"));
    var sequential = fired(number(4));
    sequential.setSequential(true);
    var aliasOverride = assignment("external", KActorsStatement.Assignment.Scope.FRAME, number(5));
    var main = action("main", shadow, breakStatement, unknown, sequential, aliasOverride);

    var behavior = behavior(init, main);
    behavior.setImports(List.of(imported("component.behavior", "external")));
    var analyzer = new BehaviorAnalyzer(behavior);

    assertFalse(analyzer.analyze());
    var messages = messages(analyzer);
    assertTrue(messages.contains("cannot override actor state"), messages);
    assertTrue(messages.contains("break can only be used inside a loop"), messages);
    assertTrue(messages.contains("Unknown identifier: missing"), messages);
    assertTrue(messages.contains("no preceding reactive call"), messages);
    assertTrue(messages.contains("cannot override an import alias"), messages);
  }

  @Test
  void frameVariablesRemainVisibleToFollowingSiblingsButDoNotEscapeTheirGroup() {
    var local = assignment("local", KActorsStatement.Assignment.Scope.FRAME, number(1));
    var useInside = returned(identifier("local"));
    var group = new KActorsStatementImpl.GroupImpl();
    group.setStatements(List.of(local, useInside));
    var useOutside = returned(identifier("local"));
    var analyzer = new BehaviorAnalyzer(behavior(action("main", group, useOutside)));

    assertFalse(analyzer.analyze());
    assertEquals(
        1,
        analyzer.getNotifications().stream()
            .filter(notification -> notification.getMessage().equals("Unknown identifier: local"))
            .count());
  }

  @Test
  void invokesValidatorAndRejectsEmitterInValuePosition() {
    var external = verb("external", "stream");
    var assignment = new KActorsStatementImpl.AssignmentImpl();
    assignment.setVariable("result");
    assignment.setAssignmentScope(KActorsStatement.Assignment.Scope.FRAME);
    assignment.setFunction(external);
    var behavior = behavior(action("main", assignment));
    behavior.setImports(List.of(imported("component.behavior", "external")));
    var validator = new ResolvingValidator();

    var analyzer = new BehaviorAnalyzer(behavior, validator);

    assertFalse(analyzer.analyze());
    assertTrue(validator.verbValidated);
    assertTrue(messages(analyzer).contains("Emitter calls cannot be used where a value is required"));
    assertEquals(Verb.Type.EMITTER, analyzer.getCalls().getFirst().executionType());
    assertTrue(analyzer.getCalls().getFirst().valueRequired());
  }

  @Test
  void propagatesExternalEmitterCallsTransitivelyToMainLifecycle() {
    var helper = action("helper", verb("external", "stream"));
    var main = action("main", verb("self", "helper"));
    var behavior = behavior(main, helper);
    behavior.setImports(List.of(imported("component.behavior", "external")));

    var analyzer = new BehaviorAnalyzer(behavior, new ResolvingValidator());

    assertTrue(analyzer.analyze(), messages(analyzer));
    assertTrue(analyzer.getActions().get("helper").callsEmitters());
    assertTrue(analyzer.getActions().get("main").callsEmitters());
    assertEquals(
        Verb.Type.EMITTER, analyzer.getActions().get("main").effectiveExecutionType());
    assertEquals(Verb.Type.EMITTER, analyzer.getAgentExecutionMode());
    assertEquals(BehaviorAnalyzer.Lifecycle.PERSISTENT, analyzer.getLifecycle());
  }

  @Test
  void supplierCallsKeepExecutionFiniteButAsynchronous() {
    var main = action("main", verb("external", "later"));
    var behavior = behavior(main);
    behavior.setImports(List.of(imported("component.behavior", "external")));
    var validator =
        new KActorsVisitor.LenientValidator() {
          @Override
          public Verb.Type classifyActionCall(
              KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
            return Verb.Type.SUPPLIER;
          }
        };

    var analyzer = new BehaviorAnalyzer(behavior, validator);

    assertTrue(analyzer.analyze(), messages(analyzer));
    assertTrue(analyzer.getActions().get("main").callsSuppliers());
    assertEquals(
        Verb.Type.SUPPLIER, analyzer.getActions().get("main").effectiveExecutionType());
    assertEquals(Verb.Type.SUPPLIER, analyzer.getAgentExecutionMode());
    assertEquals(BehaviorAnalyzer.Lifecycle.FINITE, analyzer.getLifecycle());
  }

  @Test
  void compilerEmitsTheAnalyzedAgentExecutionMode() {
    var compiler = new AgentCompiler(behavior(action("main", fired(number(1)))));

    compiler.compile();

    assertEquals(Verb.Type.EMITTER, compiler.getAgentExecutionMode());
    assertEquals(BehaviorAnalyzer.Lifecycle.PERSISTENT, compiler.getLifecycle());
    assertNotNull(compiler.getSourceCode());
    assertTrue(compiler.getSourceCode().contains("return Verb.Type.EMITTER;"));
  }

  private static class ResolvingValidator extends KActorsVisitor.LenientValidator {
    private boolean verbValidated;

    @Override
    public List<org.integratedmodelling.klab.api.services.runtime.Notification> validateVerbCall(
        KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
      verbValidated = true;
      return List.of();
    }

    @Override
    public Verb.Type classifyActionCall(
        KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
      return Verb.Type.EMITTER;
    }
  }

  private static KActorsBehaviorImpl behavior(KActorsActionImpl... actions) {
    var behavior = new KActorsBehaviorImpl();
    behavior.setUrn("test.behavior");
    behavior.setDescription("Test behavior");
    behavior.setBehaviorType(KActorsBehavior.Type.BEHAVIOR);
    behavior.setStatements(List.of(actions));
    return behavior;
  }

  private static KActorsBehavior.Import imported(String urn, String alias) {
    var imported = new KActorsBehaviorImpl.ImportImpl();
    imported.setImportedBehavior(urn);
    imported.setImportedAlias(alias);
    return imported;
  }

  private static KActorsActionImpl action(String name, KActorsStatement... statements) {
    var action = new KActorsActionImpl();
    action.setUrn(name);
    action.setCode(List.of(statements));
    return action;
  }

  private static KActorsStatementImpl.AssignmentImpl assignment(
      String name, KActorsStatement.Assignment.Scope scope, KActorsValueImpl value) {
    var assignment = new KActorsStatementImpl.AssignmentImpl();
    assignment.setVariable(name);
    assignment.setAssignmentScope(scope);
    assignment.setValue(value);
    return assignment;
  }

  private static KActorsStatementImpl.ReturnImpl returned(KActorsValueImpl value) {
    var statement = new KActorsStatementImpl.ReturnImpl();
    statement.setValue(value);
    return statement;
  }

  private static KActorsStatementImpl.FireImpl fired(KActorsValueImpl value) {
    var statement = new KActorsStatementImpl.FireImpl();
    statement.setValue(value);
    return statement;
  }

  private static KActorsStatementImpl.VerbImpl verb(String recipient, String message) {
    var statement = new KActorsStatementImpl.VerbImpl();
    statement.setRecipient(recipient);
    statement.setMessage(message);
    statement.setArguments(new KActorsArgumentsImpl());
    return statement;
  }

  private static KActorsValueImpl number(int number) {
    var value = new KActorsValueImpl();
    value.setType(ValueType.NUMBER);
    value.setStatedValue(number);
    return value;
  }

  private static KActorsValueImpl identifier(String identifier) {
    var value = new KActorsValueImpl();
    value.setType(ValueType.IDENTIFIER);
    value.setStatedValue(identifier);
    return value;
  }

  private static String messages(BehaviorAnalyzer analyzer) {
    return analyzer.getNotifications().stream()
        .map(Notification::getMessage)
        .reduce("", (left, right) -> left + "\n" + right);
  }
}
