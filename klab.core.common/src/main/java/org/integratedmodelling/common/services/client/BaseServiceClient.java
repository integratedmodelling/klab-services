package org.integratedmodelling.common.services.client;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.glassfish.tyrus.spi.ClientContainer;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.resources.CredentialsRequest;
import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.objects.ScopeRequest;
import org.integratedmodelling.klab.api.services.runtime.objects.UserScopeNotification;

public abstract class BaseServiceClient implements KlabService {

  protected final Scope userScope;
  private final ServiceClientCatalog.ClientMonitor monitor;
  protected final Utils.Http.Client client;
  private final ServiceScope serviceScope;
  protected final Settings settings;

  List<BiConsumer<ServiceStatus, Boolean>> statusListeners = new ArrayList<>();

  public BaseServiceClient(
      ServiceClientCatalog.ClientMonitor monitor,
      Scope scope,
      Settings settings,
      BiConsumer<ServiceStatus, Boolean>... statusListeners) {
    this.monitor = monitor;
    this.userScope = scope;
    this.client = monitor.getClient().withIdentity(scope.getIdentity());
    this.settings = settings;
    this.monitor.registerClient(this);
    this.serviceScope =
        new AbstractServiceDelegatingScope(scope) {
          @Override
          public <T extends KlabService> T getService(Class<T> serviceClass) {
            throw new KlabIllegalStateException(
                "Service clients don't hold other services in their scopes");
          }

          @Override
          public <T extends KlabService> Optional<T> findService(
              Class<T> serviceClass, Predicate<T>... selectors) {
            return Optional.empty();
          }

          @Override
          public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
            throw new KlabIllegalStateException(
                "Service clients don't hold other services in their scopes");
          }
        };

    var secret = Configuration.INSTANCE.getServiceSecret(monitor.getType());
    if (secret != null && monitor.isLocal()) {
      client.setHeader(ServicesAPI.SERVER_KEY_HEADER, secret);
    }
    if (statusListeners != null) {
      this.statusListeners.addAll(List.of(statusListeners));
    }
  }

  @Override
  public ServiceStatus status() {
    return monitor.getStatus().get();
  }

  @Override
  public URL getUrl() {
    return monitor.getUrl();
  }

  @Override
  public String serviceName() {
    return capabilities(userScope).getServiceName();
  }

  @Override
  public String serviceId() {
    return monitor.getServiceId();
  }

  @Override
  public Settings settings() {
    return settings;
  }

  @Override
  public Scope serviceScope() {
    return serviceScope;
  }

  public boolean isLocal() {
    return monitor.isLocal();
  }

  @Override
  public boolean shutdown() {
    int refCount = monitor.release(this);
    if (refCount == 0 && monitor.isLocal()) {
      // TODO invoke shutdown on the server
    }
    return false;
  }

  @Override
  public String declareSessionScope(
      SessionScope sessionScope, UserScope userScope, KActorsBehavior behavior) {

    ScopeRequest request = new ScopeRequest();
    request.setConfiguration(
        DigitalTwin.Configuration.builder()
            .id(sessionScope.getId())
            .name(sessionScope.getName())
            .build());
    request
        .getServiceIds()
        .addAll(
            sessionScope.getServices(KlabService.class).stream()
                .map(KlabService::serviceId)
                .toList());

    var scopeId =
        client.withScope(userScope).post(ServicesAPI.CREATE_SESSION, request, String.class);
    return scopeId == null ? null : setupMessaging(sessionScope, userScope, scopeId);
  }

  @Override
  public String declareContextScope(
      ContextScope contextScope, SessionScope sessionScope, UserScope userScope) {

    ScopeRequest request = new ScopeRequest();
    request.setConfiguration(contextScope.getConfiguration());
    request
        .getServiceIds()
        .addAll(
            contextScope.getServices(KlabService.class).stream()
                .map(KlabService::serviceId)
                .toList());

    var scopeId =
        client.withScope(sessionScope).post(ServicesAPI.CREATE_CONTEXT, request, String.class);

    if (scopeId != null) {
      setupMessaging(contextScope, sessionScope, scopeId);
    }
    return scopeId;
  }

  private String setupMessaging(SessionScope sessionScope, UserScope userScope, String scopeId) {
    var federation = Klab.INSTANCE.getFederationData(userScope.getUser());
    if (federation != null && sessionScope instanceof MessagingChannelImpl messagingChannel) {
      var queues =
          getQueuesFromHeader(
              sessionScope, client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
      if (queues == null) {
        // TODO error recovery
        Logging.INSTANCE.error("no queues found in messaging header");
      }
      messagingChannel.setupMessaging(federation, scopeId, queues);
    }
    return scopeId;
  }

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
  public ResourcePrivileges getRights(String resourceUrn, Scope scope) {
    return client
        .withScope(scope)
        .get(ServicesAPI.RESOURCES.RESOURCE_RIGHTS, ResourcePrivileges.class, "urn", resourceUrn);
  }

  @Override
  public boolean setRights(String resourceUrn, ResourcePrivileges resourcePrivileges, Scope scope) {
    return client
        .withScope(scope)
        .put(ServicesAPI.RESOURCES.RESOURCE_RIGHTS, resourcePrivileges, "urn", resourceUrn);
  }

  @Override
  public List<ExternalAuthenticationCredentials.CredentialInfo> getCredentialInfo(Scope scope) {
    return client
        .withScope(scope)
        .getCollection(
            ServicesAPI.ADMIN.CREDENTIALS, ExternalAuthenticationCredentials.CredentialInfo.class);
  }

  @Override
  public ExternalAuthenticationCredentials.CredentialInfo addCredentials(
      String host, ExternalAuthenticationCredentials credentials, Scope scope) {
    var request = new CredentialsRequest();
    request.setHost(host);
    request.setCredentials(credentials);
    return client
        .withScope(scope)
        .post(
            ServicesAPI.ADMIN.CREDENTIALS,
            request,
            ExternalAuthenticationCredentials.CredentialInfo.class);
  }

  @Override
  public <T extends Serializable> T retrieveAsset(
      String urn, Scheduler.Event locator, Class<T> assetClass, Scope scope) {
    return null;
  }

  @Override
  public InputStream exportAsset(
      String urn,
      KlabAsset.KnowledgeClass knowledgeClass,
      String mediaType,
      Parameters<String> parameters,
      Scope scope) {
    try {
      var params = new ArrayList<Object>();
      params.addAll(List.of("urn", urn, "class", knowledgeClass.name()));
      parameters.forEach(
          (k, v) -> {
            params.add(k);
            params.add(v);
          });
      var file =
          client
              .withScope(scope)
              .accepting(List.of(mediaType))
              .download(ServicesAPI.EXPORT, params.toArray(new Object[0]));
      return new FileInputStream(file);
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public CompletableFuture<ResourceSet> importAsset(
      ResourceTransport.Schema schema,
      ResourceTransport.Schema.Asset assetCoordinates,
      String suggestedUrn,
      Scope scope) {

    if (schema.getType() == ResourceTransport.Schema.Type.PROPERTIES) {
      return client
          .withScope(scope)
          .postAsync(
              ServicesAPI.IMPORT,
              assetCoordinates.getProperties(),
              ResourceSet.class,
              "schema",
              schema.getSchemaId(),
              "urn",
              suggestedUrn == null ? Urn.UNDEFINED_URN : suggestedUrn);
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
            .uploadAsync(
                ServicesAPI.IMPORT,
                assetCoordinates.getFile(),
                ResourceSet.class,
                "schema",
                schema.getSchemaId(),
                "urn",
                suggestedUrn);
      }
    }

    return null;
  }

  protected <T extends ServiceCapabilities> T getCapabilities(Scope scope, Class<T> tClass) {
    try {
      return client
          .withScope(scope)
          .get(ServicesAPI.CAPABILITIES, tClass, Notification.Mode.Silent);
    } catch (Throwable t) {
      // not ready yet
      return null;
    }
  }

  public void addListener(BiConsumer<ServiceStatus, Boolean> listener) {
    statusListeners.add(listener);
  }

  /**
   * Advertise the scope to the remote service. If the remote service is not OK with them,
   * deactivate.
   *
   * @param request
   */
  public void notifyScope(UserScopeNotification request) {

    /*
    If we're notifying a remote service, do not add local services to the request.
     */
    if (!Utils.URLs.isLocalHost(this.getUrl())) {
      request.removeLocalServices();
    }

    if (request.getServices().isEmpty()) {
      return;
    }

    if (!client.post(ServicesAPI.NOTIFY_USER_SCOPE, request, Boolean.class)) {
      Logging.INSTANCE.error(
          "Failed to notify remote service of new user scope: deactivating service client");
      // TODO deactivate (operational should return false)
    } else {
      Logging.INSTANCE.info("Successfully notified remote service of new user scope");
    }
  }

  public boolean isAlive() {
    return client.isAlive();
  }

  @Override
  public boolean loadResources(ResourceSet resourceSet, Scope scope) {
    // TODO this can be done through the API so it should be provided, although there are so far
    //  no use cases for it. It's definitely an admin endpoint.
    throw new KlabUnimplementedException("loadResources() is not implemented on clients yet");
  }
}
