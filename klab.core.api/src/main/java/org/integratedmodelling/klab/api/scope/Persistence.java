package org.integratedmodelling.klab.api.scope;

import java.util.Arrays;

/** The type of persistence associated to an asset. */
public enum Persistence {
  /** Asset disappears when it's out of scope or garbage collected. */
  ONE_OFF("Delete at editor closed"),
  /** Asset is deleted after being idle for a set timeout. */
  IDLE_TIMEOUT("Delete after set timeout"),
  /** Asset is deleted when the service that hosts it is shut down. */
  SERVICE_SHUTDOWN("Delete on service shutdown"),
  /**
   * The asset is not deleted on timeout, but it's reset to empty conditions. Used for testing and
   * demos.
   */
  REINITIALIZED_ON_TIMEOUT("Reset to empty on timeout"),
  /**
   * Asset can only be deleted upon an explicit action from its owner or other authorized identity.
   */
  EXPLICIT_ACTION("Delete only when asked");

  Persistence(String description) {
    this.description = description;
  }

  public static Persistence fromDescription(String description) {
    return Arrays.stream(values())
        .filter(p -> p.description.equals(description))
        .findFirst()
        .orElse(null);
  }

  public String description;
}
