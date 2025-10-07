package org.integratedmodelling.common.services.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.common.services.client.scope.ClientScopeManager;
import org.integratedmodelling.common.services.client.scope.ClientSessionScope;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.objects.ScopeRequest;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.integratedmodelling.klab.api.utils.Utils;

public class RuntimeClient extends BaseServiceClient
    implements RuntimeService, RuntimeService.Admin {

  private Capabilities capabilities = null;

  RuntimeClient(
      ServiceClientCatalog.ClientMonitor monitor,
      Scope userScope,
      Settings settings,
      BiConsumer<KlabService.ServiceStatus, Boolean>... statusListeners) {
    super(monitor, userScope, settings, statusListeners);
  }

  @Override
  public CompletableFuture<Observation> submit(Observation observation, ContextScope scope) {

    if (observation.getId() > 0) {
      return CompletableFuture.completedFuture(observation);
    }

    ResolutionRequest resolutionRequest = new ResolutionRequest();
    resolutionRequest.setObservation(observation);
    resolutionRequest.setAgentName(Provenance.getAgent(scope).getName());
    resolutionRequest.setResolutionConstraints(scope.getResolutionConstraints());
    return client
        .withScope(scope)
        .postAsync(ServicesAPI.RUNTIME.SUBMIT_OBSERVATION, resolutionRequest, Observation.class);
  }

  @Override
  public Capabilities capabilities(Scope scope) {
    return capabilities == null
        ? getCapabilities(scope, RuntimeCapabilitiesImpl.class)
        : capabilities;
  }

  @Override
  public List<SessionInfo> getSessionInfo(Scope scope) {
    return client
        .withScope(scope)
        .getCollection(ServicesAPI.RUNTIME.GET_SESSION_INFO, SessionInfo.class);
  }

  @Override
  public ContextScope connectContext(DigitalTwin.Configuration configuration, UserScope userScope) {

    var ret = ClientScopeManager.INSTANCE.getScope(configuration.getId(), ClientContextScope.class);
    if (ret != null) {
      return ret;
    }

    ScopeRequest request = new ScopeRequest();
    request.setConfiguration(configuration);
    userScope.getServices(KlabService.class).stream()
        .forEach(
            s -> {
              if (s.serviceId() != null) request.getServiceIds().add(s.serviceId());
            });

    var descriptor =
        client
            .withScope(userScope)
            .post(ServicesAPI.RUNTIME.CONNECT, request, DigitalTwin.Configuration.class);

    if (descriptor != null && !Utils.Notifications.hasErrors(descriptor.getNotifications())) {

      final var service = this;
      descriptor.getNotifications().forEach(n -> userScope.send(n));

      var sessionId = Utils.Paths.getLeading(configuration.getId(), '.');
      var sessionScope = ClientScopeManager.INSTANCE.getScope(sessionId, ClientSessionScope.class);
      if (sessionScope == null) {
        sessionScope = (ClientSessionScope) userScope.getUserSession(this);
        ClientScopeManager.INSTANCE.register(sessionScope);
      }

      // Add the known data that are null; notify for anything that isn't and differs.
      configuration.defineFromExisting(descriptor);

      ret = new ClientContextScope(sessionScope, this, configuration);
      ret.setId(descriptor.getId());
      ret.createDigitalTwin(descriptor.getId());
      return ret;
    }

    return null;
  }

  @Override
  public DigitalTwin.Configuration getConfiguration(String scopeId, UserScope scope) {
    return client
        .withScope(scope)
        .get(
            ServicesAPI.RUNTIME.GET_DIGITAL_TWIN_CONFIGURATION,
            DigitalTwin.Configuration.class,
            "id",
            scopeId);
  }

  @Override
  public boolean releaseSession(SessionScope scope) {
    try {
      return client.withScope(scope).get(ServicesAPI.RELEASE_SESSION, Boolean.class);
    } catch (Throwable t) {
      // just return false
    }
    return false;
  }

  @Override
  public boolean releaseContext(ContextScope scope) {
    try {
      return client.withScope(scope).get(ServicesAPI.RELEASE_CONTEXT, Boolean.class);
    } catch (Throwable t) {
      // just return false
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends RuntimeAsset> List<T> queryKnowledgeGraph(
      KnowledgeGraph.Query<T> knowledgeGraphQuery, Scope scope) {
    if (knowledgeGraphQuery instanceof KnowledgeGraphQuery<T> knowledgeGraphQuery1) {
      return (List<T>)
          client
              .withScope(scope)
              .postCollection(
                  ServicesAPI.RUNTIME.QUERY,
                  knowledgeGraphQuery,
                  knowledgeGraphQuery1.getResultType().getAssetClass());
    }
    throw new KlabIllegalStateException("Knowledge graph query using unexpected implementation");
  }

  @Override
  public ResourceSet resolveContextualizables(
      List<Contextualizable> contextualizables, ContextScope scope) {

    if (contextualizables.isEmpty()) {
      return new ResourceSet();
    }

    /**
     * Only send over those that will need resolution at the runtime side. No need to send a lookup
     * table or classification asset.
     */
    List<Contextualizable> request =
        contextualizables.stream()
            .filter(
                contextualizable ->
                    !contextualizable.getResourceUrns().isEmpty()
                        || contextualizable.getServiceCall() != null)
            .toList();

    return client
        .withScope(scope)
        .post(ServicesAPI.RUNTIME.RESOLVE_CONTEXTUALIZERS, request, ResourceSet.class);
  }

  @Override
  public Map<String, String> getExceptionTestcases(Scope scope, boolean deleteExisting) {
    return Map.of();
  }

  //  public GraphQLClient graphClient() {
  //    return graphClient;
  //  }
}
