package org.integratedmodelling.klab.api.authentication;

import java.util.List;

/** Service-configured authentication fields. Mechanism identifies the consuming protocol adapter. */
public record CredentialSchema(String id, String label, String mechanism, List<Field> fields) {
  public record Field(String name, String label, boolean secret, boolean required) {}
}
