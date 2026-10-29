package org.integratedmodelling.klab.api.services.runtime;

import java.io.Serializable;
import java.util.List;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;

/** A chosen model's frozen, request-local classifier obligation, before parent compilation. */
public record ClassifierDependencyPlan(String modelUrn, String namespace, long sourceVersion,
    String sourceFingerprint, int dependencyIndex, String localName, KimObservable source,
    long contextId, String contextObservable, String support, String reasonerId, long knowledgeRevision,
    List<Occurrence> occurrences, ContextWitness contextWitness) implements Serializable {
  /** Compatibility for old durable-context plans. Provisional contexts require a typed witness. */
  public ClassifierDependencyPlan(String modelUrn, String namespace, long sourceVersion,
      String sourceFingerprint, int dependencyIndex, String localName, KimObservable source,
      long contextId, String contextObservable, String support, String reasonerId, long knowledgeRevision,
      List<Occurrence> occurrences) {
    this(modelUrn, namespace, sourceVersion, sourceFingerprint, dependencyIndex, localName, source,
        contextId, contextObservable, support, reasonerId, knowledgeRevision, occurrences, null);
  }
  public record ContextWitness(long id, long transientId, String urn, String semantics,
      String geometry, String scopeId) implements Serializable {
    public static ContextWitness capture(org.integratedmodelling.klab.api.knowledge.observation.Observation context,
        String scopeId) {
      if (context == null || context.getId() == 0 || context.getId() == -1
          || context.getObservable() == null || context.getGeometry() == null)
        throw new IllegalArgumentException("Classifier context must have an assigned observation identity");
      return new ContextWitness(context.getId(), context.getId() > 0 ? 0 : context.getTransientId(),
          org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl.logicalUrn(context.getUrn()),
          context.getObservable().getSemantics().getUrn(),
          org.integratedmodelling.klab.api.geometry.Geometry.forTransport(context.getGeometry()).encode(), scopeId);
    }
  }
  public ClassifierDependencyPlan { occurrences=List.copyOf(occurrences); }
  public record Occurrence(List<String> path, Actuator classifier) implements Serializable {
    public Occurrence { path=List.copyOf(path); }
  }
}
