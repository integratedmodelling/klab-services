package org.integratedmodelling.klab.services;

import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.objects.UserScopeNotification;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;

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

  /**
   * Ensure we have clients for all services in the request; if so, create personalized clients for
   * the user scope and set the clients in it. If any service has the same ID of the embedding
   * service, use that instead of creating a client. Return true if all clients were set up
   * correctly.
   *
   * @param userScope
   * @param request
   * @return
   */
  public boolean setupUserScope(
      ServiceUserScope userScope, UserScopeNotification request, KlabService ownerService) {
    return false;
  }
}
