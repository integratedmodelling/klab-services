package org.integratedmodelling.klab.api.services.resolver.objects;

import org.integratedmodelling.klab.api.knowledge.KlabAsset;

import java.net.URL;
import java.util.*;

/**
 * Comes with authentication and context scope (including observer) in the
 * {@link org.integratedmodelling.klab.api.ServicesAPI#SCOPE_HEADER} header. Any context options that have
 * generated a new context scope must be encoded in the linked
 * {@link org.integratedmodelling.klab.api.scope.ContextScope}. theThe scenarios and options are set through
 * context options but will be sent only at observation request time.
 */
public class ResolutionRequest {

    private URL serviceUrl;
    private String urn;
    private KlabAsset.KnowledgeClass resolvableType;
    private Map<String, Object> options = new HashMap<>();

    // from this point on, fields collect the options in the context scope tree
    private List<String> scenarios = new ArrayList<>();
    private String observerId;
    private String contextObservationId;
    private String resolutionNamespaceUrn;
    private Map<String, String> traitResolution = new LinkedHashMap<>();

    public String getContextObservationId() {
        return contextObservationId;
    }

    public void setContextObservationId(String contextObservationId) {
        this.contextObservationId = contextObservationId;
    }

    public String getObserverId() {
        return observerId;
    }

    public void setObserverId(String observerId) {
        this.observerId = observerId;
    }

    public String getResolutionNamespaceUrn() {
        return resolutionNamespaceUrn;
    }

    public void setResolutionNamespaceUrn(String resolutionNamespaceUrn) {
        this.resolutionNamespaceUrn = resolutionNamespaceUrn;
    }

    public List<String> getScenarios() {
        return scenarios;
    }

    public void setScenarios(List<String> scenarios) {
        this.scenarios = scenarios;
    }

    public Map<String, String> getTraitResolution() {
        return traitResolution;
    }

    public void setTraitResolution(Map<String, String> traitResolution) {
        this.traitResolution = traitResolution;
    }

    /**
     * Controls the services used if they must be different from the default (services linked to the scope as
     * first choice, then those in service scope if user is local). Should be null normally.
     * <p>
     * TODO see if this is needed.
     *
     * @return
     */
    public URL getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(URL serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public String getUrn() {
        return urn;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    public KlabAsset.KnowledgeClass getResolvableType() {
        return resolvableType;
    }

    public void setResolvableType(KlabAsset.KnowledgeClass resolvableType) {
        this.resolvableType = resolvableType;
    }

}
