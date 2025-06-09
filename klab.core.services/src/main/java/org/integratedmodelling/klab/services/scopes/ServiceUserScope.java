package org.integratedmodelling.klab.services.scopes;

import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.integratedmodelling.common.authentication.scope.AbstractReactiveScopeImpl;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.ServiceSideScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.services.JobManager;
import org.integratedmodelling.klab.services.application.security.Role;

/**
 * Service-side user scope and parent class for other scopes, created and maintained on request upon
 * authentication. The services exposed are the ones authorized passed explicitly from the client
 * side after authentication, except for the service hosting the scope, which is the one and only
 * provided for its class. In this implementation (currently) the only scope that has services is
 * the context scope, and the other scopes have empty service maps. The {@link ScopeManager}
 * contains the logic.
 *
 * <p>Relies on external instrumentation after creation.
 *
 * <p>Maintained by the {@link ScopeManager}
 *
 * @author Ferd
 */
public class ServiceUserScope extends AbstractReactiveScopeImpl
    implements UserScope, ServiceSideScope {

  // the data hash is the SAME OBJECT throughout the child
  protected Parameters<String> data;
  private UserIdentity user;
  protected ServiceUserScope parentScope;
  private Status status = Status.STARTED;
  private Collection<Role> roles;
  private String id;
  private boolean local;
  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
  protected Map<KlabService.Type, List<? extends KlabService>> serviceMap = new HashMap<>();
  protected Map<KlabService.Type, KlabService> defaultServiceMap = new HashMap<>();
  private boolean messagingChecked = false;

  // if the next two are filled in, the payloads of any message generated will be collected in the
  // list
  // if they are of the passed class. Used on scope copies for monitoring and messaging.These are
  // never
  // copied downstream
  private List<Object> payloadCollector = null;
  private Class<?> collectedPayloadClass = null;

  // these are users of this service, which we keep around individually so that we can enable
  // messaging for
  // local users
  protected KlabService service;
  protected Federation federation;

  public ServiceUserScope(UserIdentity user, KlabService service) {
    super(user, true, false);
    this.user = user;
    this.data = Parameters.create();
    this.service = service;
    this.federation = user.getData().get(UserIdentity.FEDERATION_DATA_PROPERTY, Federation.class);
  }

  @Override
  public ContextScope connect(URL digitalTwinURL) {
    // TODO connect to a scope on a runtime. Unless the runtime is local, we should produce a
    // client.
    return null;
  }

  protected ServiceUserScope(ServiceUserScope parent) {
    super(parent.user, parent.isSender(), parent.isReceiver());
    copyMessagingSetup(parent);
    this.service = parent.service;
    this.user = parent.user;
    this.parentScope = parent;
    this.data = parent.data;
    this.local = parent.local;
    this.serviceMap.putAll(parent.serviceMap);
    this.defaultServiceMap.putAll(parent.defaultServiceMap);
    this.id = parent.id;
  }

  public KlabService getService() {
    return this.service;
  }

  /**
   * Create an exact copy to modify. Exclusively available to other scopes and the scope manager.
   *
   * @return
   */
  ServiceUserScope copy() {

    // ensure any virtual defined for this scope is called.
    final ServiceUserScope originalScope = this;

    var ret =
        new ServiceUserScope(this) {
          @Override
          public <T extends KlabService> T getService(Class<T> serviceClass) {
            return originalScope.getService(serviceClass);
          }

          @Override
          public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
            return originalScope.getServices(serviceClass);
          }

          @Override
          public <T extends KlabService> T getService(String serviceId, Class<T> serviceClass) {
            return originalScope.getService(serviceId, serviceClass);
          }
        };
    ret.copyInfo(this);
    return ret;
  }

  @Override
  public String getDispatchId() {
    return federation == null ? user.getUsername() : federation.getId();
  }

  protected void copyInfo(ServiceUserScope other) {
    this.id = other.id;
    this.messagingChecked = other.messagingChecked;
    this.serviceMap.putAll(other.serviceMap);
    this.roles = other.roles;
    this.status = other.status;
  }

  @Override
  public ContextScope connect(DigitalTwin.Configuration configuration) {
    return null;
  }

  @Override
  public SessionScope getUserSession(RuntimeService hostService) {

    final ServiceSessionScope ret = new ServiceSessionScope(this);
    ret.setStatus(Status.WAITING);
    ret.setName(
            federation == null || Federation.LOCAL_FEDERATION_ID.equals(federation.getId())
            ? user.getUsername()
            : federation.getId());
    ret.jobManager = new JobManager();
    // Scope is incomplete and will be instrumented with ID, messaging connection, queues and agent
    // by
    // the caller explicitly calling the methods.
    return ret;
  }

  /**
   * Must be called with clients for all services accessible from the client's environment, plus the
   * singleton of the hosting service. If this is called on a scope with non-empty services, the
   * scope will use these services instead of the default.
   *
   * @param resources
   * @param resolvers
   * @param reasoners
   * @param runtimes
   */
  public void setServices(
      List<ResourcesService> resources,
      List<Resolver> resolvers,
      List<Reasoner> reasoners,
      List<RuntimeService> runtimes) {

    serviceMap.clear();
    defaultServiceMap.clear();

    serviceMap.put(KlabService.Type.REASONER, reasoners);
    serviceMap.put(KlabService.Type.RESOLVER, resolvers);
    serviceMap.put(KlabService.Type.RESOURCES, resources);
    serviceMap.put(KlabService.Type.RUNTIME, runtimes);

    if (!reasoners.isEmpty()) {
      defaultServiceMap.put(KlabService.Type.REASONER, reasoners.getFirst());
    }
    if (!resolvers.isEmpty()) {
      defaultServiceMap.put(KlabService.Type.RESOLVER, resolvers.getFirst());
    }
    if (!resources.isEmpty()) {
      defaultServiceMap.put(KlabService.Type.RESOURCES, resources.getFirst());
    }
    if (!runtimes.isEmpty()) {
      defaultServiceMap.put(KlabService.Type.RUNTIME, runtimes.getFirst());
    }
  }

//  @Override
//  public SessionScope getUserSession() {
//
//  }

  @Override
  public SessionScope run(String behaviorName, RuntimeService host) {

    SessionScope ret = null; // getUserSession();
    // TODO add the behavior info
    return ret;
  }

  @Override
  public UserIdentity getUser() {
    return this.user;
  }

  @Override
  public Parameters<String> getData() {
    return this.data;
  }

  @Override
  public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
    var ret =
        new Utils.Casts<KlabService, T>()
            .cast(
                (Collection<KlabService>) serviceMap.get(KlabService.Type.classify(serviceClass)));
    if (ret == null) {
      return List.of();
    }
    return ret;
  }

  @Override
  public boolean hasErrors() {
    // TODO Auto-generated method stub
    return false;
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
  public <T extends KlabService> T getService(Class<T> serviceClass) {
    return (T) defaultServiceMap.get(KlabService.Type.classify(serviceClass));
  }

  @Override
  public <T extends KlabService> T getService(String serviceId, Class<T> serviceClass) {
    for (var service : getServices(serviceClass)) {
      if (serviceId.equals(service.serviceId())) {
        return service;
      }
    }
    throw new KlabResourceAccessException(
        "cannot find service with ID=" + serviceId + " in the scope");
  }

  //	@Override
  public void stop() {
    if (agent != null) {
      //      agent.tell(ReActorStop.STOP);
      //      this.agent = null;
    }
    this.data.clear();
    setStatus(Status.EMPTY);
  }

  public Collection<Role> getRoles() {
    return roles;
  }

  public void setRoles(Collection<Role> roles) {
    this.roles = roles;
  }

  @Override
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public boolean isLocal() {
    return local;
  }

  public void setLocal(boolean local) {
    this.local = local;
  }

  @Override
  public ServiceUserScope getParentScope() {
    return parentScope;
  }

  public void setParentScope(ServiceUserScope parentScope) {
    this.parentScope = parentScope;
  }

  public String toString() {
    return user.toString();
  }

  //  /**
  //   * This implementation ensures that if we don't have channels set up but the service has an
  //   * embedded broker (which means it's local and talking to local users) these get set up.
  // Channel
  //   * setup is only called once after the service has been initialized.
  //   *
  //   * @param queue
  //   * @return
  //   */
  //  @Override
  //  protected Channel getChannel(Message.Queue queue) {
  //
  //    if (!messagingChecked
  //        && service instanceof BaseService baseService
  //        && baseService.isInitialized()
  //        && baseService.getEmbeddedBroker() != null) {
  //      setupMessaging(
  //          baseService.getEmbeddedBroker().getURI().toString(),
  //          service.capabilities(this).getType().name().toLowerCase() + "." +
  // getUser().getUsername(),
  //          service.capabilities(this).getAvailableMessagingQueues());
  //      messagingChecked = true;
  //    }
  //
  //    return super.getChannel(queue);
  //  }

  @Override
  public void event(Message message) {
    super.event(message);
    if (payloadCollector != null
        && (collectedPayloadClass != null
            && collectedPayloadClass.isAssignableFrom(
                message.getPayload(Object.class).getClass()))) {
      payloadCollector.add(message.getPayload(Object.class));
    }
  }

  @Override
  public void error(Object... o) {
    super.error(o);
    if (payloadCollector != null && collectedPayloadClass.isAssignableFrom(Notification.class)) {
      payloadCollector.add(Notification.error(o).withIdentity(getId()));
    }
  }

  @Override
  public void info(Object... info) {
    super.info(info);
    if (payloadCollector != null && collectedPayloadClass.isAssignableFrom(Notification.class)) {
      payloadCollector.add(Notification.info(info).withIdentity(getId()));
    }
  }

  @Override
  public void ui(Message message) {
    super.ui(message);
    if (payloadCollector != null
        && (collectedPayloadClass != null
            && collectedPayloadClass.isAssignableFrom(
                message.getPayload(Object.class).getClass()))) {
      payloadCollector.add(message.getPayload(Object.class));
    }
  }

  @Override
  public void warn(Object... o) {
    super.warn(o);
    if (payloadCollector != null && collectedPayloadClass.isAssignableFrom(Notification.class)) {
      payloadCollector.add(Notification.warning(o).withIdentity(getId()));
    }
  }

  @Override
  public void debug(Object... o) {
    super.debug(o);
    if (payloadCollector != null && collectedPayloadClass.isAssignableFrom(Notification.class)) {
      payloadCollector.add(Notification.debug(o).withIdentity(getId()));
    }
  }

  public <T> void collectMessagePayload(Class<T> payloadClass, List<T> payloadCollection) {
    this.collectedPayloadClass = payloadClass;
    this.payloadCollector = (List<Object>) payloadCollection;
  }

  @Override
  public List<SessionScope> getActiveSessions() {
    return List.of();
  }
}
