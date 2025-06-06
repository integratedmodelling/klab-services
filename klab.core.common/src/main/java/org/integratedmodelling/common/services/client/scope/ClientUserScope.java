package org.integratedmodelling.common.services.client.scope;

import org.integratedmodelling.common.authentication.scope.AbstractReactiveScopeImpl;
import org.integratedmodelling.common.services.client.engine.EngineImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;

/**
 * Implementations must fill in the getService() strategy. This is a scope that contains an agent
 * ref. Any communication with the agent will pass the scope, so if the agent is remote the scope
 * must be reconstructed from authorization tokens into something that maintains communication with
 * the original one.
 *
 * <p>Each scope contains a hash of generic data. Creating "child" scopes will only build a new hash
 * when the scope is of a different class, otherwise the same data is passed to all children.
 *
 * <p>The scope classes inherit from each other, so care is needed if using <code>instanceof</code>
 * to discriminate.
 *
 * @author Ferd
 */
public abstract class ClientUserScope extends AbstractReactiveScopeImpl implements UserScope {

  protected final EngineImpl engine;
  private Federation federation;
  // the data hash is the SAME OBJECT throughout the child
  protected Parameters<String> data;
  private UserIdentity user;
  protected Scope parentScope;
  private Status status = Status.STARTED;
  private String id;
  protected Type type;
  //    private List<BiConsumer<Scope, Message>> listeners = new ArrayList<>();
  private Map<Long, Pair<Message, BiConsumer<Message, Message>>> responseHandlers =
      Collections.synchronizedMap(new HashMap<>());
  private String hostServiceId;

  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

  public ClientUserScope(UserIdentity user, EngineImpl engine) {
    super(user, true, true);
    this.user = user;
    this.data = Parameters.create();
    this.id = user.getId();
    this.engine = engine;
    if (user.getData().containsKey(UserIdentity.FEDERATION_DATA_PROPERTY)) {
      this.federation = user.getData().get(UserIdentity.FEDERATION_DATA_PROPERTY, Federation.class);
    }
  }

  @Override
  public ContextScope connect(URL digitalTwinURL) {
    // TODO connect to a scope on a runtime. Unless the runtime is local, we should produce a
    //  client without having to pass through other services.
    return null;
  }

  @Override
  public <T extends KlabService> T getService(String serviceId, Class<T> serviceClass) {

    for (var service : getServices(serviceClass)) {

      if (service == null) {
        return null;
      }

      if (serviceId.equals(service.serviceId())) {
        return service;
      }
    }
    throw new KlabResourceAccessException(
        "cannot find service with ID=" + serviceId + " in the scope");
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getDispatchId() {
    return federation == null ? user.getUsername() : federation.getId();
  }

  public void setId(String id) {
    this.id = id;
  }

  public String toString() {
    return "[ClientUserScope] "
        + user.getUsername()
        + ((federation == null || federation.getId().equals(Federation.LOCAL_FEDERATION_ID))
            ? ""
            : ("@" + federation.getId()))
        + " ("
        + (isConnected() ? "connected" : "not connected")
        + ")";
  }

  @Override
  public ContextScope createDigitalTwin(RuntimeService hostService, DigitalTwin.Options options) {
    return null;
  }

  @Override
  public SessionScope getUserSession(RuntimeService hostService) {

    /**
     * Registration with the hostService succeeded. Return a peer scope locked to the hostService service
     * that hosts it.
     */
    var federation = user.getData().get(UserIdentity.FEDERATION_DATA_PROPERTY, Federation.class);
    var ret =
        new ClientSessionScope(
            this,
            federation == null || Federation.LOCAL_FEDERATION_ID.equals(federation.getId())
                ? user.getUsername()
                : federation.getId(),
            hostService) {

          @Override
          public <T extends KlabService> T getService(Class<T> serviceClass) {
            if (serviceClass.isAssignableFrom(RuntimeService.class)) {
              return (T) hostService;
            }
            return ClientUserScope.this.getService(serviceClass);
          }

          @Override
          public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
            if (serviceClass.isAssignableFrom(RuntimeService.class)) {
              return List.of((T) hostService);
            }
            return ClientUserScope.this.getServices(serviceClass);
          }
        };

    var id = engine.registerSession(ret, federation);

    if (id != null) {
      ret.setId(id);
    }

    return ret;
  }

  @Override
  public SessionScope run(String behaviorName, RuntimeService hostService) {
    return null;
  }

  @Override
  public UserIdentity getUser() {
    return this.user instanceof UserIdentity user ? user : null;
  }

  @Override
  public Parameters<String> getData() {
    return this.data;
  }

  @Override
  public boolean isInterrupted() {
    return status == Status.INTERRUPTED;
  }

  @Override
  public void interrupt() {
    this.status = Status.INTERRUPTED;
  }

  @Override
  public Identity getIdentity() {
    return getUser();
  }

  @Override
  public void setStatus(Status status) {
    this.status = status;
  }

  @Override
  public Status getStatus() {
    return this.status;
  }

  @Override
  public void setData(String key, Object value) {
    this.data.put(key, value);
  }

  @Override
  public Scope getParentScope() {
    return parentScope;
  }

  public void setParentScope(Scope parentScope) {
    this.parentScope = parentScope;
  }

  @Override
  public List<SessionScope> getActiveSessions() {
    return List.of();
  }

  @Override
  public String getHostServiceId() {
    return hostServiceId;
  }

  @Override
  public void setHostServiceId(String hostServiceId) {
    this.hostServiceId = hostServiceId;
  }
}
