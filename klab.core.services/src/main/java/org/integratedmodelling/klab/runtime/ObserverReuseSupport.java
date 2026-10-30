package org.integratedmodelling.klab.runtime;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.services.resolver.Coverage;

/** Actual support of a committed dependent result relative to this query, not observer eligibility. */
public final class ObserverReuseSupport {
  private ObserverReuseSupport() {}

  /** Unknown support is not a proof of complete reuse. No semantic incompatibility is inferred. */
  public static Coverage coverage(Observation existing, Geometry requested) {
    return existing==null?null:coverage(existing.getGeometry(),requested);
  }

  public static Coverage coverage(Geometry existing, Geometry requested) {
    if(existing==null || requested==null)return null;
    try {
      var scale=GeometryRepository.INSTANCE.scale(requested);
      if(existing.isEmpty() || requested.isEmpty())return Coverage.create(scale,0);
      // The generic coverage merger has no extent on which to accumulate scalar coverage.
      // Identical known support is an independent complete-support proof, including scalars.
      if(GeometryRepository.INSTANCE.sanitize(existing).encode().equals(
          GeometryRepository.INSTANCE.sanitize(requested).encode()))return Coverage.create(scale,1);
      return Coverage.create(scale,0).merge(existing,LogicalConnector.UNION);
    } catch(RuntimeException unknownSupport) {return null;}
  }
}
