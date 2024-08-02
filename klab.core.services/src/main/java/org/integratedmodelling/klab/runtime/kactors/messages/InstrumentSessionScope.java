package org.integratedmodelling.klab.runtime.kactors.messages;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.services.runtime.kactors.AgentMessage;

import java.net.URI;
import java.net.URL;

/**
 * Sent after context creation to notify the digital twin and the messaging strategy to the agent in charge of
 * it.
 */
public class InstrumentSessionScope extends AgentMessage {

    private URI brokerUrl;

    public InstrumentSessionScope(URI brokerUrl) {
        super();
        this.brokerUrl = brokerUrl;
    }

    public URI getBrokerUrl() {
        return brokerUrl;
    }

}
