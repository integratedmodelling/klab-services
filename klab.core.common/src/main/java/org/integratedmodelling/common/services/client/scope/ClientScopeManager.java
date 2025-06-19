package org.integratedmodelling.common.services.client.scope;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.utils.Utils;

import java.util.*;

/**
 * A singleton used to keep track of scopes that were created within an instance. Differently from
 * the scope manager at service side, this only manages Session and Context scopes, from different
 * runtimes. User scopes are always obtained by authentication on the client side.
 */
public enum ClientScopeManager {
  INSTANCE;

  private Map<String, ClientSessionScope> scopes = Collections.synchronizedMap(new HashMap<>());

  /**
   * Get an existing scope, interrogating the runtime if we don't have it cached.
   *
   * @param runtime
   * @param scopeId
   * @param requestingScope
   * @param scopeClass
   * @return
   * @param <T>
   */
  public <T extends SessionScope> T getScope(
      RuntimeService runtime, String scopeId, UserScope requestingScope, Class<T> scopeClass) {
    if (scopes.containsKey(scopeId)
        && scopeClass.isAssignableFrom(scopes.get(scopeId).getClass())) {
      return (T) scopes.get(scopeId);
    }
    return null;
  }

  /**
   * Retrieves a context scope based on the provided runtime and configuration. Optionally, a new
   * scope may be created if it does not already exist.
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

    if (scopes.containsKey(configuration.getId())
        && scopes.get(configuration.getId()) instanceof ContextScope) {
      return (ContextScope) scopes.get(configuration.getId());
    }
    if (createIfMissing) {
      var service = findService(configuration, requestingScope);
      var sessionId = Utils.Paths.getLeading(configuration.getId(), '.');
      var sessionScope = getScope(service, sessionId, requestingScope, ClientSessionScope.class);
      if (sessionScope == null) {
        var info =
            service.getSessionInfo(requestingScope).stream()
                .filter(si -> si.getId().equals(sessionId))
                .findFirst();

        if (info.isEmpty()) {
          requestingScope.error(
              "Session info not found for session ID="
                  + sessionId
                  + ": scope may have been deleted");
          return null;
        }

        sessionScope =
            new ClientSessionScope(
                (ClientUserScope) requestingScope, info.get().getName(), service) {
              @Override
              public <T extends KlabService> T getService(Class<T> serviceClass) {
                return RuntimeService.class.equals(serviceClass)
                    ? (T) service
                    : requestingScope.getService(serviceClass);
              }

              @Override
              public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
                return RuntimeService.class.equals(serviceClass)
                    ? List.of((T) service)
                    : requestingScope.getServices(serviceClass);
              }
            };
        sessionScope.setId(sessionId);
        register(sessionScope);
      }
      var ret =
          new ClientContextScope(sessionScope, service, configuration) {
            @Override
            public <T extends KlabService> T getService(Class<T> serviceClass) {
              return RuntimeService.class.equals(serviceClass)
                  ? (T) service
                  : requestingScope.getService(serviceClass);
            }

            @Override
            public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
              return RuntimeService.class.equals(serviceClass)
                  ? List.of((T) service)
                  : requestingScope.getServices(serviceClass);
            }
          };
      ret.setId(configuration.getId());
      register(ret);
      return ret;
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
    // TODO ask the scope to register a new runtime
    return null;
  }

  public void register(ClientSessionScope ret) {
    scopes.put(ret.getId(), ret);
  }

  public void unregister(ClientSessionScope clientSessionScope) {
    scopes.remove(clientSessionScope.getId());
  }
}
