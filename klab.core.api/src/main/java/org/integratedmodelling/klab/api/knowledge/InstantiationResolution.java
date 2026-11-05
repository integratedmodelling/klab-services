package org.integratedmodelling.klab.api.knowledge;

import java.util.Collection;
import java.util.List;
import org.integratedmodelling.klab.api.lang.Annotation;

/** Planning policy of one instantiator contribution; never inherited by dependency submissions. */
public final class InstantiationResolution {
  private InstantiationResolution() {}
  public static final String ANNOTATION = "resolution";
  public static final String DATA_PREFIX = "klab.instantiation.resolution.";
  public enum Mode { NONE, COLLECTIVE, INDIVIDUAL }

  public record Declaration(String model, Mode mode, Annotation annotation, String support) {
    public Declaration(String model, Mode mode, Annotation annotation) { this(model, mode, annotation, null); }
    public Declaration {
      if (mode == null) throw new IllegalArgumentException("Missing instantiation resolution mode");
      if (InstantiationResolution.mode(annotation) != mode)
        throw new IllegalArgumentException("Instantiation mode differs from its authored annotation");
    }
  }
  public record Contribution(Declaration declaration, int computation, String support) {}

  public static Declaration declaration(Model model) {
    annotation(model.getAnnotations());
    var primary = model.getObservables().isEmpty() ? null : model.getObservables().getFirst();
    var effective = new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
    if (primary != null) {
      annotation(primary.getAnnotations());
      effective.setObservable(primary);
      effective.mergeAnnotations(primary.getSemantics().getAnnotations(),
          org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl.CONCEPT_ANNOTATIONS);
      effective.mergeAnnotations(primary.getAnnotations(),
          org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl.MODEL_ANNOTATIONS);
    }
    effective.mergeAnnotations(model.getAnnotations(),
        org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl.MODEL_ANNOTATIONS);
    var annotation = annotation(effective.getAnnotations());
    if (primary == null || !primary.getSemantics().isCollective()
        || primary.getContextualization() != Contextualization.INSTANTIATION) {
      if (annotation != null) throw new IllegalArgumentException("@resolution requires an each instantiator model");
      return null;
    }
    return new Declaration(model.getUrn(), mode(annotation), annotation,
        model.getCoverage() == null || model.getCoverage().isUniversal() ? null
            : org.integratedmodelling.klab.api.geometry.Geometry.forTransport(model.getCoverage()).encode());
  }

  public static Annotation annotation(Collection<Annotation> annotations) {
    Annotation result = null;
    for (var annotation : annotations) {
      if (annotation.getName() == null || !ANNOTATION.equalsIgnoreCase(annotation.getName())) continue;
      if (!ANNOTATION.equals(annotation.getName()))
        throw new IllegalArgumentException("Use lowercase @resolution");
      mode(annotation);
      if (result != null && !result.equals(annotation))
        throw new IllegalArgumentException("Conflicting @resolution annotations");
      result = annotation;
    }
    return result;
  }

  public static Mode mode(Annotation annotation) {
    if (annotation == null) return Mode.INDIVIDUAL;
    var positional = annotation.getUnnamedArguments();
    var named = annotation.get(Annotation.VALUE_PARAMETER_KEY);
    if (positional.size() > 1 || named != null && !positional.isEmpty())
      throw new IllegalArgumentException("@resolution requires exactly one mode");
    for (var key : annotation.keySet())
      if (!key.startsWith("#") && !annotation.getUnnamedKeys().contains(key)
          && !List.of("value", "override").contains(key))
        throw new IllegalArgumentException("Unknown @resolution parameter: " + key);
    var value = named != null ? named : positional.isEmpty() ? null : positional.getFirst();
    if (value == null) throw new IllegalArgumentException("@resolution requires none, collective or individual");
    return switch (value.toString()) {
      case "none" -> Mode.NONE;
      case "collective" -> Mode.COLLECTIVE;
      case "individual" -> Mode.INDIVIDUAL;
      default -> throw new IllegalArgumentException("Unknown @resolution mode: " + value);
    };
  }
}
