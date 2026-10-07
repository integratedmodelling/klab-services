package org.integratedmodelling.klab.api.exceptions;

/** Requested asset is absent, or is not visible to the requesting identity. */
public class KlabResourceNotFoundException extends KlabResourceAccessException {
  public KlabResourceNotFoundException(String message) {
    super(message);
  }
}
