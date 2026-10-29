package org.integratedmodelling.klab.api.knowledge;

/** Recipe identity format only; scoped dependency witnesses separately certify authority use. */
public final class SemanticContentFingerprint {
  private SemanticContentFingerprint() {}
  public static final String VERSION = "semantic-content-v2";
  public static boolean valid(String value) {
    return value != null && value.matches("semantic-content-v[12]:[0-9a-f]{64}");
  }
  public static boolean scoped(String value) {
    return value != null && value.matches(VERSION + ":[0-9a-f]{64}");
  }
}
