package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

class KlabErrorHandlerTest {
  @Test void springRegistersUniqueSpecificHandlersAndRetainsFallback() {
    var resolver = new org.springframework.web.method.annotation.ExceptionHandlerMethodResolver(KlabErrorHandler.class);
    assertEquals("handleAuthorizationException", resolver.resolveMethodByExceptionType(
        org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException.class).getName());
    assertEquals("handleDefaultException", resolver.resolveMethodByExceptionType(
        org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class).getName());
    assertEquals("handleMissingResource", resolver.resolveMethodByExceptionType(
        org.integratedmodelling.klab.api.exceptions.KlabResourceNotFoundException.class).getName());
    assertEquals("handleInvalidRequest", resolver.resolveMethodByExceptionType(
        org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException.class).getName());
    assertEquals("handleDefaultException", resolver.resolveMethodByExceptionType(IllegalStateException.class).getName());
  }

  @org.springframework.web.bind.annotation.RestController
  static class DeniedController {
    @org.springframework.web.bind.annotation.GetMapping("/denied")
    public void denied() {
      throw new org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException("SECRET_ASSET_IDENTITY");
    }

    @org.springframework.web.bind.annotation.PutMapping("/resource-denied")
    public void resourceDenied() {
      throw new org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException(
          "SECRET_PROJECT_IDENTITY requires CREATE");
    }
  }

  @Test void registeredAdviceReturnsForbiddenProblemWithoutLeakingIdentity() throws Exception {
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
        .standaloneSetup(new DeniedController()).setControllerAdvice(new KlabErrorHandler()).build();
    var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .get("/denied")).andReturn().getResponse();
    assertEquals(403, response.getStatus());
    assertEquals("application/problem+json", response.getContentType());
    var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
    assertEquals(403, body.get("status").asInt());
    assertEquals("Access denied", body.get("detail").asText());
    assertFalse(response.getContentAsString().contains("SECRET_ASSET_IDENTITY"));
  }

  @Test void resourceAccessDenialIsForbiddenRatherThanUnknownServerFailure() throws Exception {
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
        .standaloneSetup(new DeniedController()).setControllerAdvice(new KlabErrorHandler()).build();
    var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .put("/resource-denied")).andReturn().getResponse();
    assertEquals(403, response.getStatus());
    assertEquals("application/problem+json", response.getContentType());
    var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
    assertEquals(403, body.get("status").asInt());
    assertEquals("Access denied", body.get("detail").asText());
    assertFalse(response.getContentAsString().contains("SECRET_PROJECT_IDENTITY"));
    assertFalse(response.getContentAsString().contains("requires CREATE"));
    assertFalse(response.getContentAsString().contains("stackTrace"));
  }

  @Test void serverUnhandledFailureLogsSafeStackAndKeepsErrorResponse() {
    var logging = org.integratedmodelling.common.logging.Logging.INSTANCE;
    var previous = logging.getErrorWriter();
    var output = new java.util.ArrayList<String>();
    try {
      logging.setErrorWriter(output::add);
      var handler = new org.integratedmodelling.klab.services.application.controllers.KlabErrorHandler();
      var response = handler.handleDefaultException(new IllegalStateException("SECRET_SERVER_EXCEPTION"));
      assertEquals(500, response.getStatusCode().value());
      assertEquals(1, output.size());
      assertTrue(output.getFirst().contains("Unhandled service request failure"));
      assertTrue(output.getFirst().contains("serverUnhandledFailureLogsSafeStack"));
      assertFalse(output.getFirst().contains("SECRET_SERVER_EXCEPTION"));
    } finally { logging.setErrorWriter(previous); }
  }


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
