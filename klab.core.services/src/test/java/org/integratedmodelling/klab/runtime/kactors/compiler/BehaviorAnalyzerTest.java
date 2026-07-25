package org.integratedmodelling.klab.runtime.kactors.compiler;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.tools.DiagnosticCollector;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.integratedmodelling.common.lang.TernaryImpl;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.lang.Annotation;
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
import org.integratedmodelling.klab.runtime.kactors.ApplicationBase;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.integratedmodelling.klab.runtime.kactors.ScriptBase;
import org.integratedmodelling.klab.runtime.kactors.TestCaseBase;
import org.junit.jupiter.api.Test;

class BehaviorAnalyzerTest {

  @Test
  void compilerRegistersNamedAndUnnamedHandleAnnotations() {
    var named = action("namedHandler");
    named.setArgumentNames(List.of("payload", "sender"));
    var namedParameters = new java.util.LinkedHashMap<String, Object>();
    namedParameters.put("class", Constant.create("NAMED"));
    named.setAnnotations(
        List.of(Annotation.of("handle", namedParameters)));
    var unnamedAnnotation = Annotation.of("handle");
    unnamedAnnotation.putUnnamed(Constant.create("UNNAMED"));
    var unnamed = action("unnamedHandler");
    unnamed.setAnnotations(List.of(unnamedAnnotation));
    var compiler = new AgentCompiler(behavior(named, unnamed));

    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    String source = compiler.getSourceCode();
    assertTrue(source.contains("handlers.put(\"NAMED\""), source);
    assertTrue(source.contains("\"namedHandler\", Verb.Type.FUNCTION"), source);
    assertTrue(source.contains("List.of(\"payload\", \"sender\")"), source);
    assertTrue(source.contains("handlers.put(\"UNNAMED\""), source);
  }

  @Test
  void compilerRegistersStdinAnnotationAsConsoleInputHandler() {
    var stdin = action("readLine");
    stdin.setArgumentNames(List.of("line", "sender"));
    stdin.setAnnotations(List.of(Annotation.of("stdin")));
    var compiler = new AgentCompiler(behavior(stdin));

    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    String source = compiler.getSourceCode();
    assertTrue(source.contains("handlers.put(\"STDIN\""), source);
    assertTrue(source.contains("\"readLine\", Verb.Type.FUNCTION"), source);
    assertTrue(source.contains("List.of(\"line\", \"sender\")"), source);
  }

  @Test
  void compilerComposesHandlersFromInheritedBehaviors() {
    var inheritedAction = action("inheritedHandler");
    var annotation = Annotation.of("handle");
    annotation.putUnnamed(Constant.create("INHERITED"));
    inheritedAction.setAnnotations(List.of(annotation));
    var inherited = behavior(inheritedAction);
    inherited.setUrn("traits.handlers");
    inherited.setBehaviorType(KActorsBehavior.Type.TRAIT);
    var child = behavior();
    child.setInheritedBehaviors(List.of(inherited.getUrn()));
    var compiler =
        new AgentCompiler(
            child,
            null,
            new KActorsVisitor.LenientValidator(),
            new AgentCompiler.Resolver() {
              @Override
              public KActorsBehavior resolveBehavior(
                  String urn,
                  org.integratedmodelling.klab.api.scope.UserScope scope) {
                return inherited.getUrn().equals(urn) ? inherited : null;
              }
            });

    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    assertTrue(compiler.getGeneratedSources().containsKey(inherited.getUrn()));
    assertTrue(
        compiler
            .getGeneratedSources()
            .get(inherited.getUrn())
            .contains("handlers.put(\"INHERITED\""));
    assertTrue(
        compiler
            .getSourceCode()
            .contains("inheritAgentMessageHandlers(handlers, this.inherited_0)"));
  }

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
  void traitsAndComponentsAllowLifecycleActionsButLibrariesRejectThem() {
    var trait = behavior(action("init"), action("main", returned(number(0))));
    trait.setBehaviorType(KActorsBehavior.Type.TRAIT);
    var traitAnalyzer = new BehaviorAnalyzer(trait);

    assertTrue(traitAnalyzer.analyze(), messages(traitAnalyzer));
    assertTrue(traitAnalyzer.getActions().containsKey("init"));
    assertTrue(traitAnalyzer.getActions().containsKey("main"));

    var component = behavior(action("init"), action("main", returned(number(0))));
    component.setBehaviorType(KActorsBehavior.Type.COMPONENT);
    var componentAnalyzer = new BehaviorAnalyzer(component);

    assertTrue(componentAnalyzer.analyze(), messages(componentAnalyzer));
    assertTrue(componentAnalyzer.getActions().containsKey("init"));
    assertTrue(componentAnalyzer.getActions().containsKey("main"));

    var library = behavior(action("init"), action("main", returned(number(0))));
    library.setBehaviorType(KActorsBehavior.Type.LIBRARY);
    var libraryAnalyzer = new BehaviorAnalyzer(library);

    assertFalse(libraryAnalyzer.analyze());
    assertTrue(
        messages(libraryAnalyzer).contains("Library behaviors cannot declare the init action"),
        messages(libraryAnalyzer));
    assertTrue(
        messages(libraryAnalyzer).contains("Library behaviors cannot declare the main action"),
        messages(libraryAnalyzer));
  }

  @Test
  void rejectsDuplicateTagsWithinABehavior() {
    var first = fired(number(1));
    first.setTag("status");
    var second = fired(number(2));
    second.setTag("status");
    var analyzer = new BehaviorAnalyzer(behavior(action("main", first, second)));

    assertFalse(analyzer.analyze());
    assertTrue(messages(analyzer).contains("Duplicate tag #status"), messages(analyzer));
  }

  @Test
  void rejectsTagsDuplicatedByImportsAndInheritedBehaviors() {
    var local = fired(number(1));
    local.setTag("inherited");
    var behavior = behavior(action("main", local));
    behavior.setImports(List.of(imported("component.widgets", "widgets")));
    behavior.setInheritedBehaviors(List.of("traits.base"));
    var analyzer =
        new BehaviorAnalyzer(
            behavior,
            new KActorsVisitor.LenientValidator() {
              @Override
              public List<String> getBehaviorTags(
                  String behaviorUrn, KActorsVisitor.KActorsContext context) {
                return switch (behaviorUrn) {
                  case "component.widgets" -> List.of("shared", "component");
                  case "traits.base" -> List.of("shared", "inherited");
                  default -> List.of();
                };
              }
            });

    assertFalse(analyzer.analyze());
    var messages = messages(analyzer);
    assertTrue(messages.contains("Duplicate tag #shared"), messages);
    assertTrue(messages.contains("Duplicate tag #inherited"), messages);
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
  void adaptedAssignmentsAcquireTheTargetAgentTypeAndCompileThroughRuntimeHook() {
    var adapted = assignment("worker", KActorsStatement.Assignment.Scope.FRAME, number(1));
    adapted.setAdaptedBehaviorUrn("workers.specialized");
    var call = verb("worker", "process");
    var behavior = behavior(action("main", adapted, call));
    var adaptationValidated = new boolean[1];
    var callValidated = new boolean[1];
    var validator =
        new KActorsVisitor.LenientValidator() {
          @Override
          public List<Notification> validateAdaptation(
              KActorsStatement.Assignment assignment,
              String behaviorUrn,
              KActorsVisitor.VariableInfo sourceVariable,
              KActorsVisitor.KActorsContext context) {
            adaptationValidated[0] =
                "workers.specialized".equals(behaviorUrn)
                    && sourceVariable.type() == ValueType.NUMBER;
            return List.of();
          }

          @Override
          public Verb.Type classifyActionCall(
              KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
            var variable = context.getVariable(verb.getRecipient());
            return variable != null && "workers.specialized".equals(variable.agentUrn())
                ? Verb.Type.FUNCTION
                : null;
          }

          @Override
          public List<Notification> validateVerbCall(
              KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
            callValidated[0] = true;
            return List.of();
          }
        };
    var analyzer = new BehaviorAnalyzer(behavior, validator);

    assertTrue(analyzer.analyze(), messages(analyzer));
    assertTrue(adaptationValidated[0]);
    assertTrue(callValidated[0]);
    assertEquals(
        "workers.specialized",
        analyzer.getCalls().getFirst().knownVariables().get("worker").agentUrn());

    var compiler = new AgentCompiler(behavior, null, validator, null);
    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    assertTrue(compiler.getSourceCode().contains("adaptToBehavior("), compiler.getSourceCode());
    assertTrue(compiler.getSourceCode().contains("\"workers.specialized\""), compiler.getSourceCode());
    assertGeneratedJavaCompiles(compiler.getSourceCode());
  }

  @Test
  void behaviorAdaptationIsLocalOnlyAndValidationErrorsDoNotTypeTheVariable() {
    var actorAssignment =
        assignment("worker", KActorsStatement.Assignment.Scope.ACTOR, number(1));
    actorAssignment.setAdaptedBehaviorUrn("workers.specialized");
    var localAssignment =
        assignment("other", KActorsStatement.Assignment.Scope.FRAME, number(2));
    localAssignment.setAdaptedBehaviorUrn("workers.missing");
    var validator =
        new KActorsVisitor.LenientValidator() {
          @Override
          public List<Notification> validateAdaptation(
              KActorsStatement.Assignment assignment,
              String behaviorUrn,
              KActorsVisitor.VariableInfo sourceVariable,
              KActorsVisitor.KActorsContext context) {
            return List.of(Notification.error("Unknown adaptation behavior " + behaviorUrn));
          }
        };
    var analyzer =
        new BehaviorAnalyzer(behavior(action("init", actorAssignment), action("main", localAssignment)), validator);

    assertFalse(analyzer.analyze());
    assertTrue(messages(analyzer).contains("only allowed on local frame assignments"));
    assertTrue(messages(analyzer).contains("Unknown adaptation behavior workers.missing"));
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
  void unknownCallsRemainUnclassifiedAndSkipVerbValidation() {
    var call = verb("external", "maybeReactive");
    var source = behavior(action("main", call));
    source.setImports(List.of(imported("component.behavior", "external")));
    var verbValidated = new java.util.concurrent.atomic.AtomicBoolean();
    var validator =
        new KActorsVisitor.LenientValidator() {
          @Override
          public List<Notification> validateVerbCall(
              KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
            verbValidated.set(true);
            return List.of();
          }

          @Override
          public boolean warnAboutUnknownActionCall(
              KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
            return true;
          }
        };
    var analyzer = new BehaviorAnalyzer(source, validator);

    assertTrue(analyzer.analyze(), messages(analyzer));
    assertNull(analyzer.getCalls().getFirst().executionType());
    assertFalse(verbValidated.get(), "unknown calls must not run type-dependent validation");
    assertTrue(analyzer.getActions().get("main").callsUnknownActions());
    assertEquals(Verb.Type.EMITTER, analyzer.getAgentExecutionMode());
    assertEquals(BehaviorAnalyzer.Lifecycle.PERSISTENT, analyzer.getLifecycle());
    assertTrue(
        analyzer.getNotifications().stream()
            .anyMatch(notification -> notification.getMessage().contains("Cannot establish")));
  }

  @Test
  void validatorCanClassifyRecipientsFromAssignmentAndLoopProducerCalls() {
    var assignedProducer = verb("external", "makeWorker");
    var assignedCall = verb("worker", "run");
    var iterableProducer = verb("external", "workers");
    var loopCall = verb("workerItem", "run");
    var loop = new KActorsStatementImpl.ForImpl();
    loop.setVariable("workerItem");
    loop.setFunction(iterableProducer);
    loop.setBody(loopCall);
    var source =
        behavior(
            action(
                "main",
                assignment("worker", KActorsStatement.Assignment.Scope.FRAME, assignedProducer),
                assignedCall,
                loop));
    source.setImports(List.of(imported("component.behavior", "external")));
    var producers = new java.util.IdentityHashMap<KActorsStatement.Verb, KActorsStatement.Verb>();
    var validator =
        new KActorsVisitor.LenientValidator() {
          @Override
          public Verb.Type classifyActionCall(
              KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
            return verb == assignedProducer || verb == iterableProducer
                ? Verb.Type.FUNCTION
                : null;
          }

          @Override
          public Verb.Type classifyActionCallFromProducer(
              KActorsStatement.Verb verb,
              KActorsStatement.Verb recipientProducer,
              KActorsVisitor.KActorsContext context) {
            producers.put(verb, recipientProducer);
            return Verb.Type.SUPPLIER;
          }
        };
    var analyzer = new BehaviorAnalyzer(source, validator);

    assertTrue(analyzer.analyze(), messages(analyzer));
    assertSame(assignedProducer, producers.get(assignedCall));
    assertSame(iterableProducer, producers.get(loopCall));
    assertEquals(
        Verb.Type.SUPPLIER,
        analyzer.getCalls().stream()
            .filter(call -> call.statement() == assignedCall)
            .findFirst()
            .orElseThrow()
            .executionType());
  }

  @Test
  void compilerRoutesUnknownCallsThroughTheDynamicRuntimeBridge() {
    var statementCall = verb("external", "unknownStatement");
    var valueCall = verb("external", "unknownValue");
    var source =
        behavior(
            action(
                "main",
                statementCall,
                assignment("value", KActorsStatement.Assignment.Scope.FRAME, valueCall),
                returned(identifier("value"))));
    source.setImports(List.of(imported("component.behavior", "external")));
    var compiler = new AgentCompiler(source);

    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    var generated = compiler.getSourceCode();
    assertTrue(generated.contains("runDynamicVerb("), generated);
    assertTrue(generated.contains("invokeDynamicValue("), generated);
    assertTrue(generated.contains("awaitDynamicCalls("), generated);
    assertTrue(generated.contains("EventType.RETURN, EventType.FIRE"), generated);
    assertGeneratedJavaCompiles(generated);
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

  @Test
  void compilerEmitsJavaThatCompilesForExpressionsAndLocalState() {
    var assignment =
        assignment(
            "answer", KActorsStatement.Assignment.Scope.FRAME, expression("21 * 2"));
    var compiler =
        new AgentCompiler(behavior(action("main", assignment, returned(identifier("answer")))));

    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    assertTrue(compiler.getSourceCode().contains("Expression expression_0"));
    assertTrue(compiler.getSourceCode().contains("compileExpression(\"21 * 2\")"));
    assertGeneratedJavaCompiles(compiler.getSourceCode());
  }

  @Test
  void compilerEmitsNestedTernaryValuesAsJavaConditionalExpressions() {
    var selected =
        ternary(
            expression("firstCondition"),
            number(1),
            ternary(expression("secondCondition"), number(2), number(3)));
    var compiler =
        new AgentCompiler(
            behavior(
                action(
                    "main",
                    assignment("selected", KActorsStatement.Assignment.Scope.FRAME, selected),
                    returned(identifier("selected")))));

    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    var generated = compiler.getSourceCode();
    assertTrue(generated.contains("truthy(evaluateExpression(this.expression_0"), generated);
    assertTrue(generated.contains("truthy(evaluateExpression(this.expression_1"), generated);
    assertTrue(generated.contains("? 1 : (truthy("), generated);
    assertGeneratedJavaCompiles(generated);
  }

  @Test
  void compilerEmitsScopedHandlersForReactiveVerbMatches() {
    var match = new KActorsStatementImpl.VerbImpl.MatchActionImpl();
    match.setVariables(List.of("value"));
    match.setActionOnMatch(fired(identifier("value")));
    var stream = verb("external", "stream");
    stream.setActions(List.of(match));
    var source = behavior(action("main", stream));
    source.setImports(List.of(imported("component.behavior", "external")));
    var compiler =
        new AgentCompiler(
            source,
            null,
            new ResolvingValidator(),
            new AgentCompiler.Resolver() {});

    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    assertTrue(compiler.getSourceCode().contains("runEmitter("));
    assertTrue(compiler.getSourceCode().contains("bindMatch("));
    assertTrue(compiler.getSourceCode().contains("EventType.FIRE"));
    assertGeneratedJavaCompiles(compiler.getSourceCode());
  }

  @Test
  void compilerWaitsForEveryReactiveCallInAGroupBeforeThenStatement() {
    var first = verb("external", "first");
    first.setActions(List.of(match(verb("external", "nested"))));
    var second = verb("external", "second");
    second.setActions(
        List.of(match(assignment("secondValue", KActorsStatement.Assignment.Scope.FRAME, number(2)))));
    var group = new KActorsStatementImpl.GroupImpl();
    group.setStatements(List.of(first, second));
    var after = verb("external", "after");
    after.setSequential(true);
    var source = behavior(action("main", group, after));
    source.setImports(List.of(imported("component.behavior", "external")));
    var validator =
        new KActorsVisitor.LenientValidator() {
          @Override
          public Verb.Type classifyActionCall(
              KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
            return "after".equals(verb.getMessage()) ? Verb.Type.FUNCTION : Verb.Type.SUPPLIER;
          }
        };
    var compiler = new AgentCompiler(source, null, validator, new AgentCompiler.Resolver() {});

    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    String generated = compiler.getSourceCode();
    var barrier =
        Pattern.compile("awaitReactions\\(reaction_\\d+, reaction_\\d+\\);").matcher(generated);
    assertTrue(barrier.find(), generated);
    assertTrue(barrier.start() < generated.indexOf("\"after\""), generated);
    assertFalse(generated.contains("TODO honor `then`"));
    assertGeneratedJavaCompiles(generated);
  }

  @Test
  void compilerRecursivelyGeneratesResolvedBehaviorImports() {
    var dependency = behavior(action("helper", returned(number(7))));
    dependency.setUrn("test.dependency");
    var source = behavior(action("main", returned(number(1))));
    source.setImports(List.of(imported("test.dependency", "dependency")));
    var compiler =
        new AgentCompiler(
            source,
            null,
            new KActorsVisitor.LenientValidator(),
            new AgentCompiler.Resolver() {
              @Override
              public KActorsBehavior resolveBehavior(String urn, org.integratedmodelling.klab.api.scope.UserScope scope) {
                return "test.dependency".equals(urn) ? dependency : null;
              }
            });

    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    assertEquals(Set.of("test.behavior", "test.dependency"), compiler.getGeneratedSources().keySet());
    assertGeneratedJavaCompiles(compiler.getGeneratedSources().get("test.dependency"));
  }

  private static void assertGeneratedJavaCompiles(String source) {
    var compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "tests require a JDK");
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var classMatcher = Pattern.compile("public final class ([A-Za-z0-9_$]+)").matcher(source);
    assertTrue(classMatcher.find(), source);
    var unit =
        new SimpleJavaFileObject(
            URI.create(
                "string:///org/integratedmodelling/klab/runtime/kactors/generated/"
                    + classMatcher.group(1)
                    + ".java"),
            JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
          }
        };
    var classPath = compilerClassPath();
    var standard = compiler.getStandardFileManager(diagnostics, null, null);
    try {
      standard.setLocationFromPaths(StandardLocation.CLASS_PATH, classPath);
    } catch (IOException e) {
      fail(e);
    }
    var fileManager = new DirectoryClasspathFileManager(standard, classPath);
    var task =
        compiler.getTask(
            null,
            fileManager,
            diagnostics,
            List.of("-proc:none"),
            null,
            List.of(unit));
    assertTrue(task.call(), () -> diagnostics.getDiagnostics().toString() + "\n" + source);
  }

  @Test
  void compilerSelectsSpecializedRuntimeBasesByBehaviorType() {
    assertSpecializedBase(KActorsBehavior.Type.SCRIPT, ScriptBase.class, "extends ScriptBase");
    assertSpecializedBase(
        KActorsBehavior.Type.UNITTEST, TestCaseBase.class, "extends TestCaseBase");
    assertSpecializedBase(
        KActorsBehavior.Type.APP, ApplicationBase.class, "extends ApplicationBase");
  }

  private static void assertSpecializedBase(
      KActorsBehavior.Type type,
      Class<? extends RuntimeAgentBase> expectedBase,
      String sourceDeclaration) {
    var specialized = behavior(action("main", returned(number(0))));
    specialized.setBehaviorType(type);
    var analyzer = new BehaviorAnalyzer(specialized);

    assertTrue(analyzer.analyze(), messages(analyzer));
    assertEquals(expectedBase, analyzer.getAgentClass());

    var compiler = new AgentCompiler(specialized);
    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    assertTrue(compiler.getSourceCode().contains(sourceDeclaration), compiler.getSourceCode());
    assertGeneratedJavaCompiles(compiler.getSourceCode());
  }

  private static List<Path> compilerClassPath() {
    var entries = new LinkedHashSet<Path>();
    String configured = System.getProperty("java.class.path");
    if (configured != null && !configured.isBlank()) {
      for (var entry : configured.split(Pattern.quote(File.pathSeparator))) {
        if (!entry.isBlank()) {
          entries.add(Path.of(entry).toAbsolutePath().normalize());
        }
      }
    }
    for (var type : List.of(RuntimeAgentBase.class, KActorsBehavior.class)) {
      try {
        var source = type.getProtectionDomain().getCodeSource();
        if (source != null) {
          entries.add(Path.of(source.getLocation().toURI()).toAbsolutePath().normalize());
        }
      } catch (Exception ignored) {
        // The ordinary test classpath remains usable when a code source is unavailable.
      }
    }
    return List.copyOf(entries);
  }

  private static final class DirectoryClassFile extends SimpleJavaFileObject {

    private final Path path;
    private final String binaryName;

    private DirectoryClassFile(Path path, String binaryName) {
      super(path.toUri(), Kind.CLASS);
      this.path = path;
      this.binaryName = binaryName;
    }

    @Override
    public InputStream openInputStream() throws IOException {
      return Files.newInputStream(path);
    }
  }

  private static final class DirectoryClasspathFileManager
      extends ForwardingJavaFileManager<StandardJavaFileManager> {

    private final List<Path> directories;

    private DirectoryClasspathFileManager(
        StandardJavaFileManager delegate, List<Path> classPath) {
      super(delegate);
      this.directories = classPath.stream().filter(Files::isDirectory).toList();
    }

    @Override
    public Iterable<JavaFileObject> list(
        JavaFileManager.Location location,
        String packageName,
        Set<JavaFileObject.Kind> kinds,
        boolean recurse)
        throws IOException {
      var files = new ArrayList<JavaFileObject>();
      super.list(location, packageName, kinds, recurse).forEach(files::add);
      if (location == StandardLocation.CLASS_PATH && kinds.contains(JavaFileObject.Kind.CLASS)) {
        var listedUris =
            files.stream().map(JavaFileObject::toUri).collect(java.util.stream.Collectors.toSet());
        String packagePath = packageName.replace('.', File.separatorChar);
        for (var root : directories) {
          var directory = root.resolve(packagePath);
          if (!Files.isDirectory(directory)) {
            continue;
          }
          try (var paths = recurse ? Files.walk(directory) : Files.list(directory)) {
            paths
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                .map(
                    path -> {
                      String relative = root.relativize(path).toString();
                      String binaryName =
                          relative
                              .substring(0, relative.length() - ".class".length())
                              .replace(File.separatorChar, '.');
                      return (JavaFileObject) new DirectoryClassFile(path, binaryName);
                    })
                .filter(file -> listedUris.add(file.toUri()))
                .forEach(files::add);
          }
        }
      }
      return files;
    }

    @Override
    public String inferBinaryName(JavaFileManager.Location location, JavaFileObject file) {
      return file instanceof DirectoryClassFile existing
          ? existing.binaryName
          : super.inferBinaryName(location, file);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(
        JavaFileManager.Location location,
        String className,
        JavaFileObject.Kind kind,
        javax.tools.FileObject sibling) {
      return new SimpleJavaFileObject(
          URI.create("memory:///" + className.replace('.', '/') + kind.extension), kind) {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        @Override
        public OutputStream openOutputStream() {
          return bytes;
        }
      };
    }
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

  private static KActorsStatementImpl.AssignmentImpl assignment(
      String name,
      KActorsStatement.Assignment.Scope scope,
      KActorsStatementImpl.VerbImpl function) {
    var assignment = new KActorsStatementImpl.AssignmentImpl();
    assignment.setVariable(name);
    assignment.setAssignmentScope(scope);
    assignment.setFunction(function);
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

  private static KActorsStatementImpl.VerbImpl.MatchActionImpl match(
      KActorsStatement actionOnMatch) {
    var match = new KActorsStatementImpl.VerbImpl.MatchActionImpl();
    match.setActionOnMatch(actionOnMatch);
    return match;
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

  private static KActorsValueImpl expression(String expression) {
    var value = new KActorsValueImpl();
    value.setType(ValueType.EXPRESSION);
    value.setStatedValue(expression);
    return value;
  }

  private static KActorsValueImpl ternary(
      KActorsValueImpl condition, KActorsValueImpl trueCase, KActorsValueImpl falseCase) {
    var ternary = new TernaryImpl();
    ternary.setCondition(condition);
    ternary.setTrueCase(trueCase);
    ternary.setFalseCase(falseCase);
    var value = new KActorsValueImpl();
    value.setType(ValueType.TERNARY_EXPRESSION);
    value.setStatedValue(ternary);
    return value;
  }

  private static String messages(BehaviorAnalyzer analyzer) {
    return analyzer.getNotifications().stream()
        .map(Notification::getMessage)
        .reduce("", (left, right) -> left + "\n" + right);
  }
}
