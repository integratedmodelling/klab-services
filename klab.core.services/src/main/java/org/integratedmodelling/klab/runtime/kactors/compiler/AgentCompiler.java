package org.integratedmodelling.klab.runtime.kactors.compiler;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum AgentCompiler {
  INSTANCE;

  private Map<String, Class<? extends AgentBase>> compiledActorClasses = new ConcurrentHashMap<>();

  public AgentBase compile(String urn, Scope scope) {

    Class<? extends AgentBase> compiledActorClass = compiledActorClasses.get(urn);

    // TODO use versions intelligently
    if (compiledActorClass == null) {

      // TODO use all services
      var behavior = scope.getService(ResourcesService.class).retrieveBehavior(urn, scope);
      if (behavior != null) {
        compiledActorClass = compileBehavior(behavior);
      }
    }

    AgentBase ret = null;
    if (compiledActorClass != null) {}

    return ret;
  }

  private Class<? extends AgentBase> compileBehavior(KActorsBehavior behavior) {
    return null;
  }
}
