package org.integratedmodelling.klab.api.lang.kactors;

import java.util.*;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;

/**
 * A configurable visitor that walks an entire parsed {@link KActorsBehavior}. Subclasses can
 * override the typed visit methods to collect information and the context factory methods to carry
 * additional state through the syntax tree.
 *
 * <p>The default implementation of each compound-statement visitor continues the traversal.
 * Overrides that still need to visit children must call the corresponding {@code super} method.
 */
public class KActorsVisitor {

  public record ActionInfo(
      KActorsAction statement,
      String name,
      Verb.Type executionType,
      List<VariableInfo> parameters,
      int returns,
      int fires) {}

  public record ImportInfo(String name, Class<?> javaClass) {}

  public record CallInfo(
      KActorsStatement.Verb statement,
      String agent,
      String verb,
      Parameters<String> arguments,
      Map<String, VariableInfo> knownVariables) {}

  /**
   * Variable info will record the name and type if known, or the agent/verb pair for later checking
   *
   * @param name
   * @param type
   * @param agentUrn
   * @param verbUrn
   */
  public record VariableInfo(
      KActorsCodeStatement statement,
      String name,
      ValueType type,
      String agentUrn,
      String verbUrn) {}

  // the context will contain all known variables at the point of declaration
  public record ExpressionInfo(String expression, Map<String, VariableInfo> knownVariables) {}

  private final List<Notification> notifications = new ArrayList<>();
  private final Map<String, ActionInfo> actions = new LinkedHashMap<>();
  private final List<VariableInfo> fields = new ArrayList<>();
  private final List<ImportInfo> imports = new ArrayList<>();
  private final List<CallInfo> calls = new ArrayList<>();
  private final List<ExpressionInfo> expressions = new ArrayList<>();

  /** Context associated with one point in a behavior traversal. */
  public static class KActorsContext {

    protected final KActorsContext parent;
    protected final KActorsBehavior behavior;
    protected final KActorsAction action;
    protected final List<KActorsStatement> upstream;
    Map<String, VariableInfo> knownVariables = new LinkedHashMap<>();

    public KActorsContext(KActorsBehavior behavior) {
      this.parent = null;
      this.behavior = Objects.requireNonNull(behavior, "behavior");
      this.action = null;
      this.upstream = List.of();
    }

    protected KActorsContext(KActorsContext context) {
      this.parent = Objects.requireNonNull(context, "context");
      this.behavior = context.behavior;
      this.action = context.action;
      this.upstream = context.upstream;
    }

    protected KActorsContext(KActorsContext context, KActorsAction action) {
      this.parent = Objects.requireNonNull(context, "context");
      this.behavior = context.behavior;
      this.action = Objects.requireNonNull(action, "action");
      this.upstream = context.upstream;
    }

    protected KActorsContext(KActorsContext context, KActorsStatement statement) {
      this.parent = Objects.requireNonNull(context, "context");
      this.behavior = context.behavior;
      this.action = context.action;
      var statements = new ArrayList<>(context.upstream);
      statements.add(Objects.requireNonNull(statement, "statement"));
      this.upstream = List.copyOf(statements);
    }

    public <T extends KActorsStatement> T getUpstreamStatement(Class<T> statementClass) {
      for (int i = upstream.size() - 1; i >= 0; i--) {
        if (statementClass.isAssignableFrom(upstream.get(i).getClass())) {
          return (T) upstream.get(i);
        }
      }
      return null;
    }

    public KActorsContext getParent() {
      return parent;
    }

    public KActorsBehavior getBehavior() {
      return behavior;
    }

    public KActorsAction getAction() {
      return action;
    }

    /** Return the statement path from the action body to the statement currently being visited. */
    public List<KActorsStatement> getUpstream() {
      return upstream;
    }

    public KActorsStatement getStatement() {
      return upstream.isEmpty() ? null : upstream.getLast();
    }

    public KActorsContext withLocalVariables(List<VariableInfo> localVars) {
      for (var v : localVars) {
        this.knownVariables.put(v.name(), v);
      }
      return this;
    }
  }

  /** Create the root context for a behavior. */
  protected KActorsContext createBehaviorContext(KActorsBehavior behavior) {
    return new KActorsContext(behavior);
  }

  /** Create the context used while visiting an action. */
  protected KActorsContext createActionContext(
      KActorsContext upstreamContext, KActorsAction action) {
    return new KActorsContext(upstreamContext, action);
  }

  /** Create the context used while visiting a statement. */
  protected KActorsContext createStatementContext(
      KActorsContext upstreamContext, KActorsStatement statement) {
    return new KActorsContext(upstreamContext, statement);
  }

  public void visit(KActorsBehavior behavior) {
    var context = requireContext(createBehaviorContext(behavior), "behavior");
    // FIXME if @init is present, scan that first for `def`, which is only allowed there
    for (var action : behavior.getStatements()) {
      visitAction(action, createActionContext(context, action));
    }
  }

  protected void visitAction(KActorsAction action, KActorsContext context) {
    visitAnnotations(action.getAnnotations(), context);
    visitValues(action.getMetadata(), context);
    for (var statement : action.getCode()) {
      visitStatement(statement, createStatementContext(context, statement));
    }
  }

  public void visitAnnotation(Annotation annotation, KActorsContext context) {}

  public final void visitStatement(KActorsStatement statement, KActorsContext context) {

    visitAnnotations(statement.getAnnotations(), context);
    visitValues(statement.getMetadata(), context);

    switch (statement) {
      case KActorsStatement.Verb.MatchAction matchStatement -> visitMatch(matchStatement, context);
      case KActorsStatement.Assert.Assertion assertion -> visitAssertion(assertion, context);
      case KActorsStatement.Do doStatement -> visitDo(doStatement, context);
      case KActorsStatement.Assert assertStatement -> visitAssert(assertStatement, context);
      case KActorsStatement.Fail failStatement -> visitFail(failStatement, context);
      case KActorsStatement.Fire fireStatement -> visitFire(fireStatement, context);
      case KActorsStatement.If ifStatement -> visitIf(ifStatement, context);
      case KActorsStatement.While whileStatement -> visitWhile(whileStatement, context);
      case KActorsStatement.For forStatement -> visitFor(forStatement, context);
      case KActorsStatement.Break breakStatement -> visitBreak(breakStatement, context);
      case KActorsStatement.Text textStatement -> visitText(textStatement, context);
      case KActorsStatement.Assignment assignmentStatement ->
          visitAssignment(assignmentStatement, context);
      case KActorsStatement.Verb verbStatement -> visitVerb(verbStatement, context);
      case KActorsStatement.Group groupStatement -> visitGroup(groupStatement, context);
      case KActorsStatement.Return returnStatement -> visitReturn(returnStatement, context);
      default -> throw new IllegalArgumentException("Unsupported statement type: " + statement);
    }
  }

  protected void visitMatch(
      KActorsStatement.Verb.MatchAction matchStatement, KActorsContext context) {

    var criterion = matchStatement.getMatchCriterion();
    var localVars = new ArrayList<VariableInfo>();
    if (criterion.getType() == ValueType.IDENTIFIER) {
      // can be a string or a list. Type is matched to upstream verb
      var verb = context.getUpstreamStatement(KActorsStatement.Verb.class);
      for (var idv :
          (criterion.getValue(Object.class) instanceof List
              ? criterion.getValue(List.class)
              : List.of(criterion.getValue(Object.class)))) {
        var id = idv.toString();
        localVars.add(
            new VariableInfo(criterion, id, null, verb.getRecipient(), verb.getMessage()));
      }
    }

    visitStatementIfPresent(matchStatement.getActionOnMatch(), context, localVars);
  }

  protected void visitValue(KActorsValue value, KActorsContext context) {
    visitAnnotations(value.getAnnotations(), context);
    if (value.getType() == ValueType.EXPRESSION) {
      expressions.add(
          new ExpressionInfo(
              value.getValue(String.class), new LinkedHashMap<>(context.knownVariables)));
    } else if (value.getType() == ValueType.IDENTIFIER) {
      /*
       * TODO ensure it's known; if not, add a notification with LexicalContext.of(value)
       */
      System.out.println(value);
    }
  }

  protected void visitDo(KActorsStatement.Do doStatement, KActorsContext context) {
    visitStatementIfPresent(doStatement.getBody(), context, List.of());
    visitValueIfPresent(doStatement.getCondition(), context);
  }

  protected void visitAssert(KActorsStatement.Assert assertStatement, KActorsContext context) {
    visitValues(assertStatement.getArguments(), context);
    for (var assertion : assertStatement.getAssertions()) {
      visitStatement(assertion, context);
    }
  }

  protected void visitAssertion(
      KActorsStatement.Assert.Assertion assertion, KActorsContext context) {
    if (assertion.getCalls() != null) {
      for (var call : assertion.getCalls()) {
        visitStatement(call, context);
      }
    }
    visitValueIfPresent(assertion.getValue(), context);
  }

  protected void visitFail(KActorsStatement.Fail failStatement, KActorsContext context) {}

  protected void visitFire(KActorsStatement.Fire fireStatement, KActorsContext context) {
    if (context.getUpstreamStatement(KActorsStatement.Verb.MatchAction.class) != null) {
      // increment fire count in the current action
    }
    visitValueIfPresent(fireStatement.getValue(), context);
  }

  protected void visitIf(KActorsStatement.If ifStatement, KActorsContext context) {
    visitValueIfPresent(ifStatement.getCondition(), context);
    visitStatementIfPresent(ifStatement.getThenBody(), context, List.of());
    for (var elseIf : ifStatement.getElseIfs()) {
      visitValueIfPresent(elseIf.getFirst(), context);
      visitStatementIfPresent(elseIf.getSecond(), context, List.of());
    }
    visitStatementIfPresent(ifStatement.getElseBody(), context, List.of());
  }

  protected void visitWhile(KActorsStatement.While whileStatement, KActorsContext context) {
    visitValueIfPresent(whileStatement.getCondition(), context);
    visitStatementIfPresent(whileStatement.getBody(), context, List.of());
  }

  // for, assignment and match are those that can define contextual variables
  protected void visitFor(KActorsStatement.For forStatement, KActorsContext context) {
    visitValueIfPresent(forStatement.getIterable(), context);
    visitStatementIfPresent(
        forStatement.getBody(),
        context,
        List.of(
            new VariableInfo(
                forStatement.getIterable(),
                forStatement.getVariable(),
                forStatement.getIterable().getType(),
                null,
                null)));
  }

  protected void visitBreak(KActorsStatement.Break breakStatement, KActorsContext context) {}

  protected void visitText(KActorsStatement.Text textStatement, KActorsContext context) {}

  protected void visitAssignment(
      KActorsStatement.Assignment assignmentStatement, KActorsContext context) {

    VariableInfo varInfo = null;
    if (assignmentStatement.getValue() != null) {
      varInfo =
          new VariableInfo(
              assignmentStatement.getValue(),
              assignmentStatement.getVariable(),
              assignmentStatement.getValue().getType(),
              null,
              null);
    } else {
      varInfo =
          new VariableInfo(
              assignmentStatement.getFunction(),
              assignmentStatement.getVariable(),
              null,
              assignmentStatement.getFunction().getRecipient(),
              assignmentStatement.getFunction().getMessage());
    }

    visitValueIfPresent(assignmentStatement.getValue(), context);
    visitStatementIfPresent(assignmentStatement.getFunction(), context, List.of(varInfo));
  }

  protected void visitVerb(KActorsStatement.Verb verbStatement, KActorsContext context) {

    /*
    TODO check for receiver declared and known action.
    TODO check action parameters if possible
     */
    // record call with the known variables for later checking
    calls.add(
        new CallInfo(
            verbStatement,
            verbStatement.getRecipient() == null ? "self" : verbStatement.getRecipient(),
            verbStatement.getMessage(),
            verbStatement.getArguments(),
            new LinkedHashMap<>(context.knownVariables)));

    visitValues(verbStatement.getArguments(), context);
    for (var matchAction : verbStatement.getActions()) {
      visitStatement(matchAction, context);
    }
  }

  protected void visitGroup(KActorsStatement.Group groupStatement, KActorsContext context) {
    for (var statement : groupStatement.getStatements()) {
      visitStatement(statement, context);
    }
  }

  protected void visitReturn(KActorsStatement.Return returnStatement, KActorsContext context) {
    if (context.getUpstreamStatement(KActorsStatement.Verb.MatchAction.class) != null) {
      // increment reactor return count in the current action
    } else {
      // check if we're downstream of an if; if so, will need a final return
    }

    visitValueIfPresent(returnStatement.getValue(), context);
  }

  private void visitAnnotations(List<Annotation> annotations, KActorsContext context) {
    if (annotations != null) {
      for (var annotation : annotations) {
        visitAnnotation(annotation, context);
      }
    }
  }

  private void visitValues(java.util.Map<String, ?> values, KActorsContext context) {
    if (values != null) {
      for (var value : values.values()) {
        if (value instanceof KActorsValue kActorsValue) {
          visitValue(kActorsValue, context);
        }
      }
    }
  }

  private void visitValueIfPresent(KActorsValue value, KActorsContext context) {
    if (value != null) {
      visitValue(value, context);
    }
  }

  private void visitStatementIfPresent(
      KActorsStatement statement, KActorsContext context, List<VariableInfo> localVars) {
    if (statement != null) {
      visitStatement(statement, context.withLocalVariables(localVars));
    }
  }

  private KActorsContext requireContext(KActorsContext context, String element) {
    return Objects.requireNonNull(context, "The " + element + " context factory returned null");
  }

  public List<VariableInfo> getFields() {
    return fields;
  }

  public List<Notification> getNotifications() {
    return notifications;
  }

  public List<ImportInfo> getImports() {
    return imports;
  }
}
