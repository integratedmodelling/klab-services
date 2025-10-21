package org.integratedmodelling.resources.server.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
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
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.common.data.DataRequest;
import org.integratedmodelling.klab.common.data.ResourceContextualizationRequest;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

@RestController
@Secured(Role.USER)
@Tag(
    name = "Resources service core API",
    description = "Endpoints for managing k.LAB resources, namespaces, and knowledge assets")
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
  @Operation(
      summary = "Resolve projects",
      description =
          "Retrieve all knowledge included in one or more projects with their versions in dependency order")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Projects resolved successfully"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RESOLVE_PROJECTS)
  public @ResponseBody List<ResourceSet> resolveProjects(
      @Parameter(description = "Project identifiers to resolve") @RequestParam
          Collection<String> projects,
      Principal principal) {
    return resourcesServer
        .klabService()
        .resolveProjects(
            projects,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(summary = "Resolve model", description = "Resolve a k.LAB model by its name")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Model resolved successfully"),
    @ApiResponse(responseCode = "404", description = "Model not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RESOLVE_MODEL)
  public @ResponseBody ResourceSet resolveModel(
      @Parameter(description = "Name of the model to resolve") @PathVariable("modelName")
          String modelName,
      Principal principal) {
    return resourcesServer
        .klabService()
        .resolveModel(
            modelName,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(summary = "Resolve URN", description = "Resolve a resource by its URN identifier")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Resource resolved successfully"),
    @ApiResponse(responseCode = "404", description = "Resource not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RESOLVE_URN)
  public @ResponseBody ResourceSet resolve(
      @Parameter(description = "URN of the resource to resolve") @PathVariable("urn") String urn,
      Principal principal) {
    return resourcesServer
        .klabService()
        .resolve(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(summary = "Retrieve namespace", description = "Get a k.LAB namespace by its URN")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Namespace retrieved successfully"),
    @ApiResponse(responseCode = "404", description = "Namespace not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_NAMESPACE)
  public @ResponseBody KimNamespace retrieveNamespace(
      @Parameter(description = "URN of the namespace to retrieve") @PathVariable("urn") String urn,
      Principal principal) {
    return resourcesServer
        .klabService()
        .retrieveNamespace(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(summary = "Retrieve ontology", description = "Get a k.LAB ontology by its URN")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Ontology retrieved successfully"),
    @ApiResponse(responseCode = "404", description = "Ontology not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_ONTOLOGY)
  public @ResponseBody KimOntology retrieveOntology(
      @Parameter(description = "URN of the ontology to retrieve") @PathVariable("urn") String urn,
      Principal principal) {
    return resourcesServer
        .klabService()
        .retrieveOntology(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(
      summary = "Retrieve observation strategy document",
      description = "Get a k.LAB observation strategy document by its URN")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Document retrieved successfully"),
    @ApiResponse(responseCode = "404", description = "Document not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_OBSERVATION_STRATEGY_DOCUMENT)
  public @ResponseBody KimObservationStrategyDocument resolveObservationStrategyDocument(
      @Parameter(description = "URN of the document to retrieve") @PathVariable("urn") String urn,
      Principal principal) {
    return resourcesServer
        .klabService()
        .retrieveObservationStrategyDocument(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(
      summary = "List workspaces",
      description = "Get a list of all available k.LAB workspaces")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Workspaces listed successfully"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.LIST_WORKSPACES)
  public @ResponseBody Collection<Workspace> listWorkspaces() {
    return resourcesServer.klabService().listWorkspaces();
  }

  @Operation(summary = "Retrieve behavior", description = "Get a k.LAB behavior by its URN")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Behavior retrieved successfully"),
    @ApiResponse(responseCode = "404", description = "Behavior not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_BEHAVIOR)
  public @ResponseBody KActorsBehavior retrieveBehavior(
      @Parameter(description = "URN of the behavior to retrieve") @PathVariable("urn") String urn,
      Principal principal) {
    return resourcesServer
        .klabService()
        .retrieveBehavior(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(summary = "Retrieve resource", description = "Get a k.LAB resource by its URN list")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Resource retrieved successfully"),
    @ApiResponse(responseCode = "404", description = "Resource not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @PostMapping(ServicesAPI.RESOURCES.RETRIEVE_RESOURCE)
  public @ResponseBody Resource retrieveResource(
      @Parameter(description = "List of URNs identifying the resource") @RequestBody
          List<String> urns,
      Principal principal) {
    return resourcesServer
        .klabService()
        .retrieveResource(
            urns,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(
      summary = "Contextualize resource",
      description = "Contextualize a k.LAB resource given a contextualization request")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Resource contextualized successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid request"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @PostMapping(ServicesAPI.RESOURCES.CONTEXTUALIZE_RESOURCE)
  public @ResponseBody Resource contextualizeResource(
      @Parameter(description = "Resource contextualization request details") @RequestBody
          ResourceContextualizationRequest request,
      Principal principal) {
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

  @Operation(summary = "Resolve resource", description = "Resolve a k.LAB resource by list of URNs")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Resource resolved successfully"),
    @ApiResponse(responseCode = "404", description = "Resource not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @PostMapping(ServicesAPI.RESOURCES.RESOLVE_RESOURCE)
  public @ResponseBody ResourceSet resolveResource(
      @Parameter(description = "List of URNs to resolve") @RequestBody List<String> urns,
      Principal principal) {
    return resourcesServer
        .klabService()
        .resolveResource(
            urns,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(
      summary = "Resolve resource adapter",
      description = "Resolve a k.LAB resource adapter by URN")
  @GetMapping(ServicesAPI.RESOURCES.RESOLVE_ADAPTER)
  public @ResponseBody ResourceSet resolveAdapter(
      @Parameter(description = "URN of the adapter to retrieve") @PathVariable("urn") String urn,
      Principal principal) {
    return resourcesServer
        .klabService()
        .resolveResourceAdapter(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(summary = "Retrieve workspace", description = "Get a k.LAB workspace by its URN")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Workspace retrieved successfully"),
    @ApiResponse(responseCode = "404", description = "Workspace not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_WORKSPACE)
  public @ResponseBody Workspace resolveWorkspace(
      @Parameter(description = "URN of the workspace to retrieve") @PathVariable("urn") String urn,
      Principal principal) {
    return resourcesServer
        .klabService()
        .retrieveWorkspace(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(
      summary = "Retrieve adapter information",
      description = "Return the adapter information available to this scope")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Adapter retrieved successfully"),
    @ApiResponse(responseCode = "404", description = "Adapter not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_ADAPTER_INFO)
  public @ResponseBody AdapterDescriptor retrieveAdapterInfo(
      @Parameter(description = "URN of the workspace to retrieve") @PathVariable("urn") String urn,
      Principal principal) {
    return resourcesServer
        .klabService()
        .retrieveAdapterInfo(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(
      summary = "Resolve service call",
      description = "Resolve a k.LAB service call by name and optional version")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Service call resolved successfully"),
    @ApiResponse(responseCode = "404", description = "Service call not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RESOLVE_SERVICE_CALL)
  public @ResponseBody ResourceSet resolveServiceCall(
      @Parameter(description = "Name of the service call") @PathVariable("name") String name,
      @Parameter(description = "Optional version of the service call")
          @PathVariable(value = "version", required = false)
          String version,
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

  @Operation(
      summary = "Resolve components providing an export schema",
      description =
          "Resolve a components providing an export schema for a specified media type and optional geometry")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Service call resolved successfully"),
    @ApiResponse(responseCode = "404", description = "Service call not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RESOLVE_EXPORT_SCHEMA)
  public @ResponseBody ResourceSet resolveExportSchema(
      @Parameter(description = "Name of the service call") @RequestParam("mediaType")
          String mediaType,
      @Parameter(description = "Optional version of the service call")
          @RequestParam(value = "geometry", required = false)
          String geometry,
      Principal principal) {
    Geometry g =
        geometry == null
            ? Geometry.EMPTY
            : GeometryRepository.INSTANCE.get(geometry, Geometry.class);
    return resourcesServer
        .klabService()
        .resolveExportSchema(
            mediaType,
            g,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(
      summary = "Resolve components providing an import schema",
      description =
          "Resolve a components providing an import schema for a specified media type and optional geometry")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Service call resolved successfully"),
    @ApiResponse(responseCode = "404", description = "Service call not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RESOLVE_IMPORT_SCHEMA)
  public @ResponseBody ResourceSet resolveImportSchema(
      @Parameter(description = "Name of the service call") @RequestParam("mediaType")
          String mediaType,
      @Parameter(description = "Optional version of the service call")
          @RequestParam(value = "geometry", required = false)
          String geometry,
      Principal principal) {
    Geometry g =
        geometry == null ? null : GeometryRepository.INSTANCE.get(geometry, Geometry.class);
    return resourcesServer
        .klabService()
        .resolveImportSchema(
            mediaType,
            g,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(
      summary = "Get resource info",
      description = "Get information about a k.LAB resource by its URN")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Resource info retrieved successfully"),
    @ApiResponse(responseCode = "404", description = "Resource not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RESOURCE_INFO)
  public @ResponseBody ResourceInfo getResourceInfo(
      @Parameter(description = "URN of the resource") @PathVariable("urn") String urn,
      Principal principal) {
    return resourcesServer
        .klabService()
        .resourceInfo(
            urn,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(
      summary = "Set resource info",
      description = "Update information about a k.LAB resource")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Resource info updated successfully"),
    @ApiResponse(responseCode = "404", description = "Resource not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @PostMapping(ServicesAPI.RESOURCES.RESOURCE_INFO)
  public boolean setResourceInfo(
      @Parameter(description = "URN of the resource") @PathVariable("urn") String urn,
      @Parameter(description = "Updated resource information") @RequestBody
          ResourceInfo resourceInfo,
      Principal principal) {
    return resourcesServer
        .klabService()
        .setResourceInfo(
            urn,
            resourceInfo,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @Operation(
      summary = "Retrieve observable",
      description = "Get a k.LAB observable by its definition")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Observable retrieved successfully"),
    @ApiResponse(responseCode = "404", description = "Observable not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_OBSERVABLE)
  public @ResponseBody KimObservable resolveObservable(
      @Parameter(description = "Definition of the observable") @RequestParam("definition")
          String definition) {
    return resourcesServer.klabService().retrieveObservable(definition);
  }

  @Operation(
      summary = "Describe concept",
      description = "Get a descriptor for a k.LAB concept by its URN")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Concept descriptor retrieved successfully"),
    @ApiResponse(responseCode = "404", description = "Concept not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.DESCRIBE_CONCEPT)
  public @ResponseBody KimConcept.Descriptor describeConcept(
      @Parameter(description = "URN of the concept") @PathVariable("conceptUrn")
          String conceptUrn) {
    return resourcesServer.klabService().describeConcept(conceptUrn);
  }

  @Operation(summary = "Retrieve concept", description = "Get a k.LAB concept by its definition")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Concept retrieved successfully"),
    @ApiResponse(responseCode = "404", description = "Concept not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_CONCEPT)
  public @ResponseBody KimConcept resolveConcept(
      @Parameter(description = "Definition of the concept") @PathVariable("definition")
          String definition) {
    return resourcesServer.klabService().retrieveConcept(definition);
  }

  /**
   * This one creates the DataRequest from the binary input stream coming from the client. The
   * request may include input data in an {@link org.integratedmodelling.klab.common.data.Instance}
   * field.
   *
   * @param requestBody
   * @param principal
   */
  @PostMapping(
      value = ServicesAPI.RESOURCES.CONTEXTUALIZE,
      consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public long contextualize(HttpServletRequest requestBody, Principal principal) {

    if (principal instanceof EngineAuthorization authorization) {

      var scope = authorization.getScope();
      if (scope instanceof ServiceUserScope serviceUserScope) {

        try {
          var decoder = DecoderFactory.get().binaryDecoder(requestBody.getInputStream(), null);
          var reader = new SpecificDatumReader<>(DataRequest.class);
          var request = reader.read(null, decoder);

          var resource =
              resourcesServer
                  .klabService()
                  .retrieveResource(
                      request.getResourceUrns().stream().map(CharSequence::toString).toList(),
                      scope);
          var observable =
              serviceUserScope
                  .getService(Reasoner.class)
                  .resolveObservable(request.getObservable().toString());
          var event = Scheduler.event(request.getStartTime(), request.getEndTime());
          var geometry =
              GeometryRepository.INSTANCE.get(request.getGeometry().toString(), Geometry.class);

          Data input = null;
          if (request.getInputData() != null) {
            input = BaseDataImpl.create(request.getInputData());
          }

          var ret =
              serviceUserScope
                  .getJobManager()
                  .submit(
                      resourcesServer
                          .klabService()
                          .contextualize(
                              resource,
                              DigitalTwin.createObservation(scope, observable, geometry),
                              geometry,
                              event,
                              input,
                              scope),
                      "Resolution of " + observable);

          return ret;

        } catch (Throwable t) {
          throw new KlabIOException(t);
        }
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

  @Operation(summary = "Get worldview", description = "Retrieve the current k.LAB worldview")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Worldview retrieved successfully"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE_WORLDVIEW)
  public @ResponseBody Worldview getWorldview() {
    return resourcesServer.klabService().retrieveWorldview();
  }

  @Operation(
      summary = "Get dependents",
      description = "Get all namespaces that depend on the specified namespace")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Dependents retrieved successfully"),
    @ApiResponse(responseCode = "404", description = "Namespace not found"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.DEPENDENTS)
  public @ResponseBody List<KimNamespace> dependents(
      @Parameter(description = "ID of the namespace") @PathVariable("namespaceId")
          String namespaceId) {
    return resourcesServer.klabService().dependents(namespaceId);
  }

  @GetMapping(ServicesAPI.RESOURCES.PRECURSORS)
  public List<KimNamespace> precursors(@PathVariable("namespaceId") String namespaceId) {
    return resourcesServer.klabService().precursors(namespaceId);
  }

  @GetMapping(ServicesAPI.RESOURCES.QUERY_RESOURCES)
  public @ResponseBody List<ResourceInfo> queryResources(
      @RequestParam("query") String query,
      Principal principal,
      @RequestParam("resourceTypes") KlabAsset.KnowledgeClass... resourceTypes) {

    return resourcesServer
        .klabService()
        .queryResources(
            query,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null,
            resourceTypes);
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
