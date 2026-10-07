package org.integratedmodelling.klab.services.resources.persistence;

import java.util.*;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.exceptions.KlabStorageException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.EnumeratedExtension;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;

/** Document model catalog for Nitrite and MongoDB. One model and all its inferred descriptors
 * form a single replaceable document. Type IDs are indexed for candidate retrieval. */
public final class DocumentModelKbox extends DocumentObservableKbox implements ModelCatalog {
  public DocumentModelKbox(ResourcesService service, KboxDocumentStore documents) { super(service, documents); }

  @Override public Collection<ModelReference> inferModels(KimModel model, Scope scope) {
    return new ModelDescriptorFactory(resourceService).inferModels(model, scope);
  }
  private String generation(String namespace) {
    var marker = documents.get("namespace:" + namespace);
    return marker == null ? "" : (String) marker.getOrDefault("generation", "");
  }
  private boolean active(Map<String, Object> document) {
    return generation((String) document.get("namespace")).equals(document.getOrDefault("generation", ""));
  }
  @Override public synchronized void replaceNamespace(KimNamespace namespace, Scope scope) {
    String previous = generation(namespace.getUrn());
    String next = UUID.randomUUID().toString();
    var staged = new ArrayList<String>();
    try {
      for (var statement : namespace.getStatements()) {
        if (statement instanceof KimModel model) {
          var descriptors = new ArrayList<>(inferModels(model, scope));
          if (!descriptors.isEmpty()) {
            if (descriptors.stream().anyMatch(d -> !namespace.getUrn().equals(d.getNamespaceId())))
              throw new IllegalArgumentException("Model descriptor belongs to another namespace");
            String key = "model:" + next + ":" + model.getUrn();
            staged.add(key);
            storeDescriptors(key, descriptors, scope, next);
          }
        }
      }
    } catch (RuntimeException failure) {
      for (String key : staged) {
        try { documents.delete(key); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
      }
      throw failure;
    }
    // A single manifest replacement publishes the complete namespace. On an ambiguous publish
    // failure retain staged documents: the server may have committed the manifest already.
    documents.put("namespace:" + namespace.getUrn(), Map.of("kind", "namespace",
        "timestamp", namespace.getLastUpdateTimestamp(), "scenario", namespace.isScenario(), "generation", next));
    for (var document : documents.find("namespace", List.of(namespace.getUrn())))
      if (previous.equals(document.getOrDefault("generation", ""))) deleteDocument(document);
  }
  private void deleteDocument(Map<String, Object> document) {
    documents.delete((String) document.getOrDefault("key", document.get("_id")));
  }
  @Override public synchronized long store(Object object, Scope scope) {
    if (object instanceof KimNamespace namespace) {
      documents.put("namespace:" + namespace.getUrn(), Map.of("kind", "namespace",
          "timestamp", namespace.getLastUpdateTimestamp(), "scenario", namespace.isScenario(),
          "generation", generation(namespace.getUrn())));
      return 0;
    }
    if (object instanceof KimModel model) {
      String generation = generation(model.getNamespace());
      String key = "model:" + generation + ":" + model.getUrn();
      var descriptors = new ArrayList<>(inferModels(model, scope));
      if (descriptors.isEmpty()) {
        documents.delete(key);
        return -1;
      }
      return storeDescriptors(key, descriptors, scope, generation);
    }
    if (object instanceof ModelReference descriptor)
      return storeDescriptors("descriptor:" + documents.nextId(), List.of(descriptor), scope,
          generation(descriptor.getNamespaceId()));
    throw new IllegalArgumentException("Unsupported model catalog object: " + object);
  }

  private long storeDescriptors(String key, List<ModelReference> models, Scope scope, String generation) {
    List<Long> ids = new ArrayList<>();
    List<Long> types = new ArrayList<>();
    List<String> payloads = new ArrayList<>();
    for (var model : models) {
      Objects.requireNonNull(model.getName(), "model name");
      Objects.requireNonNull(model.getNamespaceId(), "namespace");
      Objects.requireNonNull(model.getScope(), "model scope");
      Objects.requireNonNull(model.getPermissions(), "model permissions");
      if (!models.getFirst().getName().equals(model.getName())
          || !models.getFirst().getNamespaceId().equals(model.getNamespaceId()))
        throw new IllegalArgumentException("A model aggregate must have one name and namespace");
      var concept = model.getObservableConcept();
      if (concept == null) concept = reasoner().resolveConcept(model.getObservable());
      if (concept == null) throw new KlabStorageException("Unresolvable model observable: " + model.getObservable());
      var copy = model.copy();
      copy.setObservableConcept(concept);
      ids.add(documents.nextId());
      types.add(requireConceptId(concept, scope));
      payloads.add(ModelReferenceCodec.encode(copy));
    }
    documents.put(key, Map.of("kind", "model", "name", models.getFirst().getName(),
        "namespace", models.getFirst().getNamespaceId(), "descriptorIds", ids,
        "typeIds", types, "payloads", payloads, "generation", generation));
    return ids.getFirst();
  }
  @SuppressWarnings("unchecked") private List<String> payloads(Map<String, Object> document) {
    return (List<String>) document.get("payloads");
  }
  @SuppressWarnings("unchecked") private List<Number> numbers(Map<String, Object> document, String field) {
    return (List<Number>) document.get(field);
  }
  @Override public long count() {
    return documents.find("kind", List.of("model")).stream().filter(this::active).mapToLong(d -> payloads(d).size()).sum();
  }
  @Override public boolean hasModel(String name) { return documents.find("name", List.of(name)).stream().anyMatch(this::active); }
  @Override public List<ModelReference> retrieveAll(Channel monitor) {
    List<ModelReference> ret = new ArrayList<>();
    for (var document : documents.find("kind", List.of("model")))
      if (active(document)) for (String payload : payloads(document)) ret.add(ModelReferenceCodec.decode(payload, reasoner()));
    return ret;
  }
  @Override public ModelReference retrieveModel(long id, Channel monitor) {
    for (var document : documents.find("descriptorIds", List.of(id))) {
      if (!active(document)) continue;
      var ids = numbers(document, "descriptorIds");
      for (int i = 0; i < ids.size(); i++) if (ids.get(i).longValue() == id)
        return ModelReferenceCodec.decode(payloads(document).get(i), reasoner());
    }
    return null;
  }
  @Override public ModelReference retrieveModel(String name, Channel monitor) {
    for (var document : documents.find("name", List.of(name)))
      if (active(document) && !payloads(document).isEmpty()) return ModelReferenceCodec.decode(payloads(document).getFirst(), reasoner());
    return null;
  }
  @Override public int clearNamespace(String namespace, Channel monitor) {
    int count = 0;
    for (var document : documents.find("namespace", List.of(namespace))) {
      if (active(document)) count += payloads(document).size();
      deleteDocument(document);
    }
    documents.delete("namespace:" + namespace);
    return count;
  }
  @Override public void remove(String namespace, Channel monitor) { clearNamespace(namespace, monitor); }
  @Override public long getNamespaceTimestamp(KimNamespace namespace) {
    var document = documents.get("namespace:" + namespace.getUrn());
    return document == null ? 0 : ((Number) document.get("timestamp")).longValue();
  }
  @Override public int removeIfOlder(KimNamespace namespace, Channel monitor) {
    if (documents.get("namespace:" + namespace.getUrn()) == null
        || namespace.getLastUpdateTimestamp() > getNamespaceTimestamp(namespace)) {
      clearNamespace(namespace.getUrn(), monitor);
      return 1;
    }
    return Utils.Notifications.hasErrors(namespace.getNotifications()) ? 2 : 0;
  }
  @Override public Collection<ModelReference> query(Observable observable,
      org.integratedmodelling.klab.api.geometry.Geometry geometry, Concept context,
      List<ResolutionConstraint> constraints, ContextScope scope) {
    try {
      return queryModels(observable, geometry, context, constraints, scope).stream()
          .filter(m -> ModelVisibility.authorized(m, scope)).toList();
    } catch (RuntimeException failure) {
      var error = new KlabStorageException("Model discovery failed for " + observable.getUrn());
      error.initCause(failure);
      throw error;
    }
  }
  @Override public List<ModelReference> queryModels(Observable observable,
      org.integratedmodelling.klab.api.geometry.Geometry geometry, Concept context,
      List<ResolutionConstraint> constraints, ContextScope scope) {
    List<ModelReference> result = new ArrayList<>();
    if (geometry == null || geometry.isEmpty()) return result;
    var scale = GeometryRepository.INSTANCE.scale(geometry);
    Object time = scale.getTime();
    if (time instanceof EnumeratedExtension<?> enumeration) time = enumeration.getPhysicalExtent();
    if (time instanceof EnumeratedExtension<?>) throw new KlabUnimplementedException("enumerated extension");
    var types = getCompatibleTypeIds(observable, context);
    for (var document : documents.find("typeIds", types)) {
      if (!active(document)) continue;
      var storedTypes = numbers(document, "typeIds");
      var payloads = payloads(document);
      for (int i = 0; i < payloads.size(); i++) {
        if (!types.contains(storedTypes.get(i).longValue())) continue;
        var model = ModelReferenceCodec.decode(payloads.get(i), reasoner());
        if (!ModelVisibility.accepts(model, observable, constraints) || !matchesSpace(model, scale)) continue;
        Coverage coverage = resourceService.info(model.getName(), KlabAsset.KnowledgeClass.MODEL, Coverage.class, scope);
        if (coverage == null || coverage.checkConstraints(scale)) result.add(model);
      }
    }
    return result;
  }
  private boolean matchesSpace(ModelReference model, Scale scale) {
    Space space = scale.getSpace();
    if (space instanceof EnumeratedExtension<?> enumeration) space = (Space) enumeration.getPhysicalExtent();
    if (space instanceof EnumeratedExtension<?>) throw new KlabUnimplementedException("enumerated extension");
    if (space == null || space.getGeometricShape() == null || space.getGeometricShape().isEmpty()) return true;
    if (space.getRank() < model.getMinSpatialScaleFactor() || space.getRank() > model.getMaxSpatialScaleFactor()) return false;
    if (model.getShape() == null || model.getShape().isEmpty()) return true;
    // Match the legacy H2 && envelope predicate. Exact coverage remains the coverage service's job.
    return ShapeImpl.promote(model.getShape()).getStandardizedGeometry().getEnvelopeInternal()
        .intersects(ShapeImpl.promote(space.getGeometricShape()).getStandardizedGeometry().getEnvelopeInternal());
  }
}
