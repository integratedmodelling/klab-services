package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.List;

/** Separates new role acquisition from a newly committed independent supporting origin. */
public record ConsequenceRoleSupportResult(List<Long> acquiredRecipientIds, boolean supportChanged) {
  public static final String CONTRACT = "role-support-v1";
  public ConsequenceRoleSupportResult {
    acquiredRecipientIds = List.copyOf(acquiredRecipientIds);
    if (acquiredRecipientIds.stream().anyMatch(id -> id == null || id <= 0)
        || !supportChanged && !acquiredRecipientIds.isEmpty())
      throw new IllegalArgumentException("New roles require durable recipients and committed support");
  }
}
