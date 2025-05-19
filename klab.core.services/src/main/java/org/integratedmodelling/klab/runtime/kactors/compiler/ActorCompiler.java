package org.integratedmodelling.klab.runtime.kactors.compiler;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum ActorCompiler {

  INSTANCE;

  private Map<String, Class<? extends ActorBase>> compiledActorClasses =
      Collections.synchronizedMap(new HashMap<>());

  public ActorBase compile(String urn, Scope scope) {

    Class<? extends ActorBase> compiledActorClass = compiledActorClasses.get(urn);

    // TODO use versions intelligently
    if (compiledActorClass == null) {

      // TODO use all services
      var behavior = scope.getService(ResourcesService.class).retrieveBehavior(urn, scope);
      if (behavior != null) {
        compiledActorClass = compileBehavior(behavior);
      }

    }

    ActorBase ret = null;
    if (compiledActorClass != null) {

    }
    return ret;
  }

  private Class<? extends ActorBase> compileBehavior(KActorsBehavior behavior) {
    return null;
  }
}
