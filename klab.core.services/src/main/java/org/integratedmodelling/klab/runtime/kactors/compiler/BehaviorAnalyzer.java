package org.integratedmodelling.klab.runtime.kactors.compiler;

import java.util.*;
import org.integratedmodelling.klab.api.lang.kactors.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.kactors.ApplicationBase;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.integratedmodelling.klab.runtime.kactors.ScriptBase;
import org.integratedmodelling.klab.runtime.kactors.TestCaseBase;

/**
 * Analyze the behavior and collect information for code generation. The compiler uses a
 * visitor/validator to establish and validate the type of the different actions, resolve any
 * referenced imports or inheritances, and validate those verb calls and other expressions that
 * admit validation, including searching for components that provide the needed verbs and agents
 * when unknown behaviors are referenced. Java-based actors and verbs should have their parameters
 * validated against the arguments passed in k.Actors and a strategy for the compiler to match them
 * in calls is established. The incoming behavior is guaranteed to have no syntax errors, but any
 * logical errors should be caught at this stage, and warnings may also be emitted for lack of
 * needed annotations or other issues.
 *
 * <p>All errors, warning or informational messages are collected in the notifications list that is
 * collected by the visitor/validator, then returned to the AgentCompiler through the analyzer after
 * establishing the appropriate {@link org.integratedmodelling.klab.api.actors.RuntimeAgent} class
 * to use for compilation. If there are no error notifications, the compiler may proceed to emit the
 * Java code implementing the agent.
 */
public class BehaviorAnalyzer {

  /** Whether an independently started behavior terminates by itself or needs an explicit stop. */
  public enum Lifecycle {
    FINITE,
    PERSISTENT
  }

  private final KActorsBehavior behavior;
  private final KActorsVisitor.Validator validator;
  private Class<? extends RuntimeAgentBase> agentClass = RuntimeAgentBase.class;
  private final List<Notification> notifications = new ArrayList<>();
  private Map<String, KActorsVisitor.ActionInfo> actions = Map.of();
  private List<KActorsVisitor.VariableInfo> fields = List.of();
  private List<KActorsVisitor.ImportInfo> imports = List.of();
  private List<KActorsVisitor.CallInfo> calls = List.of();
  private List<KActorsVisitor.ExpressionInfo> expressions = List.of();
  private Verb.Type agentExecutionMode = Verb.Type.FUNCTION;
  private Lifecycle lifecycle = Lifecycle.FINITE;

  public BehaviorAnalyzer(KActorsBehavior behavior) {
    this(behavior, new KActorsVisitor.LenientValidator());
  }

  public BehaviorAnalyzer(KActorsBehavior behavior, KActorsVisitor.Validator validator) {
    this.behavior = behavior;
    this.validator = Objects.requireNonNullElseGet(validator, KActorsVisitor.LenientValidator::new);
  }

  public boolean analyze() {

    notifications.clear();
    agentClass =
        switch (behavior.getBehaviorType()) {
          case APP -> ApplicationBase.class;
          case SCRIPT -> ScriptBase.class;
          case UNITTEST -> TestCaseBase.class;
          default -> RuntimeAgentBase.class;
        };
    var visitor = new KActorsVisitor();
    visitor.visit(behavior, validator);
    notifications.addAll(visitor.getNotifications());
    actions = visitor.getActions();
    fields = visitor.getFields();
    imports = visitor.getImports();
    calls = visitor.getCalls();
    expressions = visitor.getExpressions();
    agentExecutionMode = inferAgentExecutionMode();
    lifecycle =
        agentExecutionMode == Verb.Type.EMITTER ? Lifecycle.PERSISTENT : Lifecycle.FINITE;

    return notifications.stream()
        .noneMatch(notification -> notification.getLevel().severity >= Notification.Level.Error.severity);
  }

  public Class<? extends RuntimeAgentBase> getAgentClass() {
    return agentClass;
  }

  public Collection<Notification> getNotifications() {
    return List.copyOf(notifications);
  }

  public Map<String, KActorsVisitor.ActionInfo> getActions() {
    return actions;
  }

  public List<KActorsVisitor.VariableInfo> getFields() {
    return fields;
  }

  public List<KActorsVisitor.ImportInfo> getImports() {
    return imports;
  }

  public List<KActorsVisitor.CallInfo> getCalls() {
    return calls;
  }

  public List<KActorsVisitor.ExpressionInfo> getExpressions() {
    return expressions;
  }

  /**
   * Execution mode to emit from the generated {@link RuntimeAgentBase#getAgentExecutionMode()}
   * implementation. Suppliers are finite but must run asynchronously until their result arrives;
   * emitters remain alive until explicitly stopped.
   */
  public Verb.Type getAgentExecutionMode() {
    return agentExecutionMode;
  }

  public Lifecycle getLifecycle() {
    return lifecycle;
  }

  public boolean isPersistent() {
    return lifecycle == Lifecycle.PERSISTENT;
  }

  private Verb.Type inferAgentExecutionMode() {
    var mode = Verb.Type.FUNCTION;
    var init = actions.get("init");
    if (init != null) {
      mode = combine(mode, executionMode(init));
    }
    var main = actions.get("main");
    if (main != null) {
      mode = combine(mode, executionMode(main));
    }

    // Actor-like behaviors remain available for calls, @handle and @stdin messages after their
    // startup code returns. Only explicitly finite behavior kinds derive their lifecycle solely
    // from init/main execution.
    return switch (behavior.getBehaviorType()) {
      case BEHAVIOR, APP, USER, COMPONENT -> Verb.Type.EMITTER;
      default -> mode;
    };
  }

  private Verb.Type executionMode(KActorsVisitor.ActionInfo action) {
    // Unknown calls are run through the dynamic runtime bridge. Keep an independently started
    // agent alive conservatively because the unresolved call may turn out to be an emitter.
    if (action.callsUnknownActions()) {
      return Verb.Type.EMITTER;
    }
    return action.effectiveExecutionType();
  }

  private Verb.Type combine(Verb.Type left, Verb.Type right) {
    if (left == Verb.Type.EMITTER || right == Verb.Type.EMITTER) {
      return Verb.Type.EMITTER;
    }
    if (left == Verb.Type.SUPPLIER || right == Verb.Type.SUPPLIER) {
      return Verb.Type.SUPPLIER;
    }
    return Verb.Type.FUNCTION;
  }
}
