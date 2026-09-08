package org.integratedmodelling.klab.runtime.language;

import java.util.List;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Traverses and validates a complete {@link KimObservationStrategyDocument}. */
public class KimObservationStrategyDocumentVisitor extends KimObservableVisitor {

  public interface Validator extends KimObservableVisitor.Validator {
    default List<Notification> validateDocument(
        KimObservationStrategyDocument document, Context context) {
      return List.of();
    }

    default List<Notification> validateStrategy(KimObservationStrategy strategy, Context context) {
      return List.of();
    }

    default List<Notification> validatePlanNode(KimObservationPlan.Node node, Context context) {
      return List.of();
    }

  }

  public static class LenientValidator extends KimObservableVisitor.LenientValidator
      implements Validator {}

  public static class DefaultValidator extends KimValidator implements Validator {}

  private final Validator strategyValidator;

  public KimObservationStrategyDocumentVisitor() {
    this(new DefaultValidator(), null);
  }

  public KimObservationStrategyDocumentVisitor(Validator validator, Resolver resolver) {
    super(validator, resolver);
    this.strategyValidator = validator == null ? new DefaultValidator() : validator;
  }

  public void visit(KimObservationStrategyDocument document) {
    var context = beginDocument(document);
    addNotifications(strategyValidator.validateDocument(document, context));
    visitValue(document.getMetadata(), context);
    visitValue(document.getCoverage(), context);
    for (var strategy : safe(document.getStatements())) visitStatement(strategy, context);
  }

  @Override
  protected void visitUnknownStatement(KlabStatement statement, Context context) {
    if (statement instanceof KimObservationStrategy strategy) visitStrategy(strategy, context);
  }

  private void visitStrategy(KimObservationStrategy strategy, Context context) {
    addNotifications(strategyValidator.validateStrategy(strategy, context));
    visitPlanNode(strategy.getSelection(), context);
    for (var setup : safe(strategy.getSetup())) visitPlanNode(setup, context);
    visitPlanNode(strategy.getPlan(), context);
  }

  private void visitPlanNode(KimObservationPlan.Node node, Context context) {
    if (node == null || !enter(node)) return;
    var nodeContext = child(context, node);
    addNotifications(strategyValidator.validatePlanNode(node, nodeContext));
    if (node instanceof KimObservationPlan.ClosedObservable value)
      visitObservable(value.getObservable(), nodeContext);
    else if (node instanceof KimObservationPlan.MatchAlternative value)
      visitObservable(value.getObservable(), nodeContext);
    else if (node instanceof KimObservationPlan.StrategyTarget value)
      visitObservable(value.getObservable(), nodeContext);
    // Strategy calls have ordered arguments and remain their own node kind. They must not be
    // flattened into ServiceCall parameter maps or executed during semantic traversal.
    for (var child : safe(node.children())) visitPlanNode(child, nodeContext);
  }
}
