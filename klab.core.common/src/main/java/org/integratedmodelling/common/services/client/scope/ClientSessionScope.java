package org.integratedmodelling.common.services.client.scope;

import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;

import java.net.URL;
import java.util.Collection;
import java.util.List;

/** Client-side session scope */
public abstract class ClientSessionScope extends ClientUserScope implements SessionScope {

  protected final RuntimeService runtimeService;
  protected String name;

  public ClientSessionScope(
      ClientUserScope parent, String sessionName, RuntimeService runtimeService) {
    // FIXME use a copy constructor that inherits the environment from the parent
    super(parent.getUser(), parent.engine);
    this.runtimeService = runtimeService;
    this.name = sessionName;
    this.parentScope = parent;
  }

  @Override
  public ContextScope connect(URL digitalTwinURL) {
    throw new KlabIllegalStateException("connect() can not be called on a session scope");
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public String getDispatchId() {
    return getId();
  }

  public String toString() {
    return "[ClientSessionScope] "
        + name
        + ": "
        + getId()
        + " ("
        + (isConnected() ? "connected" : "not connected")
        + ")";
  }

  @Override
  public ContextScope createContext(String contextName) {

    var runtime = getService(RuntimeService.class);
    if (runtime == null) {
      throw new KlabResourceAccessException(
          "Runtime service is not accessible: cannot create context");
    }

    /**
     * Registration with the runtime succeeded. Return a peer scope locked to the runtime service
     * that hosts it.
     */
    var ret =
        new ClientContextScope(this, contextName, runtime) {

          @Override
          public <T extends KlabService> T getService(Class<T> serviceClass) {
            if (serviceClass.isAssignableFrom(RuntimeService.class)) {
              return (T) runtime;
            }
            return ClientSessionScope.this.getService(serviceClass);
          }

          @Override
          public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
            if (serviceClass.isAssignableFrom(RuntimeService.class)) {
              return List.of((T) runtime);
            }
            return ClientSessionScope.this.getServices(serviceClass);
          }
        };

    var id =
        engine.registerContext(
            ret,
            getParentScope(Type.USER, UserScope.class)
                .getUser()
                .getData()
                .get(UserIdentity.FEDERATION_DATA_PROPERTY, Federation.class));

    if (id != null) {
      ret.setId(id);
    }

    return ret;
  }

  @Override
  public void close() {
    closeMessaging();
    var runtime = getService(RuntimeService.class);
    if (runtime != null) {
      runtime.releaseSession(this);
    } else {
      throw new KlabInternalErrorException("Session scope: no runtime service available");
    }
  }

  @Override
  public List<ContextScope> getActiveContexts() {
    return List.of();
  }
}
