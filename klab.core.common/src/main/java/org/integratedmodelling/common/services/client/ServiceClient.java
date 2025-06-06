package org.integratedmodelling.common.services.client;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.authentication.scope.ChannelImpl;
import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.services.client.resources.CredentialsRequest;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.PartnerIdentity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.rest.ServiceReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Common implementation of a service client, to be specialized for all service types and APIs.
 * Manages the scope and automatically enables messaging with local services.
 */
public abstract class ServiceClient implements KlabService {

  private Identity identity;
  private Type serviceType;
  private final AtomicBoolean connected = new AtomicBoolean(false);
  private final AtomicBoolean shutdown = new AtomicBoolean(false);
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private final AtomicReference<ServiceStatus> status = new AtomicReference<>(null);
  private AbstractServiceDelegatingScope scope;
  private final URL url;
  private String token;
  private long localPollCycleSeconds = 5;
  private long onlinePollCycleSeconds = 5;
  protected Utils.Http.Client client;
  protected ServiceCapabilities capabilities;
  private KlabService ownerService;
  private boolean local;
  private Parameters<Engine.Setting> settings;
  private String serviceId;
  private final List<BiConsumer<ServiceStatus, Boolean>> statusListeners = new ArrayList<>();

  public Utils.Http.Client getHttpClient() {
    return client;
  }

  public KlabService getOwnerService() {
    return ownerService;
  }

  /**
   * Constructor to use when the client is built to be used within a service, with a
   * pre-authenticated identity.
   *
   * @param serviceType
   * @param identity
   * @param settings
   * @param ownerService
   */
  protected ServiceClient(
      KlabService.Type serviceType,
      URL url,
      Identity identity,
      Parameters<Engine.Setting> settings,
      KlabService ownerService) {
    this.settings = settings;
    this.identity = identity;
    this.ownerService = ownerService;
    this.serviceType = serviceType;
    this.url = url;
    if (this.url != null) {
      connect();
    }
  }

  protected ServiceClient(
      KlabService.Type serviceType,
      URL url,
      Identity identity,
      Parameters<Engine.Setting> settings,
      boolean connect) {
    this.settings = settings;
    this.identity = identity;
    this.serviceType = serviceType;
    this.url = url;
    this.local = Utils.URLs.isLocalHost(url);
    if (connect) {
      connect();
    }
  }

  protected ServiceClient(URL url, Parameters<Engine.Setting> settings) {
    this.url = url;
    connect();
  }

  /**
   * Read status from arbitrary service. Uses own client, no authentication needed, also used as
   * first "ping" to ensure the URL is responding.
   *
   * @param url
   * @return
   */
  public ServiceStatus readServiceStatus(URL url, Scope scope) {
    ServiceStatus ret = null;
    try {
      ret = client.get(ServicesAPI.STATUS, ServiceStatusImpl.class, Notification.Mode.Silent);
    } catch (Throwable t) {
      /* service is or has gone offline, do nothing */
    }
    return ret == null ? ServiceStatus.offline(serviceType, serviceId) : ret;
  }

  /**
   * Connects the service client to the specified service and sets up its status monitoring.
   *
   * <p>This method handles setting up the client with the necessary credentials, initializes local
   * or remote communication channels based on the service configuration, schedules periodic tasks,
   * and attaches listeners for monitoring service status updates.
   *
   * @param statusListeners optional list of listeners that will be notified about service status
   *     updates. Each listener is a {@link BiConsumer} that receives a {@link ServiceStatus} object
   *     indicating the current state of the service and a {@code Boolean} indicating whether the
   *     status has changed in a way that requires action apart from monitored parameters.
   * @return a {@code String} representing a secret token used for local service authentication if
   *     the service is local to the machine where the client is running, or {@code null} if no such
   *     token is necessary.
   */
  @SuppressWarnings("unchecked")
  public String connect(BiConsumer<ServiceStatus, Boolean>... statusListeners) {

    if (this.identity instanceof PartnerIdentity) {
      this.token = ((PartnerIdentity)identity).getToken();
    } else {
      this.token = this.identity.getId();
    }
    String ret = null;
    this.client = Utils.Http.getServiceClient(token, this);
    var secret = Configuration.INSTANCE.getServiceSecret(serviceType);
    if (secret != null) {
      local = Utils.URLs.isLocalHost(getUrl());
      if (local) {
        client.setHeader(ServicesAPI.SERVER_KEY_HEADER, secret);
        ret = secret;
      }
    }

    if (statusListeners != null) {
      this.statusListeners.addAll(Arrays.asList(statusListeners));
    }

    Channel channel =
            local
            ? new MessagingChannelImpl(this.identity, false, ownerService != null) {
          @Override
          public String getDispatchId() {
            return serviceId();
          }

          public String getId() {
                return serviceId();
              }
            }
            : new ChannelImpl(this.identity) {
              @Override
              public String getDispatchId() {
                return serviceId();
              }
            };

    this.scope =
        new AbstractServiceDelegatingScope(channel) {

          @Override
          public UserScope createUser(String username, String password) {
            return null;
          }

          @Override
          public <T extends KlabService> T getService(Class<T> serviceClass) {
            return KlabService.Type.classify(serviceClass) == serviceType
                ? (T) ServiceClient.this
                : null;
          }

          @Override
          public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
            return KlabService.Type.classify(serviceClass) == serviceType
                ? (Collection<T>) List.of(ServiceClient.this)
                : Collections.emptyList();
          }
        };

    scheduler.scheduleAtFixedRate(
        this::timedTasks,
        2,
        this.local ? localPollCycleSeconds : onlinePollCycleSeconds,
        TimeUnit.SECONDS);

    return ret;
  }

  private void timedTasks() {

    if ("off".equals(settings.get(Engine.Setting.POLLING, String.class))) {
      return;
    }

    if (this.shutdown.get()) {
//      scope.send(
//          Message.MessageClass.ServiceLifecycle,
//          Message.MessageType.ServiceStatus,
//          ServiceStatus.offline(serviceType, this.serviceId()));
      return;
    }

    try {

      var connectedBeforeChecking = connected.get();
      var statusBeforeChecking = status.get();

      /*
      TODO check for changes of status and send messages over
       */
      try {
        var currentServiceStatus = readServiceStatus(this.url, scope);
        if (currentServiceStatus == null) {
          connected.set(false);
          status.set(
              ServiceStatus.offline(
                  serviceType,
                  this.capabilities == null ? null : this.capabilities.getServiceId()));
        } else {
          status.set(currentServiceStatus);
          connected.set(true);
          System.out.println(currentServiceStatus.getServiceType() + ": "+currentServiceStatus.getServiceId());
          if (this.capabilities == null) {
            this.capabilities = capabilities(scope);
          }
        }

        ((ServiceStatusImpl) status.get()).setShutdown(this.shutdown.get());

      } finally {

        //        boolean connectionHasChanged = connected.get() != connectedBeforeChecking;
        boolean statusHasChanged =
            (statusBeforeChecking == null && status.get() != null)
                || (statusBeforeChecking != null && status.get() == null)
                || (status.get() != null
                    && statusBeforeChecking != null
                    && status.get().hasChangedComparedTo(statusBeforeChecking));

        if (connected.get()) {

          // see if we have a local service and change the token
          if ((token == null || token.isEmpty()) && Utils.URLs.isLocalHost(getUrl())) {
            // may have gotten lost if the service was starting when we booted
            var secret = Configuration.INSTANCE.getServiceSecret(serviceType);
            if (secret != null) {
              token = secret;
              client.setAuthorization(token);
              local = true;
            }
          }
        }

        if (statusHasChanged) {
          this.capabilities = capabilities(scope);
        }

        for (var listener : statusListeners) {
          listener.accept(status.get(), statusHasChanged);
        }
      }

    } catch (Throwable t) {
      scope.error(t);
    }
  }

  @Override
  public final ServiceScope serviceScope() {
    return scope;
  }

  @Override
  public final URL getUrl() {
    return url;
  }

  public final ServiceStatus status() {
    return status.get() == null ? ServiceStatus.offline(serviceType, serviceId) : status.get();
  }

  @Override
  public final String getLocalName() {
    return this.capabilities == null ? null : this.capabilities.getLocalName();
  }

  @Override
  public final boolean shutdown() {
    this.shutdown.set(true);
    if (local) {
      return client.put(ServicesAPI.ADMIN.SHUTDOWN);
    }
    return false;
  }

  public boolean isLocal() {
    return this.local;
  }

  @Override
  public String serviceId() {
    if (this.serviceId == null) {
      var capabilities = capabilities(scope);
      if (capabilities != null) {
        this.serviceId = capabilities.getServiceId();
      }
    }
    return this.serviceId;
  }

  @Override
  public ResourcePrivileges getRights(String resourceUrn, Scope scope) {
    return client.get(
        ServicesAPI.RESOURCES.RESOURCE_RIGHTS, ResourcePrivileges.class, "urn", resourceUrn);
  }

  @Override
  public boolean setRights(String resourceUrn, ResourcePrivileges resourcePrivileges, Scope scope) {
    return client.put(
        ServicesAPI.RESOURCES.RESOURCE_RIGHTS, resourcePrivileges, "urn", resourceUrn);
  }

  @Override
  public List<ExternalAuthenticationCredentials.CredentialInfo> getCredentialInfo(Scope scope) {
    return client.getCollection(
        ServicesAPI.ADMIN.CREDENTIALS, ExternalAuthenticationCredentials.CredentialInfo.class);
  }

  @Override
  public ExternalAuthenticationCredentials.CredentialInfo addCredentials(
      String host, ExternalAuthenticationCredentials credentials, Scope scope) {
    var request = new CredentialsRequest();
    request.setHost(host);
    request.setCredentials(credentials);
    return client.post(
        ServicesAPI.ADMIN.CREDENTIALS,
        request,
        ExternalAuthenticationCredentials.CredentialInfo.class);
  }

  // util to retrieve the queue names from the header
  protected Set<Message.Queue> getQueuesFromHeader(SessionScope scope, String responseHeader) {
    if (responseHeader != null) {
      var ret = EnumSet.noneOf(Message.Queue.class);
      if (!responseHeader.isBlank()) {
        String[] qq = responseHeader.split(", ");
        for (var q : qq) {
          ret.add(Message.Queue.valueOf(q));
        }
      }
      return ret;
    }
    return scope.defaultQueues();
  }

  @Override
  public InputStream exportAsset(
      String urn, ResourceTransport.Schema exportSchema, String mediaType, Scope scope) {
    try {
      var file =
          client
              .withScope(scope)
              .accepting(List.of(mediaType))
              .download(
                  ServicesAPI.EXPORT, "urn", urn, "class", exportSchema.getKnowledgeClass().name());
      return new FileInputStream(file);
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String importAsset(
      ResourceTransport.Schema schema,
      ResourceTransport.Schema.Asset assetCoordinates,
      String suggestedUrn,
      Scope scope) {

    if (schema.getType() == ResourceTransport.Schema.Type.PROPERTIES) {
      return client
          .withScope(scope)
          .post(
              ServicesAPI.IMPORT,
              assetCoordinates.getProperties(),
              String.class,
              "schema",
              schema.getSchemaId(),
              "urn",
              suggestedUrn);
    } else if (schema.getType() == ResourceTransport.Schema.Type.STREAM) {
      var file = assetCoordinates.getFile();
      if (file == null && assetCoordinates.getUrl() != null) {
        file = Utils.URLs.getFileForURL(assetCoordinates.getUrl());
      }

      if (file != null && file.exists()) {

        if (schema.getMediaTypes().isEmpty()) {
          throw new KlabInternalErrorException(
              "Cannot import a binary asset with a schema that " + "does not specify a media type");
        }

        return client
            .withScope(scope)
            .providing(schema.getMediaTypes())
            .upload(
                ServicesAPI.IMPORT,
                assetCoordinates.getFile(),
                String.class,
                "schema",
                schema.getSchemaId(),
                "urn",
                suggestedUrn);
      }
    }

    return null;
  }

  public void setProperties(ServiceReference serviceReference) {
    // TODO set owner, local/remote names etc. We already have the identity set up in the
    // constructor.
  }
}
