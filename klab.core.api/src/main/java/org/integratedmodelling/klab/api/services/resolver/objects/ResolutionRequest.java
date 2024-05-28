package org.integratedmodelling.klab.api.services.resolver.objects;

import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comes with authentication and context scope (including observer) in the Observer header. The scenarios and
 * options are set through context options but will be sent only at observation request time.
 */
public class ResolutionRequest {

    private URL serviceUrl;
    private String urn;
    private KlabAsset.KnowledgeClass resolvableType;
    private List<String> scenarios = new ArrayList<>();
    private Map<String, Object> options = new HashMap<>();

    /**
     * If null, resolution will happen
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

    public List<String> getScenarios() {
        return scenarios;
    }

    public void setScenarios(List<String> scenarios) {
        this.scenarios = scenarios;
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
