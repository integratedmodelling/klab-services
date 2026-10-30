package org.integratedmodelling.klab.api.knowledge;

import java.util.Set;

/** Independent authored/effective relationship choices; absence is never a default. */
public record RelationshipAxes(Nature nature, Orientation orientation) {
  public enum Nature { UNSPECIFIED, STRUCTURAL, FUNCTIONAL }
  public enum Orientation { UNSPECIFIED, DIRECTED, BOND }
  public static final RelationshipAxes UNSPECIFIED = new RelationshipAxes(Nature.UNSPECIFIED, Orientation.UNSPECIFIED);
  public RelationshipAxes { java.util.Objects.requireNonNull(nature);java.util.Objects.requireNonNull(orientation); }
  public RelationshipAxes merge(RelationshipAxes other) {
    if(nature!=Nature.UNSPECIFIED && other.nature!=Nature.UNSPECIFIED && nature!=other.nature
        || orientation!=Orientation.UNSPECIFIED && other.orientation!=Orientation.UNSPECIFIED && orientation!=other.orientation)
      throw new IllegalArgumentException("RELATIONSHIP_AXIS_CONFLICT: "+this+" versus "+other);
    return new RelationshipAxes(nature==Nature.UNSPECIFIED?other.nature:nature,
        orientation==Orientation.UNSPECIFIED?other.orientation:orientation);
  }
  public RelationshipAxes common(RelationshipAxes other) {
    return new RelationshipAxes(nature==other.nature?nature:Nature.UNSPECIFIED,
        orientation==other.orientation?orientation:Orientation.UNSPECIFIED);
  }
  public static RelationshipAxes of(Set<SemanticType> types) {
    var ret=UNSPECIFIED;
    if(types.contains(SemanticType.STRUCTURAL))ret=ret.merge(new RelationshipAxes(Nature.STRUCTURAL,Orientation.UNSPECIFIED));
    if(types.contains(SemanticType.FUNCTIONAL))ret=ret.merge(new RelationshipAxes(Nature.FUNCTIONAL,Orientation.UNSPECIFIED));
    if(types.contains(SemanticType.UNIDIRECTIONAL))ret=ret.merge(new RelationshipAxes(Nature.UNSPECIFIED,Orientation.DIRECTED));
    if(types.contains(SemanticType.BIDIRECTIONAL))ret=ret.merge(new RelationshipAxes(Nature.UNSPECIFIED,Orientation.BOND));
    return ret;
  }
  public void apply(Set<SemanticType> types) {
    types.removeAll(java.util.EnumSet.of(SemanticType.STRUCTURAL,SemanticType.FUNCTIONAL,SemanticType.UNIDIRECTIONAL,SemanticType.BIDIRECTIONAL));
    if(nature==Nature.STRUCTURAL)types.add(SemanticType.STRUCTURAL);
    if(nature==Nature.FUNCTIONAL)types.add(SemanticType.FUNCTIONAL);
    if(orientation==Orientation.DIRECTED)types.add(SemanticType.UNIDIRECTIONAL);
    if(orientation==Orientation.BOND)types.add(SemanticType.BIDIRECTIONAL);
  }
  public void requireComplete(String name) {
    if(nature==Nature.UNSPECIFIED || orientation==Orientation.UNSPECIFIED)
      throw new IllegalArgumentException("RELATIONSHIP_AXES_UNRESOLVED: concrete "+name+" requires nature and orientation: "+this);
  }
}
