package org.integratedmodelling.klab.api.scope;

import java.util.Arrays;

/** The type of persistence associated to an asset. Used in DigitalTwin configuration so far. */
public enum Persistence {
  /**
   * Asset disappears when it's out of scope or garbage collected. Test cases automatically remove
   * any contexts that were created with this persistence, which is their default. Data never
   * survive a runtime shutdown.
   */
  ONE_OFF("Delete when containing scope ends, such as in test cases", false, false),
  /** Asset is deleted after being idle for a set timeout. Data survive a runtime shutdown. */
  IDLE_TIMEOUT("Delete after set timeout", false, true),
  /**
   * Asset is deleted when the service that hosts it is shut down. Data do not survive a runtime
   * shutdown.
   */
  SERVICE_SHUTDOWN("Delete on service shutdown", false, false),
  /**
   * The asset is not deleted on timeout, but it's reset to empty conditions. Used for testing and
   * demos. Data survive a runtime shutdown.
   */
  REINITIALIZED_ON_TIMEOUT("Reset to empty on timeout", false, true),
  /**
   * Asset can only be deleted upon an explicit action from its owner or other authorized identity.
   * Data survive a runtime shutdown.
   */
  EXPLICIT_ACTION("Delete only when asked", true, true);

  Persistence(String description, boolean persistent, boolean survivesShutdown) {
    this.description = description;
    this.persistent = persistent;
    this.survivesShutdown = survivesShutdown;
  }

  public static Persistence fromDescription(String description) {
    return Arrays.stream(values())
        .filter(p -> p.description.equals(description))
        .findFirst()
        .orElse(null);
  }

  public final String description;
  public final boolean persistent;
  public final boolean survivesShutdown;
}
