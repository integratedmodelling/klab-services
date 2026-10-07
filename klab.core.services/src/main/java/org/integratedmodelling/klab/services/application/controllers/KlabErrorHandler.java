package org.integratedmodelling.klab.services.application.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;

@ControllerAdvice
public class KlabErrorHandler {

  @ExceptionHandler(org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(RuntimeException exception) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Asset is not accessible"));
  }

  @ExceptionHandler(org.integratedmodelling.klab.api.exceptions.KlabResourceNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleMissingResource(RuntimeException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Asset is absent or not accessible"));
  }

  @ExceptionHandler(org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleInvalidRequest(RuntimeException exception) {
    return ResponseEntity.badRequest()
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request"));
  }

  @Autowired ServiceNetworkedInstance<?> service;

  @Autowired ServiceAuthorizationManager scopeManager;

  @ExceptionHandler(NoHandlerFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public @ResponseBody ResponseEntity<ErrorResponse> handleNoMethodException(
      HttpServletRequest request, NoHandlerFoundException ex) {
    ErrorResponse errorResponse =
        ErrorResponse.create(ex, HttpStatus.NOT_FOUND, Utils.Exceptions.stackTrace(ex));
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_JSON).body(errorResponse);
  }

  @ExceptionHandler(ErrorResponseException.class)
  public ResponseEntity<ProblemDetail> handleStatusException(
      ErrorResponseException ex) {
    return ResponseEntity.status(ex.getStatusCode()).headers(ex.getHeaders())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(ex.getBody());
  }

  @ExceptionHandler(Throwable.class)
  public @ResponseBody ResponseEntity<ErrorResponse> handleDefaultException(Throwable ex) {
    ErrorResponse errorResponse =
        ErrorResponse.create(ex, HttpStatus.INTERNAL_SERVER_ERROR, Utils.Exceptions.stackTrace(ex));
    // Binary exports accept only their requested image/data format. Errors still need a JSON
    // representation, otherwise negotiation fails and the error dispatch masks the real failure.
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_JSON).body(errorResponse);
  }
}
