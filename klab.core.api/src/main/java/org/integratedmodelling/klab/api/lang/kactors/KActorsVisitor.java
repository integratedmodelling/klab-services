package org.integratedmodelling.klab.api.lang.kactors;

import java.util.*;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Ternary;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;

/**
 * A configurable visitor that walks and semantically indexes a parsed {@link KActorsBehavior}. The
 * parser guarantees syntactic correctness; this class establishes lexical scopes, records the
 * information needed by a compiler, infers action execution modes, and reports model-level errors.
 */
public class KActorsVisitor {

  /** Extension point for checks that require runtime components or language services. */
  public interface Validator {

    /**
     * Classify a call whose target is supplied by an imported/inherited behavior or Java extension.
     * The returned type should be the effective type, including reactive calls made by the target
     * action itself, so callers can reason about lifecycle across behavior boundaries.
     *
     * <p>A null return value means that the type remains unknown and validation should be turned
     * off. As this normally happens when the recipient results from another verb, the {@link
     * #classifyActionCallFromProducer(KActorsStatement.Verb, KActorsStatement.Verb,
     * KActorsContext)} will normally be called after this has failed to classify the verb, and that
     * call will include the provenance of the action that produced the recipient, so that more
     * sophisticated tests can be made before giving up.
     *
     * <p>If the type of the action cannot be established in either way, the {@link
     * #warnAboutUnknownActionCall(KActorsStatement.Verb, KActorsContext)} method controls whether a
     * warning should be emitted or not.
     */
    default Verb.Type classifyActionCall(KActorsStatement.Verb verb, KActorsContext context) {
      return null;
    }

    /**
     * Try to classify a call on a variable produced by another verb. This is invoked only when
     * {@link #classifyActionCall(KActorsStatement.Verb, KActorsContext)} returned {@code null} and
     * the recipient can be traced to an assignment or loop iterable supplied by a verb.
     *
     * @param verb the call whose execution type is needed
     * @param recipientProducer the earlier call that produced the recipient value
     * @param context the lexical context of {@code verb}
     * @return the execution type, or {@code null} when it remains unknown
     */
    default Verb.Type classifyActionCallFromProducer(
        KActorsStatement.Verb verb,
        KActorsStatement.Verb recipientProducer,
        KActorsContext context) {
      return null;
    }

    /** Whether the visitor should warn when a call remains dynamically typed. */
    default boolean warnAboutUnknownActionCall(KActorsStatement.Verb verb, KActorsContext context) {
      return false;
    }

    default List<Notification> validateBehavior(KActorsBehavior behavior, KActorsContext context) {
      return List.of();
    }

    default List<Notification> validateImport(
        KActorsBehavior.Import imported, KActorsContext context) {
      return List.of();
    }

    /**
     * Return all tags exposed by an imported or inherited behavior, including tags contributed
     * transitively by its own imports and inheritances. Implementations may resolve the URN to
     * either a parsed behavior or a component-provided actor descriptor. Returning an empty list
     * leaves cross-behavior tag validation disabled for that unresolved reference.
     */
    default List<String> getBehaviorTags(String behaviorUrn, KActorsContext context) {
      return List.of();
    }

    default List<Notification> validateAction(KActorsAction action, KActorsContext context) {
      return List.of();
    }

    default List<Notification> validateAssignment(
        KActorsStatement.Assignment assignment, KActorsContext context) {
      return List.of();
    }

    /**
     * Validate conversion of an assignment's original value to the requested behavior. The source
     * variable describes the value before adaptation, including literal type or producer-call
     * provenance. Environment-aware implementations must resolve {@code behaviorUrn} and determine
     * whether an adapter can be created for that source.
     *
     * @param assignment the local assignment carrying the {@code as} clause
     * @param behaviorUrn requested target behavior
     * @param sourceVariable compile-time description of the unadapted value
     * @param context lexical context
     * @return diagnostics; any error prevents the assigned variable from acquiring the target
     *     behavior type
     */
    default List<Notification> validateAdaptation(
        KActorsStatement.Assignment assignment,
        String behaviorUrn,
        VariableInfo sourceVariable,
        KActorsContext context) {
      return List.of();
    }

    default List<Notification> validateVerbCall(
        KActorsStatement.Verb verb, KActorsContext context) {
      return List.of();
    }

    default List<Notification> validateArguments(
        KActorsStatement.Verb verb, KActorsStatement.Arguments arguments, KActorsContext context) {
      return List.of();
    }

    default List<Notification> validateExpression(
        Expression.Descriptor expressionDescriptor, KActorsContext context) {
      return List.of();
    }
  }

  /** Syntax-only validator used when component-backed resolution is not available. */
  public static class LenientValidator implements Validator {}

  public record ActionInfo(
      KActorsAction statement,
      String name,
      Verb.Type executionType,
      List<VariableInfo> parameters,
      int returns,
      int fires,
      Set<Verb.Type> calledActionTypes,
      boolean callsUnknownActions) {
    public ActionInfo {
      parameters = List.copyOf(parameters);
      calledActionTypes = Set.copyOf(calledActionTypes);
    }

    public boolean callsSuppliers() {
      return calledActionTypes.contains(Verb.Type.SUPPLIER);
    }

    public boolean callsEmitters() {
      return calledActionTypes.contains(Verb.Type.EMITTER);
    }

    /** Effective mode when this action is invoked, including all transitively invoked actions. */
    public Verb.Type effectiveExecutionType() {
      if (executionType == Verb.Type.EMITTER || callsEmitters()) {
        return Verb.Type.EMITTER;
      }
      if (executionType == Verb.Type.SUPPLIER || callsSuppliers()) {
        return Verb.Type.SUPPLIER;
      }
      return Verb.Type.FUNCTION;
    }
  }

  public record ImportInfo(
      KActorsBehavior.Import statement, String name, String behaviorUrn, Class<?> javaClass) {}

  public record CallInfo(
      KActorsStatement.Verb statement,
      String agent,
      String verb,
      String action,
      Parameters<String> arguments,
      Map<String, VariableInfo> knownVariables,
      Verb.Type executionType,
      boolean valueRequired) {
    public CallInfo {
      knownVariables = Collections.unmodifiableMap(new LinkedHashMap<>(knownVariables));
    }
  }

  public record VariableInfo(
      KActorsCodeStatement statement,
      String name,
      ValueType type,
      String agentUrn,
      String verbUrn,
      KActorsStatement.Verb producerCall) {}

  public record ExpressionInfo(
      KActorsValue statement, Object expression, Map<String, VariableInfo> knownVariables) {
    public ExpressionInfo {
      knownVariables = Collections.unmodifiableMap(new LinkedHashMap<>(knownVariables));
    }
  }

  /** Context at one lexical point in the traversal. Child contexts copy visible variables. */
  public static class KActorsContext {

    protected final KActorsContext parent;
    protected final KActorsBehavior behavior;
    protected KActorsAction action;
    protected List<KActorsStatement> upstream;
    protected final Validator validator;
    protected final Map<String, VariableInfo> knownVariables;
    protected int loopDepth;
    protected KActorsStatement previousStatement;

    public KActorsContext(KActorsBehavior behavior, Validator validator) {
      this.parent = null;
      this.behavior = Objects.requireNonNull(behavior, "behavior");
      this.action = null;
      this.validator = Objects.requireNonNullElseGet(validator, LenientValidator::new);
      this.upstream = List.of();
      this.knownVariables = new LinkedHashMap<>();
    }

    protected KActorsContext(KActorsContext context) {
      this.parent = Objects.requireNonNull(context, "context");
      this.behavior = context.behavior;
      this.action = context.action;
      this.upstream = context.upstream;
      this.validator = context.validator;
      this.knownVariables = new LinkedHashMap<>(context.knownVariables);
      this.loopDepth = context.loopDepth;
      this.previousStatement = context.previousStatement;
    }

    protected KActorsContext(KActorsContext context, KActorsAction action) {
      this(context);
      this.action = Objects.requireNonNull(action, "action");
    }

    protected KActorsContext(KActorsContext context, KActorsStatement statement) {
      this(context);
      var statements = new ArrayList<>(context.upstream);
      statements.add(Objects.requireNonNull(statement, "statement"));
      this.upstream = List.copyOf(statements);
    }

    @SuppressWarnings("unchecked")
    public <T extends KActorsStatement> T getUpstreamStatement(Class<T> statementClass) {
      for (int i = upstream.size() - 1; i >= 0; i--) {
        if (statementClass.isInstance(upstream.get(i))) {
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

    public List<KActorsStatement> getUpstream() {
      return upstream;
    }

    public KActorsStatement getStatement() {
      return upstream.isEmpty() ? null : upstream.getLast();
    }

    public KActorsStatement getPreviousStatement() {
      return previousStatement;
    }

    public Map<String, VariableInfo> getKnownVariables() {
      return Collections.unmodifiableMap(knownVariables);
    }

    public VariableInfo getVariable(String name) {
      return knownVariables.get(name);
    }

    public boolean isInsideLoop() {
      return loopDepth > 0;
    }

    public KActorsContext withLocalVariables(Collection<VariableInfo> localVariables) {
      var ret = new KActorsContext(this);
      if (localVariables != null) {
        for (var variable : localVariables) {
          if (variable != null && variable.name() != null) {
            ret.knownVariables.put(variable.name(), variable);
          }
        }
      }
      return ret;
    }
  }

  private static final Set<String> BUILT_IN_IDENTIFIERS = Set.of("self", "scope");

  private final List<Notification> notifications = new ArrayList<>();
  private final Map<String, ActionInfo> actions = new LinkedHashMap<>();
  private final List<VariableInfo> fields = new ArrayList<>();
  private final List<ImportInfo> imports = new ArrayList<>();
  private final List<CallInfo> calls = new ArrayList<>();
  private final List<ExpressionInfo> expressions = new ArrayList<>();
  private final Map<String, KActorsAction> actionDeclarations = new LinkedHashMap<>();
  private final Map<String, VariableInfo> fieldDeclarations = new LinkedHashMap<>();
  private final Map<String, String> tagDeclarations = new LinkedHashMap<>();
  private final Map<KActorsAction, ActionAccumulator> actionAccumulators = new IdentityHashMap<>();
  private final List<PendingCall> pendingCalls = new ArrayList<>();
  private final Map<KActorsStatement.Assignment, VariableInfo> assignmentVariables =
      new IdentityHashMap<>();

  private record PendingCall(
      KActorsStatement.Verb statement, KActorsContext context, boolean valueRequired) {}

  private static final class ActionAccumulator {
    private int returns;
    private int fires;
    private int reactiveReturns;
    private final EnumSet<Verb.Type> calledActionTypes = EnumSet.noneOf(Verb.Type.class);
    private final Set<String> localCallees = new LinkedHashSet<>();
    private boolean callsUnknownActions;
  }

  protected KActorsContext createBehaviorContext(KActorsBehavior behavior, Validator validator) {
    return new KActorsContext(behavior, validator);
  }

  protected KActorsContext createActionContext(
      KActorsContext upstreamContext, KActorsAction action) {
    return new KActorsContext(upstreamContext, action);
  }

  protected KActorsContext createStatementContext(
      KActorsContext upstreamContext, KActorsStatement statement) {
    return new KActorsContext(upstreamContext, statement);
  }

  public void visit(KActorsBehavior behavior) {
    visit(behavior, new LenientValidator());
  }

  public void visit(KActorsBehavior behavior, Validator validator) {
    reset();
    visitedBehavior = Objects.requireNonNull(behavior, "behavior");
    var context = requireContext(createBehaviorContext(behavior, validator), "behavior");
    addNotifications(context.validator.validateBehavior(behavior, context));
    indexBehavior(context);
    collectActorFields(behavior);
    context.knownVariables.putAll(fieldDeclarations);

    var init = actionDeclarations.get("init");
    if (init != null) {
      visitAction(init, createActionContext(context, init));
    }
    for (var action : safe(behavior.getStatements())) {
      if (action != init) {
        visitAction(action, createActionContext(context, action));
      }
    }
    finishActions();
    finishCalls();
    finishCalledActionTypes();
  }

  private void reset() {
    notifications.clear();
    actions.clear();
    fields.clear();
    imports.clear();
    calls.clear();
    expressions.clear();
    actionDeclarations.clear();
    fieldDeclarations.clear();
    tagDeclarations.clear();
    actionAccumulators.clear();
    pendingCalls.clear();
    assignmentVariables.clear();
    visitedBehavior = null;
  }

  private void indexBehavior(KActorsContext context) {
    var behavior = context.behavior;
    if (behavior.getDescription() == null || behavior.getDescription().isBlank()) {
      error("Behavior description is mandatory", behavior);
    }

    var aliases = new HashSet<String>();
    for (var imported : safe(behavior.getImports())) {
      var alias = imported.getImportedAlias();
      imports.add(new ImportInfo(imported, alias, imported.getImportedBehavior(), null));
      if (alias == null || alias.isBlank()) {
        notifications.add(Notification.error("Imported behaviors must declare a local alias"));
      } else if ("self".equals(alias)) {
        notifications.add(Notification.error("The import alias 'self' is reserved"));
      } else if (!aliases.add(alias)) {
        notifications.add(Notification.error("Duplicate import alias: " + alias));
      }
      addNotifications(context.validator.validateImport(imported, context));
      registerReferencedTags(imported.getImportedBehavior(), context);
    }

    for (var inheritedBehavior : safe(behavior.getInheritedBehaviors())) {
      registerReferencedTags(inheritedBehavior, context);
    }

    for (var action : safe(behavior.getStatements())) {
      var previous = actionDeclarations.putIfAbsent(action.getUrn(), action);
      if (previous != null) {
        error("Duplicate action: " + action.getUrn(), action);
      }
    }

    if (behavior.getBehaviorType() == KActorsBehavior.Type.LIBRARY) {
      for (var reservedAction : List.of("init", "main")) {
        var action = actionDeclarations.get(reservedAction);
        if (action != null) {
          error("Library behaviors cannot declare the " + reservedAction + " action", action);
        }
      }
    } else if (behavior.getBehaviorType() == KActorsBehavior.Type.TASK
        && !actionDeclarations.containsKey("main")) {
      notifications.add(Notification.error("Task behaviors must declare a main action"));
    }
  }

  private void collectActorFields(KActorsBehavior behavior) {
    var init = actionDeclarations.get("init");
    if (init == null) {
      return;
    }
    for (var statement : safe(init.getCode())) {
      collectActorFields(statement);
    }
    fields.addAll(fieldDeclarations.values());
  }

  private void collectActorFields(KActorsStatement statement) {
    if (statement == null) {
      return;
    }
    if (statement instanceof KActorsStatement.Assignment assignment
        && assignment.getAssignmentScope() == KActorsStatement.Assignment.Scope.ACTOR) {
      fieldDeclarations.putIfAbsent(assignment.getVariable(), variableFor(assignment));
    }
    forEachChild(statement, this::collectActorFields);
  }

  protected void visitAction(KActorsAction action, KActorsContext context) {
    registerTag(action.getTag(), action, "behavior " + visitedBehavior.getUrn());
    addNotifications(context.validator.validateAction(action, context));
    visitAnnotations(action.getAnnotations(), context);
    visitMetadataValues(action.getMetadata(), context);
    var parameters = new ArrayList<VariableInfo>();
    var names = new HashSet<String>();
    for (var name : safe(action.getArgumentNames())) {
      if (!names.add(name)) {
        error("Duplicate action parameter: " + name, action);
      }
      var parameter = new VariableInfo(action, name, null, null, null, null);
      parameters.add(parameter);
      context.knownVariables.put(name, parameter);
    }
    actionAccumulators.put(action, new ActionAccumulator());
    visitBlock(action.getCode(), context);
    actions.put(
        action.getUrn(),
        new ActionInfo(
            action, action.getUrn(), Verb.Type.FUNCTION, parameters, 0, 0, Set.of(), false));
  }

  public void visitAnnotation(Annotation annotation, KActorsContext context) {
    visitValues(annotation, context);
  }

  public final void visitStatement(KActorsStatement statement, KActorsContext context) {
    if (statement == null) {
      return;
    }
    registerTag(statement.getTag(), statement, "behavior " + visitedBehavior.getUrn());
    if (statement.isSequential() && !hasMatchActions(context.previousStatement)) {
      warning("'then' has no preceding reactive call to wait for", statement);
    }
    visitAnnotations(statement.getAnnotations(), context);
    visitMetadataValues(statement.getMetadata(), context);

    switch (statement) {
      case KActorsStatement.Verb.MatchAction match -> visitMatch(match, context);
      case KActorsStatement.Assert.Assertion assertion -> visitAssertion(assertion, context);
      case KActorsStatement.Do loop -> visitDo(loop, context);
      case KActorsStatement.Assert assertion -> visitAssert(assertion, context);
      case KActorsStatement.Fail fail -> visitFail(fail, context);
      case KActorsStatement.Fire fire -> visitFire(fire, context);
      case KActorsStatement.If conditional -> visitIf(conditional, context);
      case KActorsStatement.While loop -> visitWhile(loop, context);
      case KActorsStatement.For loop -> visitFor(loop, context);
      case KActorsStatement.Break breakStatement -> visitBreak(breakStatement, context);
      case KActorsStatement.Text text -> visitText(text, context);
      case KActorsStatement.Assignment assignment -> visitAssignment(assignment, context);
      case KActorsStatement.Verb verb -> visitVerb(verb, context);
      case KActorsStatement.Group group -> visitGroup(group, context);
      case KActorsStatement.Return returnStatement -> visitReturn(returnStatement, context);
      default -> error("Unsupported statement type: " + statement.getClass().getName(), statement);
    }
  }

  protected void visitMatch(
      KActorsStatement.Verb.MatchAction matchStatement, KActorsContext context) {
    var localVariables = new ArrayList<VariableInfo>();
    var upstreamVerb = context.getUpstreamStatement(KActorsStatement.Verb.class);
    var agent = upstreamVerb == null ? null : upstreamVerb.getRecipient();
    var verb = upstreamVerb == null ? null : upstreamVerb.getMessage();
    for (var name : safe(matchStatement.getVariables())) {
      localVariables.add(new VariableInfo(matchStatement, name, null, agent, verb, null));
    }
    if (matchStatement.getCaptureAs() != null) {
      localVariables.add(
          new VariableInfo(matchStatement, matchStatement.getCaptureAs(), null, agent, verb, null));
    }
    var criterion = matchStatement.getMatchCriterion();
    if (criterion != null) {
      if (criterion.getType() == ValueType.IDENTIFIER) {
        var raw = valueOf(criterion);
        var values = raw instanceof Collection<?> collection ? collection : List.of(raw);
        for (var value : values) {
          if (value != null) {
            localVariables.add(
                new VariableInfo(criterion, value.toString(), null, agent, verb, null));
          }
        }
      }
      visitValue(criterion, context.withLocalVariables(localVariables));
    }
    visitNested(matchStatement.getActionOnMatch(), context, localVariables, false);
  }

  protected void visitValue(KActorsValue value, KActorsContext context) {
    visitValue(value, context, true);
  }

  private void visitValue(KActorsValue value, KActorsContext context, boolean resolveIdentifiers) {
    visitAnnotations(value.getAnnotations(), context);
    var raw = valueOf(value);
    if (value.getType() == ValueType.EXPRESSION) {
      expressions.add(new ExpressionInfo(value, raw, context.knownVariables));
      if (raw instanceof Expression.Descriptor descriptor) {
        addNotifications(context.validator.validateExpression(descriptor, context));
      }
    } else if (resolveIdentifiers && value.getType() == ValueType.IDENTIFIER && raw != null) {
      var identifier = raw.toString();
      if (!context.knownVariables.containsKey(identifier)
          && !BUILT_IN_IDENTIFIERS.contains(identifier)) {
        error("Unknown identifier: " + identifier, value);
      }
    } else if (value.getType() == ValueType.TERNARY_EXPRESSION
        && raw instanceof Ternary ternary
        && ternary.getCondition() instanceof KActorsValue condition) {
      validateBooleanValue(condition, "ternary condition");
    }
    visitValues(raw, context, resolveIdentifiers);
  }

  protected void visitDo(KActorsStatement.Do statement, KActorsContext context) {
    validateAlternative(
        statement.getCondition(), statement.getFunction(), "do condition", statement);
    visitNested(statement.getBody(), context, List.of(), true);
    visitValueIfPresent(statement.getCondition(), context);
    visitVerbAsValue(statement.getFunction(), context);
    validateBooleanValue(statement.getCondition(), "do condition");
  }

  protected void visitAssert(KActorsStatement.Assert statement, KActorsContext context) {
    if (context.behavior.getBehaviorType() != KActorsBehavior.Type.UNITTEST) {
      warning(
          "Assertions in non-test behaviors may be omitted by production compilation", statement);
    }
    visitValues(statement.getArguments(), context);
    for (var assertion : safe(statement.getAssertions())) {
      visitNested(assertion, context, List.of(), false);
    }
  }

  protected void visitAssertion(
      KActorsStatement.Assert.Assertion assertion, KActorsContext context) {
    for (var call : safe(assertion.getCalls())) {
      visitNested(call, context, List.of(), false);
    }
    visitValueIfPresent(assertion.getExpression(), context);
    visitValueIfPresent(assertion.getValue(), context);
  }

  protected void visitFail(KActorsStatement.Fail failStatement, KActorsContext context) {}

  protected void visitFire(KActorsStatement.Fire statement, KActorsContext context) {
    validateAlternative(statement.getValue(), statement.getFunction(), "fire value", statement);
    var accumulator = actionAccumulators.get(context.action);
    if (accumulator != null) {
      accumulator.fires++;
    }
    visitValueIfPresent(statement.getValue(), context);
    visitVerbAsValue(statement.getFunction(), context);
  }

  protected void visitIf(KActorsStatement.If statement, KActorsContext context) {
    validateAlternative(
        statement.getCondition(), statement.getFunction(), "if condition", statement);
    visitValueIfPresent(statement.getCondition(), context);
    visitVerbAsValue(statement.getFunction(), context);
    validateBooleanValue(statement.getCondition(), "if condition");
    visitNested(statement.getThenBody(), context, List.of(), false);
    for (var elseIf : safe(statement.getElseIfs())) {
      if (elseIf == null || elseIf.getFirst() == null) {
        continue;
      }
      var condition = elseIf.getFirst();
      validateAlternative(
          condition.getFirst(), condition.getSecond(), "else-if condition", statement);
      visitValueIfPresent(condition.getFirst(), context);
      visitVerbAsValue(condition.getSecond(), context);
      validateBooleanValue(condition.getFirst(), "else-if condition");
      visitNested(elseIf.getSecond(), context, List.of(), false);
    }
    visitNested(statement.getElseBody(), context, List.of(), false);
  }

  protected void visitWhile(KActorsStatement.While statement, KActorsContext context) {
    validateAlternative(
        statement.getCondition(), statement.getFunction(), "while condition", statement);
    visitValueIfPresent(statement.getCondition(), context);
    visitVerbAsValue(statement.getFunction(), context);
    validateBooleanValue(statement.getCondition(), "while condition");
    visitNested(statement.getBody(), context, List.of(), true);
  }

  protected void visitFor(KActorsStatement.For statement, KActorsContext context) {
    validateAlternative(
        statement.getIterable(), statement.getFunction(), "for iterable", statement);
    visitValueIfPresent(statement.getIterable(), context);
    visitVerbAsValue(statement.getFunction(), context);
    validateIterableValue(statement.getIterable(), statement);
    var loopVariables = new ArrayList<VariableInfo>();
    if (statement.getVariable() != null && !statement.getVariable().isBlank()) {
      loopVariables.add(
          new VariableInfo(
              statement, statement.getVariable(), null, null, null, statement.getFunction()));
    }
    visitNested(statement.getBody(), context, loopVariables, true);
  }

  protected void visitBreak(KActorsStatement.Break statement, KActorsContext context) {
    if (!context.isInsideLoop()) {
      error("break can only be used inside a loop", statement);
    }
  }

  protected void visitText(KActorsStatement.Text textStatement, KActorsContext context) {}

  protected void visitAssignment(KActorsStatement.Assignment statement, KActorsContext context) {
    validateAlternative(
        statement.getValue(), statement.getFunction(), "assignment value", statement);
    if (isImported(statement.getVariable())) {
      error("An assignment cannot override an import alias: " + statement.getVariable(), statement);
    } else if (statement.getAssignmentScope() == KActorsStatement.Assignment.Scope.FRAME
        && fieldDeclarations.containsKey(statement.getVariable())) {
      error("A frame variable cannot override actor state: " + statement.getVariable(), statement);
    } else if (statement.getAssignmentScope() == KActorsStatement.Assignment.Scope.ACTOR
        && !"init".equals(context.action == null ? null : context.action.getUrn())
        && !fieldDeclarations.containsKey(statement.getVariable())) {
      error("Unknown actor state variable: " + statement.getVariable(), statement);
    }
    addNotifications(context.validator.validateAssignment(statement, context));
    var sourceVariable = unadaptedVariableFor(statement, context);
    String adaptedBehaviorUrn = normalized(statement.getAdaptedBehaviorUrn());
    if (adaptedBehaviorUrn != null) {
      if (statement.getAssignmentScope() != KActorsStatement.Assignment.Scope.FRAME) {
        error("Behavior adaptation is only allowed on local frame assignments", statement);
      } else {
        var adaptationNotifications =
            safe(
                context
                    .validator
                    .validateAdaptation(
                        statement, adaptedBehaviorUrn, sourceVariable, context));
        addNotifications(adaptationNotifications);
        if (adaptationNotifications.stream()
            .noneMatch(
                notification ->
                    notification.getLevel().severity >= Notification.Level.Error.severity)) {
          assignmentVariables.put(
              statement,
              new VariableInfo(
                  statement,
                  statement.getVariable(),
                  null,
                  adaptedBehaviorUrn,
                  null,
                  sourceVariable.producerCall()));
        }
      }
    }
    visitValueIfPresent(statement.getValue(), context);
    visitVerbAsValue(statement.getFunction(), context);
  }

  protected void visitVerb(KActorsStatement.Verb statement, KActorsContext context) {
    pendingCalls.add(new PendingCall(statement, context, isValuePosition(context)));
    visitValues(statement.getArguments(), context);
    for (var matchAction : safe(statement.getActions())) {
      visitNested(matchAction, context, List.of(), false);
    }
  }

  protected void visitGroup(KActorsStatement.Group statement, KActorsContext context) {
    visitBlock(statement.getStatements(), context);
  }

  protected void visitReturn(KActorsStatement.Return statement, KActorsContext context) {
    var accumulator = actionAccumulators.get(context.action);
    var reactive = context.getUpstreamStatement(KActorsStatement.Verb.MatchAction.class) != null;
    if (accumulator != null) {
      accumulator.returns++;
      if (reactive) {
        accumulator.reactiveReturns++;
      }
    }
    validateAlternative(statement.getValue(), statement.getFunction(), "return value", statement);
    visitValueIfPresent(statement.getValue(), context);
    visitVerbAsValue(statement.getFunction(), context);
  }

  private void visitBlock(List<KActorsStatement> statements, KActorsContext parent) {
    var block = new KActorsContext(parent);
    for (var statement : safe(statements)) {
      var statementContext = createStatementContext(block, statement);
      visitStatement(statement, statementContext);
      if (statement instanceof KActorsStatement.Assignment assignment
          && assignment.getAssignmentScope() == KActorsStatement.Assignment.Scope.FRAME) {
        block.knownVariables.put(assignment.getVariable(), variableFor(assignment));
      }
      block.previousStatement = statement;
    }
  }

  private void visitNested(
      KActorsStatement statement,
      KActorsContext parent,
      Collection<VariableInfo> variables,
      boolean loop) {
    if (statement == null) {
      return;
    }
    var nested = parent.withLocalVariables(variables);
    if (loop) {
      nested.loopDepth++;
    }
    visitStatement(statement, createStatementContext(nested, statement));
  }

  private void visitVerbAsValue(KActorsStatement.Verb verb, KActorsContext context) {
    if (verb != null) {
      visitStatement(verb, createStatementContext(context, verb));
    }
  }

  private void finishActions() {
    for (var entry : actions.entrySet()) {
      var old = entry.getValue();
      var accumulator = actionAccumulators.get(old.statement());
      var type =
          accumulator.fires > 0
              ? Verb.Type.EMITTER
              : accumulator.reactiveReturns > 0 ? Verb.Type.SUPPLIER : Verb.Type.FUNCTION;
      old.statement().setActionType(type);
      entry.setValue(
          new ActionInfo(
              old.statement(),
              old.name(),
              type,
              old.parameters(),
              accumulator.returns,
              accumulator.fires,
              Set.of(),
              false));
    }
  }

  private void finishCalls() {
    for (var pending : pendingCalls) {
      var statement = pending.statement();
      var recipient = normalizeRecipient(statement.getRecipient());
      Verb.Type executionType = null;
      if ("self".equals(recipient)) {
        var target = actions.get(statement.getMessage());
        if (target == null) {
          if (safe(visitedBehavior.getInheritedBehaviors()).isEmpty()) {
            error("Unknown self action: " + statement.getMessage(), statement);
          }
        } else {
          executionType = target.executionType();
          actionAccumulators.get(pending.context().action).localCallees.add(target.name());
        }
      } else if (!isImported(recipient)
          && !pending.context().knownVariables.containsKey(recipient)) {
        error("Undeclared verb recipient: " + recipient, statement);
      }
      if (executionType == null) {
        executionType =
            pending.context().validator.classifyActionCall(statement, pending.context());
      }
      var variable = pending.context().knownVariables.get(recipient);
      if (executionType == null) {
        if (variable != null && variable.producerCall() != null) {
          executionType =
              pending
                  .context()
                  .validator
                  .classifyActionCallFromProducer(
                      statement, variable.producerCall(), pending.context());
        }
      }
      if (executionType == null) {
        if (pending.context().action != null) {
          actionAccumulators.get(pending.context().action).callsUnknownActions = true;
        }
        if (pending.context().validator.warnAboutUnknownActionCall(statement, pending.context())) {
          warning(
              "Cannot establish execution type for " + recipient + "." + statement.getMessage(),
              statement);
        }
      }
      if (executionType != null || (variable != null && variable.agentUrn() != null)) {
        addNotifications(
            pending.context().validator.validateVerbCall(statement, pending.context()));
        addNotifications(
            pending
                .context()
                .validator
                .validateArguments(statement, statement.getArguments(), pending.context()));
      }
      if (executionType != null
          && executionType != Verb.Type.FUNCTION
          && pending.context().action != null) {
        actionAccumulators.get(pending.context().action).calledActionTypes.add(executionType);
      }
      if (executionType == Verb.Type.FUNCTION && !safe(statement.getActions()).isEmpty()) {
        error("Function calls cannot declare match actions", statement);
      }
      if (executionType == Verb.Type.EMITTER && pending.valueRequired()) {
        error("Emitter calls cannot be used where a value is required", statement);
      }
      calls.add(
          new CallInfo(
              statement,
              recipient,
              statement.getMessage(),
              pending.context().action == null ? null : pending.context().action.getUrn(),
              statement.getArguments(),
              pending.context().knownVariables,
              executionType,
              pending.valueRequired()));
    }
  }

  private void finishCalledActionTypes() {
    boolean changed;
    do {
      changed = false;
      for (var action : actions.values()) {
        var accumulator = actionAccumulators.get(action.statement());
        for (var calleeName : accumulator.localCallees) {
          var callee = actions.get(calleeName);
          if (callee == null) {
            continue;
          }
          if (callee.executionType() != Verb.Type.FUNCTION) {
            changed |= accumulator.calledActionTypes.add(callee.executionType());
          }
          changed |=
              accumulator.calledActionTypes.addAll(
                  actionAccumulators.get(callee.statement()).calledActionTypes);
          if (actionAccumulators.get(callee.statement()).callsUnknownActions
              && !accumulator.callsUnknownActions) {
            accumulator.callsUnknownActions = true;
            changed = true;
          }
        }
      }
    } while (changed);

    for (var entry : actions.entrySet()) {
      var action = entry.getValue();
      var accumulator = actionAccumulators.get(action.statement());
      var completed =
          new ActionInfo(
              action.statement(),
              action.name(),
              action.executionType(),
              action.parameters(),
              action.returns(),
              action.fires(),
              accumulator.calledActionTypes,
              accumulator.callsUnknownActions);
      completed.statement().setActionType(completed.effectiveExecutionType());
      entry.setValue(completed);
    }
  }

  private boolean isImported(String recipient) {
    return imports.stream().anyMatch(info -> Objects.equals(info.name(), recipient));
  }

  private boolean isValuePosition(KActorsContext context) {
    if (context.upstream.size() < 2) {
      return false;
    }
    var parent = context.upstream.get(context.upstream.size() - 2);
    return parent instanceof KActorsStatement.Assignment
        || parent instanceof KActorsStatement.If
        || parent instanceof KActorsStatement.While
        || parent instanceof KActorsStatement.Do
        || parent instanceof KActorsStatement.For
        || parent instanceof KActorsStatement.Return
        || parent instanceof KActorsStatement.Fire;
  }

  private VariableInfo variableFor(KActorsStatement.Assignment assignment) {
    var adapted = assignmentVariables.get(assignment);
    if (adapted != null) {
      return adapted;
    }
    return unadaptedVariableFor(assignment, null);
  }

  private VariableInfo unadaptedVariableFor(
      KActorsStatement.Assignment assignment, KActorsContext context) {
    if (assignment.getValue() != null) {
      if (context != null && assignment.getValue().getType() == ValueType.IDENTIFIER) {
        var identifier = assignment.getValue().getValue(String.class);
        var source = context.knownVariables.get(identifier);
        if (source != null) {
          return new VariableInfo(
              assignment,
              assignment.getVariable(),
              source.type(),
              source.agentUrn(),
              source.verbUrn(),
              source.producerCall());
        }
      }
      return new VariableInfo(
          assignment.getValue(),
          assignment.getVariable(),
          assignment.getValue().getType(),
          null,
          null,
          null);
    }
    var function = assignment.getFunction();
    return new VariableInfo(
        assignment,
        assignment.getVariable(),
        null,
        function == null ? null : normalizeRecipient(function.getRecipient()),
        function == null ? null : function.getMessage(),
        function);
  }

  private String normalized(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private void validateAlternative(
      Object value, Object function, String role, KActorsCodeStatement statement) {
    if ((value == null) == (function == null)) {
      error("Exactly one " + role + " or functional verb must be supplied", statement);
    }
  }

  private void validateBooleanValue(KActorsValue value, String role) {
    if (value != null
        && value.getType() != ValueType.BOOLEAN
        && value.getType() != ValueType.EXPRESSION
        && value.getType() != ValueType.IDENTIFIER) {
      error("The " + role + " must evaluate to boolean", value);
    }
  }

  private void validateIterableValue(KActorsValue value, KActorsCodeStatement statement) {
    if (value == null) {
      return;
    }
    var type = value.getType();
    if (type != ValueType.LIST
        && type != ValueType.SET
        && type != ValueType.MAP
        && type != ValueType.RANGE
        && type != ValueType.EXPRESSION
        && type != ValueType.IDENTIFIER) {
      error("The for iterable must evaluate to an Iterable", statement);
    }
  }

  private boolean hasMatchActions(KActorsStatement statement) {
    if (statement == null) {
      return false;
    }
    if (statement instanceof KActorsStatement.Verb verb) {
      return !safe(verb.getActions()).isEmpty();
    }
    var found = new java.util.concurrent.atomic.AtomicBoolean();
    forEachChild(
        statement,
        child -> {
          if (!found.get() && hasMatchActions(child)) {
            found.set(true);
          }
        });
    return found.get();
  }

  private void forEachChild(
      KActorsStatement statement, java.util.function.Consumer<KActorsStatement> consumer) {
    switch (statement) {
      case KActorsStatement.Group group -> safe(group.getStatements()).forEach(consumer);
      case KActorsStatement.If conditional -> {
        consumer.accept(conditional.getFunction());
        consumer.accept(conditional.getThenBody());
        for (var elseIf : safe(conditional.getElseIfs())) {
          if (elseIf != null && elseIf.getFirst() != null) {
            consumer.accept(elseIf.getFirst().getSecond());
            consumer.accept(elseIf.getSecond());
          }
        }
        consumer.accept(conditional.getElseBody());
      }
      case KActorsStatement.While loop -> {
        consumer.accept(loop.getFunction());
        consumer.accept(loop.getBody());
      }
      case KActorsStatement.Do loop -> {
        consumer.accept(loop.getBody());
        consumer.accept(loop.getFunction());
      }
      case KActorsStatement.For loop -> {
        consumer.accept(loop.getFunction());
        consumer.accept(loop.getBody());
      }
      case KActorsStatement.Assignment assignment -> consumer.accept(assignment.getFunction());
      case KActorsStatement.Fire fire -> consumer.accept(fire.getFunction());
      case KActorsStatement.Return returned -> consumer.accept(returned.getFunction());
      case KActorsStatement.Verb verb -> safe(verb.getActions()).forEach(consumer);
      case KActorsStatement.Verb.MatchAction match -> consumer.accept(match.getActionOnMatch());
      case KActorsStatement.Assert assertion -> safe(assertion.getAssertions()).forEach(consumer);
      case KActorsStatement.Assert.Assertion assertion ->
          safe(assertion.getCalls()).forEach(consumer);
      default -> {}
    }
  }

  private void visitAnnotations(List<Annotation> annotations, KActorsContext context) {
    for (var annotation : safe(annotations)) {
      visitAnnotation(annotation, context);
    }
  }

  private void visitValues(Object values, KActorsContext context) {
    visitValues(values, context, true);
  }

  private void visitMetadataValues(Object values, KActorsContext context) {
    visitValues(values, context, false);
  }

  private void visitValues(Object values, KActorsContext context, boolean resolveIdentifiers) {
    if (values instanceof KActorsValue value) {
      visitValue(value, context, resolveIdentifiers);
    } else if (values instanceof Map<?, ?> map) {
      map.values().forEach(value -> visitValues(value, context, resolveIdentifiers));
    } else if (values instanceof Iterable<?> iterable) {
      iterable.forEach(value -> visitValues(value, context, resolveIdentifiers));
    } else if (values instanceof Object[] array) {
      for (var value : array) {
        visitValues(value, context, resolveIdentifiers);
      }
    } else if (values instanceof Ternary ternary) {
      visitValues(ternary.getCondition(), context, resolveIdentifiers);
      visitValues(ternary.getTrueCase(), context, resolveIdentifiers);
      visitValues(ternary.getFalseCase(), context, resolveIdentifiers);
    }
  }

  private void visitValueIfPresent(KActorsValue value, KActorsContext context) {
    if (value != null) {
      visitValue(value, context);
    }
  }

  private Object valueOf(KActorsValue value) {
    try {
      return value.getValue(Object.class);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private void addNotifications(Collection<Notification> additions) {
    if (additions != null) {
      additions.stream().filter(Objects::nonNull).forEach(notifications::add);
    }
  }

  private void registerReferencedTags(String behaviorUrn, KActorsContext context) {
    if (behaviorUrn == null || behaviorUrn.isBlank()) {
      return;
    }
    for (var tag : safe(context.validator.getBehaviorTags(behaviorUrn, context))) {
      registerTag(tag, null, "referenced behavior " + behaviorUrn);
    }
  }

  private void registerTag(String tag, KActorsCodeStatement statement, String origin) {
    if (tag == null || tag.isBlank()) {
      return;
    }
    var normalized = tag.startsWith("#") ? tag.substring(1) : tag;
    if (normalized.isBlank()) {
      return;
    }
    var previous = tagDeclarations.putIfAbsent(normalized, origin);
    if (previous != null) {
      var message = "Duplicate tag #" + normalized + " (already declared in " + previous + ")";
      if (statement == null) {
        notifications.add(Notification.error(message));
      } else {
        error(message, statement);
      }
    }
  }

  private void error(String message, KActorsCodeStatement statement) {
    notifications.add(
        Notification.error(message, Notification.LexicalContext.of(statement, visitedBehavior)));
  }

  private void error(String message, KActorsBehavior behavior) {
    notifications.add(Notification.error(message));
  }

  private void warning(String message, KActorsCodeStatement statement) {
    notifications.add(
        Notification.warning(message, Notification.LexicalContext.of(statement, visitedBehavior)));
  }

  private KActorsBehavior visitedBehavior;

  private String normalizeRecipient(String recipient) {
    return recipient == null || recipient.isBlank() ? "self" : recipient;
  }

  private KActorsContext requireContext(KActorsContext context, String element) {
    return Objects.requireNonNull(context, "The " + element + " context factory returned null");
  }

  private static <T> List<T> safe(List<T> values) {
    return values == null ? List.of() : values;
  }

  public Map<String, ActionInfo> getActions() {
    return Collections.unmodifiableMap(actions);
  }

  public List<VariableInfo> getFields() {
    return List.copyOf(fields);
  }

  public List<Notification> getNotifications() {
    return List.copyOf(notifications);
  }

  public List<ImportInfo> getImports() {
    return List.copyOf(imports);
  }

  public List<CallInfo> getCalls() {
    return List.copyOf(calls);
  }

  public List<ExpressionInfo> getExpressions() {
    return List.copyOf(expressions);
  }
}
