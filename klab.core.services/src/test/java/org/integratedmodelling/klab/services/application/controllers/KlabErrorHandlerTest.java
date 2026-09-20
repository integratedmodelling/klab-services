package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class KlabErrorHandlerTest {
  @Test
  void preservesContextConflictStatusAndDetail() {
    var response = new KlabErrorHandler().handleStatusException(
        new ResponseStatusException(HttpStatus.CONFLICT, "Context unavailable"));
    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    assertEquals(409, response.getBody().getStatus());
    assertEquals("Context unavailable", response.getBody().getDetail());
  }

  @Test
  void unexpectedFailureHasConsistentInternalErrorStatus() {
    var response = new KlabErrorHandler().handleDefaultException(new IllegalStateException("failed"));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals(500, response.getBody().getBody().getStatus());
  }
}
