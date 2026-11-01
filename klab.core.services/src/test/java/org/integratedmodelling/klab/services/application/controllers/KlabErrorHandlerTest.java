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

    @org.springframework.web.bind.annotation.GetMapping("/domain-error/{status}")
    public void domainError(@org.springframework.web.bind.annotation.PathVariable("status") int status) {
      switch (status) {
        case 403 -> throw new org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException("secret identity");
        case 404 -> throw new org.integratedmodelling.klab.api.exceptions.KlabResourceNotFoundException("secret asset");
        default -> throw new org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException("secret argument");
      }
    }
  }

  @Test
  void domainErrorsHaveOneHandlerAndRemainReadableForJsonAndBinaryAccepts() throws Exception {
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
        .standaloneSetup(new FailingExportController())
        .setControllerAdvice(new KlabErrorHandler()).build();
    for (var accept : new String[] {"application/json", "image/tiff", "application/geo+json"}) {
      for (int status : new int[] {400, 403, 404}) {
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .get("/domain-error/" + status).accept(accept)).andReturn().getResponse();
        assertEquals(status, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        org.junit.jupiter.api.Assertions.assertFalse(response.getContentAsString().contains("secret"));
      }
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
