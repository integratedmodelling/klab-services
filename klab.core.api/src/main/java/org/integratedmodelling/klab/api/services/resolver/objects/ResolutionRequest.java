package org.integratedmodelling.klab.api.services.resolver.objects;

import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;

import java.util.ArrayList;
import java.util.List;

/**
 * Request for the runtime to submit an observation. Resolution constraints derive the server-side
 * scope from the context identified by the scope token. An observation with ID
 * {@link Observation#QUERY_ID} requests read-only lookup and mediation of existing knowledge; the
 * server does not create a provenance agent for it. Other unresolved observations enter the normal
 * resolution lifecycle, with observer selection expressed through resolution constraints.
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

