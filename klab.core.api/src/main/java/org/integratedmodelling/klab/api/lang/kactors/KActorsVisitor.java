package org.integratedmodelling.klab.api.lang.kactors;

import java.util.*;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.collections.Identifier;
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

    /**
     * Establish whether the target action is static. This is independent of execution type:
     * functions, suppliers, and emitters may each be static or instance actions.
     *
     * @return {@code true} for an alias-callable static action, {@code false} for an instance
     *     action, or {@code null} when the target cannot be resolved
     */
    default Boolean classifyActionStaticity(KActorsStatement.Verb verb, KActorsContext context) {
      return null;
    }

    /**
     * Return the behavior URN implemented by an agent produced by this call.
     *
     * <p>This is independent of execution type and staticity. Returning {@code null} leaves the
     * result dynamically typed while retaining its producer-call provenance.
     */
    default String classifyActionResultBehavior(
        KActorsStatement.Verb verb, KActorsContext context) {
      return null;
    }

    /**
     * Return the Java runtime class produced by this call when it is known from a Java method
     * signature. This lets subsequent calls on the result be validated and compiled as ordinary
     * Java object operations without requiring the result to be an agent.
     */
    default Class<?> classifyActionResultJavaClass(
        KActorsStatement.Verb verb, KActorsContext context) {
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
     * Validate one behavior named in the current behavior's {@code inherits} clause.
     * Implementations that can resolve runtime resources should verify both its existence and type
     * compatibility.
     */
    default List<Notification> validateInheritance(
        KActorsBehavior.Import inheritedBehaviorStatement, KActorsContext context) {
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

    /**
     * Return the custom message classes handled by a referenced behavior, including handlers
     * contributed transitively through inheritance. The visitor uses this information to diagnose
     * an unacknowledged local override of an inherited {@code @handle} contract.
     */
    default List<String> getHandledMessageClasses(String behaviorUrn, KActorsContext context) {
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

    /**
     * Validate behavior adaptation in any value-bearing statement. The assignment-specific overload
     * is retained for compatibility and is called by default for assignments.
     */
    default List<Notification> validateAdaptation(
        KActorsCodeStatement statement,
        String behaviorUrn,
        VariableInfo sourceVariable,
        KActorsContext context) {
      return statement instanceof KActorsStatement.Assignment assignment
          ? validateAdaptation(assignment, behaviorUrn, sourceVariable, context)
          : List.of();
    }

    /** Validate that the result of a behavior adaptation can be consumed as a condition. */
    default List<Notification> validateBooleanAdaptation(
        KActorsCodeStatement statement,
        String behaviorUrn,
        VariableInfo sourceVariable,
        KActorsContext context) {
      return List.of();
    }

    /** Validate that the result of a behavior adaptation can be consumed as an iterable. */
    default List<Notification> validateIterableAdaptation(
        KActorsCodeStatement statement,
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
      List<Annotation> annotations,
      Verb.Type executionType,
      List<VariableInfo> parameters,
      int returns,
      int fires,
      Set<Verb.Type> calledActionTypes,
      boolean callsUnknownActions) {
    public ActionInfo {
      annotations = List.copyOf(annotations);
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
      Boolean staticAction,
      String producedAgentUrn,
      boolean valueRequired,
      java.lang.reflect.Method javaMethod) {
    public CallInfo {
      knownVariables = Collections.unmodifiableMap(new LinkedHashMap<>(knownVariables));
    }

    public CallInfo(
        KActorsStatement.Verb statement,
        String agent,
        String verb,
        String action,
        Parameters<String> arguments,
        Map<String, VariableInfo> knownVariables,
        Verb.Type executionType,
        Boolean staticAction,
        String producedAgentUrn,
        boolean valueRequired) {
      this(
          statement,
          agent,
          verb,
          action,
          arguments,
          knownVariables,
          executionType,
          staticAction,
          producedAgentUrn,
          valueRequired,
          null);
    }
  }

  public record VariableInfo(
      KActorsCodeStatement statement,
      String name,
      ValueType type,
      String agentUrn,
      String verbUrn,
      KActorsStatement.Verb producerCall,
      Class<?> javaClass) {

    public VariableInfo(
        KActorsCodeStatement statement,
        String name,
        ValueType type,
        String agentUrn,
        String verbUrn,
        KActorsStatement.Verb producerCall) {
      this(statement, name, type, agentUrn, verbUrn, producerCall, null);
    }
  }

  /** Effective type contract declared by {@code @type} on a k.Actors action parameter. */
  public record ArgumentType(String behaviorUrn, String javaClassName) {}

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
  private final Set<KActorsStatement> explicitValueStatements =
      Collections.newSetFromMap(new IdentityHashMap<>());

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
    validateAdaptActions();
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
    explicitValueStatements.clear();
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
      addNotifications(context.validator.validateInheritance(inheritedBehavior, context));
      registerReferencedTags(inheritedBehavior.getImportedBehavior(), context);
    }

    var inheritedMessageClasses = new LinkedHashSet<String>();
    for (var inheritedBehavior : safe(behavior.getInheritedBehaviors())) {
      inheritedMessageClasses.addAll(
          safe(
              context.validator.getHandledMessageClasses(
                  inheritedBehavior.getImportedBehavior(), context)));
    }

    for (var action : safe(behavior.getStatements())) {
      var previous = actionDeclarations.putIfAbsent(action.getUrn(), action);
      if (previous != null) {
        error("Duplicate action: " + action.getUrn(), action);
      }
      boolean acknowledgesOverride =
          safe(action.getAnnotations()).stream()
              .anyMatch(annotation -> "override".equals(annotation.getName()));
      if (!acknowledgesOverride) {
        for (var annotation : safe(action.getAnnotations())) {
          if (!"handle".equals(annotation.getName())) {
            continue;
          }
          String messageClass = handledMessageClass(annotation);
          if (messageClass != null && inheritedMessageClasses.contains(messageClass)) {
            warning(
                "Action "
                    + action.getUrn()
                    + " overrides the inherited @handle("
                    + messageClass
                    + ") contract; add @override to acknowledge it",
                action);
          }
        }
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

  /**
   * Extract the constant identifying a custom agent message from a {@code @handle} annotation. Both
   * the named {@code class} parameter and the main unnamed/value parameter are supported.
   */
  public static String handledMessageClass(Annotation annotation) {
    if (annotation == null || !"handle".equals(annotation.getName())) {
      return null;
    }
    Object declared =
        annotation.containsKey("class")
            ? annotation.get("class")
            : annotation.getUnnamedArguments().isEmpty()
                ? annotation.get(Annotation.VALUE_PARAMETER_KEY)
                : annotation.getUnnamedArguments().getFirst();
    if (declared == null) {
      declared =
          annotation.entrySet().stream()
              .filter(entry -> String.valueOf(entry.getKey()).matches("_p\\d+"))
              .sorted(
                  java.util.Comparator.comparingInt(
                      entry -> Integer.parseInt(String.valueOf(entry.getKey()).substring(2))))
              .map(java.util.Map.Entry::getValue)
              .findFirst()
              .orElse(null);
    }
    return handledMessageConstant(declared);
  }

  private static String handledMessageConstant(Object declared) {
    if (declared instanceof Constant constant) {
      return constant.getValue();
    }
    if (declared instanceof KActorsValue value && value.getType() == ValueType.CONSTANT) {
      Object constant = value.getValue(Object.class);
      return handledMessageConstant(constant);
    }
    if (declared instanceof java.util.Map<?, ?> map) {
      return handledMessageConstant(map.get("value"));
    }
    if (declared instanceof CharSequence text
        && text.toString().matches("[A-Z][A-Z0-9_]*(?:\\.[A-Z][A-Z0-9_]*)*")) {
      /*
       * Compatibility with transports and syntax-bean versions that retain the lexical constant
       * but erase its small Constant wrapper. Quoted strings never take this route in the normal
       * adapter; accepting only the grammar's uppercase CONSTANT form keeps the fallback narrow.
       */
      return text.toString();
    }
    return null;
  }

  /**
   * Return the behavior URN declared by an action's {@code @return} annotation.
   *
   * <p>Both {@code @return(behavior.urn)} and {@code @return(urn=behavior.urn)} are supported.
   */
  public static String returnedBehaviorUrn(KActorsAction action) {
    if (action == null) {
      return null;
    }
    var annotation =
        safe(action.getAnnotations()).stream()
            .filter(candidate -> "return".equals(candidate.getName()))
            .findFirst()
            .orElse(null);
    if (annotation == null) {
      return null;
    }
    Object declared =
        annotation.containsKey("urn")
            ? annotation.get("urn")
            : annotation.getUnnamedArguments().isEmpty()
                ? annotation.get(Annotation.VALUE_PARAMETER_KEY)
                : annotation.getUnnamedArguments().getFirst();
    return annotationBehaviorUrn(declared);
  }

  private static String annotationBehaviorUrn(Object declared) {
    if (declared == null) {
      return null;
    }
    if (declared instanceof KActorsValue value) {
      try {
        declared = value.getValue(Object.class);
      } catch (RuntimeException ignored) {
        return null;
      }
    }
    String urn =
        switch (declared) {
          case Identifier identifier -> identifier.getValue();
          case CharSequence text -> text.toString();
          default -> null;
        };
    return urn == null || urn.isBlank() ? null : urn.trim();
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
    var returnAnnotations =
        safe(action.getAnnotations()).stream()
            .filter(annotation -> "return".equals(annotation.getName()))
            .toList();
    if (returnAnnotations.size() > 1) {
      error("An action can declare only one @return annotation", action);
    } else if (!returnAnnotations.isEmpty() && returnedBehaviorUrn(action) == null) {
      error(
          "The @return annotation must declare a behavior URN as its unnamed or urn parameter",
          action);
    }
    visitAnnotations(action.getAnnotations(), context);
    visitMetadataValues(action.getMetadata(), context);
    var parameters = new ArrayList<VariableInfo>();
    var names = new HashSet<String>();
    for (var argument : safe(action.getArguments())) {
      var name = argument.getName();
      if (!names.add(name)) {
        error("Duplicate action parameter: " + name, action);
      }
      var type = actionArgumentType(argument);
      if (argument.getAnnotation() != null) {
        visitAnnotation(argument.getAnnotation(), context);
      }
      if (argument.getAnnotation() != null && "type".equals(argument.getAnnotation().getName())) {
        if (type == null) {
          error(
              "The @type annotation on action parameter "
                  + name
                  + " must declare either a behavior URN as its unnamed or urn string parameter, "
                  + "or a Java class name as its class string parameter",
              action);
        } else if (type.behaviorUrn() != null && type.javaClassName() != null) {
          error(
              "The @type annotation on action parameter "
                  + name
                  + " cannot declare both urn and class",
              action);
        } else if (type.javaClassName() != null && !isValidJavaTypeName(type.javaClassName())) {
          error(
              "The Java class in @type for action parameter "
                  + name
                  + " must be a CamelCase simple name or a canonical class name",
              action);
        }
      }
      var parameter =
          new VariableInfo(
              action,
              name,
              null,
              type == null ? null : type.behaviorUrn(),
              null,
              null,
              type == null ? null : loadDeclaredJavaClass(type.javaClassName()));
      parameters.add(parameter);
      context.knownVariables.put(name, parameter);
    }
    actionAccumulators.put(action, new ActionAccumulator());
    visitBlock(action.getCode(), context);
    actions.put(
        action.getUrn(),
        new ActionInfo(
            action,
            action.getUrn(),
            safe(action.getAnnotations()),
            Verb.Type.FUNCTION,
            parameters,
            0,
            0,
            Set.of(),
            false));
  }

  public void visitAnnotation(Annotation annotation, KActorsContext context) {
    visitValues(annotation, context);
  }

  /**
   * Decode the {@code @type} contract attached to a formal k.Actors action parameter.
   *
   * <p>An unnamed value and {@code urn=} both denote a behavior URN. Java types are accepted only
   * through the explicit {@code class=} key so a simple Java name cannot be confused with a
   * behavior identifier.
   */
  public static ArgumentType actionArgumentType(KActorsAction.Argument argument) {
    if (argument == null
        || argument.getAnnotation() == null
        || !"type".equals(argument.getAnnotation().getName())) {
      return null;
    }
    var annotation = argument.getAnnotation();
    Object unnamed =
        annotation.getUnnamedArguments().isEmpty()
            ? annotation.get(Annotation.VALUE_PARAMETER_KEY)
            : annotation.getUnnamedArguments().getFirst();
    String behaviorUrn = strictAnnotationString(annotation.get("urn"));
    if (behaviorUrn == null) {
      behaviorUrn = strictAnnotationString(unnamed);
    }
    String javaClassName = strictAnnotationString(annotation.get("class"));
    if (behaviorUrn == null && javaClassName == null) {
      return null;
    }
    return new ArgumentType(behaviorUrn, javaClassName);
  }

  private static String strictAnnotationString(Object declared) {
    if (declared instanceof KActorsValue value) {
      if (value.getType() != ValueType.STRING) {
        return null;
      }
      declared = value.getValue(Object.class);
    }
    if (!(declared instanceof CharSequence text)) {
      return null;
    }
    String ret = text.toString().trim();
    return ret.isEmpty() ? null : ret;
  }

  private static boolean isValidJavaTypeName(String name) {
    if (name == null || name.isBlank()) {
      return false;
    }
    String[] parts = name.split("\\.");
    for (int partIndex = 0; partIndex < parts.length; partIndex++) {
      String part = parts[partIndex];
      if (part.isEmpty() || !Character.isJavaIdentifierStart(part.charAt(0))) {
        return false;
      }
      for (int i = 1; i < part.length(); i++) {
        if (!Character.isJavaIdentifierPart(part.charAt(i))) {
          return false;
        }
      }
      if (parts.length > 1
          && partIndex == parts.length - 1
          && !Character.isUpperCase(part.charAt(0))) {
        return false;
      }
    }
    return true;
  }

  private static Class<?> loadDeclaredJavaClass(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    if (!name.contains(".")) {
      return switch (name.toLowerCase(Locale.ROOT)) {
        case "boolean" -> Boolean.class;
        case "byte" -> Byte.class;
        case "short" -> Short.class;
        case "integer", "int" -> Integer.class;
        case "long" -> Long.class;
        case "float" -> Float.class;
        case "double", "number" -> Double.class;
        case "character", "char" -> Character.class;
        case "string", "text" -> String.class;
        case "list", "arraylist" -> ArrayList.class;
        case "set", "linkedhashset" -> LinkedHashSet.class;
        case "map", "linkedhashmap" -> LinkedHashMap.class;
        default -> null;
      };
    }
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException ignored) {
      return null;
    }
  }

  private Class<?> javaClassForValue(KActorsValue value) {
    if (value == null) {
      return null;
    }
    return switch (value.getType()) {
      case LIST -> ArrayList.class;
      case SET -> LinkedHashSet.class;
      case MAP -> LinkedHashMap.class;
      case STRING, LOCALIZED_KEY -> String.class;
      case BOOLEAN -> Boolean.class;
      case INTEGER, NUMBERED_PATTERN -> Integer.class;
      case DOUBLE -> Double.class;
      case NUMBER -> {
        Object raw = valueOf(value);
        yield raw instanceof Number ? raw.getClass() : Number.class;
      }
      case REGEXP -> java.util.regex.Pattern.class;
      default -> {
        Object raw = valueOf(value);
        yield raw == null ? null : raw.getClass();
      }
    };
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
      case KActorsStatement.Switch switchStatement -> visitSwitch(switchStatement, context);
      case KActorsStatement.Yield yieldStatement -> visitYield(yieldStatement, context);
      case KActorsStatement.Return returnStatement -> visitReturn(returnStatement, context);
      default -> error("Unsupported statement type: " + statement.getClass().getName(), statement);
    }
  }

  protected void visitMatch(
      KActorsStatement.Verb.MatchAction matchStatement, KActorsContext context) {
    var localVariables = new ArrayList<VariableInfo>();
    var upstreamVerb = context.getUpstreamStatement(KActorsStatement.Verb.class);
    var verb = upstreamVerb == null ? null : upstreamVerb.getMessage();
    var producedAgentUrn = upstreamVerb == null ? null : producedAgentUrn(upstreamVerb, context);
    var producedJavaMethod = resolveJavaObjectMethod(upstreamVerb, context);
    var producedJavaClass =
        upstreamVerb == null
            ? null
            : producedJavaMethod == null
                ? context.validator.classifyActionResultJavaClass(upstreamVerb, context)
                : boxed(producedJavaMethod.getReturnType());
    var matchVariables = safe(matchStatement.getVariables());
    for (var name : matchVariables) {
      localVariables.add(
          new VariableInfo(
              matchStatement,
              name,
              null,
              matchVariables.size() == 1 ? producedAgentUrn : null,
              verb,
              upstreamVerb,
              matchVariables.size() == 1 ? producedJavaClass : null));
    }
    if (matchStatement.getCaptureAs() != null) {
      localVariables.add(
          new VariableInfo(
              matchStatement,
              matchStatement.getCaptureAs(),
              null,
              producedAgentUrn,
              verb,
              upstreamVerb,
              producedJavaClass));
    }
    var criterion = matchStatement.getMatchCriterion();
    if (criterion != null) {
      if (criterion.getType() == ValueType.IDENTIFIER) {
        var raw = valueOf(criterion);
        var values = raw instanceof Collection<?> collection ? collection : List.of(raw);
        for (var value : values) {
          if (value != null) {
            localVariables.add(
                new VariableInfo(
                    criterion,
                    value.toString(),
                    null,
                    producedAgentUrn,
                    verb,
                    upstreamVerb,
                    producedJavaClass));
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
        && raw instanceof Ternary ternary) {
      if (ternary.getCondition() instanceof KActorsValue condition) {
        validateBooleanValue(condition, "ternary condition");
      } else {
        error("A ternary condition must be a k.Actors value", value);
      }
      validateTernaryBranch(ternary.getTrueCase(), "true", value);
      validateTernaryBranch(ternary.getFalseCase(), "false", value);
    }
    visitValues(raw, context, resolveIdentifiers);
  }

  protected void visitDo(KActorsStatement.Do statement, KActorsContext context) {
    validateAlternative(
        statement.getCondition(), statement.getFunction(), "do condition", statement);
    visitNested(statement.getBody(), context, List.of(), true);
    visitValueIfPresent(statement.getCondition(), context);
    visitVerbAsValue(statement.getFunction(), context);
    validateConditionAdaptation(
        statement,
        statement.getCondition(),
        statement.getFunction(),
        statement.getAdaptedBehaviorUrn(),
        "do condition",
        context);
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
    validateAlternative(
        statement.getValue(),
        statement.getFunction(),
        statement.getSwitch(),
        "fire value",
        statement);
    var accumulator = actionAccumulators.get(context.action);
    if (accumulator != null) {
      accumulator.fires++;
    }
    visitValueIfPresent(statement.getValue(), context);
    visitVerbAsValue(statement.getFunction(), context);
    visitNested(statement.getSwitch(), context, List.of(), false);
    validateBehaviorAdaptation(
        statement,
        statement.getValue(),
        statement.getFunction(),
        statement.getSwitch(),
        statement.getAdaptedBehaviorUrn(),
        context,
        null);
  }

  protected void visitIf(KActorsStatement.If statement, KActorsContext context) {
    validateAlternative(
        statement.getCondition(), statement.getFunction(), "if condition", statement);
    visitValueIfPresent(statement.getCondition(), context);
    visitVerbAsValue(statement.getFunction(), context);
    validateConditionAdaptation(
        statement,
        statement.getCondition(),
        statement.getFunction(),
        statement.getAdaptedBehaviorUrn(),
        "if condition",
        context);
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
      validateConditionAdaptation(
          statement,
          condition.getFirst(),
          condition.getSecond(),
          condition.getThird(),
          "else-if condition",
          context);
      visitNested(elseIf.getSecond(), context, List.of(), false);
    }
    visitNested(statement.getElseBody(), context, List.of(), false);
  }

  protected void visitWhile(KActorsStatement.While statement, KActorsContext context) {
    validateAlternative(
        statement.getCondition(), statement.getFunction(), "while condition", statement);
    visitValueIfPresent(statement.getCondition(), context);
    visitVerbAsValue(statement.getFunction(), context);
    validateConditionAdaptation(
        statement,
        statement.getCondition(),
        statement.getFunction(),
        statement.getAdaptedBehaviorUrn(),
        "while condition",
        context);
    visitNested(statement.getBody(), context, List.of(), true);
  }

  protected void visitFor(KActorsStatement.For statement, KActorsContext context) {
    validateAlternative(
        statement.getIterable(), statement.getFunction(), "for iterable", statement);
    visitValueIfPresent(statement.getIterable(), context);
    visitVerbAsValue(statement.getFunction(), context);
    String adaptedBehaviorUrn = normalized(statement.getAdaptedBehaviorUrn());
    if (adaptedBehaviorUrn == null) {
      validateIterableValue(statement.getIterable(), statement);
    } else {
      validateBehaviorAdaptation(
          statement,
          statement.getIterable(),
          statement.getFunction(),
          null,
          adaptedBehaviorUrn,
          context,
          AdaptedUse.ITERABLE);
    }
    var loopVariables = new ArrayList<VariableInfo>();
    if (statement.getVariable() != null && !statement.getVariable().isBlank()) {
      loopVariables.add(
          new VariableInfo(
              statement,
              statement.getVariable(),
              null,
              statement.getFunction() == null
                  ? null
                  : producedAgentUrn(statement.getFunction(), context),
              statement.getFunction() == null ? null : statement.getFunction().getMessage(),
              statement.getFunction(),
              javaIterableElementClass(statement.getFunction(), context)));
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
        statement.getValue(),
        statement.getFunction(),
        statement.getSwitch(),
        "assignment value",
        statement);
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
    String adaptedBehaviorUrn = normalized(statement.getAdaptedBehaviorUrn());
    if (adaptedBehaviorUrn != null) {
      if (statement.getAssignmentScope() != KActorsStatement.Assignment.Scope.FRAME) {
        error("Behavior adaptation is only allowed on local frame assignments", statement);
      } else {
        var sourceVariable =
            sourceVariableFor(
                statement,
                statement.getValue(),
                statement.getFunction(),
                statement.getSwitch(),
                context);
        var adaptationNotifications =
            validateBehaviorAdaptation(
                statement,
                statement.getValue(),
                statement.getFunction(),
                statement.getSwitch(),
                adaptedBehaviorUrn,
                context,
                null);
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
    } else {
      assignmentVariables.put(statement, unadaptedVariableFor(statement, context));
    }
    visitValueIfPresent(statement.getValue(), context);
    visitVerbAsValue(statement.getFunction(), context);
    visitNested(statement.getSwitch(), context, List.of(), false);
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
    validateAlternative(
        statement.getValue(),
        statement.getFunction(),
        statement.getSwitch(),
        "return value",
        statement);
    visitValueIfPresent(statement.getValue(), context);
    visitVerbAsValue(statement.getFunction(), context);
    visitNested(statement.getSwitch(), context, List.of(), false);
    validateBehaviorAdaptation(
        statement,
        statement.getValue(),
        statement.getFunction(),
        statement.getSwitch(),
        statement.getAdaptedBehaviorUrn(),
        context,
        null);
  }

  protected void visitYield(KActorsStatement.Yield statement, KActorsContext context) {
    var owner = functionalYieldOwner(context);
    if (owner == null) {
      error("yield can only be used inside a switch or a verb match", statement);
    } else if (owner instanceof KActorsStatement.Verb) {
      var accumulator = actionAccumulators.get(context.action);
      if (accumulator != null) {
        accumulator.returns++;
        accumulator.reactiveReturns++;
      }
    }
    validateAlternative(
        statement.getValue(),
        statement.getFunction(),
        statement.getSwitch(),
        "yield value",
        statement);
    visitValueIfPresent(statement.getValue(), context);
    visitVerbAsValue(statement.getFunction(), context);
    visitNested(statement.getSwitch(), context, List.of(), false);
    validateBehaviorAdaptation(
        statement,
        statement.getValue(),
        statement.getFunction(),
        statement.getSwitch(),
        statement.getAdaptedBehaviorUrn(),
        context,
        null);
  }

  protected void visitSwitch(KActorsStatement.Switch statement, KActorsContext context) {
    validateAlternative(statement.getValue(), statement.getFunction(), "switch value", statement);
    visitValueIfPresent(statement.getValue(), context);
    visitVerbAsValue(statement.getFunction(), context);
    validateBehaviorAdaptation(
        statement,
        statement.getValue(),
        statement.getFunction(),
        null,
        statement.getAdaptedBehaviorUrn(),
        context,
        null);
    if (isSwitchValuePosition(context)
        && safe(statement.getCases()).stream()
            .map(KActorsStatement.Verb.MatchAction::getActionOnMatch)
            .noneMatch(this::containsYield)) {
      error("A switch used as a value must have at least one yield branch", statement);
    }
    for (var match : safe(statement.getCases())) {
      visitNested(match, context, List.of(), false);
    }
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
              old.annotations(),
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
      Boolean staticAction = null;
      java.lang.reflect.Method javaMethod = null;
      boolean unresolvedSelfAction = false;
      if ("self".equals(recipient)) {
        var target = actions.get(statement.getMessage());
        if (target == null) {
          unresolvedSelfAction = safe(visitedBehavior.getInheritedBehaviors()).isEmpty();
          executionType = RuntimeAgent.getCoreVerbType(statement.getMessage());
          if (executionType != null) {
            staticAction = false;
            unresolvedSelfAction = false;
          }
        } else {
          executionType = target.executionType();
          staticAction = target.statement().isStatic();
          actionAccumulators.get(pending.context().action).localCallees.add(target.name());
        }
      } else if (!isImported(recipient)
          && !pending.context().knownVariables.containsKey(recipient)) {
        error("Undeclared verb recipient: " + recipient, statement);
      }
      var variable = pending.context().knownVariables.get(recipient);
      if (executionType == null
          && variable != null
          && variable.agentUrn() == null
          && variable.javaClass() != null) {
        javaMethod = resolveJavaObjectMethod(statement, pending.context());
        if (javaMethod != null) {
          executionType =
              java.util.concurrent.CompletableFuture.class.isAssignableFrom(
                      javaMethod.getReturnType())
                  ? Verb.Type.SUPPLIER
                  : Verb.Type.FUNCTION;
          staticAction = false;
          if (pending.valueRequired()
              && (javaMethod.getReturnType() == void.class
                  || javaMethod.getReturnType() == Void.class)) {
            error(
                "Java method "
                    + javaMethod.getDeclaringClass().getSimpleName()
                    + "."
                    + javaMethod.getName()
                    + " does not return a value",
                statement);
          }
        }
      }
      if (executionType == null) {
        executionType =
            pending.context().validator.classifyActionCall(statement, pending.context());
      }
      if (staticAction == null) {
        staticAction =
            pending.context().validator.classifyActionStaticity(statement, pending.context());
      }
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
        if (unresolvedSelfAction) {
          error("Unknown self action: " + statement.getMessage(), statement);
        }
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
      if (isImported(recipient)
          && !Objects.equals("new", statement.getMessage())
          && Boolean.FALSE.equals(staticAction)) {
        error(
            "Non-static action "
                + statement.getMessage()
                + " must be invoked on an actor instance, not import alias "
                + recipient,
            statement);
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
              staticAction,
              producedAgentUrn(statement, pending.context()),
              pending.valueRequired(),
              javaMethod));
    }
  }

  private java.lang.reflect.Method resolveJavaObjectMethod(
      KActorsStatement.Verb call, KActorsContext context) {
    if (call == null || context == null) {
      return null;
    }
    var recipient = context.knownVariables.get(normalizeRecipient(call.getRecipient()));
    if (recipient == null || recipient.agentUrn() != null || recipient.javaClass() == null) {
      return null;
    }
    return resolveJavaObjectMethod(
        recipient.javaClass(), call.getMessage(), call.getArguments(), context);
  }

  private Class<?> javaIterableElementClass(
      KActorsStatement.Verb producer, KActorsContext context) {
    var method = resolveJavaObjectMethod(producer, context);
    if (method == null) {
      return null;
    }
    if (method.getReturnType().isArray()) {
      return boxed(method.getReturnType().getComponentType());
    }
    var generic = method.getGenericReturnType();
    if (generic instanceof java.lang.reflect.ParameterizedType parameterized
        && parameterized.getActualTypeArguments().length == 1
        && parameterized.getActualTypeArguments()[0] instanceof Class<?> elementClass) {
      return boxed(elementClass);
    }
    return null;
  }

  private java.lang.reflect.Method resolveJavaObjectMethod(
      Class<?> recipientClass,
      String requestedName,
      KActorsStatement.Arguments arguments,
      KActorsContext context) {
    if (recipientClass == null || requestedName == null || requestedName.isBlank()) {
      return null;
    }
    var supplied = argumentValues(arguments);
    String camelName = lowerUnderscoreToCamel(requestedName);
    var names = new LinkedHashMap<String, Integer>();
    names.put(requestedName, 40);
    names.put(camelName, 35);
    if (supplied.isEmpty()) {
      String property = Character.toUpperCase(camelName.charAt(0)) + camelName.substring(1);
      names.put("get" + property, 30);
      names.put("is" + property, 25);
    }

    java.lang.reflect.Method best = null;
    int bestScore = Integer.MIN_VALUE;
    boolean ambiguous = false;
    for (var method : recipientClass.getMethods()) {
      if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())
          || java.lang.reflect.Modifier.isStatic(method.getModifiers())
          || method.isBridge()
          || method.isSynthetic()
          || method.getDeclaringClass() == Object.class) {
        continue;
      }
      Integer nameScore = names.get(method.getName());
      if (nameScore == null) {
        continue;
      }
      int score = javaMethodCompatibilityScore(method, supplied, context);
      if (score < 0) {
        continue;
      }
      score += nameScore;
      if (score > bestScore) {
        best = method;
        bestScore = score;
        ambiguous = false;
      } else if (score == bestScore && !method.equals(best)) {
        ambiguous = true;
      }
    }
    return ambiguous ? null : best;
  }

  /** Return ordinary call arguments in source order, excluding inline metadata entries. */
  public static List<Object> argumentValues(KActorsStatement.Arguments arguments) {
    if (arguments == null) {
      return List.of();
    }
    var metadata =
        arguments.getMetadataKeys() == null
            ? Set.<String>of()
            : new HashSet<>(arguments.getMetadataKeys());
    return arguments.entrySet().stream()
        .filter(entry -> !metadata.contains(entry.getKey()))
        .map(Map.Entry::getValue)
        .toList();
  }

  private int javaMethodCompatibilityScore(
      java.lang.reflect.Method method, List<?> supplied, KActorsContext context) {
    var parameters = method.getParameterTypes();
    if ((!method.isVarArgs() && parameters.length != supplied.size())
        || (method.isVarArgs() && supplied.size() < parameters.length - 1)) {
      return -1;
    }
    int score = method.isVarArgs() ? -1 : 1;
    for (int index = 0; index < supplied.size(); index++) {
      Class<?> expected =
          method.isVarArgs() && index >= parameters.length - 1
              ? parameters[parameters.length - 1].getComponentType()
              : parameters[index];
      Class<?> actual = javaClassForArgument(supplied.get(index), context);
      if (actual == null) {
        continue;
      }
      Class<?> boxedExpected = boxed(expected);
      Class<?> boxedActual = boxed(actual);
      if (boxedExpected.equals(boxedActual)) {
        score += 4;
      } else if (boxedExpected.isAssignableFrom(boxedActual)) {
        score += 2;
      } else if (Number.class.isAssignableFrom(boxedExpected)
          && Number.class.isAssignableFrom(boxedActual)) {
        score += 1;
      } else if (boxedExpected == String.class) {
        score += 1;
      } else {
        return -1;
      }
    }
    return score;
  }

  private Class<?> javaClassForArgument(Object argument, KActorsContext context) {
    if (!(argument instanceof KActorsValue value)) {
      return argument == null ? null : argument.getClass();
    }
    if (value.getType() == ValueType.IDENTIFIER) {
      var variable = context.knownVariables.get(value.getValue(String.class));
      return variable == null ? null : variable.javaClass();
    }
    return javaClassForValue(value);
  }

  private static Class<?> boxed(Class<?> type) {
    if (type == null || !type.isPrimitive()) {
      return type;
    }
    if (type == boolean.class) return Boolean.class;
    if (type == byte.class) return Byte.class;
    if (type == short.class) return Short.class;
    if (type == int.class) return Integer.class;
    if (type == long.class) return Long.class;
    if (type == float.class) return Float.class;
    if (type == double.class) return Double.class;
    if (type == char.class) return Character.class;
    return type;
  }

  private static String lowerUnderscoreToCamel(String name) {
    var ret = new StringBuilder(name.length());
    boolean uppercase = false;
    for (int index = 0; index < name.length(); index++) {
      char c = name.charAt(index);
      if (c == '_') {
        uppercase = ret.length() > 0;
      } else if (uppercase) {
        ret.append(Character.toUpperCase(c));
        uppercase = false;
      } else {
        ret.append(c);
      }
    }
    return ret.toString();
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
              action.annotations(),
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

  private void validateAdaptActions() {
    var adapters =
        actions.values().stream()
            .filter(
                action ->
                    safe(action.statement().getAnnotations()).stream()
                        .anyMatch(annotation -> "adapt".equals(annotation.getName())))
            .toList();
    if (adapters.size() > 1) {
      for (var adapter : adapters) {
        error("A behavior can declare only one @adapt action", adapter.statement());
      }
      return;
    }
    if (adapters.isEmpty()) {
      return;
    }
    var adapter = adapters.getFirst();
    if (adapter.parameters().size() != 1) {
      error("An @adapt action must declare exactly one source parameter", adapter.statement());
    }
    if (adapter.effectiveExecutionType() == Verb.Type.EMITTER) {
      error("An @adapt action must be a function or supplier, not an emitter", adapter.statement());
    }
  }

  private boolean isImported(String recipient) {
    return imports.stream().anyMatch(info -> Objects.equals(info.name(), recipient));
  }

  private boolean isValuePosition(KActorsContext context) {
    return !context.upstream.isEmpty()
        && context.upstream.getLast() instanceof KActorsStatement.Verb verb
        && isValuePosition(context, verb);
  }

  private boolean isValuePosition(
      KActorsContext context, KActorsStatement.Verb candidate) {
    if (explicitValueStatements.contains(candidate)) {
      return true;
    }
    int index = context.upstream.lastIndexOf(candidate);
    return index > 0 && isValueChild(context.upstream.get(index - 1), candidate);
  }

  private boolean isValueChild(
      KActorsStatement parent, KActorsStatement.Verb candidate) {
    return switch (parent) {
      case KActorsStatement.Assignment assignment -> assignment.getFunction() == candidate;
      case KActorsStatement.Verb verb ->
          argumentValues(verb.getArguments()).stream()
              .anyMatch(
                  argument ->
                      argument == candidate
                          || argument instanceof KActorsStatement.CallArgument executable
                              && executable.getFunction() == candidate);
      case KActorsStatement.If conditional -> conditional.getFunction() == candidate;
      case KActorsStatement.While loop -> loop.getFunction() == candidate;
      case KActorsStatement.Do loop -> loop.getFunction() == candidate;
      case KActorsStatement.For loop -> loop.getFunction() == candidate;
      case KActorsStatement.Return returned -> returned.getFunction() == candidate;
      case KActorsStatement.Fire fired -> fired.getFunction() == candidate;
      case KActorsStatement.Yield yielded -> yielded.getFunction() == candidate;
      case KActorsStatement.Switch switched -> switched.getFunction() == candidate;
      default -> false;
    };
  }

  /**
   * Find the nearest construct that owns a yield. Match actions are shared by switches and verbs,
   * so their immediate lexical parent disambiguates the two cases.
   */
  private KActorsStatement functionalYieldOwner(KActorsContext context) {
    for (int i = context.upstream.size() - 2; i >= 0; i--) {
      var statement = context.upstream.get(i);
      if (statement instanceof KActorsStatement.Switch) {
        return statement;
      }
      if (statement instanceof KActorsStatement.Verb.MatchAction && i > 0) {
        var parent = context.upstream.get(i - 1);
        if (parent instanceof KActorsStatement.Switch
            || parent instanceof KActorsStatement.Verb) {
          return parent;
        }
      }
    }
    return null;
  }

  private boolean isSwitchValuePosition(KActorsContext context) {
    if (!context.upstream.isEmpty()
        && explicitValueStatements.contains(context.upstream.getLast())) {
      return true;
    }
    if (context.upstream.size() < 2) {
      return false;
    }
    var parent = context.upstream.get(context.upstream.size() - 2);
    return parent instanceof KActorsStatement.Assignment
        || parent instanceof KActorsStatement.Verb
        || parent instanceof KActorsStatement.Return
        || parent instanceof KActorsStatement.Fire
        || parent instanceof KActorsStatement.Yield;
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
              source.producerCall(),
              source.javaClass());
        }
      }
      return new VariableInfo(
          assignment.getValue(),
          assignment.getVariable(),
          assignment.getValue().getType(),
          null,
          null,
          null,
          javaClassForValue(assignment.getValue()));
    }
    var function = assignment.getFunction();
    var resultBehaviorUrn = function == null ? null : producedAgentUrn(function, context);
    if (resultBehaviorUrn == null && function != null && "new".equals(function.getMessage())) {
      resultBehaviorUrn = normalizeRecipient(function.getRecipient());
    }
    var javaMethod = resolveJavaObjectMethod(function, context);
    return new VariableInfo(
        assignment,
        assignment.getVariable(),
        null,
        resultBehaviorUrn,
        function == null ? null : function.getMessage(),
        function,
        javaMethod == null
            ? function == null
                ? null
                : context.validator.classifyActionResultJavaClass(function, context)
            : boxed(javaMethod.getReturnType()));
  }

  private VariableInfo sourceVariableFor(
      KActorsCodeStatement statement,
      KActorsValue value,
      KActorsStatement.Verb function,
      KActorsStatement.Switch switchStatement,
      KActorsContext context) {
    if (value != null) {
      if (context != null && value.getType() == ValueType.IDENTIFIER) {
        var source = context.knownVariables.get(value.getValue(String.class));
        if (source != null) {
          return new VariableInfo(
              statement,
              source.name(),
              source.type(),
              source.agentUrn(),
              source.verbUrn(),
              source.producerCall(),
              source.javaClass());
        }
      }
      return new VariableInfo(
          statement, null, value.getType(), null, null, null, javaClassForValue(value));
    }
    if (function != null) {
      var javaMethod = resolveJavaObjectMethod(function, context);
      return new VariableInfo(
          statement,
          null,
          null,
          producedAgentUrn(function, context),
          function.getMessage(),
          function,
          javaMethod == null
              ? context == null
                  ? null
                  : context.validator.classifyActionResultJavaClass(function, context)
              : boxed(javaMethod.getReturnType()));
    }
    return new VariableInfo(statement, null, null, null, null, null);
  }

  private String producedAgentUrn(KActorsStatement.Verb verb, KActorsContext context) {
    if (verb == null) {
      return null;
    }
    if ("self".equals(normalizeRecipient(verb.getRecipient()))) {
      var localAction = actionDeclarations.get(verb.getMessage());
      var localResult = returnedBehaviorUrn(localAction);
      if (localResult != null) {
        return localResult;
      }
    }
    if (context == null) {
      return null;
    }
    return normalized(context.validator.classifyActionResultBehavior(verb, context));
  }

  private enum AdaptedUse {
    BOOLEAN,
    ITERABLE
  }

  private void validateConditionAdaptation(
      KActorsCodeStatement statement,
      KActorsValue value,
      KActorsStatement.Verb function,
      String behaviorUrn,
      String role,
      KActorsContext context) {
    String normalizedUrn = normalized(behaviorUrn);
    if (normalizedUrn == null) {
      validateBooleanValue(value, role);
      return;
    }
    validateBehaviorAdaptation(
        statement, value, function, null, normalizedUrn, context, AdaptedUse.BOOLEAN);
  }

  private List<Notification> validateBehaviorAdaptation(
      KActorsCodeStatement statement,
      KActorsValue value,
      KActorsStatement.Verb function,
      KActorsStatement.Switch switchStatement,
      String behaviorUrn,
      KActorsContext context,
      AdaptedUse adaptedUse) {
    String normalizedUrn = normalized(behaviorUrn);
    if (normalizedUrn == null) {
      return List.of();
    }
    var source = sourceVariableFor(statement, value, function, switchStatement, context);
    var notifications =
        new ArrayList<>(
            safe(context.validator.validateAdaptation(statement, normalizedUrn, source, context)));
    boolean valid =
        notifications.stream()
            .noneMatch(
                notification ->
                    notification.getLevel().severity >= Notification.Level.Error.severity);
    if (valid && adaptedUse == AdaptedUse.BOOLEAN) {
      notifications.addAll(
          safe(
              context.validator.validateBooleanAdaptation(
                  statement, normalizedUrn, source, context)));
    } else if (valid && adaptedUse == AdaptedUse.ITERABLE) {
      notifications.addAll(
          safe(
              context.validator.validateIterableAdaptation(
                  statement, normalizedUrn, source, context)));
    }
    addNotifications(notifications);
    return List.copyOf(notifications);
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

  private void validateAlternative(
      Object value,
      Object function,
      Object switchStatement,
      String role,
      KActorsCodeStatement statement) {
    int supplied =
        (value == null ? 0 : 1) + (function == null ? 0 : 1) + (switchStatement == null ? 0 : 1);
    if (supplied != 1) {
      error("Exactly one " + role + ", functional verb, or switch must be supplied", statement);
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

  private boolean containsYield(KActorsStatement statement) {
    if (statement == null) {
      return false;
    }
    if (statement instanceof KActorsStatement.Yield) {
      return true;
    }
    // Nested switches and matched verbs own their own yields.
    if (statement instanceof KActorsStatement.Switch
        || statement instanceof KActorsStatement.Verb) {
      return false;
    }
    var found = new java.util.concurrent.atomic.AtomicBoolean();
    forEachChild(
        statement,
        child -> {
          if (!found.get() && containsYield(child)) {
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
      case KActorsStatement.Assignment assignment -> {
        consumer.accept(assignment.getFunction());
        consumer.accept(assignment.getSwitch());
      }
      case KActorsStatement.Fire fire -> {
        consumer.accept(fire.getFunction());
        consumer.accept(fire.getSwitch());
      }
      case KActorsStatement.Return returned -> {
        consumer.accept(returned.getFunction());
        consumer.accept(returned.getSwitch());
      }
      case KActorsStatement.Yield yielded -> {
        consumer.accept(yielded.getFunction());
        consumer.accept(yielded.getSwitch());
      }
      case KActorsStatement.Switch switchStatement -> {
        consumer.accept(switchStatement.getFunction());
        safe(switchStatement.getCases()).forEach(consumer);
      }
      case KActorsStatement.Verb verb -> {
        if (verb.getArguments() != null) {
          verb.getArguments().values().stream()
              .filter(KActorsStatement.CallArgument.class::isInstance)
              .map(KActorsStatement.CallArgument.class::cast)
              .forEach(
                  argument -> {
                    if (argument.getFunction() != null) {
                      consumer.accept(argument.getFunction());
                    }
                    if (argument.getSwitch() != null) {
                      consumer.accept(argument.getSwitch());
                    }
                  });
        }
        safe(verb.getActions()).forEach(consumer);
      }
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
    } else if (values instanceof KActorsStatement.Verb function) {
      explicitValueStatements.add(function);
      visitNested(function, context, List.of(), false);
    } else if (values instanceof KActorsStatement.Switch switchStatement) {
      explicitValueStatements.add(switchStatement);
      visitNested(switchStatement, context, List.of(), false);
    } else if (values instanceof KActorsStatement.CallArgument argument) {
      int alternatives =
          (argument.getFunction() == null ? 0 : 1) + (argument.getSwitch() == null ? 0 : 1);
      KActorsStatement lexical =
          argument.getFunction() == null ? argument.getSwitch() : argument.getFunction();
      if (alternatives != 1) {
        error(
            "Exactly one functional verb or switch must be supplied as a call argument", lexical);
      }
      visitNested(argument.getFunction(), context, List.of(), false);
      visitNested(argument.getSwitch(), context, List.of(), false);
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

  private void validateTernaryBranch(
      Object branch, String branchName, KActorsCodeStatement statement) {
    if (branch == null) {
      error("The " + branchName + " ternary branch must supply a value", statement);
    } else if (!(branch instanceof KActorsValue)
        && !(branch instanceof KActorsStatement.Verb)
        && !(branch instanceof KActorsStatement.Switch)) {
      error(
          "The "
              + branchName
              + " ternary branch must be a value, functional verb, or functional switch",
          statement);
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
