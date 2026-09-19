package org.integratedmodelling.klab.api.knowledge;

import java.io.Serializable;

/** Restriction evidence, preserving its declared direction. Proportionality is not an algorithm. */
public record SemanticInfluence(Concept target, Kind kind) implements Serializable {
  public enum Kind {
    AFFECTS,
    CREATES,
    MARKS,
    INCREASES_WITH,
    DECREASES_WITH
  }

  public boolean input() {
    return kind == Kind.INCREASES_WITH || kind == Kind.DECREASES_WITH;
  }
}
