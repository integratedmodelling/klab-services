package org.integratedmodelling.klab.api.services.resolver.objects;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;

import java.net.URL;
import java.util.*;

/**
 * The resolution constraints come from the scope beyond the content of the scope token.
 */
public class ResolutionRequest {

    private Observation observation;
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
}

