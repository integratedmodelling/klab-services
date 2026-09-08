package org.integratedmodelling.klab.services.resources.lang;

import java.util.ArrayList;
import java.util.function.Function;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimObservationPlan;
import org.integratedmodelling.klab.api.lang.kim.KimObservationPlan.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservationPlanImpl.*;
import org.integratedmodelling.languages.api.ObservableSyntax;
import org.integratedmodelling.languages.api.ObservationSyntax;

/** Internal typed copier used by LanguageAdapter; never evaluates calls or links graph symbols. */
final class ObservationPlanAdapter {
  private final Function<ObservableSyntax, KimObservable> observableAdapter;
  private final Function<ObservationSyntax.Node, KimObservationPlan.Source> sourceAdapter;

  ObservationPlanAdapter(
      Function<ObservableSyntax, KimObservable> observableAdapter,
      Function<ObservationSyntax.Node, KimObservationPlan.Source> sourceAdapter) {
    this.observableAdapter = observableAdapter;
    this.sourceAdapter = sourceAdapter;
  }

  Node adapt(ObservationSyntax.Node syntax) {
    if (syntax == null) return null;
    NodeImpl result;
    if (syntax instanceof ObservationSyntax.SymbolReference<?> value) {
      var bean = new SymbolReferenceImpl();
      bean.setName(value.getName());
      result = bean;
    } else if (syntax instanceof ObservationSyntax.StrategySelection value) {
      var bean = new StrategySelectionImpl();
      bean.setAlternatives(
          value.getAlternatives().stream()
              .map(item -> (MatchAlternative) adapt(item))
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.MatchAlternative value) {
      var bean = new MatchAlternativeImpl();
      bean.setPattern((PatternBlock) adapt(value.getPattern()));
      bean.setMatcher((ExternalMatcher) adapt(value.getMatcher()));
      bean.setObservable(
          value.getObservable() == null ? null : observableAdapter.apply(value.getObservable()));
      bean.setGuards(
          value.getGuards().stream()
              .map(item -> (StrategyCall) adapt(item))
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.PatternBlock value) {
      var bean = new PatternBlockImpl();
      bean.setSubject((StrategyVariable) adapt(value.getSubject()));
      bean.setExpression((PatternExpression) adapt(value.getExpression()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.ExternalMatcher value) {
      var bean = new ExternalMatcherImpl();
      bean.setCall((StrategyCall) adapt(value.getCall()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.LetSetup value) {
      var bean = new LetSetupImpl();
      bean.setBindings(
          value.getBindings().stream()
              .map(item -> (SemanticBinding) adapt(item))
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.SemanticBinding value) {
      var bean = new SemanticBindingImpl();
      bean.setNames(new ArrayList<>(value.getNames()));
      bean.setValue((StrategyExpression) adapt(value.getValue()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.EnsureSetup value) {
      var bean = new EnsureSetupImpl();
      bean.setChecks(
          value.getChecks().stream()
              .map(item -> (StrategyCall) adapt(item))
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.StrategyVariable value) {
      var bean = new StrategyVariableImpl();
      bean.setSpelling(value.getSpelling());
      result = bean;
    } else if (syntax instanceof ObservationSyntax.StrategyCall value) {
      var bean = new StrategyCallImpl();
      bean.setFunction(value.getFunction());
      bean.setArguments(
          value.getArguments().stream()
              .map(item -> (StrategyArgument) adapt(item))
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.StrategyArgument value) {
      var bean = new StrategyArgumentImpl();
      bean.setName(value.getName());
      bean.setValue((StrategyExpression) adapt(value.getValue()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.ClosedObservable value) {
      var bean = new ClosedObservableImpl();
      bean.setObservable(
          value.getObservable() == null ? null : observableAdapter.apply(value.getObservable()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.StrategyScalar value) {
      var bean = new StrategyScalarImpl();
      bean.setText(value.getText());
      bean.setNumber(value.getNumber() == null ? null : (Number) value.getNumber().getPod());
      bean.setBooleanValue(
          value.getBooleanValue() == null ? null : Boolean.valueOf(value.getBooleanValue()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.StrategyTarget value) {
      var bean = new StrategyTargetImpl();
      bean.setExpression((StrategyExpression) adapt(value.getExpression()));
      bean.setObservable(
          value.getObservable() == null ? null : observableAdapter.apply(value.getObservable()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.PlanBody value) {
      var bean = new PlanBodyImpl();
      bean.setSteps(
          value.getSteps().stream()
              .map(item -> (PlanStep) adapt(item))
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.GraphProducer value) {
      var bean = new GraphProducerImpl();
      bean.setMode(value.getMode() == null ? null : ProducerMode.valueOf(value.getMode().name()));
      bean.setTarget((StrategyTarget) adapt(value.getTarget()));
      bean.setContext((StrategyVariable) adapt(value.getContext()));
      bean.setInputs(
          value.getInputs().stream()
              .map(item -> (GraphInput) adapt(item))
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
      bean.setFallback((StrategyExpression) adapt(value.getFallback()));
      bean.setName(value.getName());
      result = bean;
    } else if (syntax instanceof ObservationSyntax.GraphInput value) {
      var bean = new GraphInputImpl();
      bean.setPort(value.getPort());
      bean.setGraph((SymbolReference) adapt(value.getGraph()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.GraphReference value) {
      var bean = new GraphReferenceImpl();
      bean.setObservation((StrategyVariable) adapt(value.getObservation()));
      bean.setName(value.getName());
      result = bean;
    } else if (syntax instanceof ObservationSyntax.GraphMerge value) {
      var bean = new GraphMergeImpl();
      bean.setLeft((SymbolReference) adapt(value.getLeft()));
      bean.setRight((SymbolReference) adapt(value.getRight()));
      bean.setOperator(
          value.getOperator() == null ? null : CompositionKind.valueOf(value.getOperator().name()));
      bean.setOptions(
          value.getOptions().stream()
              .map(item -> (StrategyArgument) adapt(item))
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
      bean.setTarget((StrategyTarget) adapt(value.getTarget()));
      bean.setName(value.getName());
      result = bean;
    } else if (syntax instanceof ObservationSyntax.ContextPlan value) {
      var bean = new ContextPlanImpl();
      bean.setContextGraph((SymbolReference) adapt(value.getContextGraph()));
      bean.setVariable(value.getVariable());
      bean.setBody((PlanBody) adapt(value.getBody()));
      bean.setName(value.getName());
      result = bean;
    } else if (syntax instanceof ObservationSyntax.MemberPlan value) {
      var bean = new MemberPlanImpl();
      bean.setVariable(value.getVariable());
      bean.setCollection((SymbolReference) adapt(value.getCollection()));
      bean.setBody((PlanBody) adapt(value.getBody()));
      bean.setName(value.getName());
      result = bean;
    } else if (syntax instanceof ObservationSyntax.PlanYield value) {
      var bean = new PlanYieldImpl();
      bean.setGraph((SymbolReference) adapt(value.getGraph()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.NodePattern value) {
      var bean = new NodePatternImpl();
      bean.setFields(
          value.getFields().stream()
              .map(item -> (PatternField) adapt(item))
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.KindPatternField value) {
      var bean = new KindPatternFieldImpl();
      bean.setValue((KindConstraint) adapt(value.getValue()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.KindConstraint value) {
      var bean = new KindConstraintImpl();
      bean.setKinds(
          value.getKinds().stream()
              .map(item -> PatternKind.valueOf(item.name()))
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.ActivityPatternField value) {
      var bean = new ActivityPatternFieldImpl();
      bean.setActivity(
          value.getActivity() == null
              ? null
              : Contextualization.valueOf(value.getActivity().name()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.FlagPatternField value) {
      var bean = new FlagPatternFieldImpl();
      bean.setFlag(value.getFlag() == null ? null : PatternFlag.valueOf(value.getFlag().name()));
      bean.setValue(value.getValue() == null ? null : Boolean.valueOf(value.getValue()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.ChildPatternField value) {
      var bean = new ChildPatternFieldImpl();
      bean.setChild(
          value.getChild() == null ? null : PatternChild.valueOf(value.getChild().name()));
      bean.setValue((PatternExpression) adapt(value.getValue()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.BooleanPattern value) {
      var bean = new BooleanPatternImpl();
      bean.setMode(
          value.getMode() == null ? null : PatternBooleanMode.valueOf(value.getMode().name()));
      bean.setOperands(
          value.getOperands().stream()
              .map(item -> (PatternExpression) adapt(item))
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.NotPattern value) {
      var bean = new NotPatternImpl();
      bean.setOperand((PatternExpression) adapt(value.getOperand()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.CapturePattern value) {
      var bean = new CapturePatternImpl();
      bean.setName(value.getName());
      bean.setOperand((PatternExpression) adapt(value.getOperand()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.SamePattern value) {
      var bean = new SamePatternImpl();
      bean.setName(value.getName());
      result = bean;
    } else if (syntax instanceof ObservationSyntax.PresencePattern value) {
      var bean = new PresencePatternImpl();
      bean.setOperand((PatternExpression) adapt(value.getOperand()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.CollectionPattern value) {
      var bean = new CollectionPatternImpl();
      bean.setQuantifier(
          value.getQuantifier() == null
              ? null
              : CollectionQuantifier.valueOf(value.getQuantifier().name()));
      bean.setElement((PatternExpression) adapt(value.getElement()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.ExactCollectionPattern value) {
      var bean = new ExactCollectionPatternImpl();
      bean.setElements(
          value.getElements().stream()
              .map(item -> (PatternExpression) adapt(item))
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.AnyPattern value) {
      var bean = new AnyPatternImpl();
      result = bean;
    } else if (syntax instanceof ObservationSyntax.AbsentPattern value) {
      var bean = new AbsentPatternImpl();
      result = bean;
    } else if (syntax instanceof ObservationSyntax.ScalarPattern value) {
      var bean = new ScalarPatternImpl();
      bean.setValue((StrategyScalar) adapt(value.getValue()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.SemanticPatternTest value) {
      var bean = new SemanticPatternTestImpl();
      bean.setRelation(
          value.getRelation() == null
              ? null
              : SemanticMatchRelation.valueOf(value.getRelation().name()));
      bean.setObservable((ClosedObservable) adapt(value.getObservable()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.LogicalPattern value) {
      var bean = new LogicalPatternImpl();
      bean.setConnector(
          value.getConnector() == null
              ? null
              : LogicalPatternConnector.valueOf(value.getConnector().name()));
      bean.setOrdering(
          value.getOrdering() == null ? null : OperandOrdering.valueOf(value.getOrdering().name()));
      bean.setOperands(
          value.getOperands().stream()
              .map(item -> (PatternExpression) adapt(item))
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
      bean.setRemainder((RemainderCapture) adapt(value.getRemainder()));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.RemainderCapture value) {
      var bean = new RemainderCaptureImpl();
      bean.setName(value.getName());
      result = bean;
    } else if (syntax instanceof ObservationSyntax.ValueOperatorPattern value) {
      var bean = new ValueOperatorPatternImpl();
      bean.setOperator(value.getOperator());
      bean.setSlots(
          value.getSlots().stream()
              .map(item -> (OperatorPatternSlot) adapt(item))
              .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
      result = bean;
    } else if (syntax instanceof ObservationSyntax.OperatorPatternSlot value) {
      var bean = new OperatorPatternSlotImpl();
      bean.setSlot(
          value.getSlot() == null ? null : OperatorSlotName.valueOf(value.getSlot().name()));
      bean.setPattern((PatternExpression) adapt(value.getPattern()));
      result = bean;
    } else
      throw new IllegalArgumentException(
          "Unsupported strategy node: " + syntax.getClass().getName());
    result.setSource(sourceAdapter.apply(syntax));
    return result;
  }
}
