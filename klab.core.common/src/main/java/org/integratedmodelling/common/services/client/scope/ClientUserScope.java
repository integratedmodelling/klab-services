package org.integratedmodelling.common.services.client.scope;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.integratedmodelling.common.authentication.scope.AbstractClientScope;
import org.integratedmodelling.common.services.client.engine.EngineImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;

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

  // the data hash is the SAME OBJECT throughout the child
  protected Parameters<String> data;
  protected Scope parentScope;
  protected Type type;
  private Federation federation;
  private final UserIdentity user;
  private Status status = Status.STARTED;
  private String id;
  private final Map<Long, Pair<Message, BiConsumer<Message, Message>>> responseHandlers =
      new ConcurrentHashMap<>();
  private String hostServiceId;
  private boolean empty;
  private final List<Notification> notifications = new ArrayList<>();

  public ClientUserScope(UserIdentity user, EngineImpl engine) {
    super(user, false, true, engine);
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

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String getDispatchId() {
    return federation == null ? user.getUsername() : federation.getId();
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
            ? user.getUsername().replace(".", "_")
            : federation.getId().replace(".", "_");

    var existing = ClientScopeManager.INSTANCE.getScope(sessionId, SessionScope.class);
    if (existing != null) {
      return existing;
    }

    var ret = new ClientSessionScope(this, sessionId, hostService).withId(sessionId);

    var id = hostService.declareSessionScope(ret, this, null);

    if (id != null) {
      // should be the same
      ret.setId(id);
      ClientScopeManager.INSTANCE.register(ret);
    }

    return ret;
  }

  @Override
  public SessionScope run(KActorsBehavior behavior) {
    // TODO as above, pass the behavior and the services and let the remote assign the ID
    return null;
  }

  @Override
  public UserIdentity getUser() {
    return this.user instanceof UserIdentity user ? user : null;
  }

  @Override
  public Worldview getWorldview() {
    return getEngine().getWorldview();
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
  public Status getStatus() {
    return this.status;
  }

  @Override
  public void setStatus(Status status) {
    this.status = status;
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

  @Override
  public boolean isEmpty() {
    return empty;
  }

  public void setEmpty(boolean empty) {
    this.empty = empty;
  }

  @Override
  public List<Notification> getNotifications() {
    return notifications;
  }
}
