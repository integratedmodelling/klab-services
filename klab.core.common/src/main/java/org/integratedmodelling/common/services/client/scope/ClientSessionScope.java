package org.integratedmodelling.common.services.client.scope;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/** Client-side session scope */
public class ClientSessionScope extends ClientUserScope implements SessionScope {

  protected final RuntimeService runtimeService;
  protected String name;

  public ClientSessionScope(
      ClientUserScope parent, String sessionName, RuntimeService runtimeService) {
    // FIXME use a copy constructor that inherits the environment from the parent
    super(parent.getUser(), parent.getEngine());
    this.runtimeService = runtimeService;
    this.name = sessionName;
    this.parentScope = parent;
    setId(null);
  }

  @Override
  public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
    if (RuntimeService.class.isAssignableFrom(serviceClass)) {
      return List.of((T) runtimeService);
    }
    return super.getServices(serviceClass);
  }

  @Override
  public <T extends KlabService> T getService(Class<T> serviceClass, Predicate<T>... selectors) {
    if (RuntimeService.class.isAssignableFrom(serviceClass)) {
      return (T) runtimeService;
    }
    return super.getService(serviceClass, selectors);
  }

  /**
   * Use to pre-define the ID when necessary.
   *
   * @param id
   * @return
   */
  public ClientSessionScope withId(String id) {
    this.setId(id);
    return this;
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
  public ContextScope createContext(DigitalTwin.Configuration configuration) {

    var runtime = getService(RuntimeService.class);
    if (runtime == null) {
      throw new KlabResourceAccessException(
          "Runtime service is not accessible: cannot create context");
    }

    var userScope = getParentScope(Type.USER, UserScope.class);
    /*
     * Registration with the runtime succeeded. Return a peer scope locked to the runtime service
     * that hosts it.
     */
    var ret = new ClientContextScope(this, runtime, configuration.validate(this));
    var id = runtime.declareContextScope(ret, userScope);

    if (id != null) {
      ret.setId(id);
      ClientScopeManager.INSTANCE.register(ret);
    }

    return ret;
  }

  @Override
  public void close() {
    ClientScopeManager.INSTANCE.unregister(this);
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
