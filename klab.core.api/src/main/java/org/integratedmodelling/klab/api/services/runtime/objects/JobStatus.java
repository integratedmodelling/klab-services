package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.scope.Scope;

/**
 * The result of a {@link org.integratedmodelling.klab.api.ServicesAPI.JOBS#STATUS} request.
 * Basically a status code plus a stack trace in case of errors.
 */
public class JobStatus {
  private Scope.Status status;
  private String stackTrace;

  public Scope.Status getStatus() {
    return status;
  }

  public void setStatus(Scope.Status status) {
    this.status = status;
  }

  public String getStackTrace() {
    return stackTrace;
  }

  public void setStackTrace(String stackTrace) {
    this.stackTrace = stackTrace;
  }
}
