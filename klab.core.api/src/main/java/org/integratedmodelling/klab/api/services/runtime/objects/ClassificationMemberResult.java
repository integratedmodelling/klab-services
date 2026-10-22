package org.integratedmodelling.klab.api.services.runtime.objects;

import java.io.Serializable;
import java.util.*;

/**
 * Immutable invocation facts for one supplied member. Not a membership/completion certificate.
 * IDs/URNs are captured at invocation and can be provisional; retain transient identity and the
 * enclosing attempt. EXISTING means current read-view knowledge, which may itself be tentative.
 */
public record ClassificationMemberResult(long memberIdAtInvocation, long memberTransientId,
    String memberUrnAtInvocation, String inputObservable, String requestedPredicate,
    List<String> concretePredicates, Origin origin, String support, String event)
    implements Serializable {
  public enum Origin { CLASSIFIER, EXISTING, OPTIONAL_ABSENCE }
  public ClassificationMemberResult {
    Objects.requireNonNull(origin);
    concretePredicates = List.copyOf(concretePredicates);
    for (var predicate : concretePredicates)
      if (predicate.isBlank()) throw new IllegalArgumentException("Missing concrete predicate URN");
    if ((origin == Origin.OPTIONAL_ABSENCE) != concretePredicates.isEmpty())
      throw new IllegalArgumentException("Only explicit optional absence has no predicate result");
  }

  /** JSON/property-safe invocation evidence, with no live Observation/Concept references. */
  public Map<String, Object> metadata() {
    var result = new LinkedHashMap<String, Object>();
    result.put("memberIdAtInvocation", memberIdAtInvocation);
    result.put("memberTransientId", memberTransientId);
    result.put("memberUrnAtInvocation", memberUrnAtInvocation);
    result.put("inputObservable", inputObservable);
    result.put("requestedPredicate", requestedPredicate);
    result.put("concretePredicates", concretePredicates);
    result.put("origin", origin.name());
    result.put("support", support);
    result.put("event", event);
    return Collections.unmodifiableMap(result);
  }
}
