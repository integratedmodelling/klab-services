package org.integratedmodelling.klab.services;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.integratedmodelling.common.authentication.scope.AbstractReactiveScopeImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.engine.ServiceMonitor;
import org.integratedmodelling.common.services.client.engine.SettingsImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.rest.ServiceReference;
import org.integratedmodelling.klab.services.base.BaseService;

/**
 * The ServiceScope used in local configurations. Authenticated through a user certificate and
 * providing access to the necessary services only.
 *
 * @author Ferd
 */
public class UserServiceScope extends AbstractReactiveScopeImpl implements ServiceScope {

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
  private boolean empty;
  private final List<Notification> notifications = new ArrayList<>();
  private BaseService service;
  private String hostServiceId;
  private ServiceMonitor serviceMonitor;
  private final AtomicBoolean maintenanceMode = new AtomicBoolean(true);
  private final AtomicBoolean atomicOperationMode = new AtomicBoolean(false);
  private Locality locality = Locality.EMBEDDED;

  public UserServiceScope(
      UserIdentity user, KlabService.Type serviceType, List<ServiceReference> serviceList) {
    super(user, false, false);
    this.user = user;
    if (this.user.isAnonymous()) {
      Logging.INSTANCE.warn(
          "Anonymous UserServiceScope started for service "
              + serviceType
              + "; local service dependencies will be limited");
    } else {
      Logging.INSTANCE.info(
          "UserServiceScope started for service "
              + serviceType
              + " as user "
              + this.user.getUsername()
              + " with "
              + serviceList.size()
              + " advertised services");
    }
    this.data = Parameters.create();
    this.id = user.getId();
    if (user.getData().containsKey(UserIdentity.FEDERATION_DATA_PROPERTY)) {
      this.federation = user.getData().get(UserIdentity.FEDERATION_DATA_PROPERTY, Federation.class);
    }
    this.serviceMonitor =
        new ServiceMonitor(
            this,
            SettingsImpl.forServiceOwner(serviceType),
            true,
            serviceList,
            this::notifyLocalService,
            this::notifyLocalEngine);
  }

  void setService(BaseService service) {
    this.service = Objects.requireNonNull(service);
    this.hostServiceId = service.serviceId();
    service.setMaintenanceMode(maintenanceMode.get());
    service.setAtomicOperationMode(atomicOperationMode.get());
    service.setLocality(locality);
  }

  private void notifyLocalEngine(Engine.Status status) {
    // ignore
  }

  private void notifyLocalService(
      KlabService klabService, KlabService.ServiceStatus serviceStatus) {
    // TODO maybe do something.
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
    return "[UserServiceScope] "
        + user.getUsername()
        + ((federation == null || federation.getId().equals(Federation.LOCAL_FEDERATION_ID))
            ? ""
            : ("@" + federation.getId()))
        + " ("
        + (isConnected() ? "connected" : "not connected")
        + ")";
  }

  @Override
  public Parameters<String> getData() {
    return this.data;
  }

  @Override
  public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {

    List<T> ret = new ArrayList<>();
    if (service != null && serviceClass.isAssignableFrom(service.getClass())) {
      ret.add(serviceClass.cast(service));
    }
    if (serviceMonitor != null) {
      ret.addAll(serviceMonitor.getLocallyUsableServices(serviceClass));
    }

    /* sort the list to ensure that a local service is always first */
    ret.sort(Comparator.comparing(s -> s.isLocal() ? 0 : 1));

    return ret;
  }

  @Override
  public <T extends KlabService> T getService(Class<T> serviceClass) {
    /* The service scope can return null if no service is found */
    return getServices(serviceClass).stream().findFirst().orElse(null);
  }

  @Override
  public <T extends KlabService> Optional<T> findService(
      Class<T> serviceClass, Predicate<T> selector) {

    var services = getServices(serviceClass);
    var ret =
        services.stream()
            .filter(serviceClient -> selector == null || selector.test(serviceClient))
            .toList();
    if (!ret.isEmpty()) {
      return Optional.of(ret.getFirst());
    }

    return Optional.empty();
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
    return this.user;
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
  public String getHostServiceId() {
    return this.hostServiceId;
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

  @Override
  public Locality getLocality() {
    return locality;
  }

  @Override
  public boolean isAvailable() {
    return !maintenanceMode.get();
  }

  @Override
  public void setMaintenanceMode(boolean maintenance) {
    maintenanceMode.set(maintenance);
    if (service != null) {
      service.setMaintenanceMode(maintenance);
    }
  }

  @Override
  public void setAtomicOperationMode(boolean atomicOperation) {
    atomicOperationMode.set(atomicOperation);
    if (service != null) {
      service.setAtomicOperationMode(atomicOperation);
    }
  }

  @Override
  public void setLocality(Locality locality) {
    this.locality = locality;
    if (service != null) {
      service.setLocality(locality);
    }
  }

  @Override
  public boolean isBusy() {
    return atomicOperationMode.get();
  }
}
