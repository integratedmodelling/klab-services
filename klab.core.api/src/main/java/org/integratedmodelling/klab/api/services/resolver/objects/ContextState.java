package org.integratedmodelling.klab.api.services.resolver.objects;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Concept;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bean to hold the variable state of a context scope so that we can recreate only the root context scope at
 * service side, then reconstruct scenarios, observer, context etc. by adding a tree of these to the request.
 */
public class ContextState {

    private String observerId;
    private List<String> scenarios = new ArrayList<>();
    private String contextObservationId;
    private String resolutionNamespaceUrn;
    private Map<String, String> traitResolution = new LinkedHashMap<>();
    private ContextState child;

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

    public ContextState getChild() {
        return child;
    }

    public void setChild(ContextState child) {
        this.child = child;
    }
}
