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
import java.util.Map;
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
import org.integratedmodelling.common.lang.ServiceInfoImpl;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction.Argument;
import org.integratedmodelling.klab.api.lang.kactors.KActorsCodeStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsArgumentsImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsStatementImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsValueImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.runtime.kactors.ApplicationBase;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.integratedmodelling.klab.runtime.kactors.ScriptBase;
import org.integratedmodelling.klab.runtime.kactors.TestCaseBase;
import org.junit.jupiter.api.Test;

class BehaviorAnalyzerTest {

  @Test
  void actionParametersAreVisibleThroughoutTheActionBody() {
    var echo = action("echo", returned(identifier("payload")));
    echo.setArgumentNames(List.of("payload"));
    var analyzer = new BehaviorAnalyzer(behavior(echo));

    assertTrue(analyzer.analyze(), messages(analyzer));
    assertFalse(
        analyzer.getNotifications().stream()
            .anyMatch(
                notification ->
                    notification.getMessage().equals("Unknown identifier: payload")));
    assertTrue(
        analyzer.getActions().get("echo").parameters().stream()
            .anyMatch(parameter -> parameter.name().equals("payload")));
  }

  @Test
  void compilerRegistersNamedAndUnnamedHandleAnnotations() {
    var named = action("namedHandler");
    named.setArgumentNames(List.of("payload", "sender"));
    var namedParameters = new java.util.LinkedHashMap<String, Object>();
    namedParameters.put("class", Constant.create("NAMED"));
    named.setAnnotations(List.of(Annotation.of("handle", namedParameters)));
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
  void invalidHandleAnnotationWarningUsesTheAnnotatedActionLocation() {
    var invalid = action("invalidHandler");
    invalid.setOffsetInDocument(47);
    invalid.setLength(31);
    invalid.setAnnotations(List.of(Annotation.of("handle")));
    var compiler = new AgentCompiler(behavior(invalid));

    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    var warning =
        compiler.getNotifications().stream()
            .filter(
                notification ->
                    notification
                        .getMessage()
                        .contains("@handle annotation requires a CONSTANT"))
            .findFirst()
            .orElseThrow();

    assertNotNull(warning.getLexicalContext());
    assertEquals(47, warning.getLexicalContext().getOffsetInDocument());
    assertEquals(31, warning.getLexicalContext().getLength());
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
    child.setInheritedBehaviors((List.of(new KActorsBehaviorImpl.ImportImpl(inherited.getUrn()))));
    var compiler =
        new AgentCompiler(
            child,
            null,
            new KActorsVisitor.LenientValidator(),
            new AgentCompiler.Resolver() {
              @Override
              public KActorsBehavior resolveBehavior(
                  String urn, org.integratedmodelling.klab.api.scope.UserScope scope) {
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
  void inheritedHandleOverridesRequireExplicitAcknowledgement() {
    var inheritedHandler = action("inheritedHandler");
    var inheritedHandle = Annotation.of("handle");
    inheritedHandle.putUnnamed(Constant.create("STATE_CHANGED"));
    inheritedHandler.setAnnotations(List.of(inheritedHandle));
    var inherited = behavior(inheritedHandler);
    inherited.setUrn("traits.state.handlers");
    inherited.setBehaviorType(KActorsBehavior.Type.TRAIT);
    var intermediary = behavior();
    intermediary.setUrn("traits.state.intermediary");
    intermediary.setBehaviorType(KActorsBehavior.Type.TRAIT);
    intermediary.setInheritedBehaviors(
        List.of(new KActorsBehaviorImpl.ImportImpl(inherited.getUrn())));

    var resolver =
        new AgentCompiler.Resolver() {
          @Override
          public KActorsBehavior resolveBehavior(
              String urn, org.integratedmodelling.klab.api.scope.UserScope scope) {
            if (inherited.getUrn().equals(urn)) {
              return inherited;
            }
            return intermediary.getUrn().equals(urn) ? intermediary : null;
          }
        };
    var environment = AgentCompiler.runtimeEnvironment(resolver, null);

    var localHandler = action("localHandler");
    var localHandle = Annotation.of("handle");
    localHandle.putUnnamed(Constant.create("STATE_CHANGED"));
    localHandler.setAnnotations(List.of(localHandle));
    var child = behavior(localHandler);
    child.setInheritedBehaviors(List.of(new KActorsBehaviorImpl.ImportImpl(intermediary.getUrn())));
    var analyzer = new BehaviorAnalyzer(child, environment.validator());

    assertTrue(analyzer.analyze(), messages(analyzer));
    assertTrue(
        analyzer.getNotifications().stream()
            .anyMatch(
                notification ->
                    notification
                        .getMessage()
                        .contains(
                            "overrides the inherited @handle(STATE_CHANGED) contract; add @override")));

    localHandler.setAnnotations(List.of(localHandle, Annotation.of("override")));
    var acknowledged = new BehaviorAnalyzer(child, environment.validator());

    assertTrue(acknowledged.analyze(), messages(acknowledged));
    assertFalse(
        acknowledged.getNotifications().stream()
            .anyMatch(
                notification ->
                    notification.getMessage().contains("overrides the inherited @handle")));
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
  void adaptActionsMustBeUniqueUnaryFunctionsOrSuppliers() {
    var function = action("convert", returned(identifier("source")));
    function.setArgumentNames(List.of("source"));
    function.setAnnotations(List.of(Annotation.of("adapt")));
    var functionAnalyzer = new BehaviorAnalyzer(behavior(function));

    assertTrue(functionAnalyzer.analyze(), messages(functionAnalyzer));
    assertEquals(Verb.Type.FUNCTION, function.getActionType());

    var match = new KActorsStatementImpl.VerbImpl.MatchActionImpl();
    match.setActionOnMatch(returned(identifier("source")));
    var call = verb("external", "later");
    call.setActions(List.of(match));
    var supplier = action("convert_later", call);
    supplier.setArgumentNames(List.of("source"));
    supplier.setAnnotations(List.of(Annotation.of("adapt")));
    var supplierBehavior = behavior(supplier);
    supplierBehavior.setImports(List.of(imported("component.behavior", "external")));
    var supplierAnalyzer =
        new BehaviorAnalyzer(
            supplierBehavior,
            new KActorsVisitor.LenientValidator() {
              @Override
              public Verb.Type classifyActionCall(
                  KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
                return Verb.Type.SUPPLIER;
              }
            });

    assertTrue(supplierAnalyzer.analyze(), messages(supplierAnalyzer));
    assertEquals(Verb.Type.SUPPLIER, supplier.getActionType());

    var emitter = action("invalid", fired(identifier("source")));
    emitter.setArgumentNames(List.of("source"));
    emitter.setAnnotations(List.of(Annotation.of("adapt")));
    var emitterAnalyzer = new BehaviorAnalyzer(behavior(emitter));

    var emitterValid = emitterAnalyzer.analyze();
    assertEquals(Verb.Type.EMITTER, emitter.getActionType());
    assertFalse(emitterValid);
    assertTrue(messages(emitterAnalyzer).contains("function or supplier"));

    var first = action("first", returned(identifier("source")));
    first.setArgumentNames(List.of("source"));
    first.setAnnotations(List.of(Annotation.of("adapt")));
    var second = action("second", returned(identifier("source")));
    second.setArgumentNames(List.of("source"));
    second.setAnnotations(List.of(Annotation.of("adapt")));
    var duplicateAnalyzer = new BehaviorAnalyzer(behavior(first, second));

    assertFalse(duplicateAnalyzer.analyze());
    assertTrue(messages(duplicateAnalyzer).contains("only one @adapt action"));
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
    behavior.setInheritedBehaviors((List.of(new KActorsBehaviorImpl.ImportImpl("traits.base"))));
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
  void runtimeValidatorEnforcesBehaviorInheritanceTypes() {
    for (var childType : KActorsBehavior.Type.values()) {
      assertTrue(
          childType.canInherit(KActorsBehavior.Type.TRAIT), childType + " must inherit traits");
      assertTrue(childType.canInherit(childType), childType + " must inherit its own type");
    }

    KActorsBehavior.Type[][] allowed = {
      {KActorsBehavior.Type.BEHAVIOR, KActorsBehavior.Type.TRAIT},
      {KActorsBehavior.Type.APP, KActorsBehavior.Type.APP},
      {KActorsBehavior.Type.USER, KActorsBehavior.Type.BEHAVIOR},
      {KActorsBehavior.Type.TASK, KActorsBehavior.Type.BEHAVIOR}
    };
    for (var inheritance : allowed) {
      var compiler = inheritanceCompiler(inheritance[0], inheritance[1]);
      assertTrue(
          compiler.compile(),
          () ->
              inheritance[0]
                  + " should inherit "
                  + inheritance[1]
                  + ": "
                  + compiler.getNotifications());
    }

    KActorsBehavior.Type[][] rejected = {
      {KActorsBehavior.Type.APP, KActorsBehavior.Type.BEHAVIOR},
      {KActorsBehavior.Type.BEHAVIOR, KActorsBehavior.Type.APP},
      {KActorsBehavior.Type.USER, KActorsBehavior.Type.TASK},
      {KActorsBehavior.Type.TASK, KActorsBehavior.Type.USER}
    };
    for (var inheritance : rejected) {
      var compiler = inheritanceCompiler(inheritance[0], inheritance[1]);
      assertFalse(compiler.compile(), inheritance[0] + " must not inherit " + inheritance[1]);
      assertTrue(
          compiler.getNotifications().toString().contains("cannot inherit"),
          () -> compiler.getNotifications().toString());
    }
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
    assertTrue(
        compiler.getSourceCode().contains("\"workers.specialized\""), compiler.getSourceCode());
    assertGeneratedJavaCompiles(compiler.getSourceCode());
  }

  @Test
  void adaptationsAreValidatedAndCompiledAtEveryControlAndResultBoundary() {
    var adaptedReturn = returned(number(1));
    adaptedReturn.setAdaptedBehaviorUrn("adapted.result");
    var adaptedFire = fired(number(2));
    adaptedFire.setAdaptedBehaviorUrn("adapted.event");

    var conditional = new KActorsStatementImpl.IfImpl();
    conditional.setCondition(number(1));
    conditional.setAdaptedBehaviorUrn("adapted.condition");
    conditional.setThenBody(assignment("ifValue", KActorsStatement.Assignment.Scope.FRAME, number(1)));
    conditional.setElseIfs(
        List.of(
            Pair.of(
                Triple.of(number(2), null, "adapted.else.condition"),
                assignment(
                    "elseValue", KActorsStatement.Assignment.Scope.FRAME, number(2)))));

    var whileLoop = new KActorsStatementImpl.WhileImpl();
    whileLoop.setCondition(number(1));
    whileLoop.setAdaptedBehaviorUrn("adapted.while.condition");
    whileLoop.setBody(new KActorsStatementImpl.BreakImpl());
    var doLoop = new KActorsStatementImpl.DoImpl();
    doLoop.setCondition(number(1));
    doLoop.setAdaptedBehaviorUrn("adapted.do.condition");
    doLoop.setBody(new KActorsStatementImpl.BreakImpl());
    var forLoop = new KActorsStatementImpl.ForImpl();
    forLoop.setVariable("item");
    forLoop.setIterable(list(number(1), number(2)));
    forLoop.setAdaptedBehaviorUrn("adapted.iterable");
    forLoop.setBody(
        assignment("copy", KActorsStatement.Assignment.Scope.FRAME, identifier("item")));

    var genericAdaptations = new ArrayList<String>();
    var booleanAdaptations = new ArrayList<String>();
    var iterableAdaptations = new ArrayList<String>();
    var validator =
        new KActorsVisitor.LenientValidator() {
          @Override
          public List<Notification> validateAdaptation(
              KActorsCodeStatement statement,
              String behaviorUrn,
              KActorsVisitor.VariableInfo sourceVariable,
              KActorsVisitor.KActorsContext context) {
            genericAdaptations.add(behaviorUrn);
            return List.of();
          }

          @Override
          public List<Notification> validateBooleanAdaptation(
              KActorsCodeStatement statement,
              String behaviorUrn,
              KActorsVisitor.VariableInfo sourceVariable,
              KActorsVisitor.KActorsContext context) {
            booleanAdaptations.add(behaviorUrn);
            return List.of();
          }

          @Override
          public List<Notification> validateIterableAdaptation(
              KActorsCodeStatement statement,
              String behaviorUrn,
              KActorsVisitor.VariableInfo sourceVariable,
              KActorsVisitor.KActorsContext context) {
            iterableAdaptations.add(behaviorUrn);
            return List.of();
          }
        };
    var source =
        behavior(
            action(
                "main",
                conditional,
                whileLoop,
                doLoop,
                forLoop,
                adaptedFire,
                adaptedReturn));
    var analyzer = new BehaviorAnalyzer(source, validator);

    assertTrue(analyzer.analyze(), messages(analyzer));
    assertEquals(7, genericAdaptations.size());
    assertEquals(
        Set.of(
            "adapted.condition",
            "adapted.else.condition",
            "adapted.while.condition",
            "adapted.do.condition"),
        Set.copyOf(booleanAdaptations));
    assertEquals(List.of("adapted.iterable"), iterableAdaptations);

    var compiler = new AgentCompiler(source, null, validator, null);
    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    String generated = compiler.getSourceCode();
    assertTrue(generated.contains("adaptToBehavior("), generated);
    assertTrue(generated.contains("adaptToBoolean("), generated);
    assertTrue(generated.contains("adaptToIterable("), generated);
    assertGeneratedJavaCompiles(generated);
  }

  @Test
  void yieldIsRestrictedToSwitchAndFunctionalSwitchesRequireAYield() {
    var illegalYield = yielded(number(1));
    var outside = new BehaviorAnalyzer(behavior(action("main", illegalYield)));
    assertFalse(outside.analyze());
    assertTrue(messages(outside).contains("yield can only be used inside a switch"));

    var noYieldSwitch =
        switched(
            number(1),
            switchCase(number(1), assignment("value", KActorsStatement.Assignment.Scope.FRAME, number(2))));
    var value =
        new KActorsStatementImpl.AssignmentImpl();
    value.setVariable("result");
    value.setAssignmentScope(KActorsStatement.Assignment.Scope.FRAME);
    value.setSwitch(noYieldSwitch);
    var functional = new BehaviorAnalyzer(behavior(action("main", value)));
    assertFalse(functional.analyze());
    assertTrue(messages(functional).contains("A switch used as a value must have at least one yield"));
  }

  @Test
  void compilerEmitsSynchronousSwitchWithYieldAndNullForNonYieldingBranches() {
    var expression =
        switched(
            number(1),
            switchCase(number(1), yielded(number(42))),
            switchCase(
                number(2),
                assignment("sideEffect", KActorsStatement.Assignment.Scope.FRAME, number(2))));
    var assignment = new KActorsStatementImpl.AssignmentImpl();
    assignment.setVariable("result");
    assignment.setAssignmentScope(KActorsStatement.Assignment.Scope.FRAME);
    assignment.setSwitch(expression);
    var compiler =
        new AgentCompiler(behavior(action("main", assignment, returned(identifier("result")))));

    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    String generated = compiler.getSourceCode();
    assertTrue(generated.contains("throw new RuntimeAgentBase.SwitchYield(42)"), generated);
    assertTrue(generated.contains("catch (RuntimeAgentBase.SwitchYield yielded)"), generated);
    assertTrue(generated.contains("Object switchResult_"), generated);
    assertGeneratedJavaCompiles(generated);
  }

  @Test
  void importedBehaviorAliasesExposeOnlyStaticActionsAndNewProducesAnInstance() {
    var utility = action("describe", returned(number(1)));
    utility.setStatic(true);
    utility.setActionType(Verb.Type.FUNCTION);
    var instanceAction = action("work", returned(number(2)));
    instanceAction.setActionType(Verb.Type.FUNCTION);
    var importedBehavior = behavior(utility, instanceAction);
    importedBehavior.setUrn("tools.worker");
    var resolver =
        new AgentCompiler.Resolver() {
          @Override
          public KActorsBehavior resolveBehavior(
              String urn, org.integratedmodelling.klab.api.scope.UserScope scope) {
            return "tools.worker".equals(urn) ? importedBehavior : null;
          }
        };
    var environment = AgentCompiler.runtimeEnvironment(resolver, null);

    var invalid = behavior(action("main", verb("tools", "describe"), verb("tools", "work")));
    invalid.setImports(List.of(imported("tools.worker", "tools")));
    var invalidAnalysis = new BehaviorAnalyzer(invalid, environment.validator());

    assertFalse(invalidAnalysis.analyze());
    assertEquals(Boolean.TRUE, invalidAnalysis.getCalls().get(0).staticAction());
    assertEquals(Boolean.FALSE, invalidAnalysis.getCalls().get(1).staticAction());
    assertTrue(messages(invalidAnalysis).contains("must be invoked on an actor instance"));

    var construct = verb("tools", "new");
    var worker =
        assignment("worker", KActorsStatement.Assignment.Scope.FRAME, construct);
    var valid = behavior(action("main", worker, verb("worker", "work")));
    valid.setImports(List.of(imported("tools.worker", "tools")));
    var validAnalysis = new BehaviorAnalyzer(valid, environment.validator());

    assertTrue(validAnalysis.analyze(), messages(validAnalysis));
    assertEquals(Boolean.TRUE, validAnalysis.getCalls().get(0).staticAction());
    assertEquals(Boolean.FALSE, validAnalysis.getCalls().get(1).staticAction());

    var compiler = new AgentCompiler(valid, null, environment.validator(), resolver);
    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    assertTrue(compiler.getSourceCode().contains("resolveImportedBehavior("));
    assertTrue(compiler.getGeneratedSources().containsKey("tools.worker"));
  }

  @Test
  void javaActorDescriptorsEnforceTheSameAliasStaticityRule() throws Exception {
    var descriptor = new Extensions.ActorDescriptor();
    descriptor.urn = "java.worker";
    var utility = javaVerb("utility", true, JavaStaticityActor.class.getMethod("utility"));
    var work = javaVerb("work", false, JavaStaticityActor.class.getMethod("work"));
    descriptor.verbs.add(utility.getKey());
    descriptor.verbs.add(work.getKey());
    var resolved =
        new AgentCompiler.ResolvedActor(
            descriptor,
            Map.of("utility", utility.getValue(), "work", work.getValue()));
    var resolver =
        new AgentCompiler.Resolver() {
          @Override
          public AgentCompiler.ResolvedActor resolveActor(
              String urn, org.integratedmodelling.klab.api.scope.UserScope scope) {
            return "java.worker".equals(urn) ? resolved : null;
          }
        };
    var environment = AgentCompiler.runtimeEnvironment(resolver, null);
    var source =
        behavior(action("main", verb("java", "utility"), verb("java", "work")));
    source.setImports(List.of(imported("java.worker", "java")));
    var analyzer = new BehaviorAnalyzer(source, environment.validator());

    assertFalse(analyzer.analyze());
    assertEquals(Boolean.TRUE, analyzer.getCalls().get(0).staticAction());
    assertEquals(Boolean.FALSE, analyzer.getCalls().get(1).staticAction());
    assertTrue(messages(analyzer).contains("must be invoked on an actor instance"));
  }

  @Test
  void javaProducedAgentBehaviorTypesAssignedVariables() throws Exception {
    var factoryDescriptor = new Extensions.ActorDescriptor();
    factoryDescriptor.urn = "java.factory";
    var make = javaVerb("make", true, JavaStaticityActor.class.getMethod("make"));
    factoryDescriptor.verbs.add(make.getKey());
    var factory =
        new AgentCompiler.ResolvedActor(factoryDescriptor, Map.of("make", make.getValue()));

    var productDescriptor = new Extensions.ActorDescriptor();
    productDescriptor.urn = "java.product";
    var work = javaVerb("work", false, JavaStaticityActor.class.getMethod("work"));
    productDescriptor.verbs.add(work.getKey());
    var product =
        new AgentCompiler.ResolvedActor(productDescriptor, Map.of("work", work.getValue()));
    var resolver =
        new AgentCompiler.Resolver() {
          @Override
          public AgentCompiler.ResolvedActor resolveActor(
              String urn, org.integratedmodelling.klab.api.scope.UserScope scope) {
            return switch (urn) {
              case "java.factory" -> factory;
              case "java.product" -> product;
              default -> null;
            };
          }
        };
    var makeCall = verb("factory", "make");
    var workCall = verb("worker", "work");
    var source =
        behavior(
            action(
                "main",
                assignment("worker", KActorsStatement.Assignment.Scope.FRAME, makeCall),
                workCall));
    source.setImports(List.of(imported("java.factory", "factory")));
    var analyzer =
        new BehaviorAnalyzer(
            source, AgentCompiler.runtimeEnvironment(resolver, null).validator());

    assertTrue(analyzer.analyze(), messages(analyzer));
    assertEquals("java.product", analyzer.getCalls().getFirst().producedAgentUrn());
    assertEquals(
        "java.product",
        analyzer.getCalls().get(1).knownVariables().get("worker").agentUrn());
    assertEquals(Boolean.FALSE, analyzer.getCalls().get(1).staticAction());
  }

  @Test
  void kActorsReturnAnnotationTypesAssignmentLoopAndMatchCaptures() {
    var productAction = action("work");
    productAction.setActionType(Verb.Type.FUNCTION);
    var product = behavior(productAction);
    product.setUrn("workers.product");

    var agents = action("agents");
    agents.setStatic(true);
    agents.setActionType(Verb.Type.FUNCTION);
    agents.setAnnotations(
        List.of(Annotation.of("return", "urn", "workers.product")));
    var stream = action("stream");
    stream.setStatic(true);
    stream.setActionType(Verb.Type.EMITTER);
    var streamReturn = Annotation.of("return");
    streamReturn.putUnnamed("workers.product");
    stream.setAnnotations(List.of(streamReturn));
    assertEquals("workers.product", KActorsVisitor.returnedBehaviorUrn(stream));
    var provider = behavior(agents, stream);
    provider.setUrn("workers.provider");

    var localFactory = action("make");
    localFactory.setAnnotations(
        List.of(Annotation.of("return", "urn", "workers.product")));
    var assignedCall = verb("assigned", "work");

    var loopCall = verb("loopWorker", "work");
    var loop = new KActorsStatementImpl.ForImpl();
    loop.setVariable("loopWorker");
    loop.setFunction(verb("provider", "agents"));
    loop.setBody(loopCall);

    var matchCall = verb("matchedWorker", "work");
    var match = match(matchCall);
    match.setVariables(List.of("matchedWorker"));
    var streamCall = verb("provider", "stream");
    streamCall.setActions(List.of(match));

    var source =
        behavior(
            localFactory,
            action(
                "main",
                assignment(
                    "assigned",
                    KActorsStatement.Assignment.Scope.FRAME,
                    verb("self", "make")),
                assignedCall,
                loop,
                streamCall));
    source.setImports(List.of(imported("workers.provider", "provider")));
    var resolver =
        new AgentCompiler.Resolver() {
          @Override
          public KActorsBehavior resolveBehavior(
              String urn, org.integratedmodelling.klab.api.scope.UserScope scope) {
            return switch (urn) {
              case "workers.product" -> product;
              case "workers.provider" -> provider;
              default -> null;
            };
          }
        };
    var analyzer =
        new BehaviorAnalyzer(
            source, AgentCompiler.runtimeEnvironment(resolver, null).validator());

    assertTrue(analyzer.analyze(), messages(analyzer));
    assertEquals(
        "workers.product",
        analyzer.getCalls().stream()
            .filter(call -> call.statement() == streamCall)
            .findFirst()
            .orElseThrow()
            .producedAgentUrn());
    assertEquals(
        "workers.product",
        analyzer.getCalls().stream()
            .filter(call -> call.statement() == assignedCall)
            .findFirst()
            .orElseThrow()
            .knownVariables()
            .get("assigned")
            .agentUrn());
    assertEquals(
        "workers.product",
        analyzer.getCalls().stream()
            .filter(call -> call.statement() == loopCall)
            .findFirst()
            .orElseThrow()
            .knownVariables()
            .get("loopWorker")
            .agentUrn());
    assertEquals(
        "workers.product",
        analyzer.getCalls().stream()
            .filter(call -> call.statement() == matchCall)
            .findFirst()
            .orElseThrow()
            .knownVariables()
            .get("matchedWorker")
            .agentUrn());
  }

  @Test
  void javaActorParameterMismatchCanBeNegotiated() throws Exception {
    var descriptor = new Extensions.ActorDescriptor();
    descriptor.urn = "java.worker";
    var duration =
        javaVerb(
            "duration",
            true,
            JavaStaticityActor.class.getMethod(
                "duration", double.class, java.util.concurrent.TimeUnit.class));
    descriptor.verbs.add(duration.getKey());
    var resolved =
        new AgentCompiler.ResolvedActor(descriptor, Map.of("duration", duration.getValue()));
    var call = verb("java", "duration");
    call.getArguments().putUnnamed(number(5));
    var source = behavior(action("main", call));
    source.setImports(List.of(imported("java.worker", "java")));

    var rejectingResolver =
        new AgentCompiler.Resolver() {
          @Override
          public AgentCompiler.ResolvedActor resolveActor(
              String urn, org.integratedmodelling.klab.api.scope.UserScope scope) {
            return "java.worker".equals(urn) ? resolved : null;
          }
        };
    var rejected =
        new BehaviorAnalyzer(
            source, AgentCompiler.runtimeEnvironment(rejectingResolver, null).validator());

    assertFalse(rejected.analyze());
    assertTrue(messages(rejected).contains("Cannot match parameters for Java verb"));
    assertTrue(messages(rejected).contains("parameter negotiation did not produce"));

    var negotiatingResolver =
        new AgentCompiler.Resolver() {
          @Override
          public AgentCompiler.ResolvedActor resolveActor(
              String urn, org.integratedmodelling.klab.api.scope.UserScope scope) {
            return "java.worker".equals(urn) ? resolved : null;
          }

          @Override
          public List<Object> negotiateParameterMatch(
              List<Class<?>> unmatchedParameterTypes, List<?> suppliedParameters) {
            assertEquals(
                List.of(double.class, java.util.concurrent.TimeUnit.class),
                unmatchedParameterTypes);
            assertEquals(1, suppliedParameters.size());
            return List.of(5.0, java.util.concurrent.TimeUnit.SECONDS);
          }
        };
    var accepted =
        new BehaviorAnalyzer(
            source, AgentCompiler.runtimeEnvironment(negotiatingResolver, null).validator());

    assertTrue(accepted.analyze(), messages(accepted));
  }

  @Test
  void javaVerbArgumentAnnotationsValidateNamedTypesAndAgentBehaviors() throws Exception {
    var descriptor = new Extensions.ActorDescriptor();
    descriptor.urn = "java.worker";
    var make =
        javaVerb(
            "make", true, JavaStaticityActor.class.getMethod("make"));
    var accept =
        javaVerb(
            "accept",
            true,
            JavaStaticityActor.class.getMethod("accept", Object.class, Object.class));
    descriptor.verbs.add(make.getKey());
    descriptor.verbs.add(accept.getKey());
    var resolved =
        new AgentCompiler.ResolvedActor(
            descriptor, Map.of("make", make.getValue(), "accept", accept.getValue()));
    var create = assignment("worker", KActorsStatement.Assignment.Scope.FRAME, verb("java", "make"));
    var call = verb("java", "accept");
    call.getArguments().put("label", number(5));
    call.getArguments().put("agent", identifier("worker"));
    var source = behavior(action("main", create, call));
    source.setImports(List.of(imported("java.worker", "java")));
    var resolver =
        new AgentCompiler.Resolver() {
          @Override
          public AgentCompiler.ResolvedActor resolveActor(
              String urn, org.integratedmodelling.klab.api.scope.UserScope scope) {
            return "java.worker".equals(urn) ? resolved : null;
          }
        };
    var analyzer =
        new BehaviorAnalyzer(source, AgentCompiler.runtimeEnvironment(resolver, null).validator());

    assertFalse(analyzer.analyze());
    assertTrue(messages(analyzer).contains("argument 'label'"));
    assertTrue(messages(analyzer).contains("must be java.lang.String"));
    assertTrue(messages(analyzer).contains("requires an agent implementing java.required"));
    assertTrue(messages(analyzer).contains("java.product was supplied"));

    var dynamicCall = verb("java", "accept");
    dynamicCall.getArguments().put("label", identifier("dynamicLabel"));
    dynamicCall.getArguments().put("agent", identifier("dynamicAgent"));
    var dynamicAction = action("main", dynamicCall);
    dynamicAction.setArgumentNames(List.of("dynamicLabel", "dynamicAgent"));
    var dynamicSource = behavior(dynamicAction);
    dynamicSource.setImports(List.of(imported("java.worker", "java")));
    var dynamicAnalyzer =
        new BehaviorAnalyzer(
            dynamicSource, AgentCompiler.runtimeEnvironment(resolver, null).validator());
    assertTrue(dynamicAnalyzer.analyze(), messages(dynamicAnalyzer));
  }

  @Test
  void kActorsTypeAnnotationsValidateKnownArgumentsAndCompileRuntimeGuards() {
    var type = Annotation.of("type", "class", "boolean");
    var accept = action("accept", returned(identifier("enabled")));
    accept.setArguments(List.of(new Argument("enabled", type)));
    var acceptedCall = verb("self", "accept");
    acceptedCall.getArguments().putUnnamed(bool(true));
    var acceptedSource = behavior(accept, action("main", acceptedCall));
    var acceptedResolver =
        new AgentCompiler.Resolver() {
          @Override
          public KActorsBehavior resolveBehavior(
              String urn, org.integratedmodelling.klab.api.scope.UserScope scope) {
            return acceptedSource.getUrn().equals(urn) ? acceptedSource : null;
          }
        };
    var acceptedEnvironment = AgentCompiler.runtimeEnvironment(acceptedResolver, null);
    var accepted =
        new BehaviorAnalyzer(acceptedSource, acceptedEnvironment.validator());

    assertTrue(accepted.analyze(), messages(accepted));
    var compiler =
        new AgentCompiler(
            acceptedSource,
            null,
            acceptedEnvironment.validator(),
            acceptedEnvironment.resolver());
    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    assertTrue(
        compiler.getSourceCode().contains("validateActionArguments(\"accept\""),
        compiler.getSourceCode());
    assertTrue(compiler.getSourceCode().contains("\"boolean\""), compiler.getSourceCode());
    assertGeneratedJavaCompiles(compiler.getSourceCode());

    var rejectedType = Annotation.of("type", "class", "Boolean");
    var rejectedAccept = action("accept", returned(identifier("enabled")));
    rejectedAccept.setArguments(
        List.of(new Argument("enabled", rejectedType)));
    var rejectedCall = verb("self", "accept");
    rejectedCall.getArguments().putUnnamed(number(1));
    var rejectedSource = behavior(rejectedAccept, action("main", rejectedCall));
    var rejectedResolver =
        new AgentCompiler.Resolver() {
          @Override
          public KActorsBehavior resolveBehavior(
              String urn, org.integratedmodelling.klab.api.scope.UserScope scope) {
            return rejectedSource.getUrn().equals(urn) ? rejectedSource : null;
          }
        };
    var rejected =
        new BehaviorAnalyzer(
            rejectedSource, AgentCompiler.runtimeEnvironment(rejectedResolver, null).validator());

    assertFalse(rejected.analyze());
    assertTrue(messages(rejected).contains("must be Boolean"), messages(rejected));
    assertTrue(messages(rejected).contains("java.lang.Integer"), messages(rejected));
  }

  @Test
  void behaviorAdaptationIsLocalOnlyAndValidationErrorsDoNotTypeTheVariable() {
    var actorAssignment = assignment("worker", KActorsStatement.Assignment.Scope.ACTOR, number(1));
    actorAssignment.setAdaptedBehaviorUrn("workers.specialized");
    var localAssignment = assignment("other", KActorsStatement.Assignment.Scope.FRAME, number(2));
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
        new BehaviorAnalyzer(
            behavior(action("init", actorAssignment), action("main", localAssignment)), validator);

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
    assertTrue(
        messages(analyzer).contains("Emitter calls cannot be used where a value is required"));
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
    assertEquals(Verb.Type.EMITTER, analyzer.getActions().get("main").effectiveExecutionType());
    assertEquals(Verb.Type.EMITTER, analyzer.getAgentExecutionMode());
    assertEquals(BehaviorAnalyzer.Lifecycle.PERSISTENT, analyzer.getLifecycle());
  }

  @Test
  void actorLikeBehaviorsAreClassifiedPersistentDespiteFunctionMain() {
    for (var type :
        List.of(
            KActorsBehavior.Type.BEHAVIOR,
            KActorsBehavior.Type.APP,
            KActorsBehavior.Type.USER,
            KActorsBehavior.Type.COMPONENT)) {
      var source = behavior(action("main", returned(number(0))));
      source.setBehaviorType(type);
      var analyzer = new BehaviorAnalyzer(source);

      assertTrue(analyzer.analyze(), messages(analyzer));
      assertEquals(Verb.Type.EMITTER, analyzer.getAgentExecutionMode(), type.toString());
      assertEquals(BehaviorAnalyzer.Lifecycle.PERSISTENT, analyzer.getLifecycle(), type.toString());
    }
  }

  @Test
  void supplierCallsKeepExecutionFiniteButAsynchronous() {
    var main = action("main", verb("external", "later"));
    var behavior = behavior(main);
    behavior.setBehaviorType(KActorsBehavior.Type.SCRIPT);
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
    assertEquals(Verb.Type.SUPPLIER, analyzer.getActions().get("main").effectiveExecutionType());
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
            return verb == assignedProducer || verb == iterableProducer ? Verb.Type.FUNCTION : null;
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
    assertNull(
        analyzer.getCalls().stream()
            .filter(call -> call.statement() == assignedCall)
            .findFirst()
            .orElseThrow()
            .knownVariables()
            .get("worker")
            .agentUrn(),
        "unannotated producer results must remain dynamically typed");
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
        assignment("answer", KActorsStatement.Assignment.Scope.FRAME, expression("21 * 2"));
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
        new AgentCompiler(source, null, new ResolvingValidator(), new AgentCompiler.Resolver() {});

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
        List.of(
            match(assignment("secondValue", KActorsStatement.Assignment.Scope.FRAME, number(2)))));
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
              public KActorsBehavior resolveBehavior(
                  String urn, org.integratedmodelling.klab.api.scope.UserScope scope) {
                return "test.dependency".equals(urn) ? dependency : null;
              }
            });

    assertTrue(compiler.compile(), compiler.getNotifications().toString());
    assertEquals(
        Set.of("test.behavior", "test.dependency"), compiler.getGeneratedSources().keySet());
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
            null, fileManager, diagnostics, List.of("-proc:none"), null, List.of(unit));
    assertTrue(task.call(), () -> diagnostics.getDiagnostics().toString() + "\n" + source);
  }

  private static AgentCompiler inheritanceCompiler(
      KActorsBehavior.Type childType, KActorsBehavior.Type inheritedType) {
    var inherited = behavior(action("helper", returned(number(1))));
    inherited.setUrn("test.inherited." + inheritedType.name().toLowerCase());
    inherited.setBehaviorType(inheritedType);
    var child = behavior(action("main", returned(number(0))));
    child.setUrn("test.child." + childType.name().toLowerCase());
    child.setBehaviorType(childType);
    child.setInheritedBehaviors((List.of(new KActorsBehaviorImpl.ImportImpl(inherited.getUrn()))));
    var resolver =
        new AgentCompiler.Resolver() {
          @Override
          public KActorsBehavior resolveBehavior(
              String urn, org.integratedmodelling.klab.api.scope.UserScope scope) {
            return inherited.getUrn().equals(urn) ? inherited : null;
          }
        };
    var environment = AgentCompiler.runtimeEnvironment(resolver, null);
    return new AgentCompiler(child, null, environment.validator(), environment.resolver());
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

    private DirectoryClasspathFileManager(StandardJavaFileManager delegate, List<Path> classPath) {
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

  private static Map.Entry<Extensions.FunctionDescriptor, ComponentRegistry.ServiceImplementation>
      javaVerb(String name, boolean isStatic, java.lang.reflect.Method method) {
    var descriptor = new Extensions.FunctionDescriptor();
    var serviceInfo = new ServiceInfoImpl();
    serviceInfo.setName("java.worker." + name);
    descriptor.serviceInfo = serviceInfo;
    descriptor.staticMethod = isStatic;
    var annotation = method.getAnnotation(Verb.class);
    descriptor.behaviorUrn =
        annotation == null || annotation.producesAgent().isBlank()
            ? null
            : annotation.producesAgent().trim();
    var implementation = new ComponentRegistry.ServiceImplementation();
    implementation.implementation = JavaStaticityActor.class;
    implementation.method = method;
    return Map.entry(descriptor, implementation);
  }

  public static class JavaStaticityActor {
    @Verb(name = "utility", executionType = Verb.Type.FUNCTION)
    public static Object utility() {
      return null;
    }

    @Verb(name = "work", executionType = Verb.Type.FUNCTION)
    public Object work() {
      return null;
    }

    @Verb(
        name = "make",
        executionType = Verb.Type.FUNCTION,
        producesAgent = "java.product")
    public static Object make() {
      return new JavaStaticityActor();
    }

    @Verb(name = "duration", executionType = Verb.Type.FUNCTION)
    public static Object duration(double amount, java.util.concurrent.TimeUnit unit) {
      return amount + " " + unit;
    }

    @Verb(name = "accept", executionType = Verb.Type.FUNCTION)
    public static Object accept(
        @Verb.Argument(name = "label", description = "Label", type = String.class) Object label,
        @Verb.Argument(
                name = "agent",
                description = "Required agent",
                requiresAgent = "java.required")
            Object agent) {
      return agent;
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

  private static KActorsStatementImpl.YieldImpl yielded(KActorsValueImpl value) {
    var statement = new KActorsStatementImpl.YieldImpl();
    statement.setValue(value);
    return statement;
  }

  private static KActorsStatementImpl.SwitchImpl switched(
      KActorsValueImpl value, KActorsStatement.Verb.MatchAction... cases) {
    var statement = new KActorsStatementImpl.SwitchImpl();
    statement.setValue(value);
    statement.setCases(List.of(cases));
    return statement;
  }

  private static KActorsStatementImpl.VerbImpl.MatchActionImpl switchCase(
      KActorsValueImpl criterion, KActorsStatement action) {
    var match = new KActorsStatementImpl.VerbImpl.MatchActionImpl();
    match.setMatchCriterion(criterion);
    match.setActionOnMatch(action);
    return match;
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

  private static KActorsValueImpl bool(boolean bool) {
    var value = new KActorsValueImpl();
    value.setType(ValueType.BOOLEAN);
    value.setStatedValue(bool);
    return value;
  }

  private static KActorsValueImpl list(KActorsValueImpl... values) {
    var value = new KActorsValueImpl();
    value.setType(ValueType.LIST);
    value.setStatedValue(List.of(values));
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
