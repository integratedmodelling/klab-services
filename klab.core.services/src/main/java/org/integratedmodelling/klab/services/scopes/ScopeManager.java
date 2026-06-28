package org.integratedmodelling.klab.services.scopes;

// import io.reacted.core.config.reactorsystem.ReActorSystemConfig;
// import io.reacted.core.reactorsystem.ReActorSystem;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.common.authentication.UserIdentityImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.PartnerIdentity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.ReactiveScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceSideScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.rest.GroupImpl;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;

/**
 * The scope manager maintains service-side scopes that are generated through the orchestrating
 * engine. When actors are requested, the necessary chain is created as needed. The main strategy
 * for resource maintenance and session expiration is here.
 */
public class ScopeManager {

  //  private ReActorSystem actorSystem = null;
  private KlabService service;

  /**
   * Every scope managed by this service. The relationship between scopes is managed through the
   * scope graph, using only the IDs. Scopes may persist in services that allow that, and that is
   * managed externally by recreating the scopes and their content upon request.
   */
  private final Map<String, ServiceUserScope> scopes = new ConcurrentHashMap<>();

  private Map<String, Long> idleScopeTime = new ConcurrentHashMap<>();
  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

  public ScopeManager(KlabService service) {

    this.service = service;

    executor.scheduleAtFixedRate(() -> expiredScopeCheck(), 60, 60, TimeUnit.SECONDS);
  }

  private String serviceId() {
    return service == null ? "<no-service>" : service.serviceId();
  }

  private String scopeId(Scope scope) {
    if (scope instanceof ServiceSideScope serviceSideScope) {
      return serviceSideScope.getId();
    }
    if (scope instanceof SessionScope sessionScope) {
      return sessionScope.getId();
    }
    return "<no-id>";
  }

  private String describeScope(Scope scope) {
    if (scope == null) {
      return "<null-scope>";
    }

    var ret =
        new StringBuilder(scope.getClass().getSimpleName())
            .append("[type=")
            .append(scope.getType())
            .append(", id=")
            .append(scopeId(scope));

    if (scope instanceof ReactiveScope reactiveScope) {
      ret.append(", host=").append(reactiveScope.getHostServiceId());
    }
    if (scope instanceof UserScope userScope && userScope.getUser() != null) {
      ret.append(", user=").append(userScope.getUser().getUsername());
    }
    if (scope.getParentScope() != null) {
      ret.append(", parent=").append(scopeId(scope.getParentScope()));
    }
    return ret.append("]").toString();
  }

  private String describeAuthorization(EngineAuthorization authorization) {
    return authorization == null
        ? "<no-authorization>"
        : "user="
            + authorization.getUsername()
            + ", authenticated="
            + authorization.isAuthenticated()
            + ", local="
            + authorization.isLocal();
  }

  private String header(Map<String, String> requestHeaders, String header) {
    return requestHeaders == null ? null : requestHeaders.get(header);
  }

  private long parseHeaderLong(
      Map<String, String> requestHeaders, String header, ServiceContextScope scope) {
    var value = header(requestHeaders, header);
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      Logging.INSTANCE.error(
          "Invalid scope propagation header "
              + header
              + "="
              + value
              + " for "
              + describeScope(scope)
              + " in service "
              + serviceId()
              + ": "
              + Utils.Exceptions.stackTrace(e));
      throw e;
    }
  }

  private void expiredScopeCheck() {

    // send each scope closing to a virtual thread after removing from the scope map
  }

  public void registerScope(ServiceUserScope serviceScope) {
    if (serviceScope == null || serviceScope.getId() == null) {
      Logging.INSTANCE.error(
          "Cannot register null or unidentified scope in service "
              + serviceId()
              + ": "
              + describeScope(serviceScope));
      throw new KlabIllegalArgumentException("Cannot register a scope without an ID");
    }

    var previous = scopes.put(serviceScope.getId(), serviceScope);
    if (previous != null && previous != serviceScope) {
      Logging.INSTANCE.warn(
          "Replacing managed scope "
              + describeScope(previous)
              + " with "
              + describeScope(serviceScope)
              + " in service "
              + serviceId());
    } else {
      Logging.INSTANCE.info(
          "Registered scope " + describeScope(serviceScope) + " in service " + serviceId());
    }
  }

  public ServiceUserScope login(UserIdentity user) {

    ServiceUserScope ret = scopes.get(user.getUsername());
    if (ret == null) {
      ret = new ServiceUserScope(user, service);
      ret.setId(user.getUsername());
      scopes.put(user.getUsername(), ret);
      Logging.INSTANCE.info(
          "Created service user scope for "
              + user.getUsername()
              + " in service "
              + serviceId());
    }

    return ret;
  }

  /**
   * Logout a previously logged in scope. Based on the ID, this will match a scope at any level and
   * release any resources held by that scope or any scope at a lower level.
   *
   * @param scopeId
   * @return true if the scope existed and was released.
   */
  public boolean logout(String scopeId) {
    // TODO kill the actor if it's there, that should release all resources
    var scope = scopes.get(scopeId);
    return false;
  }

  private UserIdentity createUserIdentity(EngineAuthorization engineAuthorization) {

    Logging.INSTANCE.info(service.serviceName() + " received user identity request for " + engineAuthorization.getUsername());

    UserIdentityImpl ret = new UserIdentityImpl();
    ret.setUsername(engineAuthorization.getUsername());
    ret.setEmailAddress(engineAuthorization.getEmailAddress());
    ret.setId(engineAuthorization.getToken());
    ret.setAuthenticated(engineAuthorization.isAuthenticated());
    ret.setIdentityType(Identity.Type.ENGINE_USER);
    ret.setAnonymous(
        "anonymous".equals(engineAuthorization.getUsername())
            || ServicesAPI.ANONYMOUS_TOKEN.equals(engineAuthorization.getToken()));
    ret.setOnline(!ret.isAnonymous());
    URL hub = null;
    if (service.serviceScope().getIdentity() instanceof PartnerIdentity serviceIdentity) {
      Collection<GroupImpl> groups = null;
      try {
        hub = new URI(serviceIdentity.getAuthenticatingHub()).toURL();
      } catch (MalformedURLException | URISyntaxException e) {
        throw new KlabIllegalArgumentException(e);
      }
      String token = serviceIdentity.getToken();
      Utils.Http.Client client = Utils.Http.getClient(hub, service.serviceScope());
      groups =
          client
              .withAuthentication(token)
              .getCollection(
                  ServicesAPI.HUB.USER_BASE_ID_SERVICES.replace("{id}", ret.getUsername()),
                  GroupImpl.class);
      if (groups != null) {
        ret.getGroups().addAll(groups);
      }
    } else {
      ret.getGroups().addAll(engineAuthorization.getGroups());
    }
    return ret;
  }

  public synchronized <T extends Scope> List<T> getScopes(Scope.Type type, Class<T> scopeClass) {
    List<T> ret = new ArrayList<>();
    for (var scope : scopes.values()) {
      if (scope.getType() == type) {
        ret.add((T) scope);
      }
    }
    return ret;
  }

  public ServiceUserScope getOrCreateUserScope(EngineAuthorization authorization) {

    var ret = scopes.get(authorization.getUsername());
    if (ret instanceof ServiceUserScope userScope) {
      return userScope;
    }

    ret = login(createUserIdentity(authorization));

    ret.getRoles().addAll(authorization.getRoles());
    var federation = Klab.INSTANCE.getFederationData(ret.getUser());
    if (federation != null) {
      var brokerURI = federation.getBroker();
      if (brokerURI != null) {
        ret.setupMessaging(federation, federation.getId(), ret.defaultQueues());
      }
    }
    Logging.INSTANCE.info(
        "User "
            + ret.getUser().getUsername()
            + (federation == null
                ? " is not part of any federation"
                : " part of federation " + federation.getId()));
    return ret;
  }

  /**
   * Assuming a valid rootScope is passed corresponding to the scope ID in the contextualization,
   * create the child scopes as specified by the other elements of the scope token.
   *
   * @param rootScope
   * @param contextualization
   * @return
   */
  public ContextScope contextualizeScope(
      ServiceContextScope rootScope,
      ContextScope.ScopeData contextualization,
      Map<String, String> requestHeaders) {

    ServiceContextScope ret = rootScope;

    if (contextualization.observationPath() != null) {
      for (var observationId : contextualization.observationPath()) {
        var observation = ret.getObservation(observationId);
        if (observation == null) {
          throw new KlabResourceAccessException(
              "Observation with ID "
                  + observationId
                  + " not "
                  + "found in context "
                  + ret.getName());
        }
        ret = ret.within(observation);
      }
    }

    if (contextualization.observerId() != Observation.UNASSIGNED_ID) {
      var observer = ret.getObservation(contextualization.observerId());
      if (observer == null) {
        throw new KlabResourceAccessException(
            "Subject with ID "
                + contextualization.observerId()
                + " not found in "
                + "context "
                + ret.getName());
      }
      ret = ret.withObserver(observer);
    }

    if (ret instanceof ServiceContextScope serviceContextScope) {

      // the scope
      if (requestHeaders.get(ServicesAPI.TRANSACTION_ID_HEADER) != null) {
        if (service instanceof RuntimeService runtimeService) {

          var transaction =
              ret.getTransaction(requestHeaders.get(ServicesAPI.TRANSACTION_ID_HEADER));
          if (transaction != null) {
            ret = ret.withTransaction(transaction);
            Logging.INSTANCE.debug(
                "Attached transaction "
                    + requestHeaders.get(ServicesAPI.TRANSACTION_ID_HEADER)
                    + " to "
                    + describeScope(ret)
                    + " in service "
                    + serviceId());
          } else {
            Logging.INSTANCE.warn(
                "Requested transaction "
                    + requestHeaders.get(ServicesAPI.TRANSACTION_ID_HEADER)
                    + " is not registered in "
                    + describeScope(ret)
                    + " for service "
                    + serviceId());
          }

        } else {
          ret = new ServiceContextScope(serviceContextScope);
          ret.setRemoteTransactionId(requestHeaders.get(ServicesAPI.TRANSACTION_ID_HEADER));
          Logging.INSTANCE.debug(
              "Propagated remote transaction "
                  + requestHeaders.get(ServicesAPI.TRANSACTION_ID_HEADER)
                  + " to "
                  + describeScope(ret)
                  + " in service "
                  + serviceId());
        }
      }

      if (service instanceof RuntimeService runtimeService
          && requestHeaders.get(ServicesAPI.CONTEXT_OBSERVATION_ID_HEADER) != null) {
        // lookup observations either in the current transaction or knowledge graph
        var contextObservationId =
            parseHeaderLong(
                requestHeaders, ServicesAPI.CONTEXT_OBSERVATION_ID_HEADER, serviceContextScope);
        var ctx = ret.getObservation(contextObservationId);
        if (ctx != null) {
          ret = ret.within(ctx);
          Logging.INSTANCE.debug(
              "Contextualized "
                  + describeScope(ret)
                  + " within observation "
                  + contextObservationId
                  + " in service "
                  + serviceId());
        } else {
          Logging.INSTANCE.error(
              "Null observation for context header "
                  + ServicesAPI.CONTEXT_OBSERVATION_ID_HEADER
                  + "="
                  + requestHeaders.get(ServicesAPI.CONTEXT_OBSERVATION_ID_HEADER)
                  + " in "
                  + describeScope(ret)
                  + " for service "
                  + serviceId());
        }
      }

      if (requestHeaders.get(ServicesAPI.SOURCE_OBSERVATION_ID_HEADER) != null
          && requestHeaders.get(ServicesAPI.TARGET_OBSERVATION_ID_HEADER) != null) {
        // lookup observations either in the current transaction or knowledge graph
        var src =
            ret.getObservation(
                parseHeaderLong(
                    requestHeaders, ServicesAPI.SOURCE_OBSERVATION_ID_HEADER, serviceContextScope));
        var tgt =
            ret.getObservation(
                parseHeaderLong(
                    requestHeaders, ServicesAPI.TARGET_OBSERVATION_ID_HEADER, serviceContextScope));
        if (src != null && tgt != null) {
          ret = (ServiceContextScope) ret.between(src, tgt);
          Logging.INSTANCE.debug(
              "Contextualized "
                  + describeScope(ret)
                  + " between observations "
                  + requestHeaders.get(ServicesAPI.SOURCE_OBSERVATION_ID_HEADER)
                  + " and "
                  + requestHeaders.get(ServicesAPI.TARGET_OBSERVATION_ID_HEADER)
                  + " in service "
                  + serviceId());
        } else {
          Logging.INSTANCE.error(
              "Null observations for relationship headers "
                  + requestHeaders.get(ServicesAPI.SOURCE_OBSERVATION_ID_HEADER)
                  + ", "
                  + requestHeaders.get(ServicesAPI.TARGET_OBSERVATION_ID_HEADER)
                  + " in "
                  + describeScope(ret)
                  + " for service "
                  + serviceId());
        }
      } else if (requestHeaders.get(ServicesAPI.SOURCE_OBSERVATION_ID_HEADER) != null
          || requestHeaders.get(ServicesAPI.TARGET_OBSERVATION_ID_HEADER) != null) {
        Logging.INSTANCE.warn(
            "Incomplete relationship scope headers for "
                + describeScope(ret)
                + " in service "
                + serviceId()
                + ": source="
                + requestHeaders.get(ServicesAPI.SOURCE_OBSERVATION_ID_HEADER)
                + ", target="
                + requestHeaders.get(ServicesAPI.TARGET_OBSERVATION_ID_HEADER));
      }
    }
    return ret;
  }

  public <T extends Scope> T getScope(String scopeId, Class<T> scopeClass) {
    if (scopeId == null) {
      Logging.INSTANCE.warn(
          "Scope lookup requested with null ID for "
              + scopeClass.getSimpleName()
              + " in service "
              + serviceId());
      return null;
    }
    var ret = scopes.get(scopeId);
    if (ret != null && scopeClass.isAssignableFrom(ret.getClass())) {
      return (T) ret;
    }
    if (ret != null) {
      Logging.INSTANCE.warn(
          "Scope "
              + scopeId
              + " found as "
              + describeScope(ret)
              + " but requested as "
              + scopeClass.getSimpleName()
              + " in service "
              + serviceId());
    }
    return null;
  }

  /**
   * Remove a scope from the catalog. Does not do anything else: meant to be used after scope
   * closing and child scope removal.
   *
   * @param scopeId
   * @return
   */
  public boolean releaseScope(String scopeId) {
    if (scopeId == null) {
      Logging.INSTANCE.warn("Ignoring null scope release request in service " + serviceId());
      return false;
    }
    var removed = scopes.remove(scopeId);
    if (removed != null) {
      Logging.INSTANCE.info(
          "Released scope " + describeScope(removed) + " from service " + serviceId());
      return true;
    }
    Logging.INSTANCE.warn("Requested release of unknown scope " + scopeId + " in " + serviceId());
    return false;
  }

  /**
   * Get the scope for the passed parameters. If the scope isn't there or has expired, rebuild it by
   * locating the service
   *
   * @param authorization
   * @param scopeClass
   * @param scopeId
   * @param <T>
   * @return
   */
  public <T extends Scope> T getScope(
      EngineAuthorization authorization, Class<T> scopeClass, String scopeId, String runtimeId) {

    var userScope = getOrCreateUserScope(authorization);
    if (scopeId == null && userScope != null && scopeClass.isAssignableFrom(userScope.getClass())) {
      return (T) userScope;
    }

    if (scopeId != null && userScope != null) {

      ContextScope.ScopeData scopeData;
      try {
        scopeData = ContextScope.parseScopeId(scopeId);
      } catch (RuntimeException e) {
        Logging.INSTANCE.error(
            "Cannot parse propagated scope ID "
                + scopeId
                + " while resolving "
                + scopeClass.getSimpleName()
                + " in service "
                + serviceId()
                + " for "
                + describeAuthorization(authorization)
                + ": "
                + Utils.Exceptions.stackTrace(e));
        throw e;
      }

      var ret = scopes.get(scopeId);
      if (ret != null && scopeClass.isAssignableFrom(ret.getClass())) {

        if (scopeData.type() == Scope.Type.CONTEXT
            && !ret.getUser().getUsername().equals(userScope.getUser().getUsername())) {
          ret = ((ServiceContextScope) ret).withIdentity(userScope.getIdentity());
          Logging.INSTANCE.debug(
              "Using context scope "
                  + scopeId
                  + " with request identity "
                  + userScope.getUser().getUsername()
                  + " in service "
                  + serviceId());
        }

        return (T) ret;
      } else if (ret != null) {
        Logging.INSTANCE.warn(
            "Managed scope "
                + describeScope(ret)
                + " did not match requested class "
                + scopeClass.getSimpleName()
                + " for propagated scope "
                + scopeId
                + " in service "
                + serviceId());
      }

      // not available but it's an inner scope; see if we can reconstruct the scope
      if (ContextScope.class.isAssignableFrom(scopeClass)) {

        if (runtimeId == null) {
          Logging.INSTANCE.warn(
              "Cannot reconstruct context scope "
                  + scopeId
                  + " without "
                  + ServicesAPI.SERVICE_ID_HEADER
                  + " header in service "
                  + serviceId()
                  + " for "
                  + describeAuthorization(authorization));
          return null;
        }

        var sessionId = scopeId.split("\\.")[0];
        var sessionScope = getOrCreateSessionScope(sessionId, authorization, userScope, runtimeId);
        if (sessionScope == null) {
          // TODO handle isEmpty() and notifications upstream
          Logging.INSTANCE.warn(
              "Cannot reconstruct context scope "
                  + scopeId
                  + ": session "
                  + sessionId
                  + " is unavailable in service "
                  + serviceId()
                  + " for "
                  + describeAuthorization(authorization));
          return null;
        }

        // we need the original service to retrieve the configuration
        var originalService =
            userScope
                .findService(RuntimeService.class, s -> runtimeId.equals(s.serviceId()))
                .orElse(null);

        if (originalService != null) {
          Logging.INSTANCE.debug(
              "Requesting configuration for reconstructed context "
                  + scopeId
                  + " from runtime "
                  + runtimeId
                  + " in service "
                  + serviceId());
          var configuration = originalService.getConfiguration(scopeId, userScope);
          if (configuration != null) {
            ret = new ServiceContextScope(sessionScope, configuration, userScope.getUser());
            for (var service : sessionScope.getServices(KlabService.class)) {
              ret.addService(service);
            }
            ret.setId(scopeId);
            ret.setHostServiceId(runtimeId);
            service.declareContextScope((ContextScope) ret, sessionScope, userScope);
            if (!sessionScope.getUser().getUsername().equals(authorization.getUsername())) {
              ret =
                  ((ServiceContextScope) ret)
                      .withIdentity(scopes.get(authorization.getUsername()).getIdentity());
            }
            Logging.INSTANCE.info(
                "Reconstructed context scope "
                    + describeScope(ret)
                    + " from runtime "
                    + runtimeId
                    + " in service "
                    + serviceId());
            return (T) ret;
          } else {
            Logging.INSTANCE.warn(
                "Runtime "
                    + runtimeId
                    + " returned no configuration for context "
                    + scopeId
                    + " in service "
                    + serviceId());
          }
        } else {
          Logging.INSTANCE.warn(
              "Cannot reconstruct context scope "
                  + scopeId
                  + ": runtime "
                  + runtimeId
                  + " is not available to "
                  + describeScope(userScope)
                  + " in service "
                  + serviceId());
        }

      } else if (SessionScope.class.isAssignableFrom(scopeClass)) {
        return (T) getOrCreateSessionScope(scopeId, authorization, userScope, runtimeId);
      }
    }
    Logging.INSTANCE.warn(
        "Scope resolution failed for scopeId="
            + scopeId
            + ", requestedClass="
            + scopeClass.getSimpleName()
            + ", runtimeId="
            + runtimeId
            + " in service "
            + serviceId()
            + " for "
            + describeAuthorization(authorization));
    return null;
  }

  public ServiceSessionScope getOrCreateSessionScope(
      String sessionId,
      EngineAuthorization authorization,
      ServiceUserScope userScope,
      String runtimeId) {

    var ret = getScope(sessionId, ServiceSessionScope.class);
    if (ret != null) {
      return ret;
    }

    var federation = Klab.INSTANCE.getFederationData(userScope.getUser());
    var acceptedSessionId =
        federation == null
            ? userScope.getUser().getUsername().replace(".", "_")
            : federation.getId().replace(".", "_");
    if (sessionId.equals(acceptedSessionId)) {
      ret = new ServiceSessionScope(userScope);
      ret.setStatus(Scope.Status.WAITING);
      ret.setId(sessionId);
      ret.setHostServiceId(runtimeId);
      ret.setName(
          federation == null || Federation.LOCAL_FEDERATION_ID.equals(federation.getId())
              ? userScope.getUser().getUsername()
              : federation.getId());
      for (var service : userScope.getServices(KlabService.class)) {
        if (service instanceof RuntimeService
            && runtimeId != null
            && !runtimeId.equals(service.serviceId())) {
          continue;
        }
        ret.addService(service);
      }
      service.declareSessionScope(ret, userScope, null);
      Logging.INSTANCE.info(
          "Created reconstructed session scope "
              + describeScope(ret)
              + " for "
              + describeAuthorization(authorization)
              + " in service "
              + serviceId());
      return ret;
    }
    Logging.INSTANCE.warn(
        "Rejected propagated session scope "
            + sessionId
            + " for "
            + describeAuthorization(authorization)
            + " in service "
            + serviceId()
            + "; accepted session ID is "
            + acceptedSessionId);
    return null;
  }

  public void shutdown() {
    //    if (actorSystem != null) {
    //      actorSystem.shutDown();
    //    }
  }

  /**
   * Create a new scope that will record the payload of the messages it sees when they match the
   * passed class. The ID of the scope is set to that of the service and will be used as the ID of
   * the {@link org.integratedmodelling.klab.api.services.resources.ResourceSet} generated from
   * collected notifications.
   *
   * @param scope
   * @param payloadClass
   * @param payloadCollection
   * @param <T>
   * @param <S>
   * @return
   */
  public <T, S extends Scope> S collectMessagePayload(
      S scope, Class<T> payloadClass, List<T> payloadCollection) {
    // TODO create a new scope with collector of any message payload that matches the passed class
    if (scope instanceof ServiceUserScope serviceUserScope) {
      var ret = serviceUserScope.copy();
      ret.setId(service.serviceId());
      ret.collectMessagePayload(payloadClass, payloadCollection);
      return (S) ret;
    }
    return scope;
  }
}
