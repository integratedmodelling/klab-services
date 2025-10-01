package org.integratedmodelling.klab.services;

import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.identities.UserIdentity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton that collects the "abstract" service clients still uncommitted to an identity. When a
 * scope needs a service, it can retrieve a personalized client by deriving it from the known client
 * with the same ID.
 */
public enum ServiceCatalog {
  INSTANCE;

  private final Map<String, ServiceClient> serviceClients = new ConcurrentHashMap<>();

  public <T extends ServiceClient> T getServiceClient(String serviceId, Class<T> cls) {
    return cls.cast(serviceClients.get(serviceId));
  }

  public void addServiceClient(String serviceId, ServiceClient client, UserIdentity user) {
    serviceClients.put(serviceId, client);
  }
}
