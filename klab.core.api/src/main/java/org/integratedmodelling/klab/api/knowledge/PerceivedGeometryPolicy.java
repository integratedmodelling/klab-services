package org.integratedmodelling.klab.api.knowledge;

/**
 * Automatic perception maintenance. Future worldview define instructions may select by semantics.
 */
public enum PerceivedGeometryPolicy {
  UNION;

  /** Absence of an articulated policy always means exact union. */
  public static final PerceivedGeometryPolicy DEFAULT = UNION;
}
