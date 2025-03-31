package org.integratedmodelling.resources.server.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.Principal;
import java.util.Collection;
import java.util.List;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.integratedmodelling.common.data.BaseDataImpl;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceStatus;
import org.integratedmodelling.klab.common.data.DataRequest;
import org.integratedmodelling.klab.common.data.ResourceContextualizationRequest;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

@RestController
@Secured(Role.USER)
@Tag(name = "Resources service core API")
public class ResourcesProviderController {

  @Autowired private ResourcesServer resourcesServer;

  @Autowired private ServiceAuthorizationManager authenticationManager;

  /**
   * Retrieve all the knowledge included in one or more projects. The return set contains all needed
   * documnents with their versions, in order of dependency.
   *
   * @param projects
   * @param principal
   * @return the resources to load to ingest the knowledge included in the requested projects
   */
  @GetMapping(ServicesAPI.RESOURCES.RESOLVE_PROJECTS)
  public @ResponseBody List<ResourceSet> resolveProjects(
      @RequestParam Collection<String> projects, Principal principal) {
    return resourcesServer
        .klabService()
        .resolveProjects(
            projects,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @GetMapping(ServicesAPI.RESOURCES.RESOLVE_MODEL)
  public @ResponseBody ResourceSet resolveModel(
      @PathVariable("modelName") String modelName, Principal principal) {
    return resourcesServer
        .klabService()
        .resolveModel(
            modelName,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @GetMapping(ServicesAPI.RESOURCES.RESOLVE_URN)
  public @ResponseBody ResourceSet resolve(@PathVariable("urn") String urn, Principal principal) {
    return resourcesServer
        .klabService()
        .resolve(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_NAMESPACE)
  public @ResponseBody KimNamespace retrieveNamespace(
      @PathVariable("urn") String urn, Principal principal) {
    return resourcesServer
        .klabService()
        .retrieveNamespace(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_ONTOLOGY)
  public @ResponseBody KimOntology retrieveOntology(
      @PathVariable("urn") String urn, Principal principal) {
    return resourcesServer
        .klabService()
        .retrieveOntology(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_OBSERVATION_STRATEGY_DOCUMENT)
  public @ResponseBody KimObservationStrategyDocument resolveObservationStrategyDocument(
      @PathVariable("urn") String urn, Principal principal) {
    return resourcesServer
        .klabService()
        .retrieveObservationStrategyDocument(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @GetMapping(ServicesAPI.RESOURCES.LIST_WORKSPACES)
  public @ResponseBody Collection<Workspace> listWorkspaces() {
    return resourcesServer.klabService().listWorkspaces();
  }

  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_BEHAVIOR)
  public @ResponseBody KActorsBehavior retrieveBehavior(
      @PathVariable("urn") String urn, Principal principal) {
    return resourcesServer
        .klabService()
        .retrieveBehavior(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @PostMapping(ServicesAPI.RESOURCES.RETRIEVE_RESOURCE)
  public @ResponseBody Resource retrieveResource(
      @RequestBody List<String> urns, Principal principal) {
    return resourcesServer
        .klabService()
        .retrieveResource(
            urns,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @PostMapping(ServicesAPI.RESOURCES.CONTEXTUALIZE_RESOURCE)
  public @ResponseBody Resource contextualizeResource(
      @RequestBody ResourceContextualizationRequest request, Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      return resourcesServer
          .klabService()
          .contextualizeResource(
              resourcesServer
                  .klabService()
                  .retrieveResource(List.of(request.getUrn()), authorization.getScope()),
              GeometryRepository.INSTANCE.get(request.getGeometry(), Geometry.class),
              authorization.getScope());
    }
    throw new KlabInternalErrorException("Resources service: unexpected authorization");
  }

  @PostMapping(ServicesAPI.RESOURCES.RESOLVE_RESOURCE)
  public @ResponseBody ResourceSet resolveResource(
      @RequestBody List<String> urns, Principal principal) {
    return resourcesServer
        .klabService()
        .resolveResource(
            urns,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_WORKSPACE)
  public @ResponseBody Workspace resolveWorkspace(
      @PathVariable("urn") String urn, Principal principal) {
    return resourcesServer
        .klabService()
        .retrieveWorkspace(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @GetMapping(ServicesAPI.RESOURCES.RESOLVE_SERVICE_CALL)
  public @ResponseBody ResourceSet resolveServiceCall(
      @PathVariable("name") String name,
      @PathVariable(value = "version", required = false) String version,
      Principal principal) {
    Version v = version == null ? null : Version.create(version);
    return resourcesServer
        .klabService()
        .resolveServiceCall(
            name,
            v,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @GetMapping(ServicesAPI.RESOURCES.RESOURCE_STATUS)
  public @ResponseBody ResourceStatus resourceStatus(
      @PathVariable("urn") String urn, Principal principal) {
    return resourcesServer
        .klabService()
        .resourceStatus(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_OBSERVABLE)
  public @ResponseBody KimObservable resolveObservable(
      @RequestParam("definition") String definition) {
    return resourcesServer.klabService().retrieveObservable(definition);
  }

  @GetMapping(ServicesAPI.RESOURCES.DESCRIBE_CONCEPT)
  public @ResponseBody KimConcept.Descriptor describeConcept(
      @PathVariable("conceptUrn") String conceptUrn) {
    return resourcesServer.klabService().describeConcept(conceptUrn);
  }

  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_CONCEPT)
  public @ResponseBody KimConcept resolveConcept(@PathVariable("definition") String definition) {
    return resourcesServer.klabService().retrieveConcept(definition);
  }

  /**
   * This one creates the DataRequest from the binary input stream coming from the client. The
   * request may include input data in an {@link org.integratedmodelling.klab.common.data.Instance}
   * field.
   *
   * @param requestStream
   * @param response
   * @param principal
   */
  @PostMapping(
      value = ServicesAPI.RESOURCES.CONTEXTUALIZE,
      consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public void contextualize(
      InputStream requestStream, HttpServletResponse response, Principal principal) {

    if (principal instanceof EngineAuthorization authorization) {

      try {
        var decoder = DecoderFactory.get().binaryDecoder(requestStream, null);
        var reader = new SpecificDatumReader<>(DataRequest.class);
        var request = reader.read(null, decoder);
        var scope = authorization.getScope();
        var resource =
            resourcesServer
                .klabService()
                .retrieveResource(
                    request.getResourceUrns().stream().map(CharSequence::toString).toList(), scope);
        var observable =
            resourcesServer
                .klabService()
                .serviceScope()
                .getService(Reasoner.class)
                .resolveObservable(request.getObservable().toString());

        var geometry =
            GeometryRepository.INSTANCE.get(request.getGeometry().toString(), Geometry.class);

        Data input = null;
        if (request.getInputData() != null) {
          input = BaseDataImpl.create(request.getInputData());
        }

        Data data =
            resourcesServer
                .klabService()
                .contextualize(
                    resource,
                    DigitalTwin.createObservation(scope, observable, geometry),
                    input,
                    scope);

        if (data instanceof BaseDataImpl dataImpl) {
          try {
            var output = response.getOutputStream();
            dataImpl.copyTo(output);
            output.flush();
            return;
          } catch (Throwable t) {
            throw new KlabResourceAccessException(t);
          }
        }
      } catch (IOException e) {
        throw new KlabIOException(e);
      }
    }

    throw new KlabIllegalStateException(
        "Resource contextualizer: found unexpected implementations");
  }

  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_DATAFLOW)
  public @ResponseBody KimObservationStrategyDocument resolveDataflow(
      @PathVariable("urn") String urn, Principal principal) {
    return resourcesServer
        .klabService()
        .retrieveDataflow(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_WORLDVIEW)
  public @ResponseBody Worldview getWorldview() {
    return resourcesServer.klabService().retrieveWorldview();
  }

  @GetMapping(ServicesAPI.RESOURCES.DEPENDENTS)
  public @ResponseBody List<KimNamespace> dependents(
      @PathVariable("namespaceId") String namespaceId) {
    return resourcesServer.klabService().dependents(namespaceId);
  }

  @GetMapping(ServicesAPI.RESOURCES.PRECURSORS)
  public List<KimNamespace> precursors(@PathVariable("namespaceId") String namespaceId) {
    return resourcesServer.klabService().precursors(namespaceId);
  }

  @GetMapping(ServicesAPI.RESOURCES.QUERY_RESOURCES)
  public List<String> queryResources(
      @RequestParam("urnPattern") String urnPattern,
      @RequestParam("resourceTypes") KlabAsset.KnowledgeClass... resourceTypes) {
    return resourcesServer.klabService().queryResources(urnPattern, resourceTypes);
  }

  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_PROJECT)
  public @ResponseBody Project retrieveProject(
      @PathVariable("projectName") String projectName, Principal principal) {
    return resourcesServer
        .klabService()
        .retrieveProject(
            projectName,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @PostMapping(ServicesAPI.RESOURCES.RESOLVE_MODELS)
  public @ResponseBody ResourceSet queryModels(
      @RequestBody ResolutionRequest request, Principal principal) {
    return resourcesServer
        .klabService()
        .resolveModels(
            request.getObservable(),
            principal instanceof EngineAuthorization authorization
                ? authorization
                    .getScope(ContextScope.class)
                    .withResolutionConstraints(
                        request.getResolutionConstraints().toArray(new ResolutionConstraint[0]))
                : null);
  }

  @GetMapping(ServicesAPI.RESOURCES.MODEL_GEOMETRY)
  public @ResponseBody Coverage modelGeometry(@PathVariable("modelUrn") String modelUrn) {
    return resourcesServer.klabService().modelGeometry(modelUrn);
  }

  @GetMapping(ServicesAPI.RESOURCES.READ_BEHAVIOR)
  public @ResponseBody KActorsBehavior readBehavior(@RequestParam("url") URL url) {
    return resourcesServer.klabService().readBehavior(url);
  }

  @GetMapping(ServicesAPI.RESOURCES.RESOURCE_RIGHTS)
  public ResourcePrivileges getResourceRights(
      @PathVariable("urn") String urn, Principal principal) {
    return resourcesServer
        .klabService()
        .getRights(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @PutMapping(ServicesAPI.RESOURCES.RESOURCE_RIGHTS)
  public boolean setResourceRights(
      @PathVariable("urn") String urn,
      @RequestBody ResourcePrivileges resourcePrivileges,
      Principal principal) {
    return resourcesServer
        .klabService()
        .setRights(
            urn,
            resourcePrivileges,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }
}
