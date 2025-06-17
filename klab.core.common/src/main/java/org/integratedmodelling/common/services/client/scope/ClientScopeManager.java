package org.integratedmodelling.common.services.client.scope;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.RuntimeService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
   * @param runtime the {@link RuntimeService} instance used to manage contexts and interact with
   *     the runtime system
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
      // TODO check service
      return (ContextScope) scopes.get(configuration.getId());
    }
    return null;
  }

  public void register(ClientSessionScope ret) {
  }

  public void unregister(ClientSessionScope clientSessionScope) {}
}
