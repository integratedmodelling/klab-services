package org.integratedmodelling.klab.api.knowledge;

import java.io.Serializable;

/** Restriction evidence, preserving its declared direction. Proportionality is not an algorithm. */
public record SemanticInfluence(Concept source, Concept target, Kind kind, String provenance) implements Serializable {
  public enum Kind {
    AFFECTS,
    CREATES,
    MARKS,
    INCREASES_WITH,
    DECREASES_WITH,
    DISCRETIZES,
    CLASSIFIES;

    public boolean descriptive() { return this != AFFECTS && this != CREATES; }

    public String property() {
      return "odo:" + switch (this) {
        case AFFECTS -> "affects";
        case CREATES -> "creates";
        case MARKS -> "marksQuality";
        case INCREASES_WITH -> "increasesWith";
        case DECREASES_WITH -> "decreasesWith";
        case DISCRETIZES -> "discretizesQuality";
        case CLASSIFIES -> "classifiesQuality";
      };
    }
  }

  /** Compatibility for legacy direct-effect callers. Descriptive plans require directed evidence. */
  public SemanticInfluence(Concept target, Kind kind) { this(null, target, kind, null); }

  /** Descriptive evidence never establishes a computational prerequisite. */
  @Deprecated
  public boolean input() {
    return false;
  }
}
