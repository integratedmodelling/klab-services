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

    default List<Notification> validateStrategy(
        KimObservationStrategy strategy, Context context) {
      return List.of();
    }

    default List<Notification> validateFilter(
        KimObservationStrategy.Filter filter, Context context) {
      return List.of();
    }

    default List<Notification> validateOperation(
        KimObservationStrategy.Operation operation, Context context) {
      return List.of();
    }
  }

  public static class LenientValidator extends KimObservableVisitor.LenientValidator
      implements Validator {}

  private final Validator strategyValidator;

  public KimObservationStrategyDocumentVisitor() {
    this(new LenientValidator(), null);
  }

  public KimObservationStrategyDocumentVisitor(Validator validator, Resolver resolver) {
    super(validator, resolver);
    this.strategyValidator = validator == null ? new LenientValidator() : validator;
  }

  public void visit(KimObservationStrategyDocument document) {
    var context = beginDocument(document);
    addNotifications(strategyValidator.validateDocument(document, context));
    for (var strategy : safe(document.getStatements())) visitStatement(strategy, context);
  }

  @Override
  protected void visitUnknownStatement(KlabStatement statement, Context context) {
    if (statement instanceof KimObservationStrategy strategy) visitStrategy(strategy, context);
  }

  private void visitStrategy(KimObservationStrategy strategy, Context context) {
    addNotifications(strategyValidator.validateStrategy(strategy, context));
    for (var group : safe(strategy.getFilters())) {
      for (var filter : safe(group)) visitFilter(filter, context);
    }
    if (strategy.getMacroVariables() != null) {
      strategy.getMacroVariables().values().forEach(filter -> visitFilter(filter, context));
    }
    for (var operation : safe(strategy.getOperations())) visitOperation(operation, context);
  }

  private void visitFilter(KimObservationStrategy.Filter filter, Context context) {
    if (filter == null || !enter(filter)) return;
    var filterContext = child(context, filter);
    addNotifications(strategyValidator.validateFilter(filter, filterContext));
    visitConcept(filter.getMatch(), filterContext);
    for (var function : safe(filter.getFunctions())) visitServiceCall(function, filterContext);
    visitValue(filter.getLiteral(), filterContext);
  }

  private void visitOperation(KimObservationStrategy.Operation operation, Context context) {
    if (operation == null || !enter(operation)) return;
    var operationContext = child(context, operation);
    addNotifications(strategyValidator.validateOperation(operation, operationContext));
    visitObservable(operation.getObservable(), operationContext);
    for (var function : safe(operation.getFunctions())) visitServiceCall(function, operationContext);
  }
}
