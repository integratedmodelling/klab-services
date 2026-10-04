package org.integratedmodelling.klab.services.resources.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Channel;

/** Storage-neutral model discovery contract. SQL administration remains specific to ModelKbox. */
public interface ModelCatalog extends AutoCloseable {
  long store(Object object, Scope scope);
  /** Infer before removing existing data. Document catalogs additionally publish atomically. */
  default void replaceNamespace(KimNamespace namespace, Scope scope) {
    var descriptors = new java.util.ArrayList<ModelReference>();
    for (var statement : namespace.getStatements())
      if (statement instanceof KimModel model) descriptors.addAll(inferModels(model, scope));
    clearNamespace(namespace.getUrn(), scope);
    for (var descriptor : descriptors) store(descriptor, scope);
    store(namespace, scope);
  }
  long count();
  boolean hasModel(String name);
  List<ModelReference> retrieveAll(Channel monitor);
  ModelReference retrieveModel(long id, Channel monitor);
  ModelReference retrieveModel(String name, Channel monitor);
  Collection<ModelReference> inferModels(KimModel model, Scope scope);
  Collection<ModelReference> query(Observable observable,
      org.integratedmodelling.klab.api.geometry.Geometry geometry, Concept context,
      List<ResolutionConstraint> constraints, ContextScope scope);
  List<ModelReference> queryModels(Observable observable,
      org.integratedmodelling.klab.api.geometry.Geometry geometry, Concept context,
      List<ResolutionConstraint> constraints, ContextScope scope);
  int clearNamespace(String namespace, Channel monitor);
  void remove(String namespace, Channel monitor);
  int removeIfOlder(KimNamespace namespace, Channel monitor);
  long getNamespaceTimestamp(KimNamespace namespace);
  long getConceptId(Concept concept);
  long requireConceptId(Concept concept, Channel monitor);
  Observable getType(long id);
  String getTypeDefinition(long id);
  List<String> getKnownDefinitions();
  Set<Long> getCompatibleTypeIds(Observable observable, Concept context);
  @Override void close();
}
