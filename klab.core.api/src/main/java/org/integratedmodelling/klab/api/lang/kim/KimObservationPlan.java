package org.integratedmodelling.klab.api.lang.kim;

import java.io.Serializable;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * Portable, unevaluated strategy selection/setup/plan model. Concrete implementations are plain
 * no-argument Java beans; interface serialization is registered in JacksonConfiguration in common.
 * No parser objects, callbacks, linked graphs, or Jackson annotations belong in this API.
 * Lists preserve source order and optional fields remain null. Scientific and lexical validation
 * follow adaptation; this model does not imply that a plan can already be executed.
 */
public interface KimObservationPlan {
  /** Portable source bookkeeping. URI is a string, and notifications use service API records. */
  interface Source extends Serializable {
    String getUri();
    /** Original fragment, which may include trivia outside the semantic token span below. */
    String getCode();
    int getOffset();
    int getLength();
    List<Notification> getNotifications();
  }

  interface Node extends Serializable {
    Source getSource();
    /** Contained plan nodes only, in declaration order. References never link to target nodes. */
    List<Node> children();
  }

  /** A graph symbol occurrence; lexical binding is a later pass, never an EMF cross-reference. */
  interface SymbolReference extends Node {
    String getName();
  }

  enum ProducerMode { OBSERVE, RESOLVE }

  enum CompositionKind { TRANSFORM, ATTRIBUTE, SELECT, LOGICAL_UNION, LOGICAL_INTERSECTION, MOSAIC, AGGREGATE }

  enum PatternKind { SUBJECT, AGENT, EVENT, PROCESS, RELATIONSHIP, CONFIGURATION, QUALITY, PREDICATE, ATTRIBUTE, IDENTITY, ROLE, REALM }

  enum PatternFlag { COLLECTIVE, ABSTRACT, NEGATED }

  enum PatternChild { HEAD, PREDICATES, ROLES, INHERENT, CLAUSES, SEMANTIC_OPERATOR, VALUE_OPERATORS }

  enum PatternBooleanMode { ALL, EITHER }

  enum CollectionQuantifier { CONTAINS, EVERY }

  enum SemanticMatchRelation { EXACT, IS }

  enum LogicalPatternConnector { AND, OR }

  enum OperandOrdering { CANONICAL, UNORDERED }

  enum OperatorSlotName { OPERAND, CONDITION, COMPARISON, PARAMETERS }

  /** Match alternatives are disjunctive; guards within each alternative are conjunctive. */
  interface StrategySelection extends Node {
    List<MatchAlternative> getAlternatives();
  }

  interface MatchAlternative extends Node {
    PatternBlock getPattern();
    ExternalMatcher getMatcher();
    KimObservable getObservable();
    List<StrategyCall> getGuards();
  }

  interface PatternBlock extends Node {
    StrategyVariable getSubject();
    PatternExpression getExpression();
  }

  interface ExternalMatcher extends Node {
    StrategyCall getCall();
  }

  interface StrategySetup extends Node {
  }

  interface LetSetup extends StrategySetup {
    List<SemanticBinding> getBindings();
  }

  /** Tuple destinations remain an ordered list, not a comma-encoded map key. */
  interface SemanticBinding extends Node {
    List<String> getNames();
    StrategyExpression getValue();
  }

  interface EnsureSetup extends StrategySetup {
    List<StrategyCall> getChecks();
  }

  interface StrategyExpression extends Node {
  }

  interface StrategyVariable extends StrategyExpression {
    String getSpelling();
  }

  /** Unevaluated call; preserve ordered/named arguments, including duplicates for validation. */
  interface StrategyCall extends StrategyExpression {
    String getFunction();
    List<StrategyArgument> getArguments();
  }

  interface StrategyArgument extends Node {
    String getName();
    StrategyExpression getValue();
  }

  /** A closed KimObservable, adapted through the common observable boundary. */
  interface ClosedObservable extends StrategyExpression {
    KimObservable getObservable();
  }

  /** Exactly one of text, number, or booleanValue is present; false and empty text are values. */
  interface StrategyScalar extends StrategyExpression {
    String getText();
    Number getNumber();
    Boolean getBooleanValue();
  }

  interface StrategyTarget extends Node {
    StrategyExpression getExpression();
    KimObservable getObservable();
  }

  /** Steps define lexical graph symbols. No graph has been resolved or compiled. */
  interface PlanBody extends Node {
    List<PlanStep> getSteps();
  }

  interface PlanStep extends Node {
  }

  interface GraphProducer extends PlanStep {
    ProducerMode getMode();
    StrategyTarget getTarget();
    StrategyVariable getContext();
    List<GraphInput> getInputs();
    StrategyExpression getFallback();
    /** Null for an unnamed terminal producer, whose result is returned implicitly. */
    String getName();
  }

  interface GraphInput extends Node {
    String getPort();
    SymbolReference getGraph();
  }

  interface GraphReference extends PlanStep {
    StrategyVariable getObservation();
    String getName();
  }

  /** Binary composition; a null name is a terminal result, otherwise it defines another graph. */
  interface GraphMerge extends PlanStep {
    SymbolReference getLeft();
    SymbolReference getRight();
    CompositionKind getOperator();
    List<StrategyArgument> getOptions();
    StrategyTarget getTarget();
    String getName();
  }

  interface ContextPlan extends PlanStep {
    SymbolReference getContextGraph();
    String getVariable();
    PlanBody getBody();
    String getName();
  }

  interface MemberPlan extends PlanStep {
    String getVariable();
    SymbolReference getCollection();
    PlanBody getBody();
    String getName();
  }

  interface PlanYield extends PlanStep {
    SymbolReference getGraph();
  }

  interface PatternExpression extends Node {
  }

  interface NodePattern extends PatternExpression {
    List<PatternField> getFields();
  }

  interface PatternField extends Node {
  }

  interface KindPatternField extends PatternField {
    KindConstraint getValue();
  }

  interface KindConstraint extends Node {
    List<PatternKind> getKinds();
  }

  interface ActivityPatternField extends PatternField {
    Contextualization getActivity();
  }

  interface FlagPatternField extends PatternField {
    PatternFlag getFlag();
    Boolean getValue();
  }

  interface ChildPatternField extends PatternField {
    PatternChild getChild();
    PatternExpression getValue();
  }

  interface BooleanPattern extends PatternExpression {
    PatternBooleanMode getMode();
    List<PatternExpression> getOperands();
  }

  interface NotPattern extends PatternExpression {
    PatternExpression getOperand();
  }

  interface CapturePattern extends PatternExpression {
    String getName();
    PatternExpression getOperand();
  }

  interface SamePattern extends PatternExpression {
    String getName();
  }

  interface PresencePattern extends PatternExpression {
    PatternExpression getOperand();
  }

  interface CollectionPattern extends PatternExpression {
    CollectionQuantifier getQuantifier();
    PatternExpression getElement();
  }

  interface ExactCollectionPattern extends PatternExpression {
    List<PatternExpression> getElements();
  }

  interface AnyPattern extends PatternExpression {
  }

  interface AbsentPattern extends PatternExpression {
  }

  interface ScalarPattern extends PatternExpression {
    StrategyScalar getValue();
  }

  interface SemanticPatternTest extends PatternExpression {
    SemanticMatchRelation getRelation();
    ClosedObservable getObservable();
  }

  /** Operand ordering is explicit; adaptation must not normalize or reorder operands. */
  interface LogicalPattern extends PatternExpression {
    LogicalPatternConnector getConnector();
    OperandOrdering getOrdering();
    List<PatternExpression> getOperands();
    RemainderCapture getRemainder();
  }

  interface RemainderCapture extends Node {
    String getName();
  }

  interface ValueOperatorPattern extends PatternExpression {
    String getOperator();
    List<OperatorPatternSlot> getSlots();
  }

  interface OperatorPatternSlot extends Node {
    OperatorSlotName getSlot();
    PatternExpression getPattern();
  }

}
