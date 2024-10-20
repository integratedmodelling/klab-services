package org.integratedmodelling.klab.api.services.resolver.objects;

import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;

import java.util.ArrayList;
import java.util.List;

/**
 * Request for the runtime to add an observation to the knowledge graph and optionally resolve it. The
 * resolution constraints come from the scope, creating a new content scope at server side from the root
 * context scope pointed to by the scope token.
 */
public class ResolutionRequest {

    private Observation observation;
    private Observable observable;
    private boolean startResolution;
    private String agentName; // for provenance when needed. Agents are identified by name
    private List<ResolutionConstraint> resolutionConstraints = new ArrayList<>();

    public Observation getObservation() {
        return observation;
    }

    public void setObservation(Observation observation) {
        this.observation = observation;
    }

    public List<ResolutionConstraint> getResolutionConstraints() {
        return resolutionConstraints;
    }

    public void setResolutionConstraints(List<ResolutionConstraint> resolutionConstraints) {
        this.resolutionConstraints = resolutionConstraints;
    }

    public Observable getObservable() {
        return observable;
    }

    public void setObservable(Observable observable) {
        this.observable = observable;
    }

    public boolean isStartResolution() {
        return startResolution;
    }

    public void setStartResolution(boolean startResolution) {
        this.startResolution = startResolution;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }
}

