package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.klab.api.exceptions.*;
import org.junit.jupiter.api.Test;

class DomainErrorMappingTest {
  @Test void restBoundaryMapsDomainErrorsWithoutExposingExceptionDiagnostics() {
    var handler = new KlabErrorHandler();
    var denied = handler.handleAccessDenied(new KlabAuthorizationException("sensitive identity"));
    assertEquals(403, denied.getStatusCode().value());
    assertFalse(denied.getBody().getDetail().contains("sensitive"));
    assertEquals(404, handler.handleMissingResource(new KlabResourceNotFoundException("missing")).getStatusCode().value());
    assertEquals(400, handler.handleInvalidRequest(new KlabIllegalArgumentException("bad")).getStatusCode().value());
  }
}
