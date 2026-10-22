package org.integratedmodelling.klab.api.services.runtime;

import java.io.Serializable;
import java.util.List;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;

/** A chosen model's frozen, request-local classifier obligation, before parent compilation. */
public record ClassifierDependencyPlan(String modelUrn, String namespace, long sourceVersion,
    String sourceFingerprint, int dependencyIndex, String localName, KimObservable source,
    long contextId, String contextObservable, String support, String reasonerId, long knowledgeRevision,
    List<Occurrence> occurrences) implements Serializable {
  public ClassifierDependencyPlan { occurrences=List.copyOf(occurrences); }
  public record Occurrence(List<String> path, Actuator classifier) implements Serializable {
    public Occurrence { path=List.copyOf(path); }
  }
}
