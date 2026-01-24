package org.integratedmodelling.resources.server.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@Tag(
    name = "Resources CRUD operations",
    description =
        "Endpoints for managing k.LAB resources, bridging to the info, submit, retrieve and delete endpoints of the Resources service")
@Secured(Role.USER)
public class ResourceCRUDController {

  @Autowired private ResourcesServer resourcesServer;

  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE)
  public <T extends KlabAsset> T retrieve(
      String urn,
      @PathVariable(name = "knowledgeClass") KlabAsset.KnowledgeClass assetClass,
      Principal scope) {
    return null;
  }

  @GetMapping(ServicesAPI.RESOURCES.LIST)
  public <T extends KlabAsset> List<T> list(
      @PathVariable(name = "knowledgeClass") KlabAsset.KnowledgeClass assetClass, Principal scope) {
    return List.of();
  }

  @PostMapping(ServicesAPI.RESOURCES.LIST)
  public <T extends KlabAsset> List<T> query(
      @PathVariable(name = "knowledgeClass") KlabAsset.KnowledgeClass assetClass, Principal scope) {
    return List.of();
  }

  @DeleteMapping(ServicesAPI.RESOURCES.DELETE)
  public List<ResourceSet> delete(
      String urn, KlabAsset.KnowledgeClass knowledgeClass, Principal scope) {
    return List.of();
  }

  @GetMapping(ServicesAPI.RESOURCES.RESOLVE)
  public ResourceSet resolve(
      @PathVariable(name = "urn") String urn,
      @PathVariable(name = "knowledgeClass") KlabAsset.KnowledgeClass assetClass,
      Principal principal) {

    return null;
  }

  @PutMapping(ServicesAPI.RESOURCES.SUBMIT)
  public <T> List<ResourceSet> submit(ResourcesService.SubmissionMode submissionMode, Principal scope) {
    return List.of();
  }

  @GetMapping(ServicesAPI.RESOURCES.STATUS)
  public Object status(
      String urn, KlabAsset.KnowledgeClass assetClass, Principal scope) {
    return null;
  }
}
