package org.integratedmodelling.klab.services.scopes;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Predicate;
import org.integratedmodelling.common.authentication.scope.AbstractReactiveScopeImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.ServiceSideScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.JobManager;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.base.BaseService;

/**
 * Service-side user scope and parent class for other scopes, created and maintained on request upon
 * authentication. The services exposed are the ones authorized passed explicitly from the client
 * side after authentication, except for the service hosting the scope, which is directly provided
 * for its class. Contains an explicit service hash negotiated in each service after authentication
 * using a specific API call.
 *
 * <p>Relies on external instrumentation after creation.
 *
 * <p>Maintained by the {@link ScopeManager} and instrumented by {@link
 * org.integratedmodelling.common.services.client.ServiceClientCatalog}.
 *
 * @author Ferd
 */
public class ServiceUserScope extends AbstractReactiveScopeImpl
    implements UserScope, ServiceSideScope {

  // the data hash is the SAME OBJECT throughout the child hierarchy
  protected Parameters<String> data;
  private UserIdentity user;
  protected ServiceUserScope parentScope;
  private Status status = Status.STARTED;
  private Collection<Role> roles;
  private String id;
  private boolean local;
  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
  private boolean messagingChecked = false;
  private JobManager jobManager;
  private boolean empty;
  private List<Notification> notifications = new ArrayList<>();

  protected Map<KlabService.Type, List<KlabService>> serviceMap = new ConcurrentHashMap<>();

  @Override
  public final <T extends KlabService> Optional<T> findService(
      Class<T> serviceClass, Predicate<T> selector) {

    var services = getServices(serviceClass);
    var ret =
        services.stream()
            .filter(serviceClient -> selector == null || selector.test((T) serviceClient))
            .toList();
    if (!ret.isEmpty()) {
      return Optional.of((T) ret.getFirst());
    }

    return Optional.empty();
  }

  @Override
  public final <T extends KlabService> T getService(Class<T> serviceClass) {
    return getServices(serviceClass).stream()
        .findFirst()
        .orElseThrow(
            () ->
                new KlabServiceAccessException(
                    "No suitable service for request of " + serviceClass.getSimpleName()));
  }

  @Override
  public final <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
    if (serviceClass.equals(KlabService.class)) {
      var ret = new ArrayList<T>();
      for (var services : serviceMap.values()) {
        ret.addAll(services.stream().map(s -> (T) s).toList());
      }
      sortLocalFirst(ret);
      return ret;
    }
    var ret =
        new ArrayList<>(
            serviceList(KlabService.Type.classify(serviceClass)).stream()
                .filter(this::isUsableService)
                .map(s -> (T) s)
                .toList());
    sortLocalFirst(ret);
    return ret;
  }

  // if the next two are filled in, the payloads of any message generated will be collected in the
  // list
  // if they are of the passed class. Used on scope copies for monitoring and messaging.These are
  // never
  // copied downstream
  // FIXME messy and probably obsolete, remove after checking the notification logic where this is
  // used
  private List<Object> payloadCollector = null;
  private Class<?> collectedPayloadClass = null;

  // these are users of this service, which we keep around individually so that we can enable
  // messaging for
  // local users
  protected KlabService service;

  public ServiceUserScope(UserIdentity user, KlabService service) {
    super(user, true, false);
    this.user = user;
    this.data = Parameters.create();
    this.service = service;
    this.jobManager = new JobManager();
    this.setHostServiceId(service.serviceId());
    this.roles = EnumSet.noneOf(Role.class);
  }

  public JobManager getJobManager() {
    return jobManager;
  }

  @Override
  public ContextScope connect(URL digitalTwinURL) {
    // TODO connect to a scope on a runtime. Unless the runtime is local, we should produce a
    // client.
    return null;
  }

  protected ServiceUserScope(ServiceUserScope parent) {
    super(parent.user, parent.isSender(), parent.isReceiver());
    //    copyMessagingSetup(parent);
    this.service = parent.service;
    this.setHostServiceId(parent.getHostServiceId());
    this.user = parent.user;
    this.parentScope = parent;
    this.data = parent.data;
    this.roles = parent.roles;
    this.local = parent.local;
    this.id = parent.id;
    this.jobManager = parent.jobManager;
    copyServicesFrom(parent);
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

    var ret = new ServiceUserScope(this);
    ret.copyInfo(this);
    return ret;
  }

  @Override
  public String getDispatchId() {
    var federation = Klab.INSTANCE.getFederationData(user);
    return federation == null ? user.getUsername() : federation.getId();
  }

  protected void copyInfo(ServiceUserScope other) {
    this.id = other.id;
    this.messagingChecked = other.messagingChecked;
    copyServicesFrom(other);
    this.roles = other.roles;
    this.status = other.status;
  }

  @Override
  public ContextScope connect(DigitalTwin.Configuration configuration) {
    return null;
  }

  @Override
  public SessionScope getUserSession(RuntimeService hostService) {

    var federation = Klab.INSTANCE.getFederationData(user);
    var scopeId =
        federation == null || Federation.LOCAL_FEDERATION_ID.equals(federation.getId())
            ? user.getUsername().replace(".", "_")
            : federation.getId().replace(".", "_");

    var scopeManager =
        service instanceof BaseService baseService ? baseService.getScopeManager() : null;
    var scope = scopeManager == null ? null : scopeManager.getScope(scopeId, SessionScope.class);

    if (scope != null) {
      return scope;
    }

    final var ret = new ServiceSessionScope(this);
    ret.setStatus(Status.WAITING);
    ret.setId(scopeId);
    ret.setHostServiceId(hostService.serviceId());
    ret.setName(
        federation == null || Federation.LOCAL_FEDERATION_ID.equals(federation.getId())
            ? user.getUsername()
            : federation.getId());

    return ret;
  }

  @Override
  public SessionScope run(KActorsBehavior behavior) {
    // TODO
    throw new KlabIllegalStateException(
        "Sessions at service side must be created through the service API");
  }

  @Override
  public UserIdentity getUser() {
    return this.user;
  }

  @Override
  public Worldview getWorldview() {
    throw new KlabIllegalStateException(
        "No worldview should be retrieved by scopes at service side");
  }

  protected void setUser(UserIdentity user) {
    this.user = user;
  }

  @Override
  public Parameters<String> getData() {
    return this.data;
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

  public boolean validateServices() {
    // TODO check that all essential services are available and online, waiting a bit for connection
    //  if necessary
    Logging.INSTANCE.info("Services for " + user.getUsername() + " validated");
    return true;
  }

  public void addService(KlabService klabService) {
    var list = serviceList(KlabService.Type.classify(klabService));
    for (int i = 0; i < list.size(); i++) {
      var existing = list.get(i);
      if (sameService(existing, klabService)) {
        list.set(i, klabService);
        return;
      }
    }
    list.add(klabService);
  }

  private List<KlabService> serviceList(KlabService.Type type) {
    return serviceMap.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>());
  }

  private void copyServicesFrom(ServiceUserScope other) {
    serviceMap.clear();
    other.serviceMap.forEach(
        (type, services) -> serviceMap.put(type, new CopyOnWriteArrayList<>(services)));
  }

  private boolean sameService(KlabService existing, KlabService candidate) {
    if (existing.serviceId() != null && candidate.serviceId() != null) {
      return Objects.equals(existing.serviceId(), candidate.serviceId());
    }
    return existing.getUrl() != null && Objects.equals(existing.getUrl(), candidate.getUrl());
  }

  private boolean isUsableService(KlabService service) {
    var status = service.status();
    if (status == null) {
      return false;
    }
    return status.isOperational() || (isLocalService(service) && status.isAvailable());
  }

  private <T extends KlabService> void sortLocalFirst(List<T> services) {
    services.sort(Comparator.comparing(s -> isLocalService(s) ? 0 : 1));
  }

  private boolean isLocalService(KlabService service) {
    return service.getUrl() != null && Utils.URLs.isLocalHost(service.getUrl());
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
