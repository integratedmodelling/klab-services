package org.integratedmodelling.klab.runtime.kactors.compiler.runtime;

import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;

/**
 * The agent registry manages compilation and bookkeeping of agent classes and instances. Agent
 * instances de-register when they are stopped.
 */
public enum AgentRegistry {
  INSTANCE;

  /**
   * Given a compiled and correct behavior, generate its Java code using a linking validator and
   * compile it into a class.
   *
   * @param behavior
   * @return
   */
  public Class<? extends RuntimeAgentBase> getAgentClass(KActorsBehavior behavior) {
    return null;
  }

  /**
   * Given an agent handle, return the corresponding instance, or null if the instance does not
   * exist in the registry.
   *
   * @param agent
   * @return
   * @param <T>
   */
  public <T extends RuntimeAgentBase> Class<T> getAgentInstance(Agent agent) {
    return null;
  }

  /**
   * Given an agent handle, return the same handle if the corresponding instance it exists in the
   * registry; otherwise, create the agent and return the handle that locates it. If agent creation
   * fails, return an handle responding false to {@link Agent#isAlive()}.
   *
   * @param agent
   * @return
   */
  public Agent getOrCreateAgent(Agent agent) {
    return null;
  }
}
