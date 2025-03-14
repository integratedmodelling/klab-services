package org.integratedmodelling.klab.services.scopes;

import io.reacted.core.config.reactorsystem.ReActorSystemConfig;
import io.reacted.core.reactorsystem.ReActorSystem;
import org.integratedmodelling.common.authentication.UserIdentityImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.actors.KAgent;
import org.integratedmodelling.klab.services.actors.UserAgent;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.utilities.Utils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The scope manager maintains service-side scopes that are generated through the orchestrating
 * engine. When actors are requested, the necessary chain is created as needed. The main strategy
 * for resource maintenance and session expiration is here.
 */
public class ScopeManager {

  private ReActorSystem actorSystem = null;
  KlabService service;

  /**
   * Every scope managed by this service. The relationship between scopes is managed through the
   * scope graph, using only the IDs. Scopes may persist in services that allow that, and that is
   * managed externally by recreating the scopes and their content upon request.
   */
  private Map<String, ServiceUserScope> scopes = Collections.synchronizedMap(new HashMap<>());

  private Map<String, Long> idleScopeTime = Collections.synchronizedMap(new HashMap<>());
  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

  public ScopeManager(KlabService service) {

    this.service = service;

    if (service instanceof BaseService baseService && baseService.scopesAreReactive()) {
      /*
       * boot the actor system right away, so that we can call login() before boot().
       */
      this.actorSystem =
          new ReActorSystem(ReActorSystemConfig.newBuilder().setReactorSystemName("klab").build())
              .initReActorSystem();

      Logging.INSTANCE.info("Actor system booted");
    }

    executor.scheduleAtFixedRate(() -> expiredScopeCheck(), 60, 60, TimeUnit.SECONDS);
  }

  private void expiredScopeCheck() {

    // send each scope closing to a virtual thread after removing from the scope map
  }

  public void registerScope(ServiceUserScope serviceScope, URI brokerUri) {
    scopes.put(serviceScope.getId(), serviceScope);
  }

  public ServiceUserScope login(UserIdentity user) {

    ServiceUserScope ret = scopes.get(user.getUsername());
    if (ret == null) {

      /**
       * The user scope is for a legitimately authorized user, so by default it uses all services
       * from the service scope. TODO we should filter them by permission vs. the user identity!
       * That can be done directly in the overloaded functions below.
       */
      ret =
          new ServiceUserScope(user, service) {

            @Override
            public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
              // TODO filter by permission
              return service.serviceScope().getServices(serviceClass);
            }

            @Override
            public <T extends KlabService> T getService(Class<T> serviceClass) {
              // TODO filter by permission
              return service.serviceScope().getService(serviceClass);
            }

            @Override
            public <T extends KlabService> T getService(String serviceId, Class<T> serviceClass) {
              // TODO filter by permission
              return service.serviceScope().getService(serviceId, serviceClass);
            }
          };

      if (Utils.URLs.isLocalHost(service.getUrl())) {
        /*
        pre-advertise queues; channel setup will happen later
         */
        var capabilities = service.capabilities(ret);
        for (var queue : capabilities.getAvailableMessagingQueues()) {
          ret.presetMessagingQueue(
              queue, capabilities.getType().name().toLowerCase() + "." + user.getUsername());
        }
      }

      if (service instanceof BaseService baseService && baseService.scopesAreReactive()) {
        /** agents are only created for services that request them */
        String agentName = KAgent.sanitizeName(user.getUsername());
        // TODO move to lazy logics
        KActorsBehavior.Ref agent =
            KAgent.KAgentRef.get(actorSystem.spawn(new UserAgent(agentName, ret)).get());
        ret.setAgent(agent);
        ret.setId(user.getUsername());

        File userBehavior =
            new File(ServiceConfiguration.INSTANCE.getDataPath() + File.separator + "user.kactors");
        if (userBehavior.isFile() && userBehavior.canRead()) {
          try {
            ret.send(
                Message.MessageClass.ActorCommunication,
                Message.MessageType.RunBehavior,
                userBehavior.toURI().toURL());
          } catch (MalformedURLException e) {
            ret.error(e, "while reading user.kactors behavior");
          }
        }
      }

      scopes.put(user.getUsername(), ret);
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
    UserIdentityImpl ret = new UserIdentityImpl();
    ret.setUsername(engineAuthorization.getUsername());
    ret.setId(engineAuthorization.getToken());
    ret.setAuthenticated(engineAuthorization.isAuthenticated());
    // TODO continue
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

    if (authorization.isLocal()) {
      ret.setLocal(true);
      // setup queues in scope if user is local and messaging is configured, Queue will be
      // servicetype.username.queuetype
      var brokerURI = service.capabilities(ret).getBrokerURI();
      if (brokerURI != null && !service.capabilities(ret).getAvailableMessagingQueues().isEmpty()) {
        ret.setupMessaging(
            brokerURI.toString(),
            service.capabilities(null).getType().name().toLowerCase()
                + "."
                + authorization.getUsername(),
            service.capabilities(ret).getAvailableMessagingQueues());
      }
    }

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
      ServiceContextScope rootScope, ContextScope.ScopeData contextualization) {

    ServiceContextScope ret = rootScope;

    if (contextualization.observationPath() != null) {
      for (var observationId : contextualization.observationPath()) {
        var observation = ret.query(Observation.class, observationId);
        if (observation.isEmpty()) {
          throw new KlabResourceAccessException(
              "Observation with ID "
                  + observationId
                  + " not "
                  + "found in context "
                  + ret.getName());
        }
        ret = (ServiceContextScope) ret.within(observation.getFirst());
      }
    }

    if (contextualization.observerId() != Observation.UNASSIGNED_ID) {
      var observer = ret.query(Observation.class, contextualization.observerId());
      if (observer.isEmpty()) {
        throw new KlabResourceAccessException(
            "Subject with ID "
                + contextualization.observerId()
                + " not found in "
                + "context "
                + ret.getName());
      }
      ret = ret.withObserver(observer.getFirst());
    }

    return ret;
  }

  public <T extends Scope> T getScope(String scopeId, Class<T> scopeClass) {
    var ret = scopes.get(scopeId);
    if (ret != null && ret.getClass().isAssignableFrom(scopeClass)) {
      return (T) ret;
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
    return scopes.remove(scopeId) != null;
  }

  /**
   * Get the scope for the passed parameters. If the scope isn't there or has expired,
   *
   * @param authorization
   * @param scopeClass
   * @param scopeId
   * @param <T>
   * @return
   */
  public <T extends Scope> T getScope(
      EngineAuthorization authorization, Class<T> scopeClass, String scopeId) {

    var scope = getOrCreateUserScope(authorization);
    if (scopeId == null && scope != null && scopeClass.isAssignableFrom(scope.getClass())) {
      return (T) scope;
    }

    if (scopeId != null) {
      var ret = scopes.get(scopeId);
      if (ret != null && scopeClass.isAssignableFrom(ret.getClass())) {
        return (T) ret;
      }
    }
    return null;
  }

  public void shutdown() {
    if (actorSystem != null) {
      actorSystem.shutDown();
    }
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
