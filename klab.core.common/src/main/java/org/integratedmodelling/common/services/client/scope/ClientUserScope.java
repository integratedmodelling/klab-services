package org.integratedmodelling.common.services.client.scope;

import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.integratedmodelling.common.authentication.scope.AbstractClientScope;
import org.integratedmodelling.common.authentication.scope.AbstractReactiveScopeImpl;
import org.integratedmodelling.common.services.client.engine.EngineImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Message;

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
public class ClientUserScope extends AbstractClientScope implements UserScope {

  private Federation federation;
  // the data hash is the SAME OBJECT throughout the child
  protected Parameters<String> data;
  private UserIdentity user;
  protected Scope parentScope;
  private Status status = Status.STARTED;
  private String id;
  protected Type type;
  private Map<Long, Pair<Message, BiConsumer<Message, Message>>> responseHandlers =
      Collections.synchronizedMap(new HashMap<>());
  private String hostServiceId;

  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

  public ClientUserScope(UserIdentity user, EngineImpl engine) {
    super(user, true, true, engine);
    this.user = user;
    this.data = Parameters.create();
    this.id = user.getId();
    if (user.getData().containsKey(UserIdentity.FEDERATION_DATA_PROPERTY)) {
      this.federation = user.getData().get(UserIdentity.FEDERATION_DATA_PROPERTY, Federation.class);
    }
  }

  @Override
  public ContextScope connect(URL digitalTwinURL) {
    return connect(DigitalTwin.Configuration.create(digitalTwinURL, this).validate(this));
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
  public ContextScope connect(DigitalTwin.Configuration configuration) {
    return ClientScopeManager.INSTANCE.getContextScope(configuration, true, this);
  }

  @Override
  public SessionScope getUserSession(RuntimeService hostService) {

    var sessionId =
        federation == null || Federation.LOCAL_FEDERATION_ID.equals(federation.getId())
            ? user.getUsername()
            : federation.getId().replace(".", "_");

    var existing = ClientScopeManager.INSTANCE.getScope(sessionId, SessionScope.class);
    if (existing != null) {
      return existing;
    }

    var ret = new ClientSessionScope(this, sessionId, hostService) /* {

          @Override
          public <T extends KlabService> T getService(
              Class<T> serviceClass, Predicate<T>... selectors) {
            if (serviceClass.isAssignableFrom(RuntimeService.class)) {
              return (T) hostService;
            }
            return ClientUserScope.this.getService(serviceClass, selectors);
          }

          @Override
          public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
            if (serviceClass.isAssignableFrom(RuntimeService.class)) {
              return List.of((T) hostService);
            }
            return ClientUserScope.this.getServices(serviceClass);
          }
        }*/.withId(sessionId);

    var id = hostService.registerNewSession(ret, this, null);

    if (id != null) {
      // should be the same
      ret.setId(id);
      ClientScopeManager.INSTANCE.register(ret);
    }

    return ret;
  }

  @Override
  public SessionScope run(String behaviorName, RuntimeService hostService) {
    // TODO as above, pass the behavior and the services and let the remote assign the ID
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
