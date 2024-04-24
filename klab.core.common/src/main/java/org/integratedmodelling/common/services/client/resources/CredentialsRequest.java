package org.integratedmodelling.common.services.client.resources;

import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;

public class CredentialsRequest {

    private String host;
    private ExternalAuthenticationCredentials credentials;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public ExternalAuthenticationCredentials getCredentials() {
        return credentials;
    }

    public void setCredentials(ExternalAuthenticationCredentials credentials) {
        this.credentials = credentials;
    }
}
