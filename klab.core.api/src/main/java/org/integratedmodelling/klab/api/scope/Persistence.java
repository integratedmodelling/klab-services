package org.integratedmodelling.klab.api.scope;

/** The type of persistence associated to an asset. */
public enum Persistence {
  /** Asset disappears when it's out of scope or garbage collected. */
  ONE_OFF,
  /** Asset is deleted after being idle for a set timeout. */
  IDLE_TIMEOUT,
  /** Asset is deleted when the service that hosts it is shut down. */
  SERVICE_SHUTDOWN,
  /**
   * The asset is not deleted on timeout, but it's reset to empty conditions. Used for testing and
   * demos.
   */
  REINITIALIZED_ON_TIMEOUT,
  /**
   * Asset can only be deleted upon an explicit action from its owner or other authorized identity.
   */
  EXPLICIT_ACTION
}
