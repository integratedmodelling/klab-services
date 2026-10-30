package org.integratedmodelling.klab.api.services.runtime.objects;

import java.util.Set;

/** Installed method inspection. Unavailable is never a known empty input contract. */
public record MemberQualityConsumerInspection(boolean available,Set<String> ports) {
  public MemberQualityConsumerInspection {
    if(ports==null || ports.stream().anyMatch(p->p==null || p.isBlank()) || !available && !ports.isEmpty())
      throw new IllegalArgumentException("Invalid installed member-quality input inspection");
    ports=Set.copyOf(ports);
  }
  public static MemberQualityConsumerInspection known(Set<String> ports) {
    return new MemberQualityConsumerInspection(true,ports);
  }
  public static MemberQualityConsumerInspection unavailable() {
    return new MemberQualityConsumerInspection(false,Set.of());
  }
}
