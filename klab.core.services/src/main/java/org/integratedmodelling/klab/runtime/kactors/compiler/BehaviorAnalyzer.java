package org.integratedmodelling.klab.runtime.kactors.compiler;

import java.util.*;

import org.integratedmodelling.klab.api.lang.kactors.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;

/** Analyze the behavior and collect infomation for code generation. */
public class BehaviorAnalyzer {

  private final KActorsBehavior behavior;
  private Class<? extends RuntimeAgentBase> agentClass = RuntimeAgentBase.class;
  private List<Notification> notifications = new ArrayList<>();

  public BehaviorAnalyzer(KActorsBehavior behavior) {
    this.behavior = behavior;
  }

  public boolean analyze() {

    // Collect all info in a first pass
    var visitor = new KActorsVisitor();
    visitor.visit(behavior);
    this.notifications.addAll(visitor.getNotifications());

    // Verify all info in the second pass, w.r.t. the component manager, collecting notifications

    // If all tests pass, return true

    return true;
  }

  public Class<? extends RuntimeAgentBase> getAgentClass() {
    return agentClass;
  }

  public Collection<Notification> getNotifications() {
    return notifications;
  }
}
