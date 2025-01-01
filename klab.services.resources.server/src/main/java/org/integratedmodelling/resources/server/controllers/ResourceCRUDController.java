package org.integratedmodelling.resources.server.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@Tag(name = "Resources CRUD support")
public class ResourceCRUDController {

  @Autowired private ResourcesServer resourcesServer;

  @Autowired private ServiceAuthorizationManager authenticationManager;

  /**
   * GET endpoint that returns uncontextualized resource data to the authorized user. If the
   * resource is public, the endpoint works w/o authentication.
   *
   * @param urn
   * @param principal
   */
  public void getResource(String urn, Principal principal) {}

  /**
   * POST endpoint that takes geometry and (possibly) value URLs for dependencies from the {@link
   * org.integratedmodelling.klab.api.digitaltwin.DigitalTwin} and returns the contextualized data
   * as a flow.
   */
  @Secured(Role.USER)
  public void contextualizeResource(Principal principal) {}

  /** PATCH endpoint that modifies a resource with new content, tracking all operations */
  @Secured(Role.USER)
  public void updateResource(Principal principal) {}

  /**
   * PUT endpoint that creates a new resource, returning the new URN (takes hints for it but may not
   * follow them). Uses multipart data. Note that permissions are handled separately; at creation,
   * the resource is only available to the creating identity
   */
  @Secured(Role.USER)
  public String createResource(Principal principal) {
    return null;
  }

  /**
   * DELETE endpoint that deletes a resource.
   *
   * @param urn
   */
  @Secured(Role.USER)
  public void deleteResource(String urn, Principal principal) {}
}
