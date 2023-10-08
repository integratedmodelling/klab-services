package org.integratedmodelling.klab.services.actors.messages.context;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.runtime.kactors.messages.AgentMessage;

public class GetParent extends AgentMessage {
    private Observation rootObservation;

    public Observation getRootObservation() {
        return rootObservation;
    }

    public void setRootObservation(Observation rootObservation) {
        this.rootObservation = rootObservation;
    }
}
