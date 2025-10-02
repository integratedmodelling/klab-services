package org.integratedmodelling.common.services.client;

import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;

import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public abstract class BaseServiceClient implements KlabService {

  protected final UserScope userScope;
  private final ServiceClientCatalog.ServiceMonitor monitor;
  protected final Utils.Http.Client client;
  private final ServiceScope serviceScope;

  public BaseServiceClient(ServiceClientCatalog.ServiceMonitor monitor, UserScope userScope) {
    this.monitor = monitor;
    this.userScope = userScope;
    this.client = monitor.getClient().withIdentity(userScope.getUser());
    this.monitor.registerClient();
    this.serviceScope =
        new AbstractServiceDelegatingScope(userScope) {
          @Override
          public <T extends KlabService> T getService(
              Class<T> serviceClass, Predicate<T>... selectors) {
            throw new KlabIllegalStateException(
                "Service clients don't hold other services in their scopes");
          }

          @Override
          public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
            throw new KlabIllegalStateException(
                "Service clients don't hold other services in their scopes");
          }
        };
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
    return monitor.getServerId();
  }

  @Override
  public Settings settings() {
    return null;
  }

  @Override
  public Scope serviceScope() {
    return serviceScope;
  }

  @Override
  public boolean shutdown() {
    int refCount = monitor.release();
    if (refCount == 0 && monitor.isLocal()) {
      // TODO invoke shutdown on the server
    }
    return false;
  }

  @Override
  public String registerNewSession(
      SessionScope sessionScope, UserScope userScope, KActorsBehavior behavior) {
    return "";
  }

  @Override
  public String registerNewContext(ContextScope contextScope, UserScope userScope) {
    return "";
  }

  @Override
  public ResourcePrivileges getRights(String resourceUrn, Scope scope) {
    return null;
  }

  @Override
  public boolean setRights(String resourceUrn, ResourcePrivileges resourcePrivileges, Scope scope) {
    return false;
  }

  @Override
  public List<ExternalAuthenticationCredentials.CredentialInfo> getCredentialInfo(Scope scope) {
    return List.of();
  }

  @Override
  public ExternalAuthenticationCredentials.CredentialInfo addCredentials(
      String host, ExternalAuthenticationCredentials credentials, Scope scope) {
    return null;
  }

  @Override
  public <T extends Serializable> T retrieveAsset(
      String urn, Scheduler.Event locator, Class<T> assetClass, Scope scope) {
    return null;
  }

  @Override
  public InputStream exportAsset(
      String urn, KlabAsset.KnowledgeClass knowledgeClass, String mediaType, Scope scope) {
    return null;
  }

  @Override
  public CompletableFuture<ResourceSet> importAsset(
      ResourceTransport.Schema schema,
      ResourceTransport.Schema.Asset assetCoordinates,
      String suggestedUrn,
      Scope scope) {
    return null;
  }
}
