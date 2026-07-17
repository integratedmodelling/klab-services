package org.integratedmodelling.klab.runtime.kactors.compiler;

import java.util.*;

import org.h2.expression.Variable;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.lang.kactors.*;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.kactors.AgentBase;

/** Analyze the behavior and collect infomation for code generation. */
public class BehaviorAnalyzer {

  private final KActorsBehavior behavior;
  private Class<? extends AgentBase> agentClass = AgentBase.class;
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

  public Class<? extends AgentBase> getAgentClass() {
    return agentClass;
  }

  public Collection<Notification> getNotifications() {
    return notifications;
  }
}
