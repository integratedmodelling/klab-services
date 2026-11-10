package org.integratedmodelling.klab.api.services.runtime.extension;

import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.Resource;

/** Inline learned resource awaiting resolver validation. Identity/version/history are server-owned. */
public record LearnedResourceSubmission(
    String attemptId, String namespace, String modelUrn, String resourceId, Resource resource) {
  public LearnedResourceSubmission {
    for (var value : new String[] {attemptId, namespace, modelUrn, resourceId})
      if (value == null || value.isBlank()) throw new IllegalArgumentException("Incomplete learning submission");
    Objects.requireNonNull(resource, "resource");
  }
}
