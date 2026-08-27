package org.integratedmodelling.klab.runtime.language;

import java.util.List;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Traverses and validates a complete {@link KimNamespace}. */
public class KimNamespaceVisitor extends KimObservableVisitor {

  public interface Validator extends KimObservableVisitor.Validator {
    default List<Notification> validateNamespace(KimNamespace namespace, Context context) {
      return List.of();
    }

    default List<Notification> validateModel(KimModel model, Context context) {
      return List.of();
    }

    default List<Notification> validateSymbol(KimSymbolDefinition symbol, Context context) {
      return List.of();
    }
  }

  public static class LenientValidator extends KimObservableVisitor.LenientValidator
      implements Validator {}

  public static class DefaultValidator extends KimValidator implements Validator {}

  private final Validator namespaceValidator;

  public KimNamespaceVisitor() {
    this(new DefaultValidator(), null);
  }

  public KimNamespaceVisitor(Validator validator, Resolver resolver) {
    super(validator, resolver);
    this.namespaceValidator = validator == null ? new DefaultValidator() : validator;
  }

  public void visit(KimNamespace namespace) {
    var context = beginDocument(namespace);
    addNotifications(namespaceValidator.validateNamespace(namespace, context));
    if (namespace.getImports() != null) {
      namespace
          .getImports()
          .keySet()
          .forEach(urn -> reference(urn, KlabAsset.KnowledgeClass.NAMESPACE, namespace, context));
    }
    for (var statement : safe(namespace.getStatements())) {
      visitStatement(statement, context);
    }
  }

  @Override
  protected void visitUnknownStatement(KlabStatement statement, Context context) {
    switch (statement) {
      case KimModel model -> visitModel(model, context);
      case KimSymbolDefinition symbol -> visitSymbol(symbol, context);
      case KimConceptStatement conceptStatement -> visitConceptStatement(conceptStatement, context);
      default -> {}
    }
  }

  private void visitModel(KimModel model, Context context) {
    addNotifications(namespaceValidator.validateModel(model, context));
    for (var observable : safe(model.getObservables())) visitObservable(observable, context);
    for (var dependency : safe(model.getDependencies())) visitObservable(dependency, context);
    for (var contextualizable : safe(model.getContextualization())) {
      visitContextualizable(contextualizable, context);
    }
    for (var urn : safe(model.getResourceUrns())) {
      reference(
          urn == null ? null : urn.toString(), KlabAsset.KnowledgeClass.RESOURCE, model, context);
    }
  }

  private void visitSymbol(KimSymbolDefinition symbol, Context context) {
    addNotifications(namespaceValidator.validateSymbol(symbol, context));
    visitValue(symbol.getValue(), context);
  }

  private void visitConceptStatement(KimConceptStatement statement, Context context) {
    visitConceptStatementContents(statement, context);
  }
}
