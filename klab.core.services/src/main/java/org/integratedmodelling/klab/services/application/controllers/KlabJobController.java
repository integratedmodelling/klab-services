package org.integratedmodelling.klab.services.application.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.runtime.objects.JobStatus;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@Tag(name = "Job management")
public class KlabJobController {

  @GetMapping(ServicesAPI.JOBS.STATUS)
  public JobStatus jobStatus(@PathVariable(name = "id") long id, Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var scope = authorization.getScope();
      if (scope instanceof ServiceSessionScope serviceSessionScope) {
        return serviceSessionScope.getJobManager().status(id);
      }
    }
    throw new KlabIllegalStateException("Unexpected runtime configuration");
  }

  @GetMapping(ServicesAPI.JOBS.CANCEL)
  public boolean cancelJob(@PathVariable(name = "id") long id, Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var scope = authorization.getScope();
      if (scope instanceof ServiceSessionScope serviceSessionScope) {
        return serviceSessionScope.getJobManager().cancel(id);
      }
    }
    throw new KlabIllegalStateException("Unexpected runtime configuration");
  }

  @GetMapping(ServicesAPI.JOBS.RETRIEVE)
  public String retrieveJob(@PathVariable(name = "id") long id, Principal principal) throws Throwable {
    if (principal instanceof EngineAuthorization authorization) {
      var scope = authorization.getScope();
      if (scope instanceof ServiceSessionScope serviceSessionScope) {
        return serviceSessionScope.getJobManager().getResult(id);
      }
    }
    throw new KlabIllegalStateException("Unexpected runtime configuration");
  }
}
