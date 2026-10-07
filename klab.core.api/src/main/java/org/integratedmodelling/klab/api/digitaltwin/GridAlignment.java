package org.integratedmodelling.klab.api.digitaltwin;

import java.util.List;

/** A resolved, immutable context-wide lattice. Coordinates and steps use working-CRS units. */
public record GridAlignment(int version, String definitionUrn, String fingerprint,
    String projection, double anchorX, double anchorY, double stepX, double stepY,
    boolean strict, boolean snap, List<Double> levels, List<String> warnings,
    String codeDefinition, String serviceId, List<org.integratedmodelling.klab.api.lang.Annotation> annotations) implements org.integratedmodelling.klab.api.knowledge.KlabAsset {
  public static final String SCOPE_KEY = "klab.grid.alignment";
  public GridAlignment(int version, String definitionUrn, String fingerprint, String projection,
      double anchorX, double anchorY, double stepX, double stepY, boolean strict, boolean snap,
      List<Double> levels, List<String> warnings) {
    this(version,definitionUrn,fingerprint,projection,anchorX,anchorY,stepX,stepY,strict,snap,levels,warnings,null,null);
  }
  public GridAlignment(int version, String definitionUrn, String fingerprint, String projection,
      double anchorX, double anchorY, double stepX, double stepY, boolean strict, boolean snap,
      List<Double> levels, List<String> warnings, String codeDefinition, String serviceId) {
    this(version,definitionUrn,fingerprint,projection,anchorX,anchorY,stepX,stepY,strict,snap,levels,warnings,codeDefinition,serviceId,List.of());
  }
  public List<String> emittedWarnings() {
    return org.integratedmodelling.klab.api.lang.NotificationSuppression.suppresses(annotations,org.integratedmodelling.klab.api.services.runtime.Notification.Level.Warning) ? List.of() : warnings;
  }
  public List<org.integratedmodelling.klab.api.lang.Annotation> annotations() {
    return List.copyOf(org.integratedmodelling.klab.api.lang.AnnotationCollector.merge(annotations));
  }
  @Override public String getUrn() { return definitionUrn; }
  @Override public String getServiceId() { return serviceId; }
  @Override public org.integratedmodelling.klab.api.data.Metadata getMetadata() {
    return org.integratedmodelling.klab.api.data.Metadata.create();
  }
  @Override public java.util.Collection<org.integratedmodelling.klab.api.lang.Annotation> getAnnotations() {
    return annotations();
  }

  public GridAlignment {
    if (version != 1 || definitionUrn == null || definitionUrn.isBlank()
        || fingerprint == null || fingerprint.isBlank() || projection == null || projection.isBlank()
        || !Double.isFinite(anchorX) || !Double.isFinite(anchorY)
        || !Double.isFinite(stepX) || stepX <= 0 || !Double.isFinite(stepY) || stepY <= 0)
      throw new IllegalArgumentException("Invalid resolved grid alignment");
    annotations = List.copyOf(org.integratedmodelling.klab.api.lang.AnnotationCollector.merge(annotations));
    levels = List.copyOf(levels);
    warnings = List.copyOf(warnings);
    if (levels.isEmpty()) throw new IllegalArgumentException("A grid requires resolution levels");
    double previous = 0;
    for (double level : levels) {
      if (!Double.isFinite(level) || level <= previous)
        throw new IllegalArgumentException("Grid levels must be finite, positive and increasing");
      if (previous > 0) {
        double ratio = level / previous;
        if (Math.abs(ratio - Math.rint(ratio)) > 1e-9)
          throw new IllegalArgumentException("Grid resolution levels must form a nested integer hierarchy");
      }
      previous = level;
    }
  }
}
