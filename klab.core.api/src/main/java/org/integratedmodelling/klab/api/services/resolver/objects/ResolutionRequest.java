package org.integratedmodelling.klab.api.services.resolver.objects;

import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;

import java.util.ArrayList;
import java.util.List;

/**
 * Request for the runtime to add an observation to the knowledge graph. The resolution constraints come from
 * the scope, creating a new content scope at server side from the root context scope pointed to by the scope
 * token. The observation is forced to be an observer if the role == OBSERVER, which should be used when the
 * initial observer is submitted into an empty context; otherwise the knowledge graph has the faculty of
 * providing a default observer if a regular observation is made in a scope that doesn't have one.
 */
public class ResolutionRequest {

    private Observation observation;
    private Observable observable;
    private String agentName; // for provenance when needed. Agents are identified by name
    private List<ResolutionConstraint> resolutionConstraints = new ArrayList<>();
    private long observationId;

    public long getObservationId() {
        return observationId;
    }

    public void setObservationId(long observationId) {
        this.observationId = observationId;
    }

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

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }
}

