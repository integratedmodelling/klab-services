package org.integratedmodelling.klab.api.services.runtime;

import java.io.Serializable;
import java.util.*;
import org.integratedmodelling.klab.api.data.StorageScan;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/** One completed classifier expansion, not an aggregation or a coverage certificate. */
public record DependencyFamily(String id, String rootAttempt, String modelUrn, int dependencyIndex,
    String localName, String sourceObservable, List<List<String>> occurrences, List<Branch> branches)
    implements Serializable {
  public static final String METADATA_KEY="im:classifier-dependency-families";
  public DependencyFamily {
    Objects.requireNonNull(id);Objects.requireNonNull(rootAttempt);Objects.requireNonNull(sourceObservable);
    occurrences=occurrences.stream().map(List::copyOf).toList();branches=List.copyOf(branches);
    if(branches.stream().map(Branch::id).distinct().count()!=branches.size())throw new IllegalArgumentException("Duplicate dependency branch identity");
  }
  public enum Outcome { RESOLVED, OPTIONAL_NO_MODEL }
  public record Branch(String id, String observable, StorageScan.Semantics requested, boolean optional, Outcome outcome) implements Serializable {
    public Branch { Objects.requireNonNull(id);Objects.requireNonNull(observable);Objects.requireNonNull(requested);Objects.requireNonNull(outcome);
      if(outcome==Outcome.OPTIONAL_NO_MODEL && !optional)throw new IllegalArgumentException("Required dependency cannot be absent"); }
  }
  /** Transport identity belongs to the dependency edge, never to a shared source observation. */
  public record Binding(String familyId, String branchId, String rootAttempt, String observable) implements Serializable {
    public static final String PROPERTY="classifierDependencyBinding";
    public Binding { Objects.requireNonNull(familyId);Objects.requireNonNull(branchId);Objects.requireNonNull(rootAttempt);Objects.requireNonNull(observable); }
  }
  /** Current execution view. The Observation retains its native identity and value semantics. */
  public record Input(DependencyFamily family, Branch branch, Observation observation, StorageScan.Binding storageBinding) {}
}
