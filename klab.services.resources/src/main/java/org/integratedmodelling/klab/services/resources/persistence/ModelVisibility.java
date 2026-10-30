package org.integratedmodelling.klab.services.resources.persistence;

import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;

final class ModelVisibility {
  /** Direct catalog consumers must honor the provider's existing indexing readiness gate. */
  static void requireReady(org.integratedmodelling.klab.api.services.ResourcesService service,
      org.integratedmodelling.klab.api.scope.ContextScope scope) {
    if (service instanceof org.integratedmodelling.klab.services.resources.ResourcesProvider provider
        && !provider.checkSemanticServices(scope)) {
      throw new org.integratedmodelling.klab.api.exceptions.KlabStorageException(
          "Semantic search is not available: model indexing or reasoner availability failed");
    }
  }
  static boolean authorized(ModelReference model, org.integratedmodelling.klab.api.scope.ContextScope scope) {
    if (scope == null || scope.getUser() == null || model.getPermissions() == null) return false;
    // The scope overload returns immediately for public resources and bypasses explicit denials.
    return model.getPermissions().checkAuthorization(scope.getUser().getUsername(), scope.getUser().getGroups());
  }
  static <T> List<T> constraints(List<ResolutionConstraint> constraints,
      ResolutionConstraint.Type type, Class<T> cls) {
    return constraints == null ? List.of() : constraints.stream()
        .filter(c -> c != null && c.getType() == type)
        .flatMap(c -> c.payload(cls).stream()).toList();
  }

  static boolean accepts(ModelReference model, Observable observable,
      List<ResolutionConstraint> constraints) {
    var projects = constraints(constraints, ResolutionConstraint.Type.ResolutionProject, String.class);
    var namespaces = constraints(constraints, ResolutionConstraint.Type.ResolutionNamespace, String.class);
    var scenarios = constraints(constraints, ResolutionConstraint.Type.Scenarios, String.class);
    String project = projects.isEmpty() ? null : projects.getFirst();
    String namespace = namespaces.isEmpty() ? null : namespaces.getFirst();
    // Namespace authority is mandatory, including explicitly selected scenarios.
    if ((model.getScope() == KlabStatement.Scope.PRIVATE
        || model.getObservableConcept() != null
            && model.getObservableConcept().is(SemanticType.SUBJECTIVE))
        && (namespace == null || !namespace.equals(model.getNamespaceId()))) return false;
    boolean sameProject = project != null && Objects.equals(project, model.getProjectId());
    if (model.getScope() == KlabStatement.Scope.PROJECT_PRIVATE && !sameProject) return false;
    boolean visible = (namespace != null && namespace.equals(model.getNamespaceId()))
        || scenarios.contains(model.getNamespaceId())
        || (!model.isInScenario() && (model.getScope() == KlabStatement.Scope.PUBLIC
            || model.getScope() == KlabStatement.Scope.PROJECT_PRIVATE && sameProject));
    return visible && (!observable.is(SemanticType.COUNTABLE)
        || observable.getContextualization().isCollective() == model.isReification());
  }
}
