package org.integratedmodelling.klab.api.lang.kim.impl;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimObservationPlan.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Annotation-free beans for the portable observation plan interfaces. */
public final class KimObservationPlanImpl {
  private KimObservationPlanImpl() {}

  public static class SourceImpl implements Source {
    private String uri;
    private String code;
    private int offset = -1;
    private int length;
    private List<Notification> notifications = new ArrayList<>();
    public SourceImpl() {}
    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public int getOffset() { return offset; }
    public void setOffset(int offset) { this.offset = offset; }
    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }
    public List<Notification> getNotifications() { return notifications; }
    public void setNotifications(List<Notification> notifications) { this.notifications = notifications; }
  }

  public static abstract class NodeImpl implements Node {
    private Source source;
    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }
    protected static void add(List<Node> result, Node child) { if (child != null) result.add(child); }
    @Override public List<Node> children() { return List.of(); }
  }

  public static class SymbolReferenceImpl extends NodeImpl implements SymbolReference {
    private String name;
    public SymbolReferenceImpl() {}
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
  }

  public static class StrategySelectionImpl extends NodeImpl implements StrategySelection {
    private List<MatchAlternative> alternatives = new ArrayList<>();
    public StrategySelectionImpl() {}
    @Override public List<MatchAlternative> getAlternatives() { return alternatives; }
    public void setAlternatives(List<MatchAlternative> alternatives) { this.alternatives = alternatives; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      if (alternatives != null) result.addAll(alternatives);
      return result;
    }
  }

  public static class MatchAlternativeImpl extends NodeImpl implements MatchAlternative {
    private PatternBlock pattern;
    private ExternalMatcher matcher;
    private KimObservable observable;
    private List<StrategyCall> guards = new ArrayList<>();
    public MatchAlternativeImpl() {}
    @Override public PatternBlock getPattern() { return pattern; }
    public void setPattern(PatternBlock pattern) { this.pattern = pattern; }
    @Override public ExternalMatcher getMatcher() { return matcher; }
    public void setMatcher(ExternalMatcher matcher) { this.matcher = matcher; }
    @Override public KimObservable getObservable() { return observable; }
    public void setObservable(KimObservable observable) { this.observable = observable; }
    @Override public List<StrategyCall> getGuards() { return guards; }
    public void setGuards(List<StrategyCall> guards) { this.guards = guards; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, pattern);
      add(result, matcher);
      if (guards != null) result.addAll(guards);
      return result;
    }
  }

  public static class PatternBlockImpl extends NodeImpl implements PatternBlock {
    private StrategyVariable subject;
    private PatternExpression expression;
    public PatternBlockImpl() {}
    @Override public StrategyVariable getSubject() { return subject; }
    public void setSubject(StrategyVariable subject) { this.subject = subject; }
    @Override public PatternExpression getExpression() { return expression; }
    public void setExpression(PatternExpression expression) { this.expression = expression; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, subject);
      add(result, expression);
      return result;
    }
  }

  public static class ExternalMatcherImpl extends NodeImpl implements ExternalMatcher {
    private StrategyCall call;
    public ExternalMatcherImpl() {}
    @Override public StrategyCall getCall() { return call; }
    public void setCall(StrategyCall call) { this.call = call; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, call);
      return result;
    }
  }

  public static class LetSetupImpl extends NodeImpl implements LetSetup {
    private List<SemanticBinding> bindings = new ArrayList<>();
    public LetSetupImpl() {}
    @Override public List<SemanticBinding> getBindings() { return bindings; }
    public void setBindings(List<SemanticBinding> bindings) { this.bindings = bindings; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      if (bindings != null) result.addAll(bindings);
      return result;
    }
  }

  public static class SemanticBindingImpl extends NodeImpl implements SemanticBinding {
    private List<String> names = new ArrayList<>();
    private StrategyExpression value;
    public SemanticBindingImpl() {}
    @Override public List<String> getNames() { return names; }
    public void setNames(List<String> names) { this.names = names; }
    @Override public StrategyExpression getValue() { return value; }
    public void setValue(StrategyExpression value) { this.value = value; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, value);
      return result;
    }
  }

  public static class EnsureSetupImpl extends NodeImpl implements EnsureSetup {
    private List<StrategyCall> checks = new ArrayList<>();
    public EnsureSetupImpl() {}
    @Override public List<StrategyCall> getChecks() { return checks; }
    public void setChecks(List<StrategyCall> checks) { this.checks = checks; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      if (checks != null) result.addAll(checks);
      return result;
    }
  }

  public static class StrategyVariableImpl extends NodeImpl implements StrategyVariable {
    private String spelling;
    public StrategyVariableImpl() {}
    @Override public String getSpelling() { return spelling; }
    public void setSpelling(String spelling) { this.spelling = spelling; }
  }

  public static class StrategyCallImpl extends NodeImpl implements StrategyCall {
    private String function;
    private List<StrategyArgument> arguments = new ArrayList<>();
    public StrategyCallImpl() {}
    @Override public String getFunction() { return function; }
    public void setFunction(String function) { this.function = function; }
    @Override public List<StrategyArgument> getArguments() { return arguments; }
    public void setArguments(List<StrategyArgument> arguments) { this.arguments = arguments; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      if (arguments != null) result.addAll(arguments);
      return result;
    }
  }

  public static class StrategyArgumentImpl extends NodeImpl implements StrategyArgument {
    private String name;
    private StrategyExpression value;
    public StrategyArgumentImpl() {}
    @Override public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    @Override public StrategyExpression getValue() { return value; }
    public void setValue(StrategyExpression value) { this.value = value; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, value);
      return result;
    }
  }

  public static class ClosedObservableImpl extends NodeImpl implements ClosedObservable {
    private KimObservable observable;
    public ClosedObservableImpl() {}
    @Override public KimObservable getObservable() { return observable; }
    public void setObservable(KimObservable observable) { this.observable = observable; }
  }

  public static class StrategyScalarImpl extends NodeImpl implements StrategyScalar {
    private String text;
    private Number number;
    private Boolean booleanValue;
    public StrategyScalarImpl() {}
    @Override public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    @Override public Number getNumber() { return number; }
    public void setNumber(Number number) { this.number = number; }
    @Override public Boolean getBooleanValue() { return booleanValue; }
    public void setBooleanValue(Boolean booleanValue) { this.booleanValue = booleanValue; }
  }

  public static class StrategyTargetImpl extends NodeImpl implements StrategyTarget {
    private StrategyExpression expression;
    private KimObservable observable;
    public StrategyTargetImpl() {}
    @Override public StrategyExpression getExpression() { return expression; }
    public void setExpression(StrategyExpression expression) { this.expression = expression; }
    @Override public KimObservable getObservable() { return observable; }
    public void setObservable(KimObservable observable) { this.observable = observable; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, expression);
      return result;
    }
  }

  public static class PlanBodyImpl extends NodeImpl implements PlanBody {
    private List<PlanStep> steps = new ArrayList<>();
    public PlanBodyImpl() {}
    @Override public List<PlanStep> getSteps() { return steps; }
    public void setSteps(List<PlanStep> steps) { this.steps = steps; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      if (steps != null) result.addAll(steps);
      return result;
    }
  }

  public static class GraphProducerImpl extends NodeImpl implements GraphProducer {
    private ProducerMode mode;
    private StrategyTarget target;
    private StrategyVariable context;
    private List<GraphInput> inputs = new ArrayList<>();
    private StrategyExpression fallback;
    private String name;
    public GraphProducerImpl() {}
    @Override public ProducerMode getMode() { return mode; }
    public void setMode(ProducerMode mode) { this.mode = mode; }
    @Override public StrategyTarget getTarget() { return target; }
    public void setTarget(StrategyTarget target) { this.target = target; }
    @Override public StrategyVariable getContext() { return context; }
    public void setContext(StrategyVariable context) { this.context = context; }
    @Override public List<GraphInput> getInputs() { return inputs; }
    public void setInputs(List<GraphInput> inputs) { this.inputs = inputs; }
    @Override public StrategyExpression getFallback() { return fallback; }
    public void setFallback(StrategyExpression fallback) { this.fallback = fallback; }
    @Override public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, target);
      add(result, context);
      if (inputs != null) result.addAll(inputs);
      add(result, fallback);
      return result;
    }
  }

  public static class GraphInputImpl extends NodeImpl implements GraphInput {
    private String port;
    private SymbolReference graph;
    public GraphInputImpl() {}
    @Override public String getPort() { return port; }
    public void setPort(String port) { this.port = port; }
    @Override public SymbolReference getGraph() { return graph; }
    public void setGraph(SymbolReference graph) { this.graph = graph; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, graph);
      return result;
    }
  }

  public static class GraphReferenceImpl extends NodeImpl implements GraphReference {
    private StrategyVariable observation;
    private String name;
    public GraphReferenceImpl() {}
    @Override public StrategyVariable getObservation() { return observation; }
    public void setObservation(StrategyVariable observation) { this.observation = observation; }
    @Override public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, observation);
      return result;
    }
  }

  public static class GraphMergeImpl extends NodeImpl implements GraphMerge {
    private SymbolReference left;
    private SymbolReference right;
    private CompositionKind operator;
    private List<StrategyArgument> options = new ArrayList<>();
    private StrategyTarget target;
    private String name;
    public GraphMergeImpl() {}
    @Override public SymbolReference getLeft() { return left; }
    public void setLeft(SymbolReference left) { this.left = left; }
    @Override public SymbolReference getRight() { return right; }
    public void setRight(SymbolReference right) { this.right = right; }
    @Override public CompositionKind getOperator() { return operator; }
    public void setOperator(CompositionKind operator) { this.operator = operator; }
    @Override public List<StrategyArgument> getOptions() { return options; }
    public void setOptions(List<StrategyArgument> options) { this.options = options; }
    @Override public StrategyTarget getTarget() { return target; }
    public void setTarget(StrategyTarget target) { this.target = target; }
    @Override public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, left);
      add(result, right);
      if (options != null) result.addAll(options);
      add(result, target);
      return result;
    }
  }

  public static class ContextPlanImpl extends NodeImpl implements ContextPlan {
    private SymbolReference contextGraph;
    private String variable;
    private PlanBody body;
    private String name;
    public ContextPlanImpl() {}
    @Override public SymbolReference getContextGraph() { return contextGraph; }
    public void setContextGraph(SymbolReference contextGraph) { this.contextGraph = contextGraph; }
    @Override public String getVariable() { return variable; }
    public void setVariable(String variable) { this.variable = variable; }
    @Override public PlanBody getBody() { return body; }
    public void setBody(PlanBody body) { this.body = body; }
    @Override public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, contextGraph);
      add(result, body);
      return result;
    }
  }

  public static class MemberPlanImpl extends NodeImpl implements MemberPlan {
    private String variable;
    private SymbolReference collection;
    private PlanBody body;
    private String name;
    public MemberPlanImpl() {}
    @Override public String getVariable() { return variable; }
    public void setVariable(String variable) { this.variable = variable; }
    @Override public SymbolReference getCollection() { return collection; }
    public void setCollection(SymbolReference collection) { this.collection = collection; }
    @Override public PlanBody getBody() { return body; }
    public void setBody(PlanBody body) { this.body = body; }
    @Override public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, collection);
      add(result, body);
      return result;
    }
  }

  public static class PlanYieldImpl extends NodeImpl implements PlanYield {
    private SymbolReference graph;
    public PlanYieldImpl() {}
    @Override public SymbolReference getGraph() { return graph; }
    public void setGraph(SymbolReference graph) { this.graph = graph; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, graph);
      return result;
    }
  }

  public static class NodePatternImpl extends NodeImpl implements NodePattern {
    private List<PatternField> fields = new ArrayList<>();
    public NodePatternImpl() {}
    @Override public List<PatternField> getFields() { return fields; }
    public void setFields(List<PatternField> fields) { this.fields = fields; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      if (fields != null) result.addAll(fields);
      return result;
    }
  }

  public static class KindPatternFieldImpl extends NodeImpl implements KindPatternField {
    private KindConstraint value;
    public KindPatternFieldImpl() {}
    @Override public KindConstraint getValue() { return value; }
    public void setValue(KindConstraint value) { this.value = value; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, value);
      return result;
    }
  }

  public static class KindConstraintImpl extends NodeImpl implements KindConstraint {
    private List<PatternKind> kinds = new ArrayList<>();
    public KindConstraintImpl() {}
    @Override public List<PatternKind> getKinds() { return kinds; }
    public void setKinds(List<PatternKind> kinds) { this.kinds = kinds; }
  }

  public static class ActivityPatternFieldImpl extends NodeImpl implements ActivityPatternField {
    private Contextualization activity;
    public ActivityPatternFieldImpl() {}
    @Override public Contextualization getActivity() { return activity; }
    public void setActivity(Contextualization activity) { this.activity = activity; }
  }

  public static class FlagPatternFieldImpl extends NodeImpl implements FlagPatternField {
    private PatternFlag flag;
    private Boolean value;
    public FlagPatternFieldImpl() {}
    @Override public PatternFlag getFlag() { return flag; }
    public void setFlag(PatternFlag flag) { this.flag = flag; }
    @Override public Boolean getValue() { return value; }
    public void setValue(Boolean value) { this.value = value; }
  }

  public static class ChildPatternFieldImpl extends NodeImpl implements ChildPatternField {
    private PatternChild child;
    private PatternExpression value;
    public ChildPatternFieldImpl() {}
    @Override public PatternChild getChild() { return child; }
    public void setChild(PatternChild child) { this.child = child; }
    @Override public PatternExpression getValue() { return value; }
    public void setValue(PatternExpression value) { this.value = value; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, value);
      return result;
    }
  }

  public static class BooleanPatternImpl extends NodeImpl implements BooleanPattern {
    private PatternBooleanMode mode;
    private List<PatternExpression> operands = new ArrayList<>();
    public BooleanPatternImpl() {}
    @Override public PatternBooleanMode getMode() { return mode; }
    public void setMode(PatternBooleanMode mode) { this.mode = mode; }
    @Override public List<PatternExpression> getOperands() { return operands; }
    public void setOperands(List<PatternExpression> operands) { this.operands = operands; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      if (operands != null) result.addAll(operands);
      return result;
    }
  }

  public static class NotPatternImpl extends NodeImpl implements NotPattern {
    private PatternExpression operand;
    public NotPatternImpl() {}
    @Override public PatternExpression getOperand() { return operand; }
    public void setOperand(PatternExpression operand) { this.operand = operand; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, operand);
      return result;
    }
  }

  public static class CapturePatternImpl extends NodeImpl implements CapturePattern {
    private String name;
    private PatternExpression operand;
    public CapturePatternImpl() {}
    @Override public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    @Override public PatternExpression getOperand() { return operand; }
    public void setOperand(PatternExpression operand) { this.operand = operand; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, operand);
      return result;
    }
  }

  public static class SamePatternImpl extends NodeImpl implements SamePattern {
    private String name;
    public SamePatternImpl() {}
    @Override public String getName() { return name; }
    public void setName(String name) { this.name = name; }
  }

  public static class PresencePatternImpl extends NodeImpl implements PresencePattern {
    private PatternExpression operand;
    public PresencePatternImpl() {}
    @Override public PatternExpression getOperand() { return operand; }
    public void setOperand(PatternExpression operand) { this.operand = operand; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, operand);
      return result;
    }
  }

  public static class CollectionPatternImpl extends NodeImpl implements CollectionPattern {
    private CollectionQuantifier quantifier;
    private PatternExpression element;
    public CollectionPatternImpl() {}
    @Override public CollectionQuantifier getQuantifier() { return quantifier; }
    public void setQuantifier(CollectionQuantifier quantifier) { this.quantifier = quantifier; }
    @Override public PatternExpression getElement() { return element; }
    public void setElement(PatternExpression element) { this.element = element; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, element);
      return result;
    }
  }

  public static class ExactCollectionPatternImpl extends NodeImpl implements ExactCollectionPattern {
    private List<PatternExpression> elements = new ArrayList<>();
    public ExactCollectionPatternImpl() {}
    @Override public List<PatternExpression> getElements() { return elements; }
    public void setElements(List<PatternExpression> elements) { this.elements = elements; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      if (elements != null) result.addAll(elements);
      return result;
    }
  }

  public static class AnyPatternImpl extends NodeImpl implements AnyPattern {
    public AnyPatternImpl() {}
  }

  public static class AbsentPatternImpl extends NodeImpl implements AbsentPattern {
    public AbsentPatternImpl() {}
  }

  public static class ScalarPatternImpl extends NodeImpl implements ScalarPattern {
    private StrategyScalar value;
    public ScalarPatternImpl() {}
    @Override public StrategyScalar getValue() { return value; }
    public void setValue(StrategyScalar value) { this.value = value; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, value);
      return result;
    }
  }

  public static class SemanticPatternTestImpl extends NodeImpl implements SemanticPatternTest {
    private SemanticMatchRelation relation;
    private ClosedObservable observable;
    public SemanticPatternTestImpl() {}
    @Override public SemanticMatchRelation getRelation() { return relation; }
    public void setRelation(SemanticMatchRelation relation) { this.relation = relation; }
    @Override public ClosedObservable getObservable() { return observable; }
    public void setObservable(ClosedObservable observable) { this.observable = observable; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, observable);
      return result;
    }
  }

  public static class LogicalPatternImpl extends NodeImpl implements LogicalPattern {
    private LogicalPatternConnector connector;
    private OperandOrdering ordering;
    private List<PatternExpression> operands = new ArrayList<>();
    private RemainderCapture remainder;
    public LogicalPatternImpl() {}
    @Override public LogicalPatternConnector getConnector() { return connector; }
    public void setConnector(LogicalPatternConnector connector) { this.connector = connector; }
    @Override public OperandOrdering getOrdering() { return ordering; }
    public void setOrdering(OperandOrdering ordering) { this.ordering = ordering; }
    @Override public List<PatternExpression> getOperands() { return operands; }
    public void setOperands(List<PatternExpression> operands) { this.operands = operands; }
    @Override public RemainderCapture getRemainder() { return remainder; }
    public void setRemainder(RemainderCapture remainder) { this.remainder = remainder; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      if (operands != null) result.addAll(operands);
      add(result, remainder);
      return result;
    }
  }

  public static class RemainderCaptureImpl extends NodeImpl implements RemainderCapture {
    private String name;
    public RemainderCaptureImpl() {}
    @Override public String getName() { return name; }
    public void setName(String name) { this.name = name; }
  }

  public static class ValueOperatorPatternImpl extends NodeImpl implements ValueOperatorPattern {
    private String operator;
    private List<OperatorPatternSlot> slots = new ArrayList<>();
    public ValueOperatorPatternImpl() {}
    @Override public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    @Override public List<OperatorPatternSlot> getSlots() { return slots; }
    public void setSlots(List<OperatorPatternSlot> slots) { this.slots = slots; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      if (slots != null) result.addAll(slots);
      return result;
    }
  }

  public static class OperatorPatternSlotImpl extends NodeImpl implements OperatorPatternSlot {
    private OperatorSlotName slot;
    private PatternExpression pattern;
    public OperatorPatternSlotImpl() {}
    @Override public OperatorSlotName getSlot() { return slot; }
    public void setSlot(OperatorSlotName slot) { this.slot = slot; }
    @Override public PatternExpression getPattern() { return pattern; }
    public void setPattern(PatternExpression pattern) { this.pattern = pattern; }
    @Override public List<Node> children() {
      var result = new ArrayList<Node>();
      add(result, pattern);
      return result;
    }
  }
}
