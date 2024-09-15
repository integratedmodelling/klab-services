package org.integratedmodelling.klab.api.services.resolver.objects;

import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;

import java.util.ArrayList;
import java.util.List;

/**
 * The resolution constraints come from the scope beyond the content of the scope token.
 */
public class ResolutionRequest {

    private Observation observation;
    private Observable observable;
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
}

