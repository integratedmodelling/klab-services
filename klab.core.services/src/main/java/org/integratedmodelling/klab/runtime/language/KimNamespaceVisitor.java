package org.integratedmodelling.klab.runtime.language;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Contextualizable;
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

  public static class DefaultValidator extends KimValidator implements Validator {
    @Override
    public List<Notification> validateModel(KimModel model, Context context) {
      var diagnostics = new java.util.ArrayList<Notification>();
      for (var contextualizable : safe(model.getContextualization())) {
        var target = contextualizable.getTarget();
        if (contextualizable.getTargetId() != null && !contextualizable.getTargetId().isBlank()) {
          target =
              Stream.concat(
                      safe(model.getObservables()).stream(), safe(model.getDependencies()).stream())
                  .filter(observable -> matches(contextualizable.getTargetId(), observable))
                  .findFirst()
                  .orElse(null);
        } else if (target == null && !safe(model.getObservables()).isEmpty()) {
          target = safe(model.getObservables()).iterator().next();
        }
        if (target == null) {
          diagnostics.add(
              error(
                  "Unknown contextualization target " + contextualizable.getTargetId(),
                  contextualizable,
                  context));
          continue;
        }
        validateMappingTarget(contextualizable, target, context, diagnostics);
      }
      return diagnostics;
    }

    private static boolean matches(String targetId, KimObservable observable) {
      return Objects.equals(targetId, observable.getFormalName())
          || Objects.equals(targetId, observable.getCodeName());
    }

    private static void validateMappingTarget(
        Contextualizable contextualizable,
        KimObservable target,
        Context context,
        List<Notification> diagnostics) {
      var targetType =
          target.getNonSemanticType() != null
              ? target.getNonSemanticType()
              : Artifact.Type.forSemantics(target.getSemantics().getType());
      if (contextualizable.getClassification() != null
          && !Artifact.Type.isCompatible(targetType, Artifact.Type.CONCEPT)) {
        diagnostics.add(
            error(
                "A classification produces concepts, incompatible with target type " + targetType,
                contextualizable,
                context));
      }
      if (contextualizable.getLookupTable() != null) {
        var lookupType = contextualizable.getLookupTable().getLookupType();
        if (lookupType != null && !Artifact.Type.isCompatible(targetType, lookupType)) {
          diagnostics.add(
              error(
                  "Lookup result type "
                      + lookupType
                      + " is incompatible with target type "
                      + targetType,
                  contextualizable,
                  context));
        }
      }
      if (contextualizable.getAccordingTo() != null && targetType != Artifact.Type.CONCEPT) {
        diagnostics.add(
            error(
                "according to requires a concept-valued target, not " + targetType,
                contextualizable,
                context));
      }
    }

    private static Notification error(String message, Contextualizable source, Context context) {
      return Notification.error(
          message, Notification.LexicalContext.of(source, context.getDocument()));
    }
  }

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
