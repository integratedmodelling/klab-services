package org.integratedmodelling.klab.api.services.runtime.impl;

import java.net.http.HttpClient;

/**
 * Generic service client providing get(), post() etc adapted to streamline k.LAB service communication.
 * Implementations must supply the serialization/deserialization body handlers, to add JSON and Avro support.
 * Client implementation can derive from this.
 */
public abstract class ServiceClient {

    HttpClient client;


}
