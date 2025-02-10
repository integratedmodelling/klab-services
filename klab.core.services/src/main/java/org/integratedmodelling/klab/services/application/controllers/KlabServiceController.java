package org.integratedmodelling.klab.services.application.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.h2.util.IOUtils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;

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
      HttpServletResponse response,
      Principal principal) {

    if (principal instanceof EngineAuthorization authorization) {

      var scope = authorization.getScope();
      // retrieve schema. TODO not handling authorization yet
      var schemata =
          ResourceTransport.INSTANCE.findExportSchemata(
              knowledgeClass, mediaType, instance.klabService().capabilities(scope), scope);

      if (schemata.isEmpty()) {
        throw new KlabAuthorizationException(
            "No authorized export schema with media type " + mediaType + " is available");
      } else if (schemata.size() > 1) {
        scope.warn(
            "Ambiguous request: more than one export schema with "
                + "media type "
                + mediaType
                + " is available");
      }

      var stream = instance.klabService().exportAsset(urn, schemata.getFirst(), mediaType, scope);
      if (stream == null) {
        throw new KlabResourceAccessException(
            "Service cannot stream the asset identified by " + urn);
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
  public String importAsset(
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

      return instance.klabService().importAsset(s, s.asset(data), urn, scope);
    }
    return null;
  }

  @PostMapping(value = ServicesAPI.IMPORT, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public String uploadAsset(
      @PathVariable(name = "schema") String schema,
      @PathVariable(name = "urn") String urn,
      @RequestParam("file") MultipartFile file,
      Principal principal) {
    String ret = urn;
    if (principal instanceof EngineAuthorization authorization) {}

    return ret;
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
