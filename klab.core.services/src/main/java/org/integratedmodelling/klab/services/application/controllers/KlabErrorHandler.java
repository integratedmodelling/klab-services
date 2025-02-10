package org.integratedmodelling.klab.services.application.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;

@ControllerAdvice
public class KlabErrorHandler {

  @Autowired ServiceNetworkedInstance<?> service;

  @Autowired ServiceAuthorizationManager scopeManager;

  @ExceptionHandler(NoHandlerFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public @ResponseBody ResponseEntity<ErrorResponse> handleNoMethodException(
      HttpServletRequest request, NoHandlerFoundException ex) {
    ErrorResponse errorResponse =
        ErrorResponse.create(ex, HttpStatus.NOT_FOUND, "Dio cane, handler " + "not found");
    ex.printStackTrace();
    return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(Throwable.class)
  public @ResponseBody ResponseEntity<ErrorResponse> handleDefaultException(Throwable ex) {
    ErrorResponse errorResponse =
        ErrorResponse.create(ex, HttpStatus.BAD_REQUEST, "Dio cane, empty " + "body or other shit");
    ex.printStackTrace();
    return new ResponseEntity<ErrorResponse>(errorResponse, HttpStatus.BAD_REQUEST);
  }
}
