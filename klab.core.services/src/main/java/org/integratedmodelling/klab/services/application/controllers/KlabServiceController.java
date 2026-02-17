package org.integratedmodelling.klab.services.application.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unsecured information endpoints common to all controllers, inquiring about status and
 * capabilities. If authorization is included in the request the capabilities may reflect the
 * privileges of the calling identity and contain more information.
 */
@RestController
@Tag(name = "Basic inspection")
public class KlabServiceController {

  @Autowired ServiceNetworkedInstance<?> instance;

  @Autowired private ServiceAuthorizationManager authenticationManager;

  /**
   * Retrieve the capabilities of the service. These have a common part (specified by the {@link
   * org.integratedmodelling.klab.api.services.KlabService.ServiceCapabilities} API) and
   * service-specific components that vary in each service.
   *
   * @return
   */
  @GetMapping(ServicesAPI.CAPABILITIES)
  public KlabService.ServiceCapabilities capabilities(Principal principal) {
    return instance
        .klabService()
        .capabilities(
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  /**
   * Return the status of the service at the time of the call. The result schema is specified by the
   * {@link org.integratedmodelling.klab.api.services.KlabService.ServiceStatus} interface and is
   * meant to be accessed quickly and often, to poll the service status for monitoring.
   *
   * @return
   */
  @GetMapping(ServicesAPI.STATUS)
  public KlabService.ServiceStatus status() {
    return instance.klabService().status();
  }

  /**
   * Exporting uses content negotiation to find the schema. The Accept header must be set in the
   * request unless there is only one alternative.
   *
   * @param urn
   * @param knowledgeClass
   * @param mediaType
   * @param response
   * @param principal
   */
  @GetMapping(ServicesAPI.EXPORT)
  public void exportAsset(
      @PathVariable(name = "urn") String urn,
      @PathVariable(name = "class") KlabAsset.KnowledgeClass knowledgeClass,
      @RequestHeader(HttpHeaders.ACCEPT) String mediaType,
      @RequestParam(required = false) Map<String, String> parameters,
      HttpServletResponse response,
      Principal principal) {

    if (principal instanceof EngineAuthorization authorization) {

      var scope = authorization.getScope();

      Parameters<String> params = Parameters.create();
      parameters.forEach((k, v) -> params.put(k, Utils.Data.asPOD(v)));

      var stream =
          instance.klabService().exportAsset(urn, knowledgeClass, mediaType, params, scope);
      if (stream == null) {

        // see if we have the referenced asset. In that case we can check for specific export
        // schemata based on identity, geometry and metadata before we give up.
        var asset = instance.klabService().resolveUrn(urn, knowledgeClass, scope);
        if (asset instanceof Observation observation
            && instance
                .klabService()
                .serviceId()
                .equals(observation.getContextualizationData().getServiceId())) {
          // observation available in local storage - data are here, find export based on geometry

        } else {

          throw new KlabResourceAccessException(
              "Service cannot stream the asset identified by " + urn);
        }
      }

      try {
        response.setContentType(mediaType);
        IOUtils.copy(stream, response.getOutputStream());
        stream.close();
      } catch (IOException e) {
        throw new KlabInternalErrorException(e);
      }
    }
  }

  @PostMapping(value = ServicesAPI.IMPORT, consumes = MediaType.APPLICATION_JSON_VALUE)
  public long importAsset(
      @PathVariable(name = "schema") String schema,
      @PathVariable(name = "urn") String urn,
      @RequestBody Parameters<String> data,
      Principal principal) {

    if (principal instanceof EngineAuthorization authorization) {

      var scope = authorization.getScope();
      // retrieve schema. TODO not handling authorization yet
      var s =
          ResourceTransport.INSTANCE.findSchema(
              schema, instance.klabService().capabilities(scope).getImportSchemata(), scope);
      if (s == null) {
        throw new KlabAuthorizationException(
            "No authorized import schema for property-based " + "submissions is available");
      }
      return ((ServiceUserScope) scope)
          .getJobManager()
          .submit(
              instance.klabService().importAsset(s, s.asset(data), urn, scope),
              "Import of asset using schema " + s.getSchemaId());
    }
    return -1;
  }

  @PostMapping(value = ServicesAPI.IMPORT, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public long uploadAsset(
      @PathVariable(name = "schema") String schema,
      @PathVariable(name = "urn") String urn,
      @RequestParam("file") MultipartFile file,
      Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      try {
        var scope = authorization.getScope();
        var s =
            ResourceTransport.INSTANCE.findSchema(
                schema, instance.klabService().capabilities(scope).getImportSchemata(), scope);
        if (s == null) {
          throw new KlabAuthorizationException(
              "No authorized import schema for property-based submissions is available");
        }

        String originalFilename = file.getOriginalFilename();
        String extension =
            originalFilename != null
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        Path tempFile = Files.createTempFile("upload", extension);
        file.transferTo(tempFile.toFile());
        var result =
            ((ServiceUserScope) scope)
                .getJobManager()
                .submit(
                    instance.klabService().importAsset(s, s.asset(tempFile.toFile()), urn, scope),
                    "Upload of asset using schema " + s.getSchemaId());
        tempFile.toFile().deleteOnExit();
        return result;
      } catch (IOException e) {
        throw new KlabInternalErrorException(
            "Error processing uploaded file: " + e.getMessage(), e);
      }
    }
    return -1;
  }

  //    @GetMapping(ServicesAPI.DOWNLOAD_ASSET)
  public void downloadAsset(
      @PathVariable(name = "urn") String urn,
      @RequestParam(name = "format", required = false) String format,
      @RequestParam(name = "version", required = false) String version,
      @RequestParam(name = "accessKey", required = false) String accessKey,
      HttpServletResponse response,
      Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      response.setContentType(
          format == null ? MediaType.APPLICATION_OCTET_STREAM.getType() : format);
      //            try (var input = instance.klabService().retrieveResource(urn, (version == null ?
      //            null :
      //                                                                           Version.create
      //                                                                           (version)),
      //                    accessKey, format,
      //                    authorization.getScope())) {
      //                IOUtils.copy(input, response.getOutputStream());
      //            } catch (IOException e) {
      //                throw new KlabInternalErrorException(e);
      //            }
    }
  }
}
