package org.integratedmodelling.common.services.client.scope;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.RuntimeService;

/**
 * A singleton used to keep track of scopes that were created within an instance. Differently from
 * the scope manager at service side, this only manages Session and Context scopes, from different
 * runtimes. User scopes are always obtained by authentication on the client side.
 */
public enum ClientScopeManager {
  INSTANCE;

  private Map<String, ClientSessionScope> scopes = new ConcurrentHashMap<>();

  /**
   * Get an existing scope, interrogating the runtime if we don't have it cached.
   *
   * @param scopeId
   * @param scopeClass
   * @return
   * @param <T>
   */
  public <T extends SessionScope> T getScope(String scopeId, Class<T> scopeClass) {
    if (scopes.containsKey(scopeId)
        && scopeClass.isAssignableFrom(scopes.get(scopeId).getClass())) {
      return (T) scopes.get(scopeId);
    }
    return null;
  }

  /**
   * Retrieves a context scope based on the provided runtime and configuration. Optionally, a new
   * scope may be created if it does not already exist. The requested configuration must exist on
   * the service and a connect call will be made to request connection.
   *
   * @param configuration the {@link DigitalTwin.Configuration} containing the configuration details
   *     for this scope. Persistence and other creation metadata are only relevant for scopes that
   *     are created by the call.
   * @param createIfMissing a boolean indicating whether to create a new scope if it does not
   *     already exist; set to true to create a new scope, false otherwise
   * @param requestingScope the requesting user scope
   * @return the {@link ContextScope} instance that matches the specified parameters, or null if not
   *     found and creation is not allowed
   */
  public ContextScope getContextScope(
      DigitalTwin.Configuration configuration, boolean createIfMissing, UserScope requestingScope) {

    if (configuration.getId() == null) {
      throw new KlabIllegalStateException("Cannot connect to remote scope: missing scope ID");
    }

    if (scopes.containsKey(configuration.getId())
        && scopes.get(configuration.getId()) instanceof ContextScope) {
      return (ContextScope) scopes.get(configuration.getId());
    }
    if (createIfMissing) {

      var service = findService(configuration, requestingScope);

      /* issue a CONNECT call to the service to ensure we have rights and the scope exists. */
      if (!service.status().isOperational()) {
        requestingScope.error(
            "Cannot connect to remote scope for digital twin "
                + configuration.getName()
                + ": service is not operational");
        return null;
      }

      return service.connectContext(configuration, requestingScope);
    }

    return null;
  }

  private RuntimeService findService(
      DigitalTwin.Configuration configuration, UserScope requestingScope) {
    for (var runtime : requestingScope.getServices(RuntimeService.class)) {
      if (runtime.getUrl().equals(configuration.getServiceUrl())) {
        return runtime;
      }
    }

    throw new KlabIllegalStateException("IMPLEMENT SERVICE INSTANTIATION");
    //    var newRuntime =
    //        ServiceClientCatalog.INSTANCE.getService(
    //            configuration.getUrl(), requestingScope.getIdentity(), SettingsImpl.forEngine());
    //
    //    // FIXME this will cause an exception as the client list is read-only. The client-side
    // services
    //    //  are ultimately stored in the engine - must deal with that.
    //    requestingScope.getServices(RuntimeService.class).add(newRuntime);
    //
    //    return newRuntime;
  }

  public void register(ClientSessionScope ret) {
    scopes.put(ret.getId(), ret);
    if (ret instanceof ClientContextScope contextScope) {
      contextScope.createDigitalTwin(ret.getId());
    }
  }

  public void unregister(ClientSessionScope clientSessionScope) {
    scopes.remove(clientSessionScope.getId());
  }

  public void close() {
    scopes.values().forEach(ClientSessionScope::close);
  }
}
