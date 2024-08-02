package org.integratedmodelling.klab.runtime.kactors.messages;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.services.runtime.kactors.AgentMessage;

import java.net.URI;
import java.net.URL;

/**
 * Sent after context creation to notify the digital twin and the messaging strategy to the agent in charge of
 * it.
 */
public class InstrumentContextScope extends AgentMessage {

    private final DigitalTwin digitalTwin;
    private URI brokerUrl;

    public InstrumentContextScope(DigitalTwin digitalTwin, URI brokerUrl) {
        super();
        this.digitalTwin = digitalTwin;
        this.brokerUrl = brokerUrl;
    }

    public DigitalTwin getDigitalTwin() {
        return digitalTwin;
    }

    public URI getBrokerUrl() {
        return brokerUrl;
    }

}
