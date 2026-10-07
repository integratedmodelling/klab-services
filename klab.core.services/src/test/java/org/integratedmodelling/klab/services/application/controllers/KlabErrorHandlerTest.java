package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

class KlabErrorHandlerTest {
  @org.springframework.web.bind.annotation.RestController
  static class FailingExportController {
    @org.springframework.web.bind.annotation.GetMapping("/export")
    public void export() {
      throw new IllegalArgumentException("Invalid color ramp");
    }
  }

  @Test
  void binaryExportErrorsRemainReadableDespiteImageOnlyAccept() throws Exception {
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
        .standaloneSetup(new FailingExportController())
        .setControllerAdvice(new KlabErrorHandler()).build();
    for (var mediaType : new String[] {"image/png", "image/tiff", "application/geo+json"}) {
      var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
          .get("/export").accept(mediaType)).andReturn().getResponse();
      assertEquals(500, response.getStatus());
      assertEquals("application/json", response.getContentType());
      org.junit.jupiter.api.Assertions.assertTrue(response.getContentAsString().contains("Invalid color ramp"));
    }
  }

  @Test
  void preservesContextConflictStatusAndDetail() {
    var response = new KlabErrorHandler().handleStatusException(
        new ResponseStatusException(HttpStatus.CONFLICT, "Context unavailable"));
    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
    assertEquals(409, response.getBody().getStatus());
    assertEquals("Context unavailable", response.getBody().getDetail());
  }

  @Test
  void unexpectedFailureHasConsistentInternalErrorStatus() {
    var response = new KlabErrorHandler().handleDefaultException(new IllegalStateException("failed"));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
    assertEquals(500, response.getBody().getBody().getStatus());
  }
}
