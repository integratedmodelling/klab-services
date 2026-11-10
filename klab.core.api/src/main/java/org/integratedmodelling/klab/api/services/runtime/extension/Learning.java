package org.integratedmodelling.klab.api.services.runtime.extension;

import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/** Shared learning metadata and structural binding names. */
public final class Learning {
  private Learning() {}

  public static final String ANNOTATION = "learning";
  public static final String OBSERVER = "observer";
  public static final String ATTEMPT = "klab.learning.attempt";
  public static final String MODEL = "klab.learning.model";
  public static final String CONTEXT = "klab.learning.context";
  public static final String ACTOR = "klab.learning.actor";
  public static final String RESOURCE = "klab.learning.resource";
  public static final String PREVIEW = "klab.learning.preview";

  public static String name(Observable observable) {
    if (observable == null) throw new IllegalArgumentException("Missing learning observable");
    String name = observable.getStatedName();
    if (name == null || name.isBlank()) name = observable.getReferenceName();
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("Learning observable has no stable binding name");
    return name;
  }

  public static boolean isPreview(Observation observation) {
    return observation != null && observation.getMetadata() != null
        && Boolean.TRUE.equals(observation.getMetadata().get(PREVIEW));
  }

  public static String resourceUrn(String serviceName, String namespace, String id) {
    for (String part : new String[] {serviceName, namespace, id})
      if (part == null || !part.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*"))
        throw new IllegalArgumentException("Invalid local learning resource identity component: " + part);
    return "local:" + serviceName + ":" + namespace + ":" + id;
  }
}
