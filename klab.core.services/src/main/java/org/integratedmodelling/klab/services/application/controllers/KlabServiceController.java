package org.integratedmodelling.klab.services.application.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.h2.util.IOUtils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.Principal;

/**
 * Unsecured information endpoints common to all controllers, inquiring about status and capabilities. If
 * authorization is included in the request the capabilities may reflect the privileges of the calling
 * identity and contain more information.
 */
@RestController
@Tag(name = "Basic inspection")
public class KlabServiceController {

    @Autowired
    ServiceNetworkedInstance<?> instance;

    @Autowired
    private ServiceAuthorizationManager authenticationManager;

    /**
     * Retrieve the capabilities of the service. These have a common part (specified by the
     * {@link org.integratedmodelling.klab.api.services.KlabService.ServiceCapabilities} API) and
     * service-specific components that vary in each service.
     *
     * @return
     */
    @GetMapping(ServicesAPI.CAPABILITIES)
    public KlabService.ServiceCapabilities capabilities(Principal principal) {
        return instance.klabService().capabilities(principal instanceof EngineAuthorization authorization ?
                                                   authorization.getScope() : null);
    }

    /**
     * Return the status of the service at the time of the call. The result schema is specified by the
     * {@link org.integratedmodelling.klab.api.services.KlabService.ServiceStatus} interface and is meant to
     * be accessed quickly and often, to poll the service status for monitoring.
     *
     * @return
     */
    @GetMapping(ServicesAPI.STATUS)
    public KlabService.ServiceStatus status() {
        return instance.klabService().status();
    }


//    @GetMapping(ServicesAPI.DOWNLOAD_ASSET)
    public void downloadAsset(@PathVariable(name="urn") String urn,
                              @RequestParam(name = "format", required = false) String format,
                              @RequestParam(name="version", required = false) String version,
                              @RequestParam(name="accessKey", required = false) String accessKey,
                              HttpServletResponse response,
                              Principal principal) {
        if (principal instanceof EngineAuthorization authorization) {
            response.setContentType(format == null ? MediaType.APPLICATION_OCTET_STREAM.getType() : format);
//            try (var input = instance.klabService().retrieveResource(urn, (version == null ? null :
//                                                                           Version.create(version)),
//                    accessKey, format,
//                    authorization.getScope())) {
//                IOUtils.copy(input, response.getOutputStream());
//            } catch (IOException e) {
//                throw new KlabInternalErrorException(e);
//            }
        }
    }


}
