package org.integratedmodelling.common.services.client;

import org.integratedmodelling.klab.api.services.KlabService;

import java.net.URL;

/**
 * Common implementation of a service client, to be specialized for all service types and APIs.
 */
public abstract class ServiceClient implements KlabService {

    protected ServiceClient(URL url) {

    }

}
