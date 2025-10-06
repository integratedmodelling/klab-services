package org.integratedmodelling.klab.services.application.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.*;
import org.integratedmodelling.common.services.client.ServiceClientCatalog;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.objects.UserScopeNotification;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Scope management")
public class KlabScopeController {

  @Autowired ServiceNetworkedInstance<?> instance;

  @PostMapping(ServicesAPI.NOTIFY_USER_SCOPE)
  public boolean notifyUserScope(@RequestBody UserScopeNotification request, Principal principal) {

    if (principal instanceof EngineAuthorization authorization) {

      var userScope = authorization.getScope(ServiceUserScope.class);
      if (userScope == null) {
        return false;
      }
      return setupUserScope(userScope, request, instance.klabService());
    }
    return false;
  }

  /**
   * Ensure we have clients for all services in the request; if so, create personalized clients for
   * the user scope and set the clients in it. If any service has the same ID of the embedding
   * service, use that instead of creating a client. Return true if all clients were set up
   * correctly.
   *
   * @param userScope
   * @param request
   * @return
   */
  public boolean setupUserScope(
      ServiceUserScope userScope, UserScopeNotification request, KlabService ownerService) {
    for (var serviceInfo : request.getServices()) {
      var service =
          ownerService.serviceId().equals(serviceInfo.getId())
              ? ownerService
              : ServiceClientCatalog.INSTANCE.getService(serviceInfo, ownerService, userScope);
      userScope.addService(service);
    }
    return userScope.validateServices();
  }

}
