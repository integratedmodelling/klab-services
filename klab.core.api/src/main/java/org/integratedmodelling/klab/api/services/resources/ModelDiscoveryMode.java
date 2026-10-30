package org.integratedmodelling.klab.api.services.resources;

/** Request-stage discovery policy; never a model execution grant or persistent model metadata. */
public enum ModelDiscoveryMode {
  /** Default: installed input proof and full semantic matching are required. */
  EXECUTABLE,
  /** Source-valid coarse candidates; Resolver must prepare prerequisites and re-admit before ranking. */
  PREPARABLE;

  public static final String PARAMETER = "modelDiscoveryMode";

  /** Parameters travel as the enum name. Missing mode preserves strict executable discovery. */
  public static ModelDiscoveryMode fromParameter(Object value) {
    if (value == null) return EXECUTABLE;
    if (value instanceof ModelDiscoveryMode mode) return mode;
    if (value instanceof String name) return valueOf(name);
    throw new IllegalArgumentException("Invalid model discovery mode");
  }
}
